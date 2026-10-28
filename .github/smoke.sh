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
  K() { echo "== key $1"; curl -s -m 5 -X POST -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/key?k=$1"; echo; sleep 1; }
  K back            # fecha "Definir como padrão?" (diálogo por cima)
  shot 05a-apos-voltar 2
  K back            # fecha o diálogo do QR code
  shot 05b-apos-voltar2 2
  K down; K down
  shot 05c-apos-baixo 2
  K menu
  shot 05d-apos-menu 2
  K back
  echo "== apps"; curl -s -m 8 -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/apps" | head -c 600; echo
  echo "== web"; curl -s -m 5 http://127.0.0.1:8642/ | head -c 200; echo
} > shots/servidor.txt 2>&1

adb shell am start -W -n $ACT --es open updates
shot 06-atualizacoes 8
adb shell am start -W -n $PKG/com.hazagou.spectra.remote.RemoteActivity
shot 07-controle-celular 6

# Wallpapers › Classificação
adb shell am start -W -n $ACT --es open wallpaper
sleep 3
adb shell input keyevent KEYCODE_DPAD_RIGHT
adb shell input keyevent KEYCODE_DPAD_CENTER
shot 08-wallpapers-classificacao 10

# Cabeçalhos: ← no primeiro cartão mostra a coluna
adb shell am start -W -n $ACT --es open home
sleep 4
adb shell input keyevent KEYCODE_DPAD_LEFT
shot 09-cabecalhos-visiveis 2
adb shell input keyevent KEYCODE_DPAD_RIGHT
shot 10-cabecalhos-ocultos 2

# Controle completo (ADB local) em OUTRO app: Configurações do Android
{
  echo "== props"; adb shell getprop ro.adb.secure; adb shell getprop service.adb.tcp.port; adb shell getprop ro.build.type
} >> shots/servidor.txt 2>&1
adb tcpip 5555 >> shots/servidor.txt 2>&1
sleep 8
adb wait-for-device
sleep 3
{ echo "== portas TCP em escuta (5555=15B3)"; adb shell "cat /proc/net/tcp /proc/net/tcp6 2>/dev/null | grep -i ':15B3'"; } >> shots/servidor.txt 2>&1
adb shell am force-stop $PKG
echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><boolean name=\"enabled\" value=\"true\" /></map>" > /tmp/adb.xml
adb push /tmp/adb.xml /data/local/tmp/adb.xml
adb shell "cat /data/local/tmp/adb.xml | run-as $PKG sh -c 'cat > shared_prefs/spectra_adb.xml'"
adb shell am start -W -n $ACT
sleep 6
shot 11-autorizacao-adb 1
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > shots/ui-autorizacao.xml 2>/dev/null
# aceita o diálogo "Permitir depuração": marca "sempre permitir" e toca em Permitir/OK (pelas coordenadas do dump)
python3 - <<'PY' > /tmp/taps.txt
import re
try:
    x=open('shots/ui-autorizacao.xml',encoding='utf-8',errors='ignore').read()
except Exception: x=''
def center(pattern):
    m=re.search(r'<node[^>]*(?:text|resource-id)="[^"]*(?:%s)[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'%pattern,x,re.I)
    if not m: return None
    a,b,c,d=map(int,m.groups()); return f"{(a+c)//2} {(b+d)//2}"
for p in ['Always allow|Sempre permitir|alwaysUse','button1|^Allow$|Permitir|OK']:
    c=center(p)
    if c: print(c)
PY
cat /tmp/taps.txt >> shots/servidor.txt
while read -r X Y; do adb shell input tap $X $Y; sleep 1; done < /tmp/taps.txt
sleep 4
adb forward tcp:8642 tcp:8642
adb shell am start -W -a android.settings.SETTINGS
sleep 4
shot 12-config-android-antes 1
{
  echo "== info (outro app)"; curl -s -m 5 http://127.0.0.1:8642/api/info; echo
  T=$(curl -s -m 5 -X POST -d '{"pin":"1234"}' http://127.0.0.1:8642/api/pair | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  for k in down down down; do echo "== key $k (outro app)"; curl -s -m 8 -X POST -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/key?k=$k"; echo; sleep 1; done
} >> shots/servidor.txt 2>&1
shot 13-config-android-depois 2
adb logcat -d | grep -E "SpectraAdb" > shots/adb.txt || true

adb logcat -d -b crash > shots/crash.txt
adb logcat -d | grep -E "AndroidRuntime|FATAL|SpectraRemote" > shots/erros.txt || true
exit 0
