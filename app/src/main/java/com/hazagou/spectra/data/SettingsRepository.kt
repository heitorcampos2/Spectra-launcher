package com.hazagou.spectra.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spectra_settings")

enum class AnimalIcon(val label: String) { NONE("Nenhum"), CAT("Gato"), PUG("Pug"), LION("Leão"), TIGER("Tigre"), DRAGON("Dragão") }

enum class WallpaperMotion(val label: String) {
    STATIC("Estático"),
    PARALLAX("Parallax (segue o foco)"),
    DRIFT("Movimento suave"),
    PARALLAX_DRIFT("Parallax + movimento")
}

enum class CardStyle(val label: String) { BANNER("Banner 16:9"), ICON("Ícone redondo"), POSTER("Pôster") }

enum class PerformanceMode(val label: String) {
    MAX_PERFORMANCE("Máximo desempenho"),
    BALANCED("Equilibrado"),
    MAX_QUALITY("Máxima qualidade")
}

enum class SectionKind(val key: String, val label: String) {
    CHANNELS("channels", "Canais"),
    CONTINUE("continue", "Continuar assistindo"),
    RECOMMENDED("recommended", "Recomendados")
}

enum class BgMode(val label: String) { WALLPAPER("Wallpaper"), SOLID("Cor sólida"), GRADIENT("Degradê de 2 cores") }

enum class SurfaceTone(val label: String, val argb: Long, val high: Long) {
    INDIGO("Índigo", 0xCC1C1846L, 0xE6282262L),
    AMOLED("Preto AMOLED", 0xCC000000L, 0xF0101010L),
    GRAPHITE("Grafite", 0xCC22252BL, 0xE62E323AL),
    WINE("Vinho", 0xCC3A1426L, 0xE64A1C33L),
    NAVY("Azul-noite", 0xCC0F1E3DL, 0xE6162A52L),
    FOREST("Floresta", 0xCC10291EL, 0xE6173A2BL),
    GLASS("Vidro claro", 0x66FFFFFFL, 0xD9303046L),
}

enum class FontChoice(val label: String) {
    DEFAULT("Padrão"), SANS("Sans-serif"), SANS_THIN("Sans-serif fina"), SANS_LIGHT("Sans-serif leve"),
    SANS_MEDIUM("Sans-serif média"), SERIF("Serif"), MONO("Monoespaçada"), CURSIVE("Cursiva"), CASUAL("Casual")
}

data class LauncherSettings(
    // ---- título / perfil
    val title: String = "TV",
    val titleEmoji: String = "👻",
    val titleAnimal: AnimalIcon = AnimalIcon.DRAGON,
    val showTitle: Boolean = true,
    val showClock: Boolean = true,
    val profileName: String = "User",
    val profileAvatar: String = "animal:DRAGON",
    val showProfile: Boolean = true,
    // ---- barra de status
    val showWifi: Boolean = true,
    val showNotifications: Boolean = true,
    val showGear: Boolean = true,
    val showBattery: Boolean = true,
    val showSearch: Boolean = true,
    val hideStatusOnScroll: Boolean = true,
    // ---- aparência
    val accent: Long = 0xFFE0135AL,
    val autoAccent: Boolean = true,
    val accentLum: Int = 0,
    val accentAlpha: Float = 1f,
    // ---- cores de fundo e painéis
    val bgMode: BgMode = BgMode.WALLPAPER,
    val bgColor1: Long = 0xFF17154FL,
    val bgColor2: Long = 0xFF5B1B6EL,
    val surfaceTone: SurfaceTone = SurfaceTone.INDIGO,
    // ---- fonte
    val font: FontChoice = FontChoice.DEFAULT,
    val fontBold: Boolean = false,
    val fontItalic: Boolean = false,
    val fontScale: Float = 1f,
    // ---- geral
    val leftHeaders: Boolean = true,
    val idleMinutes: Int = 0,
    val cardStyle: CardStyle = CardStyle.BANNER,
    val cardSize: Int = 1,
    val corner: Int = 14,
    val showLabels: Boolean = true,
    // ---- layout e foco
    val showSectionTitles: Boolean = true,
    val showSectionIcons: Boolean = true,
    val unselectedRowAlpha: Float = 0.45f,
    val fixedFocus: Boolean = true,
    val hideRowsAbove: Boolean = false,
    val rowSpacing: Int = 8,
    val startMargin: Int = 56,
    val cardSpacing: Int = 18,
    // ---- wallpaper
    val wallpaperPath: String? = null,
    val wallpaperMotion: WallpaperMotion = WallpaperMotion.PARALLAX_DRIFT,
    val parallaxStrength: Int = 2,
    val particles: Boolean = true,
    val dim: Float = 0.35f,
    val softBlur: Boolean = false,
    val applySystemWallpaper: Boolean = false,
    val rotationMinutes: Int = 0,
    val rotationSource: String = "saved",
    val videoVolume: Int = 0,
    // ---- desempenho
    val performance: PerformanceMode = PerformanceMode.BALANCED,
    val maxProgramsPerRow: Int = 20,
    // ---- apps / conteúdo
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val sectionOrder: List<String> = emptyList(),
    val disabledSections: Set<String> = emptySet(),
    // ---- entradas
    val inputsDemo: Boolean = false,
    val inputNames: Map<String, String> = emptyMap(),
    val castPackage: String = "",
    // ---- launcher
    val askDefaultLauncher: Boolean = true,
    val googleApiKey: String = "",
    val googleCx: String = "",
) {
    fun isSectionEnabled(pkg: String, kind: SectionKind) =
        "$pkg|*" !in disabledSections && "$pkg|${kind.key}" !in disabledSections

    fun isAppContentEnabled(pkg: String) = "$pkg|*" !in disabledSections
}

class SettingsRepository(private val context: Context) {

    internal object K {
        val title = stringPreferencesKey("title")
        val titleEmoji = stringPreferencesKey("title_emoji")
        val titleAnimal = stringPreferencesKey("title_animal")
        val showTitle = booleanPreferencesKey("show_title")
        val showClock = booleanPreferencesKey("show_clock")
        val profileName = stringPreferencesKey("profile_name")
        val profileAvatar = stringPreferencesKey("profile_avatar")
        val showProfile = booleanPreferencesKey("show_profile")
        val showWifi = booleanPreferencesKey("show_wifi")
        val showNotifications = booleanPreferencesKey("show_notifications")
        val showGear = booleanPreferencesKey("show_gear")
        val showBattery = booleanPreferencesKey("show_battery")
        val showSearch = booleanPreferencesKey("show_search")
        val hideStatusOnScroll = booleanPreferencesKey("hide_status_scroll")
        val accent = longPreferencesKey("accent")
        val accentLum = intPreferencesKey("accent_lum")
        val accentAlpha = floatPreferencesKey("accent_alpha")
        val bgMode = stringPreferencesKey("bg_mode")
        val bgColor1 = longPreferencesKey("bg_color1")
        val bgColor2 = longPreferencesKey("bg_color2")
        val surfaceTone = stringPreferencesKey("surface_tone")
        val font = stringPreferencesKey("font")
        val fontBold = booleanPreferencesKey("font_bold")
        val fontItalic = booleanPreferencesKey("font_italic")
        val fontScale = floatPreferencesKey("font_scale")
        val leftHeaders = booleanPreferencesKey("left_headers")
        val idleMinutes = intPreferencesKey("idle_minutes")
        val autoAccent = booleanPreferencesKey("auto_accent")
        val cardStyle = stringPreferencesKey("card_style")
        val cardSize = intPreferencesKey("card_size")
        val corner = intPreferencesKey("corner")
        val showLabels = booleanPreferencesKey("show_labels")
        val showSectionTitles = booleanPreferencesKey("show_section_titles")
        val showSectionIcons = booleanPreferencesKey("show_section_icons")
        val unselectedRowAlpha = floatPreferencesKey("unselected_row_alpha")
        val fixedFocus = booleanPreferencesKey("fixed_focus")
        val hideRowsAbove = booleanPreferencesKey("hide_rows_above")
        val rowSpacing = intPreferencesKey("row_spacing")
        val startMargin = intPreferencesKey("start_margin")
        val cardSpacing = intPreferencesKey("card_spacing")
        val wallpaperPath = stringPreferencesKey("wallpaper_path")
        val wallpaperMotion = stringPreferencesKey("wallpaper_motion")
        val parallaxStrength = intPreferencesKey("parallax_strength")
        val particles = booleanPreferencesKey("particles")
        val dim = floatPreferencesKey("dim")
        val softBlur = booleanPreferencesKey("soft_blur")
        val applySystemWallpaper = booleanPreferencesKey("apply_system_wallpaper")
        val rotationMinutes = intPreferencesKey("rotation_minutes")
        val rotationSource = stringPreferencesKey("rotation_source")
        val videoVolume = intPreferencesKey("video_volume")
        val performance = stringPreferencesKey("performance")
        val maxPrograms = intPreferencesKey("max_programs")
        val favorites = stringPreferencesKey("favorites")
        val hidden = stringSetPreferencesKey("hidden")
        val sectionOrder = stringPreferencesKey("section_order")
        val disabledSections = stringSetPreferencesKey("disabled_sections")
        val inputsDemo = booleanPreferencesKey("inputs_demo")
        val inputNames = stringSetPreferencesKey("input_names")
        val castPackage = stringPreferencesKey("cast_package")
        val askDefault = booleanPreferencesKey("ask_default")
        val googleApiKey = stringPreferencesKey("google_api_key")
        val googleCx = stringPreferencesKey("google_cx")
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        runCatching { enumValueOf<T>(this ?: return default) }.getOrDefault(default)

    private fun String?.toList(): List<String> =
        this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { p ->
        val d = LauncherSettings()
        LauncherSettings(
            title = p[K.title] ?: d.title,
            titleEmoji = p[K.titleEmoji] ?: d.titleEmoji,
            titleAnimal = p[K.titleAnimal].toEnum(d.titleAnimal),
            showTitle = p[K.showTitle] ?: d.showTitle,
            showClock = p[K.showClock] ?: d.showClock,
            profileName = p[K.profileName] ?: d.profileName,
            profileAvatar = p[K.profileAvatar] ?: d.profileAvatar,
            showProfile = p[K.showProfile] ?: d.showProfile,
            showWifi = p[K.showWifi] ?: d.showWifi,
            showNotifications = p[K.showNotifications] ?: d.showNotifications,
            showGear = p[K.showGear] ?: d.showGear,
            showBattery = p[K.showBattery] ?: d.showBattery,
            showSearch = p[K.showSearch] ?: d.showSearch,
            hideStatusOnScroll = p[K.hideStatusOnScroll] ?: d.hideStatusOnScroll,
            accent = p[K.accent] ?: d.accent,
            autoAccent = p[K.autoAccent] ?: d.autoAccent,
            accentLum = p[K.accentLum] ?: d.accentLum,
            accentAlpha = p[K.accentAlpha] ?: d.accentAlpha,
            bgMode = p[K.bgMode].toEnum(d.bgMode),
            bgColor1 = p[K.bgColor1] ?: d.bgColor1,
            bgColor2 = p[K.bgColor2] ?: d.bgColor2,
            surfaceTone = p[K.surfaceTone].toEnum(d.surfaceTone),
            font = p[K.font].toEnum(d.font),
            fontBold = p[K.fontBold] ?: d.fontBold,
            fontItalic = p[K.fontItalic] ?: d.fontItalic,
            fontScale = p[K.fontScale] ?: d.fontScale,
            leftHeaders = p[K.leftHeaders] ?: d.leftHeaders,
            idleMinutes = p[K.idleMinutes] ?: d.idleMinutes,
            cardStyle = p[K.cardStyle].toEnum(d.cardStyle),
            cardSize = p[K.cardSize] ?: d.cardSize,
            corner = p[K.corner] ?: d.corner,
            showLabels = p[K.showLabels] ?: d.showLabels,
            showSectionTitles = p[K.showSectionTitles] ?: d.showSectionTitles,
            showSectionIcons = p[K.showSectionIcons] ?: d.showSectionIcons,
            unselectedRowAlpha = p[K.unselectedRowAlpha] ?: d.unselectedRowAlpha,
            fixedFocus = p[K.fixedFocus] ?: d.fixedFocus,
            hideRowsAbove = p[K.hideRowsAbove] ?: d.hideRowsAbove,
            rowSpacing = p[K.rowSpacing] ?: d.rowSpacing,
            startMargin = p[K.startMargin] ?: d.startMargin,
            cardSpacing = p[K.cardSpacing] ?: d.cardSpacing,
            wallpaperPath = p[K.wallpaperPath],
            wallpaperMotion = p[K.wallpaperMotion].toEnum(d.wallpaperMotion),
            parallaxStrength = p[K.parallaxStrength] ?: d.parallaxStrength,
            particles = p[K.particles] ?: d.particles,
            dim = p[K.dim] ?: d.dim,
            softBlur = p[K.softBlur] ?: d.softBlur,
            applySystemWallpaper = p[K.applySystemWallpaper] ?: d.applySystemWallpaper,
            rotationMinutes = p[K.rotationMinutes] ?: d.rotationMinutes,
            rotationSource = p[K.rotationSource] ?: d.rotationSource,
            videoVolume = p[K.videoVolume] ?: d.videoVolume,
            performance = p[K.performance].toEnum(d.performance),
            maxProgramsPerRow = p[K.maxPrograms] ?: d.maxProgramsPerRow,
            favorites = p[K.favorites].toList(),
            hidden = p[K.hidden] ?: emptySet(),
            sectionOrder = p[K.sectionOrder].toList(),
            disabledSections = p[K.disabledSections] ?: emptySet(),
            inputsDemo = p[K.inputsDemo] ?: d.inputsDemo,
            inputNames = (p[K.inputNames] ?: emptySet()).mapNotNull { e ->
                val i = e.indexOf('|'); if (i <= 0) null else e.substring(0, i) to e.substring(i + 1)
            }.toMap(),
            castPackage = p[K.castPackage] ?: "",
            askDefaultLauncher = p[K.askDefault] ?: d.askDefaultLauncher,
            googleApiKey = p[K.googleApiKey] ?: "",
            googleCx = p[K.googleCx] ?: "",
        )
    }.distinctUntilChanged()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setTitle(v: String) = edit { it[K.title] = v }
    suspend fun setTitleEmoji(v: String) = edit { it[K.titleEmoji] = v }
    suspend fun setTitleAnimal(v: AnimalIcon) = edit { it[K.titleAnimal] = v.name }
    suspend fun setShowTitle(v: Boolean) = edit { it[K.showTitle] = v }
    suspend fun setShowClock(v: Boolean) = edit { it[K.showClock] = v }
    suspend fun setProfileName(v: String) = edit { it[K.profileName] = v }
    suspend fun setProfileAvatar(v: String) = edit { it[K.profileAvatar] = v }
    suspend fun setShowProfile(v: Boolean) = edit { it[K.showProfile] = v }
    suspend fun setShowWifi(v: Boolean) = edit { it[K.showWifi] = v }
    suspend fun setShowNotifications(v: Boolean) = edit { it[K.showNotifications] = v }
    suspend fun setShowGear(v: Boolean) = edit { it[K.showGear] = v }
    suspend fun setShowBattery(v: Boolean) = edit { it[K.showBattery] = v }
    suspend fun setShowSearch(v: Boolean) = edit { it[K.showSearch] = v }
    suspend fun setHideStatusOnScroll(v: Boolean) = edit { it[K.hideStatusOnScroll] = v }
    suspend fun setAccent(v: Long) = edit { it[K.accent] = v; it[K.autoAccent] = false }
    suspend fun setAutoAccent(v: Boolean) = edit { it[K.autoAccent] = v }
    suspend fun setAccentFull(color: Long, lum: Int, alpha: Float, auto: Boolean) = edit {
        it[K.accent] = color; it[K.accentLum] = lum.coerceIn(-60, 60); it[K.accentAlpha] = alpha.coerceIn(0.3f, 1f); it[K.autoAccent] = auto
    }
    suspend fun setBgMode(v: BgMode) = edit { it[K.bgMode] = v.name }
    suspend fun setBgColor1(v: Long) = edit { it[K.bgColor1] = v }
    suspend fun setBgColor2(v: Long) = edit { it[K.bgColor2] = v }
    suspend fun setSurfaceTone(v: SurfaceTone) = edit { it[K.surfaceTone] = v.name }
    suspend fun setFont(v: FontChoice) = edit { it[K.font] = v.name }
    suspend fun setFontBold(v: Boolean) = edit { it[K.fontBold] = v }
    suspend fun setFontItalic(v: Boolean) = edit { it[K.fontItalic] = v }
    suspend fun setFontScale(v: Float) = edit { it[K.fontScale] = v.coerceIn(0.8f, 1.4f) }
    suspend fun setLeftHeaders(v: Boolean) = edit { it[K.leftHeaders] = v }
    suspend fun setIdleMinutes(v: Int) = edit { it[K.idleMinutes] = v }
    suspend fun setCardStyle(v: CardStyle) = edit { it[K.cardStyle] = v.name }
    suspend fun setCardSize(v: Int) = edit { it[K.cardSize] = v.coerceIn(0, 3) }
    suspend fun setCorner(v: Int) = edit { it[K.corner] = v.coerceIn(0, 32) }
    suspend fun setShowLabels(v: Boolean) = edit { it[K.showLabels] = v }
    suspend fun setShowSectionTitles(v: Boolean) = edit { it[K.showSectionTitles] = v }
    suspend fun setShowSectionIcons(v: Boolean) = edit { it[K.showSectionIcons] = v }
    suspend fun setUnselectedRowAlpha(v: Float) = edit { it[K.unselectedRowAlpha] = v.coerceIn(0.1f, 1f) }
    suspend fun setFixedFocus(v: Boolean) = edit { it[K.fixedFocus] = v }
    suspend fun setHideRowsAbove(v: Boolean) = edit { it[K.hideRowsAbove] = v }
    suspend fun setRowSpacing(v: Int) = edit { it[K.rowSpacing] = v.coerceIn(0, 48) }
    suspend fun setStartMargin(v: Int) = edit { it[K.startMargin] = v.coerceIn(16, 120) }
    suspend fun setCardSpacing(v: Int) = edit { it[K.cardSpacing] = v.coerceIn(6, 40) }
    suspend fun setWallpaperPath(v: String?) = edit { if (v == null) it.remove(K.wallpaperPath) else it[K.wallpaperPath] = v }
    suspend fun setWallpaperMotion(v: WallpaperMotion) = edit { it[K.wallpaperMotion] = v.name }
    suspend fun setParallaxStrength(v: Int) = edit { it[K.parallaxStrength] = v.coerceIn(1, 3) }
    suspend fun setParticles(v: Boolean) = edit { it[K.particles] = v }
    suspend fun setDim(v: Float) = edit { it[K.dim] = v.coerceIn(0f, 0.85f) }
    suspend fun setSoftBlur(v: Boolean) = edit { it[K.softBlur] = v }
    suspend fun setApplySystemWallpaper(v: Boolean) = edit { it[K.applySystemWallpaper] = v }
    suspend fun setRotationMinutes(v: Int) = edit { it[K.rotationMinutes] = v }
    suspend fun setRotationSource(v: String) = edit { it[K.rotationSource] = v }
    suspend fun setVideoVolume(v: Int) = edit { it[K.videoVolume] = v.coerceIn(0, 100) }
    suspend fun setPerformance(v: PerformanceMode) = edit { it[K.performance] = v.name }
    suspend fun setMaxPrograms(v: Int) = edit { it[K.maxPrograms] = v.coerceIn(5, 50) }
    suspend fun setAskDefaultLauncher(v: Boolean) = edit { it[K.askDefault] = v }
    suspend fun setGoogleKeys(key: String, cx: String) = edit { it[K.googleApiKey] = key.trim(); it[K.googleCx] = cx.trim() }
    suspend fun setInputsDemo(v: Boolean) = edit { it[K.inputsDemo] = v }
    suspend fun setCastPackage(v: String) = edit { it[K.castPackage] = v }
    suspend fun setInputName(id: String, name: String) = edit {
        val cur = (it[K.inputNames] ?: emptySet()).filterNot { e -> e.startsWith("$id|") }.toSet()
        it[K.inputNames] = if (name.isBlank()) cur else cur + "$id|$name"
    }

    suspend fun setFavorites(list: List<String>) = edit { it[K.favorites] = list.distinct().joinToString(",") }
    suspend fun setSectionOrder(list: List<String>) = edit { it[K.sectionOrder] = list.distinct().joinToString(",") }

    suspend fun setHidden(pkg: String, hidden: Boolean) = edit {
        val cur = it[K.hidden] ?: emptySet()
        it[K.hidden] = if (hidden) cur + pkg else cur - pkg
    }

    /** key = "pkg|channels" | "pkg|continue" | "pkg|recommended" | "pkg|*" */
    suspend fun setSectionDisabled(key: String, disabled: Boolean) = edit {
        val cur = it[K.disabledSections] ?: emptySet()
        it[K.disabledSections] = if (disabled) cur + key else cur - key
    }

    // ------------------------------------------------------------------ Backup / restauração

    /** Exporta TODAS as configurações (inclusive seções) em JSON. */
    suspend fun exportJson(): String {
        val prefs = context.dataStore.data.first()
        val root = JSONObject().put("app", "Spectra Launcher").put("format", 2)
        val values = JSONObject()
        prefs.asMap().forEach { (key, v) ->
            val o = JSONObject()
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> o.put("t", "s").put("v", v)
                is Set<*> -> o.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                else -> return@forEach
            }
            values.put(key.name, o)
        }
        return root.put("values", values).toString(2)
    }

    suspend fun importJson(json: String) {
        val values = JSONObject(json).getJSONObject("values")
        context.dataStore.edit { p ->
            p.clear()
            values.keys().forEach { name ->
                val o = values.getJSONObject(name)
                when (o.getString("t")) {
                    "b" -> p[booleanPreferencesKey(name)] = o.getBoolean("v")
                    "i" -> p[intPreferencesKey(name)] = o.getInt("v")
                    "l" -> p[longPreferencesKey(name)] = o.getLong("v")
                    "f" -> p[floatPreferencesKey(name)] = o.getDouble("v").toFloat()
                    "s" -> p[stringPreferencesKey(name)] = o.getString("v")
                    "ss" -> {
                        val a = o.getJSONArray("v")
                        p[stringSetPreferencesKey(name)] = (0 until a.length()).map { a.getString(it) }.toSet()
                    }
                }
            }
        }
    }
}
