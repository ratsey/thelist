package com.ratsey.thelist.data

/**
 * A price as it should appear in a text field: `3.5` rather than `3.50`, and a whole number with no
 * decimal part at all, because typing over ".00" every time is friction for nothing.
 */
fun formatPriceField(value: Double?): String = when {
    value == null -> ""
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}

/** `$1.25/lb`: a formatted price and what it is per. Without a unit it is just the price. */
fun priceWithUnit(price: String, unit: String?): String =
    unit?.trim()?.takeIf { it.isNotEmpty() }?.let { "$price/$it" } ?: price

/**
 * A price typed by hand, or null when it is not one.
 *
 * Blank and nonsense both come back null so callers can tell "nothing said" from "not a number" by
 * looking at the text itself: a comma is accepted as the decimal separator because half of Europe
 * types it that way, and a negative price is not a price.
 */
fun parsePriceField(text: String): Double? =
    text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it >= 0 }
