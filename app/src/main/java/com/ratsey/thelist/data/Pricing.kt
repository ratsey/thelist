package com.ratsey.thelist.data

/**
 * What a price is measured against: one item, a weight, a volume, the length of the thing itself
 * (a roll of tape sold by the yard), or something unclassifiable.
 */
enum class Measure { COUNT, WEIGHT, VOLUME, LENGTH, OTHER }

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

    /**
     * "lb", or "100 g" when the price is for more than one of them.
     *
     * A count says the number once: "$8.12/60", not "$8.12/60 count", because the figure is the
     * count. A named pack keeps its noun — "$3.00/30 pods" is not "$3.00/30".
     */
    val label: String
        get() = when {
            // Every countable thing is the same thing: "each", "unit", "count", "item" and "pc"
            // are one apiece, so they all read as "each" rather than as four kinds of price.
            measure == Measure.COUNT && quantity == 1.0 -> "each"
            quantity == 1.0 -> unit
            measure == Measure.COUNT && unit == COUNT_UNIT -> formatQuantity(quantity)
            else -> "${formatQuantity(quantity)} $unit"
        }
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

    // The length of the thing, in millimetres: tape by the yard, cable by the metre, ribbon by
    // the foot. A roll is bought for how long it is, which is why this is not a distance.
    listOf("mm", "millimetre", "millimetres", "millimeter", "millimeters")
        .forEach { put(it, Measure.LENGTH to 1.0) }
    listOf("cm", "centimetre", "centimetres", "centimeter", "centimeters")
        .forEach { put(it, Measure.LENGTH to 10.0) }
    listOf("m", "metre", "metres", "meter", "meters")
        .forEach { put(it, Measure.LENGTH to 1000.0) }
    listOf("km", "kilometre", "kilometres", "kilometer", "kilometers")
        .forEach { put(it, Measure.LENGTH to 1_000_000.0) }
    listOf("in", "inch", "inches").forEach { put(it, Measure.LENGTH to 25.4) }
    listOf("ft", "foot", "feet").forEach { put(it, Measure.LENGTH to 304.8) }
    listOf("yd", "yard", "yards").forEach { put(it, Measure.LENGTH to 914.4) }
    listOf("mi", "mile", "miles").forEach { put(it, Measure.LENGTH to 1_609_344.0) }
}

private val BASIS_TEXT = Regex("^([0-9]+(?:[.,][0-9]+)?)?\\s*(.+)$")

/** A number on its own, with no unit word after it: "60". */
private val BARE_COUNT = Regex("[0-9]+(?:[.,][0-9]+)?")

/** The canonical unit a bare number means, and the one word worth leaving out. */
private const val COUNT_UNIT = "count"

/**
 * Reads what a price is per out of the free text typed in the "Per" field: "lb", "per lb", "each",
 * "100 g", "fl oz". Null means the price is just money, which is honest: it cannot be compared with
 * anything until it says what it is for.
 */
fun parsePriceBasis(text: String?): PriceBasis? {
    val cleaned = text?.trim()?.lowercase()?.removePrefix("per")?.trim().orEmpty()
    if (cleaned.isEmpty()) return null

    // "60" is "per 60 of them". Without this the digits were read as a quantity followed by a
    // unit — "6" of "0" — so "$8.12/60" and "$6/6" landed in two unknown measures that could
    // never be compared, which is the one thing the number was there to make possible.
    if (BARE_COUNT.matches(cleaned)) {
        val quantity = cleaned.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return PriceBasis(Measure.COUNT, "count", 1.0, quantity)
    }

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

/**
 * Which set of measures the app offers. It changes what is *suggested* for comparison, never what
 * was already typed: a price recorded in ounces stays an ounce price, it just stops being the
 * natural thing to switch to.
 */
enum class MeasureSystem(val stored: String) {
    Metric("metric"),
    Imperial("imperial"),
    ;

    companion object {
        /** Unreadable or absent settings fall back to imperial, which matches "$" as the default. */
        fun fromStored(value: String?): MeasureSystem =
            entries.firstOrNull { it.stored == value } ?: Imperial
    }
}

/** One of what a price buys, and what that one costs: the form two offers can be compared in. */
data class UnitPrice(val per: Double, val label: String)

/**
 * [price] restated as the price of a single unit, or null when it does not say what it is for.
 *
 * "$3.00 for 3 lb" is "$1.00/lb", and "$8.12 for 60" is 14 cents each: the number you can hold
 * against the next shop's offer. Null when the price is bare money, because money with no unit has
 * no rate to state.
 */
fun unitPriceOf(price: Double?, priceUnit: String?): UnitPrice? {
    if (price == null) return null
    val basis = parsePriceBasis(priceUnit) ?: return null
    val per = priceIn(price, basis, PriceBasis(basis.measure, basis.unit, basis.factor)) ?: return null
    val label = if (basis.measure == Measure.COUNT) "each" else basis.unit
    return UnitPrice(per, label)
}

/**
 * A stored per-field, split into the amount and the unit, for the field and the chips.
 *
 * "3 lb" is how much and of what — two things a chip row and a number box can each own — and
 * "60" is a count of sixty, which is why the unit comes back as "count" rather than nothing.
 */
fun splitPerField(text: String?): Pair<String, String> {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty()) return "" to ""
    val basis = parsePriceBasis(raw) ?: return "" to raw
    val quantity = if (basis.quantity == 1.0) "" else formatQuantity(basis.quantity)
    return quantity to basis.unit
}

/**
 * The per-field a chip row and an amount build: "3 lb", "60" for a plain count, "punnet".
 *
 * A count is stored as its number alone because that is how it is read and how it prints — the
 * word "count" would only repeat the figure.
 */
fun composePerField(quantity: String, unit: String): String {
    val amount = quantity.trim()
    val name = unit.trim()
    return when {
        name.isEmpty() -> amount
        name.equals("count", ignoreCase = true) && amount.isNotEmpty() -> amount
        amount.isEmpty() -> name
        else -> "$amount $name"
    }
}

/**
 * Units worth offering, grouped by the kind of thing they measure.
 *
 * [key] names the group so the screen can say it in its own language; the list carries the units
 * themselves.
 */
data class UnitGroup(val key: String, val units: List<String>)

/**
 * Every unit, in both systems at once, grouped by what it measures.
 *
 * An American shopper thinks in pounds and buys in kilos: the deli sells by the ounce while the
 * label says grams, and a foreign aisle quotes litres beside quarts. So both systems are always on
 * offer, and [system] only decides which comes first inside a group — the order you reach for, not
 * a limit on what exists.
 */
fun unitGroups(system: MeasureSystem): List<UnitGroup> {
    val metricFirst = system == MeasureSystem.Metric
    return listOf(
        UnitGroup("each", listOf("each", "count")),
        UnitGroup(
            "weight",
            if (metricFirst) listOf("g", "kg", "oz", "lb") else listOf("oz", "lb", "g", "kg"),
        ),
        UnitGroup(
            "volume",
            if (metricFirst) listOf("ml", "L", "fl oz") else listOf("fl oz", "ml", "L"),
        ),
        UnitGroup(
            "length",
            if (metricFirst) listOf("cm", "m", "in", "ft", "yd") else listOf("in", "ft", "yd", "cm", "m"),
        ),
    )
}

/**
 * The units offered for comparison in a measurement system, counting always included — both systems
 * flattened, since a price quoted in kilograms has to meet one quoted in pounds.
 */
fun comparisonUnits(system: MeasureSystem): List<String> = unitGroups(system).flatMap { it.units }

/** The hint in the "Per" field, in the units the app is set to speak. */
fun perFieldHint(system: MeasureSystem): String = when (system) {
    MeasureSystem.Metric -> "kg, 100 g, ml, count, m"
    MeasureSystem.Imperial -> "lb, 10 oz, fl oz, count, yd"
}

/** A currency symbol as typed: trimmed, at most three characters, "$" when nothing is left. */
fun normaliseCurrency(text: String?): String =
    text?.trim()?.take(3)?.takeIf { it.isNotEmpty() } ?: DEFAULT_CURRENCY
