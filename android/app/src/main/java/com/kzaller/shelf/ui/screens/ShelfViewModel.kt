package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.Format
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.Status
import com.kzaller.shelf.data.models.ItemDto
import com.kzaller.shelf.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    RECENT("Recently added"),
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A"),
    YEAR_DESC("Year (newest)"),
    YEAR_ASC("Year (oldest)"),
}

enum class ViewMode { GRID, LIST }

class ShelfViewModel(
    private val repo: ShelfRepository,
    private val prefs: AppPreferences,
    private val kind: MediaKind,
) : ViewModel() {

    private val _filters = MutableStateFlow<Set<String>>(emptySet())
    val filters: StateFlow<Set<String>> = _filters.asStateFlow()

    private val _formatFilters = MutableStateFlow<Set<String>>(emptySet())
    val formatFilters: StateFlow<Set<String>> = _formatFilters.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Sort mode persists per-kind in DataStore so it survives app restarts. */
    val sort: StateFlow<SortMode> =
        prefs.observeSort(kind).stateIn(viewModelScope, SharingStarted.Eagerly, SortMode.RECENT)

    /** Grid vs list view also persists per-kind so different shelves can prefer different layouts. */
    val viewMode: StateFlow<ViewMode> =
        prefs.observeViewMode(kind).stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.GRID)

    /** Items visible on the shelf: kind -> status + format filter -> text search -> sort. */
    val items: StateFlow<List<ItemDto>> =
        combine(repo.observeShelf(kind), _filters, _formatFilters, _query, sort) { all, filters, formatFilters, query, sort ->
            // Defensive: the DAO already filters by kind in SQL, but enforce here too
            // so a stale emission can't slip a different-kind item into the grid.
            val ofKind = all.filter { it.kind == kind }

            val statusFiltered = if (filters.isEmpty()) ofKind
                else ofKind.filter { item ->
                    val s = Status.parse(item.status)
                    // Real statuses match by intersection; the pseudo-filters ("Not Watched",
                    // "Show To") match on other fields. Everything combines as OR, like the rest.
                    val regular = filters - Status.NOT_WATCHED - Status.HAS_SHOW_TO
                    val matchesRegular = regular.any { it in s }
                    val matchesNotWatched = Status.NOT_WATCHED in filters && Status.WATCHED !in s
                    val matchesShowTo = Status.HAS_SHOW_TO in filters && !item.showTo.isNullOrBlank()
                    matchesRegular || matchesNotWatched || matchesShowTo
                }

            val formatFiltered = if (formatFilters.isEmpty()) statusFiltered
                else statusFiltered.filter { item ->
                    val f = Format.parse(item.format)
                    f.any { it in formatFilters }
                }

            val searched = if (query.isBlank()) formatFiltered
                else formatFiltered.filter { item ->
                    item.title.contains(query, ignoreCase = true) ||
                        (item.subtitle?.contains(query, ignoreCase = true) == true)
                }

            when (sort) {
                SortMode.RECENT     -> searched.sortedByDescending { it.addedAt ?: 0L }
                SortMode.TITLE_ASC  -> searched.sortedBy { it.title.lowercase() }
                SortMode.TITLE_DESC -> searched.sortedByDescending { it.title.lowercase() }
                SortMode.YEAR_DESC  -> searched.sortedByDescending { it.year ?: Int.MIN_VALUE }
                SortMode.YEAR_ASC   -> searched.sortedBy { it.year ?: Int.MAX_VALUE }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Full unfiltered count, for a "showing X of Y" label when filters/search are active. */
    val totalCount: StateFlow<Int> =
        repo.observeShelf(kind)
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** Set of selected item ids; non-empty means selection mode is active. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    init { refresh() }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _refreshing.value = true
            repo.refresh(kind, force).onFailure { _error.value = it.message }
            _refreshing.value = false
        }
    }

    fun toggleFilter(code: String) {
        _filters.value = _filters.value.toMutableSet().apply {
            if (!add(code)) remove(code)
        }
    }

    fun toggleFormatFilter(code: String) {
        _formatFilters.value = _formatFilters.value.toMutableSet().apply {
            if (!add(code)) remove(code)
        }
    }

    fun clearFilters() {
        _filters.value = emptySet()
        _formatFilters.value = emptySet()
    }

    fun setSearch(q: String) { _query.value = q }
    fun clearSearch() { _query.value = "" }

    fun setSort(mode: SortMode) {
        viewModelScope.launch { prefs.setSort(kind, mode) }
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch { prefs.setViewMode(kind, mode) }
    }

    fun toggleSelection(id: String) {
        _selection.value = _selection.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun clearSelection() { _selection.value = emptySet() }

    fun deleteSelected() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.deleteMany(ids).onFailure { _error.value = it.message }
            _selection.value = emptySet()
        }
    }

    fun setStatusForSelected(status: String) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.setStatusMany(ids, status).onFailure { _error.value = it.message }
            _selection.value = emptySet()
        }
    }

    fun clearError() { _error.value = null }

    companion object {
        fun factory(repo: ShelfRepository, prefs: AppPreferences, kind: MediaKind): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ShelfViewModel(repo, prefs, kind) as T
            }
    }
}
