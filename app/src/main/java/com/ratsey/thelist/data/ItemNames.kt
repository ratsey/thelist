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
