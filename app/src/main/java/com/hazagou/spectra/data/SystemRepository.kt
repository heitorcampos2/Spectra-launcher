package com.hazagou.spectra.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Estado de rede mostrado na barra de status. level: 0..3 (Wi-Fi), -1 = sem conexão. */
data class NetState(val connected: Boolean, val wifi: Boolean, val ethernet: Boolean, val level: Int, val internet: Boolean)

data class BatteryState(val present: Boolean, val percent: Int, val charging: Boolean)

/** Entrada física (HDMI/AV) ou atalho especial (Cast). */
data class TvInput(val id: String, val label: String, val kind: Kind, val isDemo: Boolean = false) {
    enum class Kind(val icon: String) { HDMI("⎚"), AV("◉"), CAST("📡"), OTHER("⇥") }
}

class SystemRepository(private val context: Context) {

    // ------------------------------------------------------------------ Rede
    val network: Flow<NetState> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun compute(caps: NetworkCapabilities?): NetState {
            if (caps == null) return NetState(false, false, false, -1, false)
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val eth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.signalStrength else Int.MIN_VALUE
            val level = when {
                !wifi -> 3
                rssi == Int.MIN_VALUE -> 3
                rssi >= -55 -> 3
                rssi >= -67 -> 2
                rssi >= -78 -> 1
                else -> 0
            }
            return NetState(true, wifi, eth, level, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
        trySend(compute(cm?.getNetworkCapabilities(cm.activeNetwork)))
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(compute(caps)) }
            override fun onLost(network: Network) { trySend(compute(cm?.getNetworkCapabilities(cm.activeNetwork))) }
            override fun onAvailable(network: Network) { trySend(compute(cm?.getNetworkCapabilities(network))) }
        }
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
            .onFailure { runCatching { cm?.registerNetworkCallback(NetworkRequest.Builder().build(), cb) } }
        awaitClose { runCatching { cm?.unregisterNetworkCallback(cb) } }
    }.distinctUntilChanged()

    // ------------------------------------------------------------------ Bateria (só aparece em aparelhos com bateria)
    val battery: Flow<BatteryState> = callbackFlow {
        fun parse(i: Intent?): BatteryState {
            if (i == null) return BatteryState(false, 0, false)
            val present = i.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            return BatteryState(present && level >= 0, (level * 100 / scale).coerceIn(0, 100), charging)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { trySend(parse(i)) }
        }
        val sticky = ContextCompat.registerReceiver(context, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(parse(sticky))
        awaitClose { runCatching { context.unregisterReceiver(r) } }
    }.distinctUntilChanged()

    // ------------------------------------------------------------------ Entradas (HDMI / AV)
    fun inputs(names: Map<String, String>, demo: Boolean): List<TvInput> {
        val list = ArrayList<TvInput>()
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        runCatching {
            tim?.tvInputList.orEmpty()
                .filter { it.isPassthroughInput && it.parentId == null }
                .sortedBy { it.type * 100 + (it.loadLabel(context)?.toString()?.filter { c -> c.isDigit() }?.toIntOrNull() ?: 0) }
                .forEach { info ->
                    val kind = when (info.type) {
                        TvInputInfo.TYPE_HDMI -> TvInput.Kind.HDMI
                        TvInputInfo.TYPE_COMPOSITE, TvInputInfo.TYPE_COMPONENT, TvInputInfo.TYPE_SCART, TvInputInfo.TYPE_SVIDEO -> TvInput.Kind.AV
                        else -> TvInput.Kind.OTHER
                    }
                    val label = names[info.id] ?: info.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.loadLabel(context)?.toString() ?: "Entrada"
                    list += TvInput(info.id, label, kind)
                }
        }
        if (list.isEmpty() && demo) {
            list += TvInput("demo_hdmi1", names["demo_hdmi1"] ?: "HDMI 1", TvInput.Kind.HDMI, true)
            list += TvInput("demo_hdmi2", names["demo_hdmi2"] ?: "HDMI 2", TvInput.Kind.HDMI, true)
            list += TvInput("demo_av", names["demo_av"] ?: "AV", TvInput.Kind.AV, true)
        }
        if (list.isNotEmpty() || demo) list += TvInput("cast", names["cast"] ?: "Cast / Espelhar", TvInput.Kind.CAST)
        return list
    }

    val hasPhysicalInputs: Boolean
        get() = runCatching {
            (context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager)?.tvInputList.orEmpty().any { it.isPassthroughInput }
        }.getOrDefault(false)

    /** Abre a entrada escolhida (HDMI/AV pelo app de TV do sistema; Cast pelo app configurado). */
    fun openInput(input: TvInput, castPackage: String): Boolean {
        if (input.isDemo) return false
        if (input.kind == TvInput.Kind.CAST) {
            val pm = context.packageManager
            val candidates = listOfNotNull(
                castPackage.takeIf { it.isNotBlank() }?.let { pm.getLeanbackLaunchIntentForPackage(it) ?: pm.getLaunchIntentForPackage(it) },
                Intent("android.settings.CAST_SETTINGS"),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
            return candidates.any { tryStart(it) }
        }
        val uri = TvContract.buildChannelUriForPassthroughInput(input.id)
        return tryStart(Intent(Intent.ACTION_VIEW, uri)) ||
            tryStart(Intent(TvInputManager.ACTION_SETUP_INPUTS))
    }

    private fun tryStart(i: Intent): Boolean = runCatching {
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    // ------------------------------------------------------------------ Atalhos do sistema
    fun openWifiSettings() {
        listOf(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            .firstOrNull { tryStart(it) }
    }

    fun openSystemSettings() { tryStart(Intent(Settings.ACTION_SETTINGS)) }

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openNotificationAccess(): Boolean {
        val cn = ComponentName(context, "com.hazagou.spectra.service.SpectraNotificationService")
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString()) else null
        return listOfNotNull(detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)).any { tryStart(it) }
    }
}
