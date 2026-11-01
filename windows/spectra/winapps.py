"""Apps do Windows: lista do Menu Iniciar, apps do Chrome/Edge (web apps), ícones e abertura.

No Windows a lista vem do PowerShell (Get-StartApps + atalhos do Menu Iniciar). Fora do Windows
(para testar a interface) o Spectra mostra um conjunto de exemplo.
"""

import base64
import configparser
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
import time
from pathlib import Path

IS_WIN = os.name == "nt"
NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)

# ------------------------------------------------------------------ PowerShell
SCAN_PS = r"""
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$apps = @()
try { $apps = @(Get-StartApps | ForEach-Object { [pscustomobject]@{ name = $_.Name; aumid = $_.AppID } }) } catch {}
$sh = New-Object -ComObject WScript.Shell
$roots = @([Environment]::GetFolderPath('StartMenu'), [Environment]::GetFolderPath('CommonStartMenu'))
$lnks = @()
foreach ($r in $roots) {
  if (-not $r) { continue }
  $p = Join-Path $r 'Programs'
  Get-ChildItem -LiteralPath $p -Recurse -Filter *.lnk -ErrorAction SilentlyContinue | ForEach-Object {
    $t = ''; $a = ''; $i = ''
    try { $l = $sh.CreateShortcut($_.FullName); $t = $l.TargetPath; $a = $l.Arguments; $i = $l.IconLocation } catch {}
    $lnks += [pscustomobject]@{ path = $_.FullName; name = $_.BaseName; target = $t; args = $a; icon = $i; folder = $_.Directory.Name }
  }
}
[pscustomobject]@{ apps = $apps; lnks = $lnks } | ConvertTo-Json -Depth 4 -Compress
"""

# Ícones em alta (256 px) pelo próprio Shell do Windows, inclusive apps da Microsoft Store.
ICON_PS = r"""
$ErrorActionPreference = 'Stop'
$src = @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class SpectraIcon {
  [ComImport, Guid("bcc18b79-ba16-442f-80c4-8a59c30c463b"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
  public interface IShellItemImageFactory { [PreserveSig] int GetImage(SIZE size, int flags, out IntPtr phbm); }
  [StructLayout(LayoutKind.Sequential)] public struct SIZE { public int cx; public int cy; }
  [StructLayout(LayoutKind.Sequential)] public struct BITMAP { public int bmType; public int bmWidth; public int bmHeight; public int bmWidthBytes; public ushort bmPlanes; public ushort bmBitsPixel; public IntPtr bmBits; }
  [StructLayout(LayoutKind.Sequential)] public struct BITMAPINFOHEADER { public int biSize; public int biWidth; public int biHeight; public ushort biPlanes; public ushort biBitCount; public int biCompression; public int biSizeImage; public int biXPelsPerMeter; public int biYPelsPerMeter; public int biClrUsed; public int biClrImportant; }
  [StructLayout(LayoutKind.Sequential)] public struct DIBSECTION { public BITMAP dsBm; public BITMAPINFOHEADER dsBmih; public int b0; public int b1; public int b2; public IntPtr dshSection; public int dsOffset; }
  [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
  static extern void SHCreateItemFromParsingName(string path, IntPtr pbc, [MarshalAs(UnmanagedType.LPStruct)] Guid riid, [MarshalAs(UnmanagedType.Interface)] out IShellItemImageFactory ppv);
  [DllImport("gdi32.dll")] static extern int GetObject(IntPtr h, int c, ref DIBSECTION ds);
  [DllImport("gdi32.dll")] static extern bool DeleteObject(IntPtr h);
  public static string Save(string parsing, string outPng, int px) {
    IntPtr hbm = IntPtr.Zero;
    try {
      IShellItemImageFactory f;
      SHCreateItemFromParsingName(parsing, IntPtr.Zero, new Guid("bcc18b79-ba16-442f-80c4-8a59c30c463b"), out f);
      SIZE s; s.cx = px; s.cy = px;
      int hr = f.GetImage(s, 0x5, out hbm);           // SIIGBF_BIGGERSIZEOK | SIIGBF_ICONONLY
      if (hr != 0 || hbm == IntPtr.Zero) return "hr " + hr;
      DIBSECTION ds = new DIBSECTION();
      int n = GetObject(hbm, Marshal.SizeOf(typeof(DIBSECTION)), ref ds);
      Bitmap outb;
      if (n > 0 && ds.dsBm.bmBitsPixel == 32 && ds.dsBm.bmBits != IntPtr.Zero) {
        using (Bitmap wrap = new Bitmap(ds.dsBm.bmWidth, ds.dsBm.bmHeight, ds.dsBm.bmWidthBytes, PixelFormat.Format32bppPArgb, ds.dsBm.bmBits)) {
          outb = new Bitmap(wrap.Width, wrap.Height, PixelFormat.Format32bppArgb);
          using (Graphics g = Graphics.FromImage(outb)) { g.DrawImage(wrap, 0, 0, wrap.Width, wrap.Height); }
        }
        if (ds.dsBmih.biHeight > 0) outb.RotateFlip(RotateFlipType.RotateNoneFlipY);   // DIB de baixo para cima
      } else {
        outb = Image.FromHbitmap(hbm);
      }
      outb.Save(outPng, ImageFormat.Png);
      outb.Dispose();
      return "ok";
    } catch (Exception e) { return e.Message; }
    finally { if (hbm != IntPtr.Zero) DeleteObject(hbm); }
  }
}
'@
Add-Type -TypeDefinition $src -ReferencedAssemblies System.Drawing
$jobs = Get-Content -LiteralPath $env:SPECTRA_ICON_JOBS -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($j in $jobs) {
  $r = [SpectraIcon]::Save($j.src, $j.out, 256)
  if ($r -ne 'ok' -and $j.alt) {
    try { $ic = [System.Drawing.Icon]::ExtractAssociatedIcon($j.alt); $b = $ic.ToBitmap(); $b.Save($j.out, [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() } catch {}
  }
}
"""

# Diálogo "Abrir arquivo" do Windows (para adicionar um programa .exe/.lnk ou escolher uma imagem).
PICK_PS = r"""
Add-Type -AssemblyName System.Windows.Forms
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$d = New-Object System.Windows.Forms.OpenFileDialog
$d.Title = $env:SPECTRA_PICK_TITLE
$d.Filter = $env:SPECTRA_PICK_FILTER
$d.Multiselect = $false
$f = New-Object System.Windows.Forms.Form
$f.TopMost = $true; $f.ShowInTaskbar = $false; $f.WindowState = 'Minimized'
if ($d.ShowDialog($f) -eq 'OK') { $d.FileName }
"""


def run_ps(script, env=None, timeout=90):
    """Roda um script PowerShell sem abrir janela e devolve a saída (texto)."""
    enc = base64.b64encode(script.encode("utf-16-le")).decode("ascii")
    exe = shutil.which("powershell") or os.path.join(os.environ.get("SystemRoot", r"C:\Windows"),
                                                      r"System32\WindowsPowerShell\v1.0\powershell.exe")
    r = subprocess.run([exe, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc],
                       capture_output=True, timeout=timeout, creationflags=NO_WINDOW,
                       env={**os.environ, **(env or {})})
    out = r.stdout.decode("utf-8", "replace").strip().lstrip("\ufeff")
    if r.returncode != 0 and not out:
        raise RuntimeError(r.stderr.decode("utf-8", "replace").strip()[:400] or f"PowerShell saiu com {r.returncode}")
    return out


def pick_file(title, filt):
    if not IS_WIN:
        raise RuntimeError("Disponível só no Windows")
    return run_ps(PICK_PS, {"SPECTRA_PICK_TITLE": title, "SPECTRA_PICK_FILTER": filt}, timeout=600).strip()


# ------------------------------------------------------------------ classificação
NOISE = re.compile(r"(uninstall|desinstal|remover |\bremove\b|readme|leia-?me|\bhelp\b|ajuda|manual|documenta|release notes|"
                   r"notas da vers|website|\blicen|support|suporte|changelog|what'?s new|^parar o |^fechar o |\(aberto na rede\)|"
                   r"debugging tool|^reset |recovery|modo de seguran|safe mode|command prompt for|developer command)", re.I)
TV_NAMES = re.compile(r"(youtube|netflix|prime ?video|amazon prime|disney|globoplay|\bmax\b|hbo|crunchyroll|spotify|deezer|"
                      r"twitch|plex|kodi|\bvlc\b|pluto|paramount|apple tv|apple music|star\+|stremio|sintonia|tidal|"
                      r"amazon music|mubi|claro tv|vivo play|telecine|univer|looke|discovery|espn|dazn|pandora|soundcloud)", re.I)
GAME_NAMES = re.compile(r"(\bsteam\b|epic games|\bxbox\b|\bgog\b|battle\.net|ea app|\borigin\b|ubisoft|riot client|minecraft|"
                        r"roblox|league of legends|valorant|fortnite|counter-strike|dota|genshin|gta|playnite|retroarch|"
                        r"geforce now|game pass|solitaire|paciência|hoyoplay|rockstar games)", re.I)
APP_ID_RE = re.compile(r"--app-id=([a-p]{32})", re.I)
PROFILE_RE = re.compile(r'--profile-directory=(?:"([^"]+)"|(\S+))', re.I)


def _key(*parts):
    return "a" + hashlib.sha1("|".join(p or "" for p in parts).lower().encode("utf-8")).hexdigest()[:12]


def _browser_of(target, args):
    t = (target or "").lower()
    if "--app-id=" not in (args or "").lower():
        return ""
    if "msedge" in t or "\\edge\\" in t:
        return "edge"
    if "brave" in t:
        return "brave"
    return "chrome"


def _pwa_icon(browser, profile, app_id):
    """PNG do web app instalado (pasta 'Manifest Resources' do perfil do navegador)."""
    base = {"chrome": r"Google\Chrome\User Data", "edge": r"Microsoft\Edge\User Data",
            "brave": r"BraveSoftware\Brave-Browser\User Data"}.get(browser)
    if not base or not app_id:
        return None
    root = Path(os.environ.get("LOCALAPPDATA", "")) / base / (profile or "Default") / "Web Applications" / "Manifest Resources" / app_id / "Icons"
    best, size = None, 0
    try:
        for f in root.glob("*.png"):
            try:
                n = int(f.stem)
            except ValueError:
                n = 1
            if n > size:
                best, size = f, n
    except OSError:
        return None
    return best


def classify(app):
    name = app["name"]
    tags = set(app.get("tags") or [])
    if app["kind"] == "sintonia" or name.strip().lower() == "sintonia":
        tags.add("tv")
    if TV_NAMES.search(name):
        tags.add("tv")
    if GAME_NAMES.search(name) or "steam://rungameid" in (app.get("url") or "") or "com.epicgames.launcher://" in (app.get("url") or ""):
        tags.add("games")
    if app["kind"] in ("chrome", "edge", "brave"):
        tags.add("chrome")
    app["tags"] = sorted(tags)
    return app


# ------------------------------------------------------------------ índice
class AppIndex:
    def __init__(self, data_dir, demo=False):
        self.dir = Path(data_dir)
        self.icons = self.dir / "icones"
        self.icons.mkdir(parents=True, exist_ok=True)
        self.demo = demo or not IS_WIN
        self.lock = threading.RLock()
        self.apps = {}
        self.scanned_at = 0
        self.error = ""
        self.scanning = False
        self.icon_rev = 0
        self.rev = 0
        self.watching = False
        cache = self.dir / "apps.json"
        try:
            saved = json.loads(cache.read_text(encoding="utf-8"))
            self.apps = {a["key"]: a for a in saved.get("apps", [])}
            self.scanned_at = saved.get("at", 0)
        except (OSError, ValueError, KeyError):
            pass

    # --- leitura -------------------------------------------------------------
    def list(self):
        with self.lock:
            return sorted(self.apps.values(), key=lambda a: a["name"].lower())

    def get(self, key):
        with self.lock:
            return self.apps.get(key)

    def status(self):
        return {"scannedAt": self.scanned_at, "error": self.error, "scanning": self.scanning,
                "demo": self.demo, "iconRev": self.icon_rev, "count": len(self.apps), "rev": self.rev}

    # --- novos apps sem reabrir o Spectra -------------------------------------
    @staticmethod
    def _watch_roots():
        roots = []
        for env, sub in (("APPDATA", r"Microsoft\Windows\Start Menu\Programs"),
                         ("ProgramData", r"Microsoft\Windows\Start Menu\Programs"),
                         ("USERPROFILE", "Desktop"), ("PUBLIC", "Desktop")):
            base = os.environ.get(env)
            if base and os.path.isdir(os.path.join(base, sub)):
                roots.append(os.path.join(base, sub))
        return roots

    @staticmethod
    def _pwa_roots():
        out = []
        base = os.environ.get("LOCALAPPDATA")
        if not base:
            return out
        for rel in (r"Google\Chrome\User Data", r"Microsoft\Edge\User Data", r"BraveSoftware\Brave-Browser\User Data"):
            for prof in Path(base, rel).glob("*/Web Applications/Manifest Resources"):
                out.append(str(prof))
        return out

    def signature(self):
        """Impressão digital barata das pastas onde aparecem os apps novos (atalhos e web apps)."""
        h = hashlib.sha1()
        for root in self._watch_roots():
            for dirpath, _dirs, files in os.walk(root):
                for f in files:
                    if f.lower().endswith((".lnk", ".url")):
                        p = os.path.join(dirpath, f)
                        try:
                            h.update(f"{p}|{int(os.stat(p).st_mtime)}".encode("utf-8", "replace"))
                        except OSError:
                            pass
        for d in self._pwa_roots():
            try:
                h.update(("|".join(sorted(os.listdir(d)))).encode("utf-8", "replace"))
            except OSError:
                pass
        return h.hexdigest()

    def start_watch(self, every=5, full_every=900):
        """Relê a lista sozinho quando um app é instalado ou removido (ex.: instalar um app pelo Chrome)."""
        if self.watching:
            return
        self.watching = True

        def loop():
            try:
                sig = self.signature()
            except Exception:  # noqa: BLE001
                sig = ""
            last_full = time.time()
            while True:
                time.sleep(every)
                try:
                    now = self.signature()
                except Exception:  # noqa: BLE001
                    continue
                if now != sig or time.time() - last_full > full_every:
                    sig = now
                    last_full = time.time()
                    if not self.scanning:
                        time.sleep(1.5)          # o navegador termina de criar o atalho e o ícone
                        self.scan()
        threading.Thread(target=loop, daemon=True).start()

    def scan_async(self, force=False):
        if self.scanning:
            return
        if not force and self.apps and time.time() - self.scanned_at < 600:
            return
        threading.Thread(target=self.scan, daemon=True).start()

    def scan(self):
        self.scanning = True
        try:
            apps = self._demo_apps() if self.demo else self._scan_windows()
            with self.lock:
                self.apps = {a["key"]: classify(a) for a in apps}
                self.scanned_at = int(time.time())
                self.error = ""
                self.rev += 1
            tmp = self.dir / "apps.json.tmp"
            tmp.write_text(json.dumps({"at": self.scanned_at, "apps": list(self.apps.values())}, ensure_ascii=False),
                           encoding="utf-8")
            os.replace(tmp, self.dir / "apps.json")
            self._icons()
        except Exception as exc:  # noqa: BLE001
            self.error = f"Não consegui ler os apps do Windows: {exc}"
        finally:
            self.scanning = False

    # --- Windows -------------------------------------------------------------
    def _scan_windows(self):
        raw = run_ps(SCAN_PS)
        data = json.loads(raw or "{}")
        start = data.get("apps") or []
        lnks = data.get("lnks") or []
        if isinstance(start, dict):
            start = [start]
        if isinstance(lnks, dict):
            lnks = [lnks]
        by_name = {}
        out = {}
        skip = set()
        for l in lnks:
            name = (l.get("name") or "").strip()
            if not name or NOISE.search(name):
                continue
            target, args = l.get("target") or "", l.get("args") or ""
            if "-m spectra" in args:          # os atalhos do próprio Spectra
                skip.add(name.lower())
                continue
            if target.lower().endswith((".url", ".chm", ".txt", ".pdf", ".htm", ".html")):
                continue
            browser = _browser_of(target, args)
            kind = browser or "win"
            if name.lower() == "sintonia" and ("sintonia" in args.lower() or "\\sintonia" in (l.get("path") or "").lower()):
                kind = "sintonia"
            app_id = ""
            profile = ""
            if browser:
                m = APP_ID_RE.search(args)
                app_id = m.group(1) if m else ""
                pm = PROFILE_RE.search(args)
                profile = (pm.group(1) or pm.group(2)) if pm else "Default"
            a = {"key": _key("lnk", l.get("path")), "name": name, "kind": kind, "lnk": l.get("path"), "target": target,
                 "args": args, "appId": app_id, "profile": profile, "aumid": "", "folder": l.get("folder") or ""}
            # o mesmo web app pode ter atalho no Menu Iniciar e na Área de Trabalho: fica um só
            dedup = (kind, app_id) if app_id else ("n", name.lower())
            if dedup in by_name:
                continue
            by_name[dedup] = a
            by_name.setdefault(("n", name.lower()), a)
            out[a["key"]] = a
        for s in start:
            name = (s.get("name") or "").strip()
            aumid = s.get("aumid") or ""
            if not name or not aumid or NOISE.search(name) or name.lower() in skip:
                continue
            hit = by_name.get(("n", name.lower()))
            if hit:
                hit["aumid"] = aumid          # mesmo app: guarda o AppID para o ícone em alta
                continue
            if aumid.lower().startswith(("http://", "https://")):
                continue
            kind = "store" if "!" in aumid else "win"
            a = {"key": _key("aumid", aumid), "name": name, "kind": kind, "lnk": "", "target": "", "args": "",
                 "appId": "", "profile": "", "aumid": aumid, "folder": ""}
            out[a["key"]] = a
            by_name[("n", name.lower())] = a
        # atalhos de jogos (.url do Steam/Epic) no Menu Iniciar e na Área de Trabalho
        for root in self._url_roots():
            for f in root.rglob("*.url"):
                try:
                    cp = configparser.RawConfigParser(strict=False)
                    cp.read(f, encoding="utf-8-sig")
                    url = cp.get("InternetShortcut", "URL", fallback="")
                except Exception:  # noqa: BLE001
                    continue
                if not url.startswith(("steam://rungameid", "com.epicgames.launcher://", "uplay://", "origin://",
                                       "battlenet://", "goggalaxy://")):
                    continue
                name = f.stem
                if ("n", name.lower()) in by_name:
                    continue
                a = {"key": _key("url", str(f)), "name": name, "kind": "win", "lnk": str(f), "target": "", "args": "",
                     "appId": "", "profile": "", "aumid": "", "folder": f.parent.name, "url": url,
                     "iconFile": cp.get("InternetShortcut", "IconFile", fallback="")}
                out[a["key"]] = a
                by_name[("n", name.lower())] = a
        return list(out.values())

    @staticmethod
    def _url_roots():
        roots = []
        for env, sub in (("APPDATA", r"Microsoft\Windows\Start Menu\Programs"),
                         ("ProgramData", r"Microsoft\Windows\Start Menu\Programs"),
                         ("USERPROFILE", "Desktop"), ("PUBLIC", "Desktop")):
            base = os.environ.get(env)
            if base and (Path(base) / sub).is_dir():
                roots.append(Path(base) / sub)
        return roots

    def _icons(self):
        """Gera os ícones que faltam (em segundo plano)."""
        jobs = []
        with self.lock:
            apps = list(self.apps.values())
        for a in apps:
            out = self.icons / f"{a['key']}.png"
            if out.exists() and out.stat().st_size > 200:
                continue
            if a["kind"] in ("chrome", "edge", "brave"):
                png = _pwa_icon(a["kind"], a.get("profile"), a.get("appId"))
                if png:
                    try:
                        shutil.copyfile(png, out)
                        continue
                    except OSError:
                        pass
            if self.demo:
                continue
            src = f"shell:AppsFolder\\{a['aumid']}" if a.get("aumid") else (a.get("lnk") or "")
            alt = a.get("iconFile") or a.get("target") or a.get("lnk") or ""
            if a.get("url") and a.get("iconFile"):
                src = a["iconFile"]
            if src:
                jobs.append({"src": src, "out": str(out), "alt": alt.split(",")[0] if alt else ""})
        if jobs:
            with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as tf:
                json.dump(jobs, tf, ensure_ascii=False)
                name = tf.name
            try:
                run_ps(ICON_PS, {"SPECTRA_ICON_JOBS": name}, timeout=300)
            except Exception as exc:  # noqa: BLE001
                self.error = f"Alguns ícones não puderam ser lidos ({exc})"[:300]
            finally:
                try:
                    os.unlink(name)
                except OSError:
                    pass
        self.icon_rev = int(time.time())

    def icon_file(self, key):
        p = self.icons / f"{key}.png"
        return p if p.exists() else None

    # --- abrir ---------------------------------------------------------------
    def launch(self, app):
        if self.demo:
            return f"(demonstração) abriria {app['name']}"
        if app.get("lnk") and os.path.exists(app["lnk"]):
            os.startfile(app["lnk"])  # noqa: S606  (atalho do Menu Iniciar)
        elif app.get("aumid"):
            subprocess.Popen(["explorer.exe", f"shell:AppsFolder\\{app['aumid']}"], creationflags=NO_WINDOW)
        elif app.get("target") and os.path.exists(app["target"]):
            subprocess.Popen([app["target"]] + ([app["args"]] if app.get("args") else []), close_fds=True)
        else:
            raise FileNotFoundError("O atalho deste app não existe mais. Atualize a lista de apps.")
        return "ok"

    # --- exemplo fora do Windows ----------------------------------------------
    def _demo_apps(self):
        names = [("Sintonia", "sintonia"), ("YouTube", "chrome"), ("Netflix", "chrome"), ("Spotify", "store"),
                 ("Globoplay", "chrome"), ("Crunchyroll", "chrome"), ("Prime Video", "store"), ("Disney+", "edge"),
                 ("Google Chrome", "win"), ("Microsoft Edge", "win"), ("Explorador de Arquivos", "win"),
                 ("Excel", "win"), ("Word", "win"), ("PowerPoint", "win"), ("Outlook", "win"), ("Teams", "store"),
                 ("WhatsApp", "store"), ("Calculadora", "store"), ("Configurações", "store"), ("Steam", "win"),
                 ("Xbox", "store"), ("Minecraft Launcher", "store"), ("Gmail", "chrome"), ("Google Agenda", "chrome"),
                 ("ChatGPT", "chrome"), ("Claude", "win"), ("Bloco de notas", "store"), ("Android Studio", "win"),
                 ("VLC media player", "win"), ("Fotos", "store"), ("Paint", "store"), ("Counter-Strike 2", "win")]
        out = []
        for n, k in names:
            out.append({"key": _key("demo", n), "name": n, "kind": k, "lnk": "", "target": "", "args": "", "appId": "",
                        "profile": "", "aumid": "", "folder": ""})
        return out
