package com.stickball.stats.model

// MARK: - Historical Season Stats
data class HistoricalSeasonStats(
    val seasonNumber: Int,
    val seasonName: String,
    val playerStats: List<HistoricalPlayerSeasonStats>,
) {
    val id: Int get() = seasonNumber
}

data class HistoricalPlayerSeasonStats(
    val playerName: String,
    val dongs: Int,
    val drops: Int,
    val doublePlays: Int,
    val salamies: Int,
    val wins: Int,
    val dongRobs: Int,
) {
    val id: String get() = playerName
}

// MARK: - Career Stats
data class CareerStats(
    val playerName: String,
    val totalDongs: Int = 0,
    val totalDrops: Int = 0,
    val totalDoublePlays: Int = 0,
    val totalSalamies: Int = 0,
    val totalWins: Int = 0,
    val totalDongRobs: Int = 0,
    val seasonsPlayed: Int = 0,
) {
    val id: String get() = playerName

    /** Adds an admin correction, never letting a total drop below zero */
    fun applying(adjustment: CareerAdjustment) = copy(
        totalDongs = maxOf(0, totalDongs + adjustment.dongs),
        totalDrops = maxOf(0, totalDrops + adjustment.drops),
        totalDoublePlays = maxOf(0, totalDoublePlays + adjustment.doublePlays),
        totalSalamies = maxOf(0, totalSalamies + adjustment.salamies),
        totalWins = maxOf(0, totalWins + adjustment.wins),
        totalDongRobs = maxOf(0, totalDongRobs + adjustment.dongRobs),
    )

    fun adding(season: HistoricalPlayerSeasonStats) = copy(
        totalDongs = totalDongs + season.dongs,
        totalDrops = totalDrops + season.drops,
        totalDoublePlays = totalDoublePlays + season.doublePlays,
        totalSalamies = totalSalamies + season.salamies,
        totalWins = totalWins + season.wins,
        totalDongRobs = totalDongRobs + season.dongRobs,
        seasonsPlayed = seasonsPlayed + 1,
    )

    fun adding(yearly: YearlyStats) = copy(
        totalDongs = totalDongs + yearly.totalDongs,
        totalDrops = totalDrops + yearly.totalDrops,
        totalDoublePlays = totalDoublePlays + yearly.totalDoublePlays,
        totalSalamies = totalSalamies + yearly.totalSalamies,
        totalWins = totalWins + yearly.totalWins,
        seasonsPlayed = seasonsPlayed + 1,
    )
}

// MARK: - Historical Data
object HistoricalData {
    val seasons: List<HistoricalSeasonStats> get() = HistoricalSeasonData.seasons

    /** Archived seasons that are finished (excludes the season currently being played) */
    val pastSeasons: List<HistoricalSeasonStats> by lazy {
        seasons.filter { it.seasonNumber < SeasonSchedule.seasonNumber }
    }

    /**
     * Career stats for all players across all seasons. Pass currentSeasonStats to include the
     * current season from Firebase, and adjustments to apply admin corrections (keyed by canonical name)
     */
    fun calculateCareerStats(
        currentSeasonStats: List<YearlyStats> = emptyList(),
        adjustments: Map<String, CareerAdjustment> = emptyMap(),
    ): List<CareerStats> {
        val careerDict = LinkedHashMap<String, CareerStats>()

        for (season in pastSeasons) {
            for (playerSeason in season.playerStats) {
                val canonical = PlayerAliases.canonicalName(playerSeason.playerName)
                careerDict[canonical] = (careerDict[canonical] ?: CareerStats(canonical)).adding(playerSeason)
            }
        }

        // Merge in current season stats from Firebase
        for (yearly in currentSeasonStats) {
            val canonical = PlayerAliases.canonicalName(yearly.playerName)
            careerDict[canonical] = (careerDict[canonical] ?: CareerStats(canonical)).adding(yearly)
        }

        // Admin corrections
        for ((name, adjustment) in adjustments) {
            careerDict[name] = (careerDict[name] ?: CareerStats(name)).applying(adjustment)
        }

        return careerDict.values.sortedByDescending { it.totalDongs }
    }
}
