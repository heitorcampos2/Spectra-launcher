package com.hazagou.spectra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazagou.spectra.data.AnimalIcon
import com.hazagou.spectra.ui.theme.Spectra

/** Ícone de Wi-Fi com intensidade (0..3). level = -1 → sem conexão; ethernet → ícone de cabo. */
@Composable
fun WifiIcon(level: Int, ethernet: Boolean, noInternet: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val dim = color.copy(alpha = 0.28f)
        if (ethernet) {
            val s = w / 24f
            drawRect(color, Offset(7 * s, 3 * s), Size(10 * s, 7 * s))
            drawRect(color, Offset(11 * s, 10 * s), Size(2 * s, 5 * s))
            drawRect(color, Offset(3 * s, 15 * s), Size(18 * s, 2 * s))
            drawRect(color, Offset(3 * s, 15 * s), Size(2 * s, 6 * s))
            drawRect(color, Offset(19 * s, 15 * s), Size(2 * s, 6 * s))
            drawRect(color, Offset(11 * s, 15 * s), Size(2 * s, 6 * s))
            return@Canvas
        }
        val center = Offset(w / 2f, h * 0.88f)
        val stroke = w * 0.1f
        for (i in 1..3) {
            val r = h * (0.25f * i)
            drawArc(
                color = if (level >= i) color else dim,
                startAngle = 225f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        drawCircle(if (level >= 0) color else dim, radius = stroke * 0.8f, center = center)
        if (level < 0 || noInternet) {
            drawLine(Spectra.Magenta, Offset(w * 0.15f, h * 0.15f), Offset(w * 0.85f, h * 0.85f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/** Ícone de bateria simples com percentual. */
@Composable
fun BatteryIcon(percent: Int, charging: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val bodyW = w * 0.86f
        drawRoundRect(color, Offset(0f, h * 0.2f), Size(bodyW, h * 0.6f), androidx.compose.ui.geometry.CornerRadius(h * 0.1f), style = Stroke(h * 0.08f))
        drawRect(color, Offset(bodyW, h * 0.38f), Size(w * 0.1f, h * 0.24f))
        val fill = (bodyW - h * 0.24f) * (percent / 100f)
        drawRect(if (percent <= 15 && !charging) Spectra.Magenta else color, Offset(h * 0.12f, h * 0.32f), Size(fill, h * 0.36f))
    }
}

/** Avatar do perfil: animal desenhado, emoji ou inicial do nome. */
@Composable
fun Avatar(avatar: String, name: String, accent: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), Spectra.Violet))),
        contentAlignment = Alignment.Center
    ) {
        when {
            avatar.startsWith("animal:") -> {
                val a = runCatching { AnimalIcon.valueOf(avatar.removePrefix("animal:")) }.getOrDefault(AnimalIcon.DRAGON)
                AnimalIconView(a, Color.White, Modifier.size(size).padding(size * 0.12f))
            }
            avatar.startsWith("emoji:") -> Text(avatar.removePrefix("emoji:"), fontSize = (size.value * 0.5f).sp)
            else -> Text(
                name.trim().take(1).uppercase().ifBlank { "S" }, color = Color.White,
                fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

val AVATAR_OPTIONS = listOf(
    "animal:DRAGON", "animal:CAT", "animal:PUG", "animal:LION", "animal:TIGER",
    "emoji:😎", "emoji:🧑‍🚀", "emoji:🦸", "emoji:🧙", "emoji:👾", "emoji:🤖", "emoji:🦊", "emoji:🐼", "emoji:🐸", "emoji:👻", "initial",
)
