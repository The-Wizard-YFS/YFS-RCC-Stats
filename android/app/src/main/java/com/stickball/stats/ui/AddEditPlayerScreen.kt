package com.stickball.stats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.stickball.stats.model.AchievementsCalculator
import com.stickball.stats.model.CareerAdjustment
import com.stickball.stats.model.CareerStats
import com.stickball.stats.model.HistoricalData
import com.stickball.stats.model.Player
import com.stickball.stats.model.PlayerAchievements
import com.stickball.stats.model.PlayerAliases
import kotlinx.coroutines.launch

/** The career totals and badges an admin can edit, as the profile shows them */
data class CareerEditValues(
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
    val coattails: Boolean = false,
    val galactics: Boolean = false,
    val wideOpen: Boolean = false,
) {
    constructor(stats: CareerStats, achievements: PlayerAchievements) : this(
        dongs = stats.totalDongs,
        drops = stats.totalDrops,
        doublePlays = stats.totalDoublePlays,
        salamies = stats.totalSalamies,
        wins = stats.totalWins,
        dongRobs = stats.totalDongRobs,
        yfslaChampionships = achievements.yfslaChampCount,
        mvp = achievements.mvpCount,
        dongKing = achievements.dongKingCount,
        rookieOfYear = achievements.rookieOfYearCount,
        coattails = achievements.hasCoattails,
        galactics = achievements.hasGalactics,
        wideOpen = achievements.hasWideOpen,
    )

    /** The correction that turns the calculated values (base) into these */
    fun adjustment(playerName: String, base: CareerEditValues) = CareerAdjustment(
        playerName = playerName,
        dongs = dongs - base.dongs,
        drops = drops - base.drops,
        doublePlays = doublePlays - base.doublePlays,
        salamies = salamies - base.salamies,
        wins = wins - base.wins,
        dongRobs = dongRobs - base.dongRobs,
        yfslaChampionships = yfslaChampionships - base.yfslaChampionships,
        mvp = mvp - base.mvp,
        dongKing = dongKing - base.dongKing,
        rookieOfYear = rookieOfYear - base.rookieOfYear,
        coattails = if (coattails == base.coattails) null else coattails,
        galactics = if (galactics == base.galactics) null else galactics,
        wideOpen = if (wideOpen == base.wideOpen) null else wideOpen,
    )
}

@Composable
fun AddEditPlayerView(player: Player?, dismiss: () -> Unit) {
    val stats = LocalStats.current
    val scope = rememberCoroutineScope()
    val isEditing = player != null

    var name by remember { mutableStateOf(player?.name ?: "") }
    var isPortland by remember { mutableStateOf(player?.isPortland ?: false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isNameFocused by remember { mutableStateOf(false) }

    // Career totals and badges as shown on the profile (editing existing LA players)
    var careerValues by remember { mutableStateOf(CareerEditValues()) }
    var loadedCareerValues by remember { mutableStateOf<CareerEditValues?>(null) }
    var baseCareerValues by remember { mutableStateOf<CareerEditValues?>(null) }

    // Career stats are tracked under the player's canonical name (e.g. "Candy" -> "Candyman")
    val canonicalName = PlayerAliases.canonicalName(player?.name ?: name)
    val trimmedName = name.trim { it == ' ' || it == '\t' }

    LaunchedEffect(Unit) {
        if (player != null && !player.isPortland) {
            val canonical = PlayerAliases.canonicalName(player.name)
            // Calculated values with no admin correction
            val base = HistoricalData.calculateCareerStats(stats.yearlyStats).firstOrNull { it.playerName == canonical }
                ?: CareerStats(canonical)
            val baseValues = CareerEditValues(base, AchievementsCalculator.calculateAchievements(canonical, base))
            // What the profile currently shows, including any saved correction
            val adjustment = stats.careerAdjustments[canonical]
            val current = if (adjustment != null) {
                val adjusted = base.applying(adjustment)
                CareerEditValues(adjusted, AchievementsCalculator.calculateAchievements(canonical, adjusted, adjustment))
            } else baseValues
            baseCareerValues = baseValues
            loadedCareerValues = current
            careerValues = current
        }
    }

    fun savePlayer() {
        if (trimmedName.isEmpty()) return
        isSaving = true
        scope.launch {
            try {
                if (player != null) {
                    stats.updatePlayer(player.copy(name = trimmedName, teamName = if (isPortland) Player.PORTLAND_CHAPTER else null))
                    // Career / badge corrections, saved as differences from the calculated values
                    val loaded = loadedCareerValues
                    val base = baseCareerValues
                    if (loaded != null && base != null && careerValues != loaded) {
                        stats.saveCareerAdjustment(careerValues.adjustment(canonicalName, base))
                    }
                } else {
                    stats.addPlayer(trimmedName, teamName = if (isPortland) Player.PORTLAND_CHAPTER else null)
                }
                dismiss()
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: e.toString()
            }
            isSaving = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        TronGridBackground()
        ScreenScroll(spacing = 18.dp, includeTabBar = false, bottomPadding = 24.dp) {
            Spacer(Modifier.height(0.dp))
            SheetTopButton("Cancel_Button", "Cancel", dismiss)

            SRArt(
                if (isEditing) "SR_Edit_Player_Title" else "SR_Add_Player_Title",
                Modifier.padding(horizontal = 20.dp),
                contentDescription = if (isEditing) "Edit player" else "Add player",
            )

            // Name field on the gold plate
            SRArtPlate(
                if (isNameFocused) "SR_Player_Name_Field_Selected" else "SR_Player_Name_Field_Unselected",
                Modifier.padding(horizontal = 12.dp),
            ) { size ->
                val style = displayStyle(size.height * 0.20f).copy(textAlign = TextAlign.Center)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(SRColors.gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false),
                    modifier = Modifier
                        .centerAt(size.width * 0.5f, size.height * 0.44f)
                        .width(size.width * 0.74f)
                        .onFocusChanged { isNameFocused = it.isFocused },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (name.isEmpty()) Text("PLAYER NAME", style = style.copy(color = SRColors.gold.copy(alpha = 0.6f)))
                            inner()
                        }
                    },
                )
            }

            // Portland players only appear for the Coattail Classic
            SRToggle(
                isPortland, { isPortland = it }, tint = SRColors.purple,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .srPlate(glow = SRColors.purple)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("PORTLAND CHAPTER", style = displayStyle(15.dp).gold())
                Text("Coattail Classic only · not in LA stats", style = monoStyle(11.dp, SRColors.text.copy(alpha = 0.8f)))
            }

            if (loadedCareerValues != null) {
                CareerEditor(careerValues, { careerValues = it }, showsUnsaved = careerValues != loadedCareerValues)
            }

            // Add Player / Save Changes
            PrimaryActionButton(
                title = if (isEditing) "SAVE CHANGES" else "ADD PLAYER",
                icon = if (isEditing) Icons.Filled.Check else Icons.Filled.Add,
                isSaving = isSaving,
                enabled = trimmedName.isNotEmpty() && !isSaving,
                textScale = 0.15f,
                onClick = ::savePlayer,
                modifier = Modifier.padding(horizontal = 20.dp).alpha(if (trimmedName.isEmpty()) 0.5f else 1f),
            )
        }
    }

    errorMessage?.let { SRAlert("ERROR", it) { errorMessage = null } }
}

/** Gold "primary action" plate with an icon in the left socket and a title */
@Composable
fun PrimaryActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSaving: Boolean,
    enabled: Boolean,
    textScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SRImageButton("Primary_Action_Button_Blank", onClick, modifier, enabled = enabled, contentDescription = title) { size: DpSize ->
        Icon(
            icon, null,
            Modifier.centerAt(size.width * 0.205f, size.height * 0.48f).size(size.height * 0.20f),
            tint = Color.Black.copy(alpha = 0.75f),
        )
        Box(
            Modifier.centerAt(size.width * 0.55f, size.height * 0.48f).width(size.width * 0.52f),
            contentAlignment = Alignment.Center,
        ) {
            if (isSaving) SRProgress(SRColors.text) else FitText(title, slabStyle(size.height * textScale), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CareerEditor(values: CareerEditValues, onChange: (CareerEditValues) -> Unit, showsUnsaved: Boolean) {
    Column(
        Modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("CAREER TOTALS", style = slabStyle(18.dp).gold())
        StepperGrid(
            Modifier.srPlate(glow = SRColors.purple),
            listOf(
                Triple("DONGS", values.dongs) { v: Int -> onChange(values.copy(dongs = v)) },
                Triple("DOUBLE PLAYS", values.doublePlays) { v: Int -> onChange(values.copy(doublePlays = v)) },
                Triple("SALAMIES", values.salamies) { v: Int -> onChange(values.copy(salamies = v)) },
                Triple("DONG ROBS", values.dongRobs) { v: Int -> onChange(values.copy(dongRobs = v)) },
                Triple("WINS", values.wins) { v: Int -> onChange(values.copy(wins = v)) },
                Triple("DROPS", values.drops) { v: Int -> onChange(values.copy(drops = v)) },
                Triple("YFSLA TITLES", values.yfslaChampionships) { v: Int -> onChange(values.copy(yfslaChampionships = v)) },
            ),
        )

        Text("BADGES", style = slabStyle(18.dp).gold(), modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.fillMaxWidth().srPlate(glow = SRColors.purple), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StepperGrid(
                Modifier,
                listOf(
                    Triple("MVP", values.mvp) { v: Int -> onChange(values.copy(mvp = v)) },
                    Triple("DONG KING", values.dongKing) { v: Int -> onChange(values.copy(dongKing = v)) },
                    Triple("ROOKIE OF YEAR", values.rookieOfYear) { v: Int -> onChange(values.copy(rookieOfYear = v)) },
                ),
            )
            BadgeToggle("COATTAILS", values.coattails) { onChange(values.copy(coattails = it)) }
            BadgeToggle("GALACTICS", values.galactics) { onChange(values.copy(galactics = it)) }
            BadgeToggle("WIDE OPEN", values.wideOpen) { onChange(values.copy(wideOpen = it)) }
            Text(
                "100–500 Club and Salami Club badges follow career dongs and salamies.",
                style = monoStyle(10.dp, SRColors.text.copy(alpha = 0.8f)),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(10.dp),
            )
        }

        if (showsUnsaved) {
            Text("Tap Save Changes to apply", style = monoStyle(11.dp, SRColors.pink))
        }
    }
}

/** Two-column grid of gold steppers */
@Composable
private fun StepperGrid(modifier: Modifier, items: List<Triple<String, Int, (Int) -> Unit>>) {
    Column(modifier.fillMaxWidth()) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (title, value, set) -> GoldStepper(title, value, set, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SRToggle(checked, onChange, tint = SRColors.purple, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
        Text(title, style = displayStyle(15.dp))
    }
}

