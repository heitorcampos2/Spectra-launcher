package com.hazagou.spectra.remote

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.hazagou.spectra.MainActivity
import com.hazagou.spectra.service.HomeOverrideService
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class CommandResult(val ok: Boolean, val message: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("ok", ok).apply { if (message != null) put("message", message) }
}

/**
 * Executa os comandos recebidos do celular.
 *  • Spectra em primeiro plano → as teclas vão direto para o launcher (sempre funciona).
 *  • Outro app em primeiro plano → Android 13+: acessibilidade; qualquer Android: depuração local da TV
 *    ([AdbRemote], funciona em todos os apps); por último, acessibilidade movendo o foco.
 *  • Mídia e volume → AudioManager (funciona em qualquer app).
 */
object RemoteCommands {

    private val main = Handler(Looper.getMainLooper())
    private const val NEED_A11Y = "Para controlar outros apps, ative \"Spectra — Botão Home e controle\" em Acessibilidade na TV."

    fun canControlOtherApps(): Boolean = HomeOverrideService.instance != null || AdbRemote.isReady()

    fun key(ctx: Context, k: String): CommandResult {
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (k) {
            "volup" -> return volume(audio, AudioManager.ADJUST_RAISE)
            "voldown" -> return volume(audio, AudioManager.ADJUST_LOWER)
            "mute" -> return volume(audio, AudioManager.ADJUST_TOGGLE_MUTE)
            "play" -> return media(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> return media(audio, KeyEvent.KEYCODE_MEDIA_NEXT)
            "prev" -> return media(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "ff" -> return media(audio, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "rew" -> return media(audio, KeyEvent.KEYCODE_MEDIA_REWIND)
            "stop" -> return media(audio, KeyEvent.KEYCODE_MEDIA_STOP)
        }

        val code = when (k) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ok", "oklong" -> KeyEvent.KEYCODE_DPAD_CENTER
            "back" -> KeyEvent.KEYCODE_BACK
            "menu" -> KeyEvent.KEYCODE_MENU
            "home" -> KeyEvent.KEYCODE_HOME
            "settings" -> -2
            else -> return CommandResult(false, "Tecla desconhecida: $k")
        }

        val svc = HomeOverrideService.instance
        if (k == "home") {
            if (svc != null && svc.remoteGlobal("home")) return CommandResult(true)
            return startActivity(ctx, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_FROM_HOME_OVERRIDE, true))
        }
        if (k == "settings") {
            return startActivity(ctx, Intent(android.provider.Settings.ACTION_SETTINGS))
        }

        // 1) Spectra na tela → injeta a tecla na janela em foco do launcher (inclui diálogos abertos)
        MainActivity.foreground?.get()?.let { act ->
            if (injectToOwnWindow(code, long = k == "oklong")) return CommandResult(true)
            if (sendToActivity(act, code, long = k == "oklong")) return CommandResult(true)
        }
        // 2) tablet/celular (tela de toque): setas rolam a tela e OK toca, pela acessibilidade.
        //    Com a depuração conectada (adb tcpip 5555), as teclas de verdade vão primeiro.
        if (svc != null && !svc.isTv && k != "back" && k != "menu") {
            if (AdbRemote.isReady() && AdbRemote.key(ctx, code, long = k == "oklong")) return CommandResult(true)
            if (svc.remoteTouch(k)) return CommandResult(true)
        }
        // 3) TV, Android 13+: ações nativas de seta/OK/voltar da acessibilidade (rápidas, sem depuração)
        if (svc != null && android.os.Build.VERSION.SDK_INT >= 33 && k != "menu" && k != "oklong") {
            val ok = if (k == "back") svc.remoteGlobal("back") else svc.remoteDpad(k)
            if (ok) return CommandResult(true)
        }
        // 4) outro app pela depuração local da TV: funciona em qualquer app (Android TV 11 incluído)
        if (AdbRemote.key(ctx, code, long = k == "oklong")) return CommandResult(true)
        // 5) último recurso: acessibilidade movendo o foco pelos elementos da tela (só alguns apps aceitam)
        if (svc == null && AdbRemote.state == AdbRemote.State.NO_DEBUG) return CommandResult(false, NEED_A11Y + " Ou ligue a Depuração USB na TV para controlar qualquer app.")
        val ok = svc != null && when (k) {
            "back" -> svc.remoteGlobal("back")
            "menu" -> svc.remoteGlobal("menu")
            else -> svc.remoteDpad(k)
        }
        return if (ok) CommandResult(true) else CommandResult(false, AdbRemote.hint())
    }

    fun text(ctx: Context, text: String): CommandResult {
        val svc = HomeOverrideService.instance
        if (svc != null && svc.remoteText(text)) return CommandResult(true)
        // apps que não expõem o campo para a acessibilidade: digita pela depuração local
        if (AdbRemote.text(ctx, text)) return CommandResult(true)
        if (svc == null) return CommandResult(false, NEED_A11Y)
        return CommandResult(false, "Nenhum campo de texto selecionado na TV. Abra a busca do app e tente de novo.")
    }

    fun launch(ctx: Context, pkg: String): CommandResult {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            ?: return CommandResult(false, "App não encontrado")
        return startActivity(ctx, intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    // ------------------------------------------------------------------

    private fun startActivity(ctx: Context, intent: Intent): CommandResult {
        val starter: Context = MainActivity.foreground?.get() ?: HomeOverrideService.instance ?: ctx
        return runOnMain(CommandResult(false, "TV ocupada, tente de novo")) {
            runCatching {
                starter.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.fold({ CommandResult(true) }, { CommandResult(false, "A TV bloqueou a abertura: ${it.message}") })
        }
    }

    private fun volume(audio: AudioManager, dir: Int): CommandResult = runCatching {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        CommandResult(true)
    }.getOrElse { CommandResult(false, "Volume indisponível") }

    private fun media(audio: AudioManager, code: Int): CommandResult = runCatching {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t + 30, KeyEvent.ACTION_UP, code, 0))
        CommandResult(true)
    }.getOrElse { CommandResult(false, "Mídia indisponível") }

    private val instrumentation by lazy { android.app.Instrumentation() }

    /**
     * Injeta a tecla como se viesse do controle físico. O Android só permite isso quando a janela
     * em foco é do próprio Spectra (tela inicial, diálogos, painéis) — é o caso aqui.
     */
    private fun injectToOwnWindow(code: Int, long: Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        return runCatching {
            val t = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
            if (long) {
                Thread.sleep(550)
                instrumentation.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS))
            }
            instrumentation.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
            true
        }.getOrDefault(false)
    }

    private fun sendToActivity(act: MainActivity, code: Int, long: Boolean): Boolean = runOnMain(false) {
        if (act.isFinishing || act.isDestroyed) return@runOnMain false
        val t = SystemClock.uptimeMillis()
        act.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        if (long) act.dispatchKeyEvent(KeyEvent(t, t + 600, KeyEvent.ACTION_DOWN, code, 1))
        act.dispatchKeyEvent(KeyEvent(t, t + if (long) 700 else 40, KeyEvent.ACTION_UP, code, 0))
        true
    }

    /** Executa na thread principal e espera o resultado (máx. 2 s). */
    private fun <T> runOnMain(fallback: T, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrDefault(fallback)
        var result: T? = null
        val latch = CountDownLatch(1)
        main.post { result = runCatching(block).getOrDefault(fallback); latch.countDown() }
        latch.await(2, TimeUnit.SECONDS)
        return result ?: fallback
    }
}
