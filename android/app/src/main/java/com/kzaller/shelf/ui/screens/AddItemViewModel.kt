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

    private val _bulkMode = MutableStateFlow(false)
    val bulkMode = _bulkMode.asStateFlow()

    /** Tracks whether the user's current SEARCH session originated from the camera flow,
     *  so a bulk add can return them to the camera instead of the bare search box. */
    private val _fromCamera = MutableStateFlow(false)

    private var searchJob: Job? = null

    fun goTo(mode: Mode) {
        if (mode != Mode.SEARCH && mode != Mode.MANUAL) _fromCamera.value = false
        if (mode == Mode.SEARCH || mode == Mode.MANUAL) {
            // Explicit user navigation into search/manual -- not from camera.
            _fromCamera.value = false
        }
        _mode.value = mode
    }
    fun setBulkMode(on: Boolean) { _bulkMode.value = on }

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
        _fromCamera.value = true
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
        _fromCamera.value = true
        val cleaned = text.lines().joinToString(" ") { it.trim() }.take(60)
        if (cleaned.isBlank()) return
        _query.value = cleaned
        _mode.value = Mode.SEARCH
        searchNow()
    }

    /** Called from CameraScreen when neither barcode nor OCR worked; the captured JPEG
     *  frame goes to Claude vision for "what is this?" identification. */
    fun onIdentify(jpegBytes: ByteArray) {
        _fromCamera.value = true
        viewModelScope.launch {
            _searching.value = true
            _statusMsg.value = "Identifying with AI…"
            repo.identify(jpegBytes)
                .onSuccess { result ->
                    if (result.kind == "unknown" || result.title.isBlank()) {
                        _statusMsg.value = "Couldn't identify -- try search instead"
                        _mode.value = Mode.SEARCH
                    } else {
                        // Use the identified title to drive a search against the right API.
                        _query.value = result.title
                        _mode.value = Mode.SEARCH
                        _statusMsg.value = "Identified: ${result.title}"
                        searchNow()
                    }
                }
                .onFailure {
                    _error.value = it.message ?: "identify failed"
                    _mode.value = Mode.SEARCH
                }
            _searching.value = false
        }
    }

    fun add(hit: SearchHit, status: String, after: () -> Unit) {
        viewModelScope.launch {
            _searching.value = true
            repo.add(kind, hit, status)
                .onSuccess { dto ->
                    if (_bulkMode.value) {
                        _statusMsg.value = "Added \"${dto.title}\""
                        _query.value = ""
                        _hits.value = emptyList()
                        // Camera-originated bulk adds go back to the camera so the next
                        // scan can fire immediately; pure search bulk stays in search.
                        if (_fromCamera.value) {
                            _mode.value = Mode.CAMERA
                            _fromCamera.value = false // next scan starts a fresh attempt
                        } else {
                            _mode.value = Mode.SEARCH
                        }
                    } else {
                        after()
                    }
                }
                .onFailure { _error.value = it.message }
            _searching.value = false
        }
    }

    fun addManual(title: String, subtitle: String?, year: Int?, status: String, after: () -> Unit) {
        viewModelScope.launch {
            _searching.value = true
            repo.addManual(kind, title.trim(), subtitle?.trim()?.ifBlank { null }, year, null, status)
                .onSuccess { dto ->
                    if (_bulkMode.value) {
                        _statusMsg.value = "Added \"${dto.title}\""
                        _query.value = ""
                        _hits.value = emptyList()
                        // Manual entry always stays in SEARCH/MANUAL flow.
                        _mode.value = Mode.MANUAL
                    } else {
                        after()
                    }
                }
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
