package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kzaller.shelf.data.Console
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.Platform
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.Status
import com.kzaller.shelf.data.models.CoverOption
import com.kzaller.shelf.ui.components.ShelfBackground
import com.kzaller.shelf.ui.components.shelfTextFieldColors
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

/**
 * Public entry point. Wraps a HorizontalPager over the shelf's items so swiping left/right
 * navigates between siblings. Each page owns its own DetailViewModel keyed by item id, so
 * its loading state (covers, refresh) doesn't bleed across pages.
 */
@Composable
fun DetailScreen(
    initialId: String,
    kind: MediaKind,
    repo: ShelfRepository,
    onBack: () -> Unit,
) {
    val items by repo.observeShelf(kind).collectAsState(initial = emptyList())
    if (items.isEmpty()) return // initial load hasn't populated cache yet; brief blank
    val initialIndex = items.indexOfFirst { it.id == initialId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }

    HorizontalPager(
        state = pagerState,
        key = { idx -> items[idx].id },
    ) { page ->
        val pageItem = items[page]
        val vm: DetailViewModel = viewModel(
            key = "detail-${pageItem.id}",
            factory = DetailViewModel.factory(repo, pageItem.id),
        )
        DetailPage(vm = vm, onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailPage(vm: DetailViewModel, onBack: () -> Unit) {
    val item by vm.item.collectAsState()
    val current = item
    val error by vm.error.collectAsState()
    val toast by vm.toast.collectAsState()
    val covers by vm.covers.collectAsState()
    val loadingCovers by vm.loadingCovers.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var coverSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    LaunchedEffect(toast) { toast?.let { snackbar.showSnackbar(it); vm.clearToast() } }

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
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh details")
                        }
                        IconButton(onClick = { vm.delete(after = onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = flavor.accent,
                        actionIconContentColor = flavor.accent,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
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
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            coverSheetOpen = true
                            vm.loadCovers()
                        }) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Change cover")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
                    Text("Status", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    val selectedStatuses = Status.parse(current.status)
                    val options = Status.optionsFor(current.kind)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { s ->
                            val on = s in selectedStatuses
                            AssistChip(
                                onClick = { vm.setStatus(Status.toggle(current.status, s)) },
                                label = {
                                    Text(
                                        text = Status.label(s, current.kind),
                                        color = if (on) flavor.accent else MaterialTheme.colorScheme.onBackground,
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (on) flavor.accent.copy(alpha = 0.22f) else Color.Transparent,
                                ),
                            )
                        }
                    }

                    if (current.kind == MediaKind.GAME) {
                        Spacer(Modifier.height(16.dp))
                        Text("Platform", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        val selectedPlatforms = Platform.parse(current.userPlatform)
                        val selectedConsoles = Console.parse(current.consoles)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Platform.ALL.forEach { p ->
                                val on = p in selectedPlatforms
                                AssistChip(
                                    onClick = {
                                        val newPlatforms = Platform.toggle(current.userPlatform, p)
                                        // If a platform got removed, strip its consoles too.
                                        val newPlatformSet = Platform.parse(newPlatforms)
                                        val newConsoles = Console.pruneToPlatforms(current.consoles, newPlatformSet)
                                        if (newConsoles == (current.consoles ?: "")) {
                                            vm.setPlatform(newPlatforms)
                                        } else {
                                            vm.setPlatformAndConsoles(newPlatforms, newConsoles)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = Platform.label(p),
                                            color = if (on) flavor.accent else MaterialTheme.colorScheme.onBackground,
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (on) flavor.accent.copy(alpha = 0.22f) else Color.Transparent,
                                    ),
                                )
                            }
                        }
                        // Console sub-sections: only for selected platforms that have any
                        // sub-consoles. PC and Mobile stay flat (no dropdown).
                        Platform.ALL.forEach { p ->
                            if (p !in selectedPlatforms) return@forEach
                            val consoles = Console.forPlatform(p)
                            if (consoles.isEmpty()) return@forEach
                            Spacer(Modifier.height(10.dp))
                            ConsoleDropdown(
                                platform = p,
                                consoles = consoles,
                                selected = selectedConsoles,
                                accent = flavor.accent,
                                onToggle = { code ->
                                    vm.setConsoles(Console.toggle(current.consoles, code))
                                },
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

                    Spacer(Modifier.height(16.dp))
                    CompletedSection(
                        completedAt = current.completedAt,
                        onSet = { vm.setCompletedAt(it) },
                    )

                    Spacer(Modifier.height(8.dp))
                    NotesSection(
                        savedNotes = current.notes,
                        onSave = { vm.setNotes(it) },
                    )

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

        if (coverSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { coverSheetOpen = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                CoverPickerSheet(
                    covers = covers,
                    loading = loadingCovers,
                    onPick = { url ->
                        vm.setCover(url)
                        coverSheetOpen = false
                    },
                    onClose = { coverSheetOpen = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverPickerSheet(
    covers: List<CoverOption>,
    loading: Boolean,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pick a cover", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            covers.isEmpty() -> Text(
                text = "No alternate covers available for this item.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 480.dp),
            ) {
                // Key by index, not url. Duplicate urls can sneak through if upstream
                // sources hand back the same image; using url as the key crashes the
                // grid when the dupe scrolls into view.
                itemsIndexed(covers) { _, option ->
                    Card(
                        onClick = { onPick(option.url) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .background(Color.Black.copy(alpha = 0.25f)),
                            ) {
                                AsyncImage(
                                    model = option.url,
                                    contentDescription = option.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Per-platform console selector. Selected consoles show as compact chips you can tap-to-
 * remove (X icon). A trailing "+" chip opens a DropdownMenu that lists ONLY the consoles
 * *not* already picked -- so the menu shrinks as you select and the user never sees a
 * console they've already added in the list of options.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConsoleDropdown(
    platform: String,
    consoles: List<String>,
    selected: Set<String>,
    accent: Color,
    onToggle: (String) -> Unit,
) {
    val picked = consoles.filter { it in selected }
    val unpicked = consoles.filter { it !in selected }
    var menuOpen by remember(platform) { mutableStateOf(false) }

    Text(
        text = Platform.label(platform),
        style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        ),
    )
    Spacer(Modifier.height(4.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        picked.forEach { code ->
            AssistChip(
                onClick = { onToggle(code) }, // tap a selected chip to remove it
                label = { Text(Console.label(code), color = accent) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(14.dp),
                        tint = accent,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = accent.copy(alpha = 0.22f),
                ),
            )
        }
        if (unpicked.isNotEmpty()) {
            Box {
                AssistChip(
                    onClick = { menuOpen = true },
                    label = { Text(if (picked.isEmpty()) "+ Add" else "+") },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    unpicked.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(Console.label(code)) },
                            onClick = {
                                onToggle(code)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Completion-date editor. Tap "Mark complete" to set to today; tap the date to open a
 * picker and choose a different one. Stored as epoch ms in the DB so we can do "year in
 * review" math without timezone games.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompletedSection(
    completedAt: Long?,
    onSet: (Long?) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    Text("Completed", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (completedAt == null) {
            AssistChip(
                onClick = { onSet(System.currentTimeMillis()) },
                label = { Text("Mark complete") },
                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = { pickerOpen = true }) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Pick a date")
            }
        } else {
            AssistChip(
                onClick = { pickerOpen = true },
                label = { Text(formatDate(completedAt)) },
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }

    if (pickerOpen) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = completedAt ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onSet(it) }
                    pickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun formatDate(epochMs: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
    return date.format(fmt)
}

/**
 * Notes start in read-only display mode. "Edit" enters editing; "Save" commits + returns
 * to read-only, "Cancel" discards. If there are no notes yet, an "Add notes" affordance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesSection(
    savedNotes: String?,
    onSave: (String) -> Unit,
) {
    var editing by remember(savedNotes) { mutableStateOf(false) }
    var draft by remember(savedNotes) { mutableStateOf(savedNotes.orEmpty()) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Notes", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(8.dp))
        if (!editing) {
            TextButton(onClick = { editing = true; draft = savedNotes.orEmpty() }) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (savedNotes.isNullOrBlank()) "Add" else "Edit")
            }
        }
    }
    Spacer(Modifier.height(4.dp))

    if (editing) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            colors = shelfTextFieldColors(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { editing = false; draft = savedNotes.orEmpty() }) {
                Text("Cancel")
            }
            Button(onClick = { onSave(draft); editing = false }) {
                Text("Save")
            }
        }
    } else {
        if (savedNotes.isNullOrBlank()) {
            Text(
                text = "No notes yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        } else {
            Text(
                text = savedNotes,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(12.dp),
            )
        }
    }
}
