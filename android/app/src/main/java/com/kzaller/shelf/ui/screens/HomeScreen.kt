package com.kzaller.shelf.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

@Composable
fun HomeScreen(
    onShelfTap: (MediaKind) -> Unit,
    onSearchAll: () -> Unit,
    onStats: () -> Unit,
    onAchievements: () -> Unit,
    onImport: () -> Unit,
    onOpenItem: (MediaKind, String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ShelfRepository(context) }
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repo))
    val counts by vm.counts.collectAsState()
    val glanceStats by vm.glanceStats.collectAsState()

    MediaShelfTheme(dark = true) {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                AnimatedWall(modifier = Modifier.matchParentSize())
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MEDIA",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = Color(0xFFE5C07B),
                                    letterSpacing = 6.sp,
                                    fontWeight = FontWeight.Black,
                                ),
                            )
                            Text(
                                text = "shelf",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = Color(0xFFE8E8EA),
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 28.sp,
                                ),
                            )
                        }
                        IconButton(onClick = onAchievements) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Achievements",
                                tint = Color(0xFFE5C07B),
                            )
                        }
                        IconButton(onClick = onImport) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Import from Plex",
                                tint = Color(0xFFE5C07B),
                            )
                        }
                        IconButton(onClick = onStats) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "Stats",
                                tint = Color(0xFFE5C07B),
                            )
                        }
                        IconButton(onClick = onSearchAll) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search all shelves",
                                tint = Color(0xFFE5C07B),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    CubbyUnit(
                        counts = counts,
                        onShelfTap = onShelfTap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    )
                    if (glanceStats.isNotEmpty()) {
                        CollectionGlance(
                            stats = glanceStats,
                            modifier = Modifier.padding(bottom = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- animated backdrop

/**
 * A slow synthwave perspective grid that rolls toward the viewer, matching the neon reference:
 * deep-purple sky, a glowing cyan-white horizon, magenta converging rails, and horizontal lines
 * that shift cyan→magenta as they scroll forward and wrap at the horizon (fading in there, which
 * hides the loop). Plus a few cubes drifting in the sky.
 */
@Composable
private fun AnimatedWall(modifier: Modifier = Modifier) {
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
        drawRect(brush = wallBrush())
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

// ---------------------------------------------------------------- collection at a glance

@Composable
private fun CollectionGlance(stats: List<GlanceStat>, modifier: Modifier = Modifier) {
    if (stats.isEmpty()) return

    // A continuous news-ticker crawl: two identical copies of the stat run scroll left at a
    // constant speed; when the first fully exits, the second is exactly in its place, so the
    // loop is seamless and never pauses.
    var contentWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val speedPxPerSec = with(density) { 26.dp.toPx() }
    val durationMs = if (contentWidth > 0) {
        (contentWidth / speedPxPerSec * 1000f).toInt().coerceAtLeast(1)
    } else {
        1
    }
    val transition = rememberInfiniteTransition(label = "ticker")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -contentWidth.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "ticker-offset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(vertical = 16.dp),
    ) {
        Row(modifier = Modifier.offset { IntOffset(offset.roundToInt(), 0) }) {
            TickerRun(stats, Modifier.onGloballyPositioned { contentWidth = it.size.width })
            TickerRun(stats)
        }
    }
}

/** One run of every stat, each wrapped in gaps and trailed by a divider so copies chain cleanly. */
@Composable
private fun TickerRun(stats: List<GlanceStat>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        stats.forEach { stat ->
            Spacer(Modifier.width(26.dp))
            GlanceCell(stat.value, stat.label)
            Spacer(Modifier.width(26.dp))
            GlanceDivider()
        }
    }
}

@Composable
private fun GlanceCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = Color(0xFFE5C07B),
                fontWeight = FontWeight.Black,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFE8E8EA).copy(alpha = 0.75f)),
        )
    }
}

@Composable
private fun GlanceDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(Color.White.copy(alpha = 0.12f)),
    )
}

// ---------------------------------------------------------------- the IKEA Kallax-style 2x2 cubby

@Composable
private fun CubbyUnit(
    counts: Map<MediaKind, Int>,
    onShelfTap: (MediaKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frameW = 14.dp        // outer/inner uprights and top plank
    val dividerH = 16.dp      // horizontal divider between rows
    val bottomEdgeH = 8.dp    // a small "front face" strip below the bottom plank
    val wood = woodBrush()
    val woodEdge = woodEdgeBrush()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(wood),
    ) {
        // Top plank
        Spacer(Modifier.fillMaxWidth().height(frameW).background(wood))

        // Top row: Books | divider | Movies
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Spacer(Modifier.width(frameW).fillMaxHeight().background(wood))
            CubbyCell(
                kind = MediaKind.BOOK,
                count = counts[MediaKind.BOOK] ?: 0,
                onClick = { onShelfTap(MediaKind.BOOK) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(frameW).fillMaxHeight().background(wood))
            CubbyCell(
                kind = MediaKind.MOVIE,
                count = counts[MediaKind.MOVIE] ?: 0,
                onClick = { onShelfTap(MediaKind.MOVIE) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(frameW).fillMaxHeight().background(wood))
        }

        // Horizontal divider
        Spacer(Modifier.fillMaxWidth().height(dividerH).background(wood))

        // Bottom row: TV | divider | Games
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Spacer(Modifier.width(frameW).fillMaxHeight().background(wood))
            CubbyCell(
                kind = MediaKind.TV,
                count = counts[MediaKind.TV] ?: 0,
                onClick = { onShelfTap(MediaKind.TV) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(frameW).fillMaxHeight().background(wood))
            CubbyCell(
                kind = MediaKind.GAME,
                count = counts[MediaKind.GAME] ?: 0,
                onClick = { onShelfTap(MediaKind.GAME) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(frameW).fillMaxHeight().background(wood))
        }

        // Bottom plank + front face (gives the unit a tiny 3D depth)
        Spacer(Modifier.fillMaxWidth().height(frameW).background(wood))
        Spacer(Modifier.fillMaxWidth().height(bottomEdgeH).background(woodEdge))
    }
}

@Composable
private fun CubbyCell(
    kind: MediaKind,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flavor = flavorFor(kind, dark = true)
    Box(
        modifier = modifier
            .background(cubbyBackBrush(kind))
            .border(width = 3.dp, color = Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (kind) {
                MediaKind.BOOK  -> drawBookSpines(this)
                MediaKind.MOVIE -> drawMarquee(this)
                MediaKind.TV    -> drawCrtBars(this)
                MediaKind.GAME  -> drawArcadeGrid(this)
            }
        }
        // Count badge top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text(
                text = count.toString(),
                color = flavor.accent,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
            )
        }
        // Title block top-left
        Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp, end = 52.dp)) {
            Text(
                text = kind.label,
                style = flavor.titleStyle.copy(color = flavor.accent, fontSize = 22.sp),
                maxLines = 1,
            )
            Text(
                text = tagline(kind),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.75f),
                ),
                maxLines = 1,
            )
        }
    }
}

private fun tagline(kind: MediaKind): String = when (kind) {
    MediaKind.BOOK  -> "Read & to read"
    MediaKind.MOVIE -> "Big & small screen"
    MediaKind.TV    -> "Series & seasons"
    MediaKind.GAME  -> "Played & backlog"
}

// ---------------------------------------------------------------- brushes

// Warm wooden room (was a dark navy wall) so home matches the Cibby-style shelves.
// Deep synthwave sky: near-black navy up top fading into purple toward the horizon.
private fun wallBrush() = Brush.verticalGradient(
    colors = listOf(Color(0xFF07031A), Color(0xFF140A38), Color(0xFF2A0F52)),
)

private fun woodBrush() = Brush.verticalGradient(
    colors = listOf(Color(0xFFC79762), Color(0xFF8A5A30)),
)

private fun woodEdgeBrush() = Brush.verticalGradient(
    colors = listOf(Color(0xFF8A5A30), Color(0xFF4F311A)),
)

private fun cubbyBackBrush(kind: MediaKind): Brush = when (kind) {
    MediaKind.BOOK -> Brush.verticalGradient(
        listOf(Color(0xFF3B2310), Color(0xFF6B3F1C), Color(0xFFA56A2C)),
    )
    MediaKind.MOVIE -> Brush.verticalGradient(
        listOf(Color(0xFF050714), Color(0xFF0B132B), Color(0xFF1C2541)),
    )
    MediaKind.TV -> Brush.verticalGradient(
        listOf(Color(0xFF021008), Color(0xFF0A1F14), Color(0xFF0F3B22)),
    )
    MediaKind.GAME -> Brush.verticalGradient(
        listOf(Color(0xFF06021A), Color(0xFF180A3C), Color(0xFF3A0E5C)),
    )
}

// ---------------------------------------------------------------- per-cubby illustrations
// These draw at the bottom of the cubby, leaving the top free for title + count.

private fun drawBookSpines(d: DrawScope) = with(d) {
    val spines = listOf(
        Color(0xFFB54B2A) to 0.78f,
        Color(0xFFE3B25A) to 0.92f,
        Color(0xFF6E3018) to 0.62f,
        Color(0xFFC9824A) to 0.86f,
        Color(0xFF8A4520) to 0.70f,
        Color(0xFFE9D4A5) to 0.98f,
        Color(0xFF7C3B18) to 0.74f,
    )
    val rowH = size.height * 0.46f
    val rowTop = size.height - rowH - 6f
    val totalW = size.width * 0.94f
    val gap = 3f
    val w = (totalW - gap * (spines.size - 1)) / spines.size
    var x = (size.width - totalW) / 2f
    spines.forEach { (c, hRatio) ->
        val h = rowH * hRatio
        drawRect(c, topLeft = Offset(x, rowTop + (rowH - h)), size = Size(w, h))
        drawRect(
            Color.White.copy(alpha = 0.08f),
            topLeft = Offset(x + w * 0.20f, rowTop + (rowH - h)),
            size = Size(w * 0.12f, h),
        )
        x += w + gap
    }
    // little "cubby floor" line so the books look like they're standing on something
    drawRect(
        Color(0xFF2A1409),
        topLeft = Offset(0f, size.height - 4f),
        size = Size(size.width, 4f),
    )
}

private fun drawMarquee(d: DrawScope) = with(d) {
    val rows = 2
    val bulbsPerRow = 7
    val padX = size.width * 0.10f
    val bottomPad = size.height * 0.12f
    val rowGap = size.height * 0.08f
    val bulbR = size.width * 0.024f
    val span = size.width - padX * 2
    for (r in 0 until rows) {
        for (c in 0 until bulbsPerRow) {
            val x = padX + span * c / (bulbsPerRow - 1)
            val y = size.height - bottomPad - rowGap * r
            drawCircle(Color(0xFFFFD56B).copy(alpha = 0.42f), radius = bulbR * 2.6f, center = Offset(x, y))
            drawCircle(Color(0xFFFFE08A), radius = bulbR, center = Offset(x, y))
            drawCircle(
                Color.White.copy(alpha = 0.7f),
                radius = bulbR * 0.38f,
                center = Offset(x - bulbR * 0.25f, y - bulbR * 0.25f),
            )
        }
    }
    val stars = listOf(0.20f to 0.30f, 0.55f to 0.32f, 0.78f to 0.40f, 0.40f to 0.50f, 0.85f to 0.22f)
    stars.forEach { (sx, sy) ->
        drawCircle(Color.White.copy(alpha = 0.55f), radius = 1.4f, center = Offset(size.width * sx, size.height * sy))
    }
    val stripW = size.width * 0.06f
    drawRect(Color(0xFF050714), topLeft = Offset(0f, 0f), size = Size(stripW, size.height))
    val holes = 9
    val holeW = stripW * 0.50f
    val holeH = size.height * 0.05f
    for (i in 0 until holes) {
        val y = size.height * (0.04f + 0.105f * i)
        drawRect(
            Color.Black,
            topLeft = Offset((stripW - holeW) / 2f, y),
            size = Size(holeW, holeH),
        )
    }
}

private fun drawCrtBars(d: DrawScope) = with(d) {
    val bars = listOf(
        Color(0xFFC0C0C0), Color(0xFFC0C000), Color(0xFF00C0C0),
        Color(0xFF00C000), Color(0xFFC000C0), Color(0xFFC00000),
        Color(0xFF0000C0),
    )
    val barTop = size.height * 0.48f
    val barH = size.height * 0.45f
    val barW = size.width / bars.size
    bars.forEachIndexed { i, c ->
        drawRect(c.copy(alpha = 0.78f), topLeft = Offset(i * barW, barTop), size = Size(barW, barH))
    }
    drawRect(Color(0xFF050E08), topLeft = Offset(0f, size.height - size.height * 0.06f), size = Size(size.width, size.height * 0.06f))
    var y = 0f
    while (y < size.height) {
        drawLine(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += 3f
    }
}

private fun drawArcadeGrid(d: DrawScope) = with(d) {
    val horizon = size.height * 0.54f
    val cyan = Color(0xFF34E0FF)
    val magenta = Color(0xFFFF3DBE)
    drawRect(magenta.copy(alpha = 0.22f), topLeft = Offset(0f, horizon - 5f), size = Size(size.width, 10f))
    val vCount = 9
    for (i in 0 until vCount) {
        val xTop = size.width * (0.5f + (i - vCount / 2f) * 0.04f)
        val xBottom = size.width * (i.toFloat() / (vCount - 1))
        drawLine(cyan.copy(alpha = 0.55f), Offset(xTop, horizon), Offset(xBottom, size.height), strokeWidth = 1.4f)
    }
    var step = size.height * 0.04f
    var y = horizon + step
    while (y < size.height) {
        drawLine(magenta.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.4f)
        y += step
        step *= 1.18f
    }
    val sunR = size.width * 0.20f
    val sunCx = size.width * 0.5f
    val sunCy = horizon - sunR * 0.5f
    drawCircle(magenta.copy(alpha = 0.35f), radius = sunR * 1.4f, center = Offset(sunCx, sunCy))
    drawCircle(Color(0xFFFFB6E0), radius = sunR, center = Offset(sunCx, sunCy))
    for (i in 0..3) {
        val by = sunCy + sunR * (0.25f + i * 0.18f)
        drawRect(Color(0xFF180A3C), topLeft = Offset(sunCx - sunR, by), size = Size(sunR * 2, sunR * 0.07f))
    }
}
