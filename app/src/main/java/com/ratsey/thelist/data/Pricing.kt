package com.ratsey.thelist.data

/** What a price is measured against: one item, a weight, a volume, or something unclassifiable. */
enum class Measure { COUNT, WEIGHT, VOLUME, OTHER }

/**
 * What a price is per: "each", "lb", "100 g", "L".
 *
 * A price on its own is money without a meaning — $2.99 of what? — so every recorded price carries
 * one of these, and the only fair comparison is between two of them measuring the same thing.
 * [factor] is how many base units one [unit] is (grams, millilitres, or one item), which makes $/lb
 * and $/kg comparable and keeps a pound from ever being compared with a litre.
 */
data class PriceBasis(
    val measure: Measure,
    /** The unit as written, canonicalised: "lb", "g", "ml", "each"; the raw text when unknown. */
    val unit: String,
    val factor: Double,
    /** How many [unit] the price is for: 1 for "$2.99/lb", 100 for "$3.00/100 g". */
    val quantity: Double = 1.0,
) {
    /** Money per gram, per millilitre or per item — the common ground two prices can meet on. */
    fun unitRate(price: Double): Double? {
        val scale = factor * quantity
        return if (scale > 0) price / scale else null
    }

    /** [price] restated in [other]'s unit, or null when they are not measuring the same thing. */
    fun convert(price: Double, other: PriceBasis): Double? {
        if (measure != other.measure) return null
        // Unknown units are only comparable with themselves: "punnet" is not "each".
        if (measure == Measure.OTHER && other.unit != unit) return null
        val rate = unitRate(price) ?: return null
        return rate * other.factor * other.quantity
    }

    /** "lb", or "100 g" when the price is for more than one of them. */
    val label: String
        get() = if (quantity == 1.0) unit else "${formatQuantity(quantity)} $unit"
}

/** [price] restated from [from] into [to], or null when the two cannot honestly be compared. */
fun priceIn(price: Double, from: PriceBasis?, to: PriceBasis?): Double? = when {
    from == null && to == null -> price
    from == null || to == null -> null
    else -> from.convert(price, to)
}

/**
 * The identity of a price history: one product, one type. Whole milk and semi skimmed are two
 * histories, because their prices are two prices.
 */
fun historyKey(productKey: String, variant: String?): String {
    val trimmed = variant?.trim()?.lowercase().orEmpty()
    return if (trimmed.isEmpty()) productKey else "$productKey|$trimmed"
}

/** Base units per unit of measure: grams, millilitres, or items. */
private val UNITS: Map<String, Pair<Measure, Double>> = buildMap {
    listOf(
        "each", "ea", "unit", "units", "item", "items", "pc", "pcs", "piece", "pieces",
        // Anything sold as a countable pack: pods, tablets, sheets, washes. Each is a count of one,
        // which is what makes "$0.30/pod", "$0.30/count" and "$0.30/each" the same price.
        "count", "counts", "ct", "cts", "pod", "pods", "tablet", "tablets",
        "capsule", "capsules", "sheet", "sheets", "roll", "rolls", "load", "loads",
        "wash", "washes",
    ).forEach { put(it, Measure.COUNT to 1.0) }
    listOf("g", "gram", "grams").forEach { put(it, Measure.WEIGHT to 1.0) }
    listOf("mg", "milligram", "milligrams").forEach { put(it, Measure.WEIGHT to 0.001) }
    listOf("kg", "kilo", "kilos", "kilogram", "kilograms")
        .forEach { put(it, Measure.WEIGHT to 1000.0) }
    listOf("lb", "lbs", "pound", "pounds").forEach { put(it, Measure.WEIGHT to 453.59237) }
    listOf("oz", "ounce", "ounces").forEach { put(it, Measure.WEIGHT to 28.349523125) }
    listOf("ml", "millilitre", "millilitres", "milliliter", "milliliters")
        .forEach { put(it, Measure.VOLUME to 1.0) }
    put("cl", Measure.VOLUME to 10.0)
    listOf("l", "litre", "litres", "liter", "liters").forEach { put(it, Measure.VOLUME to 1000.0) }
    listOf("fl oz", "floz", "fl. oz").forEach { put(it, Measure.VOLUME to 29.5735295625) }
    listOf("pt", "pint", "pints").forEach { put(it, Measure.VOLUME to 473.176473) }
    listOf("qt", "quart", "quarts").forEach { put(it, Measure.VOLUME to 946.352946) }
    listOf("gal", "gallon", "gallons").forEach { put(it, Measure.VOLUME to 3785.411784) }
}

private val BASIS_TEXT = Regex("^([0-9]+(?:[.,][0-9]+)?)?\\s*(.+)$")

/**
 * Reads what a price is per out of the free text typed in the "Per" field: "lb", "per lb", "each",
 * "100 g", "fl oz". Null means the price is just money, which is honest: it cannot be compared with
 * anything until it says what it is for.
 */
fun parsePriceBasis(text: String?): PriceBasis? {
    val cleaned = text?.trim()?.lowercase()?.removePrefix("per")?.trim().orEmpty()
    if (cleaned.isEmpty()) return null
    val match = BASIS_TEXT.find(cleaned) ?: return null
    val quantity = match.groupValues[1].replace(',', '.').takeIf { it.isNotEmpty() }
        ?.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
    val unit = match.groupValues[2].trim()
    if (unit.isEmpty()) return null
    val known = UNITS[unit] ?: UNITS[unit.removeSuffix("s")]
    return if (known != null) {
        PriceBasis(known.first, unit.removeSuffix("s").ifEmpty { unit }, known.second, quantity)
    } else {
        // Something the app has never heard of: kept, shown, and only ever compared with itself.
        PriceBasis(Measure.OTHER, unit, 1.0, quantity)
    }
}

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
