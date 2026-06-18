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

    private val CANONICAL_ORDER = listOf(
        OWNED, READING, READ, WATCHING, WATCHED, PLAYING, PLAYED, COMPLETE, SEEN, WISHLIST,
    )

    fun optionsFor(kind: MediaKind): List<String> = when (kind) {
        MediaKind.BOOK  -> listOf(OWNED, READING, READ, WISHLIST)
        MediaKind.MOVIE -> listOf(OWNED, WATCHING, WATCHED, WISHLIST)
        MediaKind.TV    -> listOf(OWNED, WATCHING, WATCHED, WISHLIST)
        MediaKind.GAME  -> listOf(OWNED, PLAYING, PLAYED, COMPLETE, WISHLIST)
    }

    fun label(code: String): String = when (code) {
        OWNED    -> "Owned"
        WISHLIST -> "Wishlist"
        READING  -> "Reading"
        READ     -> "Read"
        WATCHING -> "Watching"
        WATCHED  -> "Watched"
        PLAYING  -> "Playing"
        PLAYED   -> "Played"
        COMPLETE -> "100%"
        SEEN     -> "Seen"
        else     -> code.replaceFirstChar { it.uppercase() }
    }

    fun parse(csv: String?): Set<String> =
        csv?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()

    fun toggle(csv: String?, value: String): String {
        val cur = parse(csv).toMutableSet()
        if (!cur.add(value)) cur.remove(value)
        return CANONICAL_ORDER.filter { it in cur }.joinToString(",")
    }
}
