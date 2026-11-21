package com.stickball.stats.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickball.stats.R
import com.stickball.stats.service.AdminService
import com.stickball.stats.service.StatsService
import kotlin.math.max
import kotlin.math.roundToInt

// MARK: - Services available to every screen
val LocalStats = staticCompositionLocalOf<StatsService> { error("StatsService not provided") }
val LocalAdmin = staticCompositionLocalOf<AdminService> { error("AdminService not provided") }

/** Height of the tab bar, so scrolling content can clear it */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

// MARK: - Colors
/** "Stickball Rich" gold, purple and black-marble palette */
object SRColors {
    val gold = Color(0.96f, 0.78f, 0.36f)
    val goldLight = Color(1.0f, 0.90f, 0.62f)
    val goldDark = Color(0.62f, 0.42f, 0.10f)
    val purple = Color(0.62f, 0.30f, 1.0f)
    val pink = Color(0.93f, 0.45f, 1.0f)
    val marble = Color(0.05f, 0.04f, 0.05f)
    val text = Color(0.99f, 0.97f, 0.92f)

    val goldGradient: Brush = Brush.verticalGradient(listOf(goldLight, gold, goldDark, gold))
    val silverGradient: Brush = Brush.verticalGradient(listOf(Color.White, Color(0.78f, 0.78f, 0.78f), Color.White))
}

/** Names from the original Tron theme; values follow the Stickball Rich palette */
object TronColors {
    val cyan = Color(0.96f, 0.78f, 0.36f) // gold
    val orange = Color(1.0f, 0.55f, 0.2f)
    val magenta = Color(0.78f, 0.35f, 1.0f) // violet
    val green = Color(0.3f, 0.9f, 0.45f)
    val yellow = Color(1.0f, 0.88f, 0.5f) // light gold
    val purple = Color(0.62f, 0.3f, 1.0f)

    val darkBackground = Color(0.03f, 0.02f, 0.03f)
    val cardBackground = Color(0.07f, 0.05f, 0.06f)
    val surfaceBackground = Color(0.11f, 0.08f, 0.09f)
    val gridLine = Color(0.62f, 0.45f, 0.15f)

    val primaryText = Color.White
    val secondaryText = Color(0.7f, 0.7f, 0.7f)
    val dimText = Color(0.5f, 0.5f, 0.5f)
}

// MARK: - Fonts
object SRFont {
    /** Condensed bold serif for names, dates and gold headings */
    val display = FontFamily(Font(R.font.abrilfatface_regular))
    /** Heavy slab serif for list titles (Players, Past Seasons) */
    val slab = FontFamily(Font(R.font.ultra_regular))
    /** Typewriter text for subtitles */
    val mono = FontFamily(Font(R.font.courierprime_bold))
}

/** Text sized in dp so the art-aligned layout matches the iPhone regardless of font scale */
@Composable
fun Dp.asSp(): TextUnit = with(LocalDensity.current) { this@asSp.toSp() }

@Composable
fun displayStyle(size: Dp, color: Color = SRColors.text) = tight(TextStyle(fontFamily = SRFont.display, fontSize = size.asSp(), color = color))

@Composable
fun slabStyle(size: Dp, color: Color = SRColors.text) = tight(TextStyle(fontFamily = SRFont.slab, fontSize = size.asSp(), color = color))

@Composable
fun monoStyle(size: Dp, color: Color = SRColors.text) = tight(TextStyle(fontFamily = SRFont.mono, fontSize = size.asSp(), color = color))

/** Line box hugging the glyphs, like SwiftUI text, so stacked lines sit where the iOS layout puts them */
private fun tight(style: TextStyle): TextStyle = style.copy(
    lineHeight = style.fontSize * 1.18f,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** iOS system monospaced font */
@Composable
fun systemMono(size: Dp, color: Color, weight: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = FontFamily.Monospace, fontWeight = weight, fontSize = size.asSp(), color = color)

/** Soft black drop shadow under light text */
@Composable
fun TextStyle.blackShadow(radius: Dp = 1.dp, y: Dp = 1.dp, alpha: Float = 1f): TextStyle {
    val d = LocalDensity.current
    return copy(shadow = Shadow(Color.Black.copy(alpha = alpha), Offset(0f, with(d) { y.toPx() }), with(d) { radius.toPx() * 2 }))
}

/** Gold serif heading text */
@Composable
fun TextStyle.gold(): TextStyle = copy(brush = SRColors.goldGradient).blackShadow(alpha = 0.8f)

/** Neon glow around Tron-era text */
@Composable
fun TextStyle.glow(color: Color, radius: Dp): TextStyle {
    if (radius <= 0.dp) return this
    val d = LocalDensity.current
    return copy(shadow = Shadow(color.copy(alpha = 0.8f), Offset.Zero, with(d) { radius.toPx() }))
}

/**
 * Single-line text that shrinks to fit its width, like SwiftUI's
 * `.lineLimit(1).minimumScaleFactor(...)`
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minScale: Float = 0.6f,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
) {
    val maxSize = style.fontSize
    val finalStyle = if (textAlign != null) style.copy(textAlign = textAlign) else style
    BasicText(
        text = text,
        modifier = modifier,
        style = finalStyle,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(
            minFontSize = maxSize * minScale,
            maxFontSize = maxSize,
            stepSize = (maxSize.value * 0.03f).coerceAtLeast(0.25f).sp,
        ),
    )
}

@Composable
fun SRTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SRColors.gold,
            onPrimary = Color.Black,
            secondary = SRColors.purple,
            background = Color.Black,
            surface = Color(0.09f, 0.07f, 0.08f),
            surfaceContainerHigh = Color(0.12f, 0.10f, 0.11f),
            onSurface = SRColors.text,
            error = TronColors.orange,
        ),
        content = content,
    )
}

// MARK: - Art loading
/**
 * Loads art by its iOS asset name ("SR_Player_Row_Empty" -> res/drawable-nodpi/sr_player_row_empty).
 * Decoded bitmaps are cached so rows sharing the same plate art decode it only once.
 */
object SRImages {
    private val ids = HashMap<String, Int>()
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 3).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    @SuppressLint("DiscouragedApi")
    fun load(context: Context, name: String): ImageBitmap {
        cache.get(name)?.let { return it }
        val id = synchronized(ids) {
            ids.getOrPut(name) { context.resources.getIdentifier(name.lowercase(), "drawable", context.packageName) }
        }
        require(id != 0) { "Missing art: $name" }
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, id, options).asImageBitmap()
        cache.put(name, bitmap)
        return bitmap
    }

    /** Warms the cache off the main thread (called during the splash) */
    fun preload(context: Context, names: List<String>) {
        names.forEach { runCatching { load(context, it) } }
    }

    val mainScreenArt = listOf(
        "SR_Stickball_Rich_Background_1290w", "SR_Stickball_Rich_Title", "SR_Season_9_Leaders",
        "SR_Player_Row_Empty", "SR_Rank_Badge_Blank_1500", "SR_Tab_Bar_Empty", "SR_TBT_Crossed_Dowels",
        "SR_Season", "SR_Season_Selected", "SR_Weekly", "SR_Weekly_Selected", "SR_Historic",
        "SR_Historic_Selected", "SR_Players", "SR_Players_Selected",
        "SR_Selected_Dongs", "SR_Unselected_Salamies", "SR_Unselected_Wins", "SR_Unselected_Drops",
        "SR_Unselected_Double_Plays",
    ) + (0..9).map { "SR_Diamond_Digit_$it" }
}

@Composable
fun srImage(name: String): ImageBitmap {
    val context = LocalContext.current.applicationContext
    return remember(name) { SRImages.load(context, name) }
}

private val ImageBitmap.ratio: Float get() = width.toFloat() / height

/** Art at its natural proportions (SwiftUI `.resizable().aspectRatio(contentMode: .fit)`) */
@Composable
fun SRArt(name: String, modifier: Modifier = Modifier, contentDescription: String? = null, alpha: Float = 1f) {
    val bitmap = srImage(name)
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier.aspectRatio(bitmap.ratio),
        contentScale = ContentScale.Fit,
        alpha = alpha,
        filterQuality = FilterQuality.High,
    )
}

// MARK: - Background
@Composable
fun SRBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = srImage("SR_Stickball_Rich_Background_1290w"),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
        )
    }
}

/** Stickball Rich scene, dimmed so forms and lists stay readable */
@Composable
fun TronGridBackground() {
    Box(Modifier.fillMaxSize()) {
        SRBackground()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    }
}

// MARK: - Screen Header
/** Stickball Rich title with a page banner tucked under it */
@Composable
fun SRHeader(banner: String, modifier: Modifier = Modifier, titleWidth: Float = 0.86f, bannerWidth: Float = 0.84f) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = maxWidth
        OverlapColumn(spacing = (-18).dp) {
            SRArt("SR_Stickball_Rich_Title", Modifier.width(width * titleWidth))
            SRArt(banner, Modifier.width(width * bannerWidth))
        }
    }
}

/** Crossed dowels and TTFB logo shown at the bottom of each list */
@Composable
fun SRFooterLogo() {
    SRArt("SR_TBT_Crossed_Dowels", Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp))
}

// MARK: - Layout helpers
/**
 * Vertical stack that allows negative spacing (SwiftUI `VStack(spacing: -18)`),
 * centering children horizontally. Later children draw on top.
 */
@Composable
fun OverlapColumn(spacing: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val gap = spacing.roundToPx()
        val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
        val height = max(0, placeables.sumOf { it.height } + gap * max(0, placeables.size - 1))
        layout(width, constraints.constrainHeight(height)) {
            var y = 0
            placeables.forEach {
                it.place((width - it.width) / 2, y)
                y += it.height + gap
            }
        }
    }
}

/**
 * Reports only a horizontal band of the art as this element's size (the visible plate),
 * drawing the rest (the transparent glow) outside it. Taps on the glow then fall through
 * to the overlapping neighbour instead of being caught by this row.
 */
fun Modifier.band(top: Float, bottom: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val height = (placeable.height * (bottom - top)).roundToInt()
    layout(placeable.width, height) {
        placeable.place(0, -(placeable.height * top).roundToInt())
    }
}

/**
 * Rows of plate art stacked with negative spacing so their transparent glows overlap,
 * like the iOS `LazyVStack(spacing: -22)`. With onClick, only the band between
 * bandTop and bandBottom of each row is tappable.
 */
@Composable
fun <T> ArtColumn(
    items: List<T>,
    spacing: Dp,
    modifier: Modifier = Modifier,
    bandTop: Float = 0f,
    bandBottom: Float = 1f,
    itemKey: (T) -> Any = { it as Any },
    onClick: ((T) -> Unit)? = null,
    itemContent: @Composable (item: T, pressed: Boolean) -> Unit,
) {
    Layout(
        content = {
            items.forEach { item ->
                androidx.compose.runtime.key(itemKey(item)) {
                    val source = remember { MutableInteractionSource() }
                    val pressed by source.collectIsPressedAsState()
                    val tap = if (onClick != null) {
                        Modifier.clickable(source, indication = null, role = Role.Button) { onClick(item) }
                    } else Modifier
                    Box(tap.band(bandTop, bandBottom)) { itemContent(item, pressed) }
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val fraction = bandBottom - bandTop
        val gap = spacing.toPx()
        var y = 0f
        val tops = placeables.map { p ->
            val full = p.height / fraction
            val top = y + full * bandTop
            y += full + gap
            top
        }
        val height = if (placeables.isEmpty()) 0 else (y - gap).roundToInt().coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.place(0, tops[i].roundToInt()) }
        }
    }
}

/** Positions content so its center sits at (x, y) inside the parent (SwiftUI `.position`) */
fun Modifier.centerAt(x: Dp, y: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        p.place(x.roundToPx() - p.width / 2, y.roundToPx() - p.height / 2)
    }
}

/** Tap without the Material ripple (SwiftUI `.buttonStyle(.plain)`) */
fun Modifier.plainClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onClick))

/** Paints content (icons) with the gold gradient */
fun Modifier.goldTint(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(SRColors.goldGradient, blendMode = BlendMode.SrcAtop)
        }
    }

/** Stretchy black-marble plate with a gold edge, for panels without dedicated art */
fun Modifier.srPlate(glow: Color = SRColors.gold, cornerRadius: Dp = 10.dp): Modifier = this
    .drawBehind {
        val r = cornerRadius.toPx()
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                color = SRColors.marble.copy(alpha = 0.92f).toArgb()
                setShadowLayer(6.dp.toPx(), 0f, 0f, glow.copy(alpha = 0.45f).toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }
    .border(2.dp, SRColors.goldGradient, RoundedCornerShape(cornerRadius))

/** Outer neon glow around a rounded shape (iOS `.shadow(color:radius:)`) */
fun Modifier.glowShadow(color: Color, radius: Dp, cornerRadius: Dp = 0.dp, fill: Color = Color.Transparent): Modifier =
    if (radius <= 0.dp) this else this.drawBehind {
        val r = cornerRadius.toPx()
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = (if (fill.alpha > 0f) fill else color.copy(alpha = 0.01f)).toArgb()
                setShadowLayer(radius.toPx(), 0f, 0f, color.toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }

// MARK: - Image Buttons
/**
 * Button drawn with "SR_<base>_Unselected" art that swaps to "SR_<base>_Selected" while pressed.
 * Content is laid over the art and gets the art's rendered size.
 */
@Composable
fun SRImageButton(
    base: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable BoxScope.(size: DpSize) -> Unit = {},
) {
    SRPressableArt(
        normal = "SR_${base}_Unselected",
        pressed = "SR_${base}_Selected",
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentDescription = contentDescription,
        content = content,
    )
}

@Composable
fun SRPressableArt(
    normal: String,
    pressed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable BoxScope.(size: DpSize) -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val normalArt = srImage(normal)
    val art = if (isPressed) srImage(pressed) else normalArt
    Box(
        modifier
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Content is laid out from the art's top-left (like SRArtPlate); center it with Modifier.align
        BoxWithConstraints(Modifier.aspectRatio(normalArt.ratio)) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
            content(DpSize(maxWidth, maxHeight))
        }
    }
}

/**
 * A piece of plate art at its natural proportions with content laid over it.
 * The content gets the plate's rendered size for proportional placement.
 */
@Composable
fun SRArtPlate(image: String, modifier: Modifier = Modifier, content: @Composable BoxScope.(size: DpSize) -> Unit) {
    val art = srImage(image)
    BoxWithConstraints(modifier.aspectRatio(art.ratio)) {
        Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        content(DpSize(maxWidth, maxHeight))
    }
}

// MARK: - Diamond Numbers
/** Builds a number out of the diamond digit images */
@Composable
fun DiamondNumber(value: Int, height: Dp, modifier: Modifier = Modifier) {
    val digits = max(0, value).toString()
    val digitWidth = height * (180f / 253f)
    Layout(
        content = {
            digits.forEach { digit ->
                Image(
                    srImage("SR_Diamond_Digit_$digit"), null,
                    Modifier.size(digitWidth, height),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                )
            }
        },
        modifier = modifier.semantics { contentDescription = "$value" },
    ) { measurables, _ ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val overlap = (height * 0.16f).roundToPx()
        val width = max(0, placeables.sumOf { it.width } - overlap * max(0, placeables.size - 1))
        layout(width, height.roundToPx()) {
            var x = 0
            placeables.forEach {
                it.place(x, 0)
                x += it.width - overlap
            }
        }
    }
}

/** Crown-and-laurel medallion with a diamond rank number */
@Composable
fun RankBadge(rank: Int, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        SRArt("SR_Rank_Badge_Blank_1500", Modifier.fillMaxSize())
        DiamondNumber(
            value = rank,
            height = size * (if (rank >= 10) 0.30f else 0.38f),
            modifier = Modifier.padding(top = size * 0.10f),
        )
    }
}
