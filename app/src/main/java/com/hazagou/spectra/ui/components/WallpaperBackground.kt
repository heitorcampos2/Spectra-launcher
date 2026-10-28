package com.hazagou.spectra.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.data.PerformanceMode
import com.hazagou.spectra.data.WallpaperMotion
import com.hazagou.spectra.data.WallTransition
import com.hazagou.spectra.ui.theme.Spectra
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fundo do launcher:
 *  • Parallax — a imagem desliza na direção oposta ao item focado (efeito de profundidade)
 *  • Movimento suave — zoom/pan contínuo e lento (estilo Ken Burns)
 *  • Partículas — poeira estelar flutuando (desenhada direto na GPU, sem recomposição)
 *  • Escurecimento em gradiente para legibilidade + extração automática da cor de destaque
 */
@Composable
fun WallpaperBackground(
    settings: LauncherSettings,
    parallax: ParallaxState,
    onDynamicAccent: (Color?) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val file = settings.wallpaperPath?.takeIf { settings.bgMode == com.hazagou.spectra.data.BgMode.WALLPAPER }
        ?.let { File(it) }?.takeIf { it.exists() }
    val animations = settings.performance != PerformanceMode.MAX_PERFORMANCE
    val motion = settings.wallpaperMotion
    val useParallax = animations && motion in setOf(WallpaperMotion.PARALLAX, WallpaperMotion.PARALLAX_DRIFT, WallpaperMotion.PARALLAX_KEN_BURNS)
    val useDrift = animations && motion in setOf(WallpaperMotion.DRIFT, WallpaperMotion.PARALLAX_DRIFT)
    val kenBurns = animations && motion in setOf(WallpaperMotion.KEN_BURNS, WallpaperMotion.PARALLAX_KEN_BURNS)
    val breathe = animations && motion == WallpaperMotion.BREATHE
    val pan = animations && motion == WallpaperMotion.PAN
    val floating = animations && motion == WallpaperMotion.FLOAT

    // Cor de destaque automática a partir do wallpaper
    LaunchedEffect(file?.path, file?.lastModified()) {
        if (file == null) { onDynamicAccent(null); return@LaunchedEffect }
        val req = ImageRequest.Builder(context).data(file).size(96).allowHardware(false).build()
        val result = context.imageLoader.execute(req)
        val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        if (bmp != null) {
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            val sw = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch
            onDynamicAccent(sw?.rgb?.let { Color(it) })
        }
    }

    val dpScale = density.density
    val strengthPx = with(density) { (10 + settings.parallaxStrength * 12).dp.toPx() }
    val pOffset by animateOffsetAsState(
        targetValue = if (useParallax) Offset(-parallax.target.x * strengthPx, -parallax.target.y * strengthPx * 0.6f) else Offset.Zero,
        // desliza devagar e desacelera suavemente (sem "pulo" ao trocar de cartão/fileira)
        animationSpec = tween(1600, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "parallax"
    )
    val transition = rememberInfiniteTransition(label = "wp")
    val drift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val smooth = androidx.compose.animation.core.FastOutSlowInEasing
    val kbS = if (kenBurns) transition.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = smooth), RepeatMode.Reverse), label = "kb") else null
    val brS = if (breathe) transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = smooth), RepeatMode.Reverse), label = "br") else null
    val pnS = if (pan) transition.animateFloat(0f, 1f, infiniteRepeatable(tween(70_000, easing = smooth), RepeatMode.Reverse), label = "pn") else null
    val flS = if (floating) transition.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "fl") else null
    val baseScale = when {
        useParallax && useDrift -> 1.16f
        useParallax -> 1.12f
        useDrift -> 1.1f
        else -> 1f
    }

    Box(Modifier.fillMaxSize().background(Spectra.Night)) {
        val layerMod = Modifier.fillMaxSize().graphicsLayer {
            val d = if (useDrift) drift else 0f
            val extra = if (useDrift) 0.05f * d else 0f
            scaleX = baseScale + extra
            scaleY = baseScale + extra
            translationX = pOffset.x + if (useDrift) (d - 0.5f) * 40f * dpScale else 0f
            translationY = pOffset.y + if (useDrift) sin(d * PI.toFloat()) * 14f * dpScale else 0f
            val kb = kbS?.value ?: 0f
            val br = brS?.value ?: 0f
            val pn = pnS?.value ?: 0f
            val fl = flS?.value ?: 0f
            if (kenBurns) {
                val sc = (if (useParallax) 1.14f else 1.06f) + 0.14f * kb
                scaleX = sc; scaleY = sc
                translationX = pOffset.x + (kb - 0.5f) * 70f * dpScale
                translationY = pOffset.y + (0.5f - kb) * 34f * dpScale
            }
            if (breathe) { val sc = 1.04f + 0.07f * br; scaleX = sc; scaleY = sc }
            if (pan) { scaleX = 1.24f; scaleY = 1.24f; translationX = (pn - 0.5f) * size.width * 0.18f }
            if (floating) {
                val a = fl * 2f * PI.toFloat()
                scaleX = 1.09f; scaleY = 1.09f
                translationX = sin(a) * 22f * dpScale
                translationY = sin(a * 2f) * 12f * dpScale
            }
        }
        val ext = file?.extension?.lowercase().orEmpty()
        if (file != null && ext in setOf("mp4", "mkv", "webm", "3gp", "mov")) {
            // Wallpaper em vídeo (sem parallax de escala para economizar GPU)
            VideoWallpaper(file, settings.videoVolume, Modifier.fillMaxSize())
        } else if (file != null) {
            val hq = settings.performance == PerformanceMode.MAX_QUALITY
            val blur = settings.softBlur
            fun request(f: File) = ImageRequest.Builder(context)
                .data(f)
                .apply { if (blur) size(240, 135) else size(if (hq) 3840 else 1920, if (hq) 2160 else 1080) }
                .precision(Precision.INEXACT)
                .memoryCacheKey("wall:${f.path}:${f.lastModified()}:$blur:$hq")
                .build()
            // Pré-carrega o próximo wallpaper e só então troca, com a animação escolhida (sem "piscar" escuro)
            var shown by remember { mutableStateOf(file) }
            LaunchedEffect(file.path, file.lastModified(), blur, hq) {
                runCatching { context.imageLoader.execute(request(file)) }
                shown = file
            }
            val style = if (animations) settings.wallTransition else WallTransition.NONE
            AnimatedContent(
                targetState = shown,
                modifier = layerMod,
                transitionSpec = {
                    when (style) {
                        WallTransition.FADE -> fadeIn(tween(1100)) togetherWith fadeOut(tween(1100))
                        WallTransition.ZOOM -> (fadeIn(tween(1000)) + scaleIn(tween(1400), initialScale = 1.12f)) togetherWith fadeOut(tween(1000))
                        WallTransition.SLIDE -> (slideInHorizontally(tween(1000)) { it } + fadeIn(tween(600))) togetherWith
                            (slideOutHorizontally(tween(1000)) { -it / 3 } + fadeOut(tween(1000)))
                        WallTransition.BLUR_ZOOM -> (fadeIn(tween(900)) + scaleIn(tween(1100), initialScale = 0.88f)) togetherWith
                            (fadeOut(tween(900)) + scaleOut(tween(1100), targetScale = 1.18f))
                        WallTransition.NONE -> EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "wallpaperTransition"
            ) { f ->
                val req = remember(f.path, f.lastModified(), blur, hq) { request(f) }
                AsyncImage(model = req, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        } else when (settings.bgMode) {
            com.hazagou.spectra.data.BgMode.SOLID -> Box(Modifier.fillMaxSize().background(Color(settings.bgColor1)))
            com.hazagou.spectra.data.BgMode.GRADIENT -> Box(
                layerMod.background(Brush.linearGradient(listOf(Color(settings.bgColor1), Color(settings.bgColor2))))
            )
            else -> DefaultArtBackground(layerMod)
        }

        if (settings.particles && animations) Particles(settings.particleStyle, Color(settings.accent), Modifier.fillMaxSize())

        // Escurecimento para legibilidade
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = settings.dim * 0.55f),
                    0.45f to Color.Black.copy(alpha = settings.dim * 0.8f),
                    1f to Color.Black.copy(alpha = (settings.dim * 1.6f).coerceAtMost(0.95f)),
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = settings.dim * 0.7f),
                    0.5f to Color.Transparent,
                )
            )
        )
    }
}

/** Vídeo em loop como fundo (pausa quando o launcher sai da tela). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoWallpaper(file: File, volume: Int, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val player = remember(file.path) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player, volume) { player.volume = volume / 100f }
    androidx.compose.runtime.DisposableEffect(player, lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> player.pause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            player.release()
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c ->
            androidx.media3.ui.PlayerView(c).apply {
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier
    )
}

/** Arte padrão (sem imagem): índigo profundo com brilhos magenta/violeta e nuvens estilizadas. */
@Composable
fun DefaultArtBackground(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.linearGradient(listOf(Spectra.Indigo, Color(0xFF1B1257), Color(0xFF2A1665)), Offset.Zero, Offset(size.width, size.height)))
        drawCircle(
            Brush.radialGradient(listOf(Spectra.Magenta.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.78f, size.height * 0.28f), radius = size.height * 0.55f),
            radius = size.height * 0.55f, center = Offset(size.width * 0.78f, size.height * 0.28f)
        )
        drawCircle(
            Brush.radialGradient(listOf(Spectra.Violet.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.85f), radius = size.height * 0.6f),
            radius = size.height * 0.6f, center = Offset(size.width * 0.15f, size.height * 0.85f)
        )
        // nuvens em espiral
        val cloud = Spectra.Violet.copy(alpha = 0.28f)
        val baseY = size.height * 0.86f
        val r = size.height * 0.07f
        var x = -r
        var i = 0
        while (x < size.width + r) {
            val y = baseY + (if (i % 2 == 0) 0f else -r * 0.6f)
            drawCircle(cloud, r * (if (i % 3 == 0) 1.25f else 1f), Offset(x, y))
            drawCircle(Spectra.Indigo.copy(alpha = 0.35f), r * 0.45f, Offset(x + r * 0.2f, y - r * 0.1f))
            x += r * 1.5f; i++
        }
    }
}

private class Particle(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float, val alpha: Float)

@Composable
private fun Particles(style: com.hazagou.spectra.data.ParticleStyle, accent: Color, modifier: Modifier) {
    val count = when (style) {
        com.hazagou.spectra.data.ParticleStyle.BOKEH -> 16
        com.hazagou.spectra.data.ParticleStyle.FIREFLIES -> 22
        com.hazagou.spectra.data.ParticleStyle.RAIN -> 70
        com.hazagou.spectra.data.ParticleStyle.STARS -> 90
        else -> 50
    }
    val particles = remember(style) {
        val rnd = Random(7)
        List(count) { Particle(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 2.2f, 0.2f + rnd.nextFloat() * 0.8f, rnd.nextFloat(), 0.25f + rnd.nextFloat() * 0.6f) }
    }
    val transition = rememberInfiniteTransition(label = "particles")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "t")
    val twoPi = 2f * PI.toFloat()
    Canvas(modifier) {
        val time = t // lido só na fase de desenho → sem recomposição
        for (p in particles) {
            when (style) {
                com.hazagou.spectra.data.ParticleStyle.DOTS -> {
                    val yy = ((p.y - time * p.speed * 3f) % 1f + 1f) % 1f
                    val xx = p.x + sin((time * 12f + p.phase) * twoPi) * 0.01f
                    val tw = 0.6f + 0.4f * sin((time * 40f + p.phase) * twoPi)
                    drawCircle(Color.White.copy(alpha = p.alpha * tw), p.r * density, Offset(xx * size.width, yy * size.height))
                }
                com.hazagou.spectra.data.ParticleStyle.SNOW -> {
                    val yy = ((p.y + time * (4f + p.speed * 6f)) % 1f + 1f) % 1f
                    val xx = p.x + sin((time * 18f + p.phase) * twoPi) * 0.02f
                    drawCircle(Color.White.copy(alpha = 0.35f + p.alpha * 0.5f), (1.2f + p.r) * density, Offset(xx * size.width, yy * size.height))
                }
                com.hazagou.spectra.data.ParticleStyle.STARS -> {
                    val tw = (0.5f + 0.5f * sin((time * (30f + p.speed * 40f) + p.phase) * twoPi)).let { it * it }
                    val c = Offset(p.x * size.width, p.y * size.height * 0.75f)
                    val r = (0.5f + p.r * 0.6f) * density
                    drawCircle(Color.White.copy(alpha = 0.15f + 0.85f * tw * p.alpha), r, c)
                    if (p.r > 2.2f) {
                        val l = r * 5f * tw
                        drawLine(Color.White.copy(alpha = 0.5f * tw), Offset(c.x - l, c.y), Offset(c.x + l, c.y), strokeWidth = 1f)
                        drawLine(Color.White.copy(alpha = 0.5f * tw), Offset(c.x, c.y - l), Offset(c.x, c.y + l), strokeWidth = 1f)
                    }
                }
                com.hazagou.spectra.data.ParticleStyle.BOKEH -> {
                    val xx = (p.x + sin((time * 3f + p.phase) * twoPi) * 0.05f) * size.width
                    val yy = (p.y + sin((time * 2f + p.phase * 2f) * twoPi) * 0.04f) * size.height
                    val r = (28f + p.r * 26f) * density
                    val col = if (p.phase > 0.5f) accent else Color.White
                    drawCircle(
                        Brush.radialGradient(listOf(col.copy(alpha = 0.16f * p.alpha + 0.04f), Color.Transparent), Offset(xx, yy), r),
                        r, Offset(xx, yy)
                    )
                }
                com.hazagou.spectra.data.ParticleStyle.FIREFLIES -> {
                    val xx = (p.x + sin((time * (6f + p.speed * 4f) + p.phase) * twoPi) * 0.06f) * size.width
                    val yy = (p.y * 0.6f + 0.35f + sin((time * (5f + p.speed * 3f) + p.phase * 3f) * twoPi) * 0.05f) * size.height
                    val glow = (0.5f + 0.5f * sin((time * 50f + p.phase) * twoPi)).let { it * it }
                    val c = Color(0xFFE6FF7A)
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f * glow), Color.Transparent), Offset(xx, yy), 14f * density), 14f * density, Offset(xx, yy))
                    drawCircle(c.copy(alpha = 0.9f * glow), 1.6f * density, Offset(xx, yy))
                }
                com.hazagou.spectra.data.ParticleStyle.RAIN -> {
                    val yy = ((p.y + time * (60f + p.speed * 40f)) % 1f + 1f) % 1f
                    val x0 = p.x * size.width
                    val y0 = yy * size.height
                    val len = (14f + p.r * 8f) * density
                    drawLine(Color.White.copy(alpha = 0.12f + p.alpha * 0.25f), Offset(x0, y0), Offset(x0 - len * 0.25f, y0 + len), strokeWidth = 1.2f * density)
                }
            }
        }
    }
}
