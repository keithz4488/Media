package com.kzaller.shelf.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.kzaller.shelf.data.models.ItemDto
import com.kzaller.shelf.ui.theme.LocalShelfFlavor

/**
 * Compact horizontal row for the list view of a shelf. Cover thumbnail on the left,
 * title + subtitle/year on the right. Shares selection and click semantics with ItemCard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemListRow(
    item: ItemDto,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (ItemDto) -> Unit = {},
    onLongClick: (ItemDto) -> Unit = {},
) {
    val flavor = LocalShelfFlavor.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val borderColor = if (selected) flavor.accent else flavor.cardBorder
    val borderWidth = if (selected) 3.dp else 1.dp
    val container = if (selected) flavor.accent.copy(alpha = 0.18f) else flavor.cardSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { currentOnClick(item) },
                onLongClick = { currentOnLongClick(item) },
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.coverUrl != null) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = item.title.take(2).uppercase(),
                        style = flavor.titleStyle.copy(color = flavor.accent),
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(flavor.accent.copy(alpha = 0.35f)),
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = flavor.accent,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(22.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
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
            FormatBadge(formatCsv = item.format)
        }
    }
}
