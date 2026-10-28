package com.hazagou.spectra

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hazagou.spectra.data.AppContent
import com.hazagou.spectra.data.AppInfo
import com.hazagou.spectra.data.CardStyle
import com.hazagou.spectra.data.HomeSection
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.data.SectionKind
import com.hazagou.spectra.data.SectionType
import com.hazagou.spectra.data.SectionsRepository
import com.hazagou.spectra.data.SettingsRepository
import com.hazagou.spectra.data.StreamingApps
import com.hazagou.spectra.data.TvChannel
import com.hazagou.spectra.data.TvContentRepository
import com.hazagou.spectra.data.TvInput
import com.hazagou.spectra.data.TvProgram
import com.hazagou.spectra.service.SpectraNotificationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { HOME, APPS, SETTINGS, WALLPAPER, SECTIONS }

/** Seção por app de conteúdo (Canais / Continuar / Recomendados). */
data class HomeAppSection(val app: AppInfo, val content: AppContent)

enum class ShortcutKind(val label: String, val icon: String) {
    ALL_APPS("Todos os apps", "▦"),
    EDIT_SECTIONS("Editar seções", "☰"),
    WALLPAPERS("Wallpapers", "🖼"),
    CUSTOMIZE("Personalizar Spectra", "✦"),
    NOTIFICATIONS("Notificações", "🔔"),
    ANDROID_SETTINGS("Configurações do Android", "⚙"),
}

sealed interface HomeItem {
    val key: String
    data class App(val app: AppInfo) : HomeItem { override val key = "a_" + app.packageName }
    data class Input(val input: TvInput) : HomeItem { override val key = "i_" + input.id }
    data class Shortcut(val kind: ShortcutKind) : HomeItem { override val key = "s_" + kind.name }
}

sealed interface HomeRowModel {
    val key: String
    data class Items(
        val section: HomeSection,
        val items: List<HomeItem>,
        val style: CardStyle,
        val size: Int,
    ) : HomeRowModel { override val key = "sec_" + section.id }

    data class ContentHeader(val sectionId: String, val app: AppInfo, val content: AppContent) : HomeRowModel {
        override val key = "h_${sectionId}_" + app.packageName
    }

    data class Programs(
        override val key: String,
        val title: String,
        val emoji: String,
        /** App dono da fileira (null em "Ver a seguir", que mistura apps). */
        val app: AppInfo?,
        val programs: List<TvProgram>,
        val channel: TvChannel?,
        /** Seção configurável que gerou a fileira (canais individuais / ver a seguir). */
        val section: HomeSection? = null,
    ) : HomeRowModel

    data object Permission : HomeRowModel { override val key = "perm" }
}

/** App em "modo mover" dentro de uma seção. */
data class MovingApp(val sectionId: String, val pkg: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val core = application as SpectraApp
    val settingsRepo: SettingsRepository = core.settings
    val sectionsRepo: SectionsRepository = core.sections
    val appsRepo = core.apps
    val tvRepo = core.tv
    val wallRepo = core.wallpapers
    val system = core.system
    val remote = core.remote
    val updates = core.updates

    private val refreshTick = MutableStateFlow(0)
    val screen = MutableStateFlow(Screen.HOME)
    val homeResetSignal = MutableStateFlow(0)
    val sidePanelOpen = MutableStateFlow(false)
    val notificationsOpen = MutableStateFlow(false)
    val profileOpen = MutableStateFlow(false)
    val moving = MutableStateFlow<MovingApp?>(null)
    /** Painel lateral de configuração de UMA seção (id da seção). */
    val sectionPanel = MutableStateFlow<String?>(null)
    /** Painel "Editar canais". */
    val channelsOpen = MutableStateFlow(false)
    /** Seção em modo mover (pelos cabeçalhos à esquerda: ↑/↓ movem, OK confirma). */
    val movingSection = MutableStateFlow<String?>(null)
    /** Diálogo "Parear celular" (QR code do controle remoto). */
    val remotePairOpen = MutableStateFlow(false)
    /** Assistente da primeira abertura. */
    val onboardingOpen = MutableStateFlow(false)
    /** Aumenta a cada volta para o Spectra (atualiza o status das permissões no assistente). */
    val resumeTick = MutableStateFlow(0)
    /** Abre as Configurações direto numa categoria (ex.: "UPDATES", "REMOTE"). */
    val settingsDeepLink = MutableStateFlow<String?>(null)
    /** Modo ocioso (só wallpaper + relógio). */
    val idle = MutableStateFlow(false)
    @Volatile var lastInteraction: Long = System.currentTimeMillis()

    val settings: StateFlow<LauncherSettings?> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sections: StateFlow<List<HomeSection>> =
        sectionsRepo.sections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val apps: StateFlow<List<AppInfo>> =
        appsRepo.apps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tvState: StateFlow<TvContentRepository.State?> =
        tvRepo.observe(refreshTick).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val network = system.network.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val battery = system.battery.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val notifications = SpectraNotificationService.items

    /** Todos os apps com conteúdo (inclusive desativados) — tela de gerenciamento. */
    val allContentApps: StateFlow<List<HomeAppSection>> = combine(apps, tvState, settings) { apps, tv, s ->
        if (tv !is TvContentRepository.State.Ready) return@combine emptyList<HomeAppSection>()
        val byPkg = apps.associateBy { it.packageName }
        val order = s?.sectionOrder.orEmpty()
        tv.byPackage.values.mapNotNull { c -> byPkg[c.packageName]?.let { HomeAppSection(it, c) } }
            .sortedWith(compareBy<HomeAppSection> {
                val i = order.indexOf(it.app.packageName); if (i < 0) Int.MAX_VALUE else i
            }.thenBy { it.app.label.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Modelo completo da tela inicial, montado a partir das seções configuradas. */
    val homeRows: StateFlow<List<HomeRowModel>> =
        combine(apps, tvState, settings.filterNotNull(), sections, allContentApps) { apps, tv, s, sections, contentApps ->
            buildRows(apps, tv, s, sections, contentApps)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Itens exibidos de cada seção (ordem atual) — usado pelo modo mover. */
    fun itemsOf(sectionId: String): List<String> =
        (homeRows.value.firstOrNull { it is HomeRowModel.Items && it.section.id == sectionId } as? HomeRowModel.Items)
            ?.items?.filterIsInstance<HomeItem.App>()?.map { it.app.packageName }.orEmpty()

    private fun ordered(apps: List<AppInfo>, order: List<String>): List<AppInfo> {
        if (order.isEmpty()) return apps
        val idx = order.withIndex().associate { it.value to it.index }
        return apps.sortedWith(compareBy<AppInfo> { idx[it.packageName] ?: Int.MAX_VALUE })
    }

    private fun buildRows(
        apps: List<AppInfo>,
        tv: TvContentRepository.State?,
        s: LauncherSettings,
        sections: List<HomeSection>,
        contentApps: List<HomeAppSection>,
    ): List<HomeRowModel> {
        val visible = apps.filter { it.packageName !in s.hidden }
        val byPkg = visible.associateBy { it.packageName }
        val rows = ArrayList<HomeRowModel>()
        var permissionAdded = false
        for (sec in sections) {
            if (!sec.visible) continue
            val style = sec.cardStyle ?: s.cardStyle
            val size = sec.cardSize ?: s.cardSize
            fun appsRow(list: List<AppInfo>) {
                val base = list.filter { it.packageName !in sec.excluded }
                val sortedList = if (sec.sort == "az") base.sortedBy { it.label.lowercase() } else ordered(base, sec.apps)
                val items = sortedList.map { HomeItem.App(it) }
                if (items.isNotEmpty()) rows += HomeRowModel.Items(sec, items, style, size)
            }
            when (sec.type) {
                SectionType.AUTO_TV -> {
                    val detected = StreamingApps.detect(visible)
                    val extra = sec.apps.filter { it !in detected }
                    appsRow((detected + extra).mapNotNull { byPkg[it] })
                }
                SectionType.CUSTOM -> {
                    val items = sec.apps.mapNotNull { byPkg[it] }.map { HomeItem.App(it) }
                    rows += HomeRowModel.Items(sec, items, style, size) // mostra mesmo vazia (cartão "+")
                }
                SectionType.TV_APPS -> appsRow(visible.filter { it.isTvApp })
                SectionType.MOBILE_APPS -> appsRow(visible.filter { !it.isTvApp })
                SectionType.ALL_APPS -> appsRow(visible)
                SectionType.INPUTS -> {
                    val inputs = system.inputs(s.inputNames, s.inputsDemo).map { HomeItem.Input(it) }
                    if (inputs.isNotEmpty()) rows += HomeRowModel.Items(sec, inputs, style, size)
                }
                SectionType.SHORTCUTS -> rows += HomeRowModel.Items(sec, ShortcutKind.entries.map { HomeItem.Shortcut(it) }, style, size)
                SectionType.WATCH_NEXT -> {
                    if ((tv is TvContentRepository.State.NoPermission || tvRepo.needsListings) && !permissionAdded) {
                        rows += HomeRowModel.Permission; permissionAdded = true
                    }
                    val list = (tv as? TvContentRepository.State.Ready)?.watchNext.orEmpty()
                        .filter { it.packageName !in s.hidden && it.packageName !in sec.excluded }
                        .let { if (sec.sort == "az") it.sortedBy { p -> p.title.lowercase() } else it }
                        .take(s.maxProgramsPerRow)
                    if (list.isNotEmpty()) rows += HomeRowModel.Programs("wn_${sec.id}", sec.title, sec.icon, null, list, null, sec)
                }
                SectionType.SUGGESTIONS -> {
                    if (tvRepo.needsListings && !permissionAdded) { rows += HomeRowModel.Permission; permissionAdded = true }
                    val ready = tv as? TvContentRepository.State.Ready
                    // intercala os apps (1 de cada por vez) para a fileira ficar variada
                    val perApp = ready?.byPackage.orEmpty().values
                        .filter { it.packageName !in s.hidden && it.packageName !in sec.excluded }
                        .map { it.recommended }.filter { it.isNotEmpty() }
                    val mixed = ArrayList<com.hazagou.spectra.data.TvProgram>()
                    var i = 0
                    while (mixed.size < s.maxProgramsPerRow && perApp.any { it.size > i }) {
                        perApp.forEach { l -> if (i < l.size) mixed += l[i] }
                        i++
                    }
                    val list = mixed.distinctBy { it.title + it.imageUri }.take(s.maxProgramsPerRow)
                        .let { if (sec.sort == "az") it.sortedBy { p -> p.title.lowercase() } else it }
                    if (list.isNotEmpty()) rows += HomeRowModel.Programs("sg_${sec.id}", sec.title, sec.icon, null, list, null, sec)
                }
                SectionType.SUBSCRIPTIONS -> {
                    val subs = Regex("inscri|subscri|seguindo|following", RegexOption.IGNORE_CASE)
                    val list = (tv as? TvContentRepository.State.Ready)?.channels.orEmpty()
                        .filter { subs.containsMatchIn(it.name) && it.packageName !in s.hidden && it.packageName !in sec.excluded }
                        .flatMap { it.programs }
                        .let { if (sec.sort == "az") it.sortedBy { p -> p.title.lowercase() } else it }
                        .take(s.maxProgramsPerRow)
                    if (list.isNotEmpty()) rows += HomeRowModel.Programs("sb_${sec.id}", sec.title, sec.icon, null, list, null, sec)
                }
                SectionType.CHANNEL -> {
                    val ch = (tv as? TvContentRepository.State.Ready)?.channels.orEmpty()
                        .firstOrNull { it.packageName == sec.channelPackage && it.name == sec.channelName }
                    val app = byPkg[sec.channelPackage]
                    if (ch != null && app != null) {
                        val progs = (if (sec.sort == "az") ch.programs.sortedBy { it.title.lowercase() } else ch.programs).take(s.maxProgramsPerRow)
                        rows += HomeRowModel.Programs("chs_${sec.id}", sec.title.ifBlank { ch.name }, sec.icon, app, progs, ch, sec)
                    }
                }
                SectionType.APP_CONTENT -> {
                    if (tv is TvContentRepository.State.NoPermission && !permissionAdded) {
                        rows += HomeRowModel.Permission; permissionAdded = true
                    }
                    val max = s.maxProgramsPerRow
                    for (cs in contentApps) {
                        val pkg = cs.app.packageName
                        if (!s.isAppContentEnabled(pkg) || pkg in s.hidden) continue
                        val c = cs.content
                        val sub = ArrayList<HomeRowModel>()
                        if (s.isSectionEnabled(pkg, SectionKind.CONTINUE) && c.continueItems.isNotEmpty())
                            sub += HomeRowModel.Programs("c_${sec.id}_$pkg", "Continuar assistindo", "▶", cs.app, c.continueItems.take(max), null)
                        if (s.isSectionEnabled(pkg, SectionKind.RECOMMENDED) && c.recommended.isNotEmpty())
                            sub += HomeRowModel.Programs("r_${sec.id}_$pkg", "Recomendados", "★", cs.app, c.recommended.take(max), null)
                        if (s.isSectionEnabled(pkg, SectionKind.CHANNELS)) c.channels.forEach { ch ->
                            sub += HomeRowModel.Programs("ch_${sec.id}_${ch.id}", ch.name, "📺", cs.app, ch.programs.take(max), ch)
                        }
                        if (sub.isNotEmpty()) { rows += HomeRowModel.ContentHeader(sec.id, cs.app, c); rows.addAll(sub) }
                    }
                }
            }
        }
        return rows
    }

    init {
        // Troca automática de wallpaper: apresentação (wallpapers marcados) ou troca por categoria/salvos
        viewModelScope.launch {
            data class Rot(val slide: Boolean, val seconds: Int, val files: List<String>, val shuffle: Boolean, val minutes: Int, val source: String)
            settingsRepo.settings.map { Rot(it.slideshowEnabled, it.slideshowSeconds, it.slideshowFiles, it.slideshowShuffle, it.rotationMinutes, it.rotationSource) }
                .distinctUntilChanged().collectLatest { r ->
                    if (r.slide && r.files.size >= 2) {
                        while (true) {
                            delay(r.seconds * 1000L)
                            val list = r.files.filter { java.io.File(it).exists() }
                            if (list.size < 2) continue
                            val cur = settings.value?.wallpaperPath
                            val next = if (r.shuffle) list.filter { it != cur }.random()
                            else list[(list.indexOf(cur) + 1).mod(list.size)]
                            settingsRepo.setWallpaperPath(next)
                        }
                    }
                    if (r.minutes <= 0) return@collectLatest
                    while (true) {
                        delay(r.minutes * 60_000L)
                        val cur = settings.value?.wallpaperPath
                        val next = wallRepo.nextForRotation(r.source, cur) ?: continue
                        settingsRepo.setWallpaperPath(next.absolutePath)
                    }
                }
        }
    }

    fun refresh() { refreshTick.value++ }

    fun goHome() {
        screen.value = Screen.HOME
        sidePanelOpen.value = false
        notificationsOpen.value = false
        sectionPanel.value = null
        channelsOpen.value = false
        movingSection.value = null
        moving.value = null
        remotePairOpen.value = false
        homeResetSignal.value++
    }

    /** Serviço de acessibilidade ativo (botão Home + controle de outros apps). */
    fun homeOverrideEnabled(): Boolean {
        val ctx = getApplication<Application>()
        val enabled = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.startsWith("${ctx.packageName}/") }
    }

    fun openSettings(category: String) {
        settingsDeepLink.value = category
        open(Screen.SETTINGS)
    }

    fun open(s: Screen) {
        sidePanelOpen.value = false
        remotePairOpen.value = false
        sectionPanel.value = null
        channelsOpen.value = false
        notificationsOpen.value = false
        screen.value = s
    }

    fun launch(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepo.block() }
    }

    fun sectionsDo(block: suspend SectionsRepository.() -> Unit) {
        viewModelScope.launch { sectionsRepo.block() }
    }

    fun moveApp(delta: Int) {
        val m = moving.value ?: return
        val order = itemsOf(m.sectionId)
        sectionsDo { moveApp(m.sectionId, m.pkg, delta, order) }
    }

    /** Move a seção para cima/baixo trocando com a seção VISÍVEL vizinha (ignora seções vazias/ocultas). */
    fun moveSectionVisible(id: String, delta: Int) {
        val order = homeRows.value.mapNotNull { r ->
            when (r) {
                is HomeRowModel.Items -> r.section.id
                is HomeRowModel.Programs -> r.section?.id
                is HomeRowModel.ContentHeader -> r.sectionId
                HomeRowModel.Permission -> null
            }
        }.distinct()
        val i = order.indexOf(id)
        val target = order.getOrNull(i + delta) ?: return
        if (i < 0) return
        sectionsDo { moveNextTo(id, target, after = delta > 0) }
    }

    fun moveSection(pkg: String, delta: Int) = launch {
        val current = allContentApps.value.map { it.app.packageName }.toMutableList()
        val i = current.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in current.indices) return@launch
        current.removeAt(i); current.add(j, pkg)
        setSectionOrder(current)
    }
}
