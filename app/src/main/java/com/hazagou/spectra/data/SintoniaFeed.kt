package com.hazagou.spectra.data

import android.content.Context
import android.net.Uri
import com.hazagou.spectra.update.SintoniaSuggestion
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tablet e celular não têm os canais do Android TV. Aqui o Spectra monta os mesmos canais a partir do
 * Sintonia do próprio aparelho (servidor local em 127.0.0.1:8787, endereço /api/home): Continuar
 * assistindo, Canais favoritos, Filmes/Séries favoritas, Sugestões, Ao vivo no YouTube e Novos vídeos.
 * Os vídeos do YouTube abrem no SmartTube (ou no app do YouTube). Junta também o que tocou por último
 * em apps como Spotify, Crunchyroll e Globoplay ([MediaHistory]).
 * Guarda a última resposta para mostrar mesmo com o Sintonia fechado.
 */
class SintoniaFeed(private val context: Context) {

    private val cache = File(context.filesDir, "sintonia_home.json")

    private fun installed(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    private fun fetch(): JSONObject? {
        val fresh = runCatching {
            val c = URL("http://127.0.0.1:8787/api/home").openConnection() as HttpURLConnection
            c.connectTimeout = 1500; c.readTimeout = 20000
            try {
                if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
            } finally { c.disconnect() }
        }.getOrNull()
        if (fresh != null) runCatching { cache.writeText(fresh) }
        val text = fresh ?: runCatching { cache.readText() }.getOrNull() ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    /** null = nada para mostrar (sem Sintonia e sem histórico de mídia). */
    fun load(): TvContentRepository.State.Ready? {
        val channels = ArrayList<TvChannel>()
        val watchNext = ArrayList<TvProgram>()
        val sintonia = SintoniaSuggestion.PACKAGE
        val ytPkg = SintoniaSuggestion.SMARTTUBE_PACKAGES.firstOrNull(::installed)
            ?: "com.google.android.youtube".takeIf(::installed)
        if (installed(sintonia)) {
            val rows = fetch()?.optJSONArray("rows")
            var cid = -1000L
            if (rows != null) for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val rowId = row.optString("id")
                val isYt = rowId.startsWith("yt-")
                if (isYt && ytPkg == null) continue
                val pkg = if (isYt) ytPkg!! else sintonia
                val chId = cid--
                val items = row.optJSONArray("items") ?: continue
                val progs = ArrayList<TvProgram>()
                for (j in 0 until items.length()) {
                    val it = items.optJSONObject(j) ?: continue
                    val title = it.optString("title")
                    val uri = it.optString("yt").takeIf { v -> v.isNotBlank() }?.let { v ->
                        "intent://www.youtube.com/watch?v=$v#Intent;scheme=https;action=android.intent.action.VIEW;package=$pkg;end"
                    } ?: it.optJSONObject("open")?.let { o ->
                        "sintonia://open?" + o.keys().asSequence().joinToString("&") { k -> k + "=" + Uri.encode(o.optString(k)) }
                    }
                    val wide = it.optBoolean("wide") || it.optBoolean("logo")
                    progs += TvProgram(
                        id = (rowId + j + title).hashCode().toLong(),
                        packageName = pkg,
                        channelId = chId,
                        title = title,
                        subtitle = it.optString("subtitle").ifBlank { null },
                        description = null,
                        imageUri = it.optString("img").ifBlank { null },
                        aspectRatio = if (wide) 16f / 9f else 2f / 3f,
                        intentUri = uri,
                        progress = if (it.has("progress")) it.optDouble("progress").toFloat() else null,
                        watchNextType = if (rowId == "continuar") TvContentRepository.WN_CONTINUE else null,
                        isLive = it.optString("subtitle").contains("Ao vivo"),
                    )
                }
                if (progs.isEmpty()) continue
                if (rowId == "continuar") watchNext += progs
                else channels += TvChannel(chId, pkg, row.optString("title"), "", "sintonia://open?a=home", progs)
            }
        }
        watchNext += MediaHistory.programs(context)
        if (channels.isEmpty() && watchNext.isEmpty()) return null

        val byPkg = HashMap<String, AppContent>()
        for (pkg in (channels.map { it.packageName } + watchNext.map { it.packageName }).toSet()) {
            val chs = channels.filter { it.packageName == pkg }
            val (rec, normal) = chs.partition { it.name.contains("Sugest", true) }
            byPkg[pkg] = AppContent(pkg, normal, watchNext.filter { it.packageName == pkg }, rec.flatMap { it.programs })
        }
        return TvContentRepository.State.Ready(byPkg, channels, watchNext)
    }
}
