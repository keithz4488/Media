package com.kzaller.shelf.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kzaller.shelf.data.Achievement
import com.kzaller.shelf.data.Rarity

fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> Color(0xFF9AA4B2)
    Rarity.RARE -> Color(0xFF4FA3FF)
    Rarity.EPIC -> Color(0xFFB15CFF)
    Rarity.LEGENDARY -> Color(0xFFFFC94D)
}

fun rarityLabel(rarity: Rarity): String = when (rarity) {
    Rarity.COMMON -> "Common"
    Rarity.RARE -> "Rare"
    Rarity.EPIC -> "Epic"
    Rarity.LEGENDARY -> "Legendary"
}

/**
 * Flashy unlock banner: slides down from the top, pops the emoji medallion with a pulsing
 * rarity glow, and shows the title + "Achievement unlocked". Auto-dismissed by the caller.
 */
@Composable
fun AchievementUnlockBanner(
    achievement: Achievement?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = achievement != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val a = achievement ?: return@AnimatedVisibility
        val color = rarityColor(a.rarity)

        // gentle pulsing glow behind the medallion
        val infinite = rememberInfiniteTransition(label = "glow")
        val pulse by infinite.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xFF1A1622),
                        1f to color.copy(alpha = 0.22f),
                    ),
                )
                .border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // glow halo
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.35f)),
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.22f))
                        .border(1.dp, color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text(a.emoji, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.size(14.dp))
            Column {
                androidx.compose.material3.Text(
                    text = "${rarityLabel(a.rarity).uppercase()} · ACHIEVEMENT UNLOCKED",
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
                androidx.compose.material3.Text(
                    text = a.title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                )
                androidx.compose.material3.Text(
                    text = a.description,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}
