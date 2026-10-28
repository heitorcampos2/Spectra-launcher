package com.hazagou.spectra.remote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.hazagou.spectra.SpectraApp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Purple = Color(0xFF8B5CF6)
private val Night = Color(0xFF120F33)
private val Card = Color(0xFF1E1A4E)
private val CardHigh = Color(0xFF2A2466)
private val Muted = Color(0xFFA9A3D9)

/**
 * "Spectra Controle": transforma o celular em controle remoto da TV com o Spectra.
 * Pareamento pelo QR code mostrado na TV (MENU ☰ › Controle remoto) ou por IP + PIN.
 */
class RemoteActivity : ComponentActivity() {

    private val store by lazy { (application as SpectraApp).remote }
    private var current: RemoteClient? = null
    private var onScanned: ((PairedTv?) -> Unit)? = null

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        onScanned?.invoke(RemoteClient.parsePairUrl(text))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Purple, secondary = Purple, background = Night, surface = Card)) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1F6B), Night, Night)))) {
                    RemoteApp()
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

    @Composable
    private fun RemoteApp() {
        val paired by store.paired.collectAsState()
        var selected by remember { mutableStateOf(paired.firstOrNull()) }
        var showList by remember { mutableStateOf(paired.isEmpty()) }
        var message by remember { mutableStateOf<String?>(null) }

        onScanned = { tv ->
            if (tv == null) message = "Este QR code não é de uma TV com Spectra."
            else { store.addPaired(tv); selected = tv; showList = false; message = "Conectado a ${tv.name} ✔" }
        }
        fun scan() = runCatching {
            scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Aponte para o QR code na TV").setBeepEnabled(false).setOrientationLocked(false))
        }.onFailure { message = "Câmera indisponível" }

        val sel = selected
        if (showList || sel == null) {
            current = null
            TvList(paired, onPick = { selected = it; showList = false }, onScan = { scan() },
                onPaired = { tv -> store.addPaired(tv); selected = tv; showList = false },
                onRemove = { store.removePaired(it) }, message = message, onMessage = { message = it })
        } else {
            BackHandler { showList = true }
            Controller(sel, onBack = { showList = true }, message = message, onMessage = { message = it })
        }
    }

    // ================================================================ lista de TVs

    @Composable
    private fun TvList(
        paired: List<PairedTv>, onPick: (PairedTv) -> Unit, onScan: () -> Unit, onPaired: (PairedTv) -> Unit,
        onRemove: (PairedTv) -> Unit, message: String?, onMessage: (String?) -> Unit,
    ) {
        var manual by remember { mutableStateOf(false) }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("📱 Spectra Controle", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Use o celular como controle remoto da TV. Os dois precisam estar na mesma rede Wi-Fi.", color = Muted)
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) {
                Text("⌗  Escanear QR code da TV", fontSize = 17.sp)
            }
            OutlinedButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                Text("Conectar com IP e PIN", color = Color.White)
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
                Text("Na TV: aperte MENU ☰ › Controle remoto › Parear celular. Defina um PIN e o QR code aparece na tela.",
                    color = Muted, fontSize = 14.sp)
            }
            Message(message, onMessage)
            if (paired.isNotEmpty()) Text("TVs pareadas", color = Purple, fontWeight = FontWeight.Bold)
            paired.forEach { tv ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card)
                        .combinedClickable(onClick = { onPick(tv) }).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📺", fontSize = 28.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tv.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${tv.host}:${tv.port}", color = Muted, fontSize = 13.sp)
                    }
                    TextButton(onClick = { onRemove(tv) }) { Text("Remover", color = Muted) }
                }
            }
        }
        if (manual) ManualPairDialog(onDismiss = { manual = false }, onPaired = { manual = false; onPaired(it) })
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
                    if (error != null) Text(error!!, color = Color(0xFFFCA5A5), fontSize = 13.sp)
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
    private fun Controller(tv: PairedTv, onBack: () -> Unit, message: String?, onMessage: (String?) -> Unit) {
        val client = remember(tv) { RemoteClient(tv) }
        DisposableEffect(client) { current = client; onDispose { if (current === client) current = null } }
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        var online by remember { mutableStateOf<Boolean?>(null) }
        var otherApps by remember { mutableStateOf(true) }
        var tab by remember { mutableStateOf(0) }

        LaunchedEffect(client) {
            while (true) {
                val info = client.info()
                online = info != null
                if (info != null) otherApps = info.optBoolean("controlsOtherApps", true)
                delay(6000)
            }
        }

        fun send(k: String) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { val r = client.key(k); if (!r.ok) onMessage(r.message) }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ TVs", color = Color.White, fontSize = 16.sp) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tv.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (online) { true -> "● Conectado"; false -> "● Fora de alcance"; null -> "Conectando…" },
                        color = when (online) { true -> Color(0xFF4ADE80); false -> Color(0xFFFCA5A5); null -> Muted }, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(64.dp))
            }
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Color.White) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Controle") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Apps") })
            }
            Message(message, onMessage)
            if (tab == 0) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!otherApps) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33F59E0B)).padding(12.dp)) {
                        Text("Na TV, ative \"Spectra — Botão Home e controle\" em Acessibilidade para controlar também outros apps (YouTube, Netflix…).",
                            color = Color(0xFFFDE68A), fontSize = 13.sp)
                    }
                    DPad(::send)
                    Text("Deslize no círculo para navegar • segure OK para opções", color = Muted, fontSize = 12.sp)
                    KeyRow { Key("↩", "Voltar", Modifier.weight(1f)) { send("back") }; Key("⌂", "Início", Modifier.weight(1f)) { send("home") }; Key("☰", "Menu", Modifier.weight(1f)) { send("menu") } }
                    KeyRow { Key("⏪", null, Modifier.weight(1f)) { send("rew") }; Key("⏯", null, Modifier.weight(1f)) { send("play") }; Key("⏩", null, Modifier.weight(1f)) { send("ff") } }
                    KeyRow { Key("🔉", null, Modifier.weight(1f)) { send("voldown") }; Key("🔇", null, Modifier.weight(1f)) { send("mute") }; Key("🔊", null, Modifier.weight(1f)) { send("volup") } }
                    KeyRow { Key("⏮", null, Modifier.weight(1f)) { send("prev") }; Key("⚙", "Ajustes", Modifier.weight(1f)) { send("settings") }; Key("⏭", null, Modifier.weight(1f)) { send("next") } }
                    TextSender(client, onMessage)
                    Spacer(Modifier.height(24.dp))
                }
            } else AppsGrid(client, onMessage)
        }
    }

    @Composable
    private fun DPad(send: (String) -> Unit) {
        var drag by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.size(270.dp).clip(CircleShape).background(Brush.radialGradient(listOf(CardHigh, Card)))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { drag = Offset.Zero },
                        onDragEnd = {
                            val (x, y) = drag
                            if (maxOf(abs(x), abs(y)) > 40f) send(if (abs(x) > abs(y)) (if (x > 0) "right" else "left") else (if (y > 0) "down" else "up"))
                            drag = Offset.Zero
                        }
                    ) { change, amount -> change.consume(); drag += amount }
                }
        ) {
            PadKey("▲", Modifier.align(Alignment.TopCenter)) { send("up") }
            PadKey("▼", Modifier.align(Alignment.BottomCenter)) { send("down") }
            PadKey("◀", Modifier.align(Alignment.CenterStart)) { send("left") }
            PadKey("▶", Modifier.align(Alignment.CenterEnd)) { send("right") }
            Box(
                Modifier.align(Alignment.Center).size(112.dp).clip(CircleShape).background(Purple)
                    .combinedClickable(onLongClick = { send("oklong") }, onClick = { send("ok") }),
                contentAlignment = Alignment.Center
            ) { Text("OK", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
    }

    @Composable
    private fun PadKey(icon: String, modifier: Modifier, onClick: () -> Unit) {
        Box(modifier.size(78.dp).clip(CircleShape).combinedClickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(icon, color = Color.White, fontSize = 26.sp)
        }
    }

    @Composable
    private fun KeyRow(content: @Composable RowScope.() -> Unit) =
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)

    @Composable
    private fun Key(icon: String, label: String?, modifier: Modifier, onClick: () -> Unit) {
        Column(
            modifier.height(64.dp).clip(RoundedCornerShape(18.dp)).background(Card).combinedClickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(icon, color = Color.White, fontSize = 22.sp)
            if (label != null) Text(label, color = Muted, fontSize = 11.sp)
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { go() }))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { go() }, colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(14.dp)) { Text("Enviar") }
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
        LazyVerticalGrid(GridCells.Adaptive(92.dp), Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.pkg }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(Card)
                        .combinedClickable(onClick = { scope.launch { val r = client.launch(app.pkg); onMessage(if (r.ok) "Abrindo ${app.label}…" else r.message) } })
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(client.iconUrl(app.pkg), app.label, Modifier.size(52.dp).aspectRatio(1f))
                    Spacer(Modifier.height(6.dp))
                    Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }

    @Composable
    private fun Message(message: String?, onMessage: (String?) -> Unit) {
        LaunchedEffect(message) { if (message != null) { delay(3500); onMessage(null) } }
        if (message != null) Text(
            message, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color(0x55000000)).padding(10.dp)
        )
    }
}
