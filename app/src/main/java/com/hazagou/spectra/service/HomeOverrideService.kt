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
    fun remoteTouch(key: String): String? {
        val root = rootInActiveWindow
        if (key == "ok" || key == "oklong") {
            val focus = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { !it.isEditable }
                ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            var n = focus
            val action = if (key == "oklong") AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
            while (n != null) {
                if ((key == "ok" && n.isClickable) || (key == "oklong" && n.isLongClickable)) { if (n.performAction(action)) return "tocou no item"; break }
                n = n.parent
            }
            // sem item selecionado: OK do sistema (Enter) — serve para Ubuntu/Termux:X11, players e jogos
            if (key == "ok" && Build.VERSION.SDK_INT >= 33 && performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)) return "tecla OK"
            val r = screen()
            return if (gesture(r.exactCenterX(), r.exactCenterY(), r.exactCenterX(), r.exactCenterY(), if (key == "oklong") 700L else 60L)) "toque no centro" else null
        }
        val vertical = key == "up" || key == "down"
        val a = when (key) {
            "up" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
            "down" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
            "left" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT
            "right" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT
            else -> return null
        }
        val forward = key == "down" || key == "right"
        if (root != null) {
            // 1) a maior área rolável que aceita essa direção
            val all = ArrayList<AccessibilityNodeInfo>(); scrollables(root, all)
            fun area(n: AccessibilityNodeInfo) = Rect().also { n.getBoundsInScreen(it) }.let { it.width().toLong() * it.height() }
            val exact = all.filter { n -> n.actionList.any { it.id == a.id } }.maxByOrNull(::area)
            if (exact != null && exact.performAction(a.id)) return "rolou a tela"
            // 2) listas antigas só conhecem "avançar/voltar": usa a maior delas no sentido certo
            val generic = all.filter { n ->
                val r = Rect().also { n.getBoundsInScreen(it) }
                if (vertical) r.height() >= r.width() / 2 else r.width() > r.height()
            }.maxByOrNull(::area)
            if (generic != null && generic.performAction(
                    if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return "rolou a tela"
        }
        // 3) nada rolável na tela (Ubuntu no Termux:X11, players, jogos, WebView): seta de verdade do sistema
        if (Build.VERSION.SDK_INT >= 33) {
            val g = when (key) { "up" -> GLOBAL_ACTION_DPAD_UP; "down" -> GLOBAL_ACTION_DPAD_DOWN; "left" -> GLOBAL_ACTION_DPAD_LEFT; else -> GLOBAL_ACTION_DPAD_RIGHT }
            if (performGlobalAction(g)) return "tecla de seta"
        }
        // 4) último recurso: arrasta o dedo na tela
        val r = screen()
        val cx = r.exactCenterX(); val cy = r.exactCenterY()
        val dx = r.width() * 0.3f; val dy = r.height() * 0.25f
        val ok = when (key) {
            "down" -> gesture(cx, cy + dy, cx, cy - dy)
            "up" -> gesture(cx, cy - dy, cx, cy + dy)
            "right" -> gesture(cx + dx, cy, cx - dx, cy)
            else -> gesture(cx - dx, cy, cx + dx, cy)
        }
        return if (ok) "arrastou na tela" else null
    }

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
