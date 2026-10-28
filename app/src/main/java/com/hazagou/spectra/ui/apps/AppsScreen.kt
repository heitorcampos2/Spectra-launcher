package com.hazagou.spectra.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hazagou.spectra.MainViewModel
import com.hazagou.spectra.MovingApp
import com.hazagou.spectra.data.AppInfo
import com.hazagou.spectra.data.CardStyle
import com.hazagou.spectra.data.HomeSection
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.data.SectionType
import com.hazagou.spectra.ui.components.OptionRow
import com.hazagou.spectra.ui.components.PillButton
import com.hazagou.spectra.ui.components.SpectraDialog
import com.hazagou.spectra.ui.components.TextEditDialog
import com.hazagou.spectra.ui.components.rememberAutoFocus
import com.hazagou.spectra.ui.home.AppCard
import com.hazagou.spectra.ui.home.AppIconSmall
import com.hazagou.spectra.ui.home.appCardWidth
import com.hazagou.spectra.ui.theme.Spectra
import kotlinx.coroutines.delay

/** Gaveta com todos os apps (inclusive ocultos, se o filtro estiver ligado). */
@Composable
fun AppsScreen(vm: MainViewModel, settings: LauncherSettings) {
    val apps by vm.apps.collectAsState()
    var showHidden by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<AppInfo?>(null) }
    val first = remember { FocusRequester() }
    val list = remember(apps, settings.hidden, showHidden) {
        if (showHidden) apps.filter { it.packageName in settings.hidden } else apps.filter { it.packageName !in settings.hidden }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        Row(Modifier.padding(horizontal = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Todos os apps", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
            PillButton(if (showHidden) "Ver visíveis" else "Ver ocultos (${settings.hidden.size})", leading = "👁") { showHidden = !showHidden }
            Spacer(Modifier.width(12.dp))
            PillButton("Voltar", leading = "←") { vm.goHome() }
        }
        Text(
            "OK abre • segure OK para adicionar a uma seção, ocultar ou desinstalar",
            color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 56.dp, top = 6.dp)
        )
        val style = if (settings.cardStyle == CardStyle.POSTER) CardStyle.BANNER else settings.cardStyle
        LazyVerticalGrid(
            columns = GridCells.Adaptive(appCardWidth(style, settings.cardSize) + 8.dp),
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.packageName }) { app ->
                Box(if (app == list.firstOrNull()) Modifier.focusRequester(first) else Modifier, contentAlignment = Alignment.Center) {
                    AppCard(
                        app = app, style = style, size = settings.cardSize,
                        onLongClick = { optionsFor = app },
                        onClick = { vm.appsRepo.launch(app) }
                    )
                }
            }
        }
    }
    optionsFor?.let { AppOptionsDialog(it, null, vm, settings) { optionsFor = null } }
}

/**
 * Menu de opções do app (segurar OK). `section` = seção onde o app foi selecionado (para mover/remover).
 */
@Composable
fun AppOptionsDialog(app: AppInfo, section: HomeSection?, vm: MainViewModel, settings: LauncherSettings, onDismiss: () -> Unit) {
    val sections by vm.sections.collectAsState()
    var addingTo by remember { mutableStateOf(false) }
    var newSection by remember { mutableStateOf(false) }
    val first = rememberAutoFocus()
    val isHidden = app.packageName in settings.hidden
    val contentOff = !settings.isAppContentEnabled(app.packageName)
    val appSection = section?.takeIf { it.type != SectionType.APP_CONTENT && it.type != SectionType.SHORTCUTS && it.type != SectionType.INPUTS }

    SpectraDialog(onDismiss, width = 500.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconSmall(app.packageName, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(app.label, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                if (appSection != null) Text("Seção: ${appSection.title}", color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(6.dp))
        OptionRow("Abrir", "▶", Modifier.focusRequester(first)) { vm.appsRepo.launch(app); onDismiss() }
        if (appSection != null) {
            OptionRow("Mover (use ◀ ▶ e OK para confirmar)", "↔") {
                vm.moving.value = MovingApp(appSection.id, app.packageName); onDismiss()
            }
            OptionRow(if (appSection.type == SectionType.CUSTOM) "Remover desta seção" else "Esconder desta seção", "−") {
                vm.sectionsDo { removeApp(appSection.id, app.packageName) }; onDismiss()
            }
            val curSize = appSection.appSizes[app.packageName] ?: (appSection.cardSize ?: settings.cardSize)
            com.hazagou.spectra.ui.components.AdjustRow("Tamanho deste cartão", com.hazagou.spectra.ui.home.SIZE_LABELS[curSize]) { d ->
                vm.sectionsDo { update(appSection.id) { it.copy(appSizes = it.appSizes + (app.packageName to (curSize + d).coerceIn(0, 3))) } }
            }
            OptionRow("Configurar esta seção (painel lateral)", "⚙") { vm.sectionPanel.value = appSection.id; onDismiss() }
            OptionRow("Mover a seção para cima", "⇡") { vm.sectionsDo { move(appSection.id, -1) }; onDismiss() }
            OptionRow("Mover a seção para baixo", "⇣") { vm.sectionsDo { move(appSection.id, 1) }; onDismiss() }
        }
        OptionRow("Adicionar a uma seção…", "＋") { addingTo = true }
        OptionRow(if (contentOff) "Mostrar canais/continuar deste app" else "Esconder canais/continuar deste app", "📺") {
            vm.launch { setSectionDisabled("${app.packageName}|*", !contentOff) }; onDismiss()
        }
        OptionRow(if (isHidden) "Mostrar app" else "Ocultar app", "👁") { vm.launch { setHidden(app.packageName, !isHidden) }; onDismiss() }
        OptionRow("Informações do app", "ℹ") { vm.appsRepo.openAppInfo(app.packageName); onDismiss() }
        OptionRow("Desinstalar", "🗑") { vm.appsRepo.uninstall(app.packageName); onDismiss() }
    }

    if (addingTo) {
        val f2 = rememberAutoFocus()
        val targets = sections.filter { it.type == SectionType.CUSTOM || it.type == SectionType.AUTO_TV }
        SpectraDialog({ addingTo = false }) {
            Text("Adicionar \"${app.label}\" a…", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { OptionRow("Nova seção…", "＋", Modifier.focusRequester(f2)) { newSection = true } }
                items(targets, key = { it.id }) { s ->
                    OptionRow(s.title, s.icon, value = if (app.packageName in s.apps) "✔" else null) {
                        vm.sectionsDo { addApp(s.id, app.packageName) }; addingTo = false; onDismiss()
                    }
                }
            }
        }
    }
    if (newSection) TextEditDialog(
        "Nome da nova seção", "", hint = "Ex.: Filmes, Kids…",
        onSave = { t -> if (t.isNotBlank()) vm.sectionsDo { add(HomeSection.newCustom(t.trim(), "✦", listOf(app.packageName)), atIndex = 0) } },
        onDismiss = { newSection = false; addingTo = false; onDismiss() }
    )
}
