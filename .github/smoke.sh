#!/bin/bash
# Teste de fumaça no emulador Android TV (roda no GitHub Actions)
set -x
mkdir -p shots
PKG=com.hazagou.spectra.debug
ACT=$PKG/com.hazagou.spectra.MainActivity
shot() { sleep "${2:-4}"; adb exec-out screencap -p > "shots/$1.png"; }

adb install -r -g apk/SpectraLauncher-debug.apk > shots/install.txt 2>&1
adb logcat -c
adb shell am start -W -n $ACT
shot 01-abertura 12
adb shell input keyevent KEYCODE_BACK
shot 02-inicio 3
adb shell input keyevent KEYCODE_MENU
shot 03-menu 3
adb shell input keyevent KEYCODE_BACK

# Liga o controle remoto com PIN 1234 (só funciona no build debug)
adb shell am force-stop $PKG
cat > /tmp/remote.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="enabled" value="true" />
    <string name="pin">1234</string>
    <string name="salt">0123456789abcdef</string>
    <string name="tv_name">TV de teste</string>
</map>
XML
adb push /tmp/remote.xml /data/local/tmp/remote.xml
adb shell "cat /data/local/tmp/remote.xml | run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/spectra_remote.xml'"
adb shell am start -W -n $ACT --es open remote
shot 04-parear-qr 8

# Testa o servidor do controle remoto
adb forward tcp:8642 tcp:8642
{
  echo "== info";  curl -s -m 5 http://127.0.0.1:8642/api/info; echo
  echo "== pin errado"; curl -s -m 5 -X POST -d '{"pin":"0000"}' http://127.0.0.1:8642/api/pair; echo
  echo "== pair"; R=$(curl -s -m 5 -X POST -d '{"pin":"1234"}' http://127.0.0.1:8642/api/pair); echo "$R"
  T=$(echo "$R" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  echo "== sem token"; curl -s -m 5 -X POST "http://127.0.0.1:8642/api/key?k=down"; echo
  echo "== key back"; curl -s -m 5 -X POST -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/key?k=back"; echo
  sleep 2
  echo "== key down"; curl -s -m 5 -X POST -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/key?k=down"; echo
  echo "== key menu"; curl -s -m 5 -X POST -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/key?k=menu"; echo
  echo "== apps"; curl -s -m 8 -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/apps" | head -c 600; echo
  echo "== web"; curl -s -m 5 http://127.0.0.1:8642/ | head -c 200; echo
} > shots/servidor.txt 2>&1
shot 05-apos-comandos 3
adb shell input keyevent KEYCODE_BACK

adb shell am start -W -n $ACT --es open updates
shot 06-atualizacoes 8
adb shell am start -W -n $PKG/com.hazagou.spectra.remote.RemoteActivity
shot 07-controle-celular 6

adb logcat -d -b crash > shots/crash.txt
adb logcat -d | grep -E "AndroidRuntime|FATAL|SpectraRemote" > shots/erros.txt || true
exit 0
