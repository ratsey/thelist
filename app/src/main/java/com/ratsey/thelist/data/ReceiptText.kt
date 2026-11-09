package com.ratsey.thelist.data

import java.util.Locale

/**
 * Receipt lines arrive in capitals with the size stuck on the end ("TESCO FINEST COLOMBIAN
 * COFFEE 227G"). All-caps text is lowered and title-cased for display; anything already mixed
 * case was written that way on purpose and is left alone.
 */
fun prettyReceiptName(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.none { it.isLowerCase() }) {
        trimmed.lowercase(Locale.getDefault())
            .split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.getDefault()) } }
    } else {
        trimmed
    }
}
