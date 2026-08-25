@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.kzaller.shelf.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The scopes a shared cover needs to fly between screens. They're provided as locals rather than
 * threaded through every layer, since the cover sits four composables deep in the shelf grid.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The one cover currently making the trip. Without this every cover on the shelf would turn when
 * the screen transitions -- only the tapped one should move.
 */
val LocalFlyingCoverId = compositionLocalOf { mutableStateOf<String?>(null) }

/** Slow enough to watch the turn land, short enough not to hold up the detail page. */
const val COVER_FLIGHT_MS = 900

/**
 * Marks a cover as the same physical object on both screens: tapping one on the shelf flies it
 * to the detail page's thumbnail, turning a full revolution on the way so it reads as the item
 * being picked up and turned over. Going back plays the same flight in reverse.
 *
 * A no-op when the scopes aren't present, so covers still render anywhere else they're used.
 */
@Composable
fun Modifier.coverFlight(id: String): Modifier {
    val shared = LocalSharedTransitionScope.current
    val anim = LocalAnimatedVisibilityScope.current
    if (shared == null || anim == null) return this
    // Every other cover stays perfectly still.
    if (LocalFlyingCoverId.current.value != id) return this

    // 0 while the cover is at rest on either screen, 1 at the far end of the transition, so the
    // spin winds up as it leaves and unwinds as it lands.
    val spin by anim.transition.animateFloat(
        transitionSpec = { tween(COVER_FLIGHT_MS) },
        label = "coverSpin",
    ) { state -> if (state == EnterExitState.Visible) 0f else 1f }

    return with(shared) {
        this@coverFlight
            .sharedElement(
                state = rememberSharedContentState(key = "cover-$id"),
                animatedVisibilityScope = anim,
                boundsTransform = { _, _ -> tween(COVER_FLIGHT_MS) },
            )
            .graphicsLayer {
                cameraDistance = 16f * density
                rotationY = 360f * spin
            }
    }
}
