package com.stickball.stats.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stickball.stats.model.GameField
import com.stickball.stats.model.WeeklyStats
import kotlinx.coroutines.launch

// MARK: - Icons (stand-ins for the SF Symbols the iOS app uses)
/** SF Symbol "crown.fill" */
val CrownIcon: ImageVector by lazy {
    ImageVector.Builder("Crown", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(2f, 7f); lineTo(7f, 11.5f); lineTo(12f, 4f); lineTo(17f, 11.5f); lineTo(22f, 7f)
            lineTo(20f, 18f); lineTo(4f, 18f); close()
            moveTo(4f, 19.5f); lineTo(20f, 19.5f); lineTo(20f, 21.5f); lineTo(4f, 21.5f); close()
        }
    }.build()
}

val GameField.icon: ImageVector
    get() = when (this) {
        GameField.THE_DAM -> Icons.Filled.Water
        GameField.THE_SPREADING_GROUNDS -> Icons.Filled.Eco
        GameField.THE_AIRFIELD -> Icons.Filled.Flight
        GameField.UNCLE_KIMMYS_PLAYHOUSE -> Icons.Filled.NightsStay
    }

// MARK: - Screen scaffolding
/** Scrolling page that starts below the status bar and clears the tab bar */
@Composable
fun ScreenScroll(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    bottomPadding: Dp = 12.dp,
    includeTabBar: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tabBar = if (includeTabBar) LocalBottomBarInset.current else 0.dp
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        content()
        Spacer(Modifier.height(bottomPadding + tabBar))
        if (!includeTabBar) Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * Full-height sheet that slides up over the screen (SwiftUI `.sheet`).
 * Content gets a dismiss action that animates the sheet away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SRSheet(
    onDismissRequest: () -> Unit,
    fullHeight: Boolean = true,
    content: @Composable BoxScope.(dismiss: () -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        scope.launch { state.hide() }.invokeOnCompletion { if (!state.isVisible) onDismissRequest() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = state,
        dragHandle = null,
        containerColor = Color.Black,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Box(if (fullHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            content(dismiss)
        }
    }
}

/** Alert with a single OK button (SwiftUI `.alert`) */
@Composable
fun SRAlert(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(title) },
        text = { Text(message) },
    )
}

/** Destructive confirmation (SwiftUI `.confirmationDialog`) */
@Composable
fun SRConfirm(title: String, message: String?, destructiveLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(destructiveLabel, color = Color(1f, 0.35f, 0.35f)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = message?.let { { Text(it) } },
    )
}

@Composable
fun SRProgress(color: Color = SRColors.gold, size: Dp = 22.dp) {
    CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(size))
}

/** Label on the left, switch on the right (SwiftUI `Toggle`) */
@Composable
fun SRToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, tint: Color, modifier: Modifier = Modifier, label: @Composable ColumnScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = tint,
                checkedThumbColor = Color.White,
                checkedBorderColor = tint,
                uncheckedTrackColor = Color(0.2f, 0.2f, 0.22f),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color(0.2f, 0.2f, 0.22f),
            ),
        )
    }
}

/** Top row of a sheet with the gold Cancel (or Done) art button */
@Composable
fun SheetTopButton(base: String, label: String, onClick: () -> Unit, width: Dp = 104.dp, height: Dp = 44.dp) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SRImageButton(base, onClick, Modifier.size(width, height), contentDescription = label)
        Spacer(Modifier.weight(1f))
    }
}

// MARK: - Leaderboard row
/** Rank medallion, player name and diamond total */
@Composable
fun SRRankRow(rank: Int, name: String, value: Int) {
    SRArtPlate("SR_Player_Row_Empty") { size ->
        Row(
            Modifier.fillMaxSize().padding(start = size.width * 0.04f, end = size.width * 0.07f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RankBadge(rank, size.height * 0.92f)
            FitText(
                name.uppercase(),
                displayStyle(size.height * 0.34f).blackShadow(),
                Modifier.weight(1f),
            )
            DiamondNumber(value, size.height * 0.56f)
        }
    }
}

// MARK: - Tab Bar
enum class SRTab(val title: String, val icon: String) {
    SEASON("SEASON", "SR_Season"),
    WEEKLY("WEEKLY", "SR_Weekly"),
    HISTORIC("HISTORIC", "SR_Historic"),
    PLAYERS("PLAYERS", "SR_Players"),
}

@Composable
fun SRTabBar(selection: SRTab, onSelect: (SRTab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // Fades content out behind the bar's ornaments and fills the gesture area
                val top = -24.dp.toPx()
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.85f),
                        1f to Color.Black,
                        startY = top,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, size.height - top),
                )
            },
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            SRArt("SR_Tab_Bar_Empty", Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                SRTab.entries.forEach { tab ->
                    val isSelected = tab == selection
                    Column(
                        Modifier.weight(1f).plainClickable { onSelect(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SRArt(if (isSelected) tab.icon + "_Selected" else tab.icon, Modifier.size(46.dp), contentDescription = tab.title)
                        Text(
                            tab.title,
                            style = displayStyle(12.dp, if (isSelected) SRColors.text else SRColors.gold),
                            modifier = Modifier.offset(y = (-6).dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
