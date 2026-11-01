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
 * Sugestão de app na seção Atualizações (Sintonia, SmartTube…).
 * Descobre a versão mais nova publicada, baixa o APK e abre o instalador do Android.
 *
 * @param key identifica a sugestão no resultado do instalador
 * @param packages pacotes que contam como "instalado" (o primeiro é o atual)
 * @param lockPackage passa o nome do pacote ao instalador (só quando ele nunca muda)
 * @param latest devolve (versão, endereço do APK)
 */
class SintoniaSuggestion(
    private val context: Context,
    val key: String = "sintonia",
    val name: String = "Sintonia",
    private val packages: List<String> = listOf(PACKAGE),
    private val lockPackage: Boolean = true,
    private val latest: (SintoniaSuggestion) -> Pair<String, String> = { it.latestFromVersionJson(VERSION_JSON) },
) {

    companion object {
        const val PACKAGE = "com.haizougou.sintonia"
        const val PAGE = "https://github.com/heitorcampos2/Sintonia"
        const val VERSION_JSON = "https://raw.githubusercontent.com/heitorcampos2/Sintonia/main/version.json"
        const val EXTRA = "spectra.suggestion"

        // SmartTube (estável) — github.com/yuliskov/SmartTube
        const val SMARTTUBE_PAGE = "https://github.com/yuliskov/SmartTube"
        const val SMARTTUBE_APK = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable.apk"
        const val SMARTTUBE_API = "https://api.github.com/repos/yuliskov/SmartTube/releases/tags/latest"
        val SMARTTUBE_PACKAGES = listOf("org.smarttube.stable", "com.teamsmart.videomanager.tv")

        fun smartTube(context: Context) = SintoniaSuggestion(
            context, key = "smarttube", name = "SmartTube", packages = SMARTTUBE_PACKAGES, lockPackage = false,
            latest = { it.latestSmartTube() }
        )
    }

    /** Lê version.json no formato do Sintonia ({"version", "files": {"android"}}). */
    fun latestFromVersionJson(url: String): Pair<String, String> {
        val c = open(url)
        val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        c.disconnect()
        return json.getString("version") to json.optJSONObject("files")?.optString("android").orEmpty()
    }

    /** Release "latest" do SmartTube: pega o APK estável e o número da versão; sem a API, usa o link fixo. */
    fun latestSmartTube(): Pair<String, String> = runCatching {
        val c = open(SMARTTUBE_API)
        val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        c.disconnect()
        val assets = json.optJSONArray("assets")
        var url = ""
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val n = a.optString("name")
            if (n.contains("stable", true) && n.endsWith(".apk", true) && (url.isEmpty() || n == "smarttube_stable.apk"))
                url = a.optString("browser_download_url")
        }
        val text = json.optString("name") + " " + json.optString("body")
        val ver = Regex("""\b(\d{1,3}\.\d{1,3}(?:\.\d+)?)\b""").find(text)?.value ?: "mais nova"
        ver to url.ifEmpty { SMARTTUBE_APK }
    }.getOrElse { "mais nova" to SMARTTUBE_APK }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<SintoniaState>(SintoniaState.Idle)
    val state: StateFlow<SintoniaState> = _state

    /** Pacote instalado na TV (null = não instalado). */
    fun installedPackage(): String? = packages.firstOrNull { p ->
        runCatching { context.packageManager.getPackageInfo(p, 0) }.isSuccess
    }

    /** Versão instalada na TV (null = não instalado). */
    fun installedVersion(): String? = installedPackage()?.let { p ->
        runCatching { context.packageManager.getPackageInfo(p, 0).versionName ?: "?" }.getOrNull()
    }

    /** Ícone do app instalado (null = não instalado). */
    fun installedIcon(): android.graphics.drawable.Drawable? = installedPackage()?.let { p ->
        runCatching { context.packageManager.getApplicationIcon(p) }.getOrNull()
    }

    fun open() {
        val pm = context.packageManager
        val p = installedPackage() ?: return
        val i = pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p) ?: return
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
                val (version, url) = withContext(Dispatchers.IO) { latest(this@SintoniaSuggestion) }
                if (url.isBlank()) error("o APK do $name ainda não foi publicado")
                _state.value = SintoniaState.Downloading(version, 0f)
                val file = download(version, url)
                _state.value = SintoniaState.Installing(version)
                install(file)
                version
            }.onFailure { _state.value = SintoniaState.Failed("Não consegui instalar o $name: ${it.message ?: "sem internet"}") }
        }
    }

    private suspend fun download(version: String, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "sugestao-$key").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "$key.apk")
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
        if (lockPackage) params.setAppPackageName(packages.first())
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("$key.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val intent = Intent(context, InstallResultReceiver::class.java).putExtra(EXTRA, key)
            val pi = PendingIntent.getBroadcast(context, (if (key == "sintonia") 900_000 else 910_000) + id, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val v = (state.value as? SintoniaState.Installing)?.version ?: ""
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> SintoniaState.Done(v)
            PackageInstaller.STATUS_FAILURE_ABORTED -> SintoniaState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                SintoniaState.Failed("O $name instalado foi assinado com outra chave. Desinstale o $name uma vez e instale de novo por aqui.")
            else -> SintoniaState.Failed("Instalação não concluída: ${message ?: status}")
        }
    }
}
