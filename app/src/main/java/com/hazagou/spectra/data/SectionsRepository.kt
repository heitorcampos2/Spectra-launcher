package com.hazagou.spectra.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Tipos de seção da tela inicial. */
enum class SectionType(val label: String, val description: String, val defaultIcon: String) {
    AUTO_TV("TV (automática)", "Junta sozinha os apps de streaming e IPTV instalados", "📺"),
    CUSTOM("Personalizada", "Você escolhe os apps", "✦"),
    APP_CONTENT("Canais e continuar assistindo", "Fileiras de cada app compatível (canais, continuar, recomendados)", "🎬"),
    WATCH_NEXT("Ver a seguir", "Continuar assistindo de TODOS os apps numa fileira só", "▶"),
    CHANNEL("Canal", "Um canal específico de um app (escolha em Editar canais)", "📺"),
    INPUTS("Entradas", "HDMI, AV e Cast/Espelhamento", "🔌"),
    TV_APPS("Aplicativos de TV", "Todos os apps feitos para TV", "🖥"),
    MOBILE_APPS("Aplicativos móveis", "Apps de celular instalados", "📱"),
    ALL_APPS("Todos os apps", "Todos os apps visíveis", "▦"),
    SHORTCUTS("Atalhos", "Todos os apps, wallpapers, configurações…", "⚙"),
}

/**
 * Uma seção da tela inicial. `apps` guarda a lista (CUSTOM) ou a ORDEM preferida dos apps
 * (nas seções automáticas). `excluded` remove apps de seções automáticas.
 */
data class HomeSection(
    val id: String,
    val type: SectionType,
    val title: String,
    val icon: String,
    val showTitle: Boolean = true,
    val visible: Boolean = true,
    val apps: List<String> = emptyList(),
    val excluded: Set<String> = emptySet(),
    val cardStyle: CardStyle? = null,
    val cardSize: Int? = null,
    /** CHANNEL: "pacote|nome do canal". */
    val channelKey: String = "",
    /** Grade (várias linhas) em vez de fileira. */
    val grid: Boolean = false,
    /** Linha deslizante: o item focado fica sempre no início da fileira. */
    val sliding: Boolean = true,
    /** "custom" (manual) | "az" (alfabética). */
    val sort: String = "custom",
    /** Tamanho individual de cartões (pacote → 0..3). */
    val appSizes: Map<String, Int> = emptyMap(),
) {
    val channelPackage: String get() = channelKey.substringBefore('|')
    val channelName: String get() = channelKey.substringAfter('|', "")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type.name).put("title", title).put("icon", icon)
        .put("showTitle", showTitle).put("visible", visible)
        .put("apps", JSONArray(apps)).put("excluded", JSONArray(excluded.toList()))
        .put("cardStyle", cardStyle?.name ?: "").put("cardSize", cardSize ?: -1)
        .put("channelKey", channelKey).put("grid", grid).put("sliding", sliding).put("sort", sort)
        .put("appSizes", JSONObject().apply { appSizes.forEach { (k, v) -> put(k, v) } })

    companion object {
        fun fromJson(o: JSONObject): HomeSection? = runCatching {
            fun arr(name: String): List<String> = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            HomeSection(
                id = o.getString("id"),
                type = SectionType.valueOf(o.getString("type")),
                title = o.optString("title"),
                icon = o.optString("icon"),
                showTitle = o.optBoolean("showTitle", true),
                visible = o.optBoolean("visible", true),
                apps = arr("apps"),
                excluded = arr("excluded").toSet(),
                cardStyle = o.optString("cardStyle").takeIf { it.isNotBlank() }?.let { runCatching { CardStyle.valueOf(it) }.getOrNull() },
                cardSize = o.optInt("cardSize", -1).takeIf { it in 0..3 },
                channelKey = o.optString("channelKey"),
                grid = o.optBoolean("grid", false),
                sliding = o.optBoolean("sliding", true),
                sort = o.optString("sort", "custom").ifBlank { "custom" },
                appSizes = o.optJSONObject("appSizes")?.let { j -> j.keys().asSequence().associateWith { k -> j.optInt(k, 1) } } ?: emptyMap(),
            )
        }.getOrNull()

        fun newCustom(title: String, icon: String, apps: List<String> = emptyList()) =
            HomeSection("c_" + UUID.randomUUID().toString().take(8), SectionType.CUSTOM, title, icon, apps = apps)
    }
}

class SectionsRepository(private val context: Context, private val settings: SettingsRepository) {

    private val key = stringPreferencesKey("sections_json")

    fun defaults(favorites: List<String> = emptyList()): List<HomeSection> = listOf(
        HomeSection("tv", SectionType.AUTO_TV, "TV", "📺"),
        HomeSection("watchnext", SectionType.WATCH_NEXT, "Ver a seguir", "▶"),
        HomeSection("content", SectionType.APP_CONTENT, "Continuar assistindo", "🎬", showTitle = false, visible = false),
        HomeSection("fav", SectionType.CUSTOM, "Favoritos", "♥", apps = favorites),
        HomeSection("inputs", SectionType.INPUTS, "Entradas", "🔌"),
        HomeSection("tvapps", SectionType.TV_APPS, "Aplicativos de TV", "🖥"),
        HomeSection("mobile", SectionType.MOBILE_APPS, "Aplicativos móveis", "📱", cardStyle = CardStyle.ICON),
        HomeSection("shortcuts", SectionType.SHORTCUTS, "Atalhos", "⚙"),
    )

    val sections: Flow<List<HomeSection>> = context.dataStore.data.map { p ->
        val raw = p[key]
        if (raw.isNullOrBlank()) {
            val fav = p[SettingsRepository.K.favorites]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
            defaults(fav)
        } else parse(raw)
    }.distinctUntilChanged()

    private fun parse(raw: String): List<HomeSection> = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { HomeSection.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(defaults())

    suspend fun current(): List<HomeSection> = sections.first()

    suspend fun save(list: List<HomeSection>) {
        val json = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        context.dataStore.edit { it[key] = json }
    }

    suspend fun update(id: String, transform: (HomeSection) -> HomeSection) =
        save(current().map { if (it.id == id) transform(it) else it })

    suspend fun add(section: HomeSection, atIndex: Int? = null) {
        val list = current().toMutableList()
        if (atIndex != null && atIndex in 0..list.size) list.add(atIndex, section) else list.add(section)
        save(list)
    }

    suspend fun remove(id: String) = save(current().filterNot { it.id == id })

    suspend fun move(id: String, delta: Int) {
        val list = current().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val s = list.removeAt(i); list.add(j, s)
        save(list)
    }

    suspend fun resetDefaults() = save(defaults())

    /** Liga/desliga um canal individual na tela inicial (cria a seção do canal se não existir). */
    suspend fun setChannelVisible(key: String, title: String, visible: Boolean) {
        val list = current()
        val existing = list.firstOrNull { it.type == SectionType.CHANNEL && it.channelKey == key }
        when {
            existing != null -> update(existing.id) { it.copy(visible = visible) }
            visible -> {
                val idx = list.indexOfFirst { it.type == SectionType.TV_APPS }.takeIf { it >= 0 } ?: list.size
                add(HomeSection("ch_" + UUID.randomUUID().toString().take(8), SectionType.CHANNEL, title, "📺", channelKey = key), idx)
            }
        }
    }

    /** Aplica estilo/tamanho desta seção como padrão para todas. */
    suspend fun applyStyleToAll(from: HomeSection) =
        save(current().map { if (it.id == from.id) it else it.copy(cardSize = from.cardSize, cardStyle = from.cardStyle, grid = from.grid, sliding = from.sliding) })

    /** Move um app dentro da seção (ordem salva em `apps`). `currentOrder` = ordem exibida agora. */
    suspend fun moveApp(sectionId: String, pkg: String, delta: Int, currentOrder: List<String>) {
        val order = currentOrder.toMutableList()
        val i = order.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in order.indices) return
        order.removeAt(i); order.add(j, pkg)
        update(sectionId) { it.copy(apps = order) }
    }

    suspend fun addApp(sectionId: String, pkg: String) =
        update(sectionId) { s -> s.copy(apps = if (pkg in s.apps) s.apps else s.apps + pkg, excluded = s.excluded - pkg) }

    /** CUSTOM: remove da lista. Automáticas: exclui. */
    suspend fun removeApp(sectionId: String, pkg: String) = update(sectionId) { s ->
        if (s.type == SectionType.CUSTOM) s.copy(apps = s.apps - pkg) else s.copy(excluded = s.excluded + pkg)
    }
}

/** Detecção dos apps de streaming / IPTV para a seção automática "TV". */
object StreamingApps {
    /** Ordem preferida (o primeiro pacote instalado de cada grupo é usado). */
    private val KNOWN: List<List<String>> = listOf(
        listOf("com.haizougou.sintonia"),                                                  // Sintonia
        listOf("com.google.android.youtube.tv", "com.google.android.youtube"),             // YouTube
        listOf("com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv", "org.smarttube.stable", "org.smarttube.beta"), // SmartTube
        listOf("com.crunchyroll.crunchyroid"),                                             // Crunchyroll
        listOf("com.netflix.ninja", "com.netflix.mediaclient"),                            // Netflix
        listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),   // Prime Video
        listOf("com.disney.disneyplus"),                                                   // Disney+
        listOf("com.wbd.stream", "com.hbo.hbonow"),                                        // Max
        listOf("com.globo.globotv"),                                                       // Globoplay
        listOf("com.apple.atve.androidtv.appletv"),                                        // Apple TV
        listOf("com.cbs.ca", "com.cbs.app"),                                               // Paramount+
        listOf("tv.pluto.android"),                                                        // Pluto TV
        listOf("com.plexapp.android"),                                                     // Plex
        listOf("org.xbmc.kodi"),                                                           // Kodi
        listOf("tv.twitch.android.app"),                                                   // Twitch
        listOf("com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music"),
        listOf("com.spotify.tv.android", "com.spotify.music"),                             // Spotify
        listOf("deezer.android.tv", "deezer.android.app"),                                 // Deezer
        listOf("com.android.vending"),                                                     // Play Store
    )

    private val IPTV_PACKAGES = setOf(
        "com.nst.iptvsmarterstvbox", "com.nst.smartersplayer", "ar.tvplayer.tv", "com.nathnetwork.xciptv",
        "com.niklabs.pp", "com.perfectplayer", "ru.iptvremote.android.iptv", "com.ottplay.ottplay",
        "com.purple.iptv.player", "com.devcoder.iptvxtreamplayer", "com.flextv.livestore", "net.tvplayer.tv",
    )
    private val IPTV_WORDS = Regex("iptv|m3u|xtream|ott ?player|tivimate|smarters|tv ?player|live ?tv", RegexOption.IGNORE_CASE)

    fun detect(apps: List<AppInfo>): List<String> {
        val installed = apps.associateBy { it.packageName }
        val out = ArrayList<String>()
        for (group in KNOWN) group.firstOrNull { it in installed }?.let { out += it }
        // IPTV logo após Sintonia/YouTube
        val iptv = apps.filter { a ->
            a.packageName !in out && (a.packageName in IPTV_PACKAGES || IPTV_WORDS.containsMatchIn(a.label) || IPTV_WORDS.containsMatchIn(a.packageName))
        }.map { it.packageName }
        out.addAll(minOf(1, out.size), iptv)
        return out
    }
}
