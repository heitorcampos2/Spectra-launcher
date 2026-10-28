#!/bin/bash
# Valida o cliente ADB do Spectra: conecta em 127.0.0.1:5555, autoriza e envia teclas em outro app.
set -x
mkdir -p shots
PKG=com.hazagou.spectra.debug
ACT=$PKG/com.hazagou.spectra.MainActivity
shot() { sleep "${2:-3}"; adb exec-out screencap -p > "shots/$1.png"; }
log() { echo "$@" >> shots/resultado.txt; }

adb install -r -g apk/SpectraLauncher-debug.apk > shots/install.txt 2>&1
log "secure=$(adb shell getprop ro.adb.secure) type=$(adb shell getprop ro.build.type)"

# adbd também na rede (porta 5555)
adb root; sleep 4; adb wait-for-device
adb shell setprop service.adb.tcp.port 5555
adb shell setprop ctl.restart adbd
sleep 6; adb wait-for-device; sleep 2
log "porta5555: $(adb shell "cat /proc/net/tcp /proc/net/tcp6 | grep -ic ':15B3 '")"

# Controle remoto (PIN 1234) + controle completo ligado
adb shell am start -W -n $ACT; sleep 5; adb shell am force-stop $PKG
cat > /tmp/remote.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><boolean name="enabled" value="true" /><string name="pin">1234</string><string name="salt">0123456789abcdef</string></map>
XML
echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><boolean name=\"enabled\" value=\"true\" /></map>" > /tmp/adb.xml
adb push /tmp/remote.xml /data/local/tmp/remote.xml; adb push /tmp/adb.xml /data/local/tmp/adb.xml
adb shell "cat /data/local/tmp/remote.xml | run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/spectra_remote.xml'"
adb shell "cat /data/local/tmp/adb.xml | run-as $PKG sh -c 'cat > shared_prefs/spectra_adb.xml'"
adb logcat -c
adb shell am start -W -n $ACT
sleep 8
shot 01-dialogo-autorizacao 1

# aceita "Permitir depuração USB?" marcando "sempre permitir"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > shots/ui1.xml
python3 - <<'PY' > /tmp/taps.txt
import re
x=open('shots/ui1.xml',encoding='utf-8',errors='ignore').read()
def c(p):
    for m in re.finditer(r'<node [^>]*>', x):
        n=m.group(0)
        if re.search(p, n, re.I):
            b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
            if b:
                a,b2,c2,d=map(int,b.groups()); return f"{(a+c2)//2} {(b2+d)//2}"
for p in [r'resource-id="[^"]*alwaysUse"', r'resource-id="android:id/button1"']:
    r=c(p)
    if r: print(r)
PY
log "taps: $(cat /tmp/taps.txt | tr '\n' ';')"
while read -r X Y; do adb shell input tap $X $Y; sleep 1; done < /tmp/taps.txt
sleep 5

adb forward tcp:8642 tcp:8642
log "info: $(curl -s -m 5 http://127.0.0.1:8642/api/info)"
T=$(curl -s -m 5 -X POST -d '{"pin":"1234"}' http://127.0.0.1:8642/api/pair | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# Outro app em primeiro plano: Configurações do Android
adb shell am start -W -a android.settings.SETTINGS
sleep 4
adb shell uiautomator dump /sdcard/a.xml >/dev/null 2>&1; adb shell cat /sdcard/a.xml > shots/antes.xml
shot 02-config-antes 1
for k in down down down; do log "key $k: $(curl -s -m 8 -X POST -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/key?k=$k")"; sleep 1; done
log "key back: $(curl -s -m 8 -X POST -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/key?k=back")"
sleep 2
shot 03-depois-voltar 1
log "pkg em foco depois do voltar: $(adb shell dumpsys window | grep -m1 mCurrentFocus)"
adb shell am start -W -a android.settings.SETTINGS
sleep 3
log "key down: $(curl -s -m 8 -X POST -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/key?k=down")"
log "key down: $(curl -s -m 8 -X POST -H "X-Spectra-Token: $T" "http://127.0.0.1:8642/api/key?k=down")"
sleep 2
adb shell uiautomator dump /sdcard/b.xml >/dev/null 2>&1; adb shell cat /sdcard/b.xml > shots/depois.xml
shot 04-config-depois-baixo 1
log "foco antes: $(grep -o 'focused="true"[^>]*bounds="[^"]*"' shots/antes.xml | head -1)"
log "foco depois: $(grep -o 'focused="true"[^>]*bounds="[^"]*"' shots/depois.xml | head -1)"
log "texto: $(curl -s -m 8 -X POST -H "X-Spectra-Token: $T" -d '{"text":"teste spectra"}' http://127.0.0.1:8642/api/text)"
adb logcat -d | grep -E "SpectraAdb|AndroidRuntime" > shots/adb.txt
adb logcat -d -b crash > shots/crash.txt
exit 0
