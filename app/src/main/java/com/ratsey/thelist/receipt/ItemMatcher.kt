package com.ratsey.thelist.receipt

import com.ratsey.thelist.data.Item
import java.util.Locale

/**
 * Maps a receipt line onto a list item.
 *
 * Receipt names are long and noisy ("TESCO FINEST SEMI SKIMMED MILK 2L"), list items are
 * short ("Milk"), so the score measures how much of the *item* appears in the line rather
 * than the other way round.
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

    fun match(lineName: String, items: List<Item>): Item? {
        val lineTokens = tokenize(lineName).toSet()
        if (lineTokens.isEmpty()) return null

        return items
            .map { item -> item to score(tokenize(item.name).toSet(), lineTokens) }
            .filter { it.second >= MATCH_THRESHOLD }
            .maxWithOrNull(
                compareBy({ it.second }, { tokenize(it.first.name).size }),
            )
            ?.first
    }

    fun score(itemTokens: Set<String>, lineTokens: Set<String>): Double {
        if (itemTokens.isEmpty() || lineTokens.isEmpty()) return 0.0
        if (lineTokens.containsAll(itemTokens)) return 1.0
        if (itemTokens.containsAll(lineTokens)) return 0.85
        val intersection = itemTokens.intersect(lineTokens).size.toDouble()
        val union = itemTokens.union(lineTokens).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private const val MATCH_THRESHOLD = 0.75
}
