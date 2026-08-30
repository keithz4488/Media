package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.Format
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.PlexClient
import com.kzaller.shelf.data.PlexItem
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.models.CreateItemRequest
import com.kzaller.shelf.data.models.SearchHit
import com.kzaller.shelf.data.preferences.AppPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A Plex item whose match wasn't confident; the user picks the right candidate (or skips). */
data class AmbiguousMatch(
    val plex: PlexItem,
    val candidates: List<SearchHit>,
    val chosen: SearchHit? = null,
    val skipped: Boolean = false,
)

sealed interface ImportState {
    data object Idle : ImportState
    data object Scanning : ImportState
    data class Matching(val done: Int, val total: Int) : ImportState
    data class Review(val ambiguous: List<AmbiguousMatch>, val confirmed: Int) : ImportState
    data class Importing(val done: Int, val total: Int) : ImportState
    data class Done(val imported: Int, val skipped: Int) : ImportState
    data class Error(val message: String) : ImportState
}

class ImportViewModel(
    private val repo: ShelfRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val plex = PlexClient()

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    val savedUrl = MutableStateFlow("")
    val savedToken = MutableStateFlow("")

    /** This user's personal Plex webhook URL for live sync (auto-add new movies/shows). */
    val webhookUrl = MutableStateFlow<String?>(null)

    // Confirmed requests accumulate as matching runs; ambiguous ones await review.
    private val confirmed = mutableListOf<CreateItemRequest>()
    private var ambiguous = mutableListOf<AmbiguousMatch>()
    private var skippedCount = 0

    init {
        viewModelScope.launch {
            savedUrl.value = prefs.observePlexUrl().first()
            savedToken.value = prefs.observePlexToken().first()
        }
        // Fetch this user's personal live-sync webhook URL for the setup card.
        viewModelScope.launch {
            repo.plexConfig().onSuccess { webhookUrl.value = it.url }
        }
    }

    fun connectAndScan(url: String, token: String) {
        viewModelScope.launch {
            prefs.setPlex(url, token)
            // Register whose Plex account this is while we're certainly able to reach the server,
            // so live sync ignores playback by everyone else who can see the library.
            repo.registerPlexAccount(url, token)
            confirmed.clear(); ambiguous = mutableListOf(); skippedCount = 0
            _state.value = ImportState.Scanning
            val items = runCatching { plex.fetchLibrary(url, token) }
                .getOrElse { _state.value = ImportState.Error(it.message ?: "Couldn't reach Plex"); return@launch }
            if (items.isEmpty()) { _state.value = ImportState.Error("No movies or shows found"); return@launch }
            matchAll(items)
        }
    }

    private suspend fun matchAll(items: List<PlexItem>) {
        _state.value = ImportState.Matching(0, items.size)
        val gate = Semaphore(6)
        var done = 0
        coroutineScope {
            items.map { item ->
                async {
                    gate.withPermit { resolve(item) }
                    synchronized(this@ImportViewModel) {
                        done++
                        _state.value = ImportState.Matching(done, items.size)
                    }
                }
            }.awaitAll()
        }
        if (ambiguous.isEmpty()) doImport()
        else _state.value = ImportState.Review(ambiguous.toList(), confirmed.size)
    }

    private suspend fun resolve(item: PlexItem) {
        // 1) Exact match via the Plex-provided TMDB id.
        if (item.tmdbId != null) {
            val hit = repo.searchTmdbById(item.kind, item.tmdbId).getOrNull()
            if (hit != null) { addConfirmed(item, hit); return }
        }
        // 2) Fall back to a title search.
        val hits = repo.search(item.kind, item.title).getOrNull().orEmpty()
        val top = hits.firstOrNull()
        val yearOk = item.year == null || top?.year == null || kotlin.math.abs((top.year) - item.year) <= 1
        if (top != null && yearOk) {
            addConfirmed(item, top)
        } else {
            synchronized(this@ImportViewModel) {
                ambiguous.add(AmbiguousMatch(item, hits.take(6)))
            }
        }
    }

    private fun addConfirmed(item: PlexItem, hit: SearchHit) {
        synchronized(this@ImportViewModel) { confirmed.add(hit.toRequest(item)) }
    }

    fun chooseCandidate(index: Int, hit: SearchHit) {
        ambiguous = ambiguous.toMutableList().also { it[index] = it[index].copy(chosen = hit, skipped = false) }
        _state.value = ImportState.Review(ambiguous.toList(), confirmed.size)
    }

    fun skipCandidate(index: Int) {
        ambiguous = ambiguous.toMutableList().also { it[index] = it[index].copy(skipped = true, chosen = null) }
        _state.value = ImportState.Review(ambiguous.toList(), confirmed.size)
    }

    fun finishReviewAndImport() {
        ambiguous.forEach { a ->
            when {
                a.chosen != null -> confirmed.add(a.chosen.toRequest(a.plex))
                else -> skippedCount++
            }
        }
        viewModelScope.launch { doImport() }
    }

    private suspend fun doImport() {
        val requests = confirmed.toList()
        if (requests.isEmpty()) { _state.value = ImportState.Done(0, skippedCount); return }
        _state.value = ImportState.Importing(0, requests.size)
        val inserted = repo.bulkImport(requests).getOrElse {
            _state.value = ImportState.Error(it.message ?: "Import failed"); return
        }
        // Pull fresh data so the new items show on the shelves (bypass the refresh throttle).
        repo.refreshAll(force = true)
        _state.value = ImportState.Done(inserted, skippedCount)
    }

    private fun SearchHit.toRequest(plex: PlexItem): CreateItemRequest {
        // Everything imported is digitally owned; also carry over Plex's watched/watching state.
        val statuses = listOfNotNull("owned", plex.watchedStatus)
        return CreateItemRequest(
            kind = plex.kind,
            title = title,
            subtitle = subtitle,
            year = year,
            coverUrl = coverUrl,
            externalId = externalId,
            externalSrc = externalSrc,
            description = description,
            status = statuses.joinToString(","),
            format = Format.DIGITAL,
        )
    }

    companion object {
        fun factory(repo: ShelfRepository, prefs: AppPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ImportViewModel(repo, prefs) as T
            }
    }
}
