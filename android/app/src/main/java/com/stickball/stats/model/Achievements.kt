package com.stickball.stats.model

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/** Badge kinds; each maps to medallion art or a fallback icon in the UI */
enum class BadgeKind { SALAMI_CLUB, CENTURY_CLUB, MVP, DONG_KING, ROOKIE_OF_YEAR, COATTAILS, GALACTICS, WIDE_OPEN }

// MARK: - Badge Display Info
data class BadgeInfo(
    val name: String,
    val kind: BadgeKind,
    /** ARGB color of the badge */
    val color: Long,
    /** For awards won multiple times (2X, 3X, etc.) */
    val multiplier: Int = 0,
) {
    val id: String get() = name
}

private const val GOLD = 0xFFF5C75CL // TronColors.cyan (gold)
private const val LIGHT_GOLD = 0xFFFFE080L // TronColors.yellow
private const val GREEN = 0xFF4DE673L
private const val VIOLET = 0xFFC759FFL // TronColors.magenta
private const val PURPLE = 0xFF9E4DFFL
private const val BRONZE = 0xFFCC8033L
private const val SILVER = 0xFFBFBFCCL
private const val GOLD_TIER = 0xFFFFD600L
private const val PLATINUM = 0xFFE6E6F2L

// MARK: - Salami Club Tier
enum class SalamiTier(val color: Long, val title: String) {
    NONE(0, ""),
    BASE(GREEN, "Salami Club"),      // 1+ salamies
    BRONZE(com.stickball.stats.model.BRONZE, "Salami Club"),   // 10+
    SILVER(com.stickball.stats.model.SILVER, "Salami Club"),   // 20+
    GOLD(GOLD_TIER, "Salami Club"),  // 30+
    PLATINUM(com.stickball.stats.model.PLATINUM, "Salami Club"); // 40+

    companion object {
        fun tier(salamies: Int): SalamiTier = when {
            salamies >= 40 -> PLATINUM
            salamies >= 30 -> GOLD
            salamies >= 20 -> SILVER
            salamies >= 10 -> BRONZE
            salamies >= 1 -> BASE
            else -> NONE
        }
    }
}

// MARK: - Century Club Tier
enum class CenturyTier(val color: Long, val title: String) {
    NONE(0, ""),
    BASE_100(com.stickball.stats.model.GOLD, "Century Club"),
    BRONZE_200(BRONZE, "200 Club"),
    SILVER_300(SILVER, "300 Club"),
    GOLD_400(GOLD_TIER, "400 Club"),
    PLATINUM_500(PLATINUM, "500 Club");

    companion object {
        fun tier(dongs: Int): CenturyTier = when {
            dongs >= 500 -> PLATINUM_500
            dongs >= 400 -> GOLD_400
            dongs >= 300 -> SILVER_300
            dongs >= 200 -> BRONZE_200
            dongs >= 100 -> BASE_100
            else -> NONE
        }
    }
}

// MARK: - Player Achievement Record
data class PlayerAchievements(
    val playerName: String,
    val salamiTier: SalamiTier = SalamiTier.NONE,
    val centuryTier: CenturyTier = CenturyTier.NONE,
    val mvpCount: Int = 0,
    val dongKingCount: Int = 0,
    val rookieOfYearCount: Int = 0,
    val yfslaChampCount: Int = 0,
    val hasCoattails: Boolean = false,
    val hasGalactics: Boolean = false,
    val hasWideOpen: Boolean = false,
) {
    val id: String get() = playerName

    val badges: List<BadgeInfo>
        get() = buildList {
            if (salamiTier != SalamiTier.NONE) add(BadgeInfo(salamiTier.title, BadgeKind.SALAMI_CLUB, salamiTier.color))
            if (centuryTier != CenturyTier.NONE) add(BadgeInfo(centuryTier.title, BadgeKind.CENTURY_CLUB, centuryTier.color))
            if (mvpCount > 0) add(BadgeInfo("MVP", BadgeKind.MVP, LIGHT_GOLD, mvpCount))
            if (dongKingCount > 0) add(BadgeInfo("Dong King", BadgeKind.DONG_KING, GOLD, dongKingCount))
            if (rookieOfYearCount > 0) add(BadgeInfo("Rookie of the Year", BadgeKind.ROOKIE_OF_YEAR, GREEN, rookieOfYearCount))
            if (hasCoattails) add(BadgeInfo("Coattails", BadgeKind.COATTAILS, VIOLET))
            if (hasGalactics) add(BadgeInfo("Galactics", BadgeKind.GALACTICS, PURPLE))
            if (hasWideOpen) add(BadgeInfo("Wide Open", BadgeKind.WIDE_OPEN, GOLD))
        }

    val hasBadges: Boolean
        get() = salamiTier != SalamiTier.NONE || centuryTier != CenturyTier.NONE ||
            mvpCount > 0 || dongKingCount > 0 || rookieOfYearCount > 0 ||
            hasCoattails || hasGalactics || hasWideOpen
}

// MARK: - Season Awards
data class SeasonAwards(
    val seasonNumber: Int,
    val seasonName: String,
    /** Dong King - most dongs */
    val dongKing: String? = null,
    val dongKingTotal: Int = 0,
    /** MVP - most dongs + wins combined */
    val mvp: String? = null,
    val mvpDongs: Int = 0,
    val mvpWins: Int = 0,
    /** Rookie of the Year (formerly Best New Baby) */
    val rookieOfYear: String? = null,
    val rookieOfYearDongs: Int = 0,
)

// MARK: - Admin Career Adjustments
/**
 * Admin corrections to a player's career totals and badges, stored in Firestore
 * ("careerAdjustments", one document per canonical player name). Totals and award
 * counts are differences added on top of the calculated values, so careers keep
 * growing with new games; the one-off badges are on/off overrides (null = default).
 */
data class CareerAdjustment(
    val playerName: String,
    val dongs: Int = 0,
    val drops: Int = 0,
    val doublePlays: Int = 0,
    val salamies: Int = 0,
    val wins: Int = 0,
    val dongRobs: Int = 0,
    val yfslaChampionships: Int = 0,
    val mvp: Int = 0,
    val dongKing: Int = 0,
    val rookieOfYear: Int = 0,
    val coattails: Boolean? = null,
    val galactics: Boolean? = null,
    val wideOpen: Boolean? = null,
    val updatedAt: Date = Date(),
) {
    /** True when this changes nothing (safe to skip saving) */
    val isEmpty: Boolean
        get() = dongs == 0 && drops == 0 && doublePlays == 0 && salamies == 0 && wins == 0 &&
            dongRobs == 0 && yfslaChampionships == 0 && mvp == 0 && dongKing == 0 &&
            rookieOfYear == 0 && coattails == null && galactics == null && wideOpen == null

    fun toFirestore(): Map<String, Any> = buildMap {
        put("playerName", playerName)
        put("dongs", dongs)
        put("drops", drops)
        put("doublePlays", doublePlays)
        put("salamies", salamies)
        put("wins", wins)
        put("dongRobs", dongRobs)
        put("yfslaChampionships", yfslaChampionships)
        put("mvp", mvp)
        put("dongKing", dongKing)
        put("rookieOfYear", rookieOfYear)
        coattails?.let { put("coattails", it) }
        galactics?.let { put("galactics", it) }
        wideOpen?.let { put("wideOpen", it) }
        put("updatedAt", updatedAt)
    }

    companion object {
        fun from(doc: DocumentSnapshot): CareerAdjustment? {
            return CareerAdjustment(
                playerName = doc.getString("playerName") ?: return null,
                dongs = doc.int("dongs") ?: return null,
                drops = doc.int("drops") ?: return null,
                doublePlays = doc.int("doublePlays") ?: return null,
                salamies = doc.int("salamies") ?: return null,
                wins = doc.int("wins") ?: return null,
                dongRobs = doc.int("dongRobs") ?: return null,
                yfslaChampionships = doc.int("yfslaChampionships") ?: return null,
                mvp = doc.int("mvp") ?: return null,
                dongKing = doc.int("dongKing") ?: return null,
                rookieOfYear = doc.int("rookieOfYear") ?: return null,
                coattails = doc.getBoolean("coattails"),
                galactics = doc.getBoolean("galactics"),
                wideOpen = doc.getBoolean("wideOpen"),
                updatedAt = doc.date("updatedAt") ?: return null,
            )
        }
    }
}

// MARK: - Badge rosters
object YFSLAChampions {
    /** Players with YFSLA Championship wins */
    val champions: Map<String, Int> = mapOf(
        "Dong Robber" to 2,
        "Party Platter" to 1,
        "Dong Quixote" to 3,
        "Flash Dance" to 1,
        "Lunch Money" to 2,
        "Baby Boi" to 3,
        "The Deal" to 4,
        "Cuidado" to 2,
        "Candyman" to 3,
        "Katfish" to 2,
        "Cojones" to 1,
        "Hot Tub" to 3,
        "Dr. Big Dick" to 2,
    )

    fun champCount(playerName: String): Int = champions[playerName] ?: 0
}

object CoattailsPlayers {
    val players = setOf("Party Platter", "Dong Robber", "Baby Boi", "Cuidado", "The Deal")
    fun hasCoattails(playerName: String) = playerName in players
}

/** Players who have earned the Galactics badge */
object GalacticsPlayers {
    val players = setOf("Salary", "Flash Dance", "Cuidado", "Cojones", "Uncle Kimmy")
    fun hasGalactics(playerName: String) = playerName in players
}

/** Players who have earned the Wide Open badge */
object WideOpenPlayers {
    val players = setOf("Salary", "Boogie Joe", "Cuidado", "Candyman")
    fun hasWideOpen(playerName: String) = playerName in players
}

// MARK: - Achievements Calculator
object AchievementsCalculator {

    /**
     * careerStats should already include any admin adjustment (it drives the club tiers);
     * the adjustment's award counts and badge overrides are applied here.
     */
    fun calculateAchievements(
        playerName: String,
        careerStats: CareerStats,
        adjustment: CareerAdjustment? = null,
    ): PlayerAchievements {
        var mvpCount = 0
        var dongKingCount = 0
        var rookieCount = 0
        // Count MVP, Dong King, and Rookie of Year awards from historical seasons
        for (award in seasonAwardsCache) {
            if (award.mvp != null && PlayerAliases.canonicalName(award.mvp) == playerName) mvpCount += 1
            if (award.dongKing != null && PlayerAliases.canonicalName(award.dongKing) == playerName) dongKingCount += 1
            if (award.rookieOfYear != null && PlayerAliases.canonicalName(award.rookieOfYear) == playerName) rookieCount += 1
        }

        var achievements = PlayerAchievements(
            playerName = playerName,
            salamiTier = SalamiTier.tier(careerStats.totalSalamies),
            centuryTier = CenturyTier.tier(careerStats.totalDongs),
            yfslaChampCount = YFSLAChampions.champCount(playerName),
            hasCoattails = CoattailsPlayers.hasCoattails(playerName),
            hasGalactics = GalacticsPlayers.hasGalactics(playerName),
            hasWideOpen = WideOpenPlayers.hasWideOpen(playerName),
            mvpCount = mvpCount,
            dongKingCount = dongKingCount,
            rookieOfYearCount = rookieCount,
        )

        // Admin corrections
        if (adjustment != null) {
            achievements = achievements.copy(
                mvpCount = maxOf(0, achievements.mvpCount + adjustment.mvp),
                dongKingCount = maxOf(0, achievements.dongKingCount + adjustment.dongKing),
                rookieOfYearCount = maxOf(0, achievements.rookieOfYearCount + adjustment.rookieOfYear),
                yfslaChampCount = maxOf(0, achievements.yfslaChampCount + adjustment.yfslaChampionships),
                hasCoattails = adjustment.coattails ?: achievements.hasCoattails,
                hasGalactics = adjustment.galactics ?: achievements.hasGalactics,
                hasWideOpen = adjustment.wideOpen ?: achievements.hasWideOpen,
            )
        }
        return achievements
    }

    /** Weighted score: dongs (1.0), double plays (0.5), wins (0.3), drops (-0.2) */
    fun calculateWeeklyScore(dongs: Int, doublePlays: Int, wins: Int, drops: Int): Double =
        dongs * 1.0 + doublePlays * 0.5 + wins * 0.3 - drops * 0.2

    fun calculateSeasonAwards(
        season: HistoricalSeasonStats,
        previousSeasons: List<HistoricalSeasonStats>,
    ): SeasonAwards {
        // Everyone who played in a previous season
        val previousPlayers = previousSeasons
            .flatMap { it.playerStats }
            .map { PlayerAliases.canonicalName(it.playerName) }
            .toSet()

        var awards = SeasonAwards(season.seasonNumber, season.seasonName)

        // Dong King (most dongs; the first listed wins a tie, like Swift's max(by:))
        var topDonger: HistoricalPlayerSeasonStats? = null
        for (p in season.playerStats) {
            if (topDonger == null || p.dongs > topDonger.dongs) topDonger = p
        }
        if (topDonger != null) awards = awards.copy(dongKing = topDonger.playerName, dongKingTotal = topDonger.dongs)

        // MVP (most dongs + wins)
        var bestMVPScore = 0
        for (p in season.playerStats) {
            val score = p.dongs + p.wins
            if (score > bestMVPScore) {
                bestMVPScore = score
                awards = awards.copy(mvp = p.playerName, mvpDongs = p.dongs, mvpWins = p.wins)
            }
        }

        // Rookie of the Year (rookie with most dongs)
        var bestRookieDongs = 0
        for (p in season.playerStats) {
            if (PlayerAliases.canonicalName(p.playerName) !in previousPlayers && p.dongs > bestRookieDongs) {
                bestRookieDongs = p.dongs
                awards = awards.copy(rookieOfYear = p.playerName, rookieOfYearDongs = p.dongs)
            }
        }
        return awards
    }

    /** Awards for every finished season (fixed data, so computed once) */
    private val seasonAwardsCache: List<SeasonAwards> by lazy {
        val seasons = HistoricalData.pastSeasons
        seasons.mapIndexed { index, season -> calculateSeasonAwards(season, seasons.take(index)) }
    }

    fun getAllSeasonAwards(): List<SeasonAwards> = seasonAwardsCache

    fun getAllPlayerAchievements(
        currentSeasonStats: List<YearlyStats> = emptyList(),
        adjustments: Map<String, CareerAdjustment> = emptyMap(),
    ): List<PlayerAchievements> =
        HistoricalData.calculateCareerStats(currentSeasonStats, adjustments)
            .map { calculateAchievements(it.playerName, it, adjustments[it.playerName]) }
            .filter { it.hasBadges }
}
