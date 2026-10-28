package com.hazagou.spectra.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val label: String,
    val activityName: String,
    val isTvApp: Boolean,
)

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val imageCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Lista de apps; emite novamente sempre que um app é instalado, atualizado ou removido. */
    val apps: Flow<List<AppInfo>> = callbackFlow {
        trySend(loadApps())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                imageCache.evictAll()
                trySend(loadApps())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.flowOn(Dispatchers.IO)

    fun loadApps(): List<AppInfo> {
        val result = LinkedHashMap<String, AppInfo>()
        val own = context.packageName
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(tvIntent, 0).forEach { ri ->
            val ai = ri.activityInfo ?: return@forEach
            if (ai.packageName == own) return@forEach
            result[ai.packageName] = AppInfo(ai.packageName, ri.loadLabel(pm).toString(), ai.name, true)
        }
        val mobileIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(mobileIntent, 0).forEach { ri ->
            val ai = ri.activityInfo ?: return@forEach
            if (ai.packageName == own || result.containsKey(ai.packageName)) return@forEach
            result[ai.packageName] = AppInfo(ai.packageName, ri.loadLabel(pm).toString(), ai.name, false)
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    fun label(pkg: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun launch(app: AppInfo): Boolean = launch(app.packageName)

    fun launch(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun openAppInfo(pkg: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun uninstall(pkg: String) {
        runCatching {
            @Suppress("DEPRECATION")
            context.startActivity(
                Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Banner 16:9 do app de TV (ou ícone centralizado sobre fundo, se o app não tiver banner). */
    suspend fun banner(pkg: String, activity: String?, highQuality: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "b:$pkg:$highQuality"
        imageCache.get(key)?.let { return@withContext it }
        val w = if (highQuality) 480 else 320
        val h = w * 9 / 16
        val drawable: Drawable? = runCatching {
            activity?.let { pm.getActivityBanner(android.content.ComponentName(pkg, it)) }
        }.getOrNull() ?: runCatching { pm.getApplicationBanner(pkg) }.getOrNull()
        val bmp = drawable?.let { render(it, w, h) } ?: return@withContext null
        imageCache.put(key, bmp)
        bmp
    }

    suspend fun icon(pkg: String, highQuality: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "i:$pkg:$highQuality"
        imageCache.get(key)?.let { return@withContext it }
        val size = if (highQuality) 256 else 160
        val d = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: return@withContext null
        val bmp = render(d, size, size)
        imageCache.put(key, bmp)
        bmp
    }

    private fun render(d: Drawable, w: Int, h: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        d.setBounds(0, 0, w, h)
        d.draw(canvas)
        return bitmap.asImageBitmap().also { it.prepareToDraw() }
    }
}
