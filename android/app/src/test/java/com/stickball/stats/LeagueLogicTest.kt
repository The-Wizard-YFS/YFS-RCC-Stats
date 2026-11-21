package com.stickball.stats

import com.stickball.stats.model.AchievementsCalculator
import com.stickball.stats.model.CareerAdjustment
import com.stickball.stats.model.CenturyTier
import com.stickball.stats.model.HistoricalData
import com.stickball.stats.model.PlayerAliases
import com.stickball.stats.model.SeasonSchedule
import com.stickball.stats.model.SpecialEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Checks the Kotlin port against values worked out from the iOS app's Swift data */
class LeagueLogicTest {
    @Test
    fun schedule() {
        // Saturdays Oct 24, 2026 – Jun 12, 2027 (34) minus the two skipped weekends
        assertEquals(32, SeasonSchedule.totalWeeks)
        assertEquals(LocalDate.of(2026, 10, 24), SeasonSchedule.gameWeeks.first().date)
        assertEquals(SpecialEvent.COATTAIL_CLASSIC, SeasonSchedule.gameWeek(4)?.specialEvent)
        assertEquals(SpecialEvent.THE_END, SeasonSchedule.gameWeeks.last().specialEvent)
        assertNull(SeasonSchedule.gameWeeks.firstOrNull { it.date == LocalDate.of(2026, 12, 26) })
    }

    @Test
    fun aliases() {
        assertEquals("Boogie Joe", PlayerAliases.canonicalName("  Boogs "))
        assertEquals("Cuidado", PlayerAliases.canonicalName("Rookie Drew"))
        assertEquals("Somebody New", PlayerAliases.canonicalName("Somebody New"))
    }

    @Test
    fun careersAndAwards() {
        val careers = HistoricalData.calculateCareerStats().associateBy { it.playerName }
        val cuidado = careers.getValue("Cuidado")
        assertEquals(50 + 77 + 130 + 49 + 64, cuidado.totalDongs)
        assertEquals(5, cuidado.seasonsPlayed)
        // "Rookie Ahmet" (Season 3) is an alias of Two Step
        assertEquals(9 + 15 + 6 + 8, careers.getValue("Two Step").totalDongs)

        val s6 = AchievementsCalculator.getAllSeasonAwards().first { it.seasonNumber == 6 }
        assertEquals("Cuidado", s6.dongKing)
        assertEquals("Cuidado", s6.mvp)
        assertEquals("Flash Dance", s6.rookieOfYear)

        val a = AchievementsCalculator.calculateAchievements("Cuidado", cuidado)
        assertEquals(CenturyTier.SILVER_300, a.centuryTier)

        // Admin corrections never push totals below zero and override one-off badges
        val adj = CareerAdjustment("Cuidado", dongs = -10_000, coattails = false)
        val adjusted = cuidado.applying(adj)
        assertEquals(0, adjusted.totalDongs)
        assertEquals(false, AchievementsCalculator.calculateAchievements("Cuidado", adjusted, adj).hasCoattails)
    }
}
