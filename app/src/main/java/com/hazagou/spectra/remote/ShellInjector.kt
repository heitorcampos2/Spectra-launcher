package com.hazagou.spectra.remote

import android.os.SystemClock
import android.util.Base64
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * Injetor de teclas do controle pelo celular, para qualquer app da TV.
 *
 * Não roda dentro do app: o Spectra o inicia pela depuração local da própria TV (ADB em 127.0.0.1),
 * com `CLASSPATH=<apk do Spectra> app_process / com.hazagou.spectra.remote.ShellInjector`.
 * Assim ele roda com a permissão do shell de depuração, que pode mandar teclas para a tela toda
 * (é a mesma técnica do scrcpy). Recebe uma linha por comando pela entrada padrão:
 *   k <keycode> <0|1>   → aperta a tecla (1 = segurar, "OK longo")
 *   t <texto em base64> → digita o texto no campo selecionado
 */
object ShellInjector {

    @JvmStatic
    fun main(args: Array<String>) {
        val im = Class.forName("android.hardware.input.InputManager").getDeclaredMethod("getInstance").invoke(null)
        val inject = im.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        val send = { ev: InputEvent -> runCatching { inject.invoke(im, ev, 0) as Boolean }.getOrDefault(false) }

        fun key(code: Int, long: Boolean) {
            val t = SystemClock.uptimeMillis()
            send(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
            if (long) {
                Thread.sleep(600)
                send(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, 1, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_LONG_PRESS, InputDevice.SOURCE_KEYBOARD))
            }
            send(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        }

        fun text(s: String) {
            val map = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
            for (ch in s) {
                val evs = map.getEvents(charArrayOf(ch)) ?: continue   // caractere sem tecla (ex.: emoji): pula
                for (e in evs) send(KeyEvent.changeTimeRepeat(e, SystemClock.uptimeMillis(), 0))
            }
        }

        println("SPECTRA_INJECTOR_READY")
        System.out.flush()
        val input = System.`in`.bufferedReader()
        while (true) {
            val line = input.readLine() ?: break
            runCatching {
                when {
                    line.startsWith("k ") -> {
                        val p = line.split(' ')
                        key(p[1].toInt(), p.getOrNull(2) == "1")
                    }
                    line.startsWith("t ") -> text(String(Base64.decode(line.substring(2), Base64.NO_WRAP), Charsets.UTF_8))
                    line == "q" -> return
                }
            }
        }
    }
}
