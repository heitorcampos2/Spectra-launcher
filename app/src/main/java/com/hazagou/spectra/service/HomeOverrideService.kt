package com.hazagou.spectra.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hazagou.spectra.MainActivity

/**
 * Em TVs que não deixam trocar o launcher padrão (ex.: Google TV / Chromecast), este serviço detecta
 * quando a tela inicial original é aberta (botão Home) e abre o Spectra no lugar.
 *
 * v3.2: o próprio botão Home do controle é interceptado (a tela inicial original nem chega a abrir).
 * Se ela aparecer por outro caminho (ex.: ao sair de um app aberto fora do Spectra), uma imagem da última
 * tela do Spectra cobre a TV na hora, até o Spectra voltar — sem piscar a tela do sistema.
 *
 * Também executa os comandos do controle remoto pelo celular em outros apps
 * (mover o foco, OK, voltar e digitar no campo de texto selecionado).
 */
class HomeOverrideService : AccessibilityService() {

    companion object {
        @Volatile var instance: HomeOverrideService? = null
            private set
    }

    private val knownStockLaunchers = setOf(
        "com.google.android.apps.tv.launcherx",   // Google TV
        "com.google.android.tvlauncher",          // Android TV (stock)
        "com.google.android.leanbacklauncher",    // Android TV antigo
        "com.android.tv.launcher",
        "com.amazon.tv.launcher",                 // Fire TV
        "com.mitv.tvhome",                        // Xiaomi PatchWall
        "com.tcl.home", "com.tcl.launcherhome",
        "com.droidlogic.mboxlauncher",            // TV box Amlogic
        "com.android.launcher3",
    )
    private var stockLaunchers: Set<String> = knownStockLaunchers
    private var lastLaunch = 0L
    private val main = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private val hideLater = Runnable { hideCover() }
    private var homeDown = false
    private var homeLong = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        stockLaunchers = knownStockLaunchers + resolveOtherHomes()
        // garante o filtro de teclas e eventos sem atraso mesmo se a TV guardou a configuração antiga do serviço
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                notificationTimeout = 0
            }
        }
    }

    // ================================================================ botão Home

    private var homeCheckedAt = 0L
    private var spectraIsDefault = false

    /** O Spectra é a tela inicial padrão do aparelho? (confere no máximo a cada 5 s) */
    private fun spectraIsDefaultHome(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - homeCheckedAt > 5000) {
            homeCheckedAt = now
            spectraIsDefault = runCatching {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName == packageName
            }.getOrDefault(false)
        }
        return spectraIsDefault
    }

    /**
     * Na TV o Spectra assume o botão Home e volta para a frente quando a tela inicial original aparece.
     * Em tablet/celular isso só acontece se o Spectra for a tela inicial padrão; senão ele fica quieto
     * (abre só quando você chama, inclusive pelo Home do controle no celular).
     */
    fun takeOverHome(): Boolean = isTv || spectraIsDefaultHome()

    /** Botão Home do controle: abre o Spectra direto. Segurar Home abre o painel da TV (como no sistema). */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (!takeOverHome()) return false   // tablet/celular sem o Spectra como padrão: o Home é do sistema
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) { homeDown = true; homeLong = false }
                else if (!homeLong && event.repeatCount >= 1 && (event.flags and KeyEvent.FLAG_LONG_PRESS != 0 || event.eventTime - event.downTime > 600)) {
                    homeLong = true
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                }
            }
            KeyEvent.ACTION_UP -> {
                if (homeDown && !homeLong && !event.isCanceled) openSpectra(cover = true)
                homeDown = false
            }
        }
        return true
    }

    /** Traz o Spectra para a frente, sem animação. */
    fun openSpectra(cover: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 400) return
        lastLaunch = now
        val spectraOnTop = MainActivity.foreground?.get() != null
        if (cover && !spectraOnTop) showCover()
        runCatching {
            val i = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(MainActivity.EXTRA_FROM_HOME_OVERRIDE, true)
            startActivity(i, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
        }.onFailure { hideCover() }
    }

    // ================================================================ capa (imagem do Spectra por cima da TV)

    private fun showCover() {
        main.removeCallbacks(hideLater)
        if (cover == null) {
            val v = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setBackgroundColor(0xFF08090F.toInt())
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { windowAnimations = 0 }
            if (runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, lp) }.isFailure) return
            cover = v
        }
        (cover as? ImageView)?.apply {
            animate().cancel(); alpha = 1f
            setImageBitmap(MainActivity.lastFrame)
        }
        main.postDelayed(hideLater, 2000)   // segurança: nunca fica presa na tela
    }

    /** Chamado pelo Spectra quando já está desenhado na tela. */
    fun hideCover() {
        main.post {
            main.removeCallbacks(hideLater)
            val v = cover ?: return@post
            cover = null
            v.animate().alpha(0f).setDuration(120).withEndAction {
                runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
            }.start()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        cover?.let { v -> runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } }
        cover = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ================================================================ controle remoto

    fun remoteGlobal(action: String): Boolean = when (action) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        // Home do controle: sempre chama o Spectra (na TV e no tablet), mesmo quando ele não é o launcher padrão
        "home" -> { openSpectra(cover = takeOverHome()); true }
        else -> false
    }

    /** ↑ ↓ ← → OK em qualquer app. Android 13+: ações nativas de D-pad; antes disso, move o foco pelos nós da tela. */
    fun remoteDpad(key: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val a = when (key) {
                "up" -> GLOBAL_ACTION_DPAD_UP
                "down" -> GLOBAL_ACTION_DPAD_DOWN
                "left" -> GLOBAL_ACTION_DPAD_LEFT
                "right" -> GLOBAL_ACTION_DPAD_RIGHT
                "ok", "oklong" -> GLOBAL_ACTION_DPAD_CENTER
                else -> return false
            }
            if (performGlobalAction(a)) return true
        }
        val root = rootInActiveWindow ?: return false
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (key == "ok" || key == "oklong") {
            var n = focus
            val action = if (key == "oklong") AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
            while (n != null) {
                if ((key == "ok" && n.isClickable) || (key == "oklong" && n.isLongClickable)) return n.performAction(action)
                n = n.parent
            }
            return false
        }
        val dir = when (key) {
            "up" -> View.FOCUS_UP
            "down" -> View.FOCUS_DOWN
            "left" -> View.FOCUS_LEFT
            "right" -> View.FOCUS_RIGHT
            else -> return false
        }
        if (focus == null) return firstFocusable(root)?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ?: false
        val next = focus.focusSearch(dir) ?: return scrollFrom(focus, dir)
        return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS) || next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    }

    // ------------------------------------------------ celular / tablet (tela de toque)

    /** TV (Android TV / Google TV) navega por foco; celular e tablet navegam por toque e rolagem. */
    val isTv: Boolean by lazy {
        (getSystemService(UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    /**
     * Setas e OK num aparelho de toque (tablet/celular). Lá os apps quase nunca mostram foco, então as
     * setas do Android não fazem nada visível: aqui ↑ ↓ ← → rolam a tela (lista, página, carrossel)
     * e OK toca no item selecionado ou no centro da tela.
     */
    // ------------------------------------------------ cursor (tablet/celular)

    @Volatile private var curX = -1f
    @Volatile private var curY = -1f
    private var cursorView: View? = null
    private val hideCursor = Runnable { cursorView?.let { v -> runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } }; cursorView = null }

    /** Package de apps que só entendem teclas de verdade (tela desenhada por eles, sem botões acessíveis). */
    private val keyApps = setOf("com.termux.x11")

    /**
     * Setas e OK num aparelho de toque (tablet/celular): um cursor aparece na tela e anda com as setas;
     * OK toca onde ele está (segurar OK = toque longo) e, ao chegar na borda, a tela rola nessa direção.
     * Funciona em qualquer app, inclusive na tela inicial do aparelho. No Ubuntu (Termux:X11) vão teclas de verdade.
     */
    fun remoteTouch(key: String): String? {
        val pkg = activePackage()
        if (pkg in keyApps && Build.VERSION.SDK_INT >= 33) {
            val g = when (key) {
                "up" -> GLOBAL_ACTION_DPAD_UP; "down" -> GLOBAL_ACTION_DPAD_DOWN; "left" -> GLOBAL_ACTION_DPAD_LEFT
                "right" -> GLOBAL_ACTION_DPAD_RIGHT; else -> GLOBAL_ACTION_DPAD_CENTER
            }
            if (performGlobalAction(g)) return "tecla"
        }
        if (!canGesture) return null
        val r = screen()
        val shown = cursorView != null && curX >= 0
        if (!shown) { curX = r.exactCenterX(); curY = r.exactCenterY() }
        when (key) {
            "ok", "oklong" -> {
                if (!shown) { showCursor(); return "cursor no centro (aperte OK de novo para tocar)" }
                val hold = if (key == "oklong") 700L else 50L
                // o cursor some um instante para o toque não cair nele
                return if (gesture(curX, curY, curX, curY, hold)) { showCursor(); "tocou" } else null
            }
            "up", "down", "left", "right" -> {
                if (!shown) { showCursor(); return "cursor no centro" }
                val step = minOf(r.width(), r.height()) * 0.08f
                val m = r.width() * 0.03f
                val nx = curX + when (key) { "left" -> -step; "right" -> step; else -> 0f }
                val ny = curY + when (key) { "up" -> -step; "down" -> step; else -> 0f }
                val atEdge = nx < m || nx > r.width() - m || ny < m || ny > r.height() - m
                if (atEdge) { showCursor(); return if (edgeScroll(key)) "rolou a tela" else "borda" }
                curX = nx; curY = ny
                showCursor(); return "cursor"
            }
        }
        return null
    }

    /** Move o cursor pelo touchpad do celular (dx/dy em pixels do tablet). */
    fun cursorMove(dx: Float, dy: Float): String? {
        if (!canGesture) return null
        val r = screen()
        if (cursorView == null || curX < 0) { curX = r.exactCenterX(); curY = r.exactCenterY() }
        curX = (curX + dx).coerceIn(0f, r.width() - 1f)
        curY = (curY + dy).coerceIn(0f, r.height() - 1f)
        showCursor()
        return "cursor"
    }

    /** Rolagem com dois dedos do touchpad: arrasta a tela a partir do cursor. */
    fun cursorScroll(dy: Float): String? {
        if (!canGesture) return null
        val r = screen()
        val x = if (curX >= 0) curX else r.exactCenterX()
        val y = if (curY >= 0) curY else r.exactCenterY()
        val y2 = (y + dy).coerceIn(1f, r.height() - 1f)
        return if (gesture(x, y, x, y2, 200L)) "rolou" else null
    }

    private fun showCursor() {
        main.post {
            main.removeCallbacks(hideCursor)
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val size = (resources.displayMetrics.density * 28).toInt()
            val lp = (cursorView?.layoutParams as? WindowManager.LayoutParams) ?: WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START; windowAnimations = 0 }
            lp.x = (curX - size / 2f).toInt(); lp.y = (curY - size / 2f).toInt()
            val v = cursorView
            if (v == null) {
                val nv = object : View(this) {
                    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    override fun onDraw(c: android.graphics.Canvas) {
                        val r = width / 2f
                        p.style = android.graphics.Paint.Style.FILL; p.color = 0x55000000
                        c.drawCircle(r, r, r, p)
                        p.color = 0xCCFFFFFF.toInt(); c.drawCircle(r, r, r * 0.42f, p)
                        p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = r * 0.16f; p.color = 0xFF06B6D4.toInt()
                        c.drawCircle(r, r, r * 0.8f, p)
                    }
                }
                if (runCatching { wm.addView(nv, lp) }.isSuccess) cursorView = nv
            } else runCatching { wm.updateViewLayout(v, lp) }
            main.postDelayed(hideCursor, 10_000)   // some sozinho depois de 10 s parado
        }
    }

    /** Ao chegar na borda: rola a lista/página nessa direção (ou arrasta a tela). */
    private fun edgeScroll(key: String): Boolean {
        val root = rootInActiveWindow
        val a = when (key) {
            "up" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
            "down" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
            "left" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT
            else -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT
        }
        val vertical = key == "up" || key == "down"
        val forward = key == "down" || key == "right"
        if (root != null) {
            // 1) a maior área rolável que aceita essa direção
            val all = ArrayList<AccessibilityNodeInfo>(); scrollables(root, all)
            fun area(n: AccessibilityNodeInfo) = Rect().also { n.getBoundsInScreen(it) }.let { it.width().toLong() * it.height() }
            val exact = all.filter { n -> n.actionList.any { it.id == a.id } }.maxByOrNull(::area)
            if (exact != null && exact.performAction(a.id)) return true
            // 2) listas antigas só conhecem "avançar/voltar": usa a maior delas no sentido certo
            val generic = all.filter { n ->
                val r = Rect().also { n.getBoundsInScreen(it) }
                if (vertical) r.height() >= r.width() / 2 else r.width() > r.height()
            }.maxByOrNull(::area)
            if (generic != null && generic.performAction(
                    if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
        }
        // 3) sem lista acessível: arrasta o dedo na tela
        val r = screen()
        val cx = r.exactCenterX(); val cy = r.exactCenterY()
        val dx = r.width() * 0.3f; val dy = r.height() * 0.25f
        return when (key) {
            "down" -> gesture(cx, cy + dy, cx, cy - dy)
            "up" -> gesture(cx, cy - dy, cx, cy + dy)
            "right" -> gesture(cx + dx, cy, cx - dx, cy)
            else -> gesture(cx - dx, cy, cx + dx, cy)
        }
    }

    /** Pacote do app que está na frente agora (null se não der para saber). */
    fun activePackage(): String? = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()

    /** O Android só deixa arrastar/tocar na tela depois de religar o serviço com a permissão nova. */
    val canGesture: Boolean get() = runCatching {
        (serviceInfo.capabilities and android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0
    }.getOrDefault(false)

    private fun scrollables(n: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, depth: Int = 0) {
        if (depth > 40) return
        if (n.isScrollable && n.isVisibleToUser) out += n
        for (i in 0 until n.childCount) scrollables(n.getChild(i) ?: continue, out, depth + 1)
    }

    private fun screen(): Rect =
        if (Build.VERSION.SDK_INT >= 30) (getSystemService(WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds
        else resources.displayMetrics.let { Rect(0, 0, it.widthPixels, it.heightPixels) }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 250L): Boolean = canGesture && runCatching {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build(), null, null)
    }.getOrDefault(false)

    /** Digita no campo de texto selecionado (ou no primeiro campo editável da tela). */
    fun remoteText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: findEditable(root) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun scrollFrom(node: AccessibilityNodeInfo, dir: Int): Boolean {
        var n: AccessibilityNodeInfo? = node
        val action = if (dir == View.FOCUS_UP || dir == View.FOCUS_LEFT) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        while (n != null) {
            if (n.isScrollable) return n.performAction(action)
            n = n.parent
        }
        return false
    }

    private fun firstFocusable(n: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 30) return null
        if (n.isFocusable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) firstFocusable(n.getChild(i) ?: continue, depth + 1)?.let { return it }
        return null
    }

    private fun findEditable(n: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 30) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) findEditable(n.getChild(i) ?: continue, depth + 1)?.let { return it }
        return null
    }

    private fun resolveOtherHomes(): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != packageName && it != "com.android.settings" && it != "com.android.tv.settings" }
            .toSet()
    }.getOrDefault(emptySet())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in stockLaunchers) return
        if (!takeOverHome()) return   // tablet/celular sem o Spectra como padrão: não volta sozinho para a frente
        // a tela inicial original apareceu (ex.: saiu de um app aberto fora do Spectra): cobre e traz o Spectra
        openSpectra(cover = true)
    }

    override fun onInterrupt() = Unit
}
