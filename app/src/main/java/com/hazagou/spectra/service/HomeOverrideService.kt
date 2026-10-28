package com.hazagou.spectra.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hazagou.spectra.MainActivity

/**
 * Em TVs que não deixam trocar o launcher padrão (ex.: Google TV / Chromecast), este serviço detecta
 * quando a tela inicial original é aberta (botão Home) e abre o Spectra no lugar.
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        stockLaunchers = knownStockLaunchers + resolveOtherHomes()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ================================================================ controle remoto

    fun remoteGlobal(action: String): Boolean = when (action) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
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
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 700) return
        lastLaunch = now
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(MainActivity.EXTRA_FROM_HOME_OVERRIDE, true)
            )
        }
    }

    override fun onInterrupt() = Unit
}
