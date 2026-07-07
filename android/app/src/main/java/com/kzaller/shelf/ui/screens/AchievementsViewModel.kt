package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.Achievement
import com.kzaller.shelf.data.AchievementStats
import com.kzaller.shelf.data.Achievements
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AchievementUi(
    val achievement: Achievement,
    val current: Int,
    val unlocked: Boolean,
)

class AchievementsViewModel(
    private val repo: ShelfRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    val ui: StateFlow<List<AchievementUi>> =
        repo.observeAll().map { items ->
            val s = AchievementStats.from(items)
            Achievements.ALL.map { AchievementUi(it, it.current(s), it.unlocked(s)) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** FIFO queue of achievements just unlocked, surfaced as toasts on the home screen. */
    private val _queue = MutableStateFlow<List<Achievement>>(emptyList())
    val queue: StateFlow<List<Achievement>> = _queue.asStateFlow()

    init {
        viewModelScope.launch {
            // Load the real library from the server BEFORE we seed/diff. Otherwise the first
            // emission is the empty local cache: we'd bank an empty set, then treat the whole
            // library as "newly unlocked" the moment it loads — a toast storm on every fresh
            // install (which is exactly when the DataStore "seeded" flag is missing).
            repo.refreshAll()

            val catalog = Achievements.ALL.map { it.id }.toSet()
            // Seeding is silent, so a reinstall quietly banks everything already earned and
            // only achievements crossed AFTER this point ever toast.
            var seeded = prefs.observeAchievementsSeeded().first()
            var known = prefs.observeKnownAchievements().first()
            // Back-compat: an install seeded before this feature has no known set; treat the
            // whole current catalog as already-known so adding achievements doesn't storm.
            var knownReady = known.isNotEmpty()

            repo.observeAll().collect { items ->
                val computed = Achievements.unlockedIds(AchievementStats.from(items))
                if (!seeded) {
                    prefs.setUnlockedAchievements(computed)
                    prefs.setAchievementsSeeded()
                    prefs.setKnownAchievements(catalog)
                    seeded = true; known = catalog; knownReady = true
                    return@collect
                }
                val stored = prefs.observeUnlockedAchievements().first()
                // Achievements newly ADDED to the app since last run: silently bank any the
                // user already qualifies for, so catalog growth never fires a banner.
                val brandNew = if (knownReady) catalog - known else catalog
                val silentBank = computed intersect brandNew
                val effectiveStored = stored + silentBank
                val fresh = computed - effectiveStored
                if (silentBank.isNotEmpty() || fresh.isNotEmpty()) {
                    prefs.setUnlockedAchievements(effectiveStored + computed)
                }
                if (fresh.isNotEmpty()) {
                    _queue.value = _queue.value + Achievements.ALL.filter { it.id in fresh }
                }
                if (!knownReady || known != catalog) {
                    prefs.setKnownAchievements(catalog)
                    known = catalog; knownReady = true
                }
            }
        }
    }

    fun consume() { _queue.value = _queue.value.drop(1) }

    companion object {
        fun factory(repo: ShelfRepository, prefs: AppPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AchievementsViewModel(repo, prefs) as T
            }
    }
}
