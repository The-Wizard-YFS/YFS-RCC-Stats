package com.stickball.stats

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.stickball.stats.model.AchievementsCalculator
import com.stickball.stats.model.GameField
import com.stickball.stats.model.GameWeekInfo
import com.stickball.stats.model.HistoricalData
import com.stickball.stats.model.Player
import com.stickball.stats.model.SeasonSchedule
import com.stickball.stats.model.WeeklyStats
import com.stickball.stats.service.AdminService
import com.stickball.stats.service.StatsService
import com.stickball.stats.ui.AddEditPlayerView
import com.stickball.stats.ui.AdminPINEntryView
import com.stickball.stats.ui.BatchStatsEntryView
import com.stickball.stats.ui.LocalAdmin
import com.stickball.stats.ui.LocalStats
import com.stickball.stats.ui.PlayerCareerDetailView
import com.stickball.stats.ui.PlayerStatsEntryView
import com.stickball.stats.ui.SRTheme
import com.stickball.stats.ui.SeasonAwardsView
import com.stickball.stats.ui.SeasonDetailView
import com.stickball.stats.ui.SeasonScheduleView
import com.stickball.stats.ui.WeeklyPlayerSelectionView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Date

/**
 * Renders every screen to PNG (app/build/screenshots) on the JVM, with league data written
 * through the app's own Firestore code into a local, offline cache. Phone size matches an iPhone 15 Pro.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        compose.waitForIdle()
        idle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun idle() {
        repeat(5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
    }

    private fun seededService(): StatsService {
        val app = ApplicationProvider.getApplicationContext<Application>()
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setProjectId("demo-screenshots")
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("demo")
                    .build(),
            )
        }
        val db = FirebaseFirestore.getInstance()
        db.disableNetwork()

        val la = listOf(
            "Cuidado", "Flash Dance", "Surgeon", "Dong Robber", "Lunch Money", "The Deal", "Boogie Joe",
            "Cojones", "Goat Cheese", "Candyman", "Barely Bonds", "Salary", "Uncle Kimmy",
        )
        val players = la.mapIndexed { i, n -> Player(id = "p$i", name = n) } +
            Player(id = "pdx1", name = "Rose City Rocket", teamName = Player.PORTLAND_CHAPTER)
        players.forEach { db.collection("players").document(it.id!!).set(it.toFirestore()) }

        val currentWeek = SeasonSchedule.gameWeekAt(SeasonSchedule.currentWeekIndex())!!
        for (week in SeasonSchedule.gameWeeks.filter { !it.date.isAfter(currentWeek.date) && !it.isBlockedOut }) {
            la.forEachIndexed { i, n ->
                if ((i + week.weekNumber) % 5 == 0) return@forEachIndexed // a few players sit out each week
                val s = WeeklyStats(
                    id = "w${week.weekNumber}-$i", playerId = "p$i", playerName = n,
                    weekNumber = week.weekNumber, year = week.year,
                    dongs = (i * 7 + week.weekNumber * 3) % 9,
                    drops = (i + week.weekNumber) % 4,
                    doublePlays = if ((i + week.weekNumber) % 6 == 0) 1 else 0,
                    salamies = if ((i * week.weekNumber) % 7 == 3) 1 else 0,
                    wins = (i + week.weekNumber) % 2,
                    createdAt = Date(), updatedAt = Date(),
                )
                db.collection("weeklyStats").document(s.id!!).set(s.toFirestore())
            }
        }
        val info = GameWeekInfo(id = "gwi", weekNumber = currentWeek.weekNumber, field = GameField.THE_AIRFIELD.rawValue)
        db.collection("gameWeekInfo").document("gwi").set(info.toFirestore())

        val service = StatsService(app)
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline &&
            (service.players.size < players.size || service.weeklyStats.isEmpty() || service.gameWeekInfos.isEmpty())
        ) idle()
        check(service.players.size == players.size) { "players not loaded: ${service.players.size}" }
        return service
    }

    private var current: androidx.compose.runtime.MutableState<(@Composable () -> Unit)>? = null

    private fun render(service: StatsService, content: @Composable () -> Unit) {
        current?.let {
            compose.runOnUiThread { it.value = {} }
            compose.waitForIdle()
            compose.runOnUiThread { it.value = content }
            return
        }
        val state = androidx.compose.runtime.mutableStateOf(content)
        current = state
        compose.setContent {
            SRTheme {
                CompositionLocalProvider(LocalStats provides service, LocalAdmin provides AdminService.shared) {
                    Box(Modifier.fillMaxSize()) { state.value() }
                }
            }
        }
    }

    @Test
    fun screens() {
        val service = seededService()
        AdminService.shared.logout()

        // Splash at the moment both graphics have landed
        compose.mainClock.autoAdvance = false
        render(service) { DongCountryLedgerApp() }
        compose.mainClock.advanceTimeBy(1700)
        shot("01_splash")
        compose.mainClock.advanceTimeBy(3000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        shot("02_season_leaders")

        compose.onAllNodesWithText("WEEKLY").onFirst().performClick()
        shot("03_weekly")
        compose.onAllNodesWithText("HISTORIC").onFirst().performClick()
        shot("04_historic")
        compose.onAllNodesWithText("PLAYERS").onFirst().performClick()
        shot("05_players")

        AdminService.shared.authenticate("2234")
        shot("06_players_admin")
        compose.onAllNodesWithText("WEEKLY").onFirst().performClick()
        shot("07_weekly_admin")
    }

    @Test
    fun sheets() {
        val service = seededService()
        AdminService.shared.authenticate("2234")
        val cuidado = service.players.first { it.name == "Cuidado" }
        val career = HistoricalData.calculateCareerStats(service.yearlyStats, service.careerAdjustments)
            .first { it.playerName == "Cuidado" }

        render(service) { PlayerCareerDetailView(career, null) {} }
        shot("10_career_cuidado")

        render(service) { AddEditPlayerView(cuidado) {} }
        idle()
        shot("11_edit_player")

        render(service) { AddEditPlayerView(null) {} }
        shot("12_add_player")

        render(service) { AdminPINEntryView {} }
        shot("13_admin_pin")

        render(service) { WeeklyPlayerSelectionView {} }
        shot("14_who_played")

        render(service) { BatchStatsEntryView {} }
        idle()
        shot("15a_edit_week_collapsed")
        compose.onAllNodesWithText("BOOGIE JOE", useUnmergedTree = true).onFirst().performClick()
        shot("15_edit_week")

        render(service) { PlayerStatsEntryView(cuidado) {} }
        repeat(10) { idle() }
        shot("16_player_stats")

        val season6 = HistoricalData.pastSeasons.first { it.seasonNumber == 6 }
        render(service) { SeasonDetailView(season6) {} }
        shot("17_season_6")

        render(service) { SeasonAwardsView(AchievementsCalculator.getAllSeasonAwards().first { it.seasonNumber == 6 }) {} }
        shot("18_season_6_awards")

        render(service) { SeasonScheduleView {} }
        shot("19_schedule")
    }
}
