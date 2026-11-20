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

    /** The thing being bought, and the version of it: "Milk" / "Full Fat". */
    data class Parsed(val name: String, val variant: String?)

    /**
     * Splits what was typed at the divider the shopper writes. Only the first slash counts, so a
     * type may itself contain one ("Oil / Olive / Extra Virgin"). A trailing or empty type is not a
     * type at all — "Milk /" stays as written rather than becoming an anonymous second milk.
     */
    fun parse(raw: String): Parsed {
        val parts = raw.trim().split(VARIANT_SEPARATOR, limit = 2)
        return if (parts.size == 2 && parts[1].isNotBlank()) {
            Parsed(parts[0].trim(), parts[1].trim())
        } else {
            Parsed(raw.trim(), null)
        }
    }

    /**
     * What makes a product a product: its name AND its type. Comparing the name alone would let
     * "Milk / Skimmed" answer for "Milk / Full Fat", which is the exact thing a type is for.
     *
     * The name is parsed here too, so a row written before types had a field — with " / " still
     * inside its name — hashes the same as a freshly typed one and is not duplicated. An explicit
     * [variant] on the row always wins over a slash inside the name.
     */
    fun productKey(name: String, variant: String?): String {
        val parsed = parse(name)
        return key(parsed.name) + "\u0000" + key(variant ?: parsed.variant.orEmpty())
    }

    /** The identity of a single typed add, before it is split into two columns. */
    fun productKey(raw: String): String {
        val parsed = parse(raw)
        return productKey(parsed.name, parsed.variant)
    }

    /**
     * The row already standing for what is being added, if any. Most recently updated wins, so a
     * shelf and a basket row both claiming the same product revive the one edited last.
     */
    fun findExisting(candidates: List<Item>, raw: String): Item? {
        val wanted = productKey(raw)
        return candidates.filter { productKey(it.name, it.variant) == wanted }
            .maxByOrNull { it.updatedAt }
    }

    private val WHITESPACE = Regex("\\s+")
    private val VARIANT_SEPARATOR = Regex("\\s*/\\s*")
}

/**
 * Shelf rows that already answer for what is being typed, so it can be swiped back onto the list
 * instead of a near-duplicate being created. Name matching is deliberately loose — a partial word,
 * any case, or the same normalised key — because the point is to catch "milk" before "semi skimmed
 * milk 2L" becomes two rows. Once a type is typed after the slash, that type must also match: a
 * "milk / full" request should not be answered by the skimmed row.
 */
fun shelfMatches(shelf: List<Item>, draft: String, limit: Int = 3): List<Item> {
    val text = draft.trim()
    if (text.isEmpty()) return emptyList()
    val needle = text.lowercase()
    val parsed = ItemNames.parse(text)
    val nameNeedle = ItemNames.key(parsed.name)
    val variantNeedle = parsed.variant?.let(ItemNames::key)
    return shelf
        .filter { item ->
            val name = item.name.lowercase()
            val variant = item.variant?.lowercase().orEmpty()
            val whole = "$name $variant".trim()
            when {
                variantNeedle != null ->
                    (ItemNames.key(item.name).contains(nameNeedle) &&
                        variant.contains(variantNeedle)) || whole.contains(needle)

                else ->
                    name.contains(needle) || variant.contains(needle) ||
                        ItemNames.key(item.name) == ItemNames.key(text)
            }
        }
        .sortedBy { it.name.lowercase() }
        .take(limit)
}
