package com.kzaller.shelf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kzaller.shelf.data.MediaKind

/**
 * A "shelf flavor" bundles the material colors with extra knobs we need for the per-shelf vibe:
 * a background gradient, an accent color, and a card style.
 */
data class ShelfFlavor(
    val name: String,
    val materialDark: androidx.compose.material3.ColorScheme,
    val materialLight: androidx.compose.material3.ColorScheme,
    val backgroundBrush: Brush,
    val accent: Color,
    val onAccent: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val displayFont: FontFamily,
    val titleStyle: TextStyle,
    val ornament: ShelfOrnament,
)

enum class ShelfOrnament { WOOD_PLANKS, FILM_STRIP, SCANLINES, NEON_GRID, NONE }

val LocalShelfFlavor = staticCompositionLocalOf<ShelfFlavor> { error("ShelfFlavor not provided") }

private fun titleStyleFor(family: FontFamily, italic: Boolean = false, weight: FontWeight = FontWeight.Bold) =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = 28.sp,
        letterSpacing = 0.sp,
    )

// ---------------------------------------------------------------- Books: warm library
private val BooksLight = lightColorScheme(
    primary       = Color(0xFF7A4B26),
    onPrimary     = Color(0xFFFFFBF2),
    secondary     = Color(0xFFA8732B),
    background    = Color(0xFFF5E9D2),
    onBackground  = Color(0xFF2A1B0E),
    surface       = Color(0xFFEBD9B7),
    onSurface     = Color(0xFF2A1B0E),
    surfaceVariant= Color(0xFFD9C49A),
)
private val BooksDark = darkColorScheme(
    primary       = Color(0xFFE5C07B),
    onPrimary     = Color(0xFF2A1B0E),
    secondary     = Color(0xFFC4914B),
    background    = Color(0xFF1B120A),
    onBackground  = Color(0xFFF1E2C3),
    surface       = Color(0xFF2A1C10),
    onSurface     = Color(0xFFF1E2C3),
    surfaceVariant= Color(0xFF3A2818),
)

// ---------------------------------------------------------------- Movies: cinema noir
private val MoviesLight = lightColorScheme(
    primary       = Color(0xFFB7253A),
    onPrimary     = Color(0xFFFFFBF2),
    secondary     = Color(0xFFC9A227),
    background    = Color(0xFFF5EFE6),
    onBackground  = Color(0xFF1A0E10),
    surface       = Color(0xFFEDE3D2),
    onSurface     = Color(0xFF1A0E10),
    surfaceVariant= Color(0xFFD9C9B5),
)
private val MoviesDark = darkColorScheme(
    primary       = Color(0xFFE5404F),
    onPrimary     = Color(0xFFFFFFFF),
    secondary     = Color(0xFFE4C46B),
    background    = Color(0xFF0D0608),
    onBackground  = Color(0xFFEDE0D0),
    surface       = Color(0xFF1B1012),
    onSurface     = Color(0xFFEDE0D0),
    surfaceVariant= Color(0xFF2A1A1E),
)

// ---------------------------------------------------------------- TV: retro CRT phosphor
private val TvLight = lightColorScheme(
    primary       = Color(0xFF1F7A3A),
    onPrimary     = Color(0xFFE8FFEA),
    secondary     = Color(0xFFC78A1C),
    background    = Color(0xFFE8F5E9),
    onBackground  = Color(0xFF071A0E),
    surface       = Color(0xFFD8EBD8),
    onSurface     = Color(0xFF071A0E),
    surfaceVariant= Color(0xFFB7D5B7),
)
private val TvDark = darkColorScheme(
    primary       = Color(0xFF52FF8A),
    onPrimary     = Color(0xFF051A0C),
    secondary     = Color(0xFFFFC857),
    background    = Color(0xFF050E08),
    onBackground  = Color(0xFFB7FFC8),
    surface       = Color(0xFF0A1A12),
    onSurface     = Color(0xFFB7FFC8),
    surfaceVariant= Color(0xFF112418),
)

// ---------------------------------------------------------------- Games: arcade neon
private val GamesLight = lightColorScheme(
    primary       = Color(0xFFD61F8C),
    onPrimary     = Color(0xFFFFFFFF),
    secondary     = Color(0xFF1AA8C7),
    background    = Color(0xFFF1E8FA),
    onBackground  = Color(0xFF120B22),
    surface       = Color(0xFFE2D4F2),
    onSurface     = Color(0xFF120B22),
    surfaceVariant= Color(0xFFC8B6E0),
)
private val GamesDark = darkColorScheme(
    primary       = Color(0xFFFF3DBE),
    onPrimary     = Color(0xFF120B22),
    secondary     = Color(0xFF34E0FF),
    background    = Color(0xFF080414),
    onBackground  = Color(0xFFE9DCFF),
    surface       = Color(0xFF120A26),
    onSurface     = Color(0xFFE9DCFF),
    surfaceVariant= Color(0xFF1F1142),
)

private fun booksFlavor(dark: Boolean): ShelfFlavor {
    val cs = if (dark) BooksDark else BooksLight
    return ShelfFlavor(
        name = "Books",
        materialDark = BooksDark,
        materialLight = BooksLight,
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(cs.background, cs.surface),
        ),
        accent = cs.primary,
        onAccent = cs.onPrimary,
        cardSurface = cs.surface,
        cardBorder = cs.surfaceVariant,
        displayFont = FontFamily.Serif,
        titleStyle = titleStyleFor(FontFamily.Serif, italic = false, weight = FontWeight.Bold),
        ornament = ShelfOrnament.WOOD_PLANKS,
    )
}

private fun moviesFlavor(dark: Boolean): ShelfFlavor {
    val cs = if (dark) MoviesDark else MoviesLight
    return ShelfFlavor(
        name = "Movies",
        materialDark = MoviesDark,
        materialLight = MoviesLight,
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF050203), cs.background),
        ),
        accent = cs.primary,
        onAccent = cs.onPrimary,
        cardSurface = cs.surface,
        cardBorder = cs.secondary.copy(alpha = 0.6f),
        displayFont = FontFamily.Serif,
        titleStyle = titleStyleFor(FontFamily.Serif, italic = true, weight = FontWeight.Black),
        ornament = ShelfOrnament.FILM_STRIP,
    )
}

private fun tvFlavor(dark: Boolean): ShelfFlavor {
    val cs = if (dark) TvDark else TvLight
    return ShelfFlavor(
        name = "TV",
        materialDark = TvDark,
        materialLight = TvLight,
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF020A05), cs.background),
        ),
        accent = cs.primary,
        onAccent = cs.onPrimary,
        cardSurface = cs.surface,
        cardBorder = cs.primary.copy(alpha = 0.4f),
        displayFont = FontFamily.Monospace,
        titleStyle = titleStyleFor(FontFamily.Monospace, italic = false, weight = FontWeight.Bold).copy(letterSpacing = 2.sp),
        ornament = ShelfOrnament.SCANLINES,
    )
}

private fun gamesFlavor(dark: Boolean): ShelfFlavor {
    val cs = if (dark) GamesDark else GamesLight
    return ShelfFlavor(
        name = "Games",
        materialDark = GamesDark,
        materialLight = GamesLight,
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF03010A), cs.background),
        ),
        accent = cs.primary,
        onAccent = cs.onPrimary,
        cardSurface = cs.surface,
        cardBorder = cs.secondary.copy(alpha = 0.7f),
        displayFont = FontFamily.SansSerif,
        titleStyle = titleStyleFor(FontFamily.SansSerif, weight = FontWeight.Black).copy(letterSpacing = 1.sp),
        ornament = ShelfOrnament.NEON_GRID,
    )
}

fun flavorFor(kind: MediaKind, dark: Boolean): ShelfFlavor = when (kind) {
    MediaKind.BOOK  -> booksFlavor(dark)
    MediaKind.MOVIE -> moviesFlavor(dark)
    MediaKind.TV    -> tvFlavor(dark)
    MediaKind.GAME  -> gamesFlavor(dark)
}

// Neutral home/app theme used when no specific shelf is in scope.
private val HomeDark = darkColorScheme(
    primary = Color(0xFFE5C07B),
    secondary = Color(0xFFFF6B6B),
    background = Color(0xFF0E0E10),
    surface = Color(0xFF161618),
    onBackground = Color(0xFFE8E8EA),
    onSurface = Color(0xFFE8E8EA),
)
private val HomeLight = lightColorScheme(
    primary = Color(0xFF7A4B26),
    secondary = Color(0xFFB7253A),
    background = Color(0xFFFFFAF1),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1410),
    onSurface = Color(0xFF1A1410),
)

@Composable
fun MediaShelfTheme(
    flavor: ShelfFlavor? = null,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val effective = flavor ?: ShelfFlavor(
        name = "Home",
        materialDark = HomeDark,
        materialLight = HomeLight,
        backgroundBrush = Brush.verticalGradient(
            if (dark) listOf(Color(0xFF0B0B0E), Color(0xFF14141A))
            else listOf(Color(0xFFFFFAF1), Color(0xFFEFE6D2))
        ),
        accent = if (dark) Color(0xFFE5C07B) else Color(0xFF7A4B26),
        onAccent = Color.Black,
        cardSurface = if (dark) Color(0xFF161618) else Color(0xFFFFFFFF),
        cardBorder = if (dark) Color(0xFF2A2A30) else Color(0xFFE6DCC4),
        displayFont = FontFamily.SansSerif,
        titleStyle = titleStyleFor(FontFamily.SansSerif),
        ornament = ShelfOrnament.NONE,
    )

    val scheme = if (dark) effective.materialDark else effective.materialLight
    val typography = MaterialTheme.typography.copy(
        headlineMedium = effective.titleStyle,
    )
    CompositionLocalProvider(LocalShelfFlavor provides effective) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
