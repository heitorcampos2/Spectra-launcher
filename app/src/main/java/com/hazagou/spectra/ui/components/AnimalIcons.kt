package com.hazagou.spectra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hazagou.spectra.data.AnimalIcon
import com.hazagou.spectra.ui.theme.Spectra
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ícones especiais desenhados à mão em vetor (originais do Spectra): gato, pug, leão, tigre e dragão.
 * Escalam para qualquer tamanho sem perder nitidez e herdam a cor de destaque do tema.
 */
@Composable
fun AnimalIconView(animal: AnimalIcon, accent: Color, modifier: Modifier = Modifier) {
    if (animal == AnimalIcon.NONE) return
    Canvas(modifier) {
        val s = size.minDimension / 100f
        val ox = (size.width - 100f * s) / 2f
        val oy = (size.height - 100f * s) / 2f
        val d = Draw(this, s, ox, oy, accent)
        when (animal) {
            AnimalIcon.CAT -> d.cat()
            AnimalIcon.PUG -> d.pug()
            AnimalIcon.LION -> d.lion()
            AnimalIcon.TIGER -> d.tiger()
            AnimalIcon.DRAGON -> d.dragon()
            AnimalIcon.NONE -> Unit
        }
    }
}

private class Draw(val ds: DrawScope, val s: Float, val ox: Float, val oy: Float, val accent: Color) {
    val dark = Color(0xFF120E33)
    val white = Color(0xFFFDFBFF)
    val body = Brush.linearGradient(listOf(accent, Spectra.Violet), start = o(10f, 10f), end = o(90f, 95f))
    val bodyAlt = Brush.linearGradient(listOf(Spectra.Violet, accent.copy(alpha = 0.9f)), start = o(0f, 0f), end = o(100f, 100f))

    fun o(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun sz(w: Float, h: Float) = Size(w * s, h * s)

    fun path(vararg pts: Float, close: Boolean = true) = Path().apply {
        moveTo(ox + pts[0] * s, oy + pts[1] * s)
        var i = 2
        while (i < pts.size) { lineTo(ox + pts[i] * s, oy + pts[i + 1] * s); i += 2 }
        if (close) close()
    }

    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticBezierTo(ox + cx * s, oy + cy * s, ox + x * s, oy + y * s)
    fun Path.l(x: Float, y: Float) = lineTo(ox + x * s, oy + y * s)
    fun Path.m(x: Float, y: Float) = moveTo(ox + x * s, oy + y * s)

    fun oval(x: Float, y: Float, w: Float, h: Float, color: Color) = ds.drawOval(color, o(x, y), sz(w, h))
    fun ovalB(x: Float, y: Float, w: Float, h: Float, brush: Brush) = ds.drawOval(brush, o(x, y), sz(w, h))
    fun circle(x: Float, y: Float, r: Float, color: Color) = ds.drawCircle(color, r * s, o(x, y))
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, w: Float = 2.2f) =
        ds.drawLine(color, o(x1, y1), o(x2, y2), strokeWidth = w * s, cap = StrokeCap.Round)
    fun stroke(p: Path, color: Color, w: Float = 2.2f) = ds.drawPath(p, color, style = Stroke(width = w * s, cap = StrokeCap.Round))

    fun eyeSlit(cx: Float, cy: Float, w: Float, h: Float, iris: Color) {
        oval(cx - w / 2, cy - h / 2, w, h, iris)
        oval(cx - w * 0.12f, cy - h / 2 + 0.6f, w * 0.24f, h - 1.2f, dark)
        circle(cx + w * 0.18f, cy - h * 0.2f, 0.9f, white)
    }

    // ------------------------------------------------------------------ GATO
    fun cat() {
        ds.drawPath(path(20f, 46f, 25f, 10f, 46f, 32f), body)
        ds.drawPath(path(80f, 46f, 75f, 10f, 54f, 32f), body)
        ds.drawPath(path(26f, 38f, 28f, 19f, 39f, 31f), white.copy(alpha = .35f))
        ds.drawPath(path(74f, 38f, 72f, 19f, 61f, 31f), white.copy(alpha = .35f))
        ovalB(16f, 27f, 68f, 58f, body)
        eyeSlit(37f, 54f, 13f, 9f, Color(0xFFFFE07A))
        eyeSlit(63f, 54f, 13f, 9f, Color(0xFFFFE07A))
        ds.drawPath(path(45.5f, 64f, 54.5f, 64f, 50f, 69f), Color(0xFFFF8FB8))
        stroke(Path().apply { m(50f, 69f); q(48f, 75f, 43f, 73f); m(50f, 69f); q(52f, 75f, 57f, 73f) }, dark)
        line(30f, 65f, 9f, 61f, white); line(30f, 70f, 9f, 72f, white)
        line(70f, 65f, 91f, 61f, white); line(70f, 70f, 91f, 72f, white)
    }

    // ------------------------------------------------------------------ PUG
    fun pug() {
        ovalB(17f, 24f, 66f, 62f, body)
        // orelhas caídas
        ds.drawPath(Path().apply { m(24f, 32f); q(4f, 30f, 9f, 52f); q(18f, 54f, 30f, 42f); close() }, Color(0xFF2A1B4A))
        ds.drawPath(Path().apply { m(76f, 32f); q(96f, 30f, 91f, 52f); q(82f, 54f, 70f, 42f); close() }, Color(0xFF2A1B4A))
        // rugas
        stroke(Path().apply { m(40f, 34f); q(50f, 30f, 60f, 34f) }, dark.copy(alpha = .55f), 2f)
        stroke(Path().apply { m(43f, 39f); q(50f, 36f, 57f, 39f) }, dark.copy(alpha = .45f), 1.8f)
        // focinho escuro
        oval(31f, 56f, 38f, 28f, Color(0xFF231640))
        // olhos grandes
        circle(37f, 49f, 7.5f, white); circle(63f, 49f, 7.5f, white)
        circle(38f, 50f, 4.8f, dark); circle(62f, 50f, 4.8f, dark)
        circle(39.6f, 48f, 1.5f, white); circle(63.6f, 48f, 1.5f, white)
        // nariz e boca
        oval(43.5f, 60f, 13f, 7.5f, Color(0xFF0A0718))
        circle(47.5f, 62f, 1f, white.copy(alpha = .6f))
        stroke(Path().apply { m(50f, 67.5f); l(50f, 71f); q(45f, 76f, 40f, 72f); m(50f, 71f); q(55f, 76f, 60f, 72f) }, white.copy(alpha = .8f), 1.8f)
        oval(46.5f, 73f, 7f, 6f, Color(0xFFFF6F9C))
    }

    // ------------------------------------------------------------------ LEÃO
    fun lion() {
        val mane = Path()
        val points = 18
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) 47f else 36f
            val a = (PI * i / points - PI / 2).toFloat()
            val x = 50f + r * cos(a); val y = 53f + r * sin(a)
            if (i == 0) mane.m(x, y) else mane.l(x, y)
        }
        mane.close()
        ds.drawPath(mane, bodyAlt)
        circle(31f, 33f, 7f, Spectra.Violet); circle(69f, 33f, 7f, Spectra.Violet)
        circle(31f, 33f, 3.5f, dark.copy(alpha = .5f)); circle(69f, 33f, 3.5f, dark.copy(alpha = .5f))
        ds.drawCircle(body, 28f * s, o(50f, 55f))
        line(33f, 44f, 43f, 46f, dark, 2.4f); line(67f, 44f, 57f, 46f, dark, 2.4f)
        eyeSlit(39f, 51f, 9f, 6.5f, Color(0xFFFFD54F))
        eyeSlit(61f, 51f, 9f, 6.5f, Color(0xFFFFD54F))
        oval(38f, 60f, 24f, 17f, white.copy(alpha = .9f))
        ds.drawPath(path(44.5f, 60f, 55.5f, 60f, 50f, 66.5f), dark)
        stroke(Path().apply { m(50f, 66.5f); l(50f, 70f); q(46f, 74f, 42f, 71f); m(50f, 70f); q(54f, 74f, 58f, 71f) }, dark, 1.8f)
        circle(43f, 66f, .8f, dark); circle(41f, 69f, .8f, dark); circle(57f, 66f, .8f, dark); circle(59f, 69f, .8f, dark)
    }

    // ------------------------------------------------------------------ TIGRE
    fun tiger() {
        circle(26f, 29f, 10f, accent); circle(74f, 29f, 10f, accent)
        circle(26f, 29f, 5.5f, dark); circle(74f, 29f, 5.5f, dark)
        ovalB(14f, 24f, 72f, 64f, body)
        // listras da testa
        ds.drawPath(path(44f, 25f, 48f, 25f, 46f, 39f), dark)
        ds.drawPath(path(48.5f, 24f, 51.5f, 24f, 50f, 42f), dark)
        ds.drawPath(path(52f, 25f, 56f, 25f, 54f, 39f), dark)
        // listras laterais
        ds.drawPath(path(14f, 50f, 31f, 54f, 15f, 58f), dark)
        ds.drawPath(path(16f, 63f, 30f, 66f, 18f, 70f), dark)
        ds.drawPath(path(86f, 50f, 69f, 54f, 85f, 58f), dark)
        ds.drawPath(path(84f, 63f, 70f, 66f, 82f, 70f), dark)
        // olhos
        ds.drawPath(path(30f, 49f, 38f, 45f, 45f, 50f, 38f, 53f), white)
        ds.drawPath(path(70f, 49f, 62f, 45f, 55f, 50f, 62f, 53f), white)
        circle(38.5f, 49.2f, 2.8f, Color(0xFF7CFC9A)); circle(61.5f, 49.2f, 2.8f, Color(0xFF7CFC9A))
        circle(38.5f, 49.2f, 1.3f, dark); circle(61.5f, 49.2f, 1.3f, dark)
        // focinho
        oval(37f, 60f, 14f, 14f, white); oval(49f, 60f, 14f, 14f, white)
        ds.drawPath(path(45f, 58f, 55f, 58f, 50f, 64f), Color(0xFFFF6F9C))
        stroke(Path().apply { m(50f, 64f); l(50f, 70f) }, dark, 1.6f)
        oval(45f, 74f, 10f, 5f, Color(0xFFFF6F9C))
    }

    // ------------------------------------------------------------------ DRAGÃO
    fun dragon() {
        // espinhos do pescoço
        ds.drawPath(path(21f, 50f, 6f, 52f, 19f, 59f), Spectra.Violet)
        ds.drawPath(path(19f, 60f, 4f, 65f, 19f, 69f), Spectra.Violet)
        ds.drawPath(path(22f, 71f, 9f, 79f, 25f, 78f), Spectra.Violet)
        // chifres
        ds.drawPath(path(38f, 31f, 26f, 6f, 49f, 27f), Color(0xFFE8DDF7))
        ds.drawPath(path(54f, 27f, 61f, 4f, 64f, 29f), Color(0xFFE8DDF7))
        // cabeça
        val head = Path().apply {
            m(19f, 72f); l(21f, 48f); q(28f, 30f, 48f, 28f)
            l(66f, 29f); q(88f, 34f, 93f, 50f); l(93f, 57f)
            l(76f, 61f); l(89f, 67f); q(85f, 77f, 70f, 77f)
            l(44f, 80f); q(27f, 86f, 19f, 72f); close()
        }
        ds.drawPath(head, body)
        // escamas / brilho
        stroke(Path().apply { m(30f, 50f); q(36f, 44f, 44f, 46f) }, white.copy(alpha = .35f), 1.6f)
        stroke(Path().apply { m(28f, 60f); q(36f, 54f, 46f, 57f) }, white.copy(alpha = .3f), 1.6f)
        // olho
        ds.drawPath(path(55f, 44f, 63f, 39f, 71f, 43f, 63f, 47f), Color(0xFFFFE07A))
        oval(62f, 39.6f, 2.4f, 7f, dark)
        line(53f, 39f, 70f, 35f, dark, 2.6f)
        // narina
        oval(85f, 45f, 4f, 2.6f, dark)
        // dentes
        ds.drawPath(path(78f, 61.5f, 80.5f, 61f, 79.5f, 65f), white)
        ds.drawPath(path(83f, 63.5f, 85.5f, 64.6f, 83.6f, 67.5f), white)
        // linha da boca
        stroke(Path().apply { m(93f, 57f); l(76f, 61f); l(58f, 60f) }, dark, 1.8f)
        // fumaça mágica
        circle(97f, 47f, 1.8f, Spectra.Lilac.copy(alpha = .8f))
        circle(99f, 41f, 1.2f, Spectra.Lilac.copy(alpha = .6f))
    }
}
