package com.hazagou.spectra.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
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

sealed interface SintoniaState {
    data object Idle : SintoniaState
    data object Checking : SintoniaState
    data class Downloading(val version: String, val progress: Float) : SintoniaState
    data class Installing(val version: String) : SintoniaState
    data class Done(val version: String) : SintoniaState
    data class Failed(val message: String) : SintoniaState
}

/**
 * Sugestão de app: Sintonia (player de IPTV do mesmo criador).
 * Lê a versão mais nova do version.json do repositório do Sintonia, baixa o APK e abre o instalador.
 */
class SintoniaSuggestion(private val context: Context) {

    companion object {
        const val PACKAGE = "com.haizougou.sintonia"
        const val PAGE = "https://github.com/heitorcampos2/Sintonia"
        const val VERSION_JSON = "https://raw.githubusercontent.com/heitorcampos2/Sintonia/main/version.json"
        const val EXTRA = "spectra.sintonia"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<SintoniaState>(SintoniaState.Idle)
    val state: StateFlow<SintoniaState> = _state

    /** Versão do Sintonia instalada na TV (null = não instalado). */
    fun installedVersion(): String? = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0).versionName
    }.getOrNull()

    fun open() {
        val pm = context.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(PACKAGE) ?: pm.getLaunchIntentForPackage(PACKAGE) ?: return
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Busca a versão publicada, baixa o APK e abre o instalador do Android. */
    fun installLatest(updates: UpdateManager) {
        if (_state.value is SintoniaState.Downloading || _state.value is SintoniaState.Checking) return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = SintoniaState.Failed("Permita que o Spectra instale apps: na tela que abriu, ative \"Permitir desta fonte\" e tente de novo.")
            updates.openUnknownSourcesSettings()
            return
        }
        _state.value = SintoniaState.Checking
        scope.launch {
            runCatching {
                val (version, url) = withContext(Dispatchers.IO) {
                    val c = open(VERSION_JSON)
                    val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    c.disconnect()
                    json.getString("version") to json.optJSONObject("files")?.optString("android").orEmpty()
                }
                if (url.isBlank()) error("o APK do Sintonia ainda não foi publicado")
                _state.value = SintoniaState.Downloading(version, 0f)
                val file = download(version, url)
                _state.value = SintoniaState.Installing(version)
                install(file)
                version
            }.onFailure { _state.value = SintoniaState.Failed("Não consegui instalar o Sintonia: ${it.message ?: "sem internet"}") }
        }
    }

    private suspend fun download(version: String, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "sintonia").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "sintonia-$version.apk")
        val c = open(url)
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (total > 0 && done - last > 256 * 1024) {
                        last = done
                        _state.value = SintoniaState.Downloading(version, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        c.disconnect()
        if (file.length() < 1_000_000) error("arquivo incompleto")
        file
    }

    /** Segue os redirecionamentos do GitHub. */
    private fun open(start: String): HttpURLConnection {
        var url = start
        repeat(6) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "SpectraLauncher/${BuildConfig.VERSION_NAME}")
            c.connectTimeout = 10000; c.readTimeout = 30000
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
        params.setAppPackageName(PACKAGE)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("sintonia.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val intent = Intent(context, InstallResultReceiver::class.java).putExtra(EXTRA, true)
            val pi = PendingIntent.getBroadcast(context, 900_000 + id, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val v = (state.value as? SintoniaState.Installing)?.version ?: ""
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> SintoniaState.Done(v)
            PackageInstaller.STATUS_FAILURE_ABORTED -> SintoniaState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                SintoniaState.Failed("O Sintonia instalado foi assinado com outra chave. Desinstale o Sintonia uma vez e instale de novo por aqui.")
            else -> SintoniaState.Failed("Instalação não concluída: ${message ?: status}")
        }
    }
}
