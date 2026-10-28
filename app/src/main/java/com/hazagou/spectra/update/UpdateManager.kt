package com.hazagou.spectra.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.hazagou.spectra.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
    val publishedAt: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/**
 * Atualizações pelo GitHub Releases do projeto.
 * O build automático (GitHub Actions) publica cada versão em
 * github.com/heitorcampos2/Spectra-launcher/releases com os arquivos
 * SpectraLauncher.apk (versão normal) e SpectraLauncher-debug.apk.
 */
class UpdateManager(private val context: Context) {

    companion object {
        const val REPO = "heitorcampos2/Spectra-launcher"
        const val PAGE = "https://github.com/$REPO/releases/latest"
        const val API = "https://api.github.com/repos/$REPO/releases/latest"
        private const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L

        /** Nome do APK que corresponde a este app instalado (mesma assinatura e mesmo pacote). */
        val assetName: String get() = if (BuildConfig.APPLICATION_ID.endsWith(".debug")) "SpectraLauncher-debug.apk" else "SpectraLauncher.apk"

        val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

        /** true se [remote] for mais nova que [local] (comparação número a número: 3.1.12 > 3.1.9). */
        fun isNewer(remote: String, local: String): Boolean {
            val r = remote.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
            val l = local.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }

    private val prefs = context.getSharedPreferences("spectra_updates", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val _autoCheck = MutableStateFlow(prefs.getBoolean("auto", true))
    val autoCheck: StateFlow<Boolean> = _autoCheck

    fun setAutoCheck(v: Boolean) { prefs.edit().putBoolean("auto", v).apply(); _autoCheck.value = v }

    /** Verificação automática ao abrir (no máximo a cada 6 h). */
    fun checkIfDue() {
        if (!_autoCheck.value) return
        if (System.currentTimeMillis() - prefs.getLong("last", 0) < CHECK_INTERVAL) {
            // reaproveita o último resultado conhecido
            prefs.getString("cached", null)?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }?.let { r ->
                if (isNewer(r.version, currentVersion) && _state.value is UpdateState.Idle) _state.value = UpdateState.Available(r)
            }
            return
        }
        check()
    }

    fun check() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = runCatching { fetchLatest() }.fold(
                onSuccess = { r -> if (isNewer(r.version, currentVersion)) UpdateState.Available(r) else UpdateState.UpToDate(r.version) },
                onFailure = { UpdateState.Failed("Não foi possível verificar: ${it.message ?: "sem internet"}") }
            )
        }
    }

    private suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val c = URL(API).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "SpectraLauncher/${BuildConfig.VERSION_NAME}")
        c.connectTimeout = 8000; c.readTimeout = 10000
        try {
            when (val code = c.responseCode) {
                200 -> Unit
                404 -> error("nenhuma versão publicada ainda")
                403 -> error("limite do GitHub atingido, tente mais tarde")
                else -> error("GitHub respondeu $code")
            }
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val info = parse(json)
            prefs.edit().putLong("last", System.currentTimeMillis()).putString("cached", json.toString()).apply()
            info
        } finally { c.disconnect() }
    }

    private fun parse(json: JSONObject): ReleaseInfo {
        val assets = json.optJSONArray("assets")
        var url = ""; var size = 0L
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name") == assetName) { url = a.optString("browser_download_url"); size = a.optLong("size") }
        }
        if (url.isBlank()) error("a versão publicada não tem o arquivo $assetName")
        return ReleaseInfo(
            version = json.optString("tag_name").trimStart('v', 'V'),
            title = json.optString("name").ifBlank { json.optString("tag_name") },
            notes = json.optString("body"),
            apkUrl = url, sizeBytes = size,
            pageUrl = json.optString("html_url").ifBlank { PAGE },
            publishedAt = json.optString("published_at").take(10),
        )
    }

    /** Baixa o APK e abre o instalador do Android. */
    fun downloadAndInstall(release: ReleaseInfo) {
        if (_state.value is UpdateState.Downloading) return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.Failed("Permita que o Spectra instale apps: na tela que abriu, ative \"Permitir desta fonte\" e tente de novo.", release)
            openUnknownSourcesSettings()
            return
        }
        _state.value = UpdateState.Downloading(release, 0f)
        scope.launch {
            val result = runCatching { download(release) }
            result.onSuccess { file ->
                _state.value = UpdateState.Installing(release)
                runCatching { install(file) }.onFailure { _state.value = UpdateState.Failed("Falha ao instalar: ${it.message}", release) }
            }.onFailure { _state.value = UpdateState.Failed("Falha no download: ${it.message ?: "sem internet"}", release) }
        }
    }

    private suspend fun download(release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "spectra-${release.version}.apk")
        val conn = openFollowingRedirects(release.apkUrl)
        conn.let { c ->
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0 && done - lastEmit > 128 * 1024) {
                            lastEmit = done
                            _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            c.disconnect()
        }
        if (file.length() < 100_000) error("arquivo incompleto")
        file
    }

    /** Segue os redirecionamentos do GitHub (github.com → objects.githubusercontent.com). */
    private fun openFollowingRedirects(start: String): HttpURLConnection {
        var url = start
        repeat(6) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "SpectraLauncher/${BuildConfig.VERSION_NAME}")
            c.connectTimeout = 10000; c.readTimeout = 20000
            val code = c.responseCode
            if (code in 300..399) {
                val next = c.getHeaderField("Location") ?: error("redirecionamento inválido")
                c.disconnect()
                url = URL(URL(url), next).toString()
            } else if (code == 200) {
                return c
            } else {
                c.disconnect(); error("HTTP $code")
            }
        }
        error("redirecionamentos demais")
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("spectra.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }

    fun openUnknownSourcesSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in intents) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val release = (state.value as? UpdateState.Installing)?.release
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                _state.value = UpdateState.Failed(
                    "Esta versão foi assinada com outra chave. Desinstale o Spectra uma vez e instale a nova pelo GitHub; depois as atualizações funcionam por aqui.",
                    release
                )
            else -> _state.value = UpdateState.Failed("Instalação não concluída: ${message ?: status}", release)
        }
    }
}

/** Recebe o resultado do instalador do Android. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Toast.makeText(context, "Abra o Spectra e toque em Atualizar de novo", Toast.LENGTH_LONG).show() }
            return
        }
        val app = context.applicationContext as? com.hazagou.spectra.SpectraApp ?: return
        app.updates.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
