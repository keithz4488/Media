package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.Format
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.Platform
import com.kzaller.shelf.data.SteamClient
import com.kzaller.shelf.data.SteamGame
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.Status
import com.kzaller.shelf.data.models.CreateItemRequest
import com.kzaller.shelf.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SteamImportState {
    data object Idle : SteamImportState
    data object Scanning : SteamImportState
    data class Importing(val total: Int) : SteamImportState
    data class Done(val imported: Int) : SteamImportState
    data class Error(val message: String) : SteamImportState
}

class SteamImportViewModel(
    private val repo: ShelfRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val steam = SteamClient()

    private val _state = MutableStateFlow<SteamImportState>(SteamImportState.Idle)
    val state: StateFlow<SteamImportState> = _state.asStateFlow()

    val savedKey = MutableStateFlow("")
    val savedId = MutableStateFlow("")

    // "Sync now" (manual trigger of the same job the daily cron runs) + backend connection status.
    val syncing = MutableStateFlow(false)
    val syncMessage = MutableStateFlow<String?>(null)
    val backendConnected = MutableStateFlow(false)
    val backendGames = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            savedKey.value = prefs.observeSteamKey().first()
            savedId.value = prefs.observeSteamId().first()
            refreshStatus()
        }
    }

    private suspend fun refreshStatus() {
        repo.steamStatus().onSuccess {
            backendConnected.value = it.connected
            backendGames.value = it.games
        }
    }

    /** Manually run the Steam sync now and report how many new games landed. */
    fun syncNow() {
        viewModelScope.launch {
            syncing.value = true
            syncMessage.value = null
            repo.syncSteam()
                .onSuccess { added ->
                    syncMessage.value =
                        if (added > 0) "Added $added new game${if (added == 1) "" else "s"}"
                        else "Up to date — no new games"
                    if (added > 0) repo.refresh(MediaKind.GAME, force = true)
                    refreshStatus()
                }
                .onFailure { syncMessage.value = it.message ?: "Sync failed" }
            syncing.value = false
        }
    }

    fun connectAndImport(apiKey: String, idOrVanity: String) {
        viewModelScope.launch {
            _state.value = SteamImportState.Scanning
            val steamId = runCatching {
                steam.resolveSteamId(apiKey, idOrVanity)
            }.getOrElse {
                _state.value = SteamImportState.Error(it.message ?: "Couldn't reach Steam")
                return@launch
            }
            // Persist the RESOLVED 64-bit id (not the raw vanity/URL) so the achievements API,
            // which can't take a vanity name, works later without re-resolving.
            prefs.setSteam(apiKey, steamId)
            // Register the credentials with the backend so its daily cron can auto-add new
            // purchases. Best-effort: a failure here shouldn't block the import.
            repo.saveSteamConfig(apiKey, steamId)
            val games = runCatching {
                steam.fetchOwnedGames(apiKey, steamId)
            }.getOrElse {
                _state.value = SteamImportState.Error(it.message ?: "Couldn't reach Steam")
                return@launch
            }
            if (games.isEmpty()) {
                // Almost always a privacy setting rather than an empty library.
                _state.value = SteamImportState.Error("No games found — is your Steam profile's \"Game details\" set to Public?")
                return@launch
            }
            _state.value = SteamImportState.Importing(games.size)
            val requests = games.map { it.toRequest() }
            val inserted = repo.bulkImport(requests).getOrElse {
                _state.value = SteamImportState.Error(it.message ?: "Import failed")
                return@launch
            }
            repo.refresh(MediaKind.GAME, force = true)
            _state.value = SteamImportState.Done(inserted)
        }
    }

    private fun SteamGame.toRequest(): CreateItemRequest {
        // Everything from Steam is a PC game you own digitally; playtime > 0 means you've played it.
        val statuses = buildList {
            add(Status.OWNED)
            if (playtimeMinutes > 0) add(Status.PLAYED)
        }
        return CreateItemRequest(
            kind = MediaKind.GAME,
            title = name,
            subtitle = "PC",
            coverUrl = coverUrl,
            externalId = appId.toString(),
            externalSrc = "steam",
            status = statuses.joinToString(","),
            format = Format.DIGITAL,
            userPlatform = Platform.PC,
        )
    }

    companion object {
        fun factory(repo: ShelfRepository, prefs: AppPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamImportViewModel(repo, prefs) as T
            }
    }
}
