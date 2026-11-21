package com.stickball.stats.service

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.stickball.stats.BuildConfig
import com.stickball.stats.model.CareerAdjustment
import com.stickball.stats.model.GameField
import com.stickball.stats.model.GameWeek
import com.stickball.stats.model.GameWeekInfo
import com.stickball.stats.model.Player
import com.stickball.stats.model.PlayerAliases
import com.stickball.stats.model.SeasonSchedule
import com.stickball.stats.model.WeeklyStats
import com.stickball.stats.model.YearlyStats
import kotlinx.coroutines.tasks.await
import java.util.Date

private const val TAG = "StatsService"

/**
 * Firebase Firestore service for real-time cloud sync. Reads and writes exactly the
 * same collections and fields as the iOS app, so both apps share one league database.
 *
 * Like the iOS app, single-document writes are not awaited: Firestore applies them to its
 * local cache immediately (so the UI updates and offline use works) and syncs in the background.
 */
class StatsService(app: Application) : AndroidViewModel(app) {
    private val db = FirebaseFirestore.getInstance()
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private var playersListener: ListenerRegistration? = null
    private var statsListener: ListenerRegistration? = null
    private var gameWeekInfoListener: ListenerRegistration? = null
    private var adjustmentsListener: ListenerRegistration? = null

    var players by mutableStateOf<List<Player>>(emptyList())
        private set
    var weeklyStats by mutableStateOf<List<WeeklyStats>>(emptyList())
        private set
    var yearlyStats by mutableStateOf<List<YearlyStats>>(emptyList())
        private set
    /** canonical name -> admin correction */
    var careerAdjustments by mutableStateOf<Map<String, CareerAdjustment>>(emptyMap())
        private set
    /** weekNumber -> info */
    var gameWeekInfos by mutableStateOf<Map<Int, GameWeekInfo>>(emptyMap())
        private set
    var errorMessage by mutableStateOf<String?>(null)

    val schedule = SeasonSchedule

    /** Current week index (0-based) in the season schedule; starts at the most recent game */
    var currentWeekIndex by mutableIntStateOf(SeasonSchedule.currentWeekIndex())
        private set

    val currentGameWeek: GameWeek? get() = schedule.gameWeekAt(currentWeekIndex)
    val currentWeekNumber: Int get() = currentGameWeek?.weekNumber ?: 1
    val currentWeek: Int get() = currentWeekNumber
    val currentYear: Int get() = currentGameWeek?.year ?: 2026
    val currentSeason: Int get() = schedule.seasonNumber

    /** Viewing a week that hasn't been played yet */
    val isViewingFutureWeek: Boolean get() = schedule.isFutureWeek(currentWeekIndex)

    /** Stats can only be entered for weeks that have happened and aren't blocked out */
    val canEnterStatsForCurrentWeek: Boolean
        get() = !(currentGameWeek?.isBlockedOut ?: false) && (isTestMode || !isViewingFutureWeek)

    // MARK: - Test Mode
    /**
     * Debug builds only: lets admins enter stats for upcoming weeks. Test stats and field
     * picks are kept in separate "test_" collections so the league's real data is never touched.
     */
    var isTestMode by mutableStateOf(testModeAvailable && prefs.getBoolean(TEST_MODE_KEY, false))
        private set

    fun updateTestMode(enabled: Boolean) {
        if (enabled == isTestMode) return
        isTestMode = enabled
        prefs.edit().putBoolean(TEST_MODE_KEY, enabled).apply()
        restartListeners()
    }

    private val statsCollection: String get() = if (isTestMode) "test_weeklyStats" else "weeklyStats"
    private val weekInfoCollection: String get() = if (isTestMode) "test_gameWeekInfo" else "gameWeekInfo"

    /** Deletes everything entered in Test Mode */
    suspend fun clearTestData() {
        for (name in listOf("test_weeklyStats", "test_gameWeekInfo")) {
            val docs = db.collection(name).get().await().documents
            // Firestore batches hold at most 500 writes
            for (chunk in docs.chunked(450)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
    }

    private fun restartListeners() {
        removeListeners()
        weeklyStats = emptyList()
        gameWeekInfos = emptyMap()
        calculateSeasonStats()
        setupListeners()
    }

    /** LA chapter roster (excludes Portland visitors) */
    val laPlayers: List<Player> get() = players.filter { !it.isPortland }
    val portlandPlayers: List<Player> get() = players.filter { it.isPortland }

    /** Players that can be picked for the current week */
    val selectablePlayers: List<Player>
        get() = if (currentGameWeek?.specialEvent?.includesPortland == true) players else laPlayers

    init {
        Log.d(TAG, "Initializing; week index $currentWeekIndex, week $currentWeekNumber")
        setupListeners()
    }

    override fun onCleared() {
        removeListeners()
    }

    private fun removeListeners() {
        playersListener?.remove()
        statsListener?.remove()
        gameWeekInfoListener?.remove()
        adjustmentsListener?.remove()
    }

    // MARK: - Real-time Listeners

    private fun setupListeners() {
        // Admin career / badge corrections (always league data, even in Test Mode)
        adjustmentsListener = db.collection("careerAdjustments").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "careerAdjustments error: ${error.localizedMessage}")
                return@addSnapshotListener
            }
            careerAdjustments = snapshot?.documents.orEmpty()
                .mapNotNull { CareerAdjustment.from(it) }
                .associateBy { it.playerName }
        }

        playersListener = db.collection("players").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "players error: ${error.localizedMessage}")
                errorMessage = "Failed to load players: ${error.localizedMessage}"
                return@addSnapshotListener
            }
            val documents = snapshot?.documents ?: return@addSnapshotListener
            players = documents.mapNotNull { Player.from(it) }
                .filter { it.isActive }
                .sortedBy { it.name.lowercase() }
            // Portland players are excluded from season totals, so recalc when roster changes
            calculateSeasonStats()
        }

        // All stats (filtered by season in memory)
        val statsSource = statsCollection
        statsListener = db.collection(statsSource).addSnapshotListener { snapshot, error ->
            // Ignore late updates from before a Test Mode switch
            if (statsCollection != statsSource) return@addSnapshotListener
            if (error != null) {
                Log.e(TAG, "stats error: ${error.localizedMessage}")
                errorMessage = "Failed to load stats: ${error.localizedMessage}"
                return@addSnapshotListener
            }
            val documents = snapshot?.documents ?: return@addSnapshotListener
            weeklyStats = documents.mapNotNull { WeeklyStats.from(it) }
                .sortedWith(compareBy<WeeklyStats> { it.weekNumber }.thenBy { it.playerName })
            calculateSeasonStats()
        }

        // Game week info (field selections)
        val weekInfoSource = weekInfoCollection
        gameWeekInfoListener = db.collection(weekInfoSource).addSnapshotListener { snapshot, error ->
            if (weekInfoCollection != weekInfoSource) return@addSnapshotListener
            if (error != null) {
                Log.e(TAG, "gameWeekInfo error: ${error.localizedMessage}")
                return@addSnapshotListener
            }
            val documents = snapshot?.documents ?: return@addSnapshotListener
            val season = SeasonSchedule.seasonNumber
            val infos = HashMap<Int, GameWeekInfo>()
            for (doc in documents) {
                val info = GameWeekInfo.from(doc) ?: continue
                if (info.seasonNumber == season) infos[info.weekNumber] = info
            }
            gameWeekInfos = infos
        }
    }

    // MARK: - Player Management

    fun addPlayer(name: String, jerseyNumber: Int? = null, teamName: String? = null) {
        val player = Player(name = name, jerseyNumber = jerseyNumber, teamName = teamName)
        val ref = db.collection("players").document()
        ref.set(player.toFirestore())
        Log.d(TAG, "Player added with ID: ${ref.id}")
    }

    fun updatePlayer(player: Player) {
        val playerId = player.id ?: return
        db.collection("players").document(playerId).set(player.copy(updatedAt = Date()).toFirestore())
    }

    suspend fun deletePlayer(player: Player) {
        val playerId = player.id ?: return
        db.collection("players").document(playerId)
            .update(mapOf("isActive" to false, "updatedAt" to Date()))
            .await()
    }

    // MARK: - Stats Management

    suspend fun getOrCreateWeeklyStats(player: Player, week: Int, year: Int): WeeklyStats {
        val playerId = player.id ?: throw IllegalStateException("Player ID is nil")

        val snapshot = db.collection(statsCollection)
            .whereEqualTo("playerId", playerId)
            .whereEqualTo("weekNumber", week)
            .whereEqualTo("year", year)
            .whereEqualTo("season", currentSeason)
            .get()
            .await()

        snapshot.documents.firstOrNull()?.let { doc -> WeeklyStats.from(doc)?.let { return it } }

        val ref = db.collection(statsCollection).document()
        val newStats = WeeklyStats(id = ref.id, playerId = playerId, playerName = player.name, weekNumber = week, year = year)
        ref.set(newStats.toFirestore())
        return newStats
    }

    /** Saves (or clears) an admin correction to a player's career totals and badges */
    suspend fun saveCareerAdjustment(adjustment: CareerAdjustment) {
        val doc = db.collection("careerAdjustments").document(adjustment.playerName)
        if (adjustment.isEmpty) {
            doc.delete().await()
        } else {
            doc.set(adjustment.copy(updatedAt = Date()).toFirestore())
        }
    }

    /** Takes a player out of a week's game (deletes their entry for that week) */
    suspend fun removeWeeklyStats(stats: WeeklyStats) {
        val statsId = stats.id ?: return
        db.collection(statsCollection).document(statsId).delete().await()
    }

    fun updateWeeklyStats(stats: WeeklyStats) {
        val statsId = stats.id ?: return
        db.collection(statsCollection).document(statsId).set(stats.copy(updatedAt = Date()).toFirestore())
    }

    suspend fun batchUpdateStats(statsArray: List<WeeklyStats>) {
        val batch = db.batch()
        for (stats in statsArray) {
            val statsId = stats.id ?: continue
            batch.set(db.collection(statsCollection).document(statsId), stats.copy(updatedAt = Date()).toFirestore())
        }
        batch.commit().await()
    }

    // MARK: - Game Week Info (Field Selection)

    suspend fun getOrCreateGameWeekInfo(weekNumber: Int): GameWeekInfo {
        gameWeekInfos[weekNumber]?.let { return it }

        val snapshot = db.collection(weekInfoCollection)
            .whereEqualTo("weekNumber", weekNumber)
            .whereEqualTo("season", currentSeason)
            .get()
            .await()

        snapshot.documents.firstOrNull()?.let { doc -> GameWeekInfo.from(doc)?.let { return it } }

        val ref = db.collection(weekInfoCollection).document()
        val newInfo = GameWeekInfo(id = ref.id, weekNumber = weekNumber)
        ref.set(newInfo.toFirestore())
        return newInfo
    }

    suspend fun updateGameWeekField(weekNumber: Int, field: GameField?) {
        val info = getOrCreateGameWeekInfo(weekNumber).withField(field)
        val infoId = info.id ?: return
        db.collection(weekInfoCollection).document(infoId).set(info.toFirestore())
        // Update local cache immediately so the UI reflects the change
        gameWeekInfos = gameWeekInfos + (weekNumber to info)
    }

    fun getFieldForWeek(weekNumber: Int): GameField? = gameWeekInfos[weekNumber]?.gameField

    // MARK: - Stats Calculations

    fun calculateSeasonStats() {
        val statsDict = LinkedHashMap<String, YearlyStats>()

        // Portland visitors (Coattail Classic) don't count toward LA season stats
        val portlandIds = portlandPlayers.mapNotNull { it.id }.toSet()

        // Alias mapping merges duplicates (e.g. "Boogie" -> "Boogie Joe")
        for (weekly in weeklyStats) {
            if (weekly.seasonNumber == currentSeason &&
                weekly.playerId !in portlandIds &&
                schedule.gameWeek(weekly.weekNumber) != null
            ) {
                val canonical = PlayerAliases.canonicalName(weekly.playerName)
                val existing = statsDict[canonical]
                    ?: YearlyStats(playerId = weekly.playerId, playerName = canonical, year = currentYear)
                statsDict[canonical] = existing.addingWeeklyStats(weekly)
            }
        }

        yearlyStats = statsDict.values.sortedByDescending { it.totalDongs }
    }

    fun getWeeklyStatsForWeek(week: Int): List<WeeklyStats> =
        weeklyStats.filter { it.weekNumber == week && it.seasonNumber == currentSeason }

    fun getStatsForPlayer(playerId: String): List<WeeklyStats> =
        weeklyStats.filter { it.playerId == playerId && it.seasonNumber == currentSeason }.sortedBy { it.weekNumber }

    // MARK: - Week Navigation

    fun previousWeek() {
        if (currentWeekIndex > 0) currentWeekIndex -= 1
    }

    /** Upcoming weeks can be browsed to see the schedule */
    fun nextWeek() {
        if (canGoNext()) currentWeekIndex += 1
    }

    fun canGoNext(): Boolean = currentWeekIndex < schedule.totalWeeks - 1

    /** Jump back to the most recent game */
    fun goToCurrentWeek() {
        currentWeekIndex = schedule.currentWeekIndex()
    }

    fun canGoPrevious(): Boolean = currentWeekIndex > 0

    fun goToWeek(index: Int) {
        if (index >= 0 && index < schedule.totalWeeks) currentWeekIndex = index
    }

    companion object {
        /** Debug builds only (like the iOS app's Xcode/DEBUG builds) */
        val testModeAvailable: Boolean get() = BuildConfig.DEBUG
        private const val TEST_MODE_KEY = "testMode"
    }
}
