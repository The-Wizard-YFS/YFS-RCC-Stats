package com.stickball.stats.model

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/** Firestore "weeklyStats" document: one player's line for one game week */
data class WeeklyStats(
    val id: String? = null,
    val playerId: String,
    /** Denormalized for easier display */
    val playerName: String,
    val weekNumber: Int,
    val year: Int,
    /** null for entries saved before seasons were tagged (Season 8) */
    val season: Int? = SeasonSchedule.SEASON_NUMBER,
    val dongs: Int = 0,
    val drops: Int = 0,
    val doublePlays: Int = 0,
    val salamies: Int = 0,
    val wins: Int = 0,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
) {
    /** Season this entry belongs to; untagged entries are from Season 8 */
    val seasonNumber: Int get() = season ?: 8

    val weekIdentifier: String get() = "$year-W${"%02d".format(weekNumber)}"

    /** True once anything has been recorded for this player this week */
    val hasAnyStats: Boolean get() = dongs + drops + doublePlays + salamies + wins > 0

    val totalPoints: Int get() = dongs + salamies + wins - drops - doublePlays

    fun toFirestore(): Map<String, Any> = buildMap {
        put("playerId", playerId)
        put("playerName", playerName)
        put("weekNumber", weekNumber)
        put("year", year)
        season?.let { put("season", it) }
        put("dongs", dongs)
        put("drops", drops)
        put("doublePlays", doublePlays)
        put("salamies", salamies)
        put("wins", wins)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun from(doc: DocumentSnapshot): WeeklyStats? {
            return WeeklyStats(
                id = doc.id,
                playerId = doc.getString("playerId") ?: return null,
                playerName = doc.getString("playerName") ?: return null,
                weekNumber = doc.int("weekNumber") ?: return null,
                year = doc.int("year") ?: return null,
                season = doc.int("season"),
                dongs = doc.int("dongs") ?: return null,
                drops = doc.int("drops") ?: return null,
                doublePlays = doc.int("doublePlays") ?: return null,
                salamies = doc.int("salamies") ?: return null,
                wins = doc.int("wins") ?: return null,
                createdAt = doc.date("createdAt") ?: return null,
                updatedAt = doc.date("updatedAt") ?: return null,
            )
        }
    }
}

/** Aggregated stats for a player across a season */
data class YearlyStats(
    val playerId: String,
    val playerName: String,
    val year: Int,
    val totalDongs: Int = 0,
    val totalDrops: Int = 0,
    val totalDoublePlays: Int = 0,
    val totalSalamies: Int = 0,
    val totalWins: Int = 0,
    val gamesPlayed: Int = 0,
) {
    val id: String get() = playerId

    fun addingWeeklyStats(stats: WeeklyStats) = copy(
        totalDongs = totalDongs + stats.dongs,
        totalDrops = totalDrops + stats.drops,
        totalDoublePlays = totalDoublePlays + stats.doublePlays,
        totalSalamies = totalSalamies + stats.salamies,
        totalWins = totalWins + stats.wins,
        gamesPlayed = gamesPlayed + 1,
    )

    val totalPoints: Int get() = totalDongs + totalSalamies + totalWins - totalDrops - totalDoublePlays
    val avgDongs: Double get() = if (gamesPlayed > 0) totalDongs.toDouble() / gamesPlayed else 0.0
    val avgDrops: Double get() = if (gamesPlayed > 0) totalDrops.toDouble() / gamesPlayed else 0.0
}
