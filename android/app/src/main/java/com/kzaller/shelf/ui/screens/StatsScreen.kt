package com.kzaller.shelf.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kzaller.shelf.data.Export
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.models.ItemDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    vm: StatsViewModel,
    onBack: () -> Unit,
    onShelfTap: (MediaKind) -> Unit,
) {
    val snap by vm.snapshot.collectAsState()
    val allItems by vm.allItems.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The document the user picks is written on the IO dispatcher with whatever content we
    // staged when they chose the format.
    var pendingContent by remember { mutableStateOf("") }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { writeTextToUri(context, it, pendingContent, scope) } }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { writeTextToUri(context, it, pendingContent, scope) } }

    MediaShelfTheme(dark = true) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("${snap.currentYear} in review") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }, enabled = allItems.isNotEmpty()) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Export as JSON") },
                                onClick = {
                                    menuOpen = false
                                    pendingContent = Export.toJson(allItems)
                                    jsonLauncher.launch("media-shelf-backup.json")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export as CSV") },
                                onClick = {
                                    menuOpen = false
                                    pendingContent = Export.toCsv(allItems)
                                    csvLauncher.launch("media-shelf-backup.csv")
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A0A0F), Color(0xFF13131C), Color(0xFF1A1424)),
                        ),
                    ),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Headline(snap) }
                    item { Spacer(Modifier.height(4.dp)) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShelfStatCard(MediaKind.BOOK, snap, modifier = Modifier.weight(1f)) { onShelfTap(MediaKind.BOOK) }
                            ShelfStatCard(MediaKind.MOVIE, snap, modifier = Modifier.weight(1f)) { onShelfTap(MediaKind.MOVIE) }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShelfStatCard(MediaKind.TV, snap, modifier = Modifier.weight(1f)) { onShelfTap(MediaKind.TV) }
                            ShelfStatCard(MediaKind.GAME, snap, modifier = Modifier.weight(1f)) { onShelfTap(MediaKind.GAME) }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                    if (snap.recentlyCompleted.isNotEmpty()) {
                        item {
                            Text(
                                "Recently completed",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFE8E8EA),
                            )
                        }
                        items(snap.recentlyCompleted, key = { it.id }) { item -> RecentRow(item) }
                    }
                }
            }
        }
    }
}

/** Writes exported text to the user-picked document off the main thread. */
private fun writeTextToUri(context: Context, uri: Uri, text: String, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }
    }
}

@Composable
private fun Headline(snap: StatsSnapshot) {
    Column {
        Text(
            text = snap.completedYtdAll.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                color = Color(0xFFE5C07B),
                fontWeight = FontWeight.Black,
            ),
        )
        Text(
            text = "items finished in ${snap.currentYear}",
            style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFE8E8EA)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${snap.totalAll} total across your shelves",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE8E8EA).copy(alpha = 0.7f)),
        )
    }
}

@Composable
private fun ShelfStatCard(
    kind: MediaKind,
    snap: StatsSnapshot,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val flavor = flavorFor(kind, dark = true)
    val total = snap.totalsByKind[kind] ?: 0
    val ytd = snap.completedYtdByKind[kind] ?: 0
    val avg = snap.avgRatingByKind[kind]

    Card(
        onClick = onTap,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(flavor.backgroundBrush)
                .padding(14.dp),
        ) {
            Column {
                Text(
                    text = kind.label,
                    style = flavor.titleStyle.copy(color = flavor.accent, fontSize = 22.sp),
                )
                Spacer(Modifier.height(10.dp))
                StatLine("Total", total.toString())
                StatLine("Finished ${snap.currentYear}", ytd.toString())
                StatLine("Avg rating", avg?.let { "%.1f / 5".format(it) } ?: "—")
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)),
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun RecentRow(item: ItemDto) {
    val flavor = flavorFor(item.kind, dark = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 36.dp)
                    .background(flavor.accent, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    text = "${item.kind.label} · ${item.completedAt?.let(::shortDate) ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
            if (item.rating != null) {
                Text(
                    text = "${item.rating}/5",
                    style = MaterialTheme.typography.titleSmall,
                    color = flavor.accent,
                )
            }
        }
    }
}

private fun shortDate(epochMs: Long): String {
    val date = java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    return date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
}
