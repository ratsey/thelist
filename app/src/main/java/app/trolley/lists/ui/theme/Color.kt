package app.trolley.lists.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Warm paper canvas + a deep garden green. Deliberately not dynamic colour: a shopping list
 * should feel like the same app on every phone.
 */
val LightColors = lightColorScheme(
    primary = Color(0xFF14724A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9F2E3),
    onPrimaryContainer = Color(0xFF05291A),
    secondary = Color(0xFF4C6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE9DF),
    onSecondaryContainer = Color(0xFF0F1F15),
    tertiary = Color(0xFFB4523A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD1),
    onTertiaryContainer = Color(0xFF3B0900),
    background = Color(0xFFFBFAF6),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFEFEDE6),
    onSurfaceVariant = Color(0xFF6B7069),
    outline = Color(0xFFBBBDB4),
    outlineVariant = Color(0xFFE6E4DB),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF2E312F),
    inverseOnSurface = Color(0xFFF1F1EC),
    inversePrimary = Color(0xFF7DDCA6),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF7DDCA6),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0C5233),
    onPrimaryContainer = Color(0xFFD9F2E3),
    secondary = Color(0xFFB3CCB9),
    onSecondary = Color(0xFF1F3528),
    secondaryContainer = Color(0xFF354B3D),
    onSecondaryContainer = Color(0xFFCFE8D4),
    tertiary = Color(0xFFFFB59F),
    onTertiary = Color(0xFF5F1604),
    tertiaryContainer = Color(0xFF7E2C17),
    onTertiaryContainer = Color(0xFFFFDAD1),
    background = Color(0xFF101312),
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF191C1A),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF242825),
    onSurfaceVariant = Color(0xFFA8AEA7),
    outline = Color(0xFF4A4F4B),
    outlineVariant = Color(0xFF2C302D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE2E3DE),
    inverseOnSurface = Color(0xFF191C1A),
    inversePrimary = Color(0xFF14724A),
)
