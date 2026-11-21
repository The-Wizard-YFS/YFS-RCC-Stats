package com.stickball.stats.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stickball.stats.model.Player
import com.stickball.stats.model.WeeklyStats
import kotlinx.coroutines.launch

// MARK: - Single player entry
/** One player's stats for the current week */
@Composable
fun PlayerStatsEntryView(player: Player, dismiss: () -> Unit) {
    val stats = LocalStats.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<WeeklyStats?>(null) }
    var values by remember { mutableStateOf(StatValues()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val s = stats.getOrCreateWeeklyStats(player, stats.currentWeek, stats.currentYear)
            loaded = s
            values = StatValues(s.dongs, s.drops, s.doublePlays, s.salamies, s.wins)
        } catch (e: Exception) {
            saveError = e.localizedMessage ?: e.toString()
        }
        isLoading = false
    }

    // "SATURDAY · SEP 26 · WEEK 4 · THE AIRFIELD"
    val detailLine = buildList {
        stats.currentGameWeek?.let { add(it.dayTitle) }
        add("WEEK ${stats.currentWeekNumber}")
        stats.getFieldForWeek(stats.currentWeekNumber)?.let { add(it.rawValue.uppercase()) }
    }.joinToString(" · ")

    Box(Modifier.fillMaxSize()) {
        SRBackground()
        ScreenScroll(spacing = 12.dp, includeTabBar = false, bottomPadding = 24.dp) {
            // Player name and week on the header plaque
            SRArtPlate("SR_Player_Header_Plaque_Blank", Modifier.padding(start = 12.dp, end = 12.dp, top = 24.dp)) { size ->
                FitText(
                    player.name.uppercase(),
                    displayStyle(size.height * 0.20f).blackShadow(),
                    Modifier.centerAt(size.width * 0.5f, size.height * 0.35f).width(size.width * 0.72f),
                    minScale = 0.5f,
                    textAlign = TextAlign.Center,
                )
                FitText(
                    detailLine,
                    displayStyle(size.height * 0.09f).gold(),
                    Modifier.centerAt(size.width * 0.5f, size.height * 0.655f).width(size.width * 0.66f),
                    minScale = 0.5f,
                    textAlign = TextAlign.Center,
                )
            }

            if (isLoading) {
                Box(Modifier.padding(top = 40.dp)) { SRProgress(size = 32.dp) }
            } else {
                StatEditorPanel(values, { values = it }, Modifier.padding(horizontal = 14.dp))

                // Save / Cancel
                SRImageButton(
                    "Save_Stats_Button",
                    onClick = {
                        val current = loaded ?: return@SRImageButton
                        isSaving = true
                        scope.launch {
                            try {
                                stats.updateWeeklyStats(values.applyTo(current))
                                dismiss()
                            } catch (e: Exception) {
                                saveError = e.localizedMessage ?: e.toString()
                            }
                            isSaving = false
                        }
                    },
                    modifier = Modifier.padding(horizontal = 24.dp),
                    enabled = !isSaving && loaded != null,
                    contentDescription = "Save stats",
                ) {
                    if (isSaving) Box(Modifier.align(Alignment.Center)) { SRProgress(Color.White) }
                }
                SRImageButton("Cancel_Button", dismiss, Modifier.size(150.dp, 60.dp), contentDescription = "Cancel")
            }
        }
    }

    saveError?.let { SRAlert("COULDN'T SAVE", it) { saveError = null } }
}

/** The five weekly stats being edited */
data class StatValues(
    val dongs: Int = 0,
    val drops: Int = 0,
    val doublePlays: Int = 0,
    val salamies: Int = 0,
    val wins: Int = 0,
) {
    fun applyTo(stats: WeeklyStats) =
        stats.copy(dongs = dongs, drops = drops, doublePlays = doublePlays, salamies = salamies, wins = wins)

    companion object {
        fun of(stats: WeeklyStats) = StatValues(stats.dongs, stats.drops, stats.doublePlays, stats.salamies, stats.wins)
    }
}

// MARK: - Edit Week (batch entry)
/** EDIT WEEK: accordion batch entry for every player in this week's game */
@Composable
fun BatchStatsEntryView(dismiss: () -> Unit) {
    val stats = LocalStats.current
    val scope = rememberCoroutineScope()

    val playerStats = remember { mutableStateMapOf<String, WeeklyStats>() }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var expandedPlayerId by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Only players who were selected for this week
    val weekIds = stats.getWeeklyStatsForWeek(stats.currentWeek).map { it.playerId }.toSet()
    val playersThisWeek = stats.players.filter { (it.id ?: "") in weekIds }

    LaunchedEffect(Unit) {
        stats.getWeeklyStatsForWeek(stats.currentWeek).forEach { playerStats[it.playerId] = it }
        isLoading = false
    }

    fun saveAll() {
        isSaving = true
        scope.launch {
            try {
                stats.batchUpdateStats(playerStats.values.toList())
                dismiss()
            } catch (e: Exception) {
                saveError = e.localizedMessage ?: e.toString()
            }
            isSaving = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        SRBackground()
        ScreenScroll(spacing = 8.dp, includeTabBar = false, bottomPadding = 24.dp) {
            // Cancel / EDIT WEEK / Save All
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                SRArt("SR_Edit_Week_Title", Modifier.padding(horizontal = 70.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).offset(y = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SRImageButton("Cancel_Button", dismiss, Modifier.size(104.dp, 44.dp), contentDescription = "Cancel")
                    Spacer(Modifier.weight(1f))
                    SRImageButton(
                        "Save_All_Button", ::saveAll, Modifier.size(116.dp, 48.dp),
                        enabled = !isSaving && !isLoading, contentDescription = "Save all",
                    ) {
                        if (isSaving) Box(Modifier.align(Alignment.Center)) { SRProgress(Color.Black, 18.dp) }
                    }
                }
            }

            EditorDatePlaque(Modifier.padding(horizontal = 24.dp))

            when {
                isLoading -> Box(Modifier.padding(top = 40.dp)) { SRProgress(size = 32.dp) }
                playersThisWeek.isEmpty() -> Column(
                    Modifier.padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("NO PLAYERS SELECTED", style = displayStyle(18.dp))
                    Text("Add players for this week first", style = monoStyle(12.dp, SRColors.gold))
                }
                else -> Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    playersThisWeek.forEach { player ->
                        val playerId = player.id ?: return@forEach
                        val entry = playerStats[playerId] ?: return@forEach
                        BatchPlayerRow(
                            player = player,
                            stats = entry,
                            onChange = { playerStats[playerId] = it },
                            isExpanded = expandedPlayerId == playerId,
                            onTap = { expandedPlayerId = if (expandedPlayerId == playerId) null else playerId },
                        )
                    }
                }
            }
        }
    }

    saveError?.let { SRAlert("COULDN'T SAVE", it) { saveError = null } }
}

@Composable
private fun BatchPlayerRow(
    player: Player,
    stats: WeeklyStats,
    onChange: (WeeklyStats) -> Unit,
    isExpanded: Boolean,
    onTap: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Header row (always visible)
        SRArtPlate(
            if (isExpanded) "SR_Accordion_Row_Selected" else "SR_Accordion_Row_Unselected",
            Modifier.plainClickable(onClick = onTap),
        ) { size ->
            Row(
                Modifier.fillMaxSize().padding(start = size.width * 0.07f, end = size.width * 0.06f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FitText(player.name.uppercase(), displayStyle(size.height * 0.26f), Modifier.weight(1f), minScale = 0.5f)
                StatChips(stats, chipWidth = size.width * 0.085f, chipHeight = size.height * 0.42f)
                SRArt(
                    if (isExpanded) "SR_Accordion_Chevron_Up" else "SR_Accordion_Chevron_Down",
                    Modifier.width(size.height * 0.30f),
                )
            }
        }

        // Expanded editing area
        AnimatedVisibility(
            isExpanded,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
        ) {
            StatEditorPanel(StatValues.of(stats), { onChange(it.applyTo(stats)) }, Modifier.padding(horizontal = 10.dp))
        }
    }
}

/** D / DR / DP / S / W value chips shown on each accordion row */
@Composable
private fun StatChips(stats: WeeklyStats, chipWidth: Dp, chipHeight: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Chip("D", stats.dongs, SRColors.purple, chipWidth, chipHeight)
        Chip("DR", stats.drops, TronColors.orange, chipWidth, chipHeight)
        Chip("DP", stats.doublePlays, SRColors.pink, chipWidth, chipHeight)
        Chip("S", stats.salamies, TronColors.green, chipWidth, chipHeight)
        Chip("W", stats.wins, SRColors.gold, chipWidth, chipHeight)
    }
}

@Composable
private fun Chip(label: String, value: Int, color: Color, width: Dp, height: Dp) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier
            .size(width, height)
            .glowShadow(color.copy(alpha = 0.6f), 3.dp, 4.dp)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Black)), shape)
            .border(1.5.dp, color, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = displayStyle(height * 0.26f).gold(), maxLines = 1)
        FitText("$value", displayStyle(height * 0.46f), textAlign = TextAlign.Center)
    }
}

// MARK: - Stat Editor Panel
/** Gold-ruled grid of +/- steppers: Dongs, Drops, Salamies, Wins, then Double Plays */
@Composable
fun StatEditorPanel(values: StatValues, onChange: (StatValues) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().srPlate(glow = SRColors.purple, cornerRadius = 8.dp).padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            GoldStepper("DONGS", values.dongs, { onChange(values.copy(dongs = it)) }, Modifier.weight(1f))
            GoldStepper("DROPS", values.drops, { onChange(values.copy(drops = it)) }, Modifier.weight(1f).leadingGoldRule())
        }
        GoldRule()
        Row(Modifier.fillMaxWidth()) {
            GoldStepper("SALAMIES", values.salamies, { onChange(values.copy(salamies = it)) }, Modifier.weight(1f))
            GoldStepper("WINS", values.wins, { onChange(values.copy(wins = it)) }, Modifier.weight(1f).leadingGoldRule())
        }
        GoldRule()
        GoldStepper("DOUBLE PLAYS", values.doublePlays, { onChange(values.copy(doublePlays = it)) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun GoldRule() {
    Box(Modifier.fillMaxWidth().height(1.5.dp).background(SRColors.goldGradient))
}

/** Vertical gold rule along the left edge */
private fun Modifier.leadingGoldRule(): Modifier = drawBehind {
    drawRect(SRColors.goldGradient, size = Size(1.5.dp.toPx(), size.height))
}

// MARK: - Gold Stepper
@Composable
fun GoldStepper(title: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = displayStyle(15.dp), maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SRImageButton(
                "Stepper_Minus_Button",
                onClick = { if (value > 0) onChange(value - 1) },
                modifier = Modifier.size(46.dp).alpha(if (value > 0) 1f else 0.5f),
                contentDescription = "Decrease ${title.lowercase()}",
            )
            Text(
                "$value",
                style = displayStyle(34.dp).blackShadow(),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 40.dp),
            )
            SRImageButton(
                "Stepper_Plus_Button",
                onClick = { onChange(value + 1) },
                modifier = Modifier.size(46.dp),
                contentDescription = "Increase ${title.lowercase()}",
            )
        }
    }
}

// MARK: - Editor Date Plaque
/** "SATURDAY · SEP 26" over "WEEK 4 · THE AIRFIELD" */
@Composable
fun EditorDatePlaque(modifier: Modifier = Modifier) {
    val stats = LocalStats.current
    val detailLine = buildList {
        add("WEEK ${stats.currentWeekNumber}")
        stats.currentGameWeek?.specialEvent?.let { add(it.rawValue.uppercase()) }
        stats.getFieldForWeek(stats.currentWeekNumber)?.let { add(it.rawValue.uppercase()) }
    }.joinToString(" · ")

    SRArtPlate("SR_Editor_Date_Field_Plaque_Blank", modifier) { size ->
        FitText(
            stats.currentGameWeek?.dayTitle ?: "",
            displayStyle(size.height * 0.22f),
            Modifier.centerAt(size.width * 0.5f, size.height * 0.39f).width(size.width * 0.8f),
            minScale = 0.5f,
            textAlign = TextAlign.Center,
        )
        FitText(
            detailLine,
            displayStyle(size.height * 0.12f).gold(),
            Modifier.centerAt(size.width * 0.5f, size.height * 0.695f).width(size.width * 0.74f),
            minScale = 0.5f,
            textAlign = TextAlign.Center,
        )
    }
}
