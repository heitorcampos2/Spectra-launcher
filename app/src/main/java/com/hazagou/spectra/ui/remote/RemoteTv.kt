package com.hazagou.spectra.ui.remote

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazagou.spectra.MainViewModel
import com.hazagou.spectra.remote.RemoteActivity
import com.hazagou.spectra.remote.RemoteServer
import com.hazagou.spectra.ui.components.PillButton
import com.hazagou.spectra.ui.components.QrCode
import com.hazagou.spectra.ui.components.SpectraDialog
import com.hazagou.spectra.ui.components.TextEditDialog
import com.hazagou.spectra.ui.components.rememberAutoFocus
import com.hazagou.spectra.ui.home.toast
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra
import com.hazagou.spectra.update.UpdateManager
import com.hazagou.spectra.update.UpdateState
import kotlinx.coroutines.delay

/** Abre o "Spectra Controle" (este aparelho vira controle de outra TV). */
fun openPhoneRemote(ctx: android.content.Context) {
    runCatching { ctx.startActivity(Intent(ctx, RemoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Valida e salva o PIN (4 a 8 números). */
fun applyPin(vm: MainViewModel, ctx: android.content.Context, raw: String): Boolean {
    val pin = raw.filter(Char::isDigit)
    if (pin.length !in 4..8 || pin.length != raw.trim().length) { toast(ctx, "O PIN precisa ter de 4 a 8 números"); return false }
    vm.remote.setPin(pin)
    vm.remote.setEnabled(true)
    toast(ctx, "PIN salvo ✔ — celulares pareados antes precisam escanear de novo")
    return true
}

/**
 * Diálogo "Parear celular": pede o PIN (se ainda não houver) e mostra o QR code.
 * O QR abre o controle no app Spectra do celular ou, pela câmera comum, no navegador.
 */
@Composable
fun RemotePairDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val cfg by vm.remote.config.collectAsState()
    val running by RemoteServer.running.collectAsState()
    val last by RemoteServer.lastCommandAt.collectAsState()
    val ctx = LocalContext.current
    var askPin by remember { mutableStateOf(cfg.pin.isBlank()) }

    if (askPin) {
        TextEditDialog(
            title = if (cfg.pin.isBlank()) "Defina um PIN (4 a 8 números)" else "Novo PIN (4 a 8 números)",
            initial = "",
            onSave = { if (applyPin(vm, ctx, it)) askPin = false },
            onDismiss = { askPin = false; if (vm.remote.config.value.pin.isBlank()) onDismiss() }
        )
        return
    }

    // o servidor sobe em segundo plano; espera um instante para mostrar o endereço certo
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    val url = remember(cfg, running, tick) { if (running) RemoteServer.pairUrl(cfg) else null }
    val ip = remember(tick) { RemoteServer.localIp() }

    SpectraDialog(onDismiss, width = 860.dp) {
        val first = rememberAutoFocus()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📱 Controle pelo celular", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "1. Instale o Spectra no celular e abra \"Spectra Controle\".\n" +
                        "2. Toque em \"Escanear QR code da TV\".\n" +
                        "3. Celular e TV precisam estar na mesma rede Wi-Fi.",
                    color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium
                )
                Text("Sem o app? A câmera comum do celular também lê o QR e abre o controle no navegador.",
                    color = Spectra.Lilac, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                InfoLine("TV", cfg.tvName)
                InfoLine("Endereço", if (ip != null) "$ip:${RemoteServer.port}" else "sem rede")
                InfoLine("PIN", "•".repeat(cfg.pin.length) + "  (para parear pelo IP)")
                InfoLine("Status", when {
                    !cfg.enabled -> "Desligado"
                    !running -> "Iniciando…"
                    System.currentTimeMillis() - last < 60_000 -> "✔ Celular conectado"
                    else -> "Aguardando celular"
                })
                if (!vm.homeOverrideEnabled()) Text(
                    "Para controlar também YouTube, Netflix etc., ative \"Spectra — Botão Home e controle\" em Acessibilidade.",
                    color = Color(0xFFFDE68A), style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Fechar", selected = true, modifier = Modifier.focusRequester(first)) { onDismiss() }
                    PillButton("Trocar PIN", leading = "🔑") { askPin = true }
                    if (cfg.enabled) PillButton("Desligar", leading = "⏻") { vm.remote.setEnabled(false); onDismiss() }
                    else PillButton("Ligar", leading = "⏻") { vm.remote.setEnabled(true) }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (url != null) QrCode(url, Modifier.size(300.dp))
                else Column(
                    Modifier.size(300.dp).clip(RoundedCornerShape(18.dp)).background(Spectra.Surface).padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (!cfg.enabled) "Controle desligado" else if (ip == null) "Conecte a TV ao Wi-Fi ou cabo" else "Preparando…",
                        color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text("Escaneie com o celular", color = Spectra.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label: ", color = Spectra.TextSecondary, style = MaterialTheme.typography.titleSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

// ============================================================================ atualizações

/** Cartão de atualização: versão atual, verificar, novidades e botão de instalar. */
@Composable
fun UpdateCard(vm: MainViewModel, autoFocus: Boolean = false) {
    val st by vm.updates.state.collectAsState()
    val accent = LocalUi.current.accent
    val first = if (autoFocus) rememberAutoFocus() else null
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Spectra.Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Versão instalada: ${UpdateManager.currentVersion}", color = Color.White, style = MaterialTheme.typography.titleMedium)
        when (val s = st) {
            UpdateState.Idle -> Text("Toque em Verificar para procurar uma versão nova no GitHub.", color = Spectra.TextSecondary)
            UpdateState.Checking -> Text("Verificando no GitHub…", color = Spectra.TextSecondary)
            is UpdateState.UpToDate -> Text("✔ Você já está na versão mais recente (${s.latest}).", color = Color(0xFF86EFAC))
            is UpdateState.Available -> {
                Text("Nova versão ${s.release.version} disponível" + sizeLabel(s.release.sizeBytes), color = accent, style = MaterialTheme.typography.titleMedium)
                Notes(s.release.notes, s.release.publishedAt)
            }
            is UpdateState.Downloading -> {
                Text("Baixando ${s.release.version}… ${(s.progress * 100).toInt()}%", color = Color.White)
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = accent)
            }
            is UpdateState.Installing -> Text("Abrindo o instalador… confirme na tela da TV.", color = Color.White)
            is UpdateState.Failed -> Text(s.message, color = Color(0xFFFCA5A5))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val release = when (val s = st) {
                is UpdateState.Available -> s.release
                is UpdateState.Failed -> s.release
                else -> null
            }
            val mod = if (first != null) Modifier.focusRequester(first) else Modifier
            if (release != null) {
                PillButton("Atualizar agora", leading = "⬇", selected = true, modifier = mod) { vm.updates.downloadAndInstall(release) }
                PillButton("Verificar de novo", leading = "⟳") { vm.updates.check() }
            } else {
                PillButton("Verificar atualizações", leading = "⟳", selected = true, modifier = mod) { vm.updates.check() }
            }
        }
    }
}

private fun sizeLabel(bytes: Long) = if (bytes > 0) " (%.1f MB)".format(bytes / 1_048_576f) else ""

@Composable
private fun Notes(notes: String, date: String) {
    val text = notes.lines().filter { it.isNotBlank() }.take(8).joinToString("\n")
    if (text.isNotBlank() || date.isNotBlank()) Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(12.dp)
    ) {
        if (date.isNotBlank()) Text("Publicada em $date", color = Spectra.TextSecondary, style = MaterialTheme.typography.labelMedium)
        if (text.isNotBlank()) Text(text, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
    }
}

/** Página de downloads do GitHub com QR code (para baixar o APK pelo celular). */
@Composable
fun DownloadPageCard() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Spectra.Surface).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QrCode(UpdateManager.PAGE, Modifier.size(150.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("Baixar pelo celular ou computador", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("github.com/heitorcampos2/Spectra-launcher/releases", color = Spectra.Lilac, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("Escaneie para abrir a página com o botão de download do APK mais recente.",
                color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
