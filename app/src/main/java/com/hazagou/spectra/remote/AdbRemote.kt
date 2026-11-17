package com.hazagou.spectra.remote

import android.content.Context
import android.util.Base64
import dadb.AdbKeyPair
import dadb.AdbShellStream
import dadb.Dadb
import dadb.AdbShellPacket.Exit
import dadb.AdbShellPacket.StdError
import dadb.AdbShellPacket.StdOut
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Controle de QUALQUER app pela depuração local da própria TV (ADB em 127.0.0.1:5555).
 *
 * No Android TV 11 a acessibilidade não tem "apertar seta" (só existe no Android 13+), e a maioria dos
 * apps de TV (players, SmartTube, WebView, jogos) não deixa o foco ser movido pelos elementos da tela.
 * Com a depuração ligada, o Spectra se conecta à própria TV — a conexão nunca sai do aparelho — e
 * inicia o [ShellInjector], que entrega cada tecla como se viesse do controle físico.
 * Na primeira vez a TV pergunta "Permitir depuração?": é só aceitar marcando "Sempre permitir".
 */
object AdbRemote {
    private const val PORT = 5555

    enum class State { OFF, NO_DEBUG, WAITING, READY, ERROR }

    @Volatile var state: State = State.OFF; private set
    @Volatile var detail: String = ""; private set
    /** true = injetor do Spectra (instantâneo); false = comando "input" do Android (mais lento). */
    @Volatile var fastMode = false; private set

    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "spectra-adb").apply { isDaemon = true } }
    private val writeLock = Any()
    @Volatile private var dadb: Dadb? = null
    @Volatile private var shell: AdbShellStream? = null
    @Volatile private var connecting: Future<*>? = null
    @Volatile private var portCheckedAt = 0L
    @Volatile private var portOpen = false
    @Volatile private var failedAt = 0L

    fun isReady() = shell != null

    /** Mensagem para o celular quando o comando não pôde ser entregue. */
    fun hint(): String = when (state) {
        State.WAITING -> "Na TV, aceite \"Permitir depuração USB?\" (marque \"Sempre permitir\") e tente de novo."
        State.ERROR -> "A depuração da TV não aceitou a conexão${if (detail.isNotBlank()) " ($detail)" else ""}. Abra Controle pelo celular na TV e toque em Conectar."
        else -> "Este app não aceitou o comando. Para controlar qualquer app, ligue a Depuração USB na TV (instruções em Controle pelo celular, na TV)."
    }

    fun statusText(): String = when (state) {
        State.READY -> if (fastMode) "✔ Todos os apps (depuração conectada)" else "✔ Todos os apps (depuração, modo simples)"
        State.WAITING -> "Aceite \"Permitir depuração\" na tela da TV"
        State.ERROR -> "Depuração recusou a conexão"
        State.NO_DEBUG -> "Depuração USB desligada"
        State.OFF -> "Ainda não conectado"
    }

    private fun checkPort(): Boolean {
        val now = System.currentTimeMillis()
        // porta fechada: só confere de novo depois de 20 s (não atrasa cada tecla)
        if (!portOpen && now - portCheckedAt < 20_000) return false
        portCheckedAt = now
        portOpen = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 400) }; true }.getOrDefault(false)
        return portOpen
    }

    private fun okFile(ctx: Context) = File(File(ctx.filesDir, "adb"), "autorizado")
    private fun markAuthorized(ctx: Context) { runCatching { okFile(ctx).writeText(System.currentTimeMillis().toString()) } }

    /** Ao abrir o Spectra: se a TV já autorizou antes, conecta em segundo plano (a 1ª tecla já sai rápida). */
    fun warmUp(ctx: Context) {
        val app = ctx.applicationContext
        if (!okFile(app).exists()) return
        exec.execute { runCatching { ensure(app, waitMs = 0, forcePortCheck = true) } }
    }

    private fun keyPair(ctx: Context): AdbKeyPair {
        val dir = File(ctx.filesDir, "adb")
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    /** Garante a conexão (espera até [waitMs]). A primeira vez fica aguardando o "Permitir" na TV. */
    fun ensure(ctx: Context, waitMs: Long = 2500, forcePortCheck: Boolean = false): Boolean {
        if (shell != null) return true
        val app = ctx.applicationContext
        val f: Future<*>
        synchronized(this) {
            if (shell != null) return true
            val running = connecting?.let { !it.isDone } == true
            if (!running) {
                // recusou ou deu erro há pouco: não pergunta de novo a cada tecla (só pelo botão Conectar, na TV)
                if (!forcePortCheck && state == State.ERROR && System.currentTimeMillis() - failedAt < 60_000) return false
                if (forcePortCheck) portCheckedAt = 0
                if (!checkPort()) { state = State.NO_DEBUG; return false }
                state = State.WAITING
                connecting = exec.submit { connectNow(app) }
            }
            f = connecting!!
        }
        runCatching { f.get(waitMs, TimeUnit.MILLISECONDS) }
        return shell != null
    }

    private fun connectNow(app: Context) {
        var d: Dadb? = null
        try {
            d = Dadb.create("127.0.0.1", PORT, keyPair(app), 3000, 0)
            // 1) injetor do Spectra (rápido)
            val apk = app.applicationInfo.sourceDir
            val inj = d.openShell("CLASSPATH=$apk app_process / ${ShellInjector::class.java.name}")
            if (waitReady(inj)) {
                dadb = d; shell = inj; fastMode = true; state = State.READY; detail = ""
                markAuthorized(app)
                return
            }
            runCatching { inj.close() }
            // 2) plano B: comando "input" do próprio Android
            val sh = d.openShell("sh")
            dadb = d; shell = sh; fastMode = false; state = State.READY; detail = ""
            markAuthorized(app)
        } catch (e: Throwable) {
            runCatching { d?.close() }
            failedAt = System.currentTimeMillis()
            state = State.ERROR
            detail = e.message?.take(80) ?: e.javaClass.simpleName
        }
    }

    /** Espera o injetor avisar que subiu (até 8 s). */
    private fun waitReady(s: AdbShellStream): Boolean {
        val probe = Executors.newSingleThreadExecutor()
        return try {
            probe.submit<Boolean> {
                val out = StringBuilder()
                var ready = false
                while (!ready) {
                    when (val p = s.read()) {
                        is StdOut -> { out.append(String(p.payload)); ready = out.contains("SPECTRA_INJECTOR_READY") }
                        is StdError -> Unit
                        is Exit -> break
                    }
                }
                ready
            }.get(8, TimeUnit.SECONDS)
        } catch (_: Throwable) {
            false
        } finally {
            probe.shutdownNow()
        }
    }

    private fun reset() {
        synchronized(this) {
            runCatching { shell?.close() }
            runCatching { dadb?.close() }
            shell = null; dadb = null; state = State.OFF
        }
    }

    private fun send(ctx: Context, line: String): Boolean {
        repeat(2) {
            if (!ensure(ctx)) return false
            val s = shell ?: return false
            try {
                synchronized(writeLock) { s.write(line + "\n") }
                return true
            } catch (_: Throwable) {
                reset()   // a TV reiniciou a depuração: reconecta uma vez
            }
        }
        return false
    }

    /** Aperta uma tecla (KeyEvent.KEYCODE_*) no app que estiver na tela. */
    fun key(ctx: Context, code: Int, long: Boolean = false): Boolean {
        if (!ensure(ctx)) return false
        return if (fastMode) send(ctx, "k $code ${if (long) 1 else 0}")
        else send(ctx, "input keyevent ${if (long) "--longpress " else ""}$code")
    }

    /** Digita no campo de texto selecionado. */
    fun text(ctx: Context, text: String): Boolean {
        if (!ensure(ctx)) return false
        return if (fastMode) send(ctx, "t " + Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
        else send(ctx, "input text '" + text.replace("'", "").replace(" ", "%s") + "'")
    }
}
