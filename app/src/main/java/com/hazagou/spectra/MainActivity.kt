package com.hazagou.spectra

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.hazagou.spectra.data.AnimalIcon
import com.hazagou.spectra.data.TvContentRepository
import com.hazagou.spectra.ui.apps.AppsScreen
import com.hazagou.spectra.ui.components.AnimalIconView
import com.hazagou.spectra.ui.components.LocalParallax
import com.hazagou.spectra.ui.components.ParallaxState
import com.hazagou.spectra.ui.components.PillButton
import com.hazagou.spectra.ui.components.WallpaperBackground
import com.hazagou.spectra.ui.home.HomeScreen
import com.hazagou.spectra.ui.home.NotificationsPanel
import com.hazagou.spectra.ui.home.ProfileDialog
import com.hazagou.spectra.ui.home.SidePanel
import com.hazagou.spectra.ui.sections.SectionsScreen
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.hazagou.spectra.ui.home.LocalAppRepo
import com.hazagou.spectra.ui.home.toast
import com.hazagou.spectra.ui.settings.LauncherActions
import com.hazagou.spectra.ui.settings.SettingsScreen
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra
import com.hazagou.spectra.ui.theme.SpectraTheme
import com.hazagou.spectra.ui.theme.toUiConfig
import com.hazagou.spectra.ui.wallpaper.WallpaperScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), LauncherActions {

    companion object {
        const val EXTRA_FROM_HOME_OVERRIDE = "from_home_override"
        const val EXTRA_OPEN = "open"
        /** Activity visível agora (o controle remoto injeta as teclas nela). */
        @Volatile var foreground: java.lang.ref.WeakReference<MainActivity>? = null
            private set
        private val GOOGLE_TV_LAUNCHERS = listOf("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher")
    }

    private val vm: MainViewModel by viewModels()
    private var showLauncherHelp by mutableStateOf(false)
    private var epgAskedThisSession = false

    private val epgPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[TvContentRepository.PERMISSION_READ_EPG] == true || vm.tvRepo.hasPermission) vm.refresh()
        else toast(this, "Sem permissão: use o comando ADB em Personalizar › Launcher e permissões")
    }

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!isDefaultLauncher()) showLauncherHelp = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Root() }
        handleOpenExtra(intent)
    }

    /** Atalhos vindos de fora (ex.: notificação de atualização): open=updates|remote */
    private fun handleOpenExtra(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            "remote" -> vm.remotePairOpen.value = true
            "updates" -> vm.openSettings("UPDATES")
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = java.lang.ref.WeakReference(this)
        vm.refresh()
        vm.updates.checkIfDue()
    }

    override fun onPause() {
        if (foreground?.get() === this) foreground = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Botão Home pressionado com o launcher já aberto → volta ao topo da tela inicial
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra(EXTRA_FROM_HOME_OVERRIDE, false) ||
            intent.action == Intent.ACTION_MAIN
        ) vm.goHome()
        handleOpenExtra(intent)
    }

    @Composable
    private fun Root() {
        val settings by vm.settings.collectAsState()
        val screen by vm.screen.collectAsState()
        val sideOpen by vm.sidePanelOpen.collectAsState()
        val notifOpen by vm.notificationsOpen.collectAsState()
        val profileOpen by vm.profileOpen.collectAsState()
        val moving by vm.moving.collectAsState()
        val parallax = remember { ParallaxState() }
        var dynamicAccent by remember { mutableStateOf<Color?>(null) }
        var askedThisSession by rememberSaveable { mutableStateOf(false) }
        var showAsk by remember { mutableStateOf(false) }

        val s = settings
        if (s == null) {
            Box(Modifier.fillMaxSize().background(Spectra.Night))
            return
        }

        LaunchedEffect(Unit) {
            delay(900)
            if (!askedThisSession && s.askDefaultLauncher && !isDefaultLauncher() && !isHomeOverrideEnabled()) {
                showAsk = true
            } else maybeAskEpg()
            askedThisSession = true
        }

        val ui = s.toUiConfig(dynamicAccent)
        SpectraTheme(ui, s) {
            CompositionLocalProvider(LocalParallax provides parallax, LocalAppRepo provides vm.appsRepo) {
                Box(
                    Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
                        vm.lastInteraction = System.currentTimeMillis()
                        when {
                            // Qualquer tecla sai do modo ocioso
                            vm.idle.value -> { if (ev.type == KeyEventType.KeyUp) vm.idle.value = false; true }
                            // Tecla MENU (☰) abre/fecha o painel lateral em qualquer tela
                            ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU -> {
                                if (ev.type == KeyEventType.KeyUp) {
                                    vm.notificationsOpen.value = false
                                    val anyPanel = vm.sectionPanel.value != null || vm.channelsOpen.value
                                    vm.sectionPanel.value = null
                                    vm.channelsOpen.value = false
                                    vm.sidePanelOpen.value = if (anyPanel) false else !vm.sidePanelOpen.value
                                }
                                true
                            }
                            else -> false
                        }
                    }
                ) {
                    WallpaperBackground(s, parallax) { dynamicAccent = it }

                    BackHandler(enabled = true) {
                        when {
                            moving != null -> vm.moving.value = null
                            screen != Screen.HOME -> vm.goHome()
                            else -> Unit // na tela inicial o "voltar" não fecha o launcher
                        }
                    }

                    val idle by vm.idle.collectAsState()
                    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(if (idle) 0f else 1f, tween(900), label = "idle")
                    LaunchedEffect(s.idleMinutes) {
                        while (s.idleMinutes > 0) {
                            delay(10_000)
                            val quiet = System.currentTimeMillis() - vm.lastInteraction > s.idleMinutes * 60_000L
                            val panels = vm.sidePanelOpen.value || vm.notificationsOpen.value || vm.sectionPanel.value != null || vm.channelsOpen.value
                            if (quiet && vm.screen.value == Screen.HOME && !panels) vm.idle.value = true
                        }
                    }
                    if (idle) com.hazagou.spectra.ui.home.IdleClock()
                    AnimatedContent(
                        modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                        targetState = screen,
                        transitionSpec = {
                            if (ui.animations) (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(180))
                            else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        },
                        label = "screen"
                    ) { target ->
                        when (target) {
                            Screen.HOME -> HomeScreen(vm, s, onRequestEpgPermission = { requestEpgPermission() })
                            Screen.APPS -> AppsScreen(vm, s)
                            Screen.SETTINGS -> SettingsScreen(vm, s, this@MainActivity)
                            Screen.WALLPAPER -> WallpaperScreen(vm, s)
                            Screen.SECTIONS -> SectionsScreen(vm, s)
                        }
                    }

                    SidePanel(vm, s, this@MainActivity, sideOpen)
                    NotificationsPanel(vm, notifOpen)
                    com.hazagou.spectra.ui.home.SectionPanel(vm, s)
                    com.hazagou.spectra.ui.home.ChannelsPanel(vm, s) { requestEpgPermission() }
                    if (profileOpen) ProfileDialog(vm, s)
                    val remotePair by vm.remotePairOpen.collectAsState()
                    if (remotePair) com.hazagou.spectra.ui.remote.RemotePairDialog(vm) { vm.remotePairOpen.value = false }

                    if (showAsk) {
                        DefaultLauncherDialog(
                            googleTv = isGoogleTv(),
                            onYes = { showAsk = false; requestDefaultLauncher() },
                            onOverride = { showAsk = false; openAccessibilitySettings() },
                            onLater = { showAsk = false; maybeAskEpg() },
                            onNever = {
                                showAsk = false
                                lifecycleScope.launch { vm.settingsRepo.setAskDefaultLauncher(false) }
                                maybeAskEpg()
                            }
                        )
                    }
                    if (showLauncherHelp) {
                        LauncherHelpDialog(
                            googleTv = isGoogleTv(),
                            onOverride = { showLauncherHelp = false; openAccessibilitySettings() },
                            onSettings = { showLauncherHelp = false; openHomeSettings() },
                            onClose = { showLauncherHelp = false; maybeAskEpg() }
                        )
                    }
                }
            }
        }
    }

    private fun maybeAskEpg() {
        if (epgAskedThisSession) return
        epgAskedThisSession = true
        if (vm.tvRepo.hasProvider && !vm.tvRepo.hasPermission) requestEpgPermission()
    }

    // ------------------------------------------------------------------ LauncherActions

    override fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val res = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == packageName
    }

    override fun requestDefaultLauncher() {
        if (isDefaultLauncher()) { toast(this, "O Spectra já é o launcher padrão ✔"); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                val ok = runCatching { roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)); true }.getOrDefault(false)
                if (ok) return
            }
        }
        if (!openHomeSettings()) showLauncherHelp = true
    }

    private fun openHomeSettings(): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
        )
        for (i in candidates) {
            if (i.resolveActivity(packageManager) != null && runCatching { startActivity(i); true }.getOrDefault(false)) return true
        }
        return false
    }

    override fun isHomeOverrideEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    override fun openAccessibilitySettings() {
        val ok = runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); true }.getOrDefault(false)
        if (ok) toast(this, "Ative \"Spectra — Botão Home e controle\" na lista de acessibilidade")
        else toast(this, "Abra Configurações › Acessibilidade e ative \"Spectra — Botão Home e controle\"")
    }

    override fun requestEpgPermission() {
        if (!vm.tvRepo.hasProvider) { toast(this, "Esta TV não tem o provedor de canais do Android TV"); return }
        if (vm.tvRepo.hasPermission) { vm.refresh(); toast(this, "Permissão já concedida ✔"); return }
        epgPermission.launch(arrayOf(TvContentRepository.PERMISSION_READ_EPG, "android.permission.READ_TV_LISTINGS"))
    }

    override fun isGoogleTv(): Boolean =
        runCatching { packageManager.getPackageInfo(GOOGLE_TV_LAUNCHERS.first(), 0); true }.getOrDefault(false)
}

// ================================================================================ diálogos

@Composable
private fun DialogFrame(content: @Composable () -> Unit) {
    Column(
        Modifier.width(620.dp).clip(RoundedCornerShape(28.dp)).background(Spectra.SurfaceHigh).padding(30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
private fun DefaultLauncherDialog(
    googleTv: Boolean,
    onYes: () -> Unit,
    onOverride: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
    Dialog(onDismissRequest = onLater) {
        DialogFrame {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimalIconView(AnimalIcon.DRAGON, LocalUi.current.accent, Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Text("Definir o Spectra como tela inicial?", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Assim ele abre sempre que você apertar o botão Home do controle, no lugar da tela inicial original.",
                color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium
            )
            if (googleTv) Text(
                "Detectamos Google TV: ele não permite trocar o launcher padrão. Use \"Substituir botão Home\" (acessibilidade) — funciona igual.",
                color = Spectra.Lilac, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (googleTv) {
                    PillButton("Substituir botão Home", leading = "🏠", selected = true, modifier = Modifier.focusRequester(fr), onClick = onOverride)
                    PillButton("Tentar como padrão", leading = "✔", onClick = onYes)
                } else {
                    PillButton("Sim, definir como padrão", leading = "✔", selected = true, modifier = Modifier.focusRequester(fr), onClick = onYes)
                }
                PillButton("Agora não", onClick = onLater)
                PillButton("Não perguntar", onClick = onNever)
            }
        }
    }
}

@Composable
private fun LauncherHelpDialog(googleTv: Boolean, onOverride: () -> Unit, onSettings: () -> Unit, onClose: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
    Dialog(onDismissRequest = onClose) {
        DialogFrame {
            Text("Não foi possível definir automaticamente", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(
                if (googleTv) "No Google TV use a substituição do botão Home (acessibilidade)."
                else "Abra Configurações › Apps › Apps padrão › Tela inicial e escolha Spectra Launcher, ou use a substituição do botão Home.",
                color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Substituir botão Home", leading = "🏠", selected = true, modifier = Modifier.focusRequester(fr), onClick = onOverride)
                if (!googleTv) PillButton("Abrir configurações", leading = "⚙", onClick = onSettings)
                PillButton("Fechar", onClick = onClose)
            }
        }
    }
}
