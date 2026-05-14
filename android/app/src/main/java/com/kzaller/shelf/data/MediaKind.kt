package com.kzaller.shelf.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind {
    @SerialName("book")  BOOK,
    @SerialName("movie") MOVIE,
    @SerialName("tv")    TV,
    @SerialName("game")  GAME;

    val wire: String get() = when (this) {
        BOOK -> "book"; MOVIE -> "movie"; TV -> "tv"; GAME -> "game"
    }

    val label: String get() = when (this) {
        BOOK -> "Books"; MOVIE -> "Movies"; TV -> "TV"; GAME -> "Games"
    }

    companion object {
        fun fromWire(s: String): MediaKind = when (s) {
            "book" -> BOOK; "movie" -> MOVIE; "tv" -> TV; "game" -> GAME
            else -> error("unknown kind: $s")
        }
    }
}
