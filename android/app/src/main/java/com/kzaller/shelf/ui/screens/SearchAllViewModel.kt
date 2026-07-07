package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.models.ItemDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchAllViewModel(private val repo: ShelfRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Optional shelf filter; null means "all shelves". */
    private val _kindFilter = MutableStateFlow<MediaKind?>(null)
    val kindFilter: StateFlow<MediaKind?> = _kindFilter.asStateFlow()

    /** Search results across every shelf, debounced to avoid hammering Room. Blank query
     *  returns an empty list (vs. dumping the whole library). Narrowed by the shelf filter. */
    val results: StateFlow<List<ItemDto>> =
        combine(_query.debounce(150), _kindFilter) { q, kind -> q to kind }
            .flatMapLatest { (q, kind) ->
                if (q.isBlank()) flowOf(emptyList())
                else repo.observeAll().map { all ->
                    all.asSequence().filter { item ->
                        (kind == null || item.kind == kind) &&
                            (item.title.contains(q, ignoreCase = true) ||
                                (item.subtitle?.contains(q, ignoreCase = true) == true))
                    }.take(200).toList()
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Make sure the local cache has fresh data before someone types.
        viewModelScope.launch { repo.refreshAll() }
    }

    fun setQuery(q: String) { _query.value = q }

    /** Tap the same kind again to clear back to "all". */
    fun setKindFilter(kind: MediaKind?) {
        _kindFilter.value = if (_kindFilter.value == kind) null else kind
    }

    companion object {
        fun factory(repo: ShelfRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SearchAllViewModel(repo) as T
            }
    }
}
