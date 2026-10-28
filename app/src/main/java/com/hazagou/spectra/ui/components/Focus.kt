package com.hazagou.spectra.ui.components

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra

/** Alvo do parallax: (-1..1, -1..1) relativo ao centro da tela. Atualizado pelo item focado. */
class ParallaxState {
    var target by mutableStateOf(Offset.Zero)
}

val LocalParallax = compositionLocalOf { ParallaxState() }

/**
 * Cartão focável otimizado para controle remoto:
 *  • zoom suave com mola + borda luminosa na cor de destaque
 *  • OK = clique, segurar OK ou tecla MENU = opções (long press)
 *  • informa a posição ao sistema de parallax do wallpaper
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    corner: Dp = LocalUi.current.corner.dp,
    focusScale: Float = LocalUi.current.focusScale,
    glow: Boolean = true,
    background: Color = LocalUi.current.surface,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** Quando não nulo, ←/→ movem o item (modo mover) em vez de mudar o foco. */
    onMoveKey: ((Int) -> Unit)? = null,
    /** ←/→ ajustam um valor (sliders das configurações); ↑/↓ continuam navegando. */
    onAdjust: ((Int) -> Unit)? = null,
    /** ↑/↓ movem (ex.: reordenar seção) em vez de navegar. */
    onVerticalMove: ((Int) -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val ui = LocalUi.current
    val parallax = LocalParallax.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    val press = remember { PressState() }
    val scope = rememberCoroutineScope()
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        animationSpec = if (ui.animations) spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "focusScale"
    )
    val shape = RoundedCornerShape(corner)
    val accent = ui.accent

    Box(
        modifier
            .zIndex(if (focused) 2f else 0f)
            .onGloballyPositioned { coords[0] = it }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (focused && glow) {
                    val pad = 10.dp.toPx()
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.55f), Color.Transparent),
                            center = center, radius = size.maxDimension * 0.75f
                        ),
                        topLeft = Offset(-pad, -pad),
                        size = androidx.compose.ui.geometry.Size(size.width + pad * 2, size.height + pad * 2),
                        cornerRadius = CornerRadius(corner.toPx() + pad)
                    )
                }
            }
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) accent else Color.White.copy(alpha = 0.06f),
                shape = shape
            )
            .onFocusChanged { st ->
                focused = st.isFocused
                if (!st.isFocused) { press.down = false; press.job?.cancel() }
                if (st.isFocused) {
                    onFocus?.invoke()
                    coords[0]?.takeIf { it.isAttached }?.let { c ->
                        val b = c.boundsInWindow()
                        val w = with(density) { config.screenWidthDp.dp.toPx() }
                        val h = with(density) { config.screenHeightDp.dp.toPx() }
                        if (w > 0 && h > 0) {
                            parallax.target = Offset(
                                ((b.center.x / w) - 0.5f) * 2f,
                                ((b.center.y / h) - 0.5f) * 2f
                            )
                        }
                    }
                }
            }
            .onPreviewKeyEvent { ev ->
                val code = ev.nativeKeyEvent.keyCode
                val isOk = code == NativeKeyEvent.KEYCODE_DPAD_CENTER || code == NativeKeyEvent.KEYCODE_ENTER ||
                    code == NativeKeyEvent.KEYCODE_NUMPAD_ENTER || code == NativeKeyEvent.KEYCODE_BUTTON_A
                when {
                    onMoveKey != null && (code == NativeKeyEvent.KEYCODE_DPAD_LEFT || code == NativeKeyEvent.KEYCODE_DPAD_RIGHT) -> {
                        if (ev.type == KeyEventType.KeyDown) onMoveKey(if (code == NativeKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        true
                    }
                    onMoveKey != null && (code == NativeKeyEvent.KEYCODE_DPAD_UP || code == NativeKeyEvent.KEYCODE_DPAD_DOWN) -> true
                    onVerticalMove != null && (code == NativeKeyEvent.KEYCODE_DPAD_UP || code == NativeKeyEvent.KEYCODE_DPAD_DOWN) -> {
                        if (ev.type == KeyEventType.KeyDown) onVerticalMove(if (code == NativeKeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                        true
                    }
                    onAdjust != null && (code == NativeKeyEvent.KEYCODE_DPAD_LEFT || code == NativeKeyEvent.KEYCODE_DPAD_RIGHT) -> {
                        if (ev.type == KeyEventType.KeyDown) onAdjust(if (code == NativeKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        true
                    }
                    // Toque rápido no OK = abrir; segurar o OK (≥ 0,55 s) = opções.
                    // Só reage a um aperto que COMEÇOU neste cartão: assim, ao soltar o OK depois de abrir
                    // as opções, o "soltar" não aciona sem querer o primeiro item do menu.
                    isOk && ev.type == KeyEventType.KeyDown -> {
                        val n = ev.nativeKeyEvent
                        if (n.repeatCount == 0) {
                            press.down = true
                            press.longFired = false
                            press.job?.cancel()
                            if (onLongClick != null) press.job = scope.launch {
                                delay(LONG_PRESS_MS)
                                if (press.down && !press.longFired) { press.longFired = true; onLongClick() }
                            }
                        } else if (press.down && onLongClick != null && !press.longFired && n.eventTime - n.downTime >= LONG_PRESS_MS) {
                            press.longFired = true
                            press.job?.cancel()
                            onLongClick()
                        }
                        true
                    }
                    isOk && ev.type == KeyEventType.KeyUp -> {
                        press.job?.cancel()
                        val pressedHere = press.down
                        press.down = false
                        if (pressedHere && !press.longFired) onClick()
                        press.longFired = false
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick
            )
    ) {
        content(focused)
    }
}

private const val LONG_PRESS_MS = 550L

private class PressState {
    var down = false
    var longFired = false
    var job: Job? = null
}

/** Botão em formato pílula (barra superior, diálogos, configurações). */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    leading: String? = null,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val accent = LocalUi.current.accent
    FocusCard(
        modifier = modifier,
        corner = 50.dp,
        focusScale = 1.08f,
        glow = false,
        background = if (selected) accent.copy(alpha = 0.35f) else Spectra.Surface,
        onFocus = onFocus,
        onClick = onClick
    ) { focused ->
        Row(
            Modifier
                .background(if (focused) accent else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) Text(leading, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.titleSmall)
            Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xCC1E1A4E), Color(0xCC120F33))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
