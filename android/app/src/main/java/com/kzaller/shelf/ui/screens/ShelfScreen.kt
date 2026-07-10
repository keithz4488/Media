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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.material3.HorizontalDivider
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
import com.kzaller.shelf.data.Console
import com.kzaller.shelf.data.Format
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.Platform
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
        val activePlatformFilters by vm.platformFilters.collectAsState()
        val activeConsoleFilters by vm.consoleFilters.collectAsState()
        val totalActiveFilters = activeFilters.size + activeFormatFilters.size +
            activePlatformFilters.size + activeConsoleFilters.size
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
        var grouped by remember { mutableStateOf(false) }
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
                                                if (mode == sort && !grouped) Icon(Icons.Default.Check, contentDescription = null)
                                            },
                                            onClick = {
                                                vm.setSort(mode)
                                                grouped = false
                                                sortMenuOpen = false
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Group by series") },
                                        trailingIcon = {
                                            if (grouped) Icon(Icons.Default.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            grouped = !grouped
                                            sortMenuOpen = false
                                        },
                                    )
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
                            activePlatforms = activePlatformFilters,
                            activeConsoles = activeConsoleFilters,
                            shownCount = items.size,
                            totalCount = total,
                            onClear = vm::clearFilters,
                        )
                    }
                    // Pull-to-refresh wraps the grid (and the empty state) so the gesture
                    // works whether or not items are present.
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { vm.refresh(force = true) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (items.isEmpty()) {
                            EmptyState(
                                kind = kind,
                                filtered = anyFilterActive,
                                searching = query.isNotBlank(),
                            )
                        } else if (grouped) {
                            GroupedList(
                                items = items,
                                selection = selection,
                                inSelectionMode = inSelectionMode,
                                onItem = onItem,
                                onToggle = vm::toggleSelection,
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
                    selectedPlatforms = activePlatformFilters,
                    selectedConsoles = activeConsoleFilters,
                    onToggle = vm::toggleFilter,
                    onToggleFormat = vm::toggleFormatFilter,
                    onTogglePlatform = vm::togglePlatformFilter,
                    onToggleConsole = vm::toggleConsoleFilter,
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
    activePlatforms: Set<String>,
    activeConsoles: Set<String>,
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
            Status.filterOptionsFor(kind).filter { it in active }.forEach { code ->
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
            Platform.ALL.filter { it in activePlatforms }.forEach { code ->
                AssistChip(
                    onClick = onClear,
                    label = { Text(Platform.label(code)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        labelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Platform.ALL.flatMap { Console.forPlatform(it) }.filter { it in activeConsoles }.forEach { code ->
                AssistChip(
                    onClick = onClear,
                    label = { Text(Console.label(code)) },
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

/**
 * "Group by series" layout: a flat list broken into sections, one per detected series/franchise,
 * with a leftover "Standalone" section. Uses list rows (not the plank grid) so section headers
 * read cleanly.
 */
@Composable
private fun GroupedList(
    items: List<com.kzaller.shelf.data.models.ItemDto>,
    selection: Set<String>,
    inSelectionMode: Boolean,
    onItem: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    val groups = remember(items) { com.kzaller.shelf.data.Series.group(items) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        groups.forEach { g ->
            item(key = "hdr:${g.name ?: "standalone"}") {
                Text(
                    text = g.name ?: "Standalone",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            listItems(g.items, key = { "${it.kind.wire}:${it.id}" }) { item ->
                ItemListRow(
                    item = item,
                    selected = item.id in selection,
                    onClick = { clicked ->
                        if (inSelectionMode) onToggle(clicked.id) else onItem(clicked.id)
                    },
                    onLongClick = { clicked -> onToggle(clicked.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(kind: MediaKind, filtered: Boolean, searching: Boolean) {
    val (title, sub) = when {
        searching && filtered -> "No matches" to "Try a different filter or search"
        searching             -> "No matches" to "Try a different search term"
        filtered              -> "Nothing matches this filter" to "Try a different combination"
        else                  -> "Nothing here yet" to "Tap + to scan or search"
    }
    val art = when (kind) {
        MediaKind.BOOK  -> Icons.Default.MenuBook
        MediaKind.MOVIE -> Icons.Default.Movie
        MediaKind.TV    -> Icons.Default.Tv
        MediaKind.GAME  -> Icons.Default.SportsEsports
    }
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = art,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f),
                modifier = Modifier.size(120.dp),
            )
            Spacer(Modifier.height(12.dp))
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
    selectedPlatforms: Set<String>,
    selectedConsoles: Set<String>,
    onToggle: (String) -> Unit,
    onToggleFormat: (String) -> Unit,
    onTogglePlatform: (String) -> Unit,
    onToggleConsole: (String) -> Unit,
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
            Status.filterOptionsFor(kind).forEach { code ->
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
        // Platform + console are a games-only concept. Console chips cascade: they only appear
        // for the platforms currently selected, and only for platforms that have sub-consoles.
        if (kind == MediaKind.GAME) {
            Spacer(Modifier.height(16.dp))
            Text("Platform", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Platform.ALL.forEach { code ->
                    val on = code in selectedPlatforms
                    FilterChip(
                        selected = on,
                        onClick = { onTogglePlatform(code) },
                        label = { Text(Platform.label(code)) },
                        leadingIcon = if (on) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }

            val consoleOptions = selectedPlatforms
                .flatMap { Console.forPlatform(it) }
                .distinct()
            if (consoleOptions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Console", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Keep platform-grouped order for a predictable layout.
                    Platform.ALL.flatMap { Console.forPlatform(it) }
                        .filter { it in consoleOptions }
                        .forEach { code ->
                            val on = code in selectedConsoles
                            FilterChip(
                                selected = on,
                                onClick = { onToggleConsole(code) },
                                label = { Text(Console.label(code)) },
                                leadingIcon = if (on) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(),
                            )
                        }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done") }
        }
    }
}
