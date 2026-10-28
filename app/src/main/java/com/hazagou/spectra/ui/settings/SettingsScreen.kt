package com.hazagou.spectra.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazagou.spectra.BuildConfig
import com.hazagou.spectra.MainViewModel
import com.hazagou.spectra.Screen
import com.hazagou.spectra.data.AnimalIcon
import com.hazagou.spectra.data.CardStyle
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.data.PerformanceMode
import com.hazagou.spectra.data.SectionKind
import com.hazagou.spectra.data.TvContentRepository
import com.hazagou.spectra.data.WallpaperMotion
import com.hazagou.spectra.data.WallpaperRepository
import com.hazagou.spectra.ui.components.AnimalIconView
import com.hazagou.spectra.ui.components.FocusCard
import com.hazagou.spectra.ui.components.PillButton
import com.hazagou.spectra.ui.components.TextEditDialog
import com.hazagou.spectra.ui.home.AppIconSmall
import com.hazagou.spectra.ui.home.toast
import com.hazagou.spectra.ui.sections.EmojiPickerDialog
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ações que dependem da Activity (papéis do sistema, permissões, acessibilidade). */
interface LauncherActions {
    fun isDefaultLauncher(): Boolean
    fun requestDefaultLauncher()
    fun isHomeOverrideEnabled(): Boolean
    fun openAccessibilitySettings()
    fun requestEpgPermission()
    fun isGoogleTv(): Boolean
    /** Abre "Informações do app" do Spectra (Permissões) nas configurações do Android. */
    fun openAppSettings()
}

private enum class Cat(val label: String, val icon: String, val description: String, val preview: Boolean) {
    PROFILE("Perfil e título", "✦", "Nome, avatar, título, emoji e ícone especial da tela inicial.", true),
    LOOK("Cartões", "▭", "Estilo, tamanho e cantos dos cartões.", true),
    COLORS("Cores", "🎨", "Cor de destaque (64 cores, temas prontos, #HEX), cores dos menus, do título e tom dos painéis.", true),
    FONT("Fonte", "A", "Família da fonte, negrito, itálico e tamanho do texto.", true),
    LAYOUT("Layout e foco", "▤", "Nomes das seções, espaçamentos, margens, opacidade e comportamento do foco.", true),
    STATUS("Barra de status", "📶", "Wi-Fi, notificações, engrenagem, bateria, relógio e avatar no canto superior direito.", true),
    WALL("Wallpaper e fundo", "🖼", "Wallpaper (imagem, GIF ou vídeo), fundo em cor/degradê, movimento, efeitos e troca automática.", true),
    SECTIONS("Seções", "☰", "Criar, ordenar e personalizar as seções da tela inicial.", false),
    CHANNELS("Canais", "📺", "Ver a seguir e cada canal dos apps como fileira própria.", false),
    GENERAL("Geral", "⚙", "Modo ocioso e comportamento geral.", false),
    CONTENT("Canais e continuar", "🎬", "Quais apps mostram Canais, Continuar assistindo e Recomendados.", false),
    INPUTS("Entradas", "🔌", "HDMI, AV e Cast/Espelhamento.", false),
    PERF("Desempenho", "⚡", "Animações, qualidade das imagens e itens por fileira.", false),
    LAUNCHER("Launcher e permissões", "🏠", "Launcher padrão, botão Home, canais e notificações.", false),
    REMOTE("Controle remoto", "📱", "Use o celular como controle: defina um PIN, escaneie o QR code e controle a TV na mesma rede Wi-Fi.", false),
    UPDATES("Atualizações", "⟳", "Baixe e instale a versão mais recente publicada no GitHub, sempre que quiser.", false),
    BACKUP("Backup", "💾", "Salve ou restaure todas as configurações em um arquivo (pendrive, nuvem…).", false),
    ABOUT("Sobre", "ℹ", "Spectra Launcher por Heitor Campos (github.com/heitorcampos2).", false),
}

val EMOJIS = listOf(
    "", "👻", "🔥", "⭐", "🌙", "💜", "⚡", "🎬", "🍿", "📺", "🎮", "🎧", "🚀", "🌌", "🌸", "💎",
    "🐉", "🐱", "🐶", "🦁", "🐯", "🦊", "🐼", "🦄", "👾", "🤖", "☕", "🏆", "❤️", "✨", "🌊", "🍕",
)

@Composable
fun SettingsScreen(vm: MainViewModel, settings: LauncherSettings, actions: LauncherActions) {
    var cat by remember { mutableStateOf<Cat?>(null) }
    var lastCat by remember { mutableStateOf(Cat.PROFILE) }
    val apps by vm.apps.collectAsState()
    BackHandler(enabled = cat != null) { cat = null }
    val deepLink by vm.settingsDeepLink.collectAsState()
    LaunchedEffect(deepLink) {
        deepLink?.let { name -> Cat.entries.firstOrNull { it.name == name }?.let { cat = it; lastCat = it } }
        vm.settingsDeepLink.value = null
    }

    Row(Modifier.fillMaxSize()) {
        // --------------------------------------------------- esquerda: título + prévia ao vivo
        val shown = cat ?: lastCat
        Column(Modifier.weight(0.5f).fillMaxHeight().padding(start = 56.dp, top = 44.dp, end = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)).background(LocalUi.current.accent),
                    contentAlignment = Alignment.Center
                ) { Text(shown.icon, fontSize = 40.sp, color = Color.White) }
                Spacer(Modifier.width(22.dp))
                Column {
                    Text("Configurações", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(if (cat == null) "Spectra Launcher" else shown.label, color = LocalUi.current.accent, style = MaterialTheme.typography.displaySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (cat == null) "Escolha uma categoria. As mudanças aparecem na prévia na hora." else shown.description,
                color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(26.dp))
            if (shown.preview || cat == null) LivePreview(settings, apps, Modifier.padding(end = 20.dp))
        }
        // --------------------------------------------------- direita: lista / opções
        Box(
            Modifier.weight(0.5f).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(LocalUi.current.surface.copy(alpha = 0.55f), LocalUi.current.surfaceHigh)))
        ) {
            AnimatedContent(
                targetState = cat,
                transitionSpec = { (fadeIn() + slideInHorizontally { it / 6 }) togetherWith fadeOut() },
                label = "settingsPane"
            ) { c ->
                if (c == null) CategoryList(onPick = { cat = it; lastCat = it }, onFocusCat = { lastCat = it }, initial = lastCat, vm = vm)
                else CategoryPane(c, vm, settings, actions, onBack = { cat = null })
            }
        }
    }
}

@Composable
private fun CategoryList(onPick: (Cat) -> Unit, onFocusCat: (Cat) -> Unit, initial: Cat, vm: MainViewModel) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 44.dp, top = 40.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Cat.entries.toList()) { c ->
            NavRow(c.label, c.icon, Modifier.then(if (c == initial) Modifier.focusRequester(fr) else Modifier), onFocus = { onFocusCat(c) }) { onPick(c) }
        }
        item { NavRow("Voltar ao início", "←", Modifier, onFocus = {}) { vm.goHome() } }
    }
}

@Composable
private fun NavRow(label: String, icon: String, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val accent = LocalUi.current.accent
    FocusCard(modifier.fillMaxWidth(), corner = 16.dp, focusScale = 1.03f, glow = false, background = Color.Transparent, onFocus = onFocus, onClick = onClick) { f ->
        Row(Modifier.fillMaxWidth().background(if (f) Color.White.copy(alpha = 0.08f) else Color.Transparent).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(if (f) accent else accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 20.sp, color = Color.White)
            }
            Spacer(Modifier.width(18.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("▸", color = Color.White.copy(alpha = if (f) 1f else 0.5f), fontSize = 18.sp)
        }
    }
}

// ============================================================================ blocos

private fun LazyListScope.header(onBack: () -> Unit, fr: FocusRequester) {
    item { NavRow("Voltar", "‹", Modifier.focusRequester(fr), onFocus = {}) { onBack() } }
}

@Composable
fun SettingRow(title: String, value: String? = null, description: String? = null, onAdjust: ((Int) -> Unit)? = null, onClick: () -> Unit) {
    val accent = LocalUi.current.accent
    FocusCard(Modifier.fillMaxWidth(), corner = 16.dp, focusScale = 1.02f, glow = false, background = Color.White.copy(alpha = 0.04f),
        onAdjust = onAdjust, onClick = onClick) { f ->
        Row(
            Modifier.fillMaxWidth().background(if (f) accent.copy(alpha = 0.85f) else Color.Transparent).padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (description != null) Text(description, color = if (f) Color.White.copy(alpha = 0.85f) else Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (value != null) {
                if (onAdjust != null && f) Text("◀ ", color = Color.White, fontSize = 13.sp)
                Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall)
                if (onAdjust != null && f) Text(" ▶", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Toggle(title: String, on: Boolean, description: String? = null, onChange: (Boolean) -> Unit) =
    SettingRow(title, if (on) "● Ligado" else "○ Desligado", description) { onChange(!on) }

/** Linha com valor numérico: ◀ ▶ ajustam, OK avança. */
@Composable
private fun Stepper(title: String, value: Int, range: IntRange, step: Int, suffix: String = "", description: String? = null, onSet: (Int) -> Unit) {
    fun clamp(v: Int) = v.coerceIn(range.first, range.last)
    SettingRow(title, "$value$suffix", description, onAdjust = { d -> onSet(clamp(value + d * step)) }) {
        onSet(if (value + step > range.last) range.first else value + step)
    }
}

private fun <T> List<T>.next(cur: T): T = this[(indexOf(cur) + 1).mod(size)]

@Composable
private fun Group(t: String) {
    Text(t, color = LocalUi.current.accent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, start = 6.dp))
}

// ============================================================================ painel por categoria

@Composable
private fun CategoryPane(c: Cat, vm: MainViewModel, s: LauncherSettings, actions: LauncherActions, onBack: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(c) { delay(80); runCatching { fr.requestFocus() } }
    var dialog by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val allContent by vm.allContentApps.collectAsState()
    val tv by vm.tvState.collectAsState()
    val remoteCfg by vm.remote.config.collectAsState()
    val remoteRunning by com.hazagou.spectra.remote.RemoteServer.running.collectAsState()
    val autoUpdate by vm.updates.autoCheck.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = vm.settingsRepo.exportJson()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess { toast(ctx, "Backup salvo ✔") }.onFailure { toast(ctx, "Falha ao salvar o backup") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("vazio")
                vm.settingsRepo.importJson(json)
            }.onSuccess { toast(ctx, "Configurações restauradas ✔") }.onFailure { toast(ctx, "Arquivo de backup inválido") }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = 44.dp, top = 40.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        header(onBack, fr)
        when (c) {
            Cat.PROFILE -> {
                item { SettingRow("Seu nome", s.profileName, "Aparece com o avatar no canto superior direito") { dialog = "profileName" } }
                item { SettingRow("Avatar", "Escolher ›") { vm.profileOpen.value = true } }
                item { Toggle("Mostrar avatar no topo", s.showProfile) { vm.launch { setShowProfile(it) } } }
                item { Group("Título") }
                item { SettingRow("Texto do título", s.title, "Ex.: TV, Sala, Quarto…") { dialog = "title" } }
                item { Toggle("Mostrar título", s.showTitle) { vm.launch { setShowTitle(it) } } }
                item {
                    SettingRow("Fonte do título", s.titleFont.label, "Só o título da tela inicial (o resto segue Personalizar › Fonte)",
                        onAdjust = { d -> vm.launch { setTitleFont(com.hazagou.spectra.data.FontChoice.entries.let { l -> l[(l.indexOf(s.titleFont) + d).mod(l.size)] }) } }) {
                        vm.launch { setTitleFont(com.hazagou.spectra.data.FontChoice.entries.toList().next(s.titleFont)) }
                    }
                }
                item { Toggle("Título em negrito", s.titleBold) { vm.launch { setTitleBold(it) } } }
                item { Stepper("Tamanho do título", s.titleScale, 60..180, 10, "%") { v -> vm.launch { setTitleScale(v) } } }
                item { SettingRow("Cor do título", if (s.titleColor == 0L) "Degradê automático" else "#%06X".format(s.titleColor and 0xFFFFFFL),
                    "Escolha uma cor fixa ou ✕ para o degradê automático") { dialog = "titleColor" } }
                item { SettingRow("Emoji do título", s.titleEmoji.ifBlank { "nenhum" }) { dialog = "emoji" } }
                item { Text("Ícone especial", color = Spectra.TextSecondary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)) {
                        items(AnimalIcon.entries.toList()) { a ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FocusCard(Modifier.size(84.dp), corner = 20.dp,
                                    background = if (a == s.titleAnimal) LocalUi.current.accent.copy(alpha = 0.35f) else Spectra.Surface,
                                    onClick = { vm.launch { setTitleAnimal(a) } }) { _ ->
                                    if (a == AnimalIcon.NONE) Text("∅", fontSize = 28.sp, color = Color.White, modifier = Modifier.align(Alignment.Center))
                                    else AnimalIconView(a, LocalUi.current.accent, Modifier.fillMaxSize().padding(10.dp))
                                }
                                Text(a.label, color = Spectra.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            Cat.LOOK -> {
                item { Toggle("Cor de destaque automática", s.autoAccent, "Extrai a cor do wallpaper atual") { vm.launch { setAutoAccent(it) } } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Spectra.AccentPresets.forEach { col ->
                            FocusCard(Modifier.size(42.dp), corner = 21.dp, background = Color(col), onClick = { vm.launch { setAccent(col) } }) { _ ->
                                if (!s.autoAccent && s.accent == col) Box(Modifier.size(12.dp).align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)))
                            }
                        }
                    }
                }
                item { SettingRow("Estilo dos cartões", s.cardStyle.label, "Padrão para as seções (cada seção pode ter o seu)",
                    onAdjust = { d -> vm.launch { setCardStyle(CardStyle.entries.let { l -> l[(l.indexOf(s.cardStyle) + d).mod(l.size)] }) } }) {
                    vm.launch { setCardStyle(CardStyle.entries.toList().next(s.cardStyle)) }
                } }
                item { SettingRow("Tamanho dos cartões", com.hazagou.spectra.ui.home.sizeLabel(s.cardSize), "PPP, PP, P, M, G, GG ou XL • cada seção e cada cartão podem ter o seu",
                    onAdjust = { d -> vm.launch { setCardSize(s.cardSize + d) } }) {
                    vm.launch { setCardSize(if (s.cardSize >= com.hazagou.spectra.ui.home.MAX_SIZE) com.hazagou.spectra.ui.home.MIN_SIZE else s.cardSize + 1) } } }
                item { Stepper("Cantos arredondados", s.corner, 0..32, 2, " dp") { v -> vm.launch { setCorner(v) } } }
                item { Toggle("Nome do app ao focar", s.showLabels) { vm.launch { setShowLabels(it) } } }
            }
            Cat.LAYOUT -> {
                item { Toggle("Cabeçalhos à esquerda", s.leftHeaders, "Ícones das seções à esquerda com ⚙ e ⇅") { vm.launch { setLeftHeaders(it) } } }
                item { Toggle("Exibir o nome das seções", s.showSectionTitles) { vm.launch { setShowSectionTitles(it) } } }
                item { Toggle("Exibir ícone antes do nome", s.showSectionIcons) { vm.launch { setShowSectionIcons(it) } } }
                item { Stepper("Espaçamento vertical", s.rowSpacing, 0..48, 4, " dp") { v -> vm.launch { setRowSpacing(v) } } }
                item { Toggle("Manter o foco em posição fixa", s.fixedFocus, "A fileira selecionada fica sempre na mesma altura") { vm.launch { setFixedFocus(it) } } }
                item { Toggle("Ocultar fileiras acima da seleção", s.hideRowsAbove) { vm.launch { setHideRowsAbove(it) } } }
                item { Stepper("Opacidade das fileiras não selecionadas", (s.unselectedRowAlpha * 100).toInt(), 10..100, 5, "%") { v -> vm.launch { setUnselectedRowAlpha(v / 100f) } } }
                item { Stepper("Margem inicial", s.startMargin, 16..120, 8, " dp") { v -> vm.launch { setStartMargin(v) } } }
                item { Stepper("Espaçamento dos cartões", s.cardSpacing, 6..40, 2, " dp") { v -> vm.launch { setCardSpacing(v) } } }
            }
            Cat.STATUS -> {
                item { Toggle("Wi-Fi / rede", s.showWifi, "Intensidade do sinal; OK abre as configurações de rede") { vm.launch { setShowWifi(it) } } }
                item { Toggle("Notificações (com contador)", s.showNotifications) { vm.launch { setShowNotifications(it) } } }
                item { Toggle("Engrenagem (configurações do sistema)", s.showGear, "Segure OK para abrir as configurações do Spectra") { vm.launch { setShowGear(it) } } }
                item { Toggle("Bateria (aparelhos com bateria)", s.showBattery) { vm.launch { setShowBattery(it) } } }
                item { Toggle("Botão de busca", s.showSearch) { vm.launch { setShowSearch(it) } } }
                item { Toggle("Relógio", s.showClock) { vm.launch { setShowClock(it) } } }
                item { Toggle("Avatar do perfil", s.showProfile) { vm.launch { setShowProfile(it) } } }
                item { Toggle("Ocultar barra ao rolar", s.hideStatusOnScroll) { vm.launch { setHideStatusOnScroll(it) } } }
                item {
                    SettingRow("Acesso a notificações", if (vm.system.hasNotificationAccess()) "✔ Concedido" else "Não concedido ›") {
                        if (!vm.system.openNotificationAccess()) toast(ctx, "Use o comando ADB em Launcher e permissões")
                    }
                }
            }
            Cat.WALL -> {
                item { SettingRow("Escolher wallpaper", "Abrir ›", "Categorias, classificação, Google, galeria (foto, GIF, vídeo)") { vm.open(Screen.WALLPAPER) } }
                item { Group("Fundo (cor sólida ou degradê, quando não usar wallpaper)") }
                item {
                    SettingRow("Tipo de fundo", s.bgMode.label, "Wallpaper, cor sólida ou degradê de 2 cores",
                        onAdjust = { d -> vm.launch { setBgMode(com.hazagou.spectra.data.BgMode.entries.let { l -> l[(l.indexOf(s.bgMode) + d).mod(l.size)] }) } }) {
                        vm.launch { setBgMode(com.hazagou.spectra.data.BgMode.entries.toList().next(s.bgMode)) }
                    }
                }
                item { SettingRow("Cor de fundo 1", "#%06X".format(s.bgColor1 and 0xFFFFFFL), "Usada no fundo sólido e no início do degradê") { dialog = "bg1" } }
                item { SettingRow("Cor de fundo 2", "#%06X".format(s.bgColor2 and 0xFFFFFFL), "Final do degradê") { dialog = "bg2" } }
                item { SettingRow("Movimento", s.wallpaperMotion.label, onAdjust = { d -> vm.launch { setWallpaperMotion(WallpaperMotion.entries.let { l -> l[(l.indexOf(s.wallpaperMotion) + d).mod(l.size)] }) } }) {
                    vm.launch { setWallpaperMotion(WallpaperMotion.entries.toList().next(s.wallpaperMotion)) }
                } }
                item { SettingRow("Transição entre wallpapers", s.wallTransition.label, "Animação ao trocar de imagem (manual ou troca automática)",
                    onAdjust = { d -> vm.launch { setWallTransition(com.hazagou.spectra.data.WallTransition.entries.let { l -> l[(l.indexOf(s.wallTransition) + d).mod(l.size)] }) } }) {
                    vm.launch { setWallTransition(com.hazagou.spectra.data.WallTransition.entries.toList().next(s.wallTransition)) }
                } }
                item { Stepper("Intensidade do parallax", s.parallaxStrength, 1..3, 1) { v -> vm.launch { setParallaxStrength(v) } } }
                item { Toggle("Efeito animado por cima da imagem", s.particles) { vm.launch { setParticles(it) } } }
                item {
                    SettingRow("Tipo de efeito", s.particleStyle.label, "Pontos de luz, neve, estrelas, bokeh, vaga-lumes ou chuva",
                        onAdjust = { d -> vm.launch { setParticles(true); setParticleStyle(com.hazagou.spectra.data.ParticleStyle.entries.let { l -> l[(l.indexOf(s.particleStyle) + d).mod(l.size)] }) } }) {
                        vm.launch { setParticles(true); setParticleStyle(com.hazagou.spectra.data.ParticleStyle.entries.toList().next(s.particleStyle)) }
                    }
                }
                item { Stepper("Escurecimento", (s.dim * 100).toInt(), 0..80, 5, "%") { v -> vm.launch { setDim(v / 100f) } } }
                item { Toggle("Desfoque suave", s.softBlur) { vm.launch { setSoftBlur(it) } } }
                item { Group("Troca automática") }
                item {
                    val opts = listOf(0, 5, 15, 30, 60, 180)
                    SettingRow("Trocar a cada", if (s.rotationMinutes == 0) "Desligado" else "${s.rotationMinutes} min",
                        onAdjust = { d -> vm.launch { setRotationMinutes(opts[(opts.indexOf(s.rotationMinutes).coerceAtLeast(0) + d).coerceIn(0, opts.lastIndex)]) } }) {
                        vm.launch { setRotationMinutes(opts.next(s.rotationMinutes.takeIf { it in opts } ?: 0)) }
                    }
                }
                item {
                    val sources = listOf("saved") + WallpaperRepository.CATEGORIES.map { it.id }
                    val label = if (s.rotationSource == "saved") "Meus wallpapers" else WallpaperRepository.CATEGORIES.firstOrNull { it.id == s.rotationSource }?.let { "${it.emoji} ${it.label}" } ?: s.rotationSource
                    SettingRow("Fonte", label, "De onde vêm as imagens da troca automática",
                        onAdjust = { d -> vm.launch { setRotationSource(sources[(sources.indexOf(s.rotationSource).coerceAtLeast(0) + d).mod(sources.size)]) } }) {
                        vm.launch { setRotationSource(sources.next(s.rotationSource.takeIf { it in sources } ?: "saved")) }
                    }
                }
                item { Group("Vídeo") }
                item { Stepper("Volume do vídeo de fundo", s.videoVolume, 0..100, 10, "%") { v -> vm.launch { setVideoVolume(v) } } }
                item { Toggle("Aplicar também ao sistema", s.applySystemWallpaper, "Define a mesma imagem como wallpaper do Android") { vm.launch { setApplySystemWallpaper(it) } } }
            }
            Cat.COLORS -> {
                item { Group("Cor de destaque") }
                item {
                    SettingRow("Cor de destaque", if (s.autoAccent) "Automática (wallpaper)" else "#%06X".format(s.accent and 0xFFFFFFL),
                        "Grade com 32 cores, luminosidade, opacidade e cor dinâmica") { dialog = "accent" }
                }
                items(Spectra.AccentPresets.chunked(8)) { rowCols ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
                        rowCols.forEach { col ->
                            FocusCard(Modifier.size(40.dp), corner = 20.dp, background = Color(col), onClick = { vm.launch { setAccent(col) } }) { _ ->
                                if (!s.autoAccent && s.accent == col) Box(Modifier.size(12.dp).align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)))
                            }
                        }
                    }
                }
                item { SettingRow("Cor personalizada", "#HEX ›", "Digite qualquer cor, ex.: FF6A00") { dialog = "hex" } }
                item { Group("Temas prontos") }
                item { Text("Cor de destaque + fundo + painéis de uma vez", color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                        items(Spectra.ColorThemes) { t ->
                            FocusCard(Modifier.width(120.dp).height(84.dp), corner = 16.dp, glow = false,
                                background = Color(t.bg1), onClick = { vm.launch { applyColorTheme(t) }; toast(ctx, "Tema ${t.name} aplicado") }) { _ ->
                                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(t.bg1), Color(t.bg2)))))
                                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(t.emoji, fontSize = 22.sp)
                                    Text(t.name, color = Color(t.accent), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                item { Group("Menus e título") }
                item { SettingRow("Cor dos menus e painéis", if (s.menuColor == 0L) "Pelo tom (abaixo)" else "#%06X".format(s.menuColor and 0xFFFFFFL),
                    "Fundo do menu lateral, configurações, diálogos e cartões") { dialog = "menuColor" } }
                item { SettingRow("Cor do título da tela inicial", if (s.titleColor == 0L) "Degradê automático" else "#%06X".format(s.titleColor and 0xFFFFFFL),
                    "Texto \"${s.title}\" no topo da tela inicial") { dialog = "titleColor" } }
                item { Group("Painéis e cartões") }
                item {
                    SettingRow("Tom dos painéis", s.surfaceTone.label, "Cor de fundo dos cartões, menus e painéis laterais",
                        onAdjust = { d -> vm.launch { setSurfaceTone(com.hazagou.spectra.data.SurfaceTone.entries.let { l -> l[(l.indexOf(s.surfaceTone) + d).mod(l.size)] }) } }) {
                        vm.launch { setSurfaceTone(com.hazagou.spectra.data.SurfaceTone.entries.toList().next(s.surfaceTone)) }
                    }
                }
            }
            Cat.FONT -> {
                item {
                    SettingRow("Fonte", s.font.label, "Padrão, sans-serif (fina, leve, média), serif, monoespaçada, cursiva…",
                        onAdjust = { d -> vm.launch { setFont(com.hazagou.spectra.data.FontChoice.entries.let { l -> l[(l.indexOf(s.font) + d).mod(l.size)] }) } }) {
                        vm.launch { setFont(com.hazagou.spectra.data.FontChoice.entries.toList().next(s.font)) }
                    }
                }
                items(com.hazagou.spectra.data.FontChoice.entries.toList()) { fc ->
                    val fam = when (fc) {
                        com.hazagou.spectra.data.FontChoice.SERIF -> FontFamily.Serif
                        com.hazagou.spectra.data.FontChoice.MONO -> FontFamily.Monospace
                        com.hazagou.spectra.data.FontChoice.CURSIVE -> FontFamily.Cursive
                        else -> FontFamily.SansSerif
                    }
                    FocusCard(Modifier.fillMaxWidth(), corner = 14.dp, focusScale = 1.02f, glow = false,
                        background = if (fc == s.font) LocalUi.current.accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.04f),
                        onClick = { vm.launch { setFont(fc) } }) { _ ->
                        Text(fc.label + if (fc == s.font) "   ✔" else "", color = Color.White, fontFamily = fam, fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                }
                item { Toggle("Negrito (audacioso)", s.fontBold) { vm.launch { setFontBold(it) } } }
                item { Toggle("Itálico", s.fontItalic) { vm.launch { setFontItalic(it) } } }
                item { Stepper("Tamanho do texto", (s.fontScale * 100).toInt(), 80..140, 5, "%") { v -> vm.launch { setFontScale(v / 100f) } } }
            }
            Cat.CHANNELS -> {
                item { SettingRow("Editar canais", "Abrir ›", "Ligue/desligue \"Ver a seguir\" e cada canal dos apps; segure OK para configurar") {
                    vm.goHome(); vm.channelsOpen.value = true
                } }
                item { Text("Dica: na tela inicial, use o ⚙ ao lado do ícone da seção (cabeçalhos à esquerda) ou segure OK num cartão para abrir a configuração lateral da seção: nome, ícone, grade, linha deslizante, tamanho, ordenar e posição.",
                    color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
            Cat.GENERAL -> {
                item {
                    val opts = listOf(0, 1, 2, 5, 10, 30)
                    SettingRow("Modo ocioso", if (s.idleMinutes == 0) "Desligado" else "após ${s.idleMinutes} min",
                        "Esconde tudo e mostra só o wallpaper e um relógio grande",
                        onAdjust = { d -> vm.launch { setIdleMinutes(opts[(opts.indexOf(s.idleMinutes).coerceAtLeast(0) + d).coerceIn(0, opts.lastIndex)]) } }) {
                        vm.launch { setIdleMinutes(opts.next(s.idleMinutes.takeIf { it in opts } ?: 0)) }
                    }
                }
                item { Toggle("Cabeçalhos à esquerda", s.leftHeaders, "Ícones das seções à esquerda com ⚙ configurar e ⇅ mover") { vm.launch { setLeftHeaders(it) } } }
            }
            Cat.SECTIONS -> {
                item { SettingRow("Editar seções", "Abrir ›", "Criar, renomear, mover, esconder e personalizar") { vm.open(Screen.SECTIONS) } }
                item { SettingRow("Restaurar seções padrão", "⟲") { vm.sectionsDo { resetDefaults() }; toast(ctx, "Seções restauradas") } }
            }
            Cat.CONTENT -> {
                when (tv) {
                    TvContentRepository.State.NoPermission -> item { SettingRow("Permitir leitura de canais", "Conceder ›") { actions.requestEpgPermission() } }
                    TvContentRepository.State.NoProvider -> item { Text("Este aparelho não tem o provedor de canais do Android TV.", color = Spectra.TextSecondary) }
                    else -> Unit
                }
                item { Stepper("Itens por fileira", s.maxProgramsPerRow, 5..50, 5) { v -> vm.launch { setMaxPrograms(v) } } }
                if (allContent.isEmpty()) item {
                    Text("Nenhum app publicou conteúdo ainda. Abra SmartTube, Crunchyroll, Sintonia, YouTube etc. e assista algo.", color = Spectra.TextSecondary)
                }
                items(allContent, key = { it.app.packageName }) { sec ->
                    val pkg = sec.app.packageName
                    val enabled = s.isAppContentEnabled(pkg)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Spectra.Surface)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIconSmall(pkg, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(sec.app.label, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            PillButton("↑") { vm.moveSection(pkg, -1) }
                            Spacer(Modifier.width(6.dp))
                            PillButton("↓") { vm.moveSection(pkg, 1) }
                            Spacer(Modifier.width(6.dp))
                            PillButton(if (enabled) "Visível" else "Oculto", selected = enabled) { vm.launch { setSectionDisabled("$pkg|*", enabled) } }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionKind.entries.forEach { k ->
                                val count = when (k) {
                                    SectionKind.CHANNELS -> sec.content.channels.size
                                    SectionKind.CONTINUE -> sec.content.continueItems.size
                                    SectionKind.RECOMMENDED -> sec.content.recommended.size
                                }
                                val on = s.isSectionEnabled(pkg, k)
                                PillButton("${k.label} ($count)", leading = if (on) "✔" else "✕", selected = on) {
                                    vm.launch { setSectionDisabled("$pkg|${k.key}", on) }
                                }
                            }
                        }
                    }
                }
            }
            Cat.INPUTS -> {
                item { Text(if (vm.system.hasPhysicalInputs) "Entradas físicas detectadas nesta TV." else "Este aparelho não informa entradas físicas (TV box, emulador).", color = Spectra.TextSecondary) }
                item { Toggle("Mostrar entradas de exemplo", s.inputsDemo, "Útil para apresentação em emulador/VM") { vm.launch { setInputsDemo(it) } } }
                item { SettingRow("Cartão Cast/Espelhamento", s.castPackage.takeIf { it.isNotBlank() }?.let { vm.appsRepo.label(it) } ?: "Automático",
                    "Segure OK no cartão Cast para escolher o app") { toast(ctx, "Na tela inicial, segure OK no cartão Cast") } }
                item { SettingRow("Renomear entradas", "›", "Segure OK no cartão da entrada (ex.: HDMI 1 → Videogame)") { vm.goHome() } }
            }
            Cat.PERF -> {
                item {
                    SettingRow("Modo", s.performance.label,
                        when (s.performance) {
                            PerformanceMode.MAX_PERFORMANCE -> "Sem animações de fundo e partículas, imagens leves. Ideal para TV box simples."
                            PerformanceMode.BALANCED -> "Animações suaves com imagens otimizadas (recomendado)."
                            PerformanceMode.MAX_QUALITY -> "Wallpaper em 4K e capas em alta resolução. Para TVs potentes."
                        },
                        onAdjust = { d -> vm.launch { setPerformance(PerformanceMode.entries.let { l -> l[(l.indexOf(s.performance) + d).mod(l.size)] }) } }
                    ) { vm.launch { setPerformance(PerformanceMode.entries.toList().next(s.performance)) } }
                }
                item { SettingRow("Atualizar conteúdo agora", "⟳") { vm.refresh(); toast(ctx, "Atualizando…") } }
            }
            Cat.LAUNCHER -> {
                item { SettingRow("Assistente de configuração", "Abrir ›", "Passo a passo: canais, botão Home, notificações e controle") { vm.onboardingOpen.value = true } }
                item { SettingRow("Permissões do app (Android)", "Abrir ›", "Configurações › Apps › Spectra Launcher › Permissões") { actions.openAppSettings() } }
                item { SettingRow("Definir como launcher padrão", if (actions.isDefaultLauncher()) "✔ Padrão" else "Definir ›", "Abre o Spectra ao apertar Home") { actions.requestDefaultLauncher() } }
                item {
                    SettingRow("Substituir botão Home (acessibilidade)", if (actions.isHomeOverrideEnabled()) "✔ Ativo" else "Ativar ›",
                        if (actions.isGoogleTv()) "Recomendado no Google TV" else "Alternativa para TVs que bloqueiam launchers") { actions.openAccessibilitySettings() }
                }
                item { Toggle("Perguntar ao abrir se deve ser o padrão", s.askDefaultLauncher) { vm.launch { setAskDefaultLauncher(it) } } }
                item { SettingRow("Permissão de canais", "Solicitar ›") { actions.requestEpgPermission() } }
                item { SettingRow("Acesso a notificações", if (vm.system.hasNotificationAccess()) "✔ Concedido" else "Liberar ›") { vm.system.openNotificationAccess() } }
                item {
                    val pkg = BuildConfig.APPLICATION_ID
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Spectra.Surface).padding(16.dp)) {
                        Text("Pelo computador (ADB), se a TV não mostrar os diálogos:", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "adb shell pm grant $pkg com.android.providers.tv.permission.READ_EPG_DATA",
                            "adb shell cmd notification allow_listener $pkg/com.hazagou.spectra.service.SpectraNotificationService",
                            "adb shell settings put secure enabled_accessibility_services $pkg/com.hazagou.spectra.service.HomeOverrideService",
                            "adb shell settings put secure accessibility_enabled 1",
                        ).forEach { Text(it, color = Spectra.Lilac, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
                item { SettingRow("Chave da API do Google (opcional)", if (s.googleApiKey.isBlank()) "Não configurada" else "Configurada",
                    "A busca funciona sem chave. Com chave + CX usa a API oficial.") { dialog = "apikey" } }
            }
            Cat.REMOTE -> {
                item { Group("Esta TV") }
                item {
                    Toggle("Permitir controle pelo celular", remoteCfg.enabled && remoteCfg.pin.isNotBlank(),
                        if (remoteRunning) "Ativo em ${com.hazagou.spectra.remote.RemoteServer.localIp() ?: "?"}:${com.hazagou.spectra.remote.RemoteServer.port}" else "Só funciona na sua rede local, protegido por PIN") { on ->
                        if (on && remoteCfg.pin.isBlank()) dialog = "pin" else vm.remote.setEnabled(on)
                    }
                }
                item { SettingRow("PIN", if (remoteCfg.pin.isBlank()) "Definir ›" else "•".repeat(remoteCfg.pin.length), "4 a 8 números. Trocar o PIN desconecta os celulares pareados") { dialog = "pin" } }
                item { SettingRow("Mostrar QR code para parear", "Abrir ›", "Escaneie com o celular (app Spectra ou câmera)") {
                    if (remoteCfg.pin.isBlank()) dialog = "pin" else { vm.remote.setEnabled(true); vm.remotePairOpen.value = true }
                } }
                item { SettingRow("Nome desta TV", remoteCfg.tvName, "Aparece no celular") { dialog = "tvName" } }
                item {
                    SettingRow("Controlar outros apps", if (actions.isHomeOverrideEnabled()) "✔ Ativo" else "Ativar ›",
                        "Acessibilidade \"Spectra — Botão Home e controle\": mover a seleção, OK, voltar e digitar em outros apps") { actions.openAccessibilitySettings() }
                }
                item { SettingRow("Desconectar todos os celulares", "⟲", "Gera um novo código; será preciso escanear de novo") {
                    vm.remote.disconnectAll(); toast(ctx, "Celulares desconectados")
                } }
                item { Group("Este aparelho") }
                item { SettingRow("Controlar outra TV", "Abrir ›", "Transforma este aparelho (celular/tablet) em controle de uma TV com o Spectra") {
                    com.hazagou.spectra.ui.remote.openPhoneRemote(ctx)
                } }
                item { Text("No celular, o ícone \"Spectra Controle\" abre o controle direto.", color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
            Cat.UPDATES -> {
                item { com.hazagou.spectra.ui.remote.UpdateCard(vm) }
                item { Toggle("Verificar ao abrir", autoUpdate, "Procura versão nova no GitHub no máximo a cada 6 horas") { vm.updates.setAutoCheck(it) } }
                item { com.hazagou.spectra.ui.remote.DownloadPageCard() }
                item { Text("As versões são publicadas automaticamente pelo GitHub a cada mudança no projeto. A primeira instalação precisa liberar \"instalar apps desconhecidos\" para o Spectra.",
                    color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
            Cat.BACKUP -> {
                item { SettingRow("Salvar backup", "Exportar ›", "Cria um arquivo .json com tudo: seções, aparência, perfil…") {
                    runCatching { exportLauncher.launch("spectra-backup.json") }.onFailure { toast(ctx, "Seletor de arquivos indisponível") }
                } }
                item { SettingRow("Restaurar backup", "Importar ›", "Escolha um arquivo salvo antes") {
                    runCatching { importLauncher.launch(arrayOf("application/json", "*/*")) }.onFailure { toast(ctx, "Seletor de arquivos indisponível") }
                } }
            }
            Cat.ABOUT -> {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Spectra.Surface).padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimalIconView(if (s.titleAnimal == AnimalIcon.NONE) AnimalIcon.DRAGON else s.titleAnimal, LocalUi.current.accent, Modifier.size(64.dp))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Spectra Launcher", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                Text("Versão ${BuildConfig.VERSION_NAME}  •  Desenvolvedor: Heitor Campos  •  github.com/heitorcampos2", color = Spectra.TextSecondary, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Seções editáveis, seção TV automática, canais e continuar assistindo por app, entradas HDMI/Cast, " +
                                "barra de status com Wi-Fi e notificações, perfis com avatar, wallpapers (imagem, GIF, vídeo, Google Imagens) " +
                                "com parallax e troca automática, prévia ao vivo e backup. Sem anúncios, sem rastreamento.",
                            color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        "title" -> TextEditDialog("Título da tela inicial", s.title, onSave = { t -> vm.launch { setTitle(t.take(40)) } }, onDismiss = { dialog = null })
        "profileName" -> TextEditDialog("Seu nome", s.profileName, onSave = { t -> if (t.isNotBlank()) vm.launch { setProfileName(t.trim().take(24)) } }, onDismiss = { dialog = null })
        "pin" -> TextEditDialog("PIN do controle remoto (4 a 8 números)", "", onSave = { p ->
            if (com.hazagou.spectra.ui.remote.applyPin(vm, ctx, p)) vm.remotePairOpen.value = true
        }, onDismiss = { dialog = null })
        "tvName" -> TextEditDialog("Nome desta TV", remoteCfg.tvName, onSave = { t -> vm.remote.setTvName(t.trim().take(30)) }, onDismiss = { dialog = null })
        "menuColor" -> com.hazagou.spectra.ui.components.ColorPickerDialog(
            title = "Cor dos menus (✕ = usar o tom do painel)", colors = Spectra.BackgroundGrid + Spectra.ColorGrid.take(32),
            initial = if (s.menuColor != 0L) s.menuColor else 0xFF17154FL, initialAuto = s.menuColor == 0L, showAdjustments = false, allowAuto = true,
            onConfirm = { c, _, _, auto -> vm.launch { setMenuColor(if (auto) 0L else c) } }, onDismiss = { dialog = null })
        "titleColor" -> com.hazagou.spectra.ui.components.ColorPickerDialog(
            title = "Cor do título (✕ = degradê automático)", colors = Spectra.ColorGrid,
            initial = if (s.titleColor != 0L) s.titleColor else 0xFFFFFFFFL, initialAuto = s.titleColor == 0L, showAdjustments = false, allowAuto = true,
            onConfirm = { c, _, _, auto -> vm.launch { setTitleColor(if (auto) 0L else c) } }, onDismiss = { dialog = null })
        "hex" -> TextEditDialog("Cor personalizada (#RRGGBB)", "#%06X".format(s.accent and 0xFFFFFFL), onSave = { t ->
            val hex = t.trim().removePrefix("#")
            val v = hex.takeIf { it.length == 6 }?.toLongOrNull(16)
            if (v == null) toast(ctx, "Use 6 dígitos, ex.: FF6A00") else vm.launch { setAccent(0xFF000000L or v) }
        }, onDismiss = { dialog = null })
        "emoji" -> EmojiPickerDialog(EMOJIS, s.titleEmoji, onPick = { e -> vm.launch { setTitleEmoji(e) } }, onDismiss = { dialog = null })
        "accent" -> com.hazagou.spectra.ui.components.ColorPickerDialog(
            title = "Cor de destaque", colors = Spectra.ColorGrid, initial = s.accent, initialLum = s.accentLum,
            initialAlpha = s.accentAlpha, initialAuto = s.autoAccent,
            onConfirm = { c, l, a, auto -> vm.launch { setAccentFull(c, l, a, auto) } }, onDismiss = { dialog = null })
        "bg1" -> com.hazagou.spectra.ui.components.ColorPickerDialog(
            title = "Cor de fundo 1", colors = Spectra.BackgroundGrid + Spectra.ColorGrid.take(8), initial = s.bgColor1,
            showAdjustments = false, allowAuto = false,
            onConfirm = { c, _, _, _ -> vm.launch { setBgColor1(c); if (s.bgMode == com.hazagou.spectra.data.BgMode.WALLPAPER) setBgMode(com.hazagou.spectra.data.BgMode.SOLID) } },
            onDismiss = { dialog = null })
        "bg2" -> com.hazagou.spectra.ui.components.ColorPickerDialog(
            title = "Cor de fundo 2", colors = Spectra.BackgroundGrid + Spectra.ColorGrid.take(8), initial = s.bgColor2,
            showAdjustments = false, allowAuto = false,
            onConfirm = { c, _, _, _ -> vm.launch { setBgColor2(c); setBgMode(com.hazagou.spectra.data.BgMode.GRADIENT) } },
            onDismiss = { dialog = null })
        "apikey" -> {
            var key by remember { mutableStateOf<String?>(null) }
            if (key == null) TextEditDialog("API key do Google", s.googleApiKey, onSave = { key = it }, onDismiss = { if (key == null) dialog = null })
            else TextEditDialog("ID do mecanismo (CX)", s.googleCx, onSave = { cx -> vm.launch { setGoogleKeys(key ?: "", cx) } }, onDismiss = { dialog = null })
        }
    }
}
