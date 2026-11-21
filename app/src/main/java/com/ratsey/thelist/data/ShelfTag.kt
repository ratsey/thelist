package com.ratsey.thelist.data

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
    /** The label's own rate, shown beside the figure so the two numbers can be held against each other. */
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
 * with a space where the decimal point is, as `$7 52`.
 *
 * The currency mark has to be there. Two numbers side by side are ordinary on a shelf label —
 * `FAC 1 CAP 8 0302` — and only the mark says the pair is money.
 */
private val SPACED_MONEY = Regex("""[${'$'}S§]\s?([0-9]{1,4})\s+([0-9]{2})(?![0-9])""")

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
 * - the **rate** is read but not stored: it is the label's own arithmetic, shown so a shopper can
 *   see whether it agrees with the pack size.
 *
 * Null when there is no price at all: "nothing readable" is better than a guess.
 */
fun readShelfTag(lines: List<TagLine>): ShelfTag? {
    if (lines.isEmpty()) return null
    val upper = lines.map { it.text.uppercase() }
    val bare = upper.map { MONEY_MARK.replace(it, " ") }

    // Every figure on the label, and where it sits: a narrow tag can come back as one line holding
    // the price and the rate, so the words in front of a figure are what tell the two apart.
    val figures = bare.indices.flatMap { i ->
        val withPoint = MONEY.findAll(bare[i]).mapNotNull { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            value?.let { Figure(line = i, price = it, height = lines[i].height, at = match.range.first) }
        }
        // Marks were stripped from `bare` one for one, so an offset found on `upper` is the same
        // offset here; the spaced form needs the mark, so it is read from the line that still has it.
        val raised = SPACED_MONEY.findAll(upper[i]).mapNotNull { match ->
            val value = "${match.groupValues[1]}.${match.groupValues[2]}".toDoubleOrNull()
            value?.let { Figure(line = i, price = it, height = lines[i].height, at = match.range.first) }
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
    val pack = bare.indices.firstNotNullOfOrNull { i ->
        if (MONEY.containsMatchIn(bare[i]) ||
            SPACED_MONEY.containsMatchIn(upper[i]) ||
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

    return ShelfTag(price = best.price, pack = pack, rate = rate)
}

/** One figure on the label, and where on which line it was printed. */
private data class Figure(val line: Int, val price: Double, val height: Int, val at: Int)
