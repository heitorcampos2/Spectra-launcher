package com.hazagou.spectra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.hazagou.spectra.data.FontChoice
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.data.PerformanceMode

/** Paleta "Ghost" — índigo profundo, magenta e violeta (inspirada no wallpaper de referência). */
object Spectra {
    val Indigo = Color(0xFF17154F)
    val Night = Color(0xFF0E0B2A)
    val Magenta = Color(0xFFE0135A)
    val Violet = Color(0xFF9C5AA8)
    val Lilac = Color(0xFFC9A6E8)
    val Surface = Color(0xCC1C1846)
    val SurfaceHigh = Color(0xE6282262)
    val TextPrimary = Color(0xFFF5F2FF)
    val TextSecondary = Color(0xFFB9B3DA)

    /** Atalhos rápidos (linha única). */
    /** Atalhos rápidos (16) nas configurações. */
    val AccentPresets = listOf(
        0xFFE0135AL, 0xFFFF4081L, 0xFFD500F9L, 0xFF9C5AA8L, 0xFF7C4DFFL, 0xFF3D5AFEL, 0xFF2979FFL, 0xFF00B0FFL,
        0xFF00E5FFL, 0xFF1DE9B6L, 0xFF00E676L, 0xFF76FF03L, 0xFFFFD600L, 0xFFFFC400L, 0xFFFF6D00L, 0xFFFFFFFFL,
    )

    /** Grade completa de 64 cores (8 × 8): vivas, neon, pastéis, profundas, terrosas e neutras. */
    val ColorGrid: List<Long> = listOf(
        // vivas
        0xFFFF3D00L, 0xFFFF5722L, 0xFFFF9100L, 0xFFFFB300L, 0xFFFFD600L, 0xFFEEFF41L, 0xFFB2FF59L, 0xFF76FF03L,
        0xFF64DD17L, 0xFF00E676L, 0xFF1DE9B6L, 0xFF00BFA5L, 0xFF00E5FFL, 0xFF18FFFFL, 0xFF00B0FFL, 0xFF40C4FFL,
        0xFF2979FFL, 0xFF304FFEL, 0xFF3D5AFEL, 0xFF651FFFL, 0xFF7C4DFFL, 0xFFAA00FFL, 0xFFD500F9L, 0xFFE040FBL,
        0xFFF50057L, 0xFFFF4081L, 0xFFE0135AL, 0xFFFF1744L, 0xFF9C5AA8L, 0xFFC9A6E8L, 0xFFBDBDBDL, 0xFFFFFFFFL,
        // neon
        0xFF39FF14L, 0xFF00FFC6L, 0xFF00F0FFL, 0xFF4D4DFFL, 0xFFBC13FEL, 0xFFFF00E6L, 0xFFFF2079L, 0xFFFFF01FL,
        // pastéis
        0xFFFFB3BAL, 0xFFFFDFBAL, 0xFFFFFFBAL, 0xFFBAFFC9L, 0xFFBAE1FFL, 0xFFD7BAFFL, 0xFFF4C2E8L, 0xFFC5F0EEL,
        // profundas
        0xFFB71C1CL, 0xFFE65100L, 0xFFF9A825L, 0xFF2E7D32L, 0xFF00695CL, 0xFF01579BL, 0xFF283593L, 0xFF6A1B9AL,
        // terrosas e metálicas
        0xFF8D6E63L, 0xFFA1887FL, 0xFFD7CCC8L, 0xFFC0A062L, 0xFFD4AF37L, 0xFFB87333L, 0xFF90A4AEL, 0xFF607D8BL,
    )

    val BackgroundGrid: List<Long> = listOf(
        0xFF000000L, 0xFF0E0B2AL, 0xFF17154FL, 0xFF1A1033L, 0xFF2A1665L, 0xFF3D0F3AL, 0xFF5B1B6EL, 0xFF4A0E2EL,
        0xFF3A0A0AL, 0xFF4E2A06L, 0xFF3B3A0AL, 0xFF0D2B12L, 0xFF06352FL, 0xFF06303DL, 0xFF0A1E3DL, 0xFF101828L,
        0xFF1E1E1EL, 0xFF2B2B2BL, 0xFF263238L, 0xFF37474FL, 0xFF4E342EL, 0xFF311B92L, 0xFF880E4FL, 0xFFB71C1CL,
    )

    /** Temas prontos: cor de destaque + cores de fundo + tom dos painéis de uma vez. */
    val ColorThemes = listOf(
        ColorTheme("Spectra", "🐉", 0xFFE0135AL, 0xFF17154FL, 0xFF5B1B6EL, com.hazagou.spectra.data.SurfaceTone.INDIGO),
        ColorTheme("Neon", "⚡", 0xFF00F0FFL, 0xFF05010FL, 0xFF2A0845L, com.hazagou.spectra.data.SurfaceTone.AMOLED),
        ColorTheme("Oceano", "🌊", 0xFF1DE9B6L, 0xFF06303DL, 0xFF0A1E3DL, com.hazagou.spectra.data.SurfaceTone.OCEAN),
        ColorTheme("Pôr do sol", "🌅", 0xFFFF9100L, 0xFF3A0A0AL, 0xFF6A1B4DL, com.hazagou.spectra.data.SurfaceTone.SUNSET),
        ColorTheme("Floresta", "🌿", 0xFF76FF03L, 0xFF0D2B12L, 0xFF06352FL, com.hazagou.spectra.data.SurfaceTone.FOREST),
        ColorTheme("Aurora", "🌌", 0xFFB2FF59L, 0xFF0A1E3DL, 0xFF311B92L, com.hazagou.spectra.data.SurfaceTone.NAVY),
        ColorTheme("Sakura", "🌸", 0xFFF4C2E8L, 0xFF3D0F3AL, 0xFF880E4FL, com.hazagou.spectra.data.SurfaceTone.ROSE),
        ColorTheme("Ouro", "👑", 0xFFD4AF37L, 0xFF1A1206L, 0xFF4E342EL, com.hazagou.spectra.data.SurfaceTone.COFFEE),
        ColorTheme("Gelo", "❄️", 0xFFBAE1FFL, 0xFF101828L, 0xFF263238L, com.hazagou.spectra.data.SurfaceTone.SLATE),
        ColorTheme("AMOLED", "🌑", 0xFFFFFFFFL, 0xFF000000L, 0xFF000000L, com.hazagou.spectra.data.SurfaceTone.AMOLED),
        ColorTheme("Vinho", "🍷", 0xFFFF4081L, 0xFF3A0A0AL, 0xFF4A0E2EL, com.hazagou.spectra.data.SurfaceTone.WINE),
        ColorTheme("Roxo real", "💜", 0xFFD500F9L, 0xFF1A1033L, 0xFF2A1665L, com.hazagou.spectra.data.SurfaceTone.PURPLE),
    )

    fun adjust(c: Color, lum: Int): Color = when {
        lum > 0 -> lerp(c, Color.White, lum / 100f)
        lum < 0 -> lerp(c, Color.Black, -lum / 100f)
        else -> c
    }
}

data class UiConfig(
    val accent: Color,
    val corner: Int,
    val focusScale: Float,
    val animations: Boolean,
    val highQualityImages: Boolean,
    val showLabels: Boolean,
    val surface: Color = Spectra.Surface,
    val surfaceHigh: Color = Spectra.SurfaceHigh,
)

val LocalUi = staticCompositionLocalOf {
    UiConfig(Spectra.Magenta, 14, 1.1f, animations = true, highQualityImages = false, showLabels = true)
}

fun LauncherSettings.toUiConfig(dynamicAccent: Color?): UiConfig {
    val base = if (autoAccent && dynamicAccent != null) dynamicAccent else Color(accent)
    val acc = Spectra.adjust(base, accentLum).copy(alpha = accentAlpha)
    return UiConfig(
        accent = acc,
        corner = corner,
        focusScale = when (cardSize) { 0 -> 1.12f; 2, 3 -> 1.07f; else -> 1.1f },
        animations = performance != PerformanceMode.MAX_PERFORMANCE,
        highQualityImages = performance == PerformanceMode.MAX_QUALITY,
        showLabels = showLabels,
        surface = if (menuColor != 0L) Color(menuColor).copy(alpha = 0.80f) else Color(surfaceTone.argb),
        surfaceHigh = if (menuColor != 0L) adjust(Color(menuColor), 8).copy(alpha = 0.92f) else Color(surfaceTone.high),
    )
}

private fun family(f: FontChoice): FontFamily = when (f) {
    FontChoice.DEFAULT, FontChoice.SANS, FontChoice.SANS_THIN, FontChoice.SANS_LIGHT, FontChoice.SANS_MEDIUM, FontChoice.CASUAL -> FontFamily.SansSerif
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONO -> FontFamily.Monospace
    FontChoice.CURSIVE -> FontFamily.Cursive
}

private fun typography(s: LauncherSettings): Typography {
    val fam = family(s.font)
    val style = if (s.fontItalic) FontStyle.Italic else FontStyle.Normal
    fun w(base: FontWeight): FontWeight = when {
        s.fontBold -> if (base.weight >= 600) FontWeight.Black else FontWeight.Bold
        s.font == FontChoice.SANS_THIN -> FontWeight.Thin
        s.font == FontChoice.SANS_LIGHT -> FontWeight.Light
        s.font == FontChoice.SANS_MEDIUM -> if (base.weight < 500) FontWeight.Medium else base
        else -> base
    }
    fun ts(weight: FontWeight, size: TextUnit, spacing: TextUnit = 0.sp) =
        TextStyle(fontFamily = fam, fontWeight = w(weight), fontStyle = style, fontSize = size, letterSpacing = spacing)
    return Typography(
        displaySmall = ts(FontWeight.Black, 34.sp, 0.5.sp),
        headlineSmall = ts(FontWeight.Bold, 22.sp),
        titleMedium = ts(FontWeight.SemiBold, 17.sp),
        titleSmall = ts(FontWeight.SemiBold, 14.sp),
        bodyLarge = ts(FontWeight.Normal, 15.sp),
        bodyMedium = ts(FontWeight.Normal, 14.sp),
        bodySmall = ts(FontWeight.Normal, 12.sp),
        labelMedium = ts(FontWeight.Medium, 12.sp, 0.6.sp),
    )
}

@Composable
fun SpectraTheme(ui: UiConfig, settings: LauncherSettings? = null, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = ui.accent,
        onPrimary = Color.White,
        secondary = Spectra.Violet,
        background = Spectra.Night,
        surface = ui.surface,
        onSurface = Spectra.TextPrimary,
        onBackground = Spectra.TextPrimary,
        surfaceVariant = ui.surfaceHigh,
        onSurfaceVariant = Spectra.TextSecondary,
    )
    val density = LocalDensity.current
    val scaled = if (settings != null && settings.fontScale != 1f) Density(density.density, density.fontScale * settings.fontScale) else density
    CompositionLocalProvider(LocalUi provides ui, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = if (settings != null) typography(settings) else Typography(), content = content)
    }
}

/** Tema de cores pronto (Configurações › Cores e fundo › Temas prontos). */
data class ColorTheme(
    val name: String,
    val emoji: String,
    val accent: Long,
    val bg1: Long,
    val bg2: Long,
    val tone: com.hazagou.spectra.data.SurfaceTone,
)
