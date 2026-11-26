package com.ratsey.thelist.data

import java.util.Locale
import kotlin.math.round

/**
 * What a shelf-edge label says, as far as a camera can be sure of it.
 *
 * The label is the most trustworthy price in the shop and the only one that needs no network, which
 * is why reading it is worth the fuss. A misread digit is a wrong price, though, so this is always
 * something to show for confirmation — never something to save on its own.
 */
data class ShelfTag(
    /** The retail price: the big figure, not the rate printed beside it. */
    val price: Double?,
    /** What that price buys when the label prints it ("17 oz"); null when it does not. */
    val pack: String? = null,
    /**
     * The label's own rate, shown beside the figure so the two numbers can be held against each
     * other — and, when no pack size is printed, what states the size: see [pack].
     */
    val rate: UnitPrice? = null,
)

/** One line of recognised text, with how tall it was: the label prints the retail price biggest. */
data class TagLine(val text: String, val height: Int = 0)

/**
 * The cents are what make a number a price. A shelf label also carries bare numbers — the modular
 * position ("35"), the facility code ("0302"), the capacity ("CAP 8") — and none of them is money.
 */
private val MONEY = Regex("""([0-9]{1,4}[.,][0-9]{2})(?![0-9])""")

/** A currency mark, or what recognition hands back in its place: `S` and `§` for `$`. */
private val MONEY_MARK = Regex("""[${'$'}S§](?=\s?[0-9])""")

/**
 * The same price when the cents are printed small and raised (`$7⁵²`): recognition hands that back
 * with a space where the decimal point is, as `$7 52`, and often drops the mark as well.
 *
 * Two numbers side by side are ordinary on a shelf label — `FAC 1 CAP 8 0302` — so a pair is money
 * only when the mark is in front of it, or when the words on the line name a price
 * (`RETAIL PRICE 7 52`). The mark is the first group, so the caller can tell which it was.
 */
private val SPACED_MONEY = Regex("""(?<![A-Z0-9])([${'$'}S§]?)\s?([0-9]{1,4})\s+([0-9]{2})(?![0-9])""")

/**
 * A price whose cents sit so far above the line that recognition put them on their own line:
 * `RETAIL PRICE $7` and then `52`. Only a marked figure may be joined, so a modular position or a
 * facility code under a bare number is never stitched onto it.
 */
private val OPEN_PRICE = Regex("""(?<![A-Z0-9])[${'$'}S§]\s?[0-9]{1,4}[.,]?$""")

/** The line under an [OPEN_PRICE]: nothing but the two cents digits, and sometimes the point. */
private val CENTS_ONLY = Regex("""[.,]?[0-9]{2}""")

/** A space where the decimal point was: `7. 52`. */
private val SPLIT_POINT = Regex("""([0-9])\.\s+(?=[0-9]{2}(?![0-9]))""")

/** `UNIT PRICE`, and the `PER PT` that follows the rate on the line under it. */
private val RATE_LINE = Regex("""\b(UNIT|PER)\b""")

/** The words immediately before a figure on the same line: `UNIT PRICE $7.12`. */
private val RATE_BEFORE = Regex("""(UNIT|PER)(\s*PRICE)?\b[\s${'$'}S§]*$""")

/** `RETAIL PRICE`, `ROLLBACK`, `NOW`: the words that name the figure you actually pay. */
private val PRICE_LINE = Regex("""\b(PRICE|RETAIL|ROLLBACK|NOW)\b""")

/** The unit a rate is quoted in: `PER PT`, or `7.12/PT` where the label prints them on one line. */
private val UNIT_WORD = Regex("""(?:PER\s+|/\s?)(FL\.?\s?OZ|[A-Z]{1,3})(?![A-Z])""")

/**
 * A pack size printed beside the product name: `17 OZ`, `500 ML`, `1.5 L`. Longest unit first, so
 * `1 GAL` is a gallon rather than a gram followed by nonsense.
 */
private val PACK = Regex(
    """(?<![\d.])([0-9]{1,4}(?:[.,][0-9]+)?)\s?(FL\.?\s?OZ|GAL|LBS?|KG|MG|ML|QT|PT|CT|OZ|G|L)(?![A-Z])""",
)

/**
 * Reads a price tag from the lines a camera made of it.
 *
 * Three decisions, each with a reason a shopper would recognise:
 * - the **price** is the biggest figure that is not part of the rate block, because that is the
 *   number printed largest on the shelf edge;
 * - the **pack** is only taken when the label spells out a unit the app knows, because a size it
 *   misreads would quietly change the rate on every screen;
 * - the **rate** is read and used: most tags print the price and the rate for it but never the
 *   size, and dividing one by the other states the size, so the rate survives the save instead of
 *   being shown once and lost.
 *
 * Null when there is no price at all: "nothing readable" is better than a guess.
 */
fun readShelfTag(lines: List<TagLine>): ShelfTag? {
    if (lines.isEmpty()) return null
    val tagLines = joinSplitPrices(lines)
    val upper = tagLines.map { it.text.uppercase() }
    val bare = upper.map { MONEY_MARK.replace(it, " ") }

    // Every figure on the label, and where it sits: a narrow tag can come back as one line holding
    // the price and the rate, so the words in front of a figure are what tell the two apart.
    val figures = bare.indices.flatMap { i ->
        val withPoint = MONEY.findAll(bare[i]).mapNotNull { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            value?.let { Figure(line = i, price = it, height = tagLines[i].height, at = match.range.first) }
        }
        // Marks were stripped from `bare` one for one, so an offset found on `upper` is the same
        // offset here; the spaced form is read from the line that still has the marks.
        val raised = spacedCents(upper[i]).mapNotNull { match ->
            val value = "${match.groupValues[2]}.${match.groupValues[3]}".toDoubleOrNull()
            value?.let { Figure(line = i, price = it, height = tagLines[i].height, at = match.range.first) }
        }
        (withPoint + raised).sortedBy { it.at }
    }
    if (figures.isEmpty()) return null

    fun isRate(figure: Figure): Boolean {
        val line = upper[figure.line]
        // The offset belongs to the line the currency marks were stripped from, so the words in
        // front of the figure are read from that same line, not the raw one.
        if (RATE_BEFORE.containsMatchIn(bare[figure.line].substring(0, figure.at))) return true
        // The label often prints the words in the block and the number under them, so a lone figure
        // beneath "UNIT PRICE" is the rate as well. Every figure counts here, decimal point or
        // not: counting only the ones with a point let a spaced `$7 12` through as a retail price.
        val above = upper.getOrNull(figure.line - 1).orEmpty()
        return !PRICE_LINE.containsMatchIn(line) &&
            RATE_LINE.containsMatchIn(above) &&
            figures.count { it.line == figure.line } == 1
    }

    // "UNIT PRICE $7.12 PER PT" prices a pint, not the bottle on the shelf: a rate is never the
    // price someone pays.
    val priced = figures.filterNot(::isRate)
    val named = priced.filter { figure ->
        PRICE_LINE.containsMatchIn(upper[figure.line]) ||
            PRICE_LINE.containsMatchIn(upper.getOrNull(figure.line - 1).orEmpty())
    }
    val best = (named.ifEmpty { priced.ifEmpty { figures } }).maxByOrNull { it.height } ?: return null

    val ratePrice = figures.firstOrNull(::isRate)
    val unit = UNIT_WORD.find(upper.joinToString(" "))?.groupValues?.get(1)
    val rate = if (ratePrice != null && unit != null) {
        unitPriceOf(ratePrice.price, unit.lowercase())
    } else {
        null
    }

    // A pack size lives beside the name, not on a price or rate line, and only counts when the app
    // can parse it: "CAP 8" is a shelf capacity, not eight of anything.
    val printedPack = bare.indices.firstNotNullOfOrNull { i ->
        if (MONEY.containsMatchIn(bare[i]) ||
            spacedCents(upper[i]).any() ||
            RATE_LINE.containsMatchIn(upper[i])
        ) {
            return@firstNotNullOfOrNull null
        }
        PACK.find(bare[i])?.let { found ->
            val amount = found.groupValues[1].replace(',', '.')
            val name = found.groupValues[2].lowercase().replace(".", " ")
                .replace(Regex("\\s+"), " ").trim()
            "$amount $name".takeIf { parsePriceBasis(it) != null }
        }
    }

    // A tag that prints no size still states one: the photograph this parser was written from says
    // "$7.52" and "$7.12 PER PT" and nothing about the bottle, and those two figures divide to
    // 1.056 pt. Keeping it is what lets the row carry the label's unit price rather than only the
    // figure on the shelf edge.
    val pack = printedPack ?: packFromRate(best.price, rate)

    return ShelfTag(price = best.price, pack = pack, rate = rate)
}

/**
 * The size a tag's two figures imply: $7.52 at $7.12 a pint is 1.056 pt of olive oil.
 *
 * Three decimals because that is what it takes for the rate the app derives from the pack to land
 * back on the cents the label printed; a size that would not is a misread, and none is offered.
 */
private fun packFromRate(price: Double, rate: UnitPrice?): String? {
    val per = rate?.per ?: return null
    if (price <= 0.0 || per <= 0.0) return null
    val size = round(price / per * 1000) / 1000
    if (size < 0.05 || size > 1000.0) return null
    val back = round(price / size * 100) / 100
    if (back != round(per * 100) / 100) return null
    val amount = if (size % 1.0 == 0.0) {
        size.toLong().toString()
    } else {
        String.format(Locale.US, "%.3f", size).trimEnd('0').trimEnd('.')
    }
    return "$amount ${rate.label}"
}

/**
 * The spaced-out price on one line, when something on that line says the pair is money: either the
 * mark in front of it, or the words (`RETAIL PRICE 7 52`) when the camera dropped the mark.
 */
private fun spacedCents(line: String): Sequence<MatchResult> =
    SPACED_MONEY.findAll(line).filter { match ->
        match.groupValues[1].isNotEmpty() || PRICE_LINE.containsMatchIn(line)
    }

/**
 * The label joined back up where recognition split it along the price's own printing: raised cents
 * come back as their own line (`$7`, then `52`), or with a space after the point (`7. 52`).
 *
 * Only a marked figure is joined, so `FAC 1 CAP 8` and the `0302` under it stay two numbers.
 */
private fun joinSplitPrices(lines: List<TagLine>): List<TagLine> {
    val joined = ArrayList<TagLine>(lines.size)
    var i = 0
    while (i < lines.size) {
        val text = lines[i].text.trim().uppercase()
        val cents = lines.getOrNull(i + 1)?.text?.trim()
        if (OPEN_PRICE.containsMatchIn(text) && cents != null && CENTS_ONLY.matches(cents)) {
            val glue = if (cents.startsWith(".") || cents.startsWith(",")) "" else " "
            joined += TagLine(
                text = lines[i].text.trimEnd() + glue + cents,
                height = maxOf(lines[i].height, lines[i + 1].height),
            )
            i += 2
        } else {
            joined += lines[i]
            i++
        }
    }
    return joined.map { line ->
        TagLine(SPLIT_POINT.replace(line.text) { found -> found.groupValues[1] + "." }, line.height)
    }
}

/** One figure on the label, and where on which line it was printed. */
private data class Figure(val line: Int, val price: Double, val height: Int, val at: Int)
