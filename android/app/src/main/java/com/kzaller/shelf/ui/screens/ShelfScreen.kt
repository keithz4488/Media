package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.Status
import com.kzaller.shelf.ui.components.ItemCard
import com.kzaller.shelf.ui.components.ShelfBackground
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShelfScreen(
    kind: MediaKind,
    vm: ShelfViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onItem: (String) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val flavor = flavorFor(kind, dark)
    MediaShelfTheme(flavor = flavor, dark = dark) {
        val items by vm.items.collectAsState()
        val total by vm.totalCount.collectAsState()
        val activeFilters by vm.filters.collectAsState()

        var sheetOpen by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(kind.label, style = flavor.titleStyle.copy(color = flavor.accent)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { sheetOpen = true }) {
                            BadgedBox(badge = {
                                if (activeFilters.isNotEmpty()) {
                                    Badge { Text(activeFilters.size.toString()) }
                                }
                            }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = flavor.accent,
                    contentColor = flavor.onAccent,
                ) { Icon(Icons.Default.Add, contentDescription = "Add to shelf") }
            },
        ) { padding ->
            // ShelfBackground inset already pads past edge ornaments (film strip etc).
            ShelfBackground(modifier = Modifier.padding(padding)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (activeFilters.isNotEmpty()) {
                        ActiveFilterBar(
                            kind = kind,
                            active = activeFilters,
                            shownCount = items.size,
                            totalCount = total,
                            onClear = vm::clearFilters,
                        )
                    }
                    if (items.isEmpty()) {
                        EmptyState(filtered = activeFilters.isNotEmpty())
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(items, key = { it.id }) { item ->
                                ItemCard(item = item, onClick = { onItem(item.id) })
                            }
                        }
                    }
                }
            }
        }

        if (sheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { sheetOpen = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                FilterSheet(
                    kind = kind,
                    selected = activeFilters,
                    onToggle = vm::toggleFilter,
                    onClear = vm::clearFilters,
                    onClose = { sheetOpen = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterBar(
    kind: MediaKind,
    active: Set<String>,
    shownCount: Int,
    totalCount: Int,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Showing $shownCount of $totalCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Maintain the per-kind option order for predictable layout.
            Status.optionsFor(kind).filter { it in active }.forEach { code ->
                AssistChip(
                    onClick = onClear, // tapping any active chip clears all -- simple
                    label = { Text(Status.label(code, kind)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        labelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (filtered) "Nothing matches this filter" else "Nothing here yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (filtered) "Try a different combination" else "Tap + to scan or search",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    kind: MediaKind,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filter by status", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onClear() }) { Text("Reset") }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Status.optionsFor(kind).forEach { code ->
                val on = code in selected
                FilterChip(
                    selected = on,
                    onClick = { onToggle(code) },
                    label = { Text(Status.label(code, kind)) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done") }
        }
    }
}
