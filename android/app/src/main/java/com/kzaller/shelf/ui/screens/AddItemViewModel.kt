package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.models.SearchHit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddItemViewModel(
    private val repo: ShelfRepository,
    val kind: MediaKind,
) : ViewModel() {

    enum class Mode { CHOOSE, CAMERA, SEARCH, MANUAL }

    private val _mode = MutableStateFlow(Mode.CHOOSE)
    val mode = _mode.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _hits = MutableStateFlow<List<SearchHit>>(emptyList())
    val hits = _hits.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _statusMsg = MutableStateFlow<String?>(null)
    val statusMsg = _statusMsg.asStateFlow()

    private var searchJob: Job? = null

    fun goTo(mode: Mode) { _mode.value = mode }

    fun setQuery(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { _hits.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            runSearch(q)
        }
    }

    fun searchNow() {
        searchJob?.cancel()
        if (_query.value.isNotBlank()) viewModelScope.launch { runSearch(_query.value) }
    }

    private suspend fun runSearch(q: String) {
        _searching.value = true
        _error.value = null
        repo.search(kind, q)
            .onSuccess { _hits.value = it }
            .onFailure { _error.value = it.message ?: "search failed" }
        _searching.value = false
    }

    /** Called from CameraScreen when ML Kit reads a barcode. */
    fun onBarcode(value: String) {
        viewModelScope.launch {
            _searching.value = true
            _statusMsg.value = "Looking up $value"
            val result = if (kind == MediaKind.BOOK) {
                repo.lookupBookByIsbn(value)
            } else {
                repo.search(kind, value)
            }
            result.onSuccess { hits ->
                if (hits.isNotEmpty()) {
                    _hits.value = hits
                    _mode.value = Mode.SEARCH
                    _statusMsg.value = if (hits.size == 1) "Found 1 match" else "Found ${hits.size} matches"
                } else {
                    _query.value = value
                    _mode.value = Mode.SEARCH
                    _statusMsg.value = "No match for $value -- try refining"
                }
            }.onFailure { _error.value = it.message }
            _searching.value = false
        }
    }

    /** Called from CameraScreen when ML Kit reads text. */
    fun onText(text: String) {
        val cleaned = text.lines().joinToString(" ") { it.trim() }.take(60)
        if (cleaned.isBlank()) return
        _query.value = cleaned
        _mode.value = Mode.SEARCH
        searchNow()
    }

    fun add(hit: SearchHit, status: String, after: () -> Unit) {
        viewModelScope.launch {
            _searching.value = true
            repo.add(kind, hit, status)
                .onSuccess { after() }
                .onFailure { _error.value = it.message }
            _searching.value = false
        }
    }

    fun addManual(title: String, subtitle: String?, year: Int?, status: String, after: () -> Unit) {
        viewModelScope.launch {
            _searching.value = true
            repo.addManual(kind, title.trim(), subtitle?.trim()?.ifBlank { null }, year, null, status)
                .onSuccess { after() }
                .onFailure { _error.value = it.message }
            _searching.value = false
        }
    }

    fun clearStatusMsg() { _statusMsg.value = null }
    fun clearError() { _error.value = null }

    companion object {
        fun factory(repo: ShelfRepository, kind: MediaKind): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AddItemViewModel(repo, kind) as T
            }
    }
}
