package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.ui.components.SynthwaveBackground
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

@Composable
fun HomeScreen(
    onShelfTap: (MediaKind) -> Unit,
    onSearchAll: () -> Unit,
    onStats: () -> Unit,
    onAchievements: () -> Unit,
    onImport: () -> Unit,
    onImportSteam: () -> Unit,
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
                SynthwaveBackground(modifier = Modifier.matchParentSize())
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
                        Box {
                            var importMenuOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { importMenuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Import",
                                    tint = Color(0xFFE5C07B),
                                )
                            }
                            DropdownMenu(
                                expanded = importMenuOpen,
                                onDismissRequest = { importMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("From Plex") },
                                    onClick = { importMenuOpen = false; onImport() },
                                )
                                DropdownMenuItem(
                                    text = { Text("From Steam") },
                                    onClick = { importMenuOpen = false; onImportSteam() },
                                )
                            }
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

// ---------------------------------------------------------------- collection at a glance

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionGlance(stats: List<GlanceStat>, modifier: Modifier = Modifier) {
    if (stats.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        // basicMarquee measures the row at its full (unbounded) width and scrolls it continuously
        // within the box, wrapping with a small gap — a true news-crawl over ALL the stats.
        Row(
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                repeatDelayMillis = 0,
                spacing = MarqueeSpacing(0.dp),
                velocity = 34.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stats.forEach { stat ->
                Spacer(Modifier.width(26.dp))
                GlanceCell(stat.value, stat.label)
                Spacer(Modifier.width(26.dp))
                GlanceDivider()
            }
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
    val woodEdge = woodEdgeBrush()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .drawBehind { drawWalnut() },
    ) {
        // Top plank
        Spacer(Modifier.fillMaxWidth().height(frameW))

        // Top row: Books | divider | Movies
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Spacer(Modifier.width(frameW).fillMaxHeight())
            CubbyCell(
                kind = MediaKind.BOOK,
                count = counts[MediaKind.BOOK] ?: 0,
                onClick = { onShelfTap(MediaKind.BOOK) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(frameW).fillMaxHeight())
            CubbyCell(
                kind = MediaKind.MOVIE,
                count = counts[MediaKind.MOVIE] ?: 0,
                onClick = { onShelfTap(MediaKind.MOVIE) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(frameW).fillMaxHeight())
        }

        // Horizontal divider
        Spacer(Modifier.fillMaxWidth().height(dividerH))

        // Bottom row: TV | divider | Games
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Spacer(Modifier.width(frameW).fillMaxHeight())
            CubbyCell(
                kind = MediaKind.TV,
                count = counts[MediaKind.TV] ?: 0,
                onClick = { onShelfTap(MediaKind.TV) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(frameW).fillMaxHeight())
            CubbyCell(
                kind = MediaKind.GAME,
                count = counts[MediaKind.GAME] ?: 0,
                onClick = { onShelfTap(MediaKind.GAME) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(frameW).fillMaxHeight())
        }

        // Bottom plank + front face (gives the unit a tiny 3D depth)
        Spacer(Modifier.fillMaxWidth().height(frameW))
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


// Darker walnut front-face strip under the bottom plank, for a little depth.
private fun woodEdgeBrush() = Brush.verticalGradient(
    colors = listOf(Color(0xFF3E2415), Color(0xFF20120A)),
)

/**
 * Walnut wood texture for the shelf frame: a deep base gradient, wavy grain streaks, a couple
 * of knots, and a soft diagonal sheen. Drawn behind the cubby so it shows through the frame and
 * dividers (the cells paint over it). Deterministic seed so the grain doesn't shimmer on redraw.
 */
private fun DrawScope.drawWalnut() {
    val w = size.width
    val h = size.height
    val rnd = kotlin.random.Random(4242)

    drawRect(Brush.verticalGradient(listOf(Color(0xFF7A4A28), Color(0xFF3E2415))))

    val lines = (w * 0.28f).toInt().coerceAtLeast(1)
    repeat(lines) {
        val x = rnd.nextFloat() * w
        val dark = rnd.nextBoolean()
        val color = if (dark) {
            Color(0xFF281608).copy(alpha = 0.05f + rnd.nextFloat() * 0.12f)
        } else {
            Color(0xFFFFE1B4).copy(alpha = 0.03f + rnd.nextFloat() * 0.06f)
        }
        val amp = 10f * (0.4f + rnd.nextFloat())
        val phase = rnd.nextFloat() * (2f * PI.toFloat())
        val strokeW = 0.6f + rnd.nextFloat() * 1.2f
        val path = Path().apply {
            moveTo(x, 0f)
            var y = 0f
            while (y <= h) {
                lineTo(x + sin((y / h) * 2f * PI.toFloat() + phase) * amp, y)
                y += 4f
            }
        }
        drawPath(path, color, style = Stroke(width = strokeW))
    }

    repeat(3) {
        if (rnd.nextFloat() > 0.4f) {
            val kx = 0.15f * w + rnd.nextFloat() * 0.7f * w
            val ky = 0.2f * h + rnd.nextFloat() * 0.6f * h
            for (i in 0 until 5) {
                val rr = 2f + i * 1.7f
                drawOval(
                    color = Color(0xFF2D190C).copy(alpha = (0.30f - rr * 0.02f).coerceAtLeast(0.05f)),
                    topLeft = Offset(kx - rr * 1.5f, ky - rr),
                    size = Size(rr * 3f, rr * 2f),
                    style = Stroke(width = 1.1f),
                )
            }
        }
    }

    drawRect(
        Brush.linearGradient(
            colors = listOf(Color(0xFFFFEBC8).copy(alpha = 0.12f), Color.Transparent),
            start = Offset(0f, 0f),
            end = Offset(w * 0.7f, h),
        ),
    )
}

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
