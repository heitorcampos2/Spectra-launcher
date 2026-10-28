package com.hazagou.spectra

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.hazagou.spectra.data.AppRepository
import com.hazagou.spectra.data.SectionsRepository
import com.hazagou.spectra.data.SettingsRepository
import com.hazagou.spectra.data.SystemRepository
import com.hazagou.spectra.data.TvContentRepository
import com.hazagou.spectra.data.WallpaperRepository
import com.hazagou.spectra.remote.RemoteServer
import com.hazagou.spectra.remote.RemoteStore
import com.hazagou.spectra.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Spectra Launcher — por Heitor Campos (github.com/heitorcampos2).
 */
class SpectraApp : Application(), ImageLoaderFactory {

    lateinit var settings: SettingsRepository private set
    lateinit var apps: AppRepository private set
    lateinit var tv: TvContentRepository private set
    lateinit var wallpapers: WallpaperRepository private set
    lateinit var sections: SectionsRepository private set
    lateinit var system: SystemRepository private set
    lateinit var remote: RemoteStore private set
    lateinit var updates: UpdateManager private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        apps = AppRepository(this)
        tv = TvContentRepository(this)
        wallpapers = WallpaperRepository(this)
        sections = SectionsRepository(this, settings)
        system = SystemRepository(this)
        remote = RemoteStore(this)
        updates = UpdateManager(this)
        // Servidor do controle remoto: liga/desliga sozinho conforme a configuração (PIN + ativado)
        appScope.launch { remote.config.collect { RemoteServer.sync(this@SpectraApp, remote) } }
        appScope.launch { runCatching { sections.migrateToV4() } }
        appScope.launch { runCatching { settings.migrateV4() } }
    }

    /** Coil afinado para TV: cache em memória generoso, cache em disco e User-Agent de navegador. */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", WallpaperRepository.UA).build())
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .components {
                // GIF animado como wallpaper
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.22).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(250L * 1024 * 1024).build() }
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .build()
    }
}
