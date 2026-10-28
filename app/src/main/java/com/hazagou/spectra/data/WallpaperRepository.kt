package com.hazagou.spectra.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WallpaperItem(
    val id: String,
    val thumbUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val source: String,
) {
    val isHighQuality get() = width >= 1920 && height >= 1080
    val resolutionLabel get() = if (width > 0) "${width}×${height}" else ""
}

data class WallCategory(val id: String, val label: String, val emoji: String, val query: String?)

enum class WallSource(val label: String) {
    CATEGORIES("Categorias"), RANKED("Classificação"), GOOGLE("Google Imagens"), SAVED("Meus wallpapers")
}

/** Classificação dos wallpapers (acervo público Wallhaven, só conteúdo seguro, proporção de TV). */
enum class WallRank(val label: String, val emoji: String, val sorting: String, val topRange: String? = null) {
    TOP_WEEK("Top da semana", "🏆", "toplist", "1w"),
    TOP_MONTH("Top do mês", "🥇", "toplist", "1M"),
    TOP_YEAR("Top do ano", "👑", "toplist", "1y"),
    FAVORITES("Mais favoritados", "❤️", "favorites"),
    VIEWS("Mais vistos", "👁", "views"),
    NEW("Recentes", "🆕", "date_added"),
    RANDOM("Surpresa", "🎲", "random"),
}

/** Resolução mínima. */
enum class WallRes(val label: String, val atleast: String) {
    FHD("Full HD+", "1920x1080"),
    QHD("2K+", "2560x1440"),
    UHD("4K", "3840x2160"),
}

class WallpaperRepository(private val context: Context) {

    companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

        val CATEGORIES = listOf(
            WallCategory("top", "Em alta", "🔥", null),
            WallCategory("anime", "Anime", "🌸", "anime"),
            WallCategory("dark", "Dark / AMOLED", "🌑", "dark minimalism"),
            WallCategory("space", "Espaço", "🌌", "space"),
            WallCategory("nature", "Natureza", "🌿", "nature landscape"),
            WallCategory("minimal", "Minimalista", "◻️", "minimalism"),
            WallCategory("cyberpunk", "Cyberpunk", "🌆", "cyberpunk"),
            WallCategory("games", "Games", "🎮", "video games"),
            WallCategory("fantasy", "Fantasia", "🐉", "fantasy art"),
            WallCategory("animals", "Animais", "🐯", "animals"),
            WallCategory("cars", "Carros", "🏎️", "cars"),
            WallCategory("abstract", "Abstrato", "🌀", "abstract"),
            WallCategory("movies", "Filmes", "🎬", "movies"),
            WallCategory("city", "Cidades", "🏙️", "city night"),
            WallCategory("ocean", "Oceano", "🌊", "ocean"),
            WallCategory("mountains", "Montanhas", "🏔️", "mountains"),
            WallCategory("aurora", "Aurora", "🌠", "aurora"),
            WallCategory("synthwave", "Retrô / Synthwave", "🌇", "synthwave"),
            WallCategory("rain", "Chuva / Aconchego", "🌧️", "rain"),
            WallCategory("flowers", "Flores", "🌺", "flowers"),
            WallCategory("digital", "Arte digital", "🎨", "digital art"),
        )
    }

    private val dir: File get() = File(context.filesDir, "wallpapers").apply { mkdirs() }

    fun isVideo(f: File) = f.extension.lowercase() in setOf("mp4", "mkv", "webm", "3gp", "mov")
    fun isGif(f: File) = f.extension.lowercase() == "gif"

    // ---------------------------------------------------------------- Categorias (Wallhaven, só SFW, ≥1080p)

    suspend fun byCategory(cat: WallCategory, page: Int = 1): List<WallpaperItem> = withContext(Dispatchers.IO) {
        val base = "https://wallhaven.cc/api/v1/search?categories=111&purity=100&atleast=1920x1080&ratios=16x9,16x10,21x9&page=$page"
        val url = if (cat.query == null) "$base&sorting=toplist&topRange=1M"
        else "$base&sorting=relevance&q=${enc(cat.query)}"
        parseWallhaven(httpGet(url))
    }

    private var randomSeed = java.util.UUID.randomUUID().toString().take(6).filter { it.isLetterOrDigit() }

    /** Wallpapers por classificação: top/favoritos/vistos/recentes/aleatório, resolução mínima e tema opcional. */
    suspend fun ranked(rank: WallRank, res: WallRes, theme: WallCategory?, page: Int = 1): List<WallpaperItem> = withContext(Dispatchers.IO) {
        if (rank == WallRank.RANDOM && page == 1) randomSeed = java.util.UUID.randomUUID().toString().filter { it.isLetterOrDigit() }.take(6)
        val sb = StringBuilder("https://wallhaven.cc/api/v1/search?categories=111&purity=100&ratios=16x9,16x10,21x9")
            .append("&atleast=").append(res.atleast)
            .append("&sorting=").append(rank.sorting)
            .append("&order=desc&page=").append(page)
        rank.topRange?.let { sb.append("&topRange=").append(it) }
        if (rank == WallRank.RANDOM) sb.append("&seed=").append(randomSeed)
        theme?.query?.let { sb.append("&q=").append(enc(it)) }
        parseWallhaven(httpGet(sb.toString()))
    }

    suspend fun searchWallhaven(query: String, page: Int = 1): List<WallpaperItem> = withContext(Dispatchers.IO) {
        parseWallhaven(
            httpGet("https://wallhaven.cc/api/v1/search?categories=111&purity=100&atleast=1920x1080&sorting=relevance&page=$page&q=${enc(query)}")
        )
    }

    private fun parseWallhaven(json: String): List<WallpaperItem> {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            WallpaperItem(
                id = "wh_" + o.optString("id"),
                thumbUrl = o.optJSONObject("thumbs")?.optString("large") ?: return@mapNotNull null,
                fullUrl = o.optString("path"),
                width = o.optInt("dimension_x"),
                height = o.optInt("dimension_y"),
                source = "Wallhaven"
            )
        }
    }

    // ---------------------------------------------------------------- Google Imagens

    /**
     * Busca no Google Imagens filtrando por imagens GRANDES (tbs=isz:l) e devolve as URLs originais
     * em alta resolução (não as miniaturas). Se o usuário tiver chave da Custom Search API configurada,
     * usa a API oficial (mais estável); senão lê a página de resultados.
     */
    suspend fun searchGoogle(query: String, page: Int, apiKey: String, cx: String, onlyHq: Boolean): List<WallpaperItem> =
        withContext(Dispatchers.IO) {
            val items = if (apiKey.isNotBlank() && cx.isNotBlank()) runCatching { googleApi(query, page, apiKey, cx) }
                .getOrElse { googleScrape(query, page) }
            else googleScrape(query, page)
            if (onlyHq) items.filter { it.width >= 1600 || it.width == 0 } else items
        }

    private fun googleScrape(query: String, page: Int): List<WallpaperItem> {
        val url = "https://www.google.com/search?tbm=isch&tbs=isz:l&hl=pt-BR&safe=active&q=${enc(query)}&start=${page * 20}"
        val html = httpGet(url, extraHeaders = mapOf("Cookie" to "CONSENT=YES+cb; SOCS=CAI", "Accept-Language" to "pt-BR,pt;q=0.9"))
        val regex = Regex("""\["(https?://[^"]+?)",(\d+),(\d+)]""")
        val out = LinkedHashMap<String, WallpaperItem>()
        var lastThumb: String? = null
        for (m in regex.findAll(html)) {
            val u = unescape(m.groupValues[1])
            val h = m.groupValues[2].toIntOrNull() ?: 0
            val w = m.groupValues[3].toIntOrNull() ?: 0
            if (u.contains("gstatic.com") || u.contains("google.com")) {
                lastThumb = u
                continue
            }
            if (w < 800 || h < 450) continue
            if (out.containsKey(u)) continue
            out[u] = WallpaperItem("g_${u.hashCode()}", lastThumb ?: u, u, w, h, "Google")
            lastThumb = null
        }
        return out.values.toList()
    }

    private fun googleApi(query: String, page: Int, key: String, cx: String): List<WallpaperItem> {
        val url = "https://www.googleapis.com/customsearch/v1?key=${enc(key)}&cx=${enc(cx)}&searchType=image" +
            "&imgSize=xxlarge&safe=active&num=10&start=${page * 10 + 1}&q=${enc(query)}"
        val items = JSONObject(httpGet(url)).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val img = o.optJSONObject("image")
            WallpaperItem(
                id = "gapi_" + o.optString("link").hashCode(),
                thumbUrl = img?.optString("thumbnailLink") ?: o.optString("link"),
                fullUrl = o.optString("link"),
                width = img?.optInt("width") ?: 0,
                height = img?.optInt("height") ?: 0,
                source = "Google"
            )
        }
    }

    private fun unescape(s: String) = s
        .replace("\\u003d", "=").replace("\\u0026", "&").replace("\\u0025", "%")
        .replace("\\/", "/")

    // ---------------------------------------------------------------- Download / aplicar

    /** Baixa a imagem original em alta qualidade e devolve o arquivo local. */
    suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val target = File(dir, "wp_${System.currentTimeMillis()}.img")
        val conn = open(url, emptyMap())
        try {
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it, 64 * 1024) } }
        } finally {
            conn.disconnect()
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, opts)
        if (opts.outWidth <= 0) {
            target.delete()
            error("O arquivo baixado não é uma imagem válida")
        }
        trimHistory()
        target
    }

    /** Importa imagem, GIF ou VÍDEO escolhido no seletor de arquivos/pendrive ou compartilhado por outro app. */
    suspend fun import(uri: android.net.Uri, mimeHint: String? = null): File = withContext(Dispatchers.IO) {
        val mime = mimeHint ?: context.contentResolver.getType(uri) ?: ""
        val ext = when {
            mime.contains("gif") -> "gif"
            mime.startsWith("video/") -> when {
                mime.contains("webm") -> "webm"
                mime.contains("matroska") -> "mkv"
                else -> "mp4"
            }
            uri.toString().lowercase().let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".webm") } ->
                uri.toString().substringAfterLast('.').lowercase()
            uri.toString().lowercase().endsWith(".gif") -> "gif"
            else -> "img"
        }
        val target = File(dir, "wp_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it, 64 * 1024) }
        } ?: error("Não foi possível ler o arquivo")
        trimHistory()
        target
    }

    /** Próximo wallpaper para a troca automática. */
    suspend fun nextForRotation(source: String, current: String?): File? = withContext(Dispatchers.IO) {
        if (source == "saved") {
            val list = saved().filterNot { isVideo(it) }
            if (list.isEmpty()) return@withContext null
            val i = list.indexOfFirst { it.absolutePath == current }
            return@withContext list[(i + 1).mod(list.size)]
        }
        val cat = CATEGORIES.firstOrNull { it.id == source } ?: return@withContext null
        val items = runCatching { byCategory(cat, (1..5).random()) }.getOrDefault(emptyList())
        val pick = items.shuffled().firstOrNull() ?: return@withContext null
        runCatching { download(pick.fullUrl) }.getOrNull()
    }

    fun saved(): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File) { file.delete() }

    private fun trimHistory(max: Int = 24) {
        saved().drop(max).forEach { it.delete() }
    }

    suspend fun applyToSystem(file: File) = withContext(Dispatchers.IO) {
        if (isVideo(file)) return@withContext Result.failure<Unit>(IllegalArgumentException("vídeo"))
        runCatching { FileInputStream(file).use { WallpaperManager.getInstance(context).setStream(it) }; Unit }
    }

    // ---------------------------------------------------------------- HTTP

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String, headers: Map<String, String>): HttpURLConnection {
        var current = url
        repeat(5) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) error("Redirecionamento inválido")
                current = URL(URL(current), loc).toString()
            } else if (code !in 200..299) {
                c.disconnect()
                error("HTTP $code")
            } else return c
        }
        error("Muitos redirecionamentos")
    }

    private fun httpGet(url: String, extraHeaders: Map<String, String> = emptyMap()): String {
        val c = open(url, extraHeaders)
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
}
