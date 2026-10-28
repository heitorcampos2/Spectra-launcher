package com.hazagou.spectra.remote

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** Configuração do controle remoto: lado TV (PIN, nome) e lado celular (TVs pareadas). */
data class RemoteConfig(
    val enabled: Boolean,
    val pin: String,
    val salt: String,
    val tvName: String,
) {
    /** Token entregue ao celular no pareamento (QR code ou PIN). Muda quando o PIN muda. */
    val token: String get() = if (pin.isBlank()) "" else RemoteStore.sha256("spectra:$pin:$salt").take(32)
}

data class PairedTv(val name: String, val host: String, val port: Int, val token: String) {
    val id: String get() = "$host:$port"
    fun toJson(): JSONObject = JSONObject().put("name", name).put("host", host).put("port", port).put("token", token)

    companion object {
        fun fromJson(o: JSONObject) = PairedTv(o.optString("name", "TV"), o.getString("host"), o.optInt("port", RemoteServer.DEFAULT_PORT), o.getString("token"))
    }
}

class RemoteStore(context: Context) {

    private val prefs = context.getSharedPreferences("spectra_remote", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<RemoteConfig> = _config

    private val _paired = MutableStateFlow(readPaired())
    val paired: StateFlow<List<PairedTv>> = _paired

    private fun readConfig(): RemoteConfig {
        var salt = prefs.getString("salt", null)
        if (salt.isNullOrBlank()) {
            salt = newSalt()
            prefs.edit().putString("salt", salt).apply()
        }
        return RemoteConfig(
            enabled = prefs.getBoolean("enabled", false),
            pin = prefs.getString("pin", "") ?: "",
            salt = salt,
            tvName = prefs.getString("tv_name", null) ?: defaultName(),
        )
    }

    private fun defaultName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" ")
            .replaceFirstChar { it.uppercase() }.ifBlank { "TV" }

    private fun save(c: RemoteConfig) {
        prefs.edit().putBoolean("enabled", c.enabled).putString("pin", c.pin).putString("salt", c.salt)
            .putString("tv_name", c.tvName).apply()
        _config.value = c
    }

    fun setEnabled(v: Boolean) = save(_config.value.copy(enabled = v))
    /** Trocar o PIN também desconecta os celulares pareados antes (novo token). */
    fun setPin(pin: String) = save(_config.value.copy(pin = pin, salt = newSalt()))
    fun setTvName(name: String) = save(_config.value.copy(tvName = name.ifBlank { defaultName() }))
    fun disconnectAll() = save(_config.value.copy(salt = newSalt()))

    // ------------------------------------------------------------- lado celular

    private fun readPaired(): List<PairedTv> = runCatching {
        val arr = JSONArray(prefs.getString("paired", "[]"))
        (0 until arr.length()).map { PairedTv.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun savePaired(list: List<PairedTv>) {
        prefs.edit().putString("paired", JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()).apply()
        _paired.value = list
    }

    fun addPaired(tv: PairedTv) = savePaired(listOf(tv) + _paired.value.filterNot { it.id == tv.id })
    fun removePaired(tv: PairedTv) = savePaired(_paired.value.filterNot { it.id == tv.id })

    companion object {
        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun newSalt(): String {
            val b = ByteArray(16); SecureRandom().nextBytes(b)
            return b.joinToString("") { "%02x".format(it) }
        }
    }
}
