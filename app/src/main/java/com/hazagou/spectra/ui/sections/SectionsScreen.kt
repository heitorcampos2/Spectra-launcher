package com.hazagou.spectra.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.hazagou.spectra.data.TvInput
import com.hazagou.spectra.ui.components.FocusCard
import com.hazagou.spectra.ui.components.OptionRow
import com.hazagou.spectra.ui.components.PillButton
import com.hazagou.spectra.ui.components.SpectraDialog
import com.hazagou.spectra.ui.components.TextEditDialog
import com.hazagou.spectra.ui.components.rememberAutoFocus
import com.hazagou.spectra.ui.home.AppIconSmall
import com.hazagou.spectra.ui.home.toast
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra

val SECTION_ICONS = listOf(
    "📺", "🎬", "🍿", "🎮", "🎧", "🎵", "⚽", "📰", "👶", "🌸", "🔥", "⭐", "♥", "💜", "⚙", "🔌",
    "🖥", "📱", "▦", "✦", "🐉", "🐱", "🐶", "🦁", "🐯", "👻", "🚀", "🌙", "📡", "🛠", "📚", "🏆",
)

/** Editor de seções: criar, renomear, reordenar, esconder, configurar e excluir. */
@Composable
fun SectionsScreen(vm: MainViewModel, settings: LauncherSettings) {
    val sections by vm.sections.collectAsState()
    var editing by remember { mutableStateOf<HomeSection?>(null) }
    var creating by remember { mutableStateOf(false) }
    var movingId by remember { mutableStateOf<String?>(null) }
    val first = rememberAutoFocus()

    Column(Modifier.fillMaxSize().padding(top = 32.dp, start = 56.dp, end = 56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Seções da tela inicial", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text(
                    "Crie, renomeie, mude a ordem e personalize cada seção. Use \"Mover\" e as setas ↑↓ para reordenar.",
                    color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium
                )
            }
            PillButton("Nova seção", leading = "＋", selected = true, modifier = Modifier.focusRequester(first)) { creating = true }
            Spacer(Modifier.width(10.dp))
            PillButton("Restaurar padrão", leading = "⟲") { vm.sectionsDo { resetDefaults() } }
            Spacer(Modifier.width(10.dp))
            PillButton("Voltar", leading = "←") { vm.goHome() }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 60.dp)) {
            items(sections, key = { it.id }) { s ->
                SectionRow(
                    s = s, settings = settings,
                    isMoving = movingId == s.id,
                    onMoveToggle = { movingId = if (movingId == s.id) null else s.id },
                    onMove = { d -> vm.sectionsDo { move(s.id, d) } },
                    onToggleVisible = { vm.sectionsDo { update(s.id) { it.copy(visible = !it.visible) } } },
                    onToggleTitle = { vm.sectionsDo { update(s.id) { it.copy(showTitle = !it.showTitle) } } },
                    onEdit = { editing = s },
                )
            }
        }
    }

    editing?.let { s -> SectionEditDialog(s, vm, settings) { editing = null } }
    if (creating) NewSectionDialog(vm) { creating = false }
}

@Composable
private fun SectionRow(
    s: HomeSection,
    settings: LauncherSettings,
    isMoving: Boolean,
    onMoveToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onToggleVisible: () -> Unit,
    onToggleTitle: () -> Unit,
    onEdit: () -> Unit,
) {
    val accent = LocalUi.current.accent
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (isMoving) accent.copy(alpha = 0.25f) else Spectra.Surface)
            .border(if (isMoving) 2.dp else 1.dp, if (isMoving) accent else Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(s.icon, fontSize = 24.sp, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.title + if (!s.visible) "  (oculta)" else "",
                color = if (s.visible) Color.White else Spectra.TextSecondary, style = MaterialTheme.typography.titleMedium
            )
            Text(
                s.type.label + (if (s.type == SectionType.CUSTOM) " · ${s.apps.size} apps" else "") +
                    (s.cardStyle?.let { " · ${it.label}" } ?: ""),
                color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall
            )
        }
        if (isMoving) {
            PillButton("↑") { onMove(-1) }
            Spacer(Modifier.width(6.dp))
            PillButton("↓") { onMove(1) }
            Spacer(Modifier.width(6.dp))
            PillButton("Pronto", leading = "✔", selected = true) { onMoveToggle() }
        } else {
            PillButton("Mover", leading = "↕") { onMoveToggle() }
            Spacer(Modifier.width(6.dp))
            PillButton(if (s.visible) "Visível" else "Oculta", leading = if (s.visible) "●" else "○", selected = s.visible) { onToggleVisible() }
            Spacer(Modifier.width(6.dp))
            PillButton(if (s.showTitle && settings.showSectionTitles) "Nome: sim" else "Nome: não", leading = "T") { onToggleTitle() }
            Spacer(Modifier.width(6.dp))
            PillButton("Editar", leading = "✎") { onEdit() }
        }
    }
}

@Composable
private fun NewSectionDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf<String?>(null) }
    val fr = rememberAutoFocus()
    if (name == null) {
        SpectraDialog(onDismiss, width = 620.dp) {
            Text("Nova seção", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text("Escolha o tipo:", color = Spectra.TextSecondary)
            SectionType.entries.filter { it != SectionType.APP_BLOCK }.forEachIndexed { i, t ->
                OptionRow(t.label, t.defaultIcon, modifier = if (i == 0) Modifier.focusRequester(fr) else Modifier, value = null) {
                    if (t == SectionType.CUSTOM) name = ""
                    else if (t == SectionType.CHANNEL) { onDismiss(); vm.goHome(); vm.channelsOpen.value = true }
                    else {
                        vm.sectionsDo { add(HomeSection("x_" + System.currentTimeMillis(), t, t.label, t.defaultIcon)) }
                        onDismiss()
                    }
                }
            }
        }
    } else {
        TextEditDialog(
            title = "Nome da nova seção", initial = "", hint = "Ex.: Filmes, Kids, Esportes…",
            onSave = { t ->
                if (t.isNotBlank()) vm.sectionsDo { add(HomeSection.newCustom(t.trim(), "✦"), atIndex = 0) }
            },
            onDismiss = onDismiss
        )
    }
}

/** Configurações de UMA seção: nome, ícone, nome visível, estilo/tamanho dos cartões, apps, excluir. */
@Composable
fun SectionEditDialog(section: HomeSection, vm: MainViewModel, settings: LauncherSettings, onDismiss: () -> Unit) {
    val sections by vm.sections.collectAsState()
    val s = sections.firstOrNull { it.id == section.id } ?: section
    var renaming by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }
    var pickingApps by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fr = rememberAutoFocus()
    fun upd(t: (HomeSection) -> HomeSection) = vm.sectionsDo { update(s.id, t) }

    SpectraDialog(onDismiss, width = 640.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.icon, fontSize = 30.sp, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(s.title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(s.type.description, color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        OptionRow("Nome", "T", Modifier.focusRequester(fr), value = s.title) { renaming = true }
        OptionRow("Ícone", "☺", value = s.icon) { pickingIcon = true }
        OptionRow("Mostrar nome da seção", "👁", value = if (s.showTitle) "Sim" else "Não") { upd { it.copy(showTitle = !it.showTitle) } }
        OptionRow("Estilo dos cartões", "▭", value = s.cardStyle?.label ?: "Padrão (${settings.cardStyle.label})") {
            val opts = listOf<CardStyle?>(null) + CardStyle.entries
            upd { it.copy(cardStyle = opts[(opts.indexOf(it.cardStyle) + 1) % opts.size]) }
        }
        OptionRow("Tamanho dos cartões", "⤢", value = s.cardSize?.let { listOf("Pequeno", "Médio", "Grande")[it] } ?: "Padrão") {
            val opts = listOf<Int?>(null, 0, 1, 2)
            upd { it.copy(cardSize = opts[(opts.indexOf(it.cardSize) + 1) % opts.size]) }
        }
        when (s.type) {
            SectionType.CUSTOM -> OptionRow("Escolher apps", "＋", value = "${s.apps.size}") { pickingApps = true }
            SectionType.AUTO_TV, SectionType.TV_APPS, SectionType.MOBILE_APPS, SectionType.ALL_APPS -> {
                OptionRow("Incluir apps extras", "＋", value = if (s.type == SectionType.AUTO_TV) "${s.apps.size}" else null) { pickingApps = true }
                if (s.excluded.isNotEmpty()) OptionRow("Restaurar apps removidos", "⟲", value = "${s.excluded.size}") { upd { it.copy(excluded = emptySet()) } }
            }
            SectionType.APP_CONTENT -> OptionRow("Configurar apps de conteúdo", "🎬", value = "›") {
                onDismiss(); vm.open(com.hazagou.spectra.Screen.SETTINGS)
            }
            else -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            PillButton("Excluir seção", leading = "🗑") { confirmDelete = true }
            PillButton("Fechar", leading = "✔", selected = true) { onDismiss() }
        }
    }

    if (renaming) TextEditDialog("Nome da seção", s.title, onSave = { t -> if (t.isNotBlank()) upd { it.copy(title = t.trim()) } }, onDismiss = { renaming = false })
    if (pickingIcon) EmojiPickerDialog(SECTION_ICONS, s.icon, onPick = { e -> upd { it.copy(icon = e) } }, onDismiss = { pickingIcon = false })
    if (pickingApps) AppPickerDialog(
        title = "Apps de \"${s.title}\"",
        apps = vm.apps.value.filter { it.packageName !in settings.hidden },
        initial = s.apps.toSet(),
        onConfirm = { chosen ->
            val keep = s.apps.filter { it in chosen }
            upd { it.copy(apps = keep + (chosen - keep.toSet()), excluded = it.excluded - chosen) }
        },
        onDismiss = { pickingApps = false }
    )
    if (confirmDelete) SpectraDialog({ confirmDelete = false }) {
        val f2 = rememberAutoFocus()
        Text("Excluir a seção \"${s.title}\"?", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text("Os apps não são desinstalados.", color = Spectra.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Cancelar", modifier = Modifier.focusRequester(f2)) { confirmDelete = false }
            PillButton("Excluir", leading = "🗑", selected = true) { vm.sectionsDo { remove(s.id) }; confirmDelete = false; onDismiss() }
        }
    }
}

@Composable
fun EmojiPickerDialog(options: List<String>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val fr = rememberAutoFocus()
    SpectraDialog(onDismiss, width = 640.dp) {
        Text("Escolha um ícone", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        LazyVerticalGrid(GridCells.Fixed(8), Modifier.heightIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { e ->
                FocusCard(
                    Modifier.size(58.dp).then(if (e == options.first()) Modifier.focusRequester(fr) else Modifier),
                    corner = 14.dp, glow = false,
                    background = if (e == current) LocalUi.current.accent.copy(alpha = 0.4f) else Spectra.Surface,
                    onClick = { onPick(e); onDismiss() }
                ) { _ -> Text(e, fontSize = 26.sp, modifier = Modifier.align(Alignment.Center)) }
            }
        }
    }
}

/** Seletor de vários apps com marcação. */
@Composable
fun AppPickerDialog(
    title: String,
    apps: List<AppInfo>,
    initial: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(initial) }
    val fr = rememberAutoFocus()
    val accent = LocalUi.current.accent
    SpectraDialog(onDismiss, width = 860.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            PillButton("Salvar (${chosen.size})", leading = "✔", selected = true) { onConfirm(chosen); onDismiss() }
            Spacer(Modifier.width(8.dp))
            PillButton("Cancelar") { onDismiss() }
        }
        LazyVerticalGrid(
            GridCells.Fixed(4), Modifier.heightIn(max = 460.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(apps, key = { it.packageName }) { a ->
                val on = a.packageName in chosen
                FocusCard(
                    Modifier.fillMaxWidth().height(64.dp).then(if (a == apps.firstOrNull()) Modifier.focusRequester(fr) else Modifier),
                    corner = 14.dp, focusScale = 1.04f, glow = false,
                    background = if (on) accent.copy(alpha = 0.35f) else Spectra.Surface,
                    onClick = { chosen = if (on) chosen - a.packageName else chosen + a.packageName }
                ) { _ ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIconSmall(a.packageName, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(a.label, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(if (on) "✔" else "", color = Color.White)
                    }
                }
            }
        }
    }
}

/** Opções de uma entrada (renomear; para Cast, escolher o app que abre). */
@Composable
fun InputOptionsDialog(input: TvInput, vm: MainViewModel, settings: LauncherSettings, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var pickingCast by remember { mutableStateOf(false) }
    val fr = rememberAutoFocus()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    SpectraDialog(onDismiss) {
        Text(input.label, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        OptionRow("Renomear", "✎", Modifier.focusRequester(fr)) { renaming = true }
        if (input.kind == TvInput.Kind.CAST) {
            val castLabel = settings.castPackage.takeIf { it.isNotBlank() }?.let { vm.appsRepo.label(it) } ?: "Automático"
            OptionRow("App de Cast/Espelhamento", "📡", value = castLabel) { pickingCast = true }
        }
        OptionRow("Abrir", "▶") {
            if (!vm.system.openInput(input, settings.castPackage)) toast(ctx, "Não foi possível abrir")
            onDismiss()
        }
    }
    if (renaming) TextEditDialog("Nome da entrada", input.label, onSave = { n -> vm.launch { setInputName(input.id, n.trim()) } }, onDismiss = { renaming = false; onDismiss() })
    if (pickingCast) {
        val f2 = rememberAutoFocus()
        SpectraDialog({ pickingCast = false }, width = 640.dp) {
            Text("App que o cartão Cast abre", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { OptionRow("Automático (configurações de Cast)", "⟲", Modifier.focusRequester(f2)) { vm.launch { setCastPackage("") }; pickingCast = false } }
                items(vm.apps.value, key = { it.packageName }) { a ->
                    OptionRow(a.label, "•") { vm.launch { setCastPackage(a.packageName) }; pickingCast = false }
                }
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun Placeholder() = Box(Modifier)
