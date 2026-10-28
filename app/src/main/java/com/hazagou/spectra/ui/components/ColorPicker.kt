package com.hazagou.spectra.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra

/**
 * Seletor de cor em grade (como no Projectivy): 8 colunas, luminosidade, opacidade e "cor dinâmica".
 * O botão ✕ significa "automático" (cor tirada do wallpaper) quando [allowAuto] = true.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    colors: List<Long>,
    initial: Long,
    initialLum: Int = 0,
    initialAlpha: Float = 1f,
    initialAuto: Boolean = false,
    showAdjustments: Boolean = true,
    allowAuto: Boolean = true,
    onConfirm: (color: Long, lum: Int, alpha: Float, auto: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableLongStateOf(initial) }
    var lum by remember { mutableIntStateOf(initialLum) }
    var alpha by remember { mutableFloatStateOf(initialAlpha) }
    var auto by remember { mutableStateOf(initialAuto) }
    val first = rememberAutoFocus()
    val preview = Spectra.adjust(Color(color), lum).copy(alpha = alpha)

    SpectraDialog(onDismiss, width = 640.dp) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        val cells: List<Long?> = colors + (if (allowAuto) listOf<Long?>(null) else emptyList())
        androidx.compose.foundation.layout.Column(
            Modifier.heightIn(max = 260.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        cells.chunked(8).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, col ->
                    val selected = if (col == null) auto else (!auto && col == color)
                    FocusCard(
                        Modifier.size(52.dp).then(if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier),
                        corner = 14.dp, focusScale = 1.15f, glow = false,
                        background = if (col == null) Color.White else Color(col),
                        onClick = { if (col == null) auto = true else { color = col; auto = false } }
                    ) { _ ->
                        if (col == null) Text("✕", color = Color.Black, fontSize = 24.sp, modifier = Modifier.align(Alignment.Center))
                        if (selected) Box(
                            Modifier.fillMaxSize().border(3.dp, Color.White, RoundedCornerShape(14.dp))
                                .padding(16.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
                        )
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (auto) Brush.linearGradient(listOf(Spectra.Magenta, Spectra.Violet, Color(0xFF00E5FF))) else Brush.linearGradient(listOf(preview, preview)))
            )
            Spacer(Modifier.width(16.dp))
            Text(if (auto) "Automática (cor do wallpaper)" else "#%06X".format((color and 0xFFFFFFL)), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        if (showAdjustments) {
            AdjustRow("Luminosidade", "${if (lum > 0) "+" else ""}$lum") { d -> lum = (lum + d * 10).coerceIn(-60, 60) }
            AdjustRow("Opacidade", "${(alpha * 100).toInt()}%") { d -> alpha = (alpha + d * 0.1f).coerceIn(0.3f, 1f) }
            if (allowAuto) OptionRow("Preferir cor dinâmica (do wallpaper)", "◐", value = if (auto) "Sim" else "Não") { auto = !auto }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
            PillButton("Cancelar") { onDismiss() }
            PillButton("OK", leading = "✔", selected = true) { onConfirm(color, lum, alpha, auto); onDismiss() }
        }
    }
}

/** Linha com valor ajustável por ◀ ▶ (e OK avança). */
@Composable
fun AdjustRow(title: String, value: String, onAdjust: (Int) -> Unit) {
    val accent = LocalUi.current.accent
    FocusCard(Modifier.fillMaxWidth(), corner = 14.dp, focusScale = 1.02f, glow = false, background = Color.White.copy(alpha = 0.05f),
        onAdjust = onAdjust, onClick = { onAdjust(1) }) { f ->
        Row(Modifier.fillMaxWidth().background(if (f) accent.copy(alpha = 0.85f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (f) "◀  $value  ▶" else value, color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
    }
}
