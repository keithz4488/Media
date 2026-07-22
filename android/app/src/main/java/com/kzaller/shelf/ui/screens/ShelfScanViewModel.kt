package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.Console
import com.kzaller.shelf.data.Format
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.models.CreateItemRequest
import com.kzaller.shelf.data.models.SearchHit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One item read off a shelf photo, with its best catalog match (null if nothing matched). */
data class ScannedItem(
    val readTitle: String,
    val kind: MediaKind,
    val match: SearchHit?,
    val include: Boolean,
)

sealed interface ScanState {
    data object Camera : ScanState
    data object Identifying : ScanState
    data class Matching(val done: Int, val total: Int) : ScanState
    data class Review(val items: List<ScannedItem>) : ScanState
    data object Importing : ScanState
    data class Done(val added: Int) : ScanState
    data class Error(val message: String) : ScanState
}

class ShelfScanViewModel(private val repo: ShelfRepository) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Camera)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** The photo just captured, shown behind the scanner animation while identifying/matching. */
    var capturedJpeg: ByteArray? = null
        private set

    private var items = listOf<ScannedItem>()

    /** Send the shelf photo to vision, then match each recognized title to the catalog. */
    fun scan(jpeg: ByteArray) {
        capturedJpeg = jpeg
        viewModelScope.launch {
            _state.value = ScanState.Identifying
            val found = repo.identifyShelf(jpeg).getOrElse {
                _state.value = ScanState.Error(it.message ?: "Couldn't scan the shelf"); return@launch
            }
            // Keep only entries with a known kind and a title.
            val recognizable = found.mapNotNull { r ->
                val kind = MediaKind.values().firstOrNull { it.wire == r.kind } ?: return@mapNotNull null
                if (r.title.isBlank()) null else (r.title to kind)
            }
            if (recognizable.isEmpty()) {
                _state.value = ScanState.Error(
                    "Couldn't read any titles. Try again with better lighting or a straighter angle.",
                )
                return@launch
            }

            _state.value = ScanState.Matching(0, recognizable.size)
            val gate = Semaphore(6)
            var done = 0
            val matched = coroutineScope {
                recognizable.map { (title, kind) ->
                    async {
                        val hit = gate.withPermit { repo.search(kind, title).getOrNull()?.firstOrNull() }
                        synchronized(this@ShelfScanViewModel) {
                            done++
                            _state.value = ScanState.Matching(done, recognizable.size)
                        }
                        ScannedItem(readTitle = title, kind = kind, match = hit, include = hit != null)
                    }
                }.awaitAll()
            }
            items = matched
            _state.value = ScanState.Review(items)
        }
    }

    fun toggle(index: Int) {
        items = items.toMutableList().also { it[index] = it[index].copy(include = !it[index].include) }
        _state.value = ScanState.Review(items)
    }

    fun rescan() {
        items = emptyList()
        capturedJpeg = null
        _state.value = ScanState.Camera
    }

    fun confirmAndAdd() {
        viewModelScope.launch {
            val requests = items.filter { it.include && it.match != null }.map(::toRequest)
            if (requests.isEmpty()) { _state.value = ScanState.Done(0); return@launch }
            _state.value = ScanState.Importing
            val added = repo.bulkImport(requests).getOrElse {
                _state.value = ScanState.Error(it.message ?: "Couldn't add the items"); return@launch
            }
            repo.refreshAll(force = true)
            _state.value = ScanState.Done(added)
        }
    }

    private fun toRequest(item: ScannedItem): CreateItemRequest {
        val hit = item.match!!
        // A physical shelf → mark items owned + physical; auto-fill a single-platform game's console.
        val (platform, consoles) =
            if (item.kind == MediaKind.GAME) Console.autoFill(hit.subtitle) else (null to null)
        return CreateItemRequest(
            kind = item.kind,
            title = hit.title,
            subtitle = hit.subtitle,
            year = hit.year,
            coverUrl = hit.coverUrl,
            externalId = hit.externalId,
            externalSrc = hit.externalSrc,
            description = hit.description,
            status = "owned",
            format = Format.PHYSICAL,
            userPlatform = platform,
            consoles = consoles,
        )
    }

    companion object {
        fun factory(repo: ShelfRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ShelfScanViewModel(repo) as T
            }
    }
}
