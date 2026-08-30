package com.kzaller.shelf.data

/**
 * Whether a show is still running, as the backend normalises it from TMDB.
 *
 * Not a user-set status like [Status]: this belongs to the show itself, so it is never editable
 * here -- it arrives with the item and is refreshed alongside the season counts.
 */
object SeriesStatus {
    const val CONTINUING = "continuing"
    const val ENDED      = "ended"

    /** Display text, or null when TMDB hasn't told us (so callers can hide the row entirely). */
    fun label(code: String?): String? = when (code) {
        CONTINUING -> "Continuing"
        ENDED      -> "Ended"
        else       -> null
    }
}
