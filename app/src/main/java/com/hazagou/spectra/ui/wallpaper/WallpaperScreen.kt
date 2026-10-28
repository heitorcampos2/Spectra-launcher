package com.hazagou.spectra.ui.wallpaper

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hazagou.spectra.MainViewModel
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.data.WallCategory
import com.hazagou.spectra.data.WallSource
import com.hazagou.spectra.data.WallpaperItem
import com.hazagou.spectra.data.WallpaperRepository
import com.hazagou.spectra.ui.components.FocusCard
import com.hazagou.spectra.ui.components.PillButton
import com.hazagou.spectra.ui.home.toast
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

private val GOOGLE_SUGGESTIONS = listOf(
    "dark purple aesthetic wallpaper 4k",
    "anime wallpaper 4k",
    "ghost pokemon wallpaper 4k",
    "space nebula wallpaper 4k",
    "cyberpunk city wallpaper 4k",
    "minimalist dark wallpaper 4k",
    "dragon fantasy wallpaper 4k",
    "lion wallpaper 4k",
    "cute cat wallpaper 4k",
)

private sealed interface Preview {
    data class Remote(val item: WallpaperItem) : Preview
    data class Local(val file: File) : Preview
}

@Composable
fun WallpaperScreen(vm: MainViewModel, settings: LauncherSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = vm.wallRepo

    var source by remember { mutableStateOf(WallSource.CATEGORIES) }
    var category by remember { mutableStateOf(WallpaperRepository.CATEGORIES.first()) }
    var rank by remember { mutableStateOf(com.hazagou.spectra.data.WallRank.TOP_MONTH) }
    var res by remember { mutableStateOf(com.hazagou.spectra.data.WallRes.FHD) }
    var theme by remember { mutableStateOf<WallCategory?>(null) }
    var query by remember { mutableStateOf("") }
    var onlyHq by remember { mutableStateOf(true) }
    val results = remember { mutableStateListOf<WallpaperItem>() }
    var page by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var webMode by remember { mutableStateOf(false) }
    var savedFiles by remember { mutableStateOf(repo.saved()) }
    val firstTab = remember { FocusRequester() }

    val jobHolder = remember { arrayOfNulls<Job>(1) }
    fun load(reset: Boolean) {
        if (!reset && loading) return
        jobHolder[0]?.cancel()
        if (reset) { results.clear(); page = 0 }
        loading = true; error = null
        val src = source; val cat = category; val q = query; val hq = onlyHq; val pg = page
        val rk = rank; val rs = res; val th = theme
        jobHolder[0] = scope.launch {
            val me = coroutineContext[Job]
            try {
                val list = when (src) {
                    WallSource.CATEGORIES -> repo.byCategory(cat, pg + 1)
                    WallSource.RANKED -> repo.ranked(rk, rs, th, pg + 1)
                    WallSource.GOOGLE -> repo.searchGoogle(q, pg, settings.googleApiKey, settings.googleCx, hq)
                    WallSource.SAVED, WallSource.SLIDESHOW -> emptyList()
                }
                val known = results.map { it.id }.toSet()
                results.addAll(list.filter { it.id !in known })
                page = pg + 1
                if (results.isEmpty()) error = if (src == WallSource.GOOGLE)
                    "Nenhuma imagem grande encontrada. Tente outra busca ou use o modo navegador." else "Nada encontrado."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Falha ao carregar: ${e.message ?: "sem conexão"}"
            } finally {
                if (jobHolder[0] === me) loading = false
            }
        }
    }

    fun apply(file: File) {
        scope.launch {
            vm.settingsRepo.setWallpaperPath(file.absolutePath)
            if (settings.applySystemWallpaper) repo.applyToSystem(file)
            toast(context, "Wallpaper aplicado ✨")
            vm.goHome()
        }
    }

    fun selectRemote(url: String) {
        busy = "Baixando em alta qualidade…"
        scope.launch {
            runCatching { repo.download(url) }
                .onSuccess { busy = null; savedFiles = repo.saved(); apply(it) }
                .onFailure { busy = null; toast(context, "Não foi possível baixar esta imagem (${it.message}). Tente outra.") }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { repo.import(uri) }.onSuccess { savedFiles = repo.saved(); apply(it) }
                .onFailure { toast(context, "Arquivo inválido") }
        }
    }

    LaunchedEffect(source, category, rank, res, theme) {
        if (source == WallSource.CATEGORIES || source == WallSource.RANKED) load(true)
        if (source == WallSource.SAVED || source == WallSource.SLIDESHOW) savedFiles = repo.saved()
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstTab.requestFocus() } }

    BackHandler(enabled = preview != null || webMode) {
        if (preview != null) preview = null else webMode = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
            // ---------------- topo
            Row(Modifier.padding(horizontal = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wallpapers", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Spacer(Modifier.width(24.dp))
                Row(Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WallSource.entries.forEachIndexed { i, s ->
                        PillButton(
                            s.label,
                            modifier = if (i == 0) Modifier.focusRequester(firstTab) else Modifier,
                            leading = when (s) { WallSource.CATEGORIES -> "🗂"; WallSource.RANKED -> "🏆"; WallSource.GOOGLE -> "🔎"; WallSource.SAVED -> "💾"; WallSource.SLIDESHOW -> "🎞" },
                            selected = source == s
                        ) { source = s; webMode = false; if (s == WallSource.GOOGLE) { results.clear(); error = null } }
                    }
                    PillButton("Arquivo / pendrive", leading = "📁") {
                        runCatching { pickFile.launch(arrayOf("image/*", "video/*")) }.onFailure { toast(context, "Seletor de arquivos indisponível nesta TV") }
                    }
                    PillButton("Arte padrão", leading = "✦") {
                        scope.launch { vm.settingsRepo.setWallpaperPath(null); toast(context, "Arte padrão restaurada"); vm.goHome() }
                    }
                }
                PillButton("Voltar", leading = "←") { vm.goHome() }
            }

            // ---------------- filtros por fonte
            when (source) {
                WallSource.CATEGORIES -> {
                    LazyRow(
                        Modifier.fillMaxWidth().focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(WallpaperRepository.CATEGORIES, key = { it.id }) { c: WallCategory ->
                            PillButton(c.label, leading = c.emoji, selected = c == category) { category = c }
                        }
                    }
                }
                WallSource.RANKED -> {
                    LazyRow(
                        Modifier.fillMaxWidth().focusRestorer(),
                        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 16.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(com.hazagou.spectra.data.WallRank.entries.toList(), key = { "r_" + it.name }) { r ->
                            PillButton(r.label, leading = r.emoji, selected = r == rank) { rank = r }
                        }
                        item(key = "sep") { Spacer(Modifier.width(18.dp)) }
                        items(com.hazagou.spectra.data.WallRes.entries.toList(), key = { "res_" + it.name }) { r ->
                            PillButton(r.label, leading = "▣", selected = r == res) { res = r }
                        }
                    }
                    LazyRow(
                        Modifier.fillMaxWidth().focusRestorer(),
                        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 4.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "all") { PillButton("Todos os temas", leading = "✦", selected = theme == null) { theme = null } }
                        items(WallpaperRepository.CATEGORIES.filter { it.query != null }, key = { "t_" + it.id }) { c ->
                            PillButton(c.label, leading = c.emoji, selected = c == theme) { theme = c }
                        }
                    }
                }
                WallSource.GOOGLE -> {
                    Row(Modifier.padding(horizontal = 56.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("Buscar no Google Imagens (ex.: dragão roxo 4k)") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) load(true) }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = LocalUi.current.accent, unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedContainerColor = Spectra.Surface, unfocusedContainerColor = Spectra.Surface,
                            ),
                            modifier = Modifier.width(560.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        PillButton("Buscar", leading = "🔍") { if (query.isNotBlank()) load(true) else toast(context, "Digite algo para buscar") }
                        Spacer(Modifier.width(10.dp))
                        PillButton(if (onlyHq) "Só alta qualidade: SIM" else "Só alta qualidade: NÃO", leading = "HD", selected = onlyHq) { onlyHq = !onlyHq }
                        Spacer(Modifier.width(10.dp))
                        PillButton("Modo navegador", leading = "🌐") { webMode = true }
                    }
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(GOOGLE_SUGGESTIONS) { s -> PillButton(s) { query = s; load(true) } }
                    }
                }
                WallSource.SAVED -> Spacer(Modifier.height(12.dp))
                WallSource.SLIDESHOW -> {
                    val opts = listOf(15, 30, 60, 120, 300, 600, 1800, 3600)
                    fun fmt(sec: Int) = if (sec < 60) "$sec s" else if (sec < 3600) "${sec / 60} min" else "${sec / 3600} h"
                    val selected = settings.slideshowFiles.filter { p -> savedFiles.any { it.absolutePath == p } }
                    LazyRow(
                        Modifier.fillMaxWidth().focusRestorer(),
                        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 16.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            PillButton(if (settings.slideshowEnabled) "Apresentação: LIGADA" else "Apresentação: DESLIGADA", leading = "🎞", selected = settings.slideshowEnabled) {
                                if (!settings.slideshowEnabled && selected.size < 2) toast(context, "Marque pelo menos 2 wallpapers (OK em cada um)")
                                else scope.launch { vm.settingsRepo.setSlideshowEnabled(!settings.slideshowEnabled) }
                            }
                        }
                        item {
                            PillButton("Trocar a cada ${fmt(settings.slideshowSeconds)}", leading = "⏱") {
                                val i = opts.indexOf(settings.slideshowSeconds)
                                scope.launch { vm.settingsRepo.setSlideshowSeconds(opts[(i + 1).mod(opts.size)]) }
                            }
                        }
                        item {
                            PillButton(if (settings.slideshowShuffle) "Ordem: aleatória" else "Ordem: em sequência", leading = "🔀", selected = settings.slideshowShuffle) {
                                scope.launch { vm.settingsRepo.setSlideshowShuffle(!settings.slideshowShuffle) }
                            }
                        }
                        item {
                            PillButton("Marcar todos", leading = "☑") {
                                scope.launch { vm.settingsRepo.setSlideshowFiles(savedFiles.filterNot { repo.isVideo(it) }.map { it.absolutePath }) }
                            }
                        }
                        item { PillButton("Limpar", leading = "☐") { scope.launch { vm.settingsRepo.setSlideshowFiles(emptyList()); vm.settingsRepo.setSlideshowEnabled(false) } } }
                    }
                    Text(
                        "OK marca/desmarca • ${selected.size} selecionados • a transição usa o efeito escolhido em Personalizar › Fundo",
                        color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 56.dp)
                    )
                }
            }

            // ---------------- grade
            val accent = LocalUi.current.accent
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (source == WallSource.SLIDESHOW) {
                    val imgs = savedFiles.filterNot { repo.isVideo(it) }
                    items(imgs, key = { "s_" + it.path }) { f ->
                        val on = f.absolutePath in settings.slideshowFiles
                        Thumb(model = f, label = if (on) "Na apresentação" else null, badge = if (repo.isGif(f)) "GIF" else null, checked = on) {
                            val cur = settings.slideshowFiles.filter { p -> imgs.any { it.absolutePath == p } }
                            val next = if (on) cur - f.absolutePath else cur + f.absolutePath
                            scope.launch {
                                vm.settingsRepo.setSlideshowFiles(next)
                                if (next.size < 2) vm.settingsRepo.setSlideshowEnabled(false)
                            }
                        }
                    }
                    if (imgs.isEmpty()) item(span = { GridItemSpan(4) }) {
                        Text("Escolha wallpapers nas abas Categorias, Classificação ou Google: eles ficam salvos e aparecem aqui para marcar.", color = Spectra.TextSecondary)
                    }
                } else if (source == WallSource.SAVED) {
                    items(savedFiles, key = { it.path }) { f ->
                        Thumb(
                            model = f, label = if (f.absolutePath == settings.wallpaperPath) "Em uso" else null,
                            badge = when { repo.isVideo(f) -> "▶ VÍDEO"; repo.isGif(f) -> "GIF"; else -> null }
                        ) {
                            preview = Preview.Local(f)
                        }
                    }
                    if (savedFiles.isEmpty()) item(span = { GridItemSpan(4) }) {
                        Text("Os wallpapers que você escolher aparecem aqui.", color = Spectra.TextSecondary)
                    }
                } else {
                    items(results, key = { it.id }) { item ->
                        Thumb(
                            model = item.thumbUrl,
                            label = "${item.source} · ${item.resolutionLabel}",
                            badge = if (item.width >= 3840) "4K" else if (item.isHighQuality) "HD" else null
                        ) { preview = Preview.Remote(item) }
                    }
                    item(span = { GridItemSpan(4) }) {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            when {
                                loading -> CircularProgressIndicator(color = accent)
                                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(error!!, color = Spectra.TextSecondary)
                                    if (source == WallSource.GOOGLE) {
                                        Spacer(Modifier.height(10.dp))
                                        PillButton("Abrir modo navegador", leading = "🌐") { webMode = true }
                                    }
                                }
                                results.isNotEmpty() -> PillButton("Carregar mais", leading = "⟳") { load(false) }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- pré-visualização com botão de seleção
        preview?.let { p ->
            PreviewOverlay(
                preview = p,
                onSelect = {
                    when (p) {
                        is Preview.Remote -> { preview = null; selectRemote(p.item.fullUrl) }
                        is Preview.Local -> { preview = null; apply(p.file) }
                    }
                },
                onDelete = if (p is Preview.Local) ({ repo.delete(p.file); savedFiles = repo.saved(); preview = null }) else null,
                onClose = { preview = null }
            )
        }

        if (webMode) {
            GoogleWebPicker(
                query = query.ifBlank { "wallpaper 4k" },
                onPicked = { url -> webMode = false; selectRemote(url) },
                onClose = { webMode = false }
            )
        }

        busy?.let { msg ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LocalUi.current.accent)
                    Spacer(Modifier.height(14.dp))
                    Text(msg, color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Thumb(model: Any, label: String?, badge: String?, checked: Boolean? = null, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val req = remember(model) { ImageRequest.Builder(ctx).data(model).size(480, 270).crossfade(true).build() }
    FocusCard(Modifier.fillMaxWidth().aspectRatio(16f / 9f), onClick = onClick) { focused ->
        AsyncImage(req, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (checked != null) {
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).size(30.dp)
                    .background(if (checked) LocalUi.current.accent else Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { if (checked) Text("✔", color = Color.White, style = MaterialTheme.typography.titleSmall) }
            if (checked) Box(Modifier.fillMaxSize().background(LocalUi.current.accent.copy(alpha = 0.12f)))
        }
        if (label != null && (focused || label == "Em uso" || checked == true)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        if (badge != null) {
            Text(
                badge, color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .background(LocalUi.current.accent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PreviewOverlay(preview: Preview, onSelect: () -> Unit, onDelete: (() -> Unit)?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(preview) { delay(60); runCatching { first.requestFocus() } }
    val (data, info) = when (preview) {
        is Preview.Remote -> preview.item.fullUrl to "${preview.item.source} · ${preview.item.resolutionLabel}"
        is Preview.Local -> preview.file to "Salvo no aparelho"
    }
    val thumb = (preview as? Preview.Remote)?.item?.thumbUrl
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (thumb != null) AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        AsyncImage(
            ImageRequest.Builder(ctx).data(data).size(1920, 1080).crossfade(true).build(),
            null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        Column(Modifier.align(Alignment.BottomStart).padding(56.dp)) {
            Text("Pré-visualização", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(info, color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    if (preview is Preview.Remote) "Selecionar (alta qualidade)" else "Aplicar",
                    leading = "✔", modifier = Modifier.focusRequester(first), selected = true, onClick = onSelect
                )
                if (onDelete != null) PillButton("Excluir", leading = "🗑", onClick = onDelete)
                PillButton("Voltar", leading = "←", onClick = onClose)
            }
        }
    }
}

/**
 * Modo navegador: abre o Google Imagens de verdade dentro do launcher. O usuário navega, abre a imagem
 * desejada e aperta MENU (☰) ou o botão "Selecionar" — o Spectra pega a maior imagem carregada na tela
 * (a versão original em alta resolução) e aplica como wallpaper.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GoogleWebPicker(query: String, onPicked: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    val js = """
        (function(){var best=null,ba=0;document.querySelectorAll('img').forEach(function(i){
          var s=i.currentSrc||i.src; if(!s||s.indexOf('http')!==0||s.indexOf('gstatic.com')>0||s.indexOf('google.com')>0)return;
          var a=(i.naturalWidth||0)*(i.naturalHeight||0); if(a>ba){ba=a;best=s;}}); return best;})()
    """.trimIndent()

    fun pick() {
        webView?.evaluateJavascript(js) { result ->
            val url = runCatching { if (result == null || result == "null") null else JSONArray("[$result]").getString(0) }.getOrNull()
            if (url.isNullOrBlank()) toast(ctx, "Abra uma imagem primeiro (clique nela) e tente de novo")
            else onPicked(url)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = WebViewClient()
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnKeyListener { v, keyCode, event ->
                        if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener keyCode == KeyEvent.KEYCODE_MENU
                        when (keyCode) {
                            KeyEvent.KEYCODE_MENU -> { pick(); true }
                            KeyEvent.KEYCODE_BACK -> { val w = v as WebView; if (w.canGoBack()) w.goBack() else onClose(); true }
                            else -> false
                        }
                    }
                    loadUrl("https://www.google.com/search?tbm=isch&tbs=isz:l&q=" + java.net.URLEncoder.encode(query, "UTF-8"))
                    requestFocus()
                    webView = this
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize().padding(bottom = 64.dp)
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Spectra.SurfaceHigh).padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Abra a imagem desejada e aperte MENU ☰ para selecionar", color = Color.White, modifier = Modifier.weight(1f))
            PillButton("Selecionar imagem aberta", leading = "✔", selected = true) { pick() }
            PillButton("Fechar", leading = "✕") { onClose() }
        }
    }
}
