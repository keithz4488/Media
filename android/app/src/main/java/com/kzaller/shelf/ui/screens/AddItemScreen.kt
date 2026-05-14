package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.models.SearchHit
import com.kzaller.shelf.ui.components.ShelfBackground
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    kind: MediaKind,
    vm: AddItemViewModel,
    onClose: () -> Unit,
    onAdded: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val flavor = flavorFor(kind, dark)

    MediaShelfTheme(flavor = flavor, dark = dark) {
        val mode by vm.mode.collectAsState()
        val query by vm.query.collectAsState()
        val hits by vm.hits.collectAsState()
        val searching by vm.searching.collectAsState()
        val error by vm.error.collectAsState()
        val statusMsg by vm.statusMsg.collectAsState()
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }
        LaunchedEffect(statusMsg) { statusMsg?.let { snackbar.showSnackbar(it); vm.clearStatusMsg() } }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Add to ${kind.label}") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            ShelfBackground(modifier = Modifier.padding(padding)) {
                when (mode) {
                    AddItemViewModel.Mode.CHOOSE -> ChooseSection(
                        kind = kind,
                        onCamera = { vm.goTo(AddItemViewModel.Mode.CAMERA) },
                        onSearch = { vm.goTo(AddItemViewModel.Mode.SEARCH) },
                        onManual = { vm.goTo(AddItemViewModel.Mode.MANUAL) },
                    )
                    AddItemViewModel.Mode.CAMERA -> CameraScreen(
                        onBarcode = { vm.onBarcode(it) },
                        onText = { vm.onText(it) },
                        onClose = { vm.goTo(AddItemViewModel.Mode.CHOOSE) },
                    )
                    AddItemViewModel.Mode.SEARCH -> SearchSection(
                        query = query,
                        searching = searching,
                        hits = hits,
                        onQuery = vm::setQuery,
                        onSubmit = { vm.searchNow() },
                        onPick = { vm.add(it, status = "owned", after = onAdded) },
                        onManual = { vm.goTo(AddItemViewModel.Mode.MANUAL) },
                    )
                    AddItemViewModel.Mode.MANUAL -> ManualSection(
                        seed = query,
                        kind = kind,
                        onCancel = { vm.goTo(AddItemViewModel.Mode.SEARCH) },
                        onSave = { title, subtitle, year, status ->
                            vm.addManual(title, subtitle, year, status, after = onAdded)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooseSection(
    kind: MediaKind,
    onCamera: () -> Unit,
    onSearch: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "How would you like to add ${kind.label.lowercase()}?",
            style = MaterialTheme.typography.titleMedium,
        )
        BigChoice(icon = Icons.Default.PhotoCamera, label = "Camera", subtitle = "Scan a barcode or read a cover", onClick = onCamera)
        BigChoice(icon = Icons.Default.Search, label = "Search", subtitle = "Type a title", onClick = onSearch)
        BigChoice(icon = Icons.Default.Edit, label = "Enter manually", subtitle = "Skip lookup", onClick = onManual)
    }
}

@Composable
private fun BigChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.size(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSection(
    query: String,
    searching: Boolean,
    hits: List<SearchHit>,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onPick: (SearchHit) -> Unit,
    onManual: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Title or keywords") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (searching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            },
        )
        Spacer(Modifier.height(12.dp))
        if (hits.isEmpty() && !searching && query.isNotBlank()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                Text("No matches", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = onManual, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Add manually instead")
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(hits, key = { "${it.externalSrc}:${it.externalId}" }) { hit ->
                HitRow(hit = hit, onPick = { onPick(hit) })
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, onPick: () -> Unit) {
    Card(
        onClick = onPick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                if (hit.coverUrl != null) {
                    AsyncImage(model = hit.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(hit.title.take(2).uppercase(), color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(hit.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                val secondary = listOfNotNull(hit.subtitle, hit.year?.toString()).joinToString(" · ")
                if (secondary.isNotBlank()) {
                    Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualSection(
    seed: String,
    kind: MediaKind,
    onCancel: () -> Unit,
    onSave: (String, String?, Int?, String) -> Unit,
) {
    var title by remember { mutableStateOf(seed) }
    var subtitle by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("owned") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = subtitle,
            onValueChange = { subtitle = it },
            label = { Text(when (kind) {
                MediaKind.BOOK -> "Author"
                MediaKind.MOVIE -> "Director"
                MediaKind.TV -> "Network or creator"
                MediaKind.GAME -> "Platform"
            }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = year,
            onValueChange = { y -> year = y.filter { it.isDigit() }.take(4) },
            label = { Text("Year") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("owned", "seen", "wishlist").forEach { s ->
                AssistChip(
                    onClick = { status = s },
                    label = { Text(s) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (status == s) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Back") }
            Button(
                onClick = { onSave(title, subtitle, year.toIntOrNull(), status) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        }
    }
}
