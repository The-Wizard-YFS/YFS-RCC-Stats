package com.stickball.stats.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stickball.stats.model.CareerStats
import com.stickball.stats.model.HistoricalData
import com.stickball.stats.model.Player
import com.stickball.stats.model.PlayerAliases
import com.stickball.stats.service.StatsService
import kotlinx.coroutines.launch

@Composable
fun PlayersScreen() {
    val stats = LocalStats.current
    val admin = LocalAdmin.current
    val scope = rememberCoroutineScope()

    var showingAddPlayer by rememberSaveable { mutableStateOf(false) }
    var editingPlayerId by rememberSaveable { mutableStateOf<String?>(null) }
    var playerToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCareerName by rememberSaveable { mutableStateOf<String?>(null) }
    var showingAdminPIN by rememberSaveable { mutableStateOf(false) }

    // Career stats for lookup (includes the current season from Firebase)
    val allCareerStats = HistoricalData.calculateCareerStats(stats.yearlyStats, stats.careerAdjustments)
    fun careerStatsFor(player: Player): CareerStats? {
        val canonical = PlayerAliases.canonicalName(player.name)
        return allCareerStats.firstOrNull { it.playerName == canonical }
    }

    Box(Modifier.fillMaxSize()) {
        ScreenScroll(spacing = 4.dp) {
            Box(Modifier.fillMaxWidth()) {
                SRHeader(banner = "SR_Players_Banner", bannerWidth = 0.8f)
                // Padlock when locked; gold ADMIN plate when unlocked (tap to lock again)
                SRImageButton(
                    if (admin.isAdminMode) "Admin_Unlocked" else "Admin_Locked",
                    onClick = { if (admin.isAdminMode) admin.logout() else showingAdminPIN = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 4.dp)
                        .size(if (admin.isAdminMode) 104.dp else 54.dp, 50.dp),
                    contentDescription = if (admin.isAdminMode) "Admin mode on. Tap to lock" else "Unlock admin mode",
                )
            }

            if (admin.isAdminMode) {
                SRImageButton(
                    "Add_New_Player_Button",
                    onClick = { showingAddPlayer = true },
                    modifier = Modifier.padding(horizontal = 40.dp),
                    contentDescription = "Add new player",
                )
                // Test Mode controls (debug builds only)
                if (StatsService.testModeAvailable) {
                    TestModePanel(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
                }
            }

            if (stats.players.isEmpty()) {
                Text(
                    if (admin.isAdminMode) "Tap Add New Player to get started" else "No players added yet",
                    style = monoStyle(12.dp, SRColors.gold),
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                // Negative spacing absorbs the transparent glow padding in the row art
                OverlapColumn(spacing = (-32).dp, modifier = Modifier.padding(horizontal = 8.dp)) {
                    PlayerRows(stats.laPlayers, admin.isAdminMode, ::careerStatsFor,
                        onTap = { selectedCareerName = it.playerName },
                        onEdit = { editingPlayerId = it.id },
                        onDelete = { playerToDeleteId = it.id },
                    )
                    // Visiting Portland chapter (Coattail Classic only, no LA career stats)
                    if (stats.portlandPlayers.isNotEmpty()) {
                        Text(
                            "PORTLAND CHAPTER",
                            style = slabStyle(16.dp).gold(),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 12.dp),
                        )
                        PlayerRows(stats.portlandPlayers, admin.isAdminMode, { null },
                            onTap = {},
                            onEdit = { editingPlayerId = it.id },
                            onDelete = { playerToDeleteId = it.id },
                        )
                    }
                }
            }

            SRFooterLogo()
        }

        val playerToDelete = stats.players.firstOrNull { it.id != null && it.id == playerToDeleteId }
        // Keep showing the last player while the modal fades out
        val lastDeleteTarget = remember { arrayOfNulls<Player>(1) }
        if (playerToDelete != null) lastDeleteTarget[0] = playerToDelete
        AnimatedVisibility(playerToDelete != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            val player = lastDeleteTarget[0] ?: return@AnimatedVisibility
            BackHandler { playerToDeleteId = null }
            DeletePlayerModal(
                playerName = player.name,
                onCancel = { playerToDeleteId = null },
                onDelete = {
                    scope.launch { runCatching { stats.deletePlayer(player) } }
                    playerToDeleteId = null
                },
            )
        }
    }

    if (showingAddPlayer) {
        SRSheet(onDismissRequest = { showingAddPlayer = false }) { dismiss -> AddEditPlayerView(null, dismiss) }
    }
    val editing = stats.players.firstOrNull { it.id != null && it.id == editingPlayerId }
    if (editing != null) {
        SRSheet(onDismissRequest = { editingPlayerId = null }) { dismiss -> AddEditPlayerView(editing, dismiss) }
    }
    if (showingAdminPIN) {
        SRSheet(onDismissRequest = { showingAdminPIN = false }) { dismiss -> AdminPINEntryView(dismiss) }
    }
    val career = allCareerStats.firstOrNull { it.playerName == selectedCareerName }
    if (career != null) {
        SRSheet(onDismissRequest = { selectedCareerName = null }) { dismiss ->
            PlayerCareerDetailView(career, stats.careerAdjustments[career.playerName], dismiss)
        }
    }
}

@Composable
private fun PlayerRows(
    players: List<Player>,
    isAdminMode: Boolean,
    careerStatsFor: (Player) -> CareerStats?,
    onTap: (CareerStats) -> Unit,
    onEdit: (Player) -> Unit,
    onDelete: (Player) -> Unit,
) {
    ArtColumn(
        items = players,
        spacing = (-32).dp,
        bandTop = 0.26f,
        bandBottom = 0.78f,
        itemKey = { it.id ?: it.name },
        // Whole-row tap opens the career profile
        onClick = { player -> careerStatsFor(player)?.let(onTap) },
    ) { player, _ ->
        PlayerManagementRow(player, careerStatsFor(player), isAdminMode, { onEdit(player) }, { onDelete(player) })
    }
}

// MARK: - Test Mode Panel
/** Debug-build switch for entering stats on upcoming weeks without touching league data */
@Composable
private fun TestModePanel(modifier: Modifier = Modifier) {
    val stats = LocalStats.current
    val scope = rememberCoroutineScope()
    var isClearing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier.fillMaxWidth().srPlate(glow = SRColors.pink).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SRToggle(stats.isTestMode, { stats.updateTestMode(it) }, tint = SRColors.pink) {
            Text("TEST MODE", style = displayStyle(15.dp).gold())
            Text(
                "Enter stats on upcoming weeks. Saved separately from league data.",
                style = monoStyle(10.dp, SRColors.text.copy(alpha = 0.85f)),
            )
        }
        if (stats.isTestMode) {
            Row(
                Modifier.plainClickable(enabled = !isClearing) { confirmClear = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isClearing) SRProgress(SRColors.pink, 16.dp) else Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp), tint = SRColors.pink)
                Text("CLEAR TEST DATA", style = displayStyle(13.dp, SRColors.pink))
            }
        }
        message?.let { Text(it, style = monoStyle(10.dp, SRColors.gold)) }
    }

    if (confirmClear) {
        SRConfirm(
            title = "Delete all test stats and test field picks?",
            message = null,
            destructiveLabel = "Clear Test Data",
            onConfirm = {
                confirmClear = false
                isClearing = true
                message = null
                scope.launch {
                    message = try {
                        stats.clearTestData()
                        "Test data cleared."
                    } catch (e: Exception) {
                        "Couldn't clear test data: ${e.localizedMessage}"
                    }
                    isClearing = false
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

// MARK: - Player Management Row
/** Directory row: medallion initial, name, career line, and admin edit/delete buttons */
@Composable
private fun PlayerManagementRow(
    player: Player,
    careerStats: CareerStats?,
    isAdminMode: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SRArtPlate("SR_Player_Directory_Row_Unselected") { size ->
        Text(
            player.name.take(1).uppercase(),
            style = slabStyle(size.height * 0.30f).copy(brush = SRColors.silverGradient).blackShadow(),
            modifier = Modifier.centerAt(size.width * 0.121f, size.height * 0.545f),
        )

        Column(
            Modifier
                .offset(x = size.width * 0.22f, y = size.height * 0.35f)
                .width(size.width * (if (isAdminMode) 0.44f else 0.58f)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FitText(player.name.uppercase(), slabStyle(size.height * 0.17f).blackShadow())
            when {
                careerStats != null -> FitText(
                    "${careerStats.totalDongs} CAREER DONGS • ${careerStats.seasonsPlayed} SEASONS",
                    monoStyle(size.height * 0.095f, SRColors.gold),
                )
                player.isPortland -> FitText("PORTLAND CHAPTER", monoStyle(size.height * 0.095f, SRColors.pink))
            }
        }

        // Edit / delete sit over the row's chevron in admin mode
        if (isAdminMode) {
            Row(
                Modifier.centerAt(size.width * 0.84f, size.height * 0.55f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SRImageButton("Edit_Button", onEdit, Modifier.size(size.height * 0.56f), contentDescription = "Edit ${player.name}")
                SRImageButton("Delete_Button", onDelete, Modifier.size(size.height * 0.56f), contentDescription = "Delete ${player.name}")
            }
        }
    }
}

// MARK: - Delete Player Modal
@Composable
private fun DeletePlayerModal(playerName: String, onCancel: () -> Unit, onDelete: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).plainClickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        SRArtPlate("SR_Delete_Modal_Blank", Modifier.padding(horizontal = 32.dp).plainClickable { }) { size ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = size.width * 0.12f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(size.height * 0.24f))
                FitText(
                    "Remove $playerName?",
                    displayStyle(size.height * 0.07f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Spacer(Modifier.height(size.height * 0.05f))
                Text(
                    "Their stats are kept, but they won't appear in active lists.",
                    style = monoStyle(size.height * 0.04f, SRColors.gold),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                Row(Modifier.height(size.height * 0.16f), horizontalArrangement = Arrangement.spacedBy(size.width * 0.04f)) {
                    SRImageButton("Cancel_Button", onCancel, Modifier.weight(1f), contentDescription = "Cancel")
                    SRImageButton("Delete_Action_Button", onDelete, Modifier.weight(1f), contentDescription = "Delete")
                }
                Spacer(Modifier.height(size.height * 0.08f))
            }
        }
    }
}

// MARK: - Admin PIN Entry
@Composable
internal fun AdminPINEntryView(dismiss: () -> Unit) {
    val admin = LocalAdmin.current
    var pin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    fun addDigit(digit: String) {
        if (pin.length >= 4) return
        showError = false
        pin += digit
        if (pin.length == 4) {
            if (admin.authenticate(pin)) {
                dismiss()
            } else {
                showError = true
                pin = ""
            }
        }
    }

    fun deleteDigit() {
        if (pin.isEmpty()) return
        pin = pin.dropLast(1)
        showError = false
    }

    Box(Modifier.fillMaxSize()) {
        TronGridBackground()
        ScreenScroll(spacing = 18.dp, includeTabBar = false) {
            Spacer(Modifier.height(2.dp))
            SheetTopButton("Cancel_Button", "Cancel", dismiss)

            SRArt("SR_Admin_PIN_Title", Modifier.padding(horizontal = 30.dp))

            // PIN cells
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { index ->
                    SRArt(if (index < pin.length) "SR_PIN_Cell_Filled" else "SR_PIN_Cell_Empty", Modifier.size(58.dp))
                }
            }

            Text(if (showError) "INCORRECT PIN" else " ", style = displayStyle(14.dp, TronColors.orange))

            // Number pad
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (col in 1..3) {
                            val number = row * 3 + col
                            PINButton(number) { addDigit("$number") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Spacer(Modifier.size(76.dp))
                    PINButton(0) { addDigit("0") }
                    SRImageButton("PIN_Backspace", ::deleteDigit, Modifier.size(76.dp), contentDescription = "Delete digit")
                }
            }
        }
    }
}

/** Gold keypad key with a diamond digit */
@Composable
private fun PINButton(number: Int, onClick: () -> Unit) {
    SRImageButton("PIN_Key", onClick, Modifier.size(76.dp), contentDescription = "$number") {
        DiamondNumber(number, 34.dp, Modifier.align(Alignment.Center))
    }
}
