package com.kzaller.shelf.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kzaller.shelf.ui.theme.LocalShelfFlavor
import com.kzaller.shelf.ui.theme.ShelfFlavor

/**
 * Cibby-style warm wood backdrop, tinted per shelf flavor. Procedural (no image asset):
 * a vertical wood gradient, faint horizontal grain streaks, a couple of knots, and a soft
 * top-down light vignette so it reads like a sunlit wooden room.
 */
@Composable
fun WoodBackground(
    modifier: Modifier = Modifier,
    flavor: ShelfFlavor = LocalShelfFlavor.current,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawWoodRoom(flavor) }
        content()
    }
}

private fun DrawScope.drawWoodRoom(flavor: ShelfFlavor) {
    // base vertical wood gradient
    drawRect(
        brush = Brush.verticalGradient(
            0f to flavor.woodTop,
            0.55f to lerp(flavor.woodTop, flavor.woodBottom, 0.55f),
            1f to flavor.woodBottom,
        ),
        size = size,
    )

    // soft top light: a lighter wash near the top that fades out, like a window above
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.10f),
            0.35f to Color.Transparent,
        ),
        size = size,
    )

    // faint horizontal grain streaks
    val streakColor = Color.Black.copy(alpha = 0.05f)
    val streakHi = Color.White.copy(alpha = 0.04f)
    val grainLines = 26
    val step = size.height / grainLines
    var i = 0
    var y = step * 0.5f
    while (y < size.height) {
        // slight vertical wobble per line so they aren't ruler-straight
        val wobble = ((i % 3) - 1) * 4f
        drawLine(
            color = if (i % 2 == 0) streakColor else streakHi,
            start = Offset(0f, y + wobble),
            end = Offset(size.width, y - wobble),
            strokeWidth = if (i % 5 == 0) 2.2f else 1.2f,
        )
        y += step
        i++
    }

    // a couple of soft knots for character
    drawKnot(Offset(size.width * 0.22f, size.height * 0.30f), size.width * 0.05f)
    drawKnot(Offset(size.width * 0.78f, size.height * 0.66f), size.width * 0.04f)

    // bottom vignette to ground the scene
    drawRect(
        brush = Brush.verticalGradient(
            0.8f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.18f),
        ),
        size = size,
    )
}

private fun DrawScope.drawKnot(center: Offset, radius: Float) {
    drawCircle(Color.Black.copy(alpha = 0.10f), radius = radius, center = center)
    drawCircle(Color.Black.copy(alpha = 0.08f), radius = radius * 0.6f, center = center)
    drawCircle(Color.White.copy(alpha = 0.04f), radius = radius * 0.25f, center = center)
}

/** Draws a single wooden shelf plank with a 3D front edge, spanning the given width band. */
fun DrawScope.drawPlank(
    flavor: ShelfFlavor,
    topY: Float,
    left: Float,
    right: Float,
    topThickness: Float,
    frontThickness: Float,
) {
    val width = right - left
    // top face
    drawRect(
        brush = Brush.verticalGradient(
            0f to lerp(flavor.plank, Color.White, 0.10f),
            1f to flavor.plank,
            startY = topY,
            endY = topY + topThickness,
        ),
        topLeft = Offset(left, topY),
        size = Size(width, topThickness),
    )
    // front edge (darker, the lip the covers sit in front of)
    drawRect(
        brush = Brush.verticalGradient(
            0f to flavor.plank,
            1f to flavor.plankEdge,
            startY = topY + topThickness,
            endY = topY + topThickness + frontThickness,
        ),
        topLeft = Offset(left, topY + topThickness),
        size = Size(width, frontThickness),
    )
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
