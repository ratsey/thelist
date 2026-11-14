package com.ratsey.thelist.data

import java.util.Locale

/**
 * How item names are compared when deciding whether something is already on the list.
 *
 * Deliberately only case, padding and repeated spaces: "Milk" and "milk" are the same errand,
 * but "Milk" and "Oat milk" are not, and nothing here should guess at brands or sizes.
 */
object ItemNames {

    fun key(raw: String): String =
        raw.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)

    private val WHITESPACE = Regex("\\s+")
}

/**
 * Shelf rows that already answer for what is being typed, so it can be swiped back onto the list
 * instead of a near-duplicate being created. Name matching is deliberately loose — a partial word,
 * any case, or the same normalised key — because the point is to catch "milk" before "semi skimmed
 * milk 2L" becomes two rows.
 */
fun shelfMatches(shelf: List<Item>, draft: String, limit: Int = 3): List<Item> {
    val text = draft.trim()
    if (text.isEmpty()) return emptyList()
    val needle = text.lowercase()
    val key = ItemNames.key(text)
    return shelf
        .filter { item ->
            val name = item.name.lowercase()
            name.contains(needle) || ItemNames.key(item.name) == key
        }
        .sortedBy { it.name.lowercase() }
        .take(limit)
}
