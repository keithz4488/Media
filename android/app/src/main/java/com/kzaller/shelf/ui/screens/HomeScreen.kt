package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.ui.components.ShelfBackground
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

private data class ShelfTile(val kind: MediaKind, val tagline: String)

private val tiles = listOf(
    ShelfTile(MediaKind.BOOK, "Read & to read"),
    ShelfTile(MediaKind.MOVIE, "On screen, big & small"),
    ShelfTile(MediaKind.TV, "Series, seasons, episodes"),
    ShelfTile(MediaKind.GAME, "Played & in the backlog"),
)

@Composable
fun HomeScreen(onShelfTap: (MediaKind) -> Unit) {
    Scaffold(containerColor = Color.Transparent) { padding ->
        ShelfBackground(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Text(
                    text = "Media Shelf",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                Text(
                    text = "Pick a shelf",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tiles, key = { it.kind.wire }) { tile ->
                        ShelfTileCard(tile = tile, onClick = { onShelfTap(tile.kind) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfTileCard(tile: ShelfTile, onClick: () -> Unit) {
    // Each tile previews its OWN flavor, so the user can see at a glance how the shelf will look.
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val flavor = flavorFor(tile.kind, dark)
    MediaShelfTheme(flavor = flavor, dark = dark) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = flavor.cardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(0.85f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(flavor.backgroundBrush)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                com.kzaller.shelf.ui.components.ShelfBackground { /* paints ornament */
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Column(modifier = Modifier.align(Alignment.BottomStart)) {
                            Text(
                                text = tile.kind.label,
                                style = flavor.titleStyle.copy(color = flavor.accent, fontSize = 26.sp),
                            )
                            Text(
                                text = tile.tagline,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
