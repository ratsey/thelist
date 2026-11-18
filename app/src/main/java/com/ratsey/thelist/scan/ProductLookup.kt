package com.ratsey.thelist.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** What the product database returned for one scan. Every field but the name is optional. */
data class ScannedProduct(
    val barcode: String,
    val name: String,
    val brands: String? = null,
    /** The pack size as the label prints it, e.g. "400 g". */
    val quantity: String? = null,
    val category: String? = null,
)

/**
 * Scanner text is digits in one of the retail symbologies. The product database is keyed on
 * 13-digit EAN-13 — which is a UPC-A with a leading zero — and 8-digit EAN-8, so every scan is
 * widened to one of those before it is looked up.
 */
object Barcodes {

    /** Digits only, widened to what the database stores. Null when the text is not a retail code. */
    fun normalize(raw: String): String? {
        val digits = raw.trim().filter { it.isDigit() }
        return when (digits.length) {
            13, 8 -> digits
            // UPC-A scans as 12 digits; its EAN-13 form is the same digits with a zero in front.
            12 -> "0$digits"
            else -> null
        }
    }

    /**
     * UPC-E is a compressed UPC-A: the trailing zeros of the manufacturer code and the leading
     * zeros of the product code are folded away, and the GS1 pattern table says how they come
     * back, driven by the last data digit. The check digit carries over unchanged.
     */
    fun expandUpcE(eight: String): String? {
        if (eight.length != 8) return null
        val numberSystem = eight[0]
        if (numberSystem !in "01") return null
        val manufacturer = eight.substring(1, 6) // five data digits; the sixth drives the pattern
        val last = eight[6]
        val check = eight[7]
        val expanded = when (last) {
            '0' -> "$numberSystem${manufacturer.substring(0, 2)}00000${manufacturer.substring(2)}"
            '1', '2' ->
                "$numberSystem${manufacturer.substring(0, 2)}${last}0000${manufacturer.substring(2)}"
            '3' -> "$numberSystem${manufacturer.substring(0, 3)}00000${manufacturer.substring(3)}"
            '4' -> "$numberSystem${manufacturer.substring(0, 4)}00000${manufacturer.substring(4)}"
            else -> "$numberSystem${manufacturer}0000$last"
        }
        return "$expanded$check"
    }
}

/**
 * Asks Open Food Facts — a free, open, crowd-sourced product database — what a barcode is.
 * JSON over HttpURLConnection like the sync client: no network library, no key, no account.
 */
class ProductLookup {

    /** Null when the database does not know the code, or cannot be reached. */
    suspend fun lookup(rawCode: String): ScannedProduct? {
        val barcode = Barcodes.normalize(rawCode) ?: return null
        return withContext(Dispatchers.IO) {
            fetch(barcode)?.let { return@withContext parse(barcode, it) }
            // Some compressed UPC-E rows are only stored under the widened code; try both.
            val expanded = Barcodes.expandUpcE(barcode) ?: return@withContext null
            fetch(expanded)?.let { parse(barcode, it) }
        }
    }

    internal fun parse(scanCode: String, json: JSONObject): ScannedProduct? {
        if (json.optInt("status", 0) != 1) return null
        val product = json.optJSONObject("product") ?: return null
        val name = product.optString("product_name").trim()
        if (name.isEmpty()) return null
        return ScannedProduct(
            barcode = scanCode,
            name = name,
            brands = product.optString("brands").trim().takeIf { it.isNotEmpty() },
            quantity = cleanQuantity(product.optString("quantity")),
            category = product.optString("categories")
                .split(',')
                .firstOrNull { it.isNotBlank() }
                ?.trim(),
        )
    }

    /**
     * The pack size is shown as the label prints it. Open Food Facts marks estimated weights
     * with a trailing "e" ("400 g e") — that mark is for weighers, not shoppers, so it goes.
     */
    internal fun cleanQuantity(raw: String): String? {
        val cleaned = raw.trim().removeSuffix(" e").removeSuffix(" E").trim()
        return cleaned.takeIf { it.isNotEmpty() }
    }

    /** A quiet failure: no row, or the service not answering, both mean "nothing to prefill". */
    private suspend fun fetch(barcode: String): JSONObject? {
        val url = URL("$BASE_URL/$barcode.json?fields=product_name,brands,quantity,categories")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TheList-Android")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return text.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/api/v2/product"
    }
}
