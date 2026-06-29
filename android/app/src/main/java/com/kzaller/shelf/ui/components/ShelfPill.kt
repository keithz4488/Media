package com.kzaller.shelf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kzaller.shelf.data.MediaKind

/** The Cibby-style collection pill: a rounded chip with the shelf's icon + name + chevron.
 *  Tapping it opens a menu to jump straight to another shelf. */
@Composable
fun ShelfPill(
    current: MediaKind,
    accent: Color,
    onSwitch: (MediaKind) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(50),
                )
                .clickable { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
        ) {
            Icon(
                imageVector = kindIcon(current),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = current.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Switch shelf",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MediaKind.values().forEach { k ->
                DropdownMenuItem(
                    leadingIcon = { Icon(kindIcon(k), contentDescription = null) },
                    text = { Text(k.label, fontWeight = if (k == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        if (k != current) onSwitch(k)
                    },
                )
            }
        }
    }
}

fun kindIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.BOOK -> Icons.AutoMirrored.Filled.MenuBook
    MediaKind.MOVIE -> Icons.Default.Movie
    MediaKind.TV -> Icons.Default.Tv
    MediaKind.GAME -> Icons.Default.SportsEsports
}
