package com.hazagou.spectra.ui.home

import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazagou.spectra.BuildConfig
import com.hazagou.spectra.MainViewModel
import com.hazagou.spectra.Screen
import com.hazagou.spectra.ShortcutKind
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.service.SpectraNotificationService
import com.hazagou.spectra.ui.components.AVATAR_OPTIONS
import com.hazagou.spectra.ui.components.AnimalIconView
import com.hazagou.spectra.ui.components.Avatar
import com.hazagou.spectra.ui.components.FocusCard
import com.hazagou.spectra.ui.components.OptionRow
import com.hazagou.spectra.ui.components.PillButton
import com.hazagou.spectra.ui.components.SpectraDialog
import com.hazagou.spectra.ui.components.TextEditDialog
import com.hazagou.spectra.ui.components.rememberAutoFocus
import com.hazagou.spectra.ui.settings.LauncherActions
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Painel lateral aberto pela tecla MENU (☰) — atalhos, sistema e "Sobre". */
@Composable
fun SidePanel(vm: MainViewModel, settings: LauncherSettings, actions: LauncherActions, visible: Boolean) {
    var about by remember { mutableStateOf<String?>(null) }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, null) { vm.sidePanelOpen.value = false }
        )
    }
    AnimatedVisibility(visible, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
        BackHandler { vm.sidePanelOpen.value = false }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            PanelFrame {
                PanelHeader(settings)
                val first = rememberAutoFocus()
                val notifs by vm.notifications.collectAsState()
                val ctx = LocalContext.current
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { GroupTitle("Atalhos") }
                    item { OptionRow("Todos os apps", "▦", Modifier.focusRequester(first)) { vm.open(Screen.APPS) } }
                    item { OptionRow("Editar seções", "☰") { vm.open(Screen.SECTIONS) } }
                    item { OptionRow("Editar canais", "📺") { vm.sidePanelOpen.value = false; vm.channelsOpen.value = true } }
                    item { OptionRow("Personalizar Spectra", "✦") { vm.open(Screen.SETTINGS) } }
                    item { OptionRow("Wallpapers", "🖼") { vm.open(Screen.WALLPAPER) } }
                    item { OptionRow("Configurações do Android", "⚙") { runShortcut(ShortcutKind.ANDROID_SETTINGS, vm) } }
                    item { GroupTitle("Controle remoto") }
                    item { OptionRow("Parear celular (QR code)", "📱") { vm.sidePanelOpen.value = false; vm.remotePairOpen.value = true } }
                    item { OptionRow("Usar este aparelho como controle", "🎮") { vm.sidePanelOpen.value = false; com.hazagou.spectra.ui.remote.openPhoneRemote(ctx) } }
                    item {
                        val up by vm.updates.state.collectAsState()
                        OptionRow("Atualizações", "⟳", value = when (val u = up) {
                            is com.hazagou.spectra.update.UpdateState.Available -> "Nova: ${u.release.version}"
                            is com.hazagou.spectra.update.UpdateState.Downloading -> "${(u.progress * 100).toInt()}%"
                            else -> BuildConfig.VERSION_NAME.substringBefore('-')
                        }) { vm.openSettings("UPDATES") }
                    }
                    item { GroupTitle("Sistema") }
                    item { OptionRow("Notificações", "🔔", value = if (notifs.isEmpty()) null else "${notifs.size}") { vm.sidePanelOpen.value = false; vm.notificationsOpen.value = true } }
                    item { OptionRow("Wi-Fi e rede", "📶") { vm.system.openWifiSettings() } }
                    item { OptionRow("Perfil", "☺", value = settings.profileName) { vm.sidePanelOpen.value = false; vm.profileOpen.value = true } }
                    item {
                        OptionRow("Botão Home (acessibilidade)", "🏠", value = if (actions.isHomeOverrideEnabled()) "Ativo" else "Ativar") { actions.openAccessibilitySettings() }
                    }
                    item {
                        OptionRow("Launcher padrão", "★", value = if (actions.isDefaultLauncher()) "Sim" else "Definir") { actions.requestDefaultLauncher() }
                    }
                    item { GroupTitle("Sobre") }
                    item { OptionRow("Spectra Launcher", "ℹ", value = BuildConfig.VERSION_NAME) { about = "Spectra Launcher ${BuildConfig.VERSION_NAME}\nDesenvolvedor: Heitor Campos\ngithub.com/heitorcampos2\nLauncher para Android TV sem anúncios e sem rastreamento." } }
                    item {
                        OptionRow("Este dispositivo", "🖥") {
                            val cfg = ctx.resources.displayMetrics
                            about = "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                                "Tela: ${cfg.widthPixels}×${cfg.heightPixels} • ${cfg.densityDpi} dpi"
                        }
                    }
                    item {
                        OptionRow("Este wallpaper", "🖼") {
                            about = settings.wallpaperPath?.let { p -> "Arquivo: ${p.substringAfterLast('/')}\nTroca automática: " +
                                (if (settings.rotationMinutes > 0) "a cada ${settings.rotationMinutes} min" else "desligada") } ?: "Arte padrão do Spectra"
                        }
                    }
                }
            }
        }
    }
    about?.let { text ->
        SpectraDialog({ about = null }) {
            val f = rememberAutoFocus()
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
            PillButton("OK", selected = true, modifier = Modifier.focusRequester(f)) { about = null }
        }
    }
}

@Composable
fun PanelFrame(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(14.dp).width(420.dp).fillMaxHeight().clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(LocalUi.current.surfaceHigh.copy(alpha = 0.96f), Color(0xF20A0820))))
    ) { content() }
}

@Composable
private fun PanelHeader(settings: LauncherSettings) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimalIconView(com.hazagou.spectra.data.AnimalIcon.DRAGON, ui.accent, Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("SPECTRA", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text("launcher", color = ui.accent, style = MaterialTheme.typography.labelMedium)
            }
            Avatar(settings.profileAvatar, settings.profileName, ui.accent, 40.dp)
        }
        Text(
            SimpleDateFormat("EEE, d 'de' MMM 'de' yyyy", Locale("pt", "BR")).format(Date()),
            color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
        )
    }
}

@Composable
private fun GroupTitle(t: String) {
    Text(t, color = LocalUi.current.accent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp))
}

/** Painel de notificações (contador do sino). */
@Composable
fun NotificationsPanel(vm: MainViewModel, visible: Boolean) {
    AnimatedVisibility(visible, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
        BackHandler { vm.notificationsOpen.value = false }
        val notifs by vm.notifications.collectAsState()
        val ctx = LocalContext.current
        val hasAccess = vm.system.hasNotificationAccess()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.CenterEnd) {
            PanelFrame {
                Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔  Notificações", color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Text("${notifs.size}", color = LocalUi.current.accent, style = MaterialTheme.typography.headlineSmall)
                }
                val first = rememberAutoFocus()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!hasAccess) {
                        item {
                            Text("Para mostrar o contador, libere \"Acesso a notificações\" para o Spectra.", color = Spectra.TextSecondary)
                        }
                        item { OptionRow("Liberar acesso", "🔓", Modifier.focusRequester(first)) { if (!vm.system.openNotificationAccess()) toast(ctx, "Use o comando ADB em Personalizar › Launcher") } }
                    } else if (notifs.isEmpty()) {
                        item { Text("Nenhuma notificação 🎉", color = Spectra.TextSecondary) }
                        item { OptionRow("Fechar", "✕", Modifier.focusRequester(first)) { vm.notificationsOpen.value = false } }
                    } else {
                        item { OptionRow("Limpar todas", "🧹", Modifier.focusRequester(first)) { SpectraNotificationService.dismissAll() } }
                        items(notifs, key = { it.key }) { n ->
                            FocusCard(Modifier.fillMaxWidth(), corner = 16.dp, focusScale = 1.02f, glow = false,
                                onLongClick = { SpectraNotificationService.dismiss(n.key) },
                                onClick = {
                                    runCatching { n.contentIntent?.send() }
                                    vm.notificationsOpen.value = false
                                }) { f ->
                                Row(Modifier.fillMaxWidth().background(if (f) LocalUi.current.accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f)).padding(12.dp)) {
                                    AppIconSmall(n.packageName, 34.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row {
                                            Text(vm.appsRepo.label(n.packageName), color = Spectra.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                            Text(DateUtils.getRelativeTimeSpanString(n.time).toString(), color = Spectra.TextSecondary, style = MaterialTheme.typography.labelMedium)
                                        }
                                        if (n.title.isNotBlank()) Text(n.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (n.text.isNotBlank()) Text(n.text, color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        item { Text("Segure OK numa notificação para dispensá-la", color = Spectra.TextSecondary, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

/** Perfil (avatar + nome) do canto superior direito. */
@Composable
fun ProfileDialog(vm: MainViewModel, settings: LauncherSettings) {
    var renaming by remember { mutableStateOf(false) }
    val ui = LocalUi.current
    val first = rememberAutoFocus()
    SpectraDialog({ vm.profileOpen.value = false }, width = 640.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(settings.profileAvatar, settings.profileName, ui.accent, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(settings.profileName, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text("Perfil do Spectra", color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        OptionRow("Nome", "T", Modifier.focusRequester(first), value = settings.profileName) { renaming = true }
        Text("Avatar", color = Spectra.TextSecondary, style = MaterialTheme.typography.titleSmall)
        LazyVerticalGrid(GridCells.Fixed(8), Modifier.heightIn(max = 180.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AVATAR_OPTIONS) { a ->
                FocusCard(Modifier.size(56.dp), corner = 28.dp, focusScale = 1.12f,
                    background = if (a == settings.profileAvatar) ui.accent.copy(alpha = 0.5f) else Color.Transparent,
                    onClick = { vm.launch { setProfileAvatar(a) } }) { _ ->
                    Box(Modifier.fillMaxSize().padding(3.dp)) { Avatar(a, settings.profileName, ui.accent, 50.dp) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(if (settings.showProfile) "Mostrar no topo: sim" else "Mostrar no topo: não", leading = "👁") { vm.launch { setShowProfile(!settings.showProfile) } }
            PillButton("Fechar", leading = "✔", selected = true) { vm.profileOpen.value = false }
        }
        Spacer(Modifier.height(2.dp))
    }
    if (renaming) TextEditDialog("Seu nome", settings.profileName, onSave = { n -> if (n.isNotBlank()) vm.launch { setProfileName(n.trim().take(24)) } }, onDismiss = { renaming = false })
}

