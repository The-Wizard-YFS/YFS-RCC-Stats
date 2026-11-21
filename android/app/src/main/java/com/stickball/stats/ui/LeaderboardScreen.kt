package com.stickball.stats.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stickball.stats.model.YearlyStats
import kotlinx.coroutines.launch

/** Season leaderboard categories, with their labeled button art */
enum class LeaderCategory(val rawValue: String, private val artName: String) {
    DONGS("DONGS", "Dongs"),
    SALAMIES("SALAMIES", "Salamies"),
    WINS("WINS", "Wins"),
    DROPS("DROPS", "Drops"),
    DOUBLE_PLAYS("DBL PLY", "Double_Plays");

    val title: String get() = if (this == DOUBLE_PLAYS) "DOUBLE PLAYS" else rawValue
    val selectedImage: String get() = "SR_Selected_$artName"
    val unselectedImage: String get() = "SR_Unselected_$artName"

    fun value(stats: YearlyStats): Int = when (this) {
        DONGS -> stats.totalDongs
        DROPS -> stats.totalDrops
        DOUBLE_PLAYS -> stats.totalDoublePlays
        SALAMIES -> stats.totalSalamies
        WINS -> stats.totalWins
    }
}

@Composable
fun LeaderboardScreen() {
    val stats = LocalStats.current
    var selectedCategory by rememberSaveable { mutableStateOf(LeaderCategory.DONGS) }

    val sortedStats = stats.yearlyStats
        .filter { selectedCategory.value(it) > 0 }
        .sortedByDescending { selectedCategory.value(it) }

    ScreenScroll(spacing = 10.dp) {
        SRHeader(banner = "SR_Season_9_Leaders")

        // Category buttons (scroll sideways; the art is too wide to fit all five)
        CategoryArtRow(
            categories = LeaderCategory.entries,
            selected = selectedCategory,
            onSelect = { selectedCategory = it },
        ) { category, isSelected ->
            SRArt(
                if (isSelected) category.selectedImage else category.unselectedImage,
                Modifier.height(if (isSelected) 52.dp else 40.dp),
                contentDescription = category.title,
            )
        }

        // Column labels
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(CrownIcon, null, Modifier.size(13.dp).goldTint(), tint = Color.White)
            Text("PLAYER", style = displayStyle(15.dp).gold(), modifier = Modifier.padding(start = 40.dp))
            Spacer(Modifier.weight(1f))
            Text(selectedCategory.title, style = displayStyle(15.dp).gold())
        }

        if (sortedStats.isEmpty()) {
            SRArtPlate("SR_Player_Row_Empty", Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NO STATS YET", style = displayStyle(20.dp))
                    Text("Enter weekly stats to see the leaders", style = monoStyle(11.dp, SRColors.gold))
                }
            }
        } else {
            // Negative spacing absorbs the transparent glow padding in the row art
            ArtColumn(
                items = sortedStats.withIndex().toList(),
                spacing = (-22).dp,
                modifier = Modifier.padding(horizontal = 12.dp),
                itemKey = { it.value.id },
            ) { (index, row), _ ->
                SRRankRow(rank = index + 1, name = row.playerName, value = selectedCategory.value(row))
            }
        }

        SRFooterLogo()
    }
}

/** Horizontally scrolling category art that centers the tapped category */
@Composable
fun <T> CategoryArtRow(
    categories: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    art: @Composable (category: T, isSelected: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(categories) { index, category ->
            Box(
                Modifier.plainClickable {
                    onSelect(category)
                    scope.launch {
                        val info = listState.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
                        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                        listState.animateScrollBy((item.offset + item.size / 2 - viewportCenter).toFloat())
                    }
                },
            ) {
                art(category, category == selected)
            }
        }
    }
}
