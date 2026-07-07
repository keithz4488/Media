package com.kzaller.shelf.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp

/** Deep synthwave sky: near-black navy up top fading into purple toward the horizon. */
fun synthwaveSky() = Brush.verticalGradient(
    colors = listOf(Color(0xFF07031A), Color(0xFF140A38), Color(0xFF2A0F52)),
)

/**
 * A slow synthwave perspective grid that rolls toward the viewer: deep-purple sky, a glowing
 * cyan-white horizon, magenta converging rails, and horizontal lines that shift cyan→magenta as
 * they scroll forward and wrap at the horizon (fading in there, which hides the loop). Plus a
 * few cubes drifting in the sky. Shared by the home, stats, and achievements screens.
 */
@Composable
fun SynthwaveBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wall")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)),
        label = "phase",
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(17000, easing = LinearEasing)),
        label = "drift",
    )
    val cyan = Color(0xFF34E0FF)
    val magenta = Color(0xFFFF3DBE)
    val glow = Color(0xFFCFF6FF)

    Canvas(modifier = modifier) {
        drawRect(brush = synthwaveSky())
        val horizon = size.height * 0.42f

        // Drifting cubes in the sky (simple outlined squares that float upward and wrap).
        val cubes = listOf(0.14f to 0.30f, 0.38f to 0.14f, 0.62f to 0.24f, 0.82f to 0.12f, 0.28f to 0.20f, 0.72f to 0.34f)
        cubes.forEachIndexed { i, (fx, fy) ->
            val prog = (drift + i * 0.16f) % 1f
            val y = (fy - prog * 0.12f + 0.12f) % 0.42f * size.height
            val s = size.width * (0.018f + 0.01f * (i % 3))
            val x = fx * size.width
            drawRect(
                color = (if (i % 2 == 0) cyan else magenta).copy(alpha = 0.22f),
                topLeft = Offset(x, y),
                size = Size(s, s),
                style = Stroke(width = 1.5f),
            )
        }

        // Glow pooled at the horizon + a bright horizon line.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(size.width / 2f, horizon),
                radius = size.width * 0.85f,
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width / 2f, horizon),
        )
        drawLine(glow.copy(alpha = 0.85f), Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 3f)

        // Converging magenta rails toward the vanishing point.
        val cx = size.width / 2f
        val rails = 12
        for (i in -rails..rails) {
            val bottomX = cx + (i.toFloat() / rails) * size.width * 1.6f
            drawLine(magenta.copy(alpha = 0.30f), Offset(cx, horizon), Offset(bottomX, size.height), strokeWidth = 1.5f)
        }

        // Horizontal lines rolling forward, shifting cyan (far) -> magenta (near).
        val rows = 18
        for (i in 0 until rows) {
            val t = ((i + phase) % rows) / rows           // 0 at horizon -> 1 at viewer
            val y = horizon + (size.height - horizon) * (t * t)
            val color = lerp(cyan, magenta, t)
            drawLine(color.copy(alpha = (0.15f + t * 0.55f).coerceAtMost(0.7f)), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.6f)
        }
    }
}
