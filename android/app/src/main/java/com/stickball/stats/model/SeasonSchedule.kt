package com.stickball.stats.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// MARK: - Game Fields
enum class GameField(val rawValue: String) {
    THE_DAM("The Dam"),
    THE_SPREADING_GROUNDS("The Spreading Grounds"),
    THE_AIRFIELD("The Airfield"),
    UNCLE_KIMMYS_PLAYHOUSE("Uncle Kimmy's Playhouse");

    val shortName: String
        get() = when (this) {
            THE_DAM -> "DAM"
            THE_SPREADING_GROUNDS -> "SPRD"
            THE_AIRFIELD -> "AIR"
            UNCLE_KIMMYS_PLAYHOUSE -> "UKP"
        }

    /** Uncle Kimmy's Playhouse hosts the monthly Saturday night game */
    val isNightGame: Boolean get() = this == UNCLE_KIMMYS_PLAYHOUSE

    companion object {
        fun fromRaw(raw: String?): GameField? = entries.firstOrNull { it.rawValue == raw }
    }
}

// MARK: - Special Event
enum class SpecialEvent(val rawValue: String) {
    COATTAIL_CLASSIC("The Coattail Classic"),
    THE_WIDE_OPEN("The Wide Open"),
    DADS_VS_LADS("Dads Vs Lads"),
    THE_END("The End");

    val shortName: String
        get() = when (this) {
            COATTAIL_CLASSIC -> "COATTAIL"
            THE_WIDE_OPEN -> "WIDE OPEN"
            DADS_VS_LADS -> "DVL"
            THE_END -> "CHAMPIONSHIP"
        }

    /** Extra line shown under the event name */
    val subtitle: String?
        get() = when (this) {
            COATTAIL_CLASSIC -> "LA vs Portland · 4-team tournament"
            THE_WIDE_OPEN -> "Apr 1–4 · National tournament · No league game"
            else -> null
        }

    /** Blocked-out weekends have no league game and no stat entry */
    val isBlockedOut: Boolean get() = this == THE_WIDE_OPEN

    /** Portland chapter players can be added to this week */
    val includesPortland: Boolean get() = this == COATTAIL_CLASSIC
}

// MARK: - Game Week
data class GameWeek(
    /** Week number (1-based) */
    val id: Int,
    val date: LocalDate,
    val specialEvent: SpecialEvent?,
) {
    val weekNumber: Int get() = id
    val isBlockedOut: Boolean get() = specialEvent?.isBlockedOut ?: false
    val year: Int get() = date.year

    val formattedDate: String get() = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    val shortFormattedDate: String get() = date.format(DateTimeFormatter.ofPattern("M/d", Locale.getDefault()))

    /** e.g. "SATURDAY · SEP 26" */
    val dayTitle: String
        get() = date.format(DateTimeFormatter.ofPattern("EEEE · MMM d", Locale.getDefault())).uppercase()

    val displayTitle: String get() = specialEvent?.rawValue ?: "Week $weekNumber"
}

// MARK: - Season Schedule
object SeasonSchedule {
    /** Season number used by the archive (Season 8 was Nov 2025 – Jun 2026) */
    const val SEASON_NUMBER = 9
    val seasonNumber: Int get() = SEASON_NUMBER

    // Season: Oct 24, 2026 to June 12, 2027
    val seasonStart: LocalDate = LocalDate.of(2026, 10, 24)
    val seasonEnd: LocalDate = LocalDate.of(2027, 6, 12)

    private val specialEvents: Map<LocalDate, SpecialEvent> = mapOf(
        LocalDate.of(2026, 11, 14) to SpecialEvent.COATTAIL_CLASSIC,
        LocalDate.of(2027, 4, 3) to SpecialEvent.THE_WIDE_OPEN,
        LocalDate.of(2027, 5, 8) to SpecialEvent.DADS_VS_LADS,
        LocalDate.of(2027, 6, 12) to SpecialEvent.THE_END,
    )

    // Saturdays with no game
    private val skipDates: Set<LocalDate> = setOf(
        LocalDate.of(2026, 12, 26),
        LocalDate.of(2027, 1, 2),
    )

    /** Games every Saturday (Oct 24, 2026 is a Saturday) except the skip dates */
    val gameWeeks: List<GameWeek> = buildList {
        var date = seasonStart
        var weekNumber = 1
        while (!date.isAfter(seasonEnd)) {
            if (date !in skipDates) {
                add(GameWeek(weekNumber, date, specialEvents[date]))
                weekNumber += 1
            }
            date = date.plusDays(7)
        }
    }

    /** Index of the most recent game that has occurred */
    fun currentWeekIndex(): Int {
        val today = LocalDate.now()
        var lastPlayedIndex = 0
        for ((index, week) in gameWeeks.withIndex()) {
            if (!week.date.isAfter(today)) lastPlayedIndex = index else break
        }
        return lastPlayedIndex
    }

    fun gameWeek(forWeekNumber: Int): GameWeek? = gameWeeks.firstOrNull { it.weekNumber == forWeekNumber }

    fun gameWeekAt(index: Int): GameWeek? = gameWeeks.getOrNull(index)

    /** A week that hasn't been played yet */
    fun isFutureWeek(index: Int): Boolean {
        val week = gameWeekAt(index) ?: return true
        return week.date.isAfter(LocalDate.now())
    }

    val totalWeeks: Int get() = gameWeeks.size
}
