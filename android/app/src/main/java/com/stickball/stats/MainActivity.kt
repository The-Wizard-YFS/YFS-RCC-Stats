package com.stickball.stats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import com.stickball.stats.service.AdminService
import com.stickball.stats.service.StatsService
import com.stickball.stats.ui.LeaderboardScreen
import com.stickball.stats.ui.LocalAdmin
import com.stickball.stats.ui.LocalBottomBarInset
import com.stickball.stats.ui.LocalStats
import com.stickball.stats.ui.PastSeasonsScreen
import com.stickball.stats.ui.PlayersScreen
import com.stickball.stats.ui.SRBackground
import com.stickball.stats.ui.SRColors
import com.stickball.stats.ui.SRImages
import com.stickball.stats.ui.SRTab
import com.stickball.stats.ui.SRTabBar
import com.stickball.stats.ui.SRTheme
import com.stickball.stats.ui.SplashScreen
import com.stickball.stats.ui.WeeklyStatsScreen
import com.stickball.stats.ui.displayStyle
import com.stickball.stats.ui.monoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        // Decode the main screen's art while the splash plays
        lifecycleScope.launch(Dispatchers.IO) { SRImages.preload(applicationContext, SRImages.mainScreenArt) }

        val firebaseReady = FirebaseApp.getApps(this).isNotEmpty()
        setContent {
            SRTheme {
                if (firebaseReady) {
                    val stats: StatsService = viewModel()
                    CompositionLocalProvider(LocalStats provides stats, LocalAdmin provides AdminService.shared) {
                        DongCountryLedgerApp()
                    }
                } else {
                    FirebaseSetupNotice()
                }
            }
        }
    }
}

/** Splash, then the four tabs */
@Composable
fun DongCountryLedgerApp() {
    var showingSplash by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = showingSplash, animationSpec = tween(300), label = "splash") { splash ->
            if (splash) SplashScreen(onFinished = { showingSplash = false }) else MainContent()
        }
    }
}

@Composable
private fun MainContent() {
    val stats = LocalStats.current
    var selectedTab by rememberSaveable { mutableStateOf(SRTab.SEASON) }
    var barHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    // Each tab keeps its scroll position while another tab is shown
    val tabStates = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize()) {
        SRBackground()
        CompositionLocalProvider(LocalBottomBarInset provides barHeight) {
            tabStates.SaveableStateProvider(selectedTab.name) {
                when (selectedTab) {
                    SRTab.SEASON -> LeaderboardScreen()
                    SRTab.WEEKLY -> WeeklyStatsScreen()
                    SRTab.HISTORIC -> PastSeasonsScreen()
                    SRTab.PLAYERS -> PlayersScreen()
                }
            }
        }
        if (stats.isTestMode) {
            TestModeBanner(Modifier.align(Alignment.TopCenter).statusBarsPadding())
        }
        SRTabBar(
            selection = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
        )
    }
}

/** Always-visible reminder that stats entered now are test data */
@Composable
private fun TestModeBanner(modifier: Modifier = Modifier) {
    Text(
        "TEST MODE · NOT LEAGUE DATA",
        style = displayStyle(12.dp, Color.Black),
        modifier = modifier
            .shadow(4.dp, CircleShape)
            .background(SRColors.pink, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Shown when the build has no google-services.json yet */
@Composable
private fun FirebaseSetupNotice() {
    Box(Modifier.fillMaxSize()) {
        SRBackground()
        Column(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("FIREBASE NOT SET UP", style = displayStyle(22.dp), textAlign = TextAlign.Center)
            Text(
                "Add google-services.json from the Firebase console to android/app/ and rebuild. See android/README.md.",
                style = monoStyle(13.dp, SRColors.gold),
                textAlign = TextAlign.Center,
            )
        }
    }
}
