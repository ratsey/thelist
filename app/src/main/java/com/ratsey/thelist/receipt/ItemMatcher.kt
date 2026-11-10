package com.ratsey.thelist.receipt

import java.util.Locale

/**
 * Turns an item name into the identity price history is keyed by, so "Semi Skimmed Milk 2L" and
 * "semi skimmed milk 2l" are the same product while a 1L carton stays a different one.
 *
 * This also used to map receipt lines onto list items. That job went with the camera, and only the
 * key was worth keeping: the price history, the last/best on a row and the chart all hang off it.
 */
object ItemMatcher {

    private val NON_WORD = Regex("""[^a-z0-9\s]""")
    private val SPACES = Regex("""\s+""")

    private val NOISE = setOf(
        "the", "fresh", "large", "small", "medium", "each", "organic", "pack", "tin",
        "bottle", "bag", "box", "of", "and", "free", "range", "finest", "own", "brand",
        "value", "family", "size", "kg", "g", "ml", "l", "pk", "x", "ea", "single",
        "double", "premium", "classic", "original", "new",
    )

    /** Stable product identity used for price history. */
    fun productKey(name: String): String =
        tokenize(name).sorted().joinToString("-").ifBlank { name.lowercase(Locale.UK).trim() }

    fun tokenize(text: String): List<String> =
        text.lowercase(Locale.UK)
            .replace(NON_WORD, " ")
            .split(SPACES)
            .filter { it.isNotBlank() && it.length > 1 && it !in NOISE }
            .map { token -> if (token.length > 3 && token.endsWith("s")) token.dropLast(1) else token }
            .distinct()
}
