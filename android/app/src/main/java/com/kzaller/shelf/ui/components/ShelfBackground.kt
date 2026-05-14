package com.kzaller.shelf.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kzaller.shelf.ui.theme.LocalShelfFlavor
import com.kzaller.shelf.ui.theme.ShelfOrnament

/**
 * Paints the per-shelf gradient + a subtle ornament (wood planks, scanlines, etc.)
 * Children are rendered on top.
 */
@Composable
fun ShelfBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val flavor = LocalShelfFlavor.current
    Box(modifier = modifier.fillMaxSize().background(flavor.backgroundBrush)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (flavor.ornament) {
                ShelfOrnament.WOOD_PLANKS -> drawWood(flavor.cardBorder.copy(alpha = 0.25f))
                ShelfOrnament.SCANLINES   -> drawScanlines(flavor.accent.copy(alpha = 0.07f))
                ShelfOrnament.NEON_GRID   -> drawNeonGrid(flavor.accent.copy(alpha = 0.18f), flavor.cardBorder.copy(alpha = 0.18f))
                ShelfOrnament.FILM_STRIP  -> drawFilmStrip(flavor.cardBorder.copy(alpha = 0.5f))
                ShelfOrnament.NONE        -> Unit
            }
        }
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWood(line: Color) {
    val plankH = 96f
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        y += plankH
    }
    // a few vertical grain marks
    val grain = listOf(0.18f, 0.42f, 0.71f, 0.88f)
    for (g in grain) {
        val x = size.width * g
        drawLine(line.copy(alpha = line.alpha * 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.8f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScanlines(line: Color) {
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += 3f
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeonGrid(major: Color, minor: Color) {
    val step = 48f
    var x = 0f
    while (x < size.width) {
        drawLine(minor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = size.height
    var count = 0
    while (y > 0) {
        val color = if (count % 4 == 0) major else minor
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = if (count % 4 == 0) 1.5f else 1f)
        y -= step * 0.8f
        count++
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFilmStrip(color: Color) {
    val stripW = 36f
    val sprocketH = 18f
    val sprocketW = 14f
    val gap = 26f
    listOf(0f, size.width - stripW).forEach { x ->
        drawRect(color.copy(alpha = color.alpha * 0.6f), topLeft = Offset(x, 0f), size = Size(stripW, size.height))
        var y = 12f
        while (y < size.height) {
            drawRect(
                Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(x + (stripW - sprocketW) / 2, y),
                size = Size(sprocketW, sprocketH),
            )
            y += sprocketH + gap
        }
    }
    // dashed center line
    drawLine(
        color.copy(alpha = color.alpha * 0.25f),
        Offset(size.width / 2, 0f),
        Offset(size.width / 2, size.height),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f), 0f),
    )
}
