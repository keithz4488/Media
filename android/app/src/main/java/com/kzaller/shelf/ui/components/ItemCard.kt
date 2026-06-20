package com.kzaller.shelf.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.models.ItemDto
import com.kzaller.shelf.ui.theme.LocalShelfFlavor
import com.kzaller.shelf.ui.theme.ShelfOrnament

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    item: ItemDto,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (ItemDto) -> Unit = {},
    onLongClick: (ItemDto) -> Unit = {},
) {
    val flavor = LocalShelfFlavor.current
    // Snapshot the click callbacks via rememberUpdatedState so the pointer-input
    // handler always invokes the *current* lambda for this card -- prevents stale
    // closures from emitting a previous item's id during navigation transitions.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val aspect = when (item.kind) {
        MediaKind.BOOK -> 2f / 3f
        MediaKind.MOVIE, MediaKind.TV -> 2f / 3f
        MediaKind.GAME -> 3f / 4f
    }

    val borderColor = if (selected) flavor.accent else flavor.cardBorder
    val borderWidth = if (selected) 3.dp else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { currentOnClick(item) },
                onLongClick = { currentOnLongClick(item) },
            ),
        shape = RoundedCornerShape(if (flavor.ornament == ShelfOrnament.NEON_GRID) 4.dp else 10.dp),
        colors = CardDefaults.cardColors(containerColor = flavor.cardSurface),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.coverUrl != null) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(0.dp)),
                    )
                } else {
                    Text(
                        text = item.title.take(2).uppercase(),
                        style = flavor.titleStyle.copy(color = flavor.accent),
                    )
                }
                if (selected) {
                    // Dim the cover and badge a check so the selection state is obvious.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(flavor.accent.copy(alpha = 0.25f)),
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = flavor.accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(2.dp)
                            .size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondary = listOfNotNull(item.subtitle, item.year?.toString()).joinToString(" · ")
                if (secondary.isNotBlank()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
