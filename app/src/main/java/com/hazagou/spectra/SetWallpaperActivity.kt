package com.hazagou.spectra

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recebe "Compartilhar" / "Abrir com" de qualquer app (galeria, navegador, gerenciador de arquivos)
 * e define a imagem, GIF ou vídeo como wallpaper do Spectra.
 */
class SetWallpaperActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Suppress("DEPRECATION")
    private fun streamUri(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = if (intent?.action == Intent.ACTION_SEND) streamUri(intent) else intent?.data
        if (uri == null) { finish(); return }
        val app = application as SpectraApp
        scope.launch {
            runCatching { app.wallpapers.import(uri, intent.type) }
                .onSuccess { file ->
                    app.settings.setWallpaperPath(file.absolutePath)
                    Toast.makeText(this@SetWallpaperActivity, "Wallpaper do Spectra atualizado ✨", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(this@SetWallpaperActivity, "Não foi possível usar este arquivo", Toast.LENGTH_SHORT).show() }
            finish()
        }
    }
}
