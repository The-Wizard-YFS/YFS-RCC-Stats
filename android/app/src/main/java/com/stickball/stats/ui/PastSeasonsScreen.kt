package com.stickball.stats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.stickball.stats.model.AchievementsCalculator
import com.stickball.stats.model.HistoricalData
import com.stickball.stats.model.HistoricalPlayerSeasonStats
import com.stickball.stats.model.HistoricalSeasonStats
import com.stickball.stats.model.SeasonAwards

@Composable
fun PastSeasonsScreen() {
    var selectedSeason by rememberSaveable { mutableStateOf<Int?>(null) }

    ScreenScroll(spacing = 6.dp) {
        // PAST SEASONS banner on its own, larger, in place of the title lockup
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SRArt(
                "SR_Past_Seasons_Banner",
                Modifier.width(maxWidth * 0.98f).padding(top = 4.dp, bottom = 6.dp),
                contentDescription = "Past Seasons",
            )
        }

        // Most recent season first (current season is excluded)
        ArtColumn(
            items = HistoricalData.pastSeasons.reversed(),
            spacing = (-30).dp,
            modifier = Modifier.padding(horizontal = 8.dp),
            bandTop = 0.22f,
            bandBottom = 0.79f,
            itemKey = { it.seasonNumber },
            onClick = { selectedSeason = it.seasonNumber },
        ) { season, pressed ->
            // Archive row art lights up while pressed
            SRArtPlate(if (pressed) "SR_Past_Season_Row_Selected" else "SR_Past_Season_Row_Unselected") { size ->
                SeasonCard(season, size.width, size.height)
            }
        }

        SRFooterLogo()
    }

    val season = HistoricalData.pastSeasons.firstOrNull { it.seasonNumber == selectedSeason }
    if (season != null) {
        SRSheet(onDismissRequest = { selectedSeason = null }) { dismiss ->
            SeasonDetailView(season, dismiss)
        }
    }
}

/** Text laid over the archive row: medallion number, title, subtitle and dong leader */
@Composable
private fun SeasonCard(season: HistoricalSeasonStats, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    // First player with the most dongs (Swift max(by:))
    val topDonger = season.playerStats.fold<HistoricalPlayerSeasonStats, HistoricalPlayerSeasonStats?>(null) { best, p ->
        if (best == null || p.dongs > best.dongs) p else best
    }
    Box(Modifier.fillMaxSize()) {
        DiamondNumber(season.seasonNumber, h * 0.44f, Modifier.centerAt(w * 0.121f, h * 0.505f))

        Column(
            Modifier.offset(x = w * 0.22f, y = h * 0.285f).width(w * 0.60f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            FitText("SEASON ${season.seasonNumber}", slabStyle(h * 0.165f).copy(brush = SRColors.silverGradient))
            FitText(season.seasonName.uppercase(), monoStyle(h * 0.095f))
            if (topDonger != null) {
                val line = buildAnnotatedString {
                    withStyle(SpanStyle(color = SRColors.gold)) { append("DONG LEADER: ") }
                    withStyle(SpanStyle(color = SRColors.pink)) { append("${topDonger.playerName.uppercase()} (${topDonger.dongs})") }
                }
                FitAnnotatedText(line, monoStyle(h * 0.095f))
            }
        }
    }
}

@Composable
private fun FitAnnotatedText(text: androidx.compose.ui.text.AnnotatedString, style: androidx.compose.ui.text.TextStyle) {
    val max = style.fontSize
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            minFontSize = max * 0.6f,
            maxFontSize = max,
            stepSize = (max.value * 0.03f).coerceAtLeast(0.25f).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) },
        ),
    )
}

/** Season detail categories; Dong Robs has no art so it uses the blank plate with text */
enum class SeasonCategory(val title: String, private val artName: String?) {
    DONGS("DONGS", "Dongs"),
    SALAMIES("SALAMIES", "Salamies"),
    WINS("WINS", "Wins"),
    DROPS("DROPS", "Drops"),
    DOUBLE_PLAYS("DOUBLE PLAYS", "Double_Plays"),
    DONG_ROBS("DONG ROBS", null);

    fun value(stat: HistoricalPlayerSeasonStats): Int = when (this) {
        DONGS -> stat.dongs
        DROPS -> stat.drops
        DOUBLE_PLAYS -> stat.doublePlays
        SALAMIES -> stat.salamies
        WINS -> stat.wins
        DONG_ROBS -> stat.dongRobs
    }

    @Composable
    fun ButtonArt(isSelected: Boolean) {
        val state = if (isSelected) "Selected" else "Unselected"
        val height = if (isSelected) 52.dp else 40.dp
        if (artName == null) {
            Box(contentAlignment = Alignment.Center) {
                SRArt("SR_${state}_Blank", Modifier.height(height), contentDescription = title)
                Text("ROBS", style = slabStyle(if (isSelected) 20.dp else 16.dp))
            }
        } else {
            SRArt("SR_${state}_$artName", Modifier.height(height), contentDescription = title)
        }
    }
}

@Composable
internal fun SeasonDetailView(season: HistoricalSeasonStats, dismiss: () -> Unit) {
    var selectedCategory by rememberSaveable { mutableStateOf(SeasonCategory.DONGS) }
    var showingAwards by rememberSaveable { mutableStateOf(false) }

    val seasonAwards = AchievementsCalculator.getAllSeasonAwards().firstOrNull { it.seasonNumber == season.seasonNumber }
        ?: SeasonAwards(season.seasonNumber, season.seasonName)

    val sortedStats = season.playerStats
        .filter { selectedCategory.value(it) > 0 }
        .sortedByDescending { selectedCategory.value(it) }

    Box(Modifier.fillMaxSize()) {
        SRBackground()
        ScreenScroll(spacing = 10.dp, includeTabBar = false, bottomPadding = 24.dp) {
            Spacer(Modifier.height(2.dp))
            SheetTopButton("Done_Button", "Done", dismiss, height = 48.dp)

            // Season title plaque
            SRArtPlate("SR_Historic_Season_Header_Blank", Modifier.padding(horizontal = 10.dp)) { size ->
                Text(
                    "SEASON ${season.seasonNumber}",
                    style = slabStyle(size.height * 0.20f).blackShadow(),
                    modifier = Modifier.centerAt(size.width * 0.5f, size.height * 0.44f),
                )
                FitText(
                    season.seasonName.uppercase(),
                    displayStyle(size.height * 0.11f).gold(),
                    Modifier.centerAt(size.width * 0.5f, size.height * 0.825f).width(size.width * 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            SRImageButton(
                "Season_Awards_Button",
                onClick = { showingAwards = true },
                modifier = Modifier.padding(horizontal = 48.dp),
                contentDescription = "Season awards",
            )

            CategoryArtRow(SeasonCategory.entries, selectedCategory, { selectedCategory = it }) { category, isSelected ->
                category.ButtonArt(isSelected)
            }

            if (sortedStats.isEmpty()) {
                Text("NO DATA", style = displayStyle(18.dp, SRColors.gold), modifier = Modifier.padding(top = 24.dp))
            } else {
                ArtColumn(
                    items = sortedStats.withIndex().toList(),
                    spacing = (-22).dp,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    itemKey = { it.value.id },
                ) { (index, stat), _ ->
                    SRRankRow(rank = index + 1, name = stat.playerName, value = selectedCategory.value(stat))
                }
            }
        }
    }

    if (showingAwards) {
        SRSheet(onDismissRequest = { showingAwards = false }) { close ->
            SeasonAwardsView(seasonAwards, close)
        }
    }
}

@Composable
internal fun SeasonAwardsView(awards: SeasonAwards, dismiss: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        TronGridBackground()
        ScreenScroll(spacing = 16.dp, includeTabBar = false, bottomPadding = 16.dp, modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(0.dp))
            Row(Modifier.fillMaxWidth()) {
                SRImageButton("Done_Button", dismiss, Modifier.size(104.dp, 48.dp), contentDescription = "Done")
                Spacer(Modifier.weight(1f))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("SEASON ${awards.seasonNumber}", style = displayStyle(16.dp).gold())
                Text("AWARDS", style = slabStyle(34.dp))
            }

            awards.dongKing?.let { AwardCard("SR_Achievement_Dong_King", it, "${awards.dongKingTotal} DONGS") }
            awards.mvp?.let { AwardCard("SR_Achievement_MVP", it, "${awards.mvpDongs} DONGS + ${awards.mvpWins} WINS") }
            awards.rookieOfYear?.let { AwardCard("SR_Achievement_Rookie_Of_The_Year", it, "${awards.rookieOfYearDongs} DONGS") }

            if (awards.dongKing == null && awards.mvp == null && awards.rookieOfYear == null) {
                Text("NO AWARDS DATA", style = displayStyle(16.dp, SRColors.gold), modifier = Modifier.padding(top = 40.dp))
            }
        }
    }
}

/** Achievement medallion art with the winner and their numbers */
@Composable
private fun AwardCard(badge: String, playerName: String, stat: String) {
    Row(
        Modifier.fillMaxWidth().srPlate(glow = SRColors.purple).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SRArt(badge, Modifier.size(96.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FitText(playerName.uppercase(), displayStyle(22.dp))
            Text(stat, style = monoStyle(12.dp, SRColors.gold))
        }
    }
}
