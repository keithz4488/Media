package com.kzaller.shelf.data

import com.kzaller.shelf.data.models.ItemDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Serializes the library for backup/export as JSON (full fidelity) or CSV (spreadsheet-friendly). */
object Export {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun toJson(items: List<ItemDto>): String = json.encodeToString(items)

    fun toCsv(items: List<ItemDto>): String {
        val header = listOf(
            "kind", "title", "subtitle", "year", "status", "format", "rating",
            "seasons", "episodes", "cur_season", "cur_episode", "completed_at",
            "external_src", "external_id",
        )
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append('\n')
        items.forEach { i ->
            val row = listOf(
                i.kind.wire, i.title, i.subtitle ?: "", i.year?.toString() ?: "",
                i.status ?: "", i.format ?: "", i.rating?.toString() ?: "",
                i.seasons?.toString() ?: "", i.episodes?.toString() ?: "",
                i.curSeason?.toString() ?: "", i.curEpisode?.toString() ?: "",
                i.completedAt?.toString() ?: "", i.externalSrc ?: "", i.externalId ?: "",
            )
            sb.append(row.joinToString(",") { esc(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun esc(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
}
