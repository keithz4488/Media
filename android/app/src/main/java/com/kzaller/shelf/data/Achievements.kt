package com.kzaller.shelf.data

import com.kzaller.shelf.data.models.ItemDto

/** A single unlockable achievement, defined declaratively against a stats snapshot. */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val target: Int,                 // for progress display
    val progress: (AchievementStats) -> Int,
) {
    fun current(stats: AchievementStats): Int = progress(stats).coerceAtMost(target)
    fun unlocked(stats: AchievementStats): Boolean = progress(stats) >= target
}

/** Everything the achievement predicates need, computed once from the full library. */
data class AchievementStats(
    val total: Int,
    val byKind: Map<MediaKind, Int>,
    val completed: Int,
    val rated: Int,
    val gamePlatforms: Int,
    val gamesComplete100: Int,
    val kindsWithItems: Int,
) {
    companion object {
        fun from(items: List<ItemDto>): AchievementStats {
            val byKind = items.groupBy { it.kind }.mapValues { it.value.size }
            val platforms = items
                .filter { it.kind == MediaKind.GAME }
                .flatMap { Platform.parse(it.userPlatform) }
                .toSet()
            val complete100 = items.count { it.kind == MediaKind.GAME && Status.parse(it.status).contains(Status.COMPLETE) }
            return AchievementStats(
                total = items.size,
                byKind = byKind,
                completed = items.count { it.completedAt != null },
                rated = items.count { (it.rating ?: 0) > 0 },
                gamePlatforms = platforms.size,
                gamesComplete100 = complete100,
                kindsWithItems = byKind.keys.size,
            )
        }
    }
}

object Achievements {
    val ALL: List<Achievement> = listOf(
        Achievement("first", "First Find", "Add your first item", "🌱", 1) { it.total },
        Achievement("collector", "Collector", "Own 50 items", "📚", 50) { it.total },
        Achievement("hoarder", "Hoarder", "Own 250 items", "🏛️", 250) { it.total },
        Achievement("well_rounded", "Well Rounded", "Have something on all 4 shelves", "🧭", 4) { it.kindsWithItems },
        Achievement("completionist", "Completionist", "Finish 10 items", "✅", 10) { it.completed },
        Achievement("marathoner", "Marathoner", "Finish 50 items", "🏅", 50) { it.completed },
        Achievement("critic", "Critic", "Rate 20 items", "⭐", 20) { it.rated },
        Achievement("bookworm", "Bookworm", "Shelve 25 books", "📖", 25) { it.byKind[MediaKind.BOOK] ?: 0 },
        Achievement("cinephile", "Cinephile", "Shelve 25 movies", "🎬", 25) { it.byKind[MediaKind.MOVIE] ?: 0 },
        Achievement("binger", "Binger", "Shelve 25 TV shows", "📺", 25) { it.byKind[MediaKind.TV] ?: 0 },
        Achievement("gamer", "Gamer", "Shelve 25 games", "🎮", 25) { it.byKind[MediaKind.GAME] ?: 0 },
        Achievement("platform_agnostic", "Platform Agnostic", "Own games on 3 platforms", "🕹️", 3) { it.gamePlatforms },
        Achievement("perfectionist", "Perfectionist", "100% five games", "💯", 5) { it.gamesComplete100 },
    )

    fun unlockedIds(stats: AchievementStats): Set<String> =
        ALL.filter { it.unlocked(stats) }.map { it.id }.toSet()
}
