package com.hazagou.spectra.remote

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Cor de destaque do controle (botão OK, abas, botões principais e o tom do fundo). */
data class RemoteAccent(val name: String, val color: Color, val glow: Color)

val REMOTE_ACCENTS = listOf(
    RemoteAccent("Roxo", Color(0xFF8B5CF6), Color(0xFF2A1F6B)),
    RemoteAccent("Azul", Color(0xFF3B82F6), Color(0xFF14285E)),
    RemoteAccent("Ciano", Color(0xFF06B6D4), Color(0xFF0B3A4A)),
    RemoteAccent("Verde", Color(0xFF22C55E), Color(0xFF0E3B25)),
    RemoteAccent("Laranja", Color(0xFFF97316), Color(0xFF4A2410)),
    RemoteAccent("Rosa", Color(0xFFEC4899), Color(0xFF4A1534)),
    RemoteAccent("Vermelho", Color(0xFFEF4444), Color(0xFF4A1414)),
    RemoteAccent("Dourado", Color(0xFFEAB308), Color(0xFF453A0C)),
    RemoteAccent("Grafite", Color(0xFF94A3B8), Color(0xFF1E2633)),
)

/** Estilo dos ícones dos botões. */
enum class RemoteIconStyle(val label: String, val sample: String) {
    LINHAS("Linhas", "Ícones finos e modernos"),
    CLASSICO("Clássico", "Símbolos de controle remoto"),
    EMOJI("Emoji", "Coloridos"),
}

/** Preferências de aparência do controle no celular. */
class RemoteLookStore(context: Context) {
    private val prefs = context.getSharedPreferences("spectra_remote_look", Context.MODE_PRIVATE)

    private val _accent = MutableStateFlow(prefs.getInt("accent", 0).coerceIn(0, REMOTE_ACCENTS.lastIndex))
    val accent: StateFlow<Int> = _accent
    private val _icons = MutableStateFlow(runCatching { RemoteIconStyle.valueOf(prefs.getString("icons", null) ?: "") }.getOrDefault(RemoteIconStyle.LINHAS))
    val icons: StateFlow<RemoteIconStyle> = _icons
    private val _haptic = MutableStateFlow(prefs.getBoolean("haptic", true))
    val haptic: StateFlow<Boolean> = _haptic

    fun setAccent(i: Int) { prefs.edit().putInt("accent", i).apply(); _accent.value = i }
    fun setIcons(s: RemoteIconStyle) { prefs.edit().putString("icons", s.name).apply(); _icons.value = s }
    fun setHaptic(v: Boolean) { prefs.edit().putBoolean("haptic", v).apply(); _haptic.value = v }
}

/** Ícone de um botão do controle no estilo escolhido. [key] é o mesmo nome do comando enviado à TV. */
@Composable
fun RemoteIcon(key: String, style: RemoteIconStyle, size: Dp = 26.dp, color: Color = Color.White) {
    when (style) {
        RemoteIconStyle.EMOJI -> Text(EMOJI[key] ?: "•", fontSize = (size.value * 0.92f).sp)
        RemoteIconStyle.CLASSICO -> Text(CLASSIC[key] ?: "•", color = color, fontSize = (size.value * 0.92f).sp)
        RemoteIconStyle.LINHAS -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) { drawLineIcon(key, color) }
        }
    }
}

private val CLASSIC = mapOf(
    "up" to "▲", "down" to "▼", "left" to "◀", "right" to "▶", "back" to "↩", "home" to "⌂", "menu" to "☰",
    "rew" to "⏪", "play" to "⏯", "ff" to "⏩", "prev" to "⏮", "next" to "⏭", "settings" to "⚙",
    "voldown" to "🔉", "mute" to "🔇", "volup" to "🔊", "apps" to "▦", "send" to "➤",
)

private val EMOJI = mapOf(
    "up" to "⬆️", "down" to "⬇️", "left" to "⬅️", "right" to "➡️", "back" to "🔙", "home" to "🏠", "menu" to "📋",
    "rew" to "⏪", "play" to "⏯️", "ff" to "⏩", "prev" to "⏮️", "next" to "⏭️", "settings" to "⚙️",
    "voldown" to "🔉", "mute" to "🔇", "volup" to "🔊", "apps" to "📱", "send" to "📨",
)

/** Ícones de traço desenhados à mão (sem biblioteca extra): ficam iguais em qualquer celular. */
private fun DrawScope.drawLineIcon(key: String, c: Color) {
    val w = size.width; val h = size.height
    val sw = w * 0.085f
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(vararg pts: Pair<Float, Float>, close: Boolean = false) = Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * w, y * h) else lineTo(x * w, y * h) }
        if (close) close()
    }
    fun line(vararg pts: Pair<Float, Float>) = drawPath(p(*pts), c, style = st)
    fun fill(vararg pts: Pair<Float, Float>) = drawPath(p(*pts, close = true), c)
    fun speaker() {
        drawPath(p(0.14f to 0.38f, 0.30f to 0.38f, 0.50f to 0.20f, 0.50f to 0.80f, 0.30f to 0.62f, 0.14f to 0.62f, close = true), c, style = st)
    }
    when (key) {
        "up" -> line(0.22f to 0.64f, 0.5f to 0.36f, 0.78f to 0.64f)
        "down" -> line(0.22f to 0.36f, 0.5f to 0.64f, 0.78f to 0.36f)
        "left" -> line(0.64f to 0.22f, 0.36f to 0.5f, 0.64f to 0.78f)
        "right" -> line(0.36f to 0.22f, 0.64f to 0.5f, 0.36f to 0.78f)
        "back" -> {
            line(0.38f to 0.24f, 0.18f to 0.42f, 0.38f to 0.60f)
            drawPath(Path().apply {
                moveTo(0.20f * w, 0.42f * h); lineTo(0.62f * w, 0.42f * h)
                cubicTo(0.92f * w, 0.42f * h, 0.92f * w, 0.82f * h, 0.62f * w, 0.82f * h); lineTo(0.42f * w, 0.82f * h)
            }, c, style = st)
        }
        "home" -> {
            line(0.14f to 0.48f, 0.5f to 0.16f, 0.86f to 0.48f)
            drawPath(p(0.24f to 0.40f, 0.24f to 0.84f, 0.76f to 0.84f, 0.76f to 0.40f), c, style = st)
            line(0.42f to 0.84f, 0.42f to 0.62f, 0.58f to 0.62f, 0.58f to 0.84f)
        }
        "menu" -> { line(0.18f to 0.28f, 0.82f to 0.28f); line(0.18f to 0.5f, 0.82f to 0.5f); line(0.18f to 0.72f, 0.82f to 0.72f) }
        "play" -> {
            fill(0.16f to 0.24f, 0.52f to 0.5f, 0.16f to 0.76f)
            line(0.66f to 0.26f, 0.66f to 0.74f); line(0.84f to 0.26f, 0.84f to 0.74f)
        }
        "rew" -> { fill(0.48f to 0.26f, 0.12f to 0.5f, 0.48f to 0.74f); fill(0.86f to 0.26f, 0.50f to 0.5f, 0.86f to 0.74f) }
        "ff" -> { fill(0.52f to 0.26f, 0.88f to 0.5f, 0.52f to 0.74f); fill(0.14f to 0.26f, 0.50f to 0.5f, 0.14f to 0.74f) }
        "prev" -> { line(0.22f to 0.24f, 0.22f to 0.76f); fill(0.80f to 0.24f, 0.32f to 0.5f, 0.80f to 0.76f) }
        "next" -> { line(0.78f to 0.24f, 0.78f to 0.76f); fill(0.20f to 0.24f, 0.68f to 0.5f, 0.20f to 0.76f) }
        "volup", "voldown", "mute" -> {
            speaker()
            when (key) {
                "mute" -> { line(0.64f to 0.38f, 0.86f to 0.62f); line(0.86f to 0.38f, 0.64f to 0.62f) }
                else -> {
                    drawArc(c, -45f, 90f, false, Offset(0.42f * w, 0.32f * h), Size(0.24f * w, 0.36f * h), style = st)
                    if (key == "volup") drawArc(c, -50f, 100f, false, Offset(0.42f * w, 0.18f * h), Size(0.44f * w, 0.64f * h), style = st)
                }
            }
        }
        "settings" -> {
            drawCircle(c, radius = 0.17f * w, style = st)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val cx = 0.5f * w; val cy = 0.5f * h
                drawLine(c, Offset(cx + (0.27f * w * Math.cos(a)).toFloat(), cy + (0.27f * h * Math.sin(a)).toFloat()),
                    Offset(cx + (0.38f * w * Math.cos(a)).toFloat(), cy + (0.38f * h * Math.sin(a)).toFloat()), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
            }
        }
        "apps" -> for (r in 0..1) for (col in 0..1) drawRoundRect(c, Offset((0.16f + col * 0.38f) * w, (0.16f + r * 0.38f) * h),
            Size(0.30f * w, 0.30f * h), androidx.compose.ui.geometry.CornerRadius(0.08f * w), style = st)
        "send" -> {
            drawPath(p(0.14f to 0.20f, 0.88f to 0.5f, 0.14f to 0.80f, 0.26f to 0.5f, close = true), c, style = st)
            line(0.26f to 0.5f, 0.56f to 0.5f)
        }
        else -> drawCircle(c, radius = 0.12f * w)
    }
}
