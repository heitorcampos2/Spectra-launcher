package com.hazagou.spectra.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.content.ComponentName
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "O que tocou por último" em cada app de mídia (Spotify, Crunchyroll, Globoplay…), lido pelo aviso de
 * mídia (precisa do Acesso a notificações). Esses apps não liberam o histórico da conta para outros apps;
 * isto guarda só o último item de cada um, com a capa, para o cartão "Continuar" do tablet.
 */
object MediaHistory {

    /** Sintonia e YouTube já têm fileiras próprias; o próprio Spectra não conta. */
    private val SKIP = setOf("com.haizougou.sintonia", "org.smarttube.stable", "com.teamsmart.videomanager.tv", "com.google.android.youtube")
    private const val MAX_AGE = 14L * 24 * 3600 * 1000

    private fun dir(ctx: Context) = File(ctx.filesDir, "media_history").apply { mkdirs() }
    private fun index(ctx: Context) = File(dir(ctx), "index.json")

    @Synchronized
    private fun read(ctx: Context): JSONObject = runCatching { JSONObject(index(ctx).readText()) }.getOrDefault(JSONObject())

    /** Chamado pelo leitor de notificações quando um app de mídia muda o que está tocando. */
    @Synchronized
    fun record(ctx: Context, token: MediaSession.Token?) {
        token ?: return
        val mc = runCatching { MediaController(ctx, token) }.getOrNull() ?: return
        val pkg = mc.packageName ?: return
        if (pkg == ctx.packageName || pkg in SKIP) return
        val md = mc.metadata ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return
        val sub = listOfNotNull(
            md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            md.getString(MediaMetadata.METADATA_KEY_ALBUM)
        ).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val pos = mc.playbackState?.position ?: 0L
        val all = read(ctx)
        val old = all.optJSONObject(pkg)
        // capa: guarda em arquivo (só quando muda o título, para não regravar a cada segundo)
        val art = File(dir(ctx), "$pkg.jpg")
        if (old?.optString("title") != title || !art.exists()) {
            val bmp: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            if (bmp != null) runCatching { art.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
            else md.getString(MediaMetadata.METADATA_KEY_ART_URI)?.let { all.put("$pkg#uri", it) }
        }
        val wide = runCatching {
            val b = md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            b != null && b.width > b.height * 1.2f
        }.getOrDefault(false)
        all.put(pkg, JSONObject().put("title", title).put("sub", sub).put("pos", pos).put("dur", dur)
            .put("t", System.currentTimeMillis()).put("wide", wide).put("v", System.currentTimeMillis()))
        runCatching { index(ctx).writeText(all.toString()) }
    }

    /** Lê as sessões de mídia abertas agora (ao abrir o Spectra). */
    fun scan(ctx: Context, listener: ComponentName) {
        runCatching {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(listener).forEach { record(ctx, it.sessionToken) }
        }
    }

    fun programs(ctx: Context): List<TvProgram> {
        val all = read(ctx)
        val now = System.currentTimeMillis()
        return all.keys().asSequence().filter { !it.contains('#') }.mapNotNull { pkg ->
            val o = all.optJSONObject(pkg) ?: return@mapNotNull null
            val t = o.optLong("t")
            if (now - t > MAX_AGE) return@mapNotNull null
            val art = File(dir(ctx), "$pkg.jpg")
            val img = if (art.exists()) android.net.Uri.fromFile(art).toString() else all.optString("$pkg#uri").ifBlank { null }
            val dur = o.optLong("dur"); val pos = o.optLong("pos")
            TvProgram(
                id = ("media" + pkg).hashCode().toLong(), packageName = pkg, channelId = -1L,
                title = o.optString("title"), subtitle = o.optString("sub").ifBlank { null }, description = null,
                imageUri = img, aspectRatio = if (o.optBoolean("wide")) 16f / 9f else 1f,
                intentUri = "spectra-media:$pkg",
                progress = if (dur > 0 && pos > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else null,
                watchNextType = TvContentRepository.WN_CONTINUE, isLive = false, durationMs = dur, positionMs = pos,
            ) to t
        }.sortedByDescending { it.second }.map { it.first }.toList()
    }

    /** Toque no cartão: abre o app e pede para continuar tocando (se a sessão ainda existir). */
    fun resume(ctx: Context, pkg: String, listener: ComponentName) {
        runCatching {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(listener).firstOrNull { it.packageName == pkg }?.transportControls?.play()
        }
    }

    @Suppress("unused")
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
