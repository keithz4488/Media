package com.kzaller.shelf.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.kzaller.shelf.data.Format
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.Status
import com.kzaller.shelf.ui.components.ExpandingAddFab
import com.kzaller.shelf.ui.components.ItemListRow
import com.kzaller.shelf.ui.components.ShelfPill
import com.kzaller.shelf.ui.components.ShelfWoodGrid
import com.kzaller.shelf.ui.components.WoodBackground
import com.kzaller.shelf.ui.components.shelfTextFieldColors
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShelfScreen(
    kind: MediaKind,
    vm: ShelfViewModel,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onItem: (String) -> Unit,
    onSwitchShelf: (MediaKind) -> Unit = {},
) {
    // Always use the dark (light-text) scheme: the wood backdrop is dark/saturated, so text
    // must stay cream regardless of the phone's light/dark setting.
    val dark = true
    val flavor = flavorFor(kind, dark)
    MediaShelfTheme(flavor = flavor, dark = dark) {
        val items by vm.items.collectAsState()
        val total by vm.totalCount.collectAsState()
        val activeFilters by vm.filters.collectAsState()
        val activeFormatFilters by vm.formatFilters.collectAsState()
        val totalActiveFilters = activeFilters.size + activeFormatFilters.size
        val anyFilterActive = totalActiveFilters > 0
        val query by vm.query.collectAsState()
        val sort by vm.sort.collectAsState()
        val viewMode by vm.viewMode.collectAsState()
        val refreshing by vm.refreshing.collectAsState()
        val selection by vm.selection.collectAsState()
        val inSelectionMode = selection.isNotEmpty()

        // System back should exit selection mode first, not navigate up.
        BackHandler(enabled = inSelectionMode) { vm.clearSelection() }

        var sheetOpen by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()
        var searchOpen by remember { mutableStateOf(false) }
        var sortMenuOpen by remember { mutableStateOf(false) }
        var bulkStatusMenuOpen by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (inSelectionMode) {
                    // Contextual top bar: count + delete + set-status + cancel.
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selection.size} selected",
                                style = flavor.titleStyle.copy(color = flavor.accent),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { vm.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { bulkStatusMenuOpen = true }) {
                                    Icon(Icons.Default.Check, contentDescription = "Set status")
                                }
                                DropdownMenu(
                                    expanded = bulkStatusMenuOpen,
                                    onDismissRequest = { bulkStatusMenuOpen = false },
                                ) {
                                    Status.optionsFor(kind).forEach { code ->
                                        DropdownMenuItem(
                                            text = { Text("Set: ${Status.label(code, kind)}") },
                                            onClick = {
                                                vm.setStatusForSelected(code)
                                                bulkStatusMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { vm.deleteSelected() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            navigationIconContentColor = flavor.accent,
                            actionIconContentColor = flavor.accent,
                        ),
                    )
                } else {
                    TopAppBar(
                        title = {
                            ShelfPill(
                                current = kind,
                                accent = flavor.accent,
                                onSwitch = onSwitchShelf,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                if (searchOpen) { vm.clearSearch(); searchOpen = false }
                                else searchOpen = true
                            }) {
                                Icon(
                                    imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = if (searchOpen) "Close search" else "Search",
                                )
                            }
                            IconButton(onClick = {
                                vm.setViewMode(if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
                            }) {
                                Icon(
                                    imageVector = if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = if (viewMode == ViewMode.GRID) "Switch to list view" else "Switch to grid view",
                                )
                            }
                            Box {
                                IconButton(onClick = { sortMenuOpen = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = sortMenuOpen,
                                    onDismissRequest = { sortMenuOpen = false },
                                ) {
                                    SortMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.label) },
                                            trailingIcon = {
                                                if (mode == sort) Icon(Icons.Default.Check, contentDescription = null)
                                            },
                                            onClick = {
                                                vm.setSort(mode)
                                                sortMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { sheetOpen = true }) {
                                BadgedBox(badge = {
                                    if (anyFilterActive) {
                                        Badge { Text(totalActiveFilters.toString()) }
                                    }
                                }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            navigationIconContentColor = flavor.accent,
                            actionIconContentColor = flavor.accent,
                        ),
                    )
                }
            },
        ) { padding ->
          Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            WoodBackground {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = vm::setSearch,
                            placeholder = { Text("Search ${kind.label.lowercase()}") },
                            singleLine = true,
                            colors = shelfTextFieldColors(),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { vm.clearSearch() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (anyFilterActive) {
                        ActiveFilterBar(
                            kind = kind,
                            active = activeFilters,
                            activeFormats = activeFormatFilters,
                            shownCount = items.size,
                            totalCount = total,
                            onClear = vm::clearFilters,
                        )
                    }
                    // Pull-to-refresh wraps the grid (and the empty state) so the gesture
                    // works whether or not items are present.
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { vm.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (items.isEmpty()) {
                            EmptyState(
                                filtered = anyFilterActive,
                                searching = query.isNotBlank(),
                            )
                        } else if (viewMode == ViewMode.GRID) {
                            // Cibby-style: standing covers resting on wooden planks.
                            ShelfWoodGrid(
                                items = items,
                                kind = kind,
                                selection = selection,
                                inSelectionMode = inSelectionMode,
                                onItem = onItem,
                                onToggle = vm::toggleSelection,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                listItems(
                                    items,
                                    key = { "${it.kind.wire}:${it.id}" },
                                ) { item ->
                                    ItemListRow(
                                        item = item,
                                        selected = item.id in selection,
                                        onClick = { clicked ->
                                            if (inSelectionMode) vm.toggleSelection(clicked.id)
                                            else onItem(clicked.id)
                                        },
                                        onLongClick = { clicked -> vm.toggleSelection(clicked.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Radial add button overlay (hidden while multi-selecting).
            if (!inSelectionMode) {
                ExpandingAddFab(
                    accent = flavor.accent,
                    onAccent = flavor.onAccent,
                    onScan = { onAdd("camera") },
                    onSearch = { onAdd("search") },
                    onManual = { onAdd("manual") },
                )
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
                    selectedFormats = activeFormatFilters,
                    onToggle = vm::toggleFilter,
                    onToggleFormat = vm::toggleFormatFilter,
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
    activeFormats: Set<String>,
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
            Format.ALL.filter { it in activeFormats }.forEach { code ->
                AssistChip(
                    onClick = onClear,
                    label = { Text(Format.label(code)) },
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
private fun EmptyState(filtered: Boolean, searching: Boolean) {
    val (title, sub) = when {
        searching && filtered -> "No matches" to "Try a different filter or search"
        searching             -> "No matches" to "Try a different search term"
        filtered              -> "Nothing matches this filter" to "Try a different combination"
        else                  -> "Nothing here yet" to "Tap + to scan or search"
    }
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = sub,
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
    selectedFormats: Set<String>,
    onToggle: (String) -> Unit,
    onToggleFormat: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filter", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onClear() }) { Text("Reset") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
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
        Spacer(Modifier.height(16.dp))
        Text("Format", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Format.ALL.forEach { code ->
                val on = code in selectedFormats
                FilterChip(
                    selected = on,
                    onClick = { onToggleFormat(code) },
                    label = { Text(Format.label(code)) },
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
