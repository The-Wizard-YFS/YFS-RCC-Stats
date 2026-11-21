package com.stickball.stats.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private enum class ImpactStrength { HEAVY, RIGID }

/**
 * Opening splash: the Dong Country Ledger 5000 headline slams onto the screen, then the
 * Season 9 banner. Each hit shakes the screen, flashes, throws a gold shockwave and thumps
 * the haptics. Tap to skip.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    val headline = remember { Animatable(0f) } // 0 = flying in, 1 = landed
    val subhead = remember { Animatable(0f) }
    val shakes = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val ringScale = remember { Animatable(0.3f) }
    val ringOpacity = remember { Animatable(0f) }
    var ringY by remember { mutableFloatStateOf(0.40f) }
    var finished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun finish() {
        if (finished) return
        finished = true
        onFinished()
    }

    fun CoroutineScope.impact(y: Float, strength: ImpactStrength) {
        thump(context, strength)
        launch { shakes.animateTo(floor(shakes.value) + 1f, tween(400, easing = LinearEasing)) }
        launch {
            flash.snapTo(0.55f)
            flash.animateTo(0f, tween(350, easing = LinearOutSlowInEasing))
        }
        ringY = y
        launch {
            ringScale.snapTo(0.3f)
            ringScale.animateTo(1.8f, tween(600, easing = LinearOutSlowInEasing))
        }
        launch {
            ringOpacity.snapTo(0.9f)
            ringOpacity.animateTo(0f, tween(600, easing = LinearOutSlowInEasing))
        }
    }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            launch { headline.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
            launch {
                delay(400)
                subhead.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            }
            delay(2200)
            finish()
            return@LaunchedEffect
        }

        delay(350)
        launch { headline.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = stiffnessFor(0.26f))) }
        delay(160)
        impact(0.40f, ImpactStrength.HEAVY)

        delay(550)
        launch { subhead.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = stiffnessFor(0.24f))) }
        delay(150)
        impact(0.56f, ImpactStrength.RIGID)

        delay(1600)
        finish()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .plainClickable { scope.launch { finish() } },
    ) {
        val w = maxWidth
        val h = maxHeight
        val density = LocalDensity.current
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Each whole step of `shakes` is one decaying shake
                    val progress = shakes.value - floor(shakes.value)
                    if (progress > 0f) {
                        val decay = 1f - progress
                        translationX = with(density) { (14 * decay * sin(progress * PI * 8)).toFloat().dp.toPx() }
                        translationY = with(density) { (9 * decay * cos(progress * PI * 10)).toFloat().dp.toPx() }
                    }
                },
        ) {
            Image(
                srImage("SR_Splash_Background"), null,
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }, // overscan for the shake
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )

            // Shockwave from the impact point
            Canvas(
                Modifier
                    .size(w * 0.8f)
                    .centerAt(w / 2, h * ringY)
                    .graphicsLayer {
                        scaleX = ringScale.value
                        scaleY = ringScale.value
                        alpha = ringOpacity.value
                    }
                    .blur(1.5.dp),
            ) {
                val stroke = 6.dp.toPx()
                drawCircle(SRColors.goldGradient, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            }

            OverlapColumn(spacing = -(w * 0.04f), modifier = Modifier.centerAt(w / 2, h * 0.46f)) {
                SlamIn("SR_Splash_Headline", w * 0.94f, headline.value, startScale = 3.2f, startRotation = -8f)
                SlamIn("SR_Splash_Season_9", w * 0.9f, subhead.value, startScale = 2.6f, startRotation = 6f)
            }

            Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
        }
    }
}

/** Starts huge, tilted and invisible, then lands at full size */
@Composable
private fun SlamIn(art: String, width: Dp, landed: Float, startScale: Float, startRotation: Float) {
    val bitmap = srImage(art)
    val landedClamped = landed.coerceIn(0f, 1f)
    Box {
        // Drop shadow (blur needs Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Image(
                bitmap, null,
                Modifier
                    .width(width)
                    .graphicsLayer {
                        val s = startScale + (1f - startScale) * landed
                        scaleX = s; scaleY = s
                        rotationZ = startRotation * (1f - landed)
                        alpha = 0.7f * landedClamped
                        translationY = (30f - 24f * landedClamped) * density
                    }
                    .blur((30f - 20f * landedClamped).dp),
                colorFilter = ColorFilter.tint(Color.Black),
                contentScale = ContentScale.Fit,
            )
        }
        Image(
            bitmap, null,
            Modifier
                .width(width)
                .graphicsLayer {
                    val s = startScale + (1f - startScale) * landed
                    scaleX = s; scaleY = s
                    rotationZ = startRotation * (1f - landed)
                    alpha = landedClamped
                }
                .blur((6f * (1f - landedClamped)).dp),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
        )
    }
}

/** SwiftUI spring(response:) expressed as a Compose stiffness */
private fun stiffnessFor(response: Float): Float {
    val omega = 2 * PI / response
    return (omega * omega).toFloat()
}

/** Haptic thump when a graphic lands */
private fun thump(context: Context, strength: ImpactStrength) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator == null || !vibrator.hasVibrator()) return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = if (strength == ImpactStrength.HEAVY) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
            vibrator.vibrate(VibrationEffect.createPredefined(effect))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(if (strength == ImpactStrength.HEAVY) 40 else 25, 255))
        }
    }
}
