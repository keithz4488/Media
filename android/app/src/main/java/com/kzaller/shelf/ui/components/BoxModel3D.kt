package com.kzaller.shelf.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import com.kzaller.shelf.data.MediaKind
import kotlinx.coroutines.launch

private fun lighten(c: Color, t: Float) = Color(
    red = c.red + (1f - c.red) * t,
    green = c.green + (1f - c.green) * t,
    blue = c.blue + (1f - c.blue) * t,
    alpha = c.alpha,
)

private fun darken(c: Color, t: Float) = Color(
    red = c.red * (1f - t),
    green = c.green * (1f - t),
    blue = c.blue * (1f - t),
    alpha = c.alpha,
)

/**
 * A rotatable 3D case built from three perspective-projected faces (front, right spine, top)
 * hinged at the front face's edges via graphicsLayer 3D rotations + cameraDistance. Drag to
 * spin; it springs back to a 3/4 resting pose on release. Detail-page only.
 *
 * Note: this is a real 3D projection (not a fake tilt), but proportions/limits are tuned by
 * eye and may want adjustment per device.
 */
@Composable
fun BoxModel3D(
    coverUrl: String?,
    title: String,
    kind: MediaKind,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    // Resting 3/4 view; drag adjusts from here. A bit more turn so the spine/top read.
    val restYaw = -34f   // negative so the right spine faces the viewer
    val restPitch = -12f
    val yaw = remember { Animatable(restYaw) }
    val pitch = remember { Animatable(restPitch) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Camera distance must be LARGE relative to the cover (~700px), or geometry projects
    // behind the camera and collapses. ~900dp*density keeps perspective gentle but present.
    val cam = density.density * 900f

    // Depth of the case as a fraction of the front width, per kind. Beefed up so the box
    // clearly reads as a 3D case rather than a sheet.
    val depthFraction = when (kind) {
        MediaKind.BOOK -> 0.20f      // chunky hardcover
        MediaKind.GAME -> 0.16f      // game box
        else -> 0.12f                // DVD / Blu-ray case
    }
    // A solid case-edge color (darkened accent) reads better than stretching cover art onto
    // the thin spine/top faces.
    val edgeColor = Color(
        red = accent.red * 0.35f,
        green = accent.green * 0.35f,
        blue = accent.blue * 0.35f,
        alpha = 1f,
    )
    val aspect = if (kind == MediaKind.GAME) 3f / 4f else 2f / 3f

    val painter = rememberAsyncImagePainter(model = coverUrl)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val frontW: Dp = maxWidth
        val frontH: Dp = frontW / aspect
        val depth: Dp = frontW * depthFraction

        Box(
            modifier = Modifier
                .size(frontW, frontH)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch { yaw.animateTo(restYaw) }
                            scope.launch { pitch.animateTo(restPitch) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            yaw.snapTo((yaw.value + dragAmount.x * 0.35f).coerceIn(-72f, 72f))
                            pitch.snapTo((pitch.value - dragAmount.y * 0.30f).coerceIn(-55f, 25f))
                        }
                    }
                }
                .graphicsLayer {
                    rotationY = yaw.value
                    rotationX = pitch.value
                    cameraDistance = cam
                },
        ) {
            // ---- Top face: hinged at the front's top edge, swung back/up. Solid case edge,
            // lit a touch lighter than the spine since it faces up toward the light.
            Box(
                modifier = Modifier
                    .size(frontW, depth)
                    .offset(y = -depth)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f) // hinge at bottom edge
                        rotationX = -90f
                        cameraDistance = cam
                    }
                    .background(
                        Brush.verticalGradient(
                            0f to lighten(edgeColor, 0.18f),
                            1f to edgeColor,
                        ),
                    ),
            )

            // ---- Right spine: hinged at the front's right edge, swung back. Solid case edge
            // with a soft vertical sheen so it looks molded.
            Box(
                modifier = Modifier
                    .size(depth, frontH)
                    .offset(x = frontW)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f) // hinge at left edge
                        rotationY = 90f
                        cameraDistance = cam
                    }
                    .background(
                        Brush.horizontalGradient(
                            0f to lighten(edgeColor, 0.12f),
                            1f to darken(edgeColor, 0.25f),
                        ),
                    ),
            )

            // ---- Front face.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
                if (coverUrl != null) {
                    Image(
                        painter = painter,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // subtle sheen so the flat front reads as a glossy case face
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.10f),
                            0.4f to Color.Transparent,
                        ),
                    ),
                )
            }
        }
    }
}
