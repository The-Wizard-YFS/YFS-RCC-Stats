package com.stickball.stats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stickball.stats.model.AchievementsCalculator
import com.stickball.stats.model.GameField
import com.stickball.stats.model.GameWeek
import com.stickball.stats.model.Player
import com.stickball.stats.model.WeeklyStats
import kotlinx.coroutines.launch

@Composable
fun WeeklyStatsScreen() {
    val stats = LocalStats.current
    val admin = LocalAdmin.current
    var showingPlayerSelection by rememberSaveable { mutableStateOf(false) }
    var showingBatchEntry by rememberSaveable { mutableStateOf(false) }
    var showingSchedule by rememberSaveable { mutableStateOf(false) }
    var selectedPlayerId by rememberSaveable { mutableStateOf<String?>(null) }

    val weekStats = stats.getWeeklyStatsForWeek(stats.currentWeek)

    // Players who have stats for the current week (i.e. were selected to play)
    val weekPlayerIds = weekStats.map { it.playerId }.toSet()
    val playersThisWeek = stats.players.filter { (it.id ?: "") in weekPlayerIds }

    // Player of the Week (shown as TOP BALLER)
    val topBaller: Player? = run {
        if (weekStats.isEmpty()) return@run null
        var bestPlayer: Player? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (s in weekStats) {
            val score = AchievementsCalculator.calculateWeeklyScore(s.dongs, s.doublePlays, s.wins, s.drops)
            if (score > bestScore) {
                bestScore = score
                bestPlayer = stats.players.firstOrNull { it.id == s.playerId }
            }
        }
        if (bestPlayer != null && bestScore > 0) bestPlayer else null
    }

    fun currentWeekStats(player: Player): WeeklyStats? {
        val playerId = player.id ?: return null
        return stats.weeklyStats.firstOrNull {
            it.playerId == playerId &&
                it.weekNumber == stats.currentWeek &&
                it.year == stats.currentYear &&
                it.seasonNumber == stats.currentSeason
        }
    }

    val showsAdminButtons = admin.isAdminMode && stats.canEnterStatsForCurrentWeek
    val gameWeek = stats.currentGameWeek

    ScreenScroll(spacing = 8.dp) {
        // Title and WEEKLY banner, flanked by the admin add-players and edit-week buttons
        Box(Modifier.fillMaxWidth()) {
            SRHeader(banner = "SR_Weekly_Banner", bannerWidth = 0.62f)
            if (showsAdminButtons) {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                ) {
                    SRImageButton(
                        "Add_Player_Icon_Button",
                        onClick = { showingPlayerSelection = true },
                        modifier = Modifier.size(62.dp),
                        contentDescription = "Add players for this week",
                    )
                    Spacer(Modifier.weight(1f))
                    if (playersThisWeek.isNotEmpty()) {
                        SRImageButton(
                            "Edit_Week_Icon_Button",
                            onClick = { showingBatchEntry = true },
                            modifier = Modifier.size(62.dp),
                            contentDescription = "Edit this week's stats",
                        )
                    }
                }
            }
        }

        WeekNavigationHeader(onShowSchedule = { showingSchedule = true })

        when {
            gameWeek != null && gameWeek.isBlockedOut ->
                BlockedOutWeekView(gameWeek, Modifier.padding(vertical = 24.dp))

            stats.isViewingFutureWeek && !stats.isTestMode && gameWeek != null ->
                UpcomingWeekView(gameWeek, Modifier.padding(vertical = 24.dp))

            playersThisWeek.isEmpty() ->
                SRArtPlate("SR_Player_Row_Empty", Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NO PLAYERS ADDED", style = displayStyle(18.dp))
                        Text(
                            if (admin.isAdminMode) "Tap + to select who played" else "Admin mode required to add players",
                            style = monoStyle(11.dp, SRColors.gold),
                        )
                    }
                }

            else -> {
                if (topBaller != null) {
                    TopBallerCallout(topBaller.name, Modifier.padding(horizontal = 12.dp))
                }
                WeeklyStatsTable(
                    rows = playersThisWeek.mapNotNull { p -> currentWeekStats(p)?.let { p to it } },
                    isAdminMode = admin.isAdminMode,
                    onSelect = { selectedPlayerId = it.id },
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }

        SRFooterLogo()
    }

    if (showingPlayerSelection) {
        SRSheet(onDismissRequest = { showingPlayerSelection = false }) { dismiss -> WeeklyPlayerSelectionView(dismiss) }
    }
    val selectedPlayer = stats.players.firstOrNull { it.id != null && it.id == selectedPlayerId }
    if (selectedPlayer != null) {
        SRSheet(onDismissRequest = { selectedPlayerId = null }) { dismiss -> PlayerStatsEntryView(selectedPlayer, dismiss) }
    }
    if (showingBatchEntry) {
        SRSheet(onDismissRequest = { showingBatchEntry = false }) { dismiss -> BatchStatsEntryView(dismiss) }
    }
    if (showingSchedule) {
        SRSheet(onDismissRequest = { showingSchedule = false }) { dismiss -> SeasonScheduleView(dismiss) }
    }
}

// MARK: - Top Baller Callout
/** Player of the week on the TOP BALLER plaque */
@Composable
private fun TopBallerCallout(playerName: String, modifier: Modifier = Modifier) {
    SRArtPlate("SR_Top_Baller_Callout_Blank", modifier) { size ->
        FitText(
            playerName.uppercase(),
            displayStyle(size.height * 0.24f).copy(brush = SRColors.silverGradient).blackShadow(),
            Modifier.centerAt(size.width * 0.57f, size.height * 0.60f).width(size.width * 0.66f),
            textAlign = TextAlign.Center,
        )
    }
}

// MARK: - Weekly Stats Table
private val tableColumns = listOf("DONGS", "DROPS", "DBL PLAY", "SALAMIES", "WINS")

/** Gold-ruled table of this week's stats (Dongs, Drops, Double Plays, Salamies, Wins) */
@Composable
private fun WeeklyStatsTable(
    rows: List<Pair<Player, WeeklyStats>>,
    isAdminMode: Boolean,
    onSelect: (Player) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().srPlate(glow = SRColors.purple, cornerRadius = 6.dp)) {
        val nameColumn = maxWidth * 0.30f
        Column {
            // Header
            Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                FitText("PLAYER", displayStyle(12.dp).gold(), Modifier.width(nameColumn), textAlign = TextAlign.Center)
                tableColumns.forEach { title ->
                    GoldDivider()
                    FitText(title, displayStyle(12.dp).gold(), Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            rows.forEach { (player, s) ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(SRColors.goldGradient))
                Row(
                    Modifier.height(36.dp).plainClickable(enabled = isAdminMode) { onSelect(player) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FitText(
                        player.name.uppercase(),
                        displayStyle(15.dp),
                        Modifier.width(nameColumn).padding(start = 8.dp),
                        minScale = 0.55f,
                    )
                    listOf(s.dongs, s.drops, s.doublePlays, s.salamies, s.wins).forEach { value ->
                        GoldDivider()
                        Text(
                            "$value",
                            style = displayStyle(18.dp, if (value > 0) SRColors.text else SRColors.text.copy(alpha = 0.45f)),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoldDivider() {
    Box(Modifier.width(1.5.dp).fillMaxHeight().background(SRColors.goldGradient))
}

// MARK: - Week Navigation Header
@Composable
private fun WeekNavigationHeader(onShowSchedule: () -> Unit) {
    val stats = LocalStats.current
    val admin = LocalAdmin.current
    var showingFieldPicker by rememberSaveable { mutableStateOf(false) }
    val gameWeek = stats.currentGameWeek

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Special event name
        gameWeek?.specialEvent?.let { event ->
            Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.rawValue.uppercase(), style = displayStyle(18.dp).gold())
                event.subtitle?.let {
                    Text(it.uppercase(), style = monoStyle(10.dp, SRColors.pink), textAlign = TextAlign.Center)
                }
            }
        }

        // Arrows and date panel
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SRImageButton(
                "Week_Arrow_Left",
                onClick = { stats.previousWeek() },
                modifier = Modifier.size(58.dp).alpha(if (stats.canGoPrevious()) 1f else 0.4f),
                enabled = stats.canGoPrevious(),
                contentDescription = "Previous week",
            )
            SRArtPlate("SR_Weekly_Date_Panel_Blank", Modifier.weight(1f).plainClickable(onClick = onShowSchedule)) { size ->
                // Lines sit where SwiftUI's centered VStack puts them (Abril Fatface line height ≈ 1.33em)
                FitText(
                    gameWeek?.dayTitle ?: "",
                    displayStyle(size.height * 0.24f),
                    Modifier.centerAt(size.width * 0.5f, size.height * 0.40f).width(size.width * 0.84f),
                    textAlign = TextAlign.Center,
                )
                val parts = buildList {
                    if (gameWeek?.isBlockedOut != true) add("WEEK ${stats.currentWeekNumber}")
                    if (stats.isViewingFutureWeek) add("· UPCOMING")
                }
                Text(
                    parts.joinToString("  "),
                    style = displayStyle(size.height * 0.15f).gold(),
                    modifier = Modifier.centerAt(size.width * 0.5f, size.height * 0.66f),
                )
            }
            SRImageButton(
                "Week_Arrow_Right",
                onClick = { stats.nextWeek() },
                modifier = Modifier.size(58.dp).alpha(if (stats.canGoNext()) 1f else 0.4f),
                enabled = stats.canGoNext(),
                contentDescription = "Next week",
            )
        }

        // Field plate (tappable for admins) with night game badge
        if (gameWeek?.isBlockedOut != true) {
            FieldSelectorButton(
                weekNumber = stats.currentWeekNumber,
                isAdminMode = admin.isAdminMode,
                isOpen = showingFieldPicker,
                onTap = { showingFieldPicker = true },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (stats.gameWeekInfos[stats.currentWeekNumber]?.gameField?.isNightGame == true) {
                NightGameBadge()
            }
        }

        // Shortcut back to this week's game when browsing other weeks
        if (stats.currentWeekIndex != stats.schedule.currentWeekIndex()) {
            Text(
                "BACK TO THIS WEEK",
                style = monoStyle(11.dp, SRColors.pink).copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier.plainClickable { stats.goToCurrentWeek() },
            )
        }
    }

    if (showingFieldPicker) {
        SRSheet(onDismissRequest = { showingFieldPicker = false }, fullHeight = false) { dismiss ->
            FieldPickerSheet(stats.currentWeekNumber, dismiss)
        }
    }
}

// MARK: - Field Selector Button
@Composable
private fun FieldSelectorButton(weekNumber: Int, isAdminMode: Boolean, isOpen: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val stats = LocalStats.current
    val currentField = stats.gameWeekInfos[weekNumber]?.gameField

    SRArtPlate(
        if (isOpen) "SR_Weekly_Field_Selector_Selected" else "SR_Weekly_Field_Selector_Unselected",
        modifier.plainClickable(enabled = isAdminMode, onClick = onTap),
    ) { size ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = size.width * 0.08f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (currentField != null) {
                Icon(currentField.icon, null, Modifier.size(size.height * 0.26f).goldTint(), tint = Color.White)
            }
            FitText(
                currentField?.rawValue?.uppercase() ?: "NO FIELD SET",
                displayStyle(size.height * 0.26f, if (currentField != null) SRColors.text else SRColors.gold.copy(alpha = 0.7f)),
                Modifier.weight(1f, fill = false),
            )
        }
        // Chevron only for admins; read-only plate otherwise
        if (isAdminMode) {
            SRArt(
                if (isOpen) "SR_Field_Chevron_Up" else "SR_Field_Chevron_Down",
                Modifier.align(Alignment.CenterEnd).padding(end = size.width * 0.07f).width(size.height * 0.36f),
            )
        }
    }
}

// MARK: - Field Picker Sheet
@Composable
private fun FieldPickerSheet(weekNumber: Int, dismiss: () -> Unit) {
    val stats = LocalStats.current
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth()) {
        TronGridBackground()
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp)) {
                Text(
                    "Cancel",
                    style = androidx.compose.ui.text.TextStyle(color = TronColors.orange, fontSize = 17.dp.asSp()),
                    modifier = Modifier.plainClickable(onClick = dismiss),
                )
            }
            Text("SELECT FIELD", style = systemMono(18.dp, TronColors.cyan).glow(TronColors.cyan, 5.dp))

            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GameField.entries.forEach { field ->
                    FieldOptionRow(field, isSelected = stats.getFieldForWeek(weekNumber) == field) {
                        scope.launch {
                            runCatching { stats.updateGameWeekField(weekNumber, field) }
                            dismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldOptionRow(field: GameField, isSelected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) TronColors.green.copy(alpha = 0.1f) else TronColors.cardBackground, shape)
            .border(1.dp, if (isSelected) TronColors.green.copy(alpha = 0.5f) else TronColors.gridLine.copy(alpha = 0.3f), shape)
            .plainClickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp)) {
            Icon(field.icon, null, Modifier.size(20.dp), tint = if (isSelected) TronColors.green else TronColors.secondaryText)
        }
        Text(field.rawValue.uppercase(), style = systemMono(14.dp, if (isSelected) TronColors.green else TronColors.primaryText))
        Spacer(Modifier.weight(1f))
        if (isSelected) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = TronColors.green)
        }
    }
}

// MARK: - Night Game Badge
@Composable
private fun NightGameBadge() {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .background(TronColors.yellow.copy(alpha = 0.12f), shape)
            .border(1.dp, TronColors.yellow.copy(alpha = 0.5f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.NightsStay, null, Modifier.size(11.dp), tint = TronColors.yellow)
        Text("NIGHT GAME", style = systemMono(10.dp, TronColors.yellow))
    }
}

// MARK: - Upcoming / Blocked Out
@Composable
private fun UpcomingWeekView(gameWeek: GameWeek, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(56.dp), tint = TronColors.cyan.copy(alpha = 0.5f))
        Text("UPCOMING GAME", style = systemMono(18.dp, TronColors.secondaryText))
        Text(gameWeek.formattedDate.uppercase(), style = systemMono(14.dp, TronColors.cyan, androidx.compose.ui.text.font.FontWeight.Medium))
        if (gameWeek.specialEvent?.includesPortland == true) {
            Text("PORTLAND CHAPTER IN TOWN", style = systemMono(11.dp, TronColors.magenta))
        }
        Text("Stats open on game day", style = systemMono(12.dp, TronColors.dimText, androidx.compose.ui.text.font.FontWeight.Normal))
    }
}

@Composable
private fun BlockedOutWeekView(gameWeek: GameWeek, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Filled.SportsScore, null, Modifier.size(56.dp), tint = TronColors.magenta)
        Text(
            (gameWeek.specialEvent?.rawValue ?: "Blocked Out").uppercase(),
            style = systemMono(22.dp, TronColors.magenta).glow(TronColors.magenta, 6.dp),
            textAlign = TextAlign.Center,
        )
        gameWeek.specialEvent?.subtitle?.let {
            Text(
                it.uppercase(),
                style = systemMono(11.dp, TronColors.secondaryText, androidx.compose.ui.text.font.FontWeight.Medium),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        Text("NO LEAGUE GAME THIS WEEKEND", style = systemMono(12.dp, TronColors.dimText))
    }
}

// MARK: - Season Schedule
@Composable
internal fun SeasonScheduleView(dismiss: () -> Unit) {
    val stats = LocalStats.current
    val thisWeekIndex = stats.schedule.currentWeekIndex()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, thisWeekIndex - 3))

    Box(Modifier.fillMaxSize()) {
        TronGridBackground()
        Column(Modifier.fillMaxSize()) {
            // Inline navigation bar
            Box(Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal = 16.dp)) {
                Text("SEASON SCHEDULE", style = systemMono(16.dp, TronColors.cyan), modifier = Modifier.align(Alignment.Center))
                Text(
                    "Done",
                    style = androidx.compose.ui.text.TextStyle(color = TronColors.cyan, fontSize = 17.dp.asSp()),
                    modifier = Modifier.align(Alignment.CenterEnd).plainClickable(onClick = dismiss),
                )
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                itemsIndexed(stats.schedule.gameWeeks, key = { _, w -> w.id }) { index, week ->
                    ScheduleRow(
                        week = week,
                        field = stats.gameWeekInfos[week.weekNumber]?.gameField,
                        isThisWeek = index == thisWeekIndex,
                        isUpcoming = stats.schedule.isFutureWeek(index),
                        modifier = Modifier.plainClickable {
                            stats.goToWeek(index)
                            dismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(week: GameWeek, field: GameField?, isThisWeek: Boolean, isUpcoming: Boolean, modifier: Modifier = Modifier) {
    val accent = when {
        week.isBlockedOut || week.specialEvent != null -> TronColors.magenta
        isUpcoming -> TronColors.secondaryText
        else -> TronColors.cyan
    }
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        isThisWeek -> TronColors.cyan
        week.specialEvent != null -> TronColors.magenta.copy(alpha = 0.5f)
        else -> TronColors.gridLine.copy(alpha = 0.3f)
    }
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (isUpcoming || isThisWeek) 1f else 0.7f)
            .background(if (week.isBlockedOut) TronColors.magenta.copy(alpha = 0.12f) else TronColors.cardBackground, shape)
            .border(if (isThisWeek) 2.dp else 1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(week.shortFormattedDate, style = systemMono(14.dp, accent), modifier = Modifier.width(48.dp))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (week.isBlockedOut) week.displayTitle.uppercase() else "WEEK ${week.weekNumber}",
                style = systemMono(13.dp, TronColors.primaryText),
            )
            val event = week.specialEvent
            if (event != null && !week.isBlockedOut) {
                Text(event.rawValue.uppercase(), style = systemMono(10.dp, TronColors.magenta))
            }
            if (week.isBlockedOut) {
                Text("BLOCKED OUT · NO LEAGUE GAME", style = systemMono(10.dp, TronColors.magenta.copy(alpha = 0.8f)))
            } else if (field != null) {
                val color = if (field.isNightGame) TronColors.yellow else TronColors.green
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(field.icon, null, Modifier.size(10.dp), tint = color)
                    Text(
                        field.rawValue.uppercase() + if (field.isNightGame) " · NIGHT GAME" else "",
                        style = systemMono(9.dp, color, androidx.compose.ui.text.font.FontWeight.Medium),
                    )
                }
            }
        }

        if (isThisWeek) {
            Text(
                "NOW",
                style = systemMono(9.dp, TronColors.darkBackground),
                modifier = Modifier.background(TronColors.cyan, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}
