package com.stickball.stats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stickball.stats.model.GameField
import com.stickball.stats.model.Player
import kotlinx.coroutines.launch

/** Pick who played this week and the field */
@Composable
fun WeeklyPlayerSelectionView(dismiss: () -> Unit) {
    val stats = LocalStats.current
    val scope = rememberCoroutineScope()

    var selectedPlayerIds by remember { mutableStateOf(setOf<String>()) }
    var selectedField by remember { mutableStateOf<GameField?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var removalsWithStats by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        // Pre-select players who already have stats for this week, and the saved field
        selectedPlayerIds = stats.getWeeklyStatsForWeek(stats.currentWeek).map { it.playerId }.toSet()
        selectedField = stats.getFieldForWeek(stats.currentWeekNumber)
    }

    // Entries for players who were in this week's game but are now unchecked
    val entriesToRemove = stats.getWeeklyStatsForWeek(stats.currentWeek).filter { it.playerId !in selectedPlayerIds }
    // Something to save: players picked, players to remove, or a field change
    val hasChanges = selectedPlayerIds.isNotEmpty() || entriesToRemove.isNotEmpty() ||
        selectedField != stats.getFieldForWeek(stats.currentWeekNumber)
    val showsPortland = stats.currentGameWeek?.specialEvent?.includesPortland == true

    fun togglePlayer(player: Player) {
        val id = player.id ?: return
        selectedPlayerIds = if (id in selectedPlayerIds) selectedPlayerIds - id else selectedPlayerIds + id
    }

    fun confirmSelection() {
        isSaving = true
        val removals = entriesToRemove
        scope.launch {
            val failures = mutableListOf<String>()

            selectedField?.let { field ->
                try {
                    stats.updateGameWeekField(stats.currentWeekNumber, field)
                } catch (e: Exception) {
                    failures += "Field: ${e.localizedMessage}"
                }
            }

            // Take unchecked players out of this week's game
            for (entry in removals) {
                try {
                    stats.removeWeeklyStats(entry)
                } catch (e: Exception) {
                    failures += "${entry.playerName}: ${e.localizedMessage}"
                }
            }

            // Create stats entries for all selected players
            for (player in stats.selectablePlayers) {
                val id = player.id ?: continue
                if (id !in selectedPlayerIds) continue
                try {
                    stats.getOrCreateWeeklyStats(player, stats.currentWeek, stats.currentYear)
                } catch (e: Exception) {
                    failures += "${player.name}: ${e.localizedMessage}"
                }
            }

            isSaving = false
            if (failures.isEmpty()) {
                dismiss()
            } else {
                // Stay open so nothing silently disappears
                saveError = failures.take(3).joinToString("\n") +
                    if (failures.size > 3) "\n…and ${failures.size - 3} more" else ""
            }
        }
    }

    /** Asks first if unchecking would delete recorded stats */
    fun confirmTapped() {
        val withStats = entriesToRemove.filter { it.hasAnyStats }
        if (withStats.isEmpty()) {
            confirmSelection()
        } else {
            removalsWithStats = withStats.map { entry ->
                stats.players.firstOrNull { it.id == entry.playerId }?.name ?: entry.playerName
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        SRBackground()
        ScreenScroll(spacing = 10.dp, includeTabBar = false, bottomPadding = 140.dp) {
            Spacer(Modifier.height(2.dp))
            SheetTopButton("Cancel_Button", "Cancel", dismiss)

            SRArt("SR_Weekly_Banner", Modifier.padding(horizontal = 60.dp))

            stats.currentGameWeek?.specialEvent?.let {
                Text(it.rawValue.uppercase(), style = displayStyle(16.dp).gold())
            }

            EditorDatePlaque(Modifier.padding(horizontal = 24.dp))

            Text("WHO PLAYED?", style = slabStyle(20.dp).blackShadow(radius = 2.dp))

            // Field plates
            Column(
                Modifier.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("FIELD", style = displayStyle(13.dp).gold())
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    GameField.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            row.forEach { field ->
                                FieldChip(field, selectedField == field, { selectedField = field }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Quick actions
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("SELECT ALL") { selectedPlayerIds = stats.selectablePlayers.mapNotNull { it.id }.toSet() }
                QuickAction("CLEAR ALL") { selectedPlayerIds = emptySet() }
                Spacer(Modifier.weight(1f))
                Text("${selectedPlayerIds.size} SELECTED", style = displayStyle(14.dp).gold())
            }

            SelectionRows(stats.laPlayers, selectedPlayerIds, ::togglePlayer)

            // Portland chapter joins for the Coattail Classic
            if (showsPortland) {
                Text(
                    "PORTLAND CHAPTER",
                    style = slabStyle(16.dp).gold(),
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp),
                )
                if (stats.portlandPlayers.isEmpty()) {
                    Text("Add Portland players in the Players tab", style = monoStyle(11.dp, SRColors.gold))
                } else {
                    SelectionRows(stats.portlandPlayers, selectedPlayerIds, ::togglePlayer)
                }
            }
        }

        // Gold primary-action plate pinned to the bottom
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            PrimaryActionButton(
                title = "SAVE PLAYERS",
                icon = Icons.Filled.Check,
                isSaving = isSaving,
                enabled = hasChanges && !isSaving,
                textScale = 0.13f,
                onClick = ::confirmTapped,
                modifier = Modifier.alpha(if (hasChanges) 1f else 0.5f),
            )
        }
    }

    saveError?.let { SRAlert("COULDN'T SAVE", it) { saveError = null } }
    if (removalsWithStats.isNotEmpty()) {
        SRConfirm(
            title = "Remove ${removalsWithStats.joinToString(", ")} from this week?",
            message = "Stats already entered for them this week will be deleted.",
            destructiveLabel = "Remove and Delete Their Stats",
            onConfirm = {
                removalsWithStats = emptyList()
                confirmSelection()
            },
            onDismiss = { removalsWithStats = emptyList() },
        )
    }
}

@Composable
private fun SelectionRows(players: List<Player>, selected: Set<String>, onToggle: (Player) -> Unit) {
    ArtColumn(
        items = players,
        spacing = (-26).dp,
        modifier = Modifier.padding(horizontal = 8.dp),
        bandTop = 0.27f,
        bandBottom = 0.72f,
        itemKey = { it.id ?: it.name },
        onClick = onToggle,
    ) { player, _ ->
        PlayerSelectionTile(player, (player.id ?: "") in selected)
    }
}

@Composable
private fun QuickAction(title: String, onClick: () -> Unit) {
    Text(
        title,
        style = displayStyle(12.dp).gold(),
        modifier = Modifier
            .plainClickable(onClick = onClick)
            .srPlate(cornerRadius = 6.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** Blank category plate with the field's icon and name; Uncle Kimmy's Playhouse is the night game */
@Composable
private fun FieldChip(field: GameField, isSelected: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(52.dp).plainClickable(onClick = onTap), contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center) {
            SRArt(if (isSelected) "SR_Selected_Blank" else "SR_Unselected_Blank", Modifier.height(if (isSelected) 52.dp else 46.dp))
            Row(
                Modifier.matchParentSize().padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            ) {
                Icon(field.icon, null, Modifier.size(14.dp), tint = if (field.isNightGame) SRColors.goldLight else SRColors.gold)
                FitText(field.rawValue.uppercase(), displayStyle(13.dp), Modifier.weight(1f, fill = false), minScale = 0.5f)
            }
        }
    }
}

/** Gold accordion row with a diamond checkbox */
@Composable
private fun PlayerSelectionTile(player: Player, isSelected: Boolean) {
    SRArtPlate(if (isSelected) "SR_Accordion_Row_Selected" else "SR_Accordion_Row_Unselected") { size ->
        Row(
            Modifier.fillMaxSize().padding(start = size.width * 0.07f, end = size.width * 0.06f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SRArt(if (isSelected) "SR_PIN_Cell_Filled" else "SR_PIN_Cell_Empty", Modifier.size(size.height * 0.40f))
            FitText(
                player.name.uppercase(),
                displayStyle(size.height * 0.24f, if (isSelected) SRColors.text else SRColors.text.copy(alpha = 0.7f)),
                Modifier.weight(1f),
            )
        }
    }
}
