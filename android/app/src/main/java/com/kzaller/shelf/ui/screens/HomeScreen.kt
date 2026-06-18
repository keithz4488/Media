package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

private data class ShelfTile(val kind: MediaKind, val tagline: String)

private val tiles = listOf(
    ShelfTile(MediaKind.BOOK,  "Read & to read"),
    ShelfTile(MediaKind.MOVIE, "Big screen, small screen"),
    ShelfTile(MediaKind.TV,    "Series & seasons"),
    ShelfTile(MediaKind.GAME,  "Played & in the backlog"),
)

@Composable
fun HomeScreen(onShelfTap: (MediaKind) -> Unit) {
    val context = LocalContext.current
    val repo = remember { ShelfRepository(context) }
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repo))
    val counts by vm.counts.collectAsState()

    // Force a dark, rich home regardless of system theme -- light mode tan was muddy.
    MediaShelfTheme(dark = true) {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(homeBrush()),
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(24.dp))
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
                    Spacer(Modifier.height(20.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(tiles, key = { it.kind.wire }) { tile ->
                            ShelfTileCard(
                                tile = tile,
                                count = counts[tile.kind] ?: 0,
                                onClick = { onShelfTap(tile.kind) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun homeBrush() = Brush.verticalGradient(
    colors = listOf(Color(0xFF0A0A0F), Color(0xFF13131C), Color(0xFF1A1424)),
)

@Composable
private fun ShelfTileCard(tile: ShelfTile, count: Int, onClick: () -> Unit) {
    val flavor = flavorFor(tile.kind, dark = true)
    MediaShelfTheme(flavor = flavor, dark = true) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(0.82f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(tileBrush(tile.kind)),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    when (tile.kind) {
                        MediaKind.BOOK  -> drawBookSpines(this)
                        MediaKind.MOVIE -> drawMarquee(this)
                        MediaKind.TV    -> drawCrtBars(this)
                        MediaKind.GAME  -> drawArcadeGrid(this)
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = count.toString(),
                        color = flavor.accent,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                    )
                }
                // Title + tagline at the TOP, so the illustration can fill the bottom.
                Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp, end = 56.dp)) {
                    Text(
                        text = tile.kind.label,
                        style = flavor.titleStyle.copy(color = flavor.accent, fontSize = 26.sp),
                    )
                    Text(
                        text = tile.tagline,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.78f),
                        ),
                    )
                }
            }
        }
    }
}

private fun tileBrush(kind: MediaKind): Brush = when (kind) {
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

// ---------------------------------------------------------------- per-tile illustrations

private fun drawBookSpines(d: DrawScope) = with(d) {
    val spines = listOf(
        Color(0xFFB54B2A) to 0.70f,
        Color(0xFFE3B25A) to 0.92f,
        Color(0xFF6E3018) to 0.55f,
        Color(0xFFC9824A) to 0.82f,
        Color(0xFF8A4520) to 0.66f,
        Color(0xFFE9D4A5) to 0.98f,
        Color(0xFF7C3B18) to 0.74f,
    )
    val rowH = size.height * 0.46f
    val rowTop = size.height - rowH - 6f
    val totalW = size.width * 0.94f
    val gap = 4f
    val w = (totalW - gap * (spines.size - 1)) / spines.size
    var x = (size.width - totalW) / 2f
    spines.forEach { (c, hRatio) ->
        val h = rowH * hRatio
        drawRect(c, topLeft = Offset(x, rowTop + (rowH - h)), size = Size(w, h))
        drawRect(
            Color.White.copy(alpha = 0.08f),
            topLeft = Offset(x + w * 0.18f, rowTop + (rowH - h)),
            size = Size(w * 0.12f, h),
        )
        x += w + gap
    }
    // little "shelf" line
    drawRect(
        Color(0xFF2A1409),
        topLeft = Offset(0f, size.height - 5f),
        size = Size(size.width, 5f),
    )
}

private fun drawMarquee(d: DrawScope) = with(d) {
    // Twin marquee bulb rows along the bottom edge -- like the front of a theater.
    val rows = 2
    val bulbsPerRow = 8
    val padX = size.width * 0.10f
    val bottomPad = size.height * 0.10f
    val rowGap = size.height * 0.07f
    val bulbR = size.width * 0.023f
    val span = size.width - padX * 2
    for (r in 0 until rows) {
        for (c in 0 until bulbsPerRow) {
            val x = padX + span * c / (bulbsPerRow - 1)
            val y = size.height - bottomPad - rowGap * r
            // warm glow halo against the navy background
            drawCircle(Color(0xFFFFD56B).copy(alpha = 0.42f), radius = bulbR * 2.6f, center = Offset(x, y))
            drawCircle(Color(0xFFFFE08A), radius = bulbR, center = Offset(x, y))
            drawCircle(
                Color.White.copy(alpha = 0.7f),
                radius = bulbR * 0.38f,
                center = Offset(x - bulbR * 0.25f, y - bulbR * 0.25f),
            )
        }
    }
    // a few "stars" in the upper portion to give the navy depth
    val stars = listOf(
        0.18f to 0.18f, 0.30f to 0.32f, 0.55f to 0.22f,
        0.78f to 0.40f, 0.88f to 0.16f, 0.40f to 0.50f,
    )
    stars.forEach { (sx, sy) ->
        drawCircle(Color.White.copy(alpha = 0.55f), radius = 1.4f, center = Offset(size.width * sx, size.height * sy))
    }
    // film strip down the left edge
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
    // Color bars on the lower half so the title can sit on the dark green at the top.
    val bars = listOf(
        Color(0xFFC0C0C0), Color(0xFFC0C000), Color(0xFF00C0C0),
        Color(0xFF00C000), Color(0xFFC000C0), Color(0xFFC00000),
        Color(0xFF0000C0),
    )
    val barTop = size.height * 0.46f
    val barH = size.height * 0.46f
    val barW = size.width / bars.size
    bars.forEachIndexed { i, c ->
        drawRect(c.copy(alpha = 0.78f), topLeft = Offset(i * barW, barTop), size = Size(barW, barH))
    }
    // bottom strip: a thin black "letterbox" border to evoke a TV bezel
    drawRect(Color(0xFF050E08), topLeft = Offset(0f, size.height - size.height * 0.06f), size = Size(size.width, size.height * 0.06f))
    // scanlines covering everything
    var y = 0f
    while (y < size.height) {
        drawLine(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += 3f
    }
}

private fun drawArcadeGrid(d: DrawScope) = with(d) {
    val horizon = size.height * 0.52f
    val cyan = Color(0xFF34E0FF)
    val magenta = Color(0xFFFF3DBE)
    drawRect(magenta.copy(alpha = 0.22f), topLeft = Offset(0f, horizon - 6f), size = Size(size.width, 12f))
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
    val sunR = size.width * 0.18f
    val sunCx = size.width * 0.5f
    val sunCy = horizon - sunR * 0.6f
    drawCircle(magenta.copy(alpha = 0.35f), radius = sunR * 1.4f, center = Offset(sunCx, sunCy))
    drawCircle(Color(0xFFFFB6E0), radius = sunR, center = Offset(sunCx, sunCy))
    for (i in 0..3) {
        val by = sunCy + sunR * (0.25f + i * 0.18f)
        drawRect(Color(0xFF180A3C), topLeft = Offset(sunCx - sunR, by), size = Size(sunR * 2, sunR * 0.07f))
    }
}
