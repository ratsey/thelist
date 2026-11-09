package com.ratsey.thelist.receipt

/**
 * Turns raw OCR text from a till receipt into a store, a date, a total and priced lines.
 *
 * Receipts vary wildly, so this is a set of conservative heuristics: keep anything that
 * looks like "name ... price", drop the footer noise, and never invent a number. Anything
 * uncertain is left null for the review screen to fill in.
 */
object ReceiptParser {

    private val PRICE = Regex("""(?<![\d.,])(\d{1,4}[.,]\d{2})(?![\d])""")
    private val QTY_MULTIPLIER = Regex("""\b(\d{1,3})\s*[xX@]\s*(\d{1,4}[.,]\d{2})\b""")
    private val QTY_PREFIX = Regex("""^\s*(\d{1,3})\s*[xX@]\s*""")
    private val LEADING_CODE = Regex("""^\s*\d{3,6}\s+""")
    private val TRAILING_FLAG = Regex("""\s+[TFB]$""")
    private val SPACES = Regex("""\s+""")
    private val ALL_CAPS_HINT = Regex("""[A-Z]{3,}""")

    private val SKIP_WORDS = listOf(
        "total", "subtotal", "sub total", "balance", "vat", "tax", "cash", "change", "card",
        "tender", "saving", "discount", "points", "loyalty", "reward", "amount", "approved",
        "auth", "terminal", "receipt", "invoice", "tel", "phone", "www", "http", "thank",
        "order", "cardholder", "expiry", "signature", "pin", "clubcard", "nectar", "coupon",
        "item count", "items", "rounding", "deposit", "refund", "basket", "store", "shop",
        "date", "time", "assistant", "till", "lane", "served", "savingstotal",
        "summe", "gesamt", "gesamtsumme", "zwischensumme", "bar", "ec", "karte", "kartenzahlung",
        "rueckgeld", "ruckgeld", "mwst", "mehrwertsteuer", "gesamtbetrag", "montant", "espece",
        "carte", "tva", "importe", "efectivo", "tarjeta", "iva", "cambio", "suma", "articulos",
    )
    private val SKIP_PATTERN = Regex(
        "\\b(" + SKIP_WORDS.joinToString("|") { Regex.escape(it) } + ")\\b",
        RegexOption.IGNORE_CASE,
    )

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    fun parse(rawText: String, dayFirst: Boolean = true): ParsedReceipt {
        val lines = rawText.lines().map { it.replace(SPACES, " ").trim() }.filter { it.isNotEmpty() }
        val currency = detectCurrency(rawText)
        val parsedLines = lines.mapNotNull { parseLine(it) }
        val total = findTotal(lines) ?: parsedLines.takeIf { it.isNotEmpty() }
            ?.sumOf { it.lineTotal }
            ?.takeIf { it > 0 }
            ?.round2()

        return ParsedReceipt(
            storeName = findStore(lines),
            purchasedAt = parseDate(lines.joinToString(" "), dayFirst),
            currency = currency,
            total = total,
            lines = parsedLines,
        )
    }

    private fun detectCurrency(text: String): String = when {
        text.contains("£") -> "£"
        text.contains("€") -> "€"
        text.contains("$") -> "$"
        Regex("""\bGBP\b""").containsMatchIn(text) -> "£"
        Regex("""\b(EUR|EURO)\b""").containsMatchIn(text) -> "€"
        Regex("""\bUSD\b""").containsMatchIn(text) -> "$"
        else -> "£"
    }

    private fun parseLine(line: String): ParsedLine? {
        val priceMatches = PRICE.findAll(line).toList()
        if (priceMatches.isEmpty()) return null
        if (SKIP_PATTERN.containsMatchIn(line)) return null

        val firstPriceStart = priceMatches.first().range.first
        if (firstPriceStart <= 0) return null

        var namePart = line.substring(0, firstPriceStart)
        val quantityHint = QTY_MULTIPLIER.find(line)?.groupValues?.get(1)?.toIntOrNull()
            ?: QTY_PREFIX.find(namePart)?.groupValues?.get(1)?.toIntOrNull()

        namePart = namePart
            .replace(QTY_MULTIPLIER, " ")
            .replace(QTY_PREFIX, " ")
            .replace(LEADING_CODE, " ")

        val name = cleanName(namePart)
        if (name.count { it.isLetter() } < 2) return null
        if (name.length > 60) return null
        if (!name.any { it.isLetter() }) return null

        val prices = priceMatches.map { toDouble(it.groupValues[1]) }.filter { it > 0.0 }
        if (prices.isEmpty()) return null

        val quantity = (quantityHint ?: 1).coerceAtLeast(1).toDouble()
        val lineTotal = prices.last()
        val unitPrice = when {
            quantity > 1 && prices.size >= 2 -> prices.first()
            quantity > 1 -> (lineTotal / quantity).round2()
            else -> lineTotal
        }

        if (lineTotal > 5000.0) return null

        return ParsedLine(
            name = name,
            quantity = quantity,
            unitPrice = unitPrice,
            lineTotal = lineTotal,
        )
    }

    private fun cleanName(raw: String): String =
        raw.replace(SPACES, " ")
            .trim()
            .trimEnd('*', '/', '-', ':', '.', '@', ' ')
            .replace(TRAILING_FLAG, "")
            .trim()

    private fun toDouble(value: String): Double =
        value.replace(',', '.').toDoubleOrNull() ?: 0.0

    private fun findTotal(lines: List<String>): Double? {
        val totals = lines.filter { line ->
            Regex("""\btotal\b""", RegexOption.IGNORE_CASE).containsMatchIn(line) &&
                !Regex("""\bsub""", RegexOption.IGNORE_CASE).containsMatchIn(line)
        }
        for (line in totals.asReversed()) {
            val match = PRICE.findAll(line).lastOrNull() ?: continue
            val value = toDouble(match.groupValues[1])
            if (value > 0.0) return value
        }
        return null
    }

    private fun findStore(lines: List<String>): String? =
        lines.take(10).firstOrNull { line ->
            line.length in 3..40 &&
                line.count { it.isLetter() } >= 3 &&
                line.count { it.isDigit() } <= 1 &&
                !SKIP_PATTERN.containsMatchIn(line) &&
                !PRICE.containsMatchIn(line) &&
                !line.startsWith("http", ignoreCase = true) &&
                (line.any { it.isLetter() } && (ALL_CAPS_HINT.containsMatchIn(line) || line.any { it.isUpperCase() }))
        }?.trim()

    private fun parseDate(text: String, dayFirst: Boolean): Long? {
        ISO_DATE.find(text)?.let { match ->
            val (year, month, day) = match.destructured
            return buildDate(year.toInt(), month.toInt(), day.toInt())
        }

        NUMERIC_DATE.findAll(text).forEach { match ->
            val (a, b, yearRaw) = match.destructured
            var year = yearRaw.toInt()
            if (year < 100) year += 2000
            val first = a.toInt()
            val second = b.toInt()

            val candidates = when {
                first > 12 -> listOf(second to first)
                second > 12 -> listOf(first to second)
                dayFirst -> listOf(second to first, first to second)
                else -> listOf(first to second, second to first)
            }
            for ((month, day) in candidates) {
                buildDate(year, month, day)?.let { return it }
            }
        }

        DAY_MONTH_NAME.find(text)?.let { match ->
            val (day, monthName, yearRaw) = match.destructured
            monthNumber(monthName)?.let { month ->
                var year = yearRaw.toInt()
                if (year < 100) year += 2000
                buildDate(year, month, day.toInt())?.let { return it }
            }
        }

        MONTH_NAME_DAY.find(text)?.let { match ->
            val (monthName, day, yearRaw) = match.destructured
            monthNumber(monthName)?.let { month ->
                var year = yearRaw.toInt()
                if (year < 100) year += 2000
                buildDate(year, month, day.toInt())?.let { return it }
            }
        }

        return null
    }

    private fun monthNumber(name: String): Int? {
        val key = name.lowercase().take(3)
        return MONTHS[key]
    }

    private fun buildDate(year: Int, month: Int, day: Int): Long? {
        val date = runCatching { java.time.LocalDate.of(year, month, day) }.getOrNull() ?: return null
        val today = java.time.LocalDate.now()
        if (date.isAfter(today.plusDays(1))) return null
        if (date.isBefore(today.minusYears(6))) return null
        return date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private val ISO_DATE = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val NUMERIC_DATE = Regex("""(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})""")
    private val DAY_MONTH_NAME = Regex(
        """(\d{1,2})\s*([A-Za-z]{3,9})\.?\s*,?\s*(\d{2,4})""",
    )
    private val MONTH_NAME_DAY = Regex(
        """([A-Za-z]{3,9})\.?\s*(\d{1,2})\s*,?\s*(\d{2,4})""",
    )
}

fun Double.round2(): Double = kotlin.math.round(this * 100) / 100

data class ParsedLine(
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double,
)

data class ParsedReceipt(
    val storeName: String?,
    val purchasedAt: Long?,
    val currency: String,
    val total: Double?,
    val lines: List<ParsedLine>,
)
