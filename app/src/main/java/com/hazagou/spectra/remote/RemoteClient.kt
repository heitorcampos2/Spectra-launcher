package com.hazagou.spectra.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteApp(val pkg: String, val label: String)

/** Lado celular: fala com o servidor da TV na rede local. */
class RemoteClient(val tv: PairedTv) {

    private val base = "http://${tv.host}:${tv.port}"

    fun iconUrl(pkg: String) = "$base/api/icon?pkg=${Uri.encode(pkg)}&t=${tv.token}"

    suspend fun key(k: String): CommandResult = post("/api/key?k=${Uri.encode(k)}", JSONObject())
    suspend fun text(t: String): CommandResult = post("/api/text", JSONObject().put("text", t))
    suspend fun launch(pkg: String): CommandResult = post("/api/launch?pkg=${Uri.encode(pkg)}", JSONObject())

    suspend fun info(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(request("GET", "/api/info", null).second) }.getOrNull()
    }

    suspend fun apps(): List<RemoteApp> = withContext(Dispatchers.IO) {
        val (code, body) = request("GET", "/api/apps", null)
        if (code != 200) error(errorOf(body, code))
        val arr = JSONArray(body)
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> RemoteApp(o.getString("pkg"), o.getString("label")) } }
    }

    private suspend fun post(path: String, body: JSONObject): CommandResult = withContext(Dispatchers.IO) {
        runCatching {
            val (code, resp) = request("POST", path, body.toString())
            if (code == 200) CommandResult(true) else CommandResult(false, errorOf(resp, code))
        }.getOrElse { CommandResult(false, "TV fora de alcance. Confira se estão na mesma rede Wi-Fi.") }
    }

    private fun request(method: String, path: String, body: String?): Pair<Int, String> =
        http(method, "$base$path", body, mapOf("X-Spectra-Token" to tv.token))

    companion object {
        /** Lê o QR code da TV: http://IP:PORTA/?t=TOKEN&n=NOME */
        fun parsePairUrl(text: String): PairedTv? {
            val u = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return null
            val host = u.host
            if (u.scheme != "http" || host.isNullOrBlank()) return null
            val token = u.getQueryParameter("t")?.takeIf { it.length >= 16 } ?: return null
            return PairedTv(u.getQueryParameter("n") ?: "TV", host, if (u.port > 0) u.port else RemoteServer.DEFAULT_PORT, token)
        }

        /** Pareamento manual: IP da TV + PIN. */
        suspend fun pair(host: String, port: Int, pin: String): Result<PairedTv> = withContext(Dispatchers.IO) {
            runCatching {
                val (code, resp) = http("POST", "http://$host:$port/api/pair", JSONObject().put("pin", pin).toString(), emptyMap())
                if (code != 200) error(errorOf(resp, code))
                val o = JSONObject(resp)
                PairedTv(o.optString("name", "TV"), host, o.optInt("port", port), o.getString("token"))
            }.recoverCatching { e ->
                throw if (e is java.io.IOException) IllegalStateException("TV não encontrada em $host:$port. Confira o IP e se estão na mesma rede.") else e
            }
        }

        private fun errorOf(body: String, code: Int): String =
            runCatching { JSONObject(body).let { it.optString("message").ifBlank { it.optString("error") } } }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: "Erro $code"

        private fun http(method: String, url: String, body: String?, headers: Map<String, String>): Pair<Int, String> {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 2500
            c.readTimeout = 4000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            return try {
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally { c.disconnect() }
        }
    }
}
