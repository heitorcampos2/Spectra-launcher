package com.hazagou.spectra.remote

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import com.hazagou.spectra.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Servidor HTTP minúsculo que roda na TV (só na rede local). O celular — com o app Spectra ou
 * pelo navegador — manda teclas, texto e pedidos para abrir apps.
 *
 * Segurança: todo comando exige o token gerado a partir do PIN definido na TV.
 * O token chega ao celular pelo QR code ou pelo pareamento com PIN (5 tentativas erradas = bloqueio de 1 min).
 */
object RemoteServer {
    const val DEFAULT_PORT = 8642
    private const val TAG = "SpectraRemote"

    val running = MutableStateFlow(false)
    /** Porta realmente em uso (8642 ou a próxima livre). */
    @Volatile var port: Int = DEFAULT_PORT; private set
    /** Momento do último comando recebido (mostra "Celular conectado" na TV). */
    val lastCommandAt = MutableStateFlow(0L)

    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)
    private var failures = 0
    private var lockedUntil = 0L

    private lateinit var appContext: Context
    private lateinit var store: RemoteStore

    @Synchronized
    fun start(context: Context, store: RemoteStore) {
        appContext = context.applicationContext
        this.store = store
        if (server != null) return
        Thread({
            val ss = (DEFAULT_PORT..DEFAULT_PORT + 8).firstNotNullOfOrNull { p ->
                runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull()
            }
            if (ss == null) { Log.w(TAG, "nenhuma porta livre"); return@Thread }
            synchronized(this) { server = ss }
            port = ss.localPort
            running.value = true
            try {
                while (!ss.isClosed) {
                    val s = ss.accept()
                    pool.execute { runCatching { handle(s) }.onFailure { Log.w(TAG, "req", it) }; runCatching { s.close() } }
                }
            } catch (_: Exception) {
            } finally {
                running.value = false
                synchronized(this) { if (server === ss) server = null }
            }
        }, "spectra-remote").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        running.value = false
    }

    /** Liga/desliga conforme a configuração. */
    fun sync(context: Context, store: RemoteStore) {
        val c = store.config.value
        if (c.enabled && c.pin.isNotBlank()) start(context, store) else stop()
    }

    /** IP da TV na rede local (Wi-Fi ou cabo). */
    fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { n -> if (n.name.startsWith("wlan") || n.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    /** Conteúdo do QR code: abre o controle no app Spectra do celular ou no navegador. */
    fun pairUrl(config: RemoteConfig): String? {
        val ip = localIp() ?: return null
        return "http://$ip:$port/?t=${config.token}&n=${URLEncoder.encode(config.tvName, "UTF-8")}"
    }

    // ================================================================== HTTP

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String)

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        val out = socket.getOutputStream()
        val req = parse(input) ?: return
        val c = store.config.value
        if (c.pin.isBlank() || !c.enabled) return send(out, 503, json(JSONObject().put("error", "Controle remoto desligado na TV")))

        when {
            req.method == "GET" && (req.path == "/" || req.path == "/index.html") ->
                return send(out, 200, RemoteWebPage.html(c.tvName).toByteArray(), "text/html; charset=utf-8")
            req.path == "/api/info" -> return send(out, 200, json(info(c)))
            req.path == "/api/pair" && req.method == "POST" -> return pair(out, req, c)
        }

        val token = req.headers["x-spectra-token"] ?: req.query["t"] ?: ""
        if (!safeEquals(token, c.token)) return send(out, 401, json(JSONObject().put("error", "Não pareado: escaneie o QR code ou use o PIN")))
        lastCommandAt.value = System.currentTimeMillis()

        when (req.path) {
            "/api/key" -> {
                val k = req.query["k"] ?: JSONObject(req.body.ifBlank { "{}" }).optString("k")
                val r = RemoteCommands.key(appContext, k)
                send(out, if (r.ok) 200 else 409, json(r.toJson()))
            }
            "/api/text" -> {
                val text = runCatching { JSONObject(req.body).optString("text") }.getOrElse { req.body }
                val r = RemoteCommands.text(appContext, text)
                send(out, if (r.ok) 200 else 409, json(r.toJson()))
            }
            "/api/launch" -> {
                val pkg = req.query["pkg"] ?: runCatching { JSONObject(req.body).optString("pkg") }.getOrDefault("")
                val r = RemoteCommands.launch(appContext, pkg)
                send(out, if (r.ok) 200 else 409, json(r.toJson()))
            }
            "/api/apps" -> send(out, 200, apps().toString().toByteArray(), "application/json; charset=utf-8")
            "/api/icon" -> {
                val png = icon(req.query["pkg"] ?: "") ?: return send(out, 404, json(JSONObject().put("error", "sem ícone")))
                send(out, 200, png, "image/png", cache = true)
            }
            else -> send(out, 404, json(JSONObject().put("error", "não encontrado")))
        }
    }

    private fun info(c: RemoteConfig) = JSONObject()
        .put("app", "Spectra Launcher").put("version", BuildConfig.VERSION_NAME)
        .put("name", c.tvName).put("port", port)
        .put("controlsOtherApps", RemoteCommands.canControlOtherApps())

    private fun pair(out: OutputStream, req: Request, c: RemoteConfig) {
        val now = SystemClock.elapsedRealtime()
        if (now < lockedUntil) return send(out, 429, json(JSONObject().put("error", "Muitas tentativas. Aguarde 1 minuto.")))
        val pin = runCatching { JSONObject(req.body).optString("pin") }.getOrNull()?.takeIf { it.isNotBlank() } ?: req.query["pin"] ?: ""
        if (!safeEquals(pin, c.pin)) {
            failures++
            if (failures >= 5) { lockedUntil = now + 60_000; failures = 0 }
            return send(out, 403, json(JSONObject().put("error", "PIN incorreto")))
        }
        failures = 0
        send(out, 200, json(JSONObject().put("token", c.token).put("name", c.tvName).put("port", port)))
    }

    private fun apps(): JSONArray {
        val pm = appContext.packageManager
        val seen = LinkedHashMap<String, String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0).forEach { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg != appContext.packageName && pkg !in seen) seen[pkg] = ri.loadLabel(pm).toString()
            }
        }
        return JSONArray().apply {
            seen.entries.sortedBy { it.value.lowercase() }.forEach { (pkg, label) -> put(JSONObject().put("pkg", pkg).put("label", label)) }
        }
    }

    private val iconCache = android.util.LruCache<String, ByteArray>(2 * 1024 * 1024)

    private fun icon(pkg: String): ByteArray? {
        if (pkg.isBlank()) return null
        iconCache.get(pkg)?.let { return it }
        return runCatching {
            val d = appContext.packageManager.getApplicationIcon(pkg)
            val size = 128
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size); d.draw(Canvas(bmp))
            ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        }.getOrNull()?.also { iconCache.put(pkg, it) }
    }

    // ------------------------------------------------------------------ utilitários

    private fun parse(input: InputStream): Request? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val parts = lines.firstOrNull()?.split(" ") ?: return null
        if (parts.size < 2) return null
        val headers = lines.drop(1).mapNotNull { l ->
            val i = l.indexOf(':'); if (i <= 0) null else l.substring(0, i).trim().lowercase() to l.substring(i + 1).trim()
        }.toMap()
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val len = (headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, 64 * 1024)
        val body = ByteArray(len)
        var read = 0
        while (read < len) { val n = input.read(body, read, len - read); if (n < 0) break; read += n }
        return Request(parts[0].uppercase(), path, query, headers, String(body, 0, read, Charsets.UTF_8))
    }

    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var last4 = 0
        while (buf.size() < 16 * 1024) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) return buf.toString("ISO-8859-1").trimEnd()
        }
        return null
    }

    private fun json(o: JSONObject) = o.toString().toByteArray()

    private fun send(out: OutputStream, code: Int, body: ByteArray, type: String = "application/json; charset=utf-8", cache: Boolean = false) {
        val status = when (code) { 200 -> "OK"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; 409 -> "Conflict"; 429 -> "Too Many Requests"; 503 -> "Service Unavailable"; else -> "OK" }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: ${if (cache) "max-age=86400" else "no-store"}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun safeEquals(a: String, b: String): Boolean =
        b.isNotEmpty() && MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
