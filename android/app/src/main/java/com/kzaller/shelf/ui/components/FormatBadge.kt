package com.kzaller.shelf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kzaller.shelf.data.Format

/**
 * Small corner badge showing whether an item is physical (disc) and/or digital (cloud).
 * Renders nothing when the item has no format set.
 */
@Composable
fun FormatBadge(formatCsv: String?, modifier: Modifier = Modifier) {
    val formats = Format.parse(formatCsv)
    if (formats.isEmpty()) return
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        if (Format.PHYSICAL in formats) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = "Physical",
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
        if (Format.DIGITAL in formats) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Digital",
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}
