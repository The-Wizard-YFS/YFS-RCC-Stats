package com.stickball.stats.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stickball.stats.model.AchievementsCalculator
import com.stickball.stats.model.BadgeInfo
import com.stickball.stats.model.BadgeKind
import com.stickball.stats.model.CareerAdjustment
import com.stickball.stats.model.CareerStats

/** Player profile: name plaque, badges and career totals */
@Composable
fun PlayerCareerDetailView(stats: CareerStats, adjustment: CareerAdjustment?, dismiss: () -> Unit) {
    // adjustment is already applied to stats; it's used here for badges and championships
    val achievements = AchievementsCalculator.calculateAchievements(stats.playerName, stats, adjustment)

    Box(Modifier.fillMaxSize()) {
        SRBackground()
        ScreenScroll(spacing = 10.dp, includeTabBar = false, bottomPadding = 32.dp) {
            Spacer(Modifier.height(2.dp))
            SheetTopButton("Done_Button", "Done", dismiss, height = 48.dp)

            // Name and seasons on the profile plaque
            SRArtPlate("SR_Profile_Header_Plaque_Blank", Modifier.padding(horizontal = 10.dp)) { size ->
                FitText(
                    stats.playerName.uppercase(),
                    slabStyle(size.height * 0.15f).blackShadow(),
                    Modifier.centerAt(size.width * 0.5f, size.height * 0.545f).width(size.width * 0.74f),
                    minScale = 0.5f,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${stats.seasonsPlayed} SEASONS PLAYED",
                    style = monoStyle(size.height * 0.07f, SRColors.gold),
                    modifier = Modifier.centerAt(size.width * 0.5f, size.height * 0.835f),
                )
            }

            // Achievement badges (3 per row)
            if (achievements.hasBadges) {
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    achievements.badges.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { BadgeDisplayView(it, Modifier.weight(1f)) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // Career totals
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ProfileStatRow("DONGS", stats.totalDongs)
                ProfileStatRow("DOUBLE PLAYS", stats.totalDoublePlays)
                ProfileStatRow("SALAMIES", stats.totalSalamies)
                ProfileStatRow("DONG ROBBERIES", stats.totalDongRobs)
                if (achievements.yfslaChampCount > 0) {
                    ProfileStatRow("YFSLA CHAMPIONSHIPS", achievements.yfslaChampCount)
                }
            }
        }
    }
}

/** Stat label in the long plate and the diamond total in the end box */
@Composable
private fun ProfileStatRow(title: String, value: Int) {
    SRArtPlate("SR_Profile_Stat_Row_Blank") { size ->
        FitText(
            title,
            displayStyle(size.height * 0.26f).gold(),
            Modifier.centerAt(size.width * 0.39f, size.height * 0.52f).width(size.width * 0.62f),
        )
        DiamondNumber(value, size.height * 0.48f, Modifier.centerAt(size.width * 0.865f, size.height * 0.52f))
    }
}

/** Achievement medallion art for badges that have it */
private fun BadgeInfo.artName(): String? = when (name) {
    "Salami Club" -> "SR_Achievement_Salami_Club"
    "Century Club" -> "SR_Achievement_100_Club"
    "200 Club" -> "SR_Achievement_200_Club"
    "300 Club" -> "SR_Achievement_300_Club"
    "400 Club" -> "SR_Achievement_400_Club"
    "Coattails" -> "SR_Achievement_Coattails"
    "Galactics" -> "SR_Achievement_Galactics"
    "Wide Open" -> "SR_Achievement_Wide_Open"
    "MVP" -> "SR_Achievement_MVP"
    "Dong King" -> "SR_Achievement_Dong_King"
    "Rookie of the Year" -> "SR_Achievement_Rookie_Of_The_Year"
    else -> null
}

private val BadgeKind.fallbackIcon: ImageVector
    get() = when (this) {
        BadgeKind.SALAMI_CLUB -> Icons.Filled.Eco
        BadgeKind.CENTURY_CLUB -> Icons.Filled.SportsBaseball
        BadgeKind.MVP -> Icons.Filled.Star
        BadgeKind.DONG_KING -> CrownIcon
        BadgeKind.ROOKIE_OF_YEAR -> Icons.Filled.ChildCare
        BadgeKind.COATTAILS -> Icons.Filled.Checkroom
        BadgeKind.GALACTICS -> Icons.Filled.AutoAwesome
        BadgeKind.WIDE_OPEN -> Icons.Filled.MeetingRoom
    }

@Composable
private fun BadgeDisplayView(badge: BadgeInfo, modifier: Modifier = Modifier) {
    val art = badge.artName()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Multiplier sits on the medallion's top-right corner
        Box(Modifier.height(86.dp), contentAlignment = Alignment.Center) {
            if (art != null) {
                SRArt(art, Modifier.height(86.dp), contentDescription = badge.name)
            } else {
                // Gold ring medallion (500 Club has no art yet)
                Box(
                    Modifier
                        .size(70.dp)
                        .background(Brush.radialGradient(listOf(Color(badge.color).copy(alpha = 0.5f), Color.Black)), CircleShape)
                        .border(4.dp, SRColors.goldGradient, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(badge.kind.fallbackIcon, badge.name, Modifier.size(28.dp).goldTint(), tint = Color.White)
                }
            }
            // Multiplier badge (2X, 3X...)
            if (badge.multiplier > 1) {
                Text(
                    "${badge.multiplier}X",
                    style = displayStyle(12.dp, Color.Black),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(SRColors.goldGradient, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        // Art already carries the badge title
        if (art == null) {
            Text(
                badge.name.uppercase(),
                style = displayStyle(11.dp).gold(),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
