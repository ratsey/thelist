package com.ratsey.thelist.ui

import androidx.compose.runtime.compositionLocalOf
import com.ratsey.thelist.data.DEFAULT_CURRENCY

/**
 * The money the app writes in, provided once at the root from the region settings. Money that was
 * actually paid keeps the currency on its receipt; this is only for figures being typed now.
 */
val LocalCurrency = compositionLocalOf { DEFAULT_CURRENCY }
