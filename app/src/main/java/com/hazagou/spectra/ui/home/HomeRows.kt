package com.hazagou.spectra.ui.home

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazagou.spectra.HomeItem
import com.hazagou.spectra.HomeRowModel
import com.hazagou.spectra.MainViewModel
import com.hazagou.spectra.MovingApp
import com.hazagou.spectra.data.AppInfo
import com.hazagou.spectra.data.HomeSection
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.data.SectionType
import com.hazagou.spectra.data.TvInput
import com.hazagou.spectra.data.TvProgram
import com.hazagou.spectra.ui.apps.AppOptionsDialog
import com.hazagou.spectra.ui.components.FocusCard
import com.hazagou.spectra.ui.sections.AppPickerDialog
import com.hazagou.spectra.ui.sections.InputOptionsDialog
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra
import kotlinx.coroutines.delay

/** "Linha deslizante": o item focado fica sempre no início da fileira (estilo Projectivy). */
/** Rolagem vertical entre seções com animação suave (sem mola). */
private object SmoothScrollSpec : BringIntoViewSpec {
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> =
        androidx.compose.animation.core.tween(520, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
}

private class PivotSpec(private val startPx: Float) : BringIntoViewSpec {
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> =
        androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - startPx
}

/** Abre a coluna de cabeçalhos à esquerda (← no primeiro cartão de uma fileira). */
private val LocalRevealRail = staticCompositionLocalOf<(() -> Unit)?> { null }

/** No primeiro cartão da fileira, ← mostra a coluna de configuração das seções. */
@Composable
private fun Modifier.revealRailOnLeft(enabled: Boolean = true): Modifier {
    val reveal = LocalRevealRail.current
    if (reveal == null || !enabled) return this
    return onPreviewKeyEvent { ev ->
        if (ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
            if (ev.type == KeyEventType.KeyDown) reveal()
            true
        } else false
    }
}

/** Identificador de seção da fileira (para o painel lateral e os cabeçalhos à esquerda). */
private fun HomeRowModel.sectionId(): String? = when (this) {
    is HomeRowModel.Items -> section.id
    is HomeRowModel.Programs -> section?.id
    is HomeRowModel.ContentHeader -> sectionId
    HomeRowModel.Permission -> null
}

@Composable
fun HomeScreen(vm: MainViewModel, settings: LauncherSettings, onRequestEpgPermission: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val rows by vm.homeRows.collectAsState()
    val moving by vm.moving.collectAsState()
    val movingSection by vm.movingSection.collectAsState()
    val resetSignal by vm.homeResetSignal.collectAsState()

    val listState = rememberLazyListState()
    var focusedRow by remember { mutableIntStateOf(0) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var optionsFor by remember { mutableStateOf<Pair<AppInfo, HomeSection?>?>(null) }
    var pickerFor by remember { mutableStateOf<HomeSection?>(null) }
    var inputOptions by remember { mutableStateOf<TvInput?>(null) }
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    fun requester(key: String) = rowFocus.getOrPut(key) { FocusRequester() }

    LaunchedEffect(resetSignal) {
        focusedRow = 0
        listState.scrollToItem(0)
        delay(150)
        rows.firstOrNull { it !is HomeRowModel.ContentHeader && it !is HomeRowModel.Permission }?.let {
            runCatching { requester(it.key).requestFocus() }
        }
    }

    LaunchedEffect(focusedRow, settings.fixedFocus, settings.hideRowsAbove) {
        if (!settings.fixedFocus || focusedRow < 0 || focusedRow >= rows.size) return@LaunchedEffect
        var target = focusedRow
        if (target > 0 && rows[target - 1] is HomeRowModel.ContentHeader) target -= 1
        val peek = if (settings.hideRowsAbove || target == 0) 0 else with(density) { -64.dp.roundToPx() }
        // rolagem suave e contínua até a fileira (desacelera no fim), sem "saltos"
        runCatching {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (info != null) {
                listState.animateScrollBy(
                    (info.offset + peek).toFloat(),
                    androidx.compose.animation.core.tween(560, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                )
            } else listState.animateScrollToItem(target, peek)
        }
    }

    val showTop = !(settings.hideStatusOnScroll && focusedRow > 0)

    fun jumpTo(index: Int) {
        val r = rows.getOrNull(index) ?: return
        runCatching { requester(r.key).requestFocus() }
    }

    // Coluna de cabeçalhos: escondida; aparece com ← no primeiro cartão e some quando o foco sai dela.
    var railVisible by remember { mutableStateOf(false) }
    var railHadFocus by remember { mutableStateOf(false) }
    val railFocus = remember { FocusRequester() }
    LaunchedEffect(railVisible) {
        if (railVisible) { delay(90); runCatching { railFocus.requestFocus() } } else railHadFocus = false
    }
    LaunchedEffect(settings.leftHeaders) { if (!settings.leftHeaders) railVisible = false }
    BackHandler(enabled = railVisible) {
        railVisible = false
        vm.movingSection.value = null
        focusedKey?.let { runCatching { requester(it).requestFocus() } }
    }
    val reveal: (() -> Unit)? = if (settings.leftHeaders && moving == null) ({ railVisible = true }) else null

    Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = settings.leftHeaders && railVisible,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Box(Modifier.onFocusChanged { st ->
                if (st.hasFocus) railHadFocus = true
                else if (railHadFocus) { railVisible = false; vm.movingSection.value = null }
            }) {
                HeaderRail(
                    rows = rows, focusedKey = focusedKey, movingSection = movingSection,
                    currentFocus = railFocus,
                    onJump = { jumpTo(it) },
                    onConfigure = { id -> vm.sectionPanel.value = id },
                    onMove = { id ->
                        val start = vm.movingSection.value != id
                        vm.movingSection.value = if (start) id else null
                        toast(context, if (start) "↑ ↓ mudam a posição da seção • OK confirma" else "Posição salva ✔")
                    },
                    onMoveDelta = { id, d -> vm.moveSectionVisible(id, d) },
                )
            }
        }
        CompositionLocalProvider(LocalRevealRail provides reveal) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            AnimatedVisibility(visible = showTop, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Box(Modifier.onFocusChanged { if (it.hasFocus) focusedRow = -1 }) { TopBar(vm, settings) }
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides SmoothScrollSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (settings.fixedFocus) 420.dp else 80.dp, top = 8.dp),
            ) {
                itemsIndexed(rows, key = { _, r -> r.key }) { idx, row ->
                    val isHeaderOfFocused = row is HomeRowModel.ContentHeader && focusedRow == idx + 1
                    val target = when {
                        focusedRow == idx || isHeaderOfFocused -> 1f
                        focusedRow < 0 && idx <= 1 -> 1f
                        settings.hideRowsAbove && idx < focusedRow -> 0f
                        else -> settings.unselectedRowAlpha
                    }
                    val alpha by animateFloatAsState(target, androidx.compose.animation.core.tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "rowAlpha")
                    val isMovingSec = movingSection != null && row.sectionId() == movingSection
                    Box(
                        Modifier
                            .graphicsLayer { this.alpha = if (isMovingSec) 1f else alpha }
                            .onFocusChanged { if (it.hasFocus) { focusedRow = idx; focusedKey = row.key } }
                            .padding(top = settings.rowSpacing.dp)
                            .then(if (isMovingSec) Modifier.background(LocalUi.current.accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp)) else Modifier)
                    ) {
                        when (row) {
                            is HomeRowModel.Items -> ItemsRow(
                                row = row, vm = vm, settings = settings, moving = moving, context = context,
                                firstFocus = requester(row.key),
                                onOptions = { app -> optionsFor = app to row.section },
                                onAdd = { pickerFor = row.section },
                                onInputOptions = { inputOptions = it },
                                onConfigure = { vm.sectionPanel.value = row.section.id }
                            )
                            is HomeRowModel.ContentHeader -> AppSectionHeader(row, settings.startMargin)
                            is HomeRowModel.Programs -> ProgramsRow(
                                row = row, vm = vm, settings = settings, context = context,
                                firstFocus = requester(row.key),
                                rowFocused = focusedRow == idx,
                                onConfigure = { row.section?.id?.let { vm.sectionPanel.value = it } ?: run { vm.channelsOpen.value = true } }
                            )
                            HomeRowModel.Permission -> Box(Modifier.focusRequester(requester(row.key))) { PermissionCard(settings.startMargin, onRequestEpgPermission) }
                        }
                    }
                }
                item(key = "footer") {
                    Text(
                        "Spectra Launcher 3  •  por Heitor Campos   —   MENU ☰ abre o painel  •  segure OK para opções",
                        color = Spectra.TextSecondary.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = settings.startMargin.dp, top = 24.dp)
                    )
                }
            }
            }
        }
        }
    }

    optionsFor?.let { (app, section) ->
        AppOptionsDialog(app = app, section = section, vm = vm, settings = settings, onDismiss = { optionsFor = null })
    }
    pickerFor?.let { sec ->
        AppPickerDialog(
            title = "Apps da seção \"${sec.title}\"",
            apps = vm.apps.value.filter { it.packageName !in settings.hidden },
            initial = sec.apps.toSet(),
            onConfirm = { chosen ->
                val keep = sec.apps.filter { it in chosen }
                vm.sectionsDo { update(sec.id) { it.copy(apps = keep + (chosen - keep.toSet())) } }
            },
            onDismiss = { pickerFor = null }
        )
    }
    inputOptions?.let { inp -> InputOptionsDialog(inp, vm, settings) { inputOptions = null } }
}

// ------------------------------------------------------------------------------------------ cabeçalhos à esquerda

@Composable
private fun RailIcon(row: HomeRowModel, size: Dp) {
    when (row) {
        is HomeRowModel.Items -> Text(row.section.icon, fontSize = (size.value * 0.55f).sp, color = LocalUi.current.accent)
        is HomeRowModel.Programs -> if (row.app != null && row.section?.type != SectionType.WATCH_NEXT) AppIconSmall(row.app.packageName, size)
        else Text(row.section?.icon ?: "▶", fontSize = (size.value * 0.55f).sp, color = LocalUi.current.accent)
        else -> Unit
    }
}

/**
 * Coluna de ícones à esquerda (como no Projectivy): mostra as seções; a atual fica destacada com
 * ⚙ (configurar a seção no painel lateral) e ⇅ (mover a seção com ↑/↓).
 */
@Composable
private fun HeaderRail(
    rows: List<HomeRowModel>,
    focusedKey: String?,
    movingSection: String?,
    currentFocus: FocusRequester,
    onJump: (Int) -> Unit,
    onConfigure: (String) -> Unit,
    onMove: (String) -> Unit,
    onMoveDelta: (String, Int) -> Unit,
) {
    val accent = LocalUi.current.accent
    val entries = rows.withIndex().filter { it.value is HomeRowModel.Items || it.value is HomeRowModel.Programs }
    val railState = rememberLazyListState()
    val current = entries.indexOfFirst { it.value.key == focusedKey }
    val focusTarget = if (current >= 0) current else 0
    LaunchedEffect(current) { if (current >= 0) runCatching { railState.animateScrollToItem((current - 2).coerceAtLeast(0)) } }
    LazyColumn(
        state = railState,
        modifier = Modifier.width(110.dp).fillMaxHeight(),
        contentPadding = PaddingValues(top = 150.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.End
    ) {
        itemsIndexed(entries, key = { _, e -> "rail_" + e.value.key }) { pos, e ->
            val isCur = e.value.key == focusedKey
            val secId = e.value.sectionId()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
                if (isCur && secId != null) {
                    FocusCard(Modifier.size(30.dp), corner = 15.dp, glow = false, background = Color.Black.copy(alpha = 0.35f),
                        onVerticalMove = if (movingSection == secId) ({ d: Int -> onMoveDelta(secId, d) }) else null,
                        onClick = { onMove(secId) }) { f ->
                        Text(if (movingSection == secId) "↕" else "⇅", color = if (f || movingSection == secId) accent else Color.White,
                            fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
                    }
                    Spacer(Modifier.width(4.dp))
                    FocusCard(Modifier.size(30.dp), corner = 15.dp, glow = false, background = Color.Black.copy(alpha = 0.35f),
                        onClick = { onConfigure(secId) }) { f ->
                        Text("⚙", color = if (f) accent else Color.White, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                val size = if (isCur) 40.dp else 28.dp
                FocusCard(Modifier.size(size + 8.dp).then(if (pos == focusTarget) Modifier.focusRequester(currentFocus) else Modifier),
                    corner = 10.dp, glow = false, focusScale = 1.15f,
                    background = if (isCur) accent.copy(alpha = 0.22f) else Color.Transparent,
                    onClick = { onJump(e.index) }) { _ ->
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (isCur) 1f else 0.55f }, contentAlignment = Alignment.Center) {
                        RailIcon(e.value, size)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ fileira de apps / atalhos / entradas

@Composable
private fun SlidingProvider(enabled: Boolean, startMargin: Int, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val px = with(LocalDensity.current) { startMargin.dp.toPx() }
    val spec = remember(px) { PivotSpec(px) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { content() }
}

@Composable
private fun ItemsRow(
    row: HomeRowModel.Items,
    vm: MainViewModel,
    settings: LauncherSettings,
    moving: MovingApp?,
    context: Context,
    firstFocus: FocusRequester,
    onOptions: (AppInfo) -> Unit,
    onAdd: () -> Unit,
    onInputOptions: (TvInput) -> Unit,
    onConfigure: () -> Unit,
) {
    val sec = row.section
    @Composable
    fun Item(i: Int, item: HomeItem) {
        Box(if (i == 0) Modifier.focusRequester(firstFocus).revealRailOnLeft(enabled = moving == null) else Modifier) {
            when (item) {
                is HomeItem.App -> {
                    val isMoving = moving?.sectionId == sec.id && moving.pkg == item.app.packageName
                    AppCard(
                        app = item.app, style = row.style, size = sec.appSizes[item.app.packageName] ?: row.size,
                        moving = isMoving,
                        onMoveKey = { d -> vm.moveApp(d) },
                        onLongClick = { onOptions(item.app) },
                        onClick = {
                            if (isMoving) vm.moving.value = null
                            else if (!vm.appsRepo.launch(item.app)) toast(context, "Não foi possível abrir ${item.app.label}")
                        }
                    )
                }
                is HomeItem.Input -> {
                    val inp = item.input
                    ActionCard(
                        label = inp.label, icon = inp.kind.icon, style = row.style, size = row.size,
                        tint = if (inp.kind == TvInput.Kind.CAST) Color(0xFF2979FF) else Spectra.Violet,
                        subtitle = if (inp.isDemo) "exemplo" else null,
                        onLongClick = { onInputOptions(inp) },
                        onClick = {
                            if (inp.isDemo) toast(context, "${inp.label}: entrada de exemplo (esta TV não tem entradas físicas)")
                            else if (!vm.system.openInput(inp, settings.castPackage)) toast(context, "Não foi possível abrir ${inp.label}")
                        }
                    )
                }
                is HomeItem.Shortcut -> ActionCard(
                    label = item.kind.label, icon = item.kind.icon, style = row.style, size = row.size,
                    onLongClick = onConfigure,
                    onClick = { runShortcut(item.kind, vm) }
                )
            }
        }
    }
    @Composable
    fun AddCardInner() {
        ActionCard(
            label = if (row.items.isEmpty()) "Adicionar apps" else "Adicionar", icon = "+",
            style = row.style, size = row.size, tint = Color.White.copy(alpha = 0.25f),
            onLongClick = onConfigure, onClick = onAdd
        )
    }
    @Composable
    fun AddCard() {
        Box(if (row.items.isEmpty()) Modifier.focusRequester(firstFocus).revealRailOnLeft() else Modifier) { AddCardInner() }
    }
    val showAdd = sec.type == SectionType.CUSTOM
    Column {
        if (sec.showTitle && settings.showSectionTitles) RowTitle(sec.title, sec.icon, settings)
        if (sec.grid) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardW = appCardWidth(row.style, row.size)
                val avail = maxWidth - (settings.startMargin * 2).dp
                val cols = ((avail + settings.cardSpacing.dp) / (cardW + settings.cardSpacing.dp)).toInt().coerceAtLeast(1)
                val all: List<HomeItem?> = row.items + if (showAdd) listOf<HomeItem?>(null) else emptyList()
                Column(
                    Modifier.padding(start = settings.startMargin.dp, end = settings.startMargin.dp, top = 16.dp, bottom = 8.dp).focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(settings.cardSpacing.dp)
                ) {
                    all.chunked(cols).forEachIndexed { r, chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(settings.cardSpacing.dp)) {
                            chunk.forEachIndexed { c, it -> if (it == null) AddCard() else Item(r * cols + c, it) }
                        }
                    }
                }
            }
        } else {
            SlidingProvider(sec.sliding, settings.startMargin) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = settings.startMargin.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(settings.cardSpacing.dp)
                ) {
                    itemsIndexed(row.items, key = { _, it -> it.key }) { i, item -> Item(i, item) }
                    if (showAdd) item(key = "__add_${sec.id}") { AddCard() }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ fileira de programas (canais / ver a seguir)

private fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "${h}h${m}min" else "${m}min${sec}s"
}

private fun fmtClock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun ProgramsRow(
    row: HomeRowModel.Programs,
    vm: MainViewModel,
    settings: LauncherSettings,
    context: Context,
    firstFocus: FocusRequester,
    rowFocused: Boolean,
    onConfigure: () -> Unit,
) {
    val sec = row.section
    val size = sec?.cardSize ?: settings.cardSize
    val programH = programHeight(size)
    var focusedProgram by remember { mutableStateOf<TvProgram?>(null) }
    val showTitle = (sec?.showTitle ?: true) && settings.showSectionTitles

    @Composable
    fun Card(i: Int, p: TvProgram) {
        Box(if (i == 0) Modifier.focusRequester(firstFocus).revealRailOnLeft() else Modifier) {
            ProgramCard(
                program = p, height = programH,
                onFocus = { focusedProgram = p },
                onLongClick = onConfigure,
                onClick = { if (!vm.tvRepo.open(p, row.channel, vm.appsRepo)) toast(context, "Não foi possível abrir") }
            )
        }
    }

    Column {
        if (showTitle) {
            Row(Modifier.padding(start = settings.startMargin.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (settings.showSectionIcons) {
                    if (row.app != null) { AppIconSmall(row.app.packageName, 24.dp); Spacer(Modifier.width(10.dp)) }
                    else Text(row.emoji, color = LocalUi.current.accent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
                }
                Text(row.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (sec?.grid == true) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardW = programH * 1.6f
                val avail = maxWidth - (settings.startMargin * 2).dp
                val cols = ((avail + settings.cardSpacing.dp) / (cardW + settings.cardSpacing.dp)).toInt().coerceAtLeast(1)
                Column(
                    Modifier.padding(start = settings.startMargin.dp, top = 16.dp, bottom = 8.dp).focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(settings.cardSpacing.dp)
                ) {
                    row.programs.chunked(cols).forEachIndexed { r, chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(settings.cardSpacing.dp)) {
                            chunk.forEachIndexed { c, p -> Card(r * cols + c, p) }
                        }
                    }
                }
            }
        } else {
            SlidingProvider(sec?.sliding ?: true, settings.startMargin) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = settings.startMargin.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(settings.cardSpacing.dp)
                ) {
                    itemsIndexed(row.programs, key = { _, it -> "${it.watchNextType ?: "p"}_${it.id}" }) { i, p -> Card(i, p) }
                }
            }
        }
        // Informações do item focado, logo abaixo da fileira (como no Projectivy)
        val p = focusedProgram
        AnimatedVisibility(visible = rowFocused && p != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            if (p != null) ProgramInfo(p, vm, settings.startMargin)
        }
    }
}

@Composable
private fun ProgramInfo(p: TvProgram, vm: MainViewModel, start: Int) {
    Row(Modifier.padding(start = start.dp, end = 40.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconSmall(p.packageName, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(vm.appsRepo.label(p.packageName), color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.heightIn(min = 40.dp)) {
            Text(p.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.durationMs > 0) Text(fmtDuration(p.durationMs), color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            val parts = buildList {
                p.subtitle?.let { add(it) }
                if (p.positionMs > 0) add("Continuar de ${fmtClock(p.positionMs)}")
                if (isEmpty()) p.description?.let { add(it) }
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

