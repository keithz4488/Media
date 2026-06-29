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

    // bottom vignette to ground the scene
    drawRect(
        brush = Brush.verticalGradient(
            0.8f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.18f),
        ),
        size = size,
    )
}

/**
 * Draws a single wooden shelf board with real depth: a soft contact shadow where the covers
 * meet the shelf, a lit top surface with a bright front lip, a tall front face with vertical
 * grain, and a drop shadow cast below onto the wall.
 */
fun DrawScope.drawPlank(
    flavor: ShelfFlavor,
    topY: Float,
    left: Float,
    right: Float,
    topThickness: Float,
    frontThickness: Float,
) {
    val width = right - left

    // 1) contact shadow: a soft dark band just above the shelf, so covers look like they
    //    rest on it rather than float over a bar.
    val contactH = topThickness * 1.6f
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.30f),
            startY = topY - contactH,
            endY = topY,
        ),
        topLeft = Offset(left, topY - contactH),
        size = Size(width, contactH),
    )

    // 2) top surface of the board, lighter toward the front edge where light lands
    drawRect(
        brush = Brush.verticalGradient(
            0f to lerp(flavor.plank, Color.Black, 0.18f),   // back of the surface, shaded
            1f to lerp(flavor.plank, Color.White, 0.18f),   // front of the surface, lit
            startY = topY,
            endY = topY + topThickness,
        ),
        topLeft = Offset(left, topY),
        size = Size(width, topThickness),
    )
    // bright lip line along the very front-top edge
    drawRect(
        color = lerp(flavor.plank, Color.White, 0.40f),
        topLeft = Offset(left, topY + topThickness - 1.5f),
        size = Size(width, 2f),
    )

    // 3) front face of the board (the thickness you see), darker, with vertical grain ticks
    val faceTop = topY + topThickness
    drawRect(
        brush = Brush.verticalGradient(
            0f to flavor.plank,
            1f to flavor.plankEdge,
            startY = faceTop,
            endY = faceTop + frontThickness,
        ),
        topLeft = Offset(left, faceTop),
        size = Size(width, frontThickness),
    )
    val grain = lerp(flavor.plankEdge, Color.Black, 0.25f)
    var gx = left + 18f
    var k = 0
    while (gx < right) {
        val h = if (k % 2 == 0) frontThickness * 0.55f else frontThickness * 0.35f
        drawRect(
            color = grain.copy(alpha = 0.35f),
            topLeft = Offset(gx, faceTop + (frontThickness - h) / 2f),
            size = Size(1.5f, h),
        )
        gx += 34f
        k++
    }

    // 4) drop shadow under the board, onto the wall below
    val shadowH = frontThickness * 1.2f
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.28f),
            1f to Color.Transparent,
            startY = faceTop + frontThickness,
            endY = faceTop + frontThickness + shadowH,
        ),
        topLeft = Offset(left, faceTop + frontThickness),
        size = Size(width, shadowH),
    )
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
