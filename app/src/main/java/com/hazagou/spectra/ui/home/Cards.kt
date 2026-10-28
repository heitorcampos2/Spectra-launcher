package com.hazagou.spectra.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hazagou.spectra.data.AppInfo
import com.hazagou.spectra.data.AppRepository
import com.hazagou.spectra.data.CardStyle
import com.hazagou.spectra.data.TvProgram
import com.hazagou.spectra.ui.components.FocusCard
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra

val LocalAppRepo = compositionLocalOf<AppRepository> { error("AppRepository não fornecido") }

/** Tamanhos: -2 = PPP, -1 = PP, 0 = P, 1 = M, 2 = G, 3 = GG, 4 = XL. */
const val MIN_SIZE = -2
const val MAX_SIZE = 4

fun sizeFactor(size: Int): Float = when (size.coerceIn(MIN_SIZE, MAX_SIZE)) {
    -2 -> 0.58f; -1 -> 0.70f; 0 -> 0.82f; 2 -> 1.21f; 3 -> 1.5f; 4 -> 1.85f; else -> 1f
}

fun sizeLabel(size: Int): String = when (size.coerceIn(MIN_SIZE, MAX_SIZE)) {
    -2 -> "PPP"; -1 -> "PP"; 0 -> "P"; 2 -> "G"; 3 -> "GG"; 4 -> "XL"; else -> "M"
}

fun appCardWidth(style: CardStyle, size: Int): Dp = when (style) {
    CardStyle.BANNER -> 214.dp
    CardStyle.ICON -> 104.dp
    CardStyle.POSTER -> 134.dp
    CardStyle.SQUARE -> 120.dp
    CardStyle.WIDE -> 290.dp
    CardStyle.COMPACT -> 236.dp
} * sizeFactor(size)

fun appCardHeight(style: CardStyle, size: Int): Dp {
    val w = appCardWidth(style, size)
    return when (style) {
        CardStyle.BANNER -> w * 9f / 16f
        CardStyle.ICON, CardStyle.SQUARE -> w
        CardStyle.POSTER -> w * 1.45f
        CardStyle.WIDE -> w * 9f / 21f
        CardStyle.COMPACT -> w * 0.30f
    }
}

fun programHeight(size: Int): Dp = 140.dp * sizeFactor(size)

@Composable
fun rememberBanner(app: AppInfo): ImageBitmap? {
    val repo = LocalAppRepo.current
    val hq = LocalUi.current.highQualityImages
    val v by produceState<ImageBitmap?>(null, app.packageName, hq) { value = repo.banner(app.packageName, app.activityName, hq) }
    return v
}

@Composable
fun rememberIcon(pkg: String): ImageBitmap? {
    val repo = LocalAppRepo.current
    val hq = LocalUi.current.highQualityImages
    val v by produceState<ImageBitmap?>(null, pkg, hq) { value = repo.icon(pkg, hq) }
    return v
}

@Composable
fun AppIconSmall(pkg: String, size: Dp, modifier: Modifier = Modifier) {
    val icon = rememberIcon(pkg)
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Color.White.copy(alpha = 0.08f))) {
        if (icon != null) Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Rótulo abaixo do cartão: aparece ao focar (ou sempre, no estilo ícone). */
@Composable
private fun CardLabel(text: String, focused: Boolean, always: Boolean) {
    val ui = LocalUi.current
    if (!ui.showLabels) return
    val a by animateFloatAsState(if (always || focused) 1f else 0f, label = "label")
    Box(Modifier.height(26.dp).padding(top = 8.dp).graphicsLayer { alpha = a }) {
        Text(
            text, style = MaterialTheme.typography.labelMedium,
            color = if (focused) Color.White else Spectra.TextSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

/** Selo mostrado no modo mover. */
@Composable
private fun MoveBadge(modifier: Modifier) {
    Text(
        "◀  mover  ▶", color = Color.White, fontSize = 11.sp,
        modifier = modifier.padding(6.dp).clip(RoundedCornerShape(8.dp))
            .background(LocalUi.current.accent).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun AppCard(
    app: AppInfo,
    style: CardStyle,
    size: Int,
    moving: Boolean = false,
    onMoveKey: ((Int) -> Unit)? = null,
    onFocus: () -> Unit = {},
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val w = appCardWidth(style, size)
    val h = appCardHeight(style, size)
    val movingMod = if (moving) Modifier.border(3.dp, Color.White, RoundedCornerShape(ui.corner.dp)) else Modifier
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(w)) {
        FocusCard(
            modifier = Modifier.width(w).height(h).onFocusChanged { focused = it.hasFocus }.then(movingMod),
            corner = if (style == CardStyle.ICON) w / 2 else if (style == CardStyle.COMPACT) h / 2 else ui.corner.dp,
            onFocus = onFocus,
            onLongClick = if (moving) null else onLongClick,
            onMoveKey = if (moving) onMoveKey else null,
            onClick = onClick
        ) { _ ->
            when (style) {
                CardStyle.BANNER, CardStyle.WIDE -> {
                    val banner = if (app.isTvApp) rememberBanner(app) else null
                    if (banner != null) Image(banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else IconOnGradient(app)
                }
                CardStyle.SQUARE -> {
                    val icon = rememberIcon(app.packageName)
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ui.accent.copy(alpha = 0.28f), Spectra.Indigo))), contentAlignment = Alignment.Center) {
                        if (icon != null) Image(icon, app.label, Modifier.fillMaxSize(0.6f))
                    }
                }
                CardStyle.COMPACT -> {
                    val icon = rememberIcon(app.packageName)
                    Row(
                        Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(ui.accent.copy(alpha = 0.30f), Spectra.SurfaceHigh))).padding(horizontal = h * 0.18f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) Image(icon, null, Modifier.size(h * 0.66f).clip(RoundedCornerShape(h * 0.2f)))
                        Spacer(Modifier.width(10.dp))
                        Text(app.label, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                CardStyle.ICON -> {
                    val icon = rememberIcon(app.packageName)
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Spectra.SurfaceHigh, Spectra.Indigo))), contentAlignment = Alignment.Center) {
                        if (icon != null) Image(icon, app.label, Modifier.fillMaxSize(0.66f))
                    }
                }
                CardStyle.POSTER -> {
                    val icon = rememberIcon(app.packageName)
                    Column(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ui.accent.copy(alpha = 0.35f), Spectra.Indigo))),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (icon != null) Image(icon, app.label, Modifier.size(w * 0.5f))
                        Spacer(Modifier.height(10.dp))
                        Text(app.label, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
            if (moving) MoveBadge(Modifier.align(Alignment.TopCenter))
        }
        if (style != CardStyle.POSTER && style != CardStyle.COMPACT) CardLabel(app.label, focused, always = style == CardStyle.ICON || style == CardStyle.SQUARE)
    }
}

/** Cartão genérico (atalhos, entradas HDMI/Cast, "+ adicionar"). */
@Composable
fun ActionCard(
    label: String,
    icon: String,
    style: CardStyle,
    size: Int,
    tint: Color? = null,
    subtitle: String? = null,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val w = appCardWidth(style, size)
    val h = appCardHeight(style, size)
    val base = tint ?: ui.accent
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(w)) {
        FocusCard(
            modifier = Modifier.width(w).height(h).onFocusChanged { focused = it.hasFocus },
            corner = if (style == CardStyle.ICON) w / 2 else if (style == CardStyle.COMPACT) h / 2 else ui.corner.dp,
            onFocus = onFocus,
            onLongClick = onLongClick,
            onClick = onClick
        ) { f ->
            Column(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(base.copy(alpha = if (f) 0.75f else 0.45f), Spectra.Indigo.copy(alpha = 0.9f)))
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(icon, fontSize = (h.value * if (style == CardStyle.BANNER) 0.3f else 0.38f).sp, color = Color.White)
                if (style != CardStyle.ICON) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
                    if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
        if (style == CardStyle.ICON) CardLabel(label, focused, always = true)
        else Spacer(Modifier.height(if (ui.showLabels) 26.dp else 0.dp))
    }
}

@Composable
private fun IconOnGradient(app: AppInfo) {
    val icon = rememberIcon(app.packageName)
    val accent = LocalUi.current.accent
    Row(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Spectra.SurfaceHigh, accent.copy(alpha = 0.35f)))).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Image(icon, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(10.dp))
        Text(app.label, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ProgramCard(
    program: TvProgram,
    height: Dp,
    /** Formato escolhido na seção (null = o formato que o app publicou). */
    format: CardStyle? = null,
    onFocus: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val aspect = when (format) {
        CardStyle.POSTER -> 2f / 3f
        CardStyle.SQUARE, CardStyle.ICON -> 1f
        CardStyle.WIDE -> 21f / 9f
        CardStyle.BANNER, CardStyle.COMPACT -> 16f / 9f
        null -> program.aspectRatio.coerceIn(0.66f, 1.78f)
    }
    val width = height * aspect
    val request = remember(program.imageUri, ui.highQualityImages) {
        ImageRequest.Builder(ctx)
            .data(program.imageUri)
            .size(if (ui.highQualityImages) 640 else 400)
            .crossfade(ui.animations)
            .build()
    }
    FocusCard(
        modifier = Modifier.width(width).height(height),
        corner = if (format == CardStyle.ICON) height / 2 else ui.corner.dp,
        onFocus = onFocus,
        onLongClick = onLongClick,
        onClick = onClick
    ) { focused ->
        AsyncImage(model = request, contentDescription = program.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = if (focused) 0.85f else 0.55f))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(program.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (focused && !program.subtitle.isNullOrBlank()) {
                Text(program.subtitle, color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            program.progress?.let { p ->
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(p).height(4.dp).background(ui.accent))
                }
            }
        }
        if (program.isLive) {
            Text(
                "AO VIVO", color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE53935)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
