package com.hazagou.spectra.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazagou.spectra.MainViewModel
import com.hazagou.spectra.data.AppInfo
import com.hazagou.spectra.data.CardStyle
import com.hazagou.spectra.data.HomeSection
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.data.SectionType
import com.hazagou.spectra.data.StreamingApps
import com.hazagou.spectra.data.TvChannel
import com.hazagou.spectra.data.TvContentRepository
import com.hazagou.spectra.ui.components.AdjustRow
import com.hazagou.spectra.ui.components.FocusCard
import com.hazagou.spectra.ui.components.OptionRow
import com.hazagou.spectra.ui.components.PillButton
import com.hazagou.spectra.ui.components.TextEditDialog
import com.hazagou.spectra.ui.components.rememberAutoFocus
import com.hazagou.spectra.ui.sections.EmojiPickerDialog
import com.hazagou.spectra.ui.sections.SECTION_ICONS
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra

/** Painel lateral que desliza da direita. */
@Composable
fun RightPanel(visible: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
    }
    AnimatedVisibility(visible, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
        BackHandler { onClose() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) { PanelFrame { content() } }
    }
}

@Composable
private fun PanelTitle(icon: String, title: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 26.sp, color = LocalUi.current.accent, modifier = Modifier.padding(end = 14.dp))
        Column {
            Text(title, color = LocalUi.current.accent, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Configuração lateral de UMA seção/canal, direto na tela inicial:
 * nome, ícone, visível, grade, linha deslizante, exibir nome, tamanho, estilo, ordenar,
 * posição da seção, gerenciar aplicativos (visível, tamanho e posição de cada cartão) e "definir como padrão".
 */
@Composable
fun SectionPanel(vm: MainViewModel, settings: LauncherSettings) {
    val id by vm.sectionPanel.collectAsState()
    val sections by vm.sections.collectAsState()
    val section = sections.firstOrNull { it.id == id }
    RightPanel(visible = section != null, onClose = { vm.sectionPanel.value = null }) {
        if (section != null) SectionPanelContent(section, vm, settings)
    }
}

@Composable
private fun SectionPanelContent(s: HomeSection, vm: MainViewModel, settings: LauncherSettings) {
    var renaming by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }
    var managing by remember(s.id) { mutableStateOf(false) }
    val first = rememberAutoFocus()
    fun upd(t: (HomeSection) -> HomeSection) = vm.sectionsDo { update(s.id, t) }
    val size = s.cardSize ?: settings.cardSize
    val isApps = s.type in setOf(SectionType.CUSTOM, SectionType.AUTO_TV, SectionType.TV_APPS, SectionType.MOBILE_APPS, SectionType.ALL_APPS)

    PanelTitle(s.icon, if (managing) "Gerenciar aplicativos" else s.title, if (managing) s.title else s.type.label)
    if (managing) {
        ManageApps(s, vm, settings, onBack = { managing = false })
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { OptionRow("Nome", "T", Modifier.focusRequester(first), value = s.title) { renaming = true } }
        item { OptionRow("Mudar ícone", "☺", value = s.icon) { pickingIcon = true } }
        item { OptionRow("Visível", "👁", value = onOff(s.visible)) { upd { it.copy(visible = !it.visible) } } }
        item { OptionRow("Grade", "▦", value = onOff(s.grid)) { upd { it.copy(grid = !it.grid) } } }
        item { OptionRow("Linha deslizante", "↔", value = onOff(s.sliding)) { upd { it.copy(sliding = !it.sliding) } } }
        item { OptionRow("Exibir nome", "▭", value = onOff(s.showTitle)) { upd { it.copy(showTitle = !it.showTitle) } } }
        item { AdjustRow("Tamanho", SIZE_LABELS[size]) { d -> upd { it.copy(cardSize = ((it.cardSize ?: settings.cardSize) + d).coerceIn(0, 3)) } } }
        if (isApps || s.type == SectionType.SHORTCUTS || s.type == SectionType.INPUTS) item {
            AdjustRow("Estilo dos cartões", (s.cardStyle ?: settings.cardStyle).label) { d ->
                val l = CardStyle.entries
                upd { it.copy(cardStyle = l[(l.indexOf(it.cardStyle ?: settings.cardStyle) + d).mod(l.size)]) }
            }
        }
        item { OptionRow("Ordenar", "≡", value = if (s.sort == "az") "A–Z" else "Personalizado") { upd { it.copy(sort = if (it.sort == "az") "custom" else "az") } } }
        if (s.type == SectionType.APP_BLOCK) {
            val pkg = s.channelPackage
            item { OptionRow("Mostrar este app na tela inicial", "👁", value = onOff(settings.isAppContentEnabled(pkg))) {
                vm.launch { setSectionDisabled("$pkg|*", settings.isAppContentEnabled(pkg)) }
            } }
            com.hazagou.spectra.data.SectionKind.entries.forEach { k ->
                item { OptionRow(k.label, when (k) {
                        com.hazagou.spectra.data.SectionKind.CONTINUE -> "▶"
                        com.hazagou.spectra.data.SectionKind.RECOMMENDED -> "★"
                        com.hazagou.spectra.data.SectionKind.CHANNELS -> "📺"
                    }, value = onOff(settings.isSectionEnabled(pkg, k))) {
                    vm.launch { setSectionDisabled("$pkg|${k.key}", settings.isSectionEnabled(pkg, k)) }
                } }
            }
        }
        item { OptionRow("Mover seção para cima", "⇡") { vm.moveSectionVisible(s.id, -1) } }
        item { OptionRow("Mover seção para baixo", "⇣") { vm.moveSectionVisible(s.id, 1) } }
        if (isApps) item { OptionRow("Gerenciar aplicativos", "✎", value = "›") { managing = true } }
        item {
            OptionRow("Definir como padrão", "↦", value = "aplicar a todas") {
                vm.sectionsDo { applyStyleToAll(s) }
                vm.launch { setCardSize(size); s.cardStyle?.let { setCardStyle(it) } }
            }
        }
        if (s.type == SectionType.CUSTOM || s.type == SectionType.CHANNEL) item {
            OptionRow("Excluir seção", "🗑") { vm.sectionsDo { remove(s.id) }; vm.sectionPanel.value = null }
        }
        item { OptionRow("Fechar", "✕") { vm.sectionPanel.value = null } }
    }
    if (renaming) TextEditDialog("Nome da seção", s.title, onSave = { t -> if (t.isNotBlank()) upd { it.copy(title = t.trim()) } }, onDismiss = { renaming = false })
    if (pickingIcon) EmojiPickerDialog(SECTION_ICONS, s.icon, onPick = { e -> upd { it.copy(icon = e) } }, onDismiss = { pickingIcon = false })
}

private fun onOff(b: Boolean) = if (b) "● Sim" else "○ Não"

/** Lista de apps da seção: mostrar/ocultar, tamanho individual (◀ ▶) e posição (↑ ↓). */
@Composable
private fun ManageApps(s: HomeSection, vm: MainViewModel, settings: LauncherSettings, onBack: () -> Unit) {
    val apps by vm.apps.collectAsState()
    val first = rememberAutoFocus()
    val visibleApps = apps.filter { it.packageName !in settings.hidden }
    // candidatos: para seções automáticas, o que a regra traz + extras; para CUSTOM, todos os apps
    val candidates: List<AppInfo> = when (s.type) {
        SectionType.TV_APPS -> visibleApps.filter { it.isTvApp }
        SectionType.MOBILE_APPS -> visibleApps.filter { !it.isTvApp }
        SectionType.AUTO_TV -> {
            val det = StreamingApps.detect(visibleApps)
            val byPkg = visibleApps.associateBy { it.packageName }
            (det + s.apps.filter { it !in det }).mapNotNull { byPkg[it] } + visibleApps.filter { it.packageName !in det && it.packageName !in s.apps }
        }
        else -> visibleApps
    }
    val shown = vm.itemsOf(s.id)
    val ordered = candidates.sortedBy { a -> shown.indexOf(a.packageName).let { if (it < 0) Int.MAX_VALUE else it } }
    fun isOn(pkg: String) = when (s.type) {
        SectionType.CUSTOM -> pkg in s.apps
        SectionType.AUTO_TV -> pkg in shown || (pkg in s.apps && pkg !in s.excluded)
        else -> pkg !in s.excluded
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { OptionRow("Voltar", "‹", Modifier.focusRequester(first)) { onBack() } }
        item { Text("OK liga/desliga • ◀ ▶ muda o tamanho do cartão • ↑↓ nos botões muda a posição", color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        items(ordered, key = { it.packageName }) { a ->
            val on = isOn(a.packageName)
            val sz = s.appSizes[a.packageName] ?: (s.cardSize ?: settings.cardSize)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusCard(
                    Modifier.weight(1f), corner = 14.dp, focusScale = 1.02f, glow = false,
                    background = Color.White.copy(alpha = 0.05f),
                    onAdjust = { d -> vm.sectionsDo { update(s.id) { it.copy(appSizes = it.appSizes + (a.packageName to (sz + d).coerceIn(0, 3))) } } },
                    onClick = {
                        if (on) vm.sectionsDo { removeApp(s.id, a.packageName) } else vm.sectionsDo { addApp(s.id, a.packageName) }
                    }
                ) { f ->
                    Row(Modifier.fillMaxWidth().background(if (f) LocalUi.current.accent.copy(alpha = 0.8f) else Color.Transparent).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AppIconSmall(a.packageName, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(a.label, color = if (on) Color.White else Spectra.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(if (f) "◀ ${SIZE_LABELS[sz]} ▶" else SIZE_LABELS[sz], color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(10.dp))
                        Text(if (on) "✔" else "○", color = Color.White)
                    }
                }
                if (on) {
                    Spacer(Modifier.width(6.dp))
                    PillButton("↑") { vm.sectionsDo { moveApp(s.id, a.packageName, -1, vm.itemsOf(s.id)) } }
                    Spacer(Modifier.width(4.dp))
                    PillButton("↓") { vm.sectionsDo { moveApp(s.id, a.packageName, 1, vm.itemsOf(s.id)) } }
                }
            }
        }
    }
}

/** "Editar canais": Ver a seguir + todos os canais publicados pelos apps, com visibilidade e configuração. */
@Composable
fun ChannelsPanel(vm: MainViewModel, settings: LauncherSettings, onRequestPermission: () -> Unit) {
    val open by vm.channelsOpen.collectAsState()
    RightPanel(open, onClose = { vm.channelsOpen.value = false }) {
        val tv by vm.tvState.collectAsState()
        val sections by vm.sections.collectAsState()
        val apps by vm.apps.collectAsState()
        var expanded by remember { mutableStateOf<String?>(null) }
        val first = rememberAutoFocus()
        val byPkg = apps.associateBy { it.packageName }
        PanelTitle("📺", "Canais", "OK mostra/oculta • segure OK para configurar")
        val ready = tv as? TvContentRepository.State.Ready
        val channels: List<TvChannel> = ready?.channels.orEmpty()
        val grouped = channels.groupBy { it.packageName }.toList().sortedBy { (pkg, _) -> byPkg[pkg]?.label?.lowercase() ?: pkg }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (tv is TvContentRepository.State.NoPermission || vm.tvRepo.needsListings) item {
                OptionRow("Liberar canais dos apps", "🔓", Modifier.focusRequester(first)) { onRequestPermission() }
            }
            val wn = sections.firstOrNull { it.type == SectionType.WATCH_NEXT }
            item {
                ChannelRow(
                    icon = { androidx.compose.material3.Text("▶", color = LocalUi.current.accent, fontSize = 22.sp) },
                    title = "Ver a seguir", subtitle = "${ready?.watchNext?.size ?: 0} itens de todos os apps",
                    visible = wn?.visible == true,
                    modifier = if (tv !is TvContentRepository.State.NoPermission && !vm.tvRepo.needsListings) Modifier.focusRequester(first) else Modifier,
                    onToggle = {
                        if (wn == null) vm.sectionsDo { add(HomeSection("watchnext", SectionType.WATCH_NEXT, "Ver a seguir", "▶"), 0) }
                        else vm.sectionsDo { update(wn.id) { it.copy(visible = !it.visible) } }
                    },
                    onConfigure = { if (wn != null) { vm.channelsOpen.value = false; vm.sectionPanel.value = wn.id } }
                )
            }
            val contentSec = sections.firstOrNull { it.type == SectionType.APP_CONTENT }
            item {
                ChannelRow(
                    icon = { androidx.compose.material3.Text("🎬", fontSize = 22.sp) },
                    title = "Blocos automáticos por app", subtitle = "Continuar • Recomendados • Canais agrupados por app",
                    visible = contentSec?.visible == true,
                    onToggle = { contentSec?.let { c -> vm.sectionsDo { update(c.id) { it.copy(visible = !it.visible) } } } },
                    onConfigure = { contentSec?.let { vm.channelsOpen.value = false; vm.sectionPanel.value = it.id } }
                )
            }
            for ((type, info) in listOf(
                SectionType.SUGGESTIONS to Triple("✨", "Sugestões", "Recomendações de todos os apps numa fileira"),
                SectionType.SUBSCRIPTIONS to Triple("🔔", "Inscrições", "Canais de inscrições (YouTube, SmartTube…)"),
            )) {
                val sec = sections.firstOrNull { it.type == type }
                item {
                    ChannelRow(
                        icon = { androidx.compose.material3.Text(info.first, fontSize = 22.sp) },
                        title = info.second, subtitle = info.third,
                        visible = sec?.visible == true,
                        onToggle = {
                            if (sec == null) vm.sectionsDo { add(HomeSection(type.name.lowercase(), type, info.second, info.first), 1) }
                            else vm.sectionsDo { update(sec.id) { it.copy(visible = !it.visible) } }
                        },
                        onConfigure = { if (sec != null) { vm.channelsOpen.value = false; vm.sectionPanel.value = sec.id } }
                    )
                }
            }
            if (channels.isEmpty()) item {
                Text("Nenhum canal encontrado. Veja o diagnóstico abaixo.", color = Spectra.TextSecondary)
            }
            grouped.forEach { (pkg, list) ->
                val app = byPkg[pkg]
                val label = app?.label ?: pkg
                if (list.size == 1) {
                    val ch = list.first()
                    item(key = "c_${ch.id}") { SingleChannel(vm, sections, label, ch) }
                } else {
                    item(key = "g_$pkg") {
                        val anyOn = list.any { c -> sections.any { it.type == SectionType.CHANNEL && it.channelKey == chKey(c) && it.visible } }
                        ChannelRow(
                            icon = { AppIconSmall(pkg, 36.dp) },
                            title = label, subtitle = "${list.size} canais" + if (expanded == pkg) "  ▾" else "  ▸",
                            visible = anyOn,
                            onToggle = { expanded = if (expanded == pkg) null else pkg },
                            onConfigure = { expanded = if (expanded == pkg) null else pkg }
                        )
                    }
                    if (expanded == pkg) list.forEach { ch ->
                        item(key = "c_${ch.id}") { Box(Modifier.padding(start = 24.dp)) { SingleChannel(vm, sections, label, ch) } }
                    }
                }
            }
            item {
                val d by vm.tvRepo.diagnostics.collectAsState()
                d?.let { dg ->
                    androidx.compose.foundation.layout.Column(
                        Modifier.padding(top = 14.dp).fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(14.dp)
                    ) {
                        Text("Diagnóstico de canais", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        val lines = listOf(
                            "Provedor de canais do Android TV: " + if (dg.provider) "✔ presente" else "✕ ausente (celular/tablet não tem canais)",
                            "Permissão de canais (READ_EPG_DATA): " + if (dg.readEpg) "✔ concedida" else "✕ negada",
                            "Permissão de listagens (READ_TV_LISTINGS): " + dg.readListings,
                            "Linhas lidas: ${dg.channelRows} canais • ${dg.previewRows} programas • ${dg.watchNextRows} ver a seguir",
                            "Apps com canais visíveis: " + dg.channelApps.joinToString { byPkg[it]?.label ?: it }.ifBlank { "nenhum" },
                        ) + listOfNotNull(dg.error?.let { "Erro: $it" })
                        lines.forEach { Text(it, color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp)) }
                        if (vm.tvRepo.needsListings) {
                            Text(
                                "Falta a permissão de listagens: sem ela o Android esconde os canais dos outros apps. " +
                                    "Toque em \"Liberar canais dos apps\" no topo. Se a TV não mostrar a pergunta, libere pelo computador:",
                                color = Color(0xFFFDE68A), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                "adb shell pm grant ${com.hazagou.spectra.BuildConfig.APPLICATION_ID} android.permission.READ_TV_LISTINGS",
                                color = Spectra.Lilac, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else if (dg.provider && dg.readEpg && dg.channelRows == 0 && dg.watchNextRows == 0) Text(
                            "Os apps ainda não publicaram canais. Abra o Sintonia (ou outro app) e assista algo por alguns segundos.",
                            color = Color(0xFFFDE68A), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun chKey(ch: TvChannel) = "${ch.packageName}|${ch.name}"

@Composable
private fun SingleChannel(vm: MainViewModel, sections: List<HomeSection>, appLabel: String, ch: TvChannel) {
    val sec = sections.firstOrNull { it.type == SectionType.CHANNEL && it.channelKey == chKey(ch) }
    ChannelRow(
        icon = { AppIconSmall(ch.packageName, 36.dp) },
        title = appLabel, subtitle = "${ch.name} • ${ch.programs.size} itens",
        visible = sec?.visible == true,
        onToggle = { vm.sectionsDo { setChannelVisible(chKey(ch), "$appLabel · ${ch.name}", !(sec?.visible ?: false)) } },
        onConfigure = {
            vm.sectionsDo {
                if (sec == null) setChannelVisible(chKey(ch), "$appLabel · ${ch.name}", true)
                val created = current().firstOrNull { it.type == SectionType.CHANNEL && it.channelKey == chKey(ch) }
                vm.channelsOpen.value = false
                vm.sectionPanel.value = created?.id
            }
        }
    )
}

@Composable
private fun ChannelRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onConfigure: () -> Unit,
) {
    val accent = LocalUi.current.accent
    FocusCard(modifier.fillMaxWidth(), corner = 14.dp, focusScale = 1.02f, glow = false, background = Color.White.copy(alpha = 0.04f),
        onLongClick = onConfigure, onClick = onToggle) { f ->
        Row(Modifier.fillMaxWidth().background(if (f) Color.White.copy(alpha = 0.16f) else Color.Transparent).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!visible) Text("  ⊘", color = Spectra.TextSecondary, style = MaterialTheme.typography.titleMedium)
                }
                Text(subtitle, color = if (f) Color.White else Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (f) PillButtonStatic("⚙", accent)
        }
    }
}

@Composable
private fun PillButtonStatic(text: String, color: Color) {
    Text(text, color = color, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
}
