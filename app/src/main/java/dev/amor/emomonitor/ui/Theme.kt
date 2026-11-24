package dev.amor.emomonitor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.amor.emomonitor.R
import dev.amor.emomonitor.data.AirLevel

/** Manrope 是可变字体，必须显式给 wght 轴设值，否则 Android 只会取默认 400 实例。 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    manrope(300),
    manrope(400),
    manrope(500),
    manrope(600),
    manrope(700),
    manrope(800),
)

private val Ink = Color(0xFF1D1D1F)
private val InkDark = Color(0xFFEDEDEF)

@Immutable
data class EmoColors(
    val dark: Boolean,
    val paper: Color,
    val ink: Color,
    val inkSoft: Color,
    val sheet: Color,
    val field: Color,
    val hairline: Color,
    val glassFill: Color,
    val glassStroke: Color,
    val danger: Color,
)

private val LightPalette = EmoColors(
    dark = false,
    paper = Color(0xFFFAFAFC),
    ink = Ink,
    inkSoft = Ink.copy(alpha = 0.62f),
    sheet = Color.White,
    field = Color(0xFFF2F2F5),
    hairline = Ink.copy(alpha = 0.08f),
    glassFill = Color.White.copy(alpha = 0.55f),
    glassStroke = Color.White.copy(alpha = 0.70f),
    danger = Color(0xFFD64545),
)

private val DarkPalette = EmoColors(
    dark = true,
    paper = Color(0xFF0F0F11),
    ink = InkDark,
    inkSoft = InkDark.copy(alpha = 0.62f),
    sheet = Color(0xFF1A1A1D),
    field = Color(0xFF26262A),
    hairline = Color.White.copy(alpha = 0.10f),
    glassFill = Color.White.copy(alpha = 0.08f),
    glassStroke = Color.White.copy(alpha = 0.12f),
    danger = Color(0xFFD64545),
)

val LocalEmoColors = staticCompositionLocalOf { LightPalette }

object Emo {
    val colors: EmoColors
        @Composable @ReadOnlyComposable get() = LocalEmoColors.current
}

/** HAZARD 的 #2B2B2B 在深色面板上不可见，深色模式统一换成 ink。 */
fun EmoColors.levelColor(level: AirLevel?): Color = when {
    level == null -> inkSoft
    dark && level == AirLevel.HAZARD -> ink
    else -> level.color
}

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    background = LightPalette.paper,
    onBackground = Ink,
    surface = LightPalette.sheet,
    onSurface = Ink,
    surfaceVariant = LightPalette.field,
    onSurfaceVariant = LightPalette.inkSoft,
    outline = LightPalette.hairline,
    error = LightPalette.danger,
)

private val DarkColors = darkColorScheme(
    primary = InkDark,
    onPrimary = Color(0xFF0F0F11),
    background = DarkPalette.paper,
    onBackground = InkDark,
    surface = DarkPalette.sheet,
    onSurface = InkDark,
    surfaceVariant = DarkPalette.field,
    onSurfaceVariant = DarkPalette.inkSoft,
    outline = DarkPalette.hairline,
    error = DarkPalette.danger,
)

/** 所有数字都用 tnum：大号读数逐位滚动时，非等宽数字会让整串左右抖。 */
private const val Tabular = "tnum"

/** 数字统一用 Manrope + tnum，避免每个调用点各写一遍。 */
fun numStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Manrope,
    fontSize = size,
    fontWeight = weight,
    fontFeatureSettings = Tabular,
)

private fun style(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Int = (size * 1.35f).toInt(),
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = Tabular,
)

private fun typography() = Typography(
    displayLarge = style(96, FontWeight.Medium, 104, -0.04f),
    displayMedium = style(44, FontWeight.SemiBold),
    displaySmall = style(30, FontWeight.SemiBold),
    headlineLarge = style(30, FontWeight.SemiBold, 38),
    headlineMedium = style(28, FontWeight.SemiBold, 36),
    headlineSmall = style(24, FontWeight.SemiBold, 32),
    titleLarge = style(22, FontWeight.SemiBold, 30),
    titleMedium = style(16, FontWeight.Medium, 24),
    titleSmall = style(15, FontWeight.Medium, 22),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(15, FontWeight.Normal, 23),
    bodySmall = style(13, FontWeight.Normal, 20),
    labelLarge = style(15, FontWeight.Medium, 20),
    labelMedium = style(13, FontWeight.Medium, 18, 0.02f),
    labelSmall = style(13, FontWeight.Medium, 16, 0.02f),
)

@Composable
fun EmoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalEmoColors provides palette) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = typography(),
            content = content,
        )
    }
}
