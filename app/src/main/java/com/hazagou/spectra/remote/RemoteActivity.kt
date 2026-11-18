package com.hazagou.spectra.remote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.hazagou.spectra.BuildConfig
import com.hazagou.spectra.SpectraApp
import com.hazagou.spectra.update.SintoniaState
import com.hazagou.spectra.update.SintoniaSuggestion
import com.hazagou.spectra.update.UpdateState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Night = Color(0xFF0E0C24)
private val Card = Color(0xFF1C1940)
private val CardHigh = Color(0xFF2A2660)
private val Line = Color(0x14FFFFFF)
private val Muted = Color(0xFFA9A3D9)
private val Good = Color(0xFF4ADE80)
private val Bad = Color(0xFFFCA5A5)

/** Aparência escolhida em Ajustes do controle. */
private data class Look(val accent: RemoteAccent, val icons: RemoteIconStyle, val haptic: Boolean)
private val LocalLook = staticCompositionLocalOf { Look(REMOTE_ACCENTS[0], RemoteIconStyle.LINHAS, true) }

private enum class Page { LIST, CONTROL, SETTINGS }

/**
 * "Spectra Controle": transforma o celular em controle remoto da TV (ou do tablet) com o Spectra.
 * Pareamento pelo QR code mostrado na TV (MENU ☰ › Controle remoto) ou por IP + PIN.
 */
class RemoteActivity : ComponentActivity() {

    private val app by lazy { application as SpectraApp }
    private val store by lazy { app.remote }
    private val look by lazy { RemoteLookStore(this) }
    private val smartTube by lazy { SintoniaSuggestion.smartTube(applicationContext) }
    private var current: RemoteClient? = null
    private var onScanned: ((PairedTv?) -> Unit)? = null

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        onScanned?.invoke(RemoteClient.parsePairUrl(text))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.updates.checkIfDue()
        setContent {
            val accentIdx by look.accent.collectAsState()
            val icons by look.icons.collectAsState()
            val haptic by look.haptic.collectAsState()
            val accent = REMOTE_ACCENTS[accentIdx]
            CompositionLocalProvider(LocalLook provides Look(accent, icons, haptic)) {
                MaterialTheme(colorScheme = darkColorScheme(primary = accent.color, secondary = accent.color, background = Night, surface = Card)) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.glow, Night, Night)))) {
                        RemoteApp()
                    }
                }
            }
        }
    }

    /** Botões de volume do celular controlam o volume da TV. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val c = current
        if (c != null && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val k = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "volup" else "voldown"
            lifecycleScope.launch { c.key(k) }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ================================================================ navegação

    @Composable
    private fun RemoteApp() {
        val paired by store.paired.collectAsState()
        var selected by remember { mutableStateOf(paired.firstOrNull()) }
        var page by remember { mutableStateOf(if (paired.isEmpty()) Page.LIST else Page.CONTROL) }
        var back by remember { mutableStateOf(Page.LIST) }
        var message by remember { mutableStateOf<String?>(null) }

        onScanned = { tv ->
            if (tv == null) message = "Este QR code não é de uma TV com Spectra."
            else { val t = store.addPaired(tv); selected = t; page = Page.CONTROL; message = "Conectado a ${t.label} ✔" }
        }
        fun scan() = runCatching {
            scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Aponte para o QR code na TV").setBeepEnabled(false).setOrientationLocked(false))
        }.onFailure { message = "Câmera indisponível" }
        fun openSettings(from: Page) { back = from; page = Page.SETTINGS }

        val sel = selected
        val shown = if (page == Page.CONTROL && sel == null) Page.LIST else page
        if (shown != Page.CONTROL) current = null
        if (shown != Page.LIST) BackHandler { page = if (shown == Page.SETTINGS && back == Page.CONTROL && sel != null) Page.CONTROL else Page.LIST }

        AnimatedContent(shown, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page") { p ->
            when (p) {
                Page.LIST -> TvList(paired, onPick = { selected = it; page = Page.CONTROL }, onScan = { scan() },
                    onPaired = { tv -> selected = store.addPaired(tv); page = Page.CONTROL },
                    onRemove = { store.removePaired(it); if (selected?.id == it.id) selected = null },
                    onRename = { tv, n -> val r = store.renamePaired(tv, n); if (selected?.id == tv.id) selected = r },
                    onSettings = { openSettings(Page.LIST) }, message = message, onMessage = { message = it })
                Page.CONTROL -> sel?.let { Controller(it, onBack = { page = Page.LIST }, onSettings = { openSettings(Page.CONTROL) }, message = message, onMessage = { message = it }) }
                Page.SETTINGS -> SettingsPage(onBack = { page = if (back == Page.CONTROL && sel != null) Page.CONTROL else Page.LIST })
            }
        }
    }

    // ================================================================ peças comuns

    @Composable
    private fun TopBar(title: String, subtitle: @Composable (() -> Unit)? = null, onBack: (() -> Unit)?, onSettings: (() -> Unit)?) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(56.dp)) { if (onBack != null) RoundIcon("back", onBack) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.invoke()
            }
            Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) { if (onSettings != null) RoundIcon("settings", onSettings) }
        }
    }

    @Composable
    private fun RoundIcon(key: String, onClick: () -> Unit) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Card).border(1.dp, Line, CircleShape).combinedClickable(onClick = onClick),
            contentAlignment = Alignment.Center) { RemoteIcon(key, LocalLook.current.icons, size = 20.dp) }
    }

    @Composable
    private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), color = LocalLook.current.accent.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            content()
        }
    }

    @Composable
    private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }

    @Composable
    private fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LocalLook.current.accent.color, contentColor = Color.White)) { Text(text, fontWeight = FontWeight.SemiBold) }
    }

    @Composable
    private fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(16.dp)) { Text(text, color = Color.White) }
    }

    // ================================================================ lista de aparelhos

    @Composable
    private fun TvList(
        paired: List<PairedTv>, onPick: (PairedTv) -> Unit, onScan: () -> Unit, onPaired: (PairedTv) -> Unit,
        onRemove: (PairedTv) -> Unit, onRename: (PairedTv, String) -> Unit, onSettings: () -> Unit,
        message: String?, onMessage: (String?) -> Unit,
    ) {
        var manual by remember { mutableStateOf(false) }
        var renaming by remember { mutableStateOf<PairedTv?>(null) }
        val upd by app.updates.state.collectAsState()
        val accent = LocalLook.current.accent.color
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Spectra Controle", onBack = null, onSettings = onSettings)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Use o celular como controle remoto da TV ou do tablet. Os dois precisam estar na mesma rede Wi-Fi.", color = Muted, fontSize = 14.sp)
                if (upd is UpdateState.Available) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.18f))
                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).combinedClickable(onClick = onSettings).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⬆", color = Color.White, fontSize = 18.sp); Spacer(Modifier.width(10.dp))
                    Text("Nova versão do controle: ${(upd as UpdateState.Available).release.version}. Toque para atualizar.", color = Color.White, fontSize = 14.sp)
                }
                AccentButton("⌗  Escanear QR code da TV", Modifier.fillMaxWidth().height(56.dp), onClick = onScan)
                GhostButton("Conectar com IP e PIN", Modifier.fillMaxWidth()) { manual = true }
                Panel {
                    Text("Na TV ou no tablet: abra o Spectra › MENU ☰ › Controle remoto › Parear celular. Defina um PIN e o QR code aparece na tela.",
                        color = Muted, fontSize = 13.sp)
                }
                Message(message, onMessage)
                if (paired.isNotEmpty()) Section("Aparelhos pareados") {
                    paired.forEach { tv ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(20.dp))
                                .combinedClickable(onClick = { onPick(tv) }, onLongClick = { renaming = tv }).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Text(if (tv.name.contains("TV", true)) "📺" else "📱", fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tv.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (tv.alias.isNotBlank()) "${tv.name} · ${tv.host}" else "${tv.host}:${tv.port}", color = Muted, fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(onClick = { renaming = tv }) { Text("Renomear", color = Muted, fontSize = 13.sp) }
                                TextButton(onClick = { onRemove(tv) }) { Text("Remover", color = Muted, fontSize = 13.sp) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        if (manual) ManualPairDialog(onDismiss = { manual = false }, onPaired = { manual = false; onPaired(it) })
        renaming?.let { tv -> RenameDialog(tv, onDismiss = { renaming = null }, onSave = { onRename(tv, it); renaming = null }) }
    }

    @Composable
    private fun RenameDialog(tv: PairedTv, onDismiss: () -> Unit, onSave: (String) -> Unit) {
        var name by remember { mutableStateOf(tv.alias) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Renomear aparelho") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Opcional. Deixe em branco para voltar ao nome original: ${tv.name}.", color = Muted, fontSize = 13.sp)
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nome (ex.: TV da sala, Tablet)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSave(name) }))
                }
            },
            confirmButton = { Button(onClick = { onSave(name) }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        )
    }

    @Composable
    private fun ManualPairDialog(onDismiss: () -> Unit, onPaired: (PairedTv) -> Unit) {
        var host by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Conectar à TV") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("O IP e o PIN aparecem na TV em Controle remoto › Parear celular.", color = Muted, fontSize = 13.sp)
                    OutlinedTextField(host, { host = it.trim() }, label = { Text("IP da TV (ex.: 192.168.0.20)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    if (error != null) Text(error!!, color = Bad, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(enabled = !busy && host.isNotBlank() && pin.length >= 4, onClick = {
                    busy = true; error = null
                    val h = host.substringBefore(':')
                    val p = host.substringAfter(':', "").toIntOrNull() ?: RemoteServer.DEFAULT_PORT
                    scope.launch {
                        RemoteClient.pair(h, p, pin).onSuccess(onPaired).onFailure { error = it.message ?: "Falha ao conectar" }
                        busy = false
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Conectar") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        )
    }

    // ================================================================ controle

    @Composable
    private fun Controller(tv: PairedTv, onBack: () -> Unit, onSettings: () -> Unit, message: String?, onMessage: (String?) -> Unit) {
        val client = remember(tv) { RemoteClient(tv) }
        DisposableEffect(client) { current = client; onDispose { if (current === client) current = null } }
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val lk = LocalLook.current
        var online by remember { mutableStateOf<Boolean?>(null) }
        var otherApps by remember { mutableStateOf(true) }
        var tab by remember { mutableStateOf(0) }
        var tvInfo by remember { mutableStateOf("") }
        var lastVia by remember { mutableStateOf<String?>(null) }
        var touchpad by remember { mutableStateOf(false) }
        // touchpad: junta os movimentos do dedo e manda no máximo ~25 vezes por segundo
        val pending = remember { floatArrayOf(0f, 0f) }
        LaunchedEffect(client, touchpad) {
            while (touchpad) {
                delay(40)
                val dx: Float; val dy: Float
                synchronized(pending) { dx = pending[0]; dy = pending[1]; pending[0] = 0f; pending[1] = 0f }
                if (dx != 0f || dy != 0f) { val r = client.move(dx, dy); if (!r.ok) onMessage(r.message) }
            }
        }

        LaunchedEffect(client) {
            while (true) {
                val info = client.info()
                online = info != null
                if (info != null) {
                    otherApps = info.optBoolean("controlsOtherApps", true)
                    touchpad = info.optBoolean("touch") && info.optBoolean("gestures", false)
                    tvInfo = buildString {
                        append("Spectra ").append(info.optString("version"))
                        if (!info.optBoolean("a11y", true)) append(" · acessibilidade desligada")
                        else if (info.optBoolean("touch") && !info.optBoolean("gestures", true)) append(" · religue a acessibilidade")
                    }
                }
                delay(6000)
            }
        }

        fun send(k: String) {
            if (lk.haptic) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { val r = client.key(k); if (!r.ok) onMessage(r.message) else if (r.via != null) lastVia = r.via }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            TopBar(tv.label, subtitle = {
                Text(
                    when (online) { true -> "● Conectado"; false -> "● Fora de alcance"; null -> "Conectando…" },
                    color = when (online) { true -> Good; false -> Bad; null -> Muted }, fontSize = 12.sp
                )
                if (online == true && tvInfo.isNotBlank())
                    Text(tvInfo + (lastVia?.let { " · $it" } ?: ""), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }, onBack = onBack, onSettings = onSettings)
            Segmented(listOf("Controle", "Apps"), tab) { tab = it }
            Message(message, onMessage)
            if (tab == 0) {
                // Sem rolagem: tudo se ajusta ao tamanho da tela (celular em pé, deitado, tablet)
                BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val W = maxWidth; val H = maxHeight
                    val warn = !otherApps
                    val warnH = if (warn) 64.dp else 0.dp
                    if (W > H * 1.15f) {
                        // deitado: setas à esquerda, botões à direita
                        val d = minOf(H - warnH, W * 0.46f).coerceAtLeast(150.dp)
                        Column(Modifier.fillMaxSize()) {
                            if (warn) A11yWarning()
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(Modifier.width(d), contentAlignment = Alignment.Center) { DPad(d, ::send, if (touchpad) { dx: Float, dy: Float -> synchronized(pending) { pending[0] += dx; pending[1] += dy } } else null) }
                                val k = ((H - warnH - 56.dp - 50.dp) / 4).coerceIn(40.dp, 64.dp)
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                                    KeyRows(k, ::send)
                                    TextSender(client, onMessage)
                                }
                            }
                        }
                    } else {
                        // em pé: altura dos botões e tamanho do círculo calculados pelo espaço que sobra
                        val k = (H * 0.085f).coerceIn(44.dp, 66.dp)
                        val hint = H > 640.dp
                        val fixed = warnH + k * 4 + 56.dp + (if (hint) 18.dp else 0.dp) + 8.dp * 7
                        val d = minOf(W - 24.dp, H - fixed).coerceIn(150.dp, 300.dp)
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                            if (warn) A11yWarning()
                            KeyRow { Key("back", "Voltar", ::send, h = k); Key("home", "Início", ::send, h = k); Key("menu", "Menu", ::send, h = k) }
                            DPad(d, ::send, if (touchpad) { dx: Float, dy: Float -> synchronized(pending) { pending[0] += dx; pending[1] += dy } } else null)
                            if (hint) Text(if (touchpad) "Arraste no círculo para mover o cursor • OK toca • setas na borda rolam" else "Deslize no círculo para navegar • segure OK para opções",
                                color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            KeyRow { Key("voldown", "Vol −", ::send, h = k); Key("mute", "Mudo", ::send, h = k); Key("volup", "Vol +", ::send, h = k) }
                            KeyRow { Key("rew", null, ::send, h = k); Key("play", null, ::send, highlight = true, h = k); Key("ff", null, ::send, h = k) }
                            KeyRow { Key("prev", null, ::send, h = k); Key("settings", "Ajustes", ::send, h = k); Key("next", null, ::send, h = k) }
                            TextSender(client, onMessage)
                        }
                    }
                }
            } else AppsGrid(client, onMessage)
        }
    }

    @Composable
    private fun A11yWarning() {
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33F59E0B)).padding(10.dp)) {
            Text("No aparelho, ative \"Spectra — Botão Home e controle\" em Acessibilidade para controlar outros apps.",
                color = Color(0xFFFDE68A), fontSize = 12.sp, maxLines = 2)
        }
    }

    @Composable
    private fun KeyRows(k: androidx.compose.ui.unit.Dp, send: (String) -> Unit) {
        KeyRow { Key("back", "Voltar", send, h = k); Key("home", "Início", send, h = k); Key("menu", "Menu", send, h = k) }
        KeyRow { Key("voldown", "Vol −", send, h = k); Key("mute", "Mudo", send, h = k); Key("volup", "Vol +", send, h = k) }
        KeyRow { Key("rew", null, send, h = k); Key("play", null, send, highlight = true, h = k); Key("ff", null, send, h = k) }
        KeyRow { Key("prev", null, send, h = k); Key("settings", "Ajustes", send, h = k); Key("next", null, send, h = k) }
    }

    @Composable
    private fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val accent = LocalLook.current.accent.color
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Card).padding(4.dp)) {
            items.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (i == selected) accent else Color.Transparent).combinedClickable(onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center) {
                    Text(label, color = if (i == selected) Color.White else Muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    @Composable
    private fun DPad(d: androidx.compose.ui.unit.Dp, send: (String) -> Unit, onMove: ((Float, Float) -> Unit)? = null) {
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val lk = LocalLook.current
        var drag by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.size(d).clip(CircleShape)
                .background(Brush.radialGradient(listOf(CardHigh, Card, lk.accent.glow)))
                .border(2.dp, Brush.sweepGradient(listOf(lk.accent.color.copy(alpha = 0.6f), Color.White.copy(alpha = 0.06f), lk.accent.color.copy(alpha = 0.6f))), CircleShape)
                .pointerInput(onMove != null) {
                    if (onMove != null) detectDragGestures { change, amount -> change.consume(); onMove(amount.x / density, amount.y / density) }
                    else detectDragGestures(
                        onDragStart = { drag = Offset.Zero },
                        onDragEnd = {
                            val (x, y) = drag
                            if (maxOf(abs(x), abs(y)) > 40f) send(if (abs(x) > abs(y)) (if (x > 0) "right" else "left") else (if (y > 0) "down" else "up"))
                            drag = Offset.Zero
                        }
                    ) { change, amount -> change.consume(); drag += amount }
                }
        ) {
            PadKey("up", Alignment.TopCenter, d, send)
            PadKey("down", Alignment.BottomCenter, d, send)
            PadKey("left", Alignment.CenterStart, d, send)
            PadKey("right", Alignment.CenterEnd, d, send)
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            val sc by animateFloatAsState(if (pressed) 0.92f else 1f, label = "ok")
            Box(
                Modifier.align(Alignment.Center).size(d * 0.41f).scale(sc).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(lk.accent.color, lk.accent.color.copy(alpha = 0.75f))))
                    .border(3.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .combinedClickable(interactionSource = src, indication = null, onLongClick = { send("oklong") }, onClick = { send("ok") }),
                contentAlignment = Alignment.Center
            ) { Text("OK", color = Color.White, fontSize = (d.value * 0.085f).sp, fontWeight = FontWeight.Bold) }
        }
    }

    @Composable
    private fun BoxScope.PadKey(key: String, align: Alignment, d: androidx.compose.ui.unit.Dp, send: (String) -> Unit) {
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val sc by animateFloatAsState(if (pressed) 0.85f else 1f, label = key)
        Box(Modifier.align(align).size(d * 0.29f).scale(sc).clip(CircleShape)
            .background(if (pressed) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(interactionSource = src, indication = null, onClick = { send(key) }),
            contentAlignment = Alignment.Center) { RemoteIcon(key, LocalLook.current.icons, size = d * 0.11f) }
    }

    @Composable
    private fun KeyRow(content: @Composable RowScope.() -> Unit) =
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)

    @Composable
    private fun RowScope.Key(key: String, label: String?, send: (String) -> Unit, highlight: Boolean = false, h: androidx.compose.ui.unit.Dp = 66.dp) {
        val lk = LocalLook.current
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val sc by animateFloatAsState(if (pressed) 0.93f else 1f, label = key)
        Column(
            Modifier.weight(1f).height(h).scale(sc).clip(RoundedCornerShape(20.dp))
                .background(if (highlight) lk.accent.color.copy(alpha = 0.28f) else if (pressed) CardHigh else Card)
                .border(1.dp, if (highlight) lk.accent.color.copy(alpha = 0.5f) else Line, RoundedCornerShape(20.dp))
                .combinedClickable(interactionSource = src, indication = null, onClick = { send(key) }),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            RemoteIcon(key, lk.icons, size = (h * 0.36f).coerceIn(18.dp, 24.dp))
            if (label != null && h >= 54.dp) { Spacer(Modifier.height(3.dp)); Text(label, color = Muted, fontSize = 11.sp) }
        }
    }

    @Composable
    private fun TextSender(client: RemoteClient, onMessage: (String?) -> Unit) {
        var text by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        fun go() {
            val t = text
            if (t.isBlank()) return
            scope.launch { val r = client.text(t); if (r.ok) { text = ""; onMessage("Texto enviado ✔") } else onMessage(r.message) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Digitar na TV (busca, senha…)") }, singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { go() }))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(LocalLook.current.accent.color).combinedClickable(onClick = { go() }),
                contentAlignment = Alignment.Center) { RemoteIcon("send", LocalLook.current.icons, size = 22.dp) }
        }
    }

    @Composable
    private fun AppsGrid(client: RemoteClient, onMessage: (String?) -> Unit) {
        var apps by remember { mutableStateOf<List<RemoteApp>?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(client) {
            apps = runCatching { client.apps() }.getOrElse { onMessage(it.message ?: "Não foi possível carregar os apps"); emptyList() }
        }
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.pkg }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(20.dp))
                        .combinedClickable(onClick = { scope.launch { val r = client.launch(app.pkg); onMessage(if (r.ok) "Abrindo ${app.label}…" else r.message) } })
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(client.iconUrl(app.pkg), app.label, Modifier.size(54.dp).aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }

    // ================================================================ ajustes do controle

    @Composable
    private fun SettingsPage(onBack: () -> Unit) {
        val lk = LocalLook.current
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Ajustes do controle", onBack = onBack, onSettings = null)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Section("Cor do controle") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        REMOTE_ACCENTS.forEachIndexed { i, a ->
                            val sel = a == lk.accent
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                                Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.radialGradient(listOf(a.color, a.glow)))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Line, CircleShape)
                                    .combinedClickable(onClick = { look.setAccent(i) }), contentAlignment = Alignment.Center) {
                                    if (sel) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(a.name, color = if (sel) Color.White else Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Section("Ícones dos botões") {
                    RemoteIconStyle.values().forEach { st ->
                        val sel = st == lk.icons
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (sel) lk.accent.color.copy(alpha = 0.18f) else Card)
                                .border(1.dp, if (sel) lk.accent.color else Line, RoundedCornerShape(18.dp))
                                .combinedClickable(onClick = { look.setIcons(st) }).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(st.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(st.sample, color = Muted, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("home", "back", "play", "volup").forEach { RemoteIcon(it, st, size = 22.dp) }
                            }
                        }
                    }
                }
                Section("Toque") {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Vibrar ao apertar", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Uma vibração curta a cada botão", color = Muted, fontSize = 12.sp)
                            }
                            Switch(lk.haptic, { look.setHaptic(it) }, colors = SwitchDefaults.colors(checkedTrackColor = lk.accent.color))
                        }
                    }
                }
                Section("Atualizações") { UpdatesPanel() }
                Section("Apps sugeridos") {
                    SuggestionPanel(app.sintonia, "📡", "TV ao vivo, filmes e séries da sua lista IPTV, com guia e favoritos.")
                    SuggestionPanel(smartTube, "▶️", "YouTube sem anúncios, feito para TV e controle remoto.")
                }
                Text("Spectra Controle ${BuildConfig.VERSION_NAME} · por Heitor Campos", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    @Composable
    private fun UpdatesPanel() {
        val st by app.updates.state.collectAsState()
        LaunchedEffect(Unit) { if (app.updates.state.value is UpdateState.Idle) app.updates.check() }
        val lk = LocalLook.current
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(lk.accent.color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("📱", fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Spectra Controle", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Versão neste celular: ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp)
                }
            }
            when (val s = st) {
                UpdateState.Idle, UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                    Text("Procurando atualização…", color = Muted, fontSize = 13.sp)
                }
                is UpdateState.UpToDate -> Text("✔ Você está na versão mais nova.", color = Good, fontSize = 13.sp)
                is UpdateState.Available -> {
                    Text("Nova versão ${s.release.version} disponível.", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (s.release.notes.isNotBlank()) Text(s.release.notes.take(400), color = Muted, fontSize = 12.sp)
                    AccentButton("Baixar e instalar", Modifier.fillMaxWidth()) { app.updates.downloadAndInstall(s.release) }
                }
                is UpdateState.Downloading -> {
                    Text("Baixando ${s.release.version}… ${(s.progress * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = lk.accent.color)
                }
                is UpdateState.Installing -> Text("Abrindo o instalador… confirme na tela.", color = Color.White, fontSize = 13.sp)
                is UpdateState.Failed -> {
                    Text(s.message, color = Bad, fontSize = 13.sp)
                    s.release?.let { r -> AccentButton("Tentar de novo", Modifier.fillMaxWidth()) { app.updates.downloadAndInstall(r) } }
                }
            }
            if (st !is UpdateState.Checking && st !is UpdateState.Downloading && st !is UpdateState.Installing)
                GhostButton("Verificar agora", Modifier.fillMaxWidth()) { app.updates.check() }
        }
    }

    @Composable
    private fun SuggestionPanel(sug: SintoniaSuggestion, emoji: String, description: String) {
        val st by sug.state.collectAsState()
        val installed = remember(st) { sug.installedVersion() }
        val lk = LocalLook.current
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(lk.accent.color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sug.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(description, color = Muted, fontSize = 12.sp)
                }
            }
            when (val s = st) {
                SintoniaState.Checking -> Text("Procurando a versão mais nova…", color = Muted, fontSize = 13.sp)
                is SintoniaState.Downloading -> {
                    Text("Baixando… ${(s.progress * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = lk.accent.color)
                }
                is SintoniaState.Installing -> Text("Abrindo o instalador… confirme na tela.", color = Color.White, fontSize = 13.sp)
                is SintoniaState.Done -> Text("✔ ${sug.name} instalado.", color = Good, fontSize = 13.sp)
                is SintoniaState.Failed -> Text(s.message, color = Bad, fontSize = 13.sp)
                SintoniaState.Idle -> if (installed != null) Text("Instalado (versão $installed).", color = Muted, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (installed != null) {
                    AccentButton("Abrir", Modifier.weight(1f)) { sug.open() }
                    GhostButton("Atualizar", Modifier.weight(1f)) { sug.installLatest(app.updates) }
                } else AccentButton("Instalar", Modifier.fillMaxWidth()) { sug.installLatest(app.updates) }
            }
        }
    }

    @Composable
    private fun Message(message: String?, onMessage: (String?) -> Unit) {
        LaunchedEffect(message) { if (message != null) { delay(3500); onMessage(null) } }
        if (message != null) Text(
            message, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).padding(10.dp)
        )
    }
}
