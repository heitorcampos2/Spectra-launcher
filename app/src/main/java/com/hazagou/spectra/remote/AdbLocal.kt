package com.hazagou.spectra.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher

/**
 * "Controle completo": o Spectra conversa com a depuração ADB da própria TV (127.0.0.1:5555)
 * e envia teclas de verdade (`input keyevent`) — funciona em QUALQUER app e em todo o sistema,
 * igual ao controle físico.
 *
 * Requisito, uma única vez: Opções do desenvolvedor › Depuração USB / Depuração de rede ligada,
 * e aceitar "Permitir depuração?" (marque "Sempre permitir deste computador").
 */
object AdbLocal {
    private const val TAG = "SpectraAdb"

    enum class Status { OFF, CONNECTING, WAITING_AUTH, CONNECTED, UNAVAILABLE }
    val status = MutableStateFlow(Status.OFF)
    val lastError = MutableStateFlow<String?>(null)

    private const val A_SYNC = 0x434e5953
    private const val A_CNXN = 0x4e584e43
    private const val A_AUTH = 0x48545541
    private const val A_OPEN = 0x4e45504f
    private const val A_OKAY = 0x59414b4f
    private const val A_CLSE = 0x45534c43
    private const val A_WRTE = 0x45545257
    private const val VERSION = 0x01000000
    private const val MAX_DATA = 256 * 1024

    private const val AUTH_TOKEN = 1
    private const val AUTH_SIGNATURE = 2
    private const val AUTH_RSAPUBLICKEY = 3

    private class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private lateinit var appContext: Context
    private var socket: Socket? = null
    private var out: OutputStream? = null
    private val lock = Object()
    private val localIds = AtomicInteger(1)
    /** Stream de shell persistente (comandos rápidos, sem reabrir conexão). */
    @Volatile private var shellLocal = 0
    @Volatile private var shellRemote = 0
    private val shellOkay = ArrayBlockingQueue<Boolean>(4)
    private val openResults = java.util.concurrent.ConcurrentHashMap<Int, ArrayBlockingQueue<Int>>()
    @Volatile private var connected = false

    private val prefs by lazy { appContext.getSharedPreferences("spectra_adb", Context.MODE_PRIVATE) }
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) { prefs.edit().putBoolean("enabled", v).apply() }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (enabled) Thread({ connect() }, "spectra-adb").apply { isDaemon = true }.start()
    }

    val isConnected: Boolean get() = connected

    // =============================================================== API

    /** Envia uma tecla (código KeyEvent). Conecta sozinho se preciso. */
    fun key(code: Int, long: Boolean = false): Boolean =
        exec(if (long) "input keyevent --longpress $code" else "input keyevent $code")

    fun text(t: String): Boolean {
        if (t.isEmpty()) return true
        // input text: espaço vira %s; o resto vai entre aspas simples
        val escaped = t.replace("'", "'\\''").replace(" ", "%s")
        return exec("input text '$escaped'")
    }

    /** Executa um comando no shell persistente da TV. */
    fun exec(cmd: String): Boolean {
        if (!connected) {
            // só tenta reconectar sozinho se já foi autorizado antes (não trava esperando o diálogo)
            if (!enabled || status.value == Status.WAITING_AUTH) return false
            if (!connect()) return false
        }
        return runCatching { writeShell("$cmd\n") }.getOrElse {
            Log.w(TAG, "shell", it); disconnect()
            connect() && runCatching { writeShell("$cmd\n") }.getOrDefault(false)
        }
    }

    /** Liga o controle completo e tenta conectar (pode abrir o diálogo "Permitir depuração?" na TV). */
    fun enableAndConnect(): Boolean { enabled = true; return connect() }

    fun disable() { enabled = false; disconnect(); status.value = Status.OFF }

    // =============================================================== conexão

    @Synchronized
    fun connect(): Boolean {
        if (connected) return true
        status.value = Status.CONNECTING
        lastError.value = null
        return try {
            val s = Socket()
            s.connect(InetSocketAddress("127.0.0.1", 5555), 1500)
            s.tcpNoDelay = true
            s.soTimeout = 0
            socket = s
            out = s.getOutputStream()
            val input = DataInputStream(s.getInputStream().buffered())
            val keys = loadOrCreateKeys()
            send(A_CNXN, VERSION, MAX_DATA, "host::spectra\u0000".toByteArray())

            var sentSignature = false
            s.soTimeout = 60_000 // tempo para a pessoa aceitar o diálogo na TV
            while (true) {
                val m = read(input)
                when (m.cmd) {
                    A_AUTH -> if (m.arg0 == AUTH_TOKEN) {
                        if (!sentSignature) {
                            sentSignature = true
                            send(A_AUTH, AUTH_SIGNATURE, 0, sign(keys, m.data))
                        } else {
                            status.value = Status.WAITING_AUTH
                            send(A_AUTH, AUTH_RSAPUBLICKEY, 0, adbPublicKey(keys.public as RSAPublicKey))
                        }
                    }
                    A_CNXN -> break
                    else -> Unit
                }
            }
            s.soTimeout = 0
            connected = true
            startReader(input)
            openShell()
            status.value = Status.CONNECTED
            Log.i(TAG, "conectado ao adbd local")
            true
        } catch (e: java.net.SocketTimeoutException) {
            fail(Status.WAITING_AUTH, "A TV não confirmou a depuração. Aceite \"Permitir depuração\" e tente de novo.")
        } catch (e: java.net.ConnectException) {
            fail(Status.UNAVAILABLE, "Depuração desligada na TV (porta 5555 fechada).")
        } catch (e: Exception) {
            Log.w(TAG, "connect", e)
            fail(Status.UNAVAILABLE, e.message ?: "Falha ao conectar")
        }
    }

    private fun fail(st: Status, msg: String): Boolean {
        disconnect()
        status.value = st
        lastError.value = msg
        return false
    }

    fun disconnect() {
        connected = false
        shellLocal = 0; shellRemote = 0
        runCatching { socket?.close() }
        socket = null; out = null
        if (status.value == Status.CONNECTED) status.value = Status.OFF
    }

    private fun startReader(input: DataInputStream) {
        Thread({
            try {
                while (connected) {
                    val m = read(input)
                    when (m.cmd) {
                        A_OKAY -> {
                            openResults.remove(m.arg1)?.offer(m.arg0)
                            if (m.arg1 == shellLocal) { if (shellRemote == 0) shellRemote = m.arg0; shellOkay.offer(true) }
                        }
                        A_WRTE -> send(A_OKAY, m.arg1, m.arg0, ByteArray(0)) // descarta a saída
                        A_CLSE -> {
                            openResults.remove(m.arg1)?.offer(-1)
                            if (m.arg1 == shellLocal) { shellLocal = 0; shellRemote = 0 }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.i(TAG, "leitura encerrada: ${e.message}")
            } finally {
                connected = false
                if (status.value == Status.CONNECTED) status.value = Status.OFF
            }
        }, "spectra-adb-reader").apply { isDaemon = true }.start()
    }

    private fun openShell() {
        val id = localIds.getAndIncrement()
        val q = ArrayBlockingQueue<Int>(1)
        openResults[id] = q
        shellLocal = id
        shellRemote = 0
        shellOkay.clear()
        send(A_OPEN, id, 0, "shell:\u0000".toByteArray())
        val remote = q.poll(4, TimeUnit.SECONDS) ?: error("shell não abriu")
        if (remote < 0) error("shell recusado")
        shellRemote = remote
        shellOkay.clear()
    }

    private fun writeShell(text: String): Boolean {
        if (shellLocal == 0 || shellRemote == 0) openShell()
        shellOkay.clear()
        send(A_WRTE, shellLocal, shellRemote, text.toByteArray())
        // espera o OKAY do adbd (controle de fluxo) — rápido na rede local
        shellOkay.poll(3, TimeUnit.SECONDS) ?: error("sem resposta do shell")
        return true
    }

    // =============================================================== protocolo

    private fun send(cmd: Int, a0: Int, a1: Int, data: ByteArray) {
        val b = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0
        for (x in data) sum += (x.toInt() and 0xFF)
        b.putInt(cmd).putInt(a0).putInt(a1).putInt(data.size).putInt(sum).putInt(cmd.inv())
        b.put(data)
        synchronized(lock) {
            val o = out ?: error("desconectado")
            o.write(b.array()); o.flush()
        }
    }

    private fun read(input: DataInputStream): Msg {
        val h = ByteArray(24)
        input.readFully(h)
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = b.int; val a0 = b.int; val a1 = b.int; val len = b.int
        if (len < 0 || len > MAX_DATA * 4) error("mensagem inválida")
        val data = ByteArray(len)
        input.readFully(data)
        return Msg(cmd, a0, a1, data)
    }

    // =============================================================== chaves RSA (formato do adb)

    private fun loadOrCreateKeys(): KeyPair {
        val dir = File(appContext.filesDir, "adb").apply { mkdirs() }
        val priv = File(dir, "adbkey"); val pub = File(dir, "adbkey.pub")
        if (priv.exists() && pub.exists()) runCatching {
            val kf = KeyFactory.getInstance("RSA")
            return KeyPair(kf.generatePublic(X509EncodedKeySpec(pub.readBytes())), kf.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes())))
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        priv.writeBytes(kp.private.encoded); pub.writeBytes(kp.public.encoded)
        return kp
    }

    /** Assinatura PKCS#1 v1.5 com SHA-1 sobre o token (o token já é tratado como o "hash"). */
    private fun sign(kp: KeyPair, token: ByteArray): ByteArray {
        val prefix = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
        val k = 256
        val block = ByteArray(k)
        block[0] = 0; block[1] = 1
        val padEnd = k - token.size - prefix.size - 1
        for (i in 2 until padEnd) block[i] = 0xFF.toByte()
        block[padEnd] = 0
        System.arraycopy(prefix, 0, block, padEnd + 1, prefix.size)
        System.arraycopy(token, 0, block, k - token.size, token.size)
        val c = Cipher.getInstance("RSA/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, kp.private)
        return c.doFinal(block)
    }

    /** Chave pública no formato que o adbd entende (mincrypt RSAPublicKey + base64 + nome). */
    private fun adbPublicKey(pub: RSAPublicKey): ByteArray {
        val words = 64
        val r32 = BigInteger.ONE.shiftLeft(32)
        var n = pub.modulus
        val r = BigInteger.ONE.shiftLeft(words * 32)
        var rr = r.modPow(BigInteger.valueOf(2), n)
        val n0inv = n.mod(r32).modInverse(r32)
        val b = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(words)
        b.putInt(n0inv.negate().toInt())
        val nWords = IntArray(words); val rrWords = IntArray(words)
        for (i in 0 until words) {
            var dr = n.divideAndRemainder(r32); n = dr[0]; nWords[i] = dr[1].toInt()
            dr = rr.divideAndRemainder(r32); rr = dr[0]; rrWords[i] = dr[1].toInt()
        }
        nWords.forEach { b.putInt(it) }
        rrWords.forEach { b.putInt(it) }
        b.putInt(pub.publicExponent.toInt())
        val encoded = Base64.encodeToString(b.array(), Base64.NO_WRAP)
        return "$encoded spectra@${android.os.Build.MODEL.replace(' ', '_')}\u0000".toByteArray()
    }
}
