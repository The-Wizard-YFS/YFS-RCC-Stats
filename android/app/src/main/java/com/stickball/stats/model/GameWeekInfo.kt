package com.stickball.stats.model

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/** Firestore "gameWeekInfo" document: per-week info like the field */
data class GameWeekInfo(
    val id: String? = null,
    val weekNumber: Int,
    /** null for entries saved before seasons were tagged (Season 8) */
    val season: Int? = SeasonSchedule.SEASON_NUMBER,
    /** GameField raw value */
    val field: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
) {
    val seasonNumber: Int get() = season ?: 8

    val gameField: GameField? get() = GameField.fromRaw(this.field)

    fun withField(newField: GameField?) = copy(field = newField?.rawValue, updatedAt = Date())

    fun toFirestore(): Map<String, Any> = buildMap {
        put("weekNumber", weekNumber)
        season?.let { put("season", it) }
        field?.let { put("field", it) }
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun from(doc: DocumentSnapshot): GameWeekInfo? {
            return GameWeekInfo(
                id = doc.id,
                weekNumber = doc.int("weekNumber") ?: return null,
                season = doc.int("season"),
                field = doc.getString("field"),
                createdAt = doc.date("createdAt") ?: return null,
                updatedAt = doc.date("updatedAt") ?: return null,
            )
        }
    }
}
