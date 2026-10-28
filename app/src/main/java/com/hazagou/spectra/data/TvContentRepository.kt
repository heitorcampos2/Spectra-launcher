package com.hazagou.spectra.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lê o conteúdo que os apps publicam no TvProvider do Android TV — o mesmo mecanismo usado
 * pela tela inicial oficial e por launchers como o Projectivy:
 *
 *  • content://android.media.tv/channel            → canais (TYPE_PREVIEW) de cada app
 *  • content://android.media.tv/preview_program    → cartões de cada canal
 *  • content://android.media.tv/watch_next_program → "Continuar assistindo", "Próximo episódio", "Minha lista"
 *
 * Apps compatíveis: SmartTube, Crunchyroll, Sintonia, YouTube, Netflix, Prime Video, Disney+, Max, Globoplay, Plex…
 *
 * Importante: o TvProvider exige READ_EPG_DATA e NÃO permite "selection"/"sortOrder" sem a permissão de
 * sistema ACCESS_ALL_EPG_DATA. Por isso as consultas são feitas sem filtro e todo o filtro/ordenação
 * acontece aqui no app.
 */
class TvContentRepository(private val context: Context) {

    companion object {
        const val PERMISSION_READ_EPG = "com.android.providers.tv.permission.READ_EPG_DATA"
        const val PERMISSION_READ_LISTINGS = "android.permission.READ_TV_LISTINGS"
        private const val AUTHORITY = "android.media.tv"
        val CHANNELS: Uri = Uri.parse("content://$AUTHORITY/channel")
        val PREVIEW_PROGRAMS: Uri = Uri.parse("content://$AUTHORITY/preview_program")
        val WATCH_NEXT: Uri = Uri.parse("content://$AUTHORITY/watch_next_program")

        // TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_*
        const val WN_CONTINUE = 0
        const val WN_NEXT = 1
        const val WN_NEW = 2
        const val WN_WATCHLIST = 3

        private val RECOMMENDED_NAME = Regex(
            "recomend|recommend|para voc|for you|sugest|suggest|destaque|featured|trending|em alta|popular|top",
            RegexOption.IGNORE_CASE
        )

        fun channelLogoUri(channelId: Long): Uri = Uri.parse("content://$AUTHORITY/channel/$channelId/logo")
    }

    sealed interface State {
        data object NoProvider : State
        data object NoPermission : State
        data class Ready(
            val byPackage: Map<String, AppContent>,
            /** Todos os canais (com programas), para o sistema de canais individuais. */
            val channels: List<TvChannel> = emptyList(),
            /** "Ver a seguir" de todos os apps juntos (mais recentes primeiro). */
            val watchNext: List<TvProgram> = emptyList(),
        ) : State
    }

    /** Diagnóstico mostrado em "Editar canais" (para saber por que um app não aparece). */
    data class Diagnostics(
        val provider: Boolean,
        val readEpg: Boolean,
        val readListings: String,
        val channelRows: Int,
        val channelApps: List<String>,
        val previewRows: Int,
        val watchNextRows: Int,
        val error: String? = null,
    )
    val diagnostics = kotlinx.coroutines.flow.MutableStateFlow<Diagnostics?>(null)

    private fun listingsState(): String = runCatching {
        val info = context.packageManager.getPermissionInfo(PERMISSION_READ_LISTINGS, 0)
        if (hasListingsPermission) "✔ concedida"
        else {
            val dangerous = if (android.os.Build.VERSION.SDK_INT >= 28) info.protection == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
            else (info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
            if (dangerous) "✕ negada — toque em \"Liberar canais dos apps\"" else "✕ negada — esta TV só libera pelo computador (ADB)"
        }
    }.getOrDefault("não existe neste Android")

    /** Sem ela o Android só mostra ao Spectra os canais do próprio Spectra (motivo dos "0 canais"). */
    val listingsExists: Boolean
        get() = runCatching { context.packageManager.getPermissionInfo(PERMISSION_READ_LISTINGS, 0); true }.getOrDefault(false)
    val hasListingsPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, PERMISSION_READ_LISTINGS) == PackageManager.PERMISSION_GRANTED
    val needsListings: Boolean get() = hasProvider && listingsExists && !hasListingsPermission

    private var rawChannels = 0
    private var rawPreview = 0
    private var rawWatchNext = 0

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, PERMISSION_READ_EPG) == PackageManager.PERMISSION_GRANTED

    val hasProvider: Boolean
        get() = context.packageManager.resolveContentProvider(AUTHORITY, 0) != null

    /** Atualiza sozinho quando qualquer app publica/altera canais ou "continuar assistindo". */
    @OptIn(FlowPreview::class)
    fun observe(refreshTick: Flow<Int>): Flow<State> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        runCatching { context.contentResolver.registerContentObserver(Uri.parse("content://$AUTHORITY"), true, observer) }
        trySend(Unit)
        val job = launch { refreshTick.collect { trySend(Unit) } }
        awaitClose {
            job.cancel()
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }.debounce(700).map { load() }.flowOn(Dispatchers.IO)

    suspend fun load(): State = withContext(Dispatchers.IO) {
        fun diag(err: String? = null, ready: State.Ready? = null) {
            diagnostics.value = Diagnostics(
                provider = hasProvider, readEpg = hasPermission, readListings = listingsState(),
                channelRows = rawChannels, channelApps = ready?.channels?.map { it.packageName }?.distinct().orEmpty(),
                previewRows = rawPreview, watchNextRows = rawWatchNext, error = err,
            )
            android.util.Log.i("SpectraTv", "diagnóstico: ${diagnostics.value}")
        }
        rawChannels = 0; rawPreview = 0; rawWatchNext = 0
        if (!hasProvider) { diag("sem provedor de canais (celular/tablet)"); return@withContext State.NoProvider }
        if (!hasPermission) { diag("sem permissão de leitura"); return@withContext State.NoPermission }
        try {
            buildContent().also { diag(ready = it) }
        } catch (e: SecurityException) {
            diag("SecurityException: ${e.message}")
            State.NoPermission
        } catch (e: Exception) {
            diag("${e.javaClass.simpleName}: ${e.message}")
            State.Ready(emptyMap())
        }
    }

    private fun buildContent(): State.Ready {
        val channels = queryChannels()
        val programs = queryPreviewPrograms().groupBy { it.channelId }
        val watchNext = queryWatchNext()

        val result = HashMap<String, AppContent>()
        val packages = (channels.map { it.packageName } + watchNext.map { it.packageName }).toSet()

        for (pkg in packages) {
            val appChannels = channels.filter { it.packageName == pkg }
                .map { ch -> ch.copy(programs = programs[ch.id].orEmpty()) }
                .filter { it.programs.isNotEmpty() }

            val (recChannels, normalChannels) = appChannels.partition { RECOMMENDED_NAME.containsMatchIn(it.name) }
            val wn = watchNext.filter { it.packageName == pkg }

            val continueItems = wn.filter { it.watchNextType == WN_CONTINUE || it.watchNextType == WN_NEXT }
            val recommended = (wn.filter { it.watchNextType == WN_NEW || it.watchNextType == WN_WATCHLIST } +
                recChannels.flatMap { it.programs }).distinctBy { it.title + it.imageUri }

            // Se o app só tiver um canal "padrão" e nenhum recomendado explícito, o canal padrão
            // vira a fileira de Recomendados (é o que esses apps usam para sugestões).
            val finalRecommended: List<TvProgram>
            val finalChannels: List<TvChannel>
            if (recommended.isEmpty() && normalChannels.size == 1) {
                finalRecommended = normalChannels.first().programs
                finalChannels = emptyList()
            } else {
                finalRecommended = recommended
                finalChannels = normalChannels
            }

            if (finalChannels.isEmpty() && continueItems.isEmpty() && finalRecommended.isEmpty()) continue
            result[pkg] = AppContent(pkg, finalChannels, continueItems, finalRecommended)
        }
        val allChannels = channels.map { ch -> ch.copy(programs = programs[ch.id].orEmpty()) }.filter { it.programs.isNotEmpty() }
        return State.Ready(result, allChannels, watchNext)
    }

    private fun queryChannels(): List<TvChannel> {
        val out = ArrayList<TvChannel>()
        context.contentResolver.query(CHANNELS, null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                rawChannels++
                val type = c.str("type")
                if (type != null && type != "TYPE_PREVIEW") continue
                // Não filtra "browsable": só o launcher oficial aprova canais; aqui quem decide é a pessoa (Editar canais).
                val id = c.long("_id") ?: continue
                val pkg = c.str("package_name") ?: continue
                out += TvChannel(
                    id = id,
                    packageName = pkg,
                    name = c.str("display_name").orEmpty().ifBlank { "Canal" },
                    logoUri = channelLogoUri(id).toString(),
                    appLinkIntentUri = c.str("app_link_intent_uri"),
                    programs = emptyList()
                )
            }
        }
        return out
    }

    private fun queryPreviewPrograms(): List<TvProgram> {
        val out = ArrayList<Pair<Int, TvProgram>>()
        context.contentResolver.query(PREVIEW_PROGRAMS, null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                rawPreview++
                if (c.int("browsable", 1) == 0) continue
                val p = c.toProgram(watchNextType = null) ?: continue
                out += c.int("weight", 0) to p
            }
        }
        return out.sortedByDescending { it.first }.map { it.second }
    }

    private fun queryWatchNext(): List<TvProgram> {
        val out = ArrayList<Pair<Long, TvProgram>>()
        context.contentResolver.query(WATCH_NEXT, null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                rawWatchNext++
                if (c.int("browsable", 1) == 0) continue
                val p = c.toProgram(watchNextType = c.int("watch_next_type", WN_CONTINUE)) ?: continue
                out += (c.long("last_engagement_time_utc_millis") ?: 0L) to p
            }
        }
        return out.sortedByDescending { it.first }.map { it.second }
    }

    private fun Cursor.toProgram(watchNextType: Int?): TvProgram? {
        val id = long("_id") ?: return null
        val pkg = str("package_name") ?: return null
        val title = str("title").orEmpty()
        val episode = str("episode_title")
        val season = str("season_display_number")
        val epNum = str("episode_display_number")
        val subtitle = buildString {
            if (!season.isNullOrBlank()) append("T$season ")
            if (!epNum.isNullOrBlank()) append("E$epNum ")
            if (!episode.isNullOrBlank()) append(episode)
        }.trim().ifBlank { null }
        val poster = str("poster_art_uri")
        val thumb = str("thumbnail_uri")
        val pos = long("last_playback_position_millis") ?: 0L
        val dur = long("duration_millis") ?: 0L
        val aspect = when (int("poster_art_aspect_ratio", 0)) {
            0 -> 16f / 9f
            1 -> 3f / 2f
            2 -> 4f / 3f
            3 -> 1f
            4 -> 2f / 3f
            5 -> 1f / 1.441f
            else -> 16f / 9f
        }
        return TvProgram(
            id = id,
            packageName = pkg,
            channelId = long("channel_id") ?: -1L,
            title = title,
            subtitle = subtitle,
            description = str("short_description"),
            imageUri = poster ?: thumb,
            aspectRatio = aspect,
            intentUri = str("intent_uri"),
            progress = if (dur > 0 && pos > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else null,
            watchNextType = watchNextType,
            isLive = int("live", 0) == 1,
            durationMs = dur,
            positionMs = pos,
        )
    }

    private fun Cursor.idx(name: String) = getColumnIndex(name)
    private fun Cursor.str(name: String): String? = idx(name).let { if (it < 0 || isNull(it)) null else getString(it) }
    private fun Cursor.long(name: String): Long? = idx(name).let { if (it < 0 || isNull(it)) null else getLong(it) }
    private fun Cursor.int(name: String, def: Int): Int = idx(name).let { if (it < 0 || isNull(it)) def else getInt(it) }

    /** Abre o programa/episódio exatamente no ponto publicado pelo app. */
    fun open(program: TvProgram, channel: TvChannel? = null, apps: AppRepository): Boolean {
        val candidates = listOfNotNull(program.intentUri, channel?.appLinkIntentUri)
        for (uri in candidates) {
            val ok = runCatching {
                val intent = if (uri.startsWith("intent:")) Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                else Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return apps.launch(program.packageName)
    }
}

data class TvProgram(
    val id: Long,
    val packageName: String,
    val channelId: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val imageUri: String?,
    val aspectRatio: Float,
    val intentUri: String?,
    val progress: Float?,
    val watchNextType: Int?,
    val isLive: Boolean,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
)

data class TvChannel(
    val id: Long,
    val packageName: String,
    val name: String,
    val logoUri: String,
    val appLinkIntentUri: String?,
    val programs: List<TvProgram>,
)

data class AppContent(
    val packageName: String,
    val channels: List<TvChannel>,
    val continueItems: List<TvProgram>,
    val recommended: List<TvProgram>,
)
