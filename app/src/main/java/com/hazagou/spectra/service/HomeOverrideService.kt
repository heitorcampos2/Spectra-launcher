package com.hazagou.spectra.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.hazagou.spectra.MainActivity

/**
 * Em TVs que não deixam trocar o launcher padrão (ex.: Google TV / Chromecast), este serviço detecta
 * quando a tela inicial original é aberta (botão Home) e abre o Spectra no lugar.
 * Não lê nenhum conteúdo da tela: só observa qual pacote ficou em primeiro plano.
 */
class HomeOverrideService : AccessibilityService() {

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
        stockLaunchers = knownStockLaunchers + resolveOtherHomes()
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
