package com.kzaller.shelf.data

/**
 * Statuses are stored as a CSV of lowercase wire codes (e.g. "owned,reading").
 * The set of *available* codes depends on the media kind, and each code has a
 * Title Case display label.
 */
object Status {
    const val OWNED    = "owned"
    const val WISHLIST = "wishlist"
    const val READING  = "reading"
    const val READ     = "read"
    const val WATCHING = "watching"
    const val WATCHED  = "watched"
    const val PLAYING  = "playing"
    const val PLAYED   = "played"
    const val COMPLETE = "complete"
    const val SEEN     = "seen" // legacy: older items may have this; still display it
    const val SHOW_TO  = "show_to" // movies/TV you've seen and want to show someone else

    /**
     * Pseudo status-filter (not a real status you can set on an item): a quick "backlog"
     * view matching anything that doesn't have `watched` checked. Only offered on shelves
     * where "Watched" is a status (movies + TV).
     */
    const val NOT_WATCHED = "not_watched"

    private val CANONICAL_ORDER = listOf(
        OWNED, READING, READ, WATCHING, WATCHED, SHOW_TO, PLAYING, PLAYED, COMPLETE, SEEN, WISHLIST,
    )

    fun optionsFor(kind: MediaKind): List<String> = when (kind) {
        MediaKind.BOOK  -> listOf(OWNED, READING, READ, WISHLIST)
        MediaKind.MOVIE -> listOf(OWNED, WATCHED, SHOW_TO, WISHLIST)
        MediaKind.TV    -> listOf(OWNED, WATCHING, WATCHED, SHOW_TO, WISHLIST)
        MediaKind.GAME  -> listOf(OWNED, PLAYING, PLAYED, COMPLETE, WISHLIST)
    }

    /** Extra pseudo-filters (backlog views) offered on a shelf, beyond real statuses. */
    fun extraFilters(kind: MediaKind): List<String> = when (kind) {
        MediaKind.MOVIE, MediaKind.TV -> listOf(NOT_WATCHED)
        else -> emptyList()
    }

    /** All selectable filter chips for a shelf: real statuses plus any pseudo-filters. */
    fun filterOptionsFor(kind: MediaKind): List<String> = optionsFor(kind) + extraFilters(kind)

    fun label(code: String, kind: MediaKind? = null): String {
        // Kind-specific overrides come first.
        if (kind == MediaKind.TV && code == WISHLIST) return "Must Watch"
        return when (code) {
            OWNED    -> "Own"
            WISHLIST -> "Wishlist"
            READING  -> "Reading"
            READ     -> "Read"
            WATCHING -> "Watching"
            WATCHED  -> "Watched"
            PLAYING  -> "Playing"
            PLAYED   -> "Played"
            COMPLETE -> "100%"
            SEEN     -> "Seen"
            SHOW_TO  -> "Show To"
            NOT_WATCHED -> "Not Watched"
            else     -> code.replaceFirstChar { it.uppercase() }
        }
    }

    fun parse(csv: String?): Set<String> =
        csv?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()

    fun toggle(csv: String?, value: String): String {
        val cur = parse(csv).toMutableSet()
        if (!cur.add(value)) cur.remove(value)
        return CANONICAL_ORDER.filter { it in cur }.joinToString(",")
    }

    /** Add a status if missing, keeping canonical order. */
    fun ensure(csv: String?, value: String): String {
        val cur = parse(csv).toMutableSet().apply { add(value) }
        return CANONICAL_ORDER.filter { it in cur }.joinToString(",")
    }

    /** Remove a status if present, keeping canonical order. */
    fun without(csv: String?, value: String): String {
        val cur = parse(csv).toMutableSet().apply { remove(value) }
        return CANONICAL_ORDER.filter { it in cur }.joinToString(",")
    }
}
