package com.hazagou.spectra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra
import kotlinx.coroutines.delay

/** Moldura padrão dos diálogos do Spectra. */
@Composable
fun SpectraDialog(onDismiss: () -> Unit, width: Dp = 520.dp, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(width).clip(RoundedCornerShape(26.dp)).background(LocalUi.current.surfaceHigh)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** Linha de opção focável (usada em menus e diálogos). */
@Composable
fun OptionRow(text: String, icon: String = "", modifier: Modifier = Modifier, value: String? = null, onClick: () -> Unit) {
    val accent = LocalUi.current.accent
    FocusCard(modifier.fillMaxWidth(), corner = 14.dp, focusScale = 1.03f, glow = false, background = Color.White.copy(alpha = 0.05f), onClick = onClick) { f ->
        Row(
            Modifier.fillMaxWidth().background(if (f) accent else Color.Transparent).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon.isNotEmpty()) Text(icon, modifier = Modifier.width(32.dp), color = Color.White)
            Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (value != null) Text(value, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Pede o foco para o primeiro item ao abrir. */
@Composable
fun rememberAutoFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
    return fr
}

@Composable
fun TextEditDialog(title: String, initial: String, hint: String = "", onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fr = rememberAutoFocus()
    SpectraDialog(onDismiss, width = 600.dp) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            placeholder = { if (hint.isNotEmpty()) Text(hint) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = LocalUi.current.accent
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(fr)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Salvar", leading = "✔", selected = true) { onSave(text); onDismiss() }
            PillButton("Cancelar", leading = "✕") { onDismiss() }
        }
    }
}
