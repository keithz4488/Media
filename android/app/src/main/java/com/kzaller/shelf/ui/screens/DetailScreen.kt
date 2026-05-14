package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.ui.components.ShelfBackground
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: DetailViewModel, onBack: () -> Unit) {
    val item by vm.item.collectAsState()
    val current = item

    val dark = isSystemInDarkTheme()
    val flavor = current?.let { flavorFor(it.kind, dark) } ?: flavorFor(MediaKind.BOOK, dark)

    MediaShelfTheme(flavor = flavor, dark = dark) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(current?.title ?: "", maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = { vm.delete(after = onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            ShelfBackground(modifier = Modifier.padding(padding)) {
                if (current == null) return@ShelfBackground
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (current.coverUrl != null) {
                            AsyncImage(
                                model = current.coverUrl,
                                contentDescription = current.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = current.title.take(2).uppercase(),
                                style = flavor.titleStyle.copy(color = flavor.accent),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    val secondary = listOfNotNull(current.subtitle, current.year?.toString()).joinToString(" · ")
                    if (secondary.isNotBlank()) {
                        Text(
                            text = secondary,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("owned", "seen", "wishlist").forEach { s ->
                            AssistChip(
                                onClick = { vm.setStatus(s) },
                                label = { Text(s) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if ((current.status ?: "owned") == s) flavor.accent.copy(alpha = 0.2f) else Color.Transparent,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Rating", style = MaterialTheme.typography.titleSmall)
                    Row {
                        for (i in 1..5) {
                            val filled = (current.rating ?: 0) >= i
                            IconButton(onClick = { vm.setRating(if (current.rating == i) null else i) }) {
                                Icon(
                                    imageVector = if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Rate $i",
                                    tint = flavor.accent,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    var notes by remember(current.id) { mutableStateOf(current.notes ?: "") }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        AssistChip(onClick = { vm.setNotes(notes) }, label = { Text("Save notes") })
                    }
                    if (!current.description.isNullOrBlank()) {
                        Spacer(Modifier.height(20.dp))
                        Text("About", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = current.description!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
