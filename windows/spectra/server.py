"""Servidor local do Spectra para Windows: API JSON e arquivos da interface."""

import base64
import gzip
import json
import mimetypes
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import traceback
import urllib.parse
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from . import VERSION, sintonia, winapps
from . import update as updmod
from .config import DEFAULT_SECTIONS, DEFAULT_SETTINGS, data_dir, merge_defaults, norm_section
from .store import JsonStore

WEB_DIR = Path(__file__).parent / "web"
BUILTIN_WALLS = {"paisagem": WEB_DIR / "wallpapers" / "paisagem.jpg"}
IMG_EXT = (".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")
VID_EXT = (".mp4", ".webm", ".mkv", ".mov")
NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)
DETACHED = getattr(subprocess, "DETACHED_PROCESS", 0) | getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)


class ApiError(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


def browser_paths():
    """Chrome e Edge instalados (para abrir janelas no modo aplicativo)."""
    if os.name != "nt":
        return [p for p in (shutil.which(x) for x in ("google-chrome", "chromium", "chromium-browser", "microsoft-edge")) if p]
    env = os.environ
    bases = [env.get("PROGRAMFILES", r"C:\Program Files"), env.get("PROGRAMFILES(X86)", r"C:\Program Files (x86)"),
             env.get("LOCALAPPDATA", "")]
    out = []
    for rel in (r"Google\Chrome\Application\chrome.exe", r"Microsoft\Edge\Application\msedge.exe"):
        for b in bases:
            if b and os.path.isfile(os.path.join(b, rel)):
                out.append(os.path.join(b, rel))
                break
    return out


def open_app_window(url, fullscreen=False, maximized=True):
    """Abre `url` numa janela própria do Chrome/Edge (sem barra do navegador)."""
    for exe in browser_paths():
        try:
            args = [exe, f"--app={url}"]
            if fullscreen:
                args.append("--start-fullscreen")
            elif maximized:
                args.append("--start-maximized")
            subprocess.Popen(args, close_fds=True, creationflags=DETACHED if os.name == "nt" else 0,
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return True
        except OSError:
            continue
    import webbrowser
    webbrowser.open(url)
    return False


class App:
    def __init__(self, data=None, port=8790):
        self.dir = data_dir(data)
        self.port = port
        self.settings_store = JsonStore(self.dir / "settings.json", DEFAULT_SETTINGS)
        self.settings_store.data = merge_defaults(self.settings_store.data, DEFAULT_SETTINGS)
        self._fix_channels()
        self.sections = JsonStore(self.dir / "sections.json", {"items": DEFAULT_SECTIONS})
        self.custom = JsonStore(self.dir / "custom.json", {"items": {}})
        self.recent = JsonStore(self.dir / "recent.json", {"items": []})
        self.walls = self.dir / "wallpapers"
        self.walls.mkdir(exist_ok=True)
        (self.dir / "icones-proprios").mkdir(exist_ok=True)
        self.index = winapps.AppIndex(self.dir, demo=os.environ.get("SPECTRA_DEMO") == "1")
        self.index.scan_async(force=True)
        if not self.index.demo or os.environ.get("SPECTRA_WATCH") == "1":
            self.index.start_watch()
        self._net = (0, None, "")

    def _fix_channels(self):
        """Garante que todo canal conhecido esteja na lista (versões novas podem trazer canais novos)."""
        d = self.settings_store.data
        have = {c.get("id") for c in d.get("channels") or []}
        for c in DEFAULT_SETTINGS["channels"]:
            if c["id"] not in have:
                d.setdefault("channels", []).append(dict(c))

    # --- estado ----------------------------------------------------------------
    def settings(self):
        return self.settings_store.data

    def save_settings(self, patch):
        def apply(data):
            merged = merge_defaults(patch, data)
            data.clear()
            data.update(merge_defaults(merged, DEFAULT_SETTINGS))
            return data
        out = self.settings_store.update(apply)
        if "general" in patch and "startWithWindows" in (patch.get("general") or {}):
            try:
                self.set_startup(bool(patch["general"]["startWithWindows"]))
            except Exception as exc:  # noqa: BLE001
                raise ApiError(f"Não consegui mudar a inicialização com o Windows ({exc})") from exc
        return out

    def state(self):
        return {"version": VERSION, "settings": self.settings(), "sections": self.get_sections(),
                "custom": self.custom.data["items"], "platform": "windows" if os.name == "nt" else sys.platform,
                "demo": self.index.demo, "dataDir": str(self.dir), "device": socket.gethostname()}

    def get_sections(self):
        return [norm_section(s) for s in self.sections.data["items"]]

    # --- apps ------------------------------------------------------------------
    def all_apps(self):
        apps = []
        for a in self.index.list():
            apps.append({"key": a["key"], "name": a["name"], "kind": a["kind"], "tags": a.get("tags", []),
                         "folder": a.get("folder", ""), "icon": bool(self.icon(a["key"]))})
        for key, c in self.custom.data["items"].items():
            apps.append({"key": key, "name": c["name"], "kind": "custom", "type": c.get("type"), "tags": c.get("tags", []),
                         "path": c.get("path") or c.get("url") or "", "icon": bool(self.icon(key))})
        self._seed_favorites(apps)
        return apps

    def _seed_favorites(self, apps):
        """Na primeira leitura dos apps, monta os Favoritos com o básico (como o Android faz com a seção TV)."""
        if self.settings().get("_favSeeded") or not self.index.scanned_at or not apps:
            return
        want = [r"^sintonia$", r"^(google chrome|chrome)$", r"^microsoft edge$", r"^(explorador de arquivos|file explorer)$",
                r"^youtube$", r"^(configurações|settings)$", r"^(microsoft store|store)$", r"^spotify"]
        picks = []
        for rx in want:
            for a in apps:
                if re.search(rx, a["name"], re.I) and a["key"] not in picks:
                    picks.append(a["key"])
                    break
        for sec in self.sections.data["items"]:
            if sec.get("id") == "fav" and not sec.get("apps"):
                sec["apps"] = picks
                sec["order"] = list(picks)
        self.sections.save()
        self.settings_store.data["_favSeeded"] = True
        self.settings_store.save()

    def launch(self, key):
        c = self.custom.data["items"].get(key)
        if c:
            msg = self._launch_custom(c)
        else:
            a = self.index.get(key)
            if not a:
                raise ApiError("App não encontrado. Atualize a lista de apps.", 404)
            try:
                msg = self.index.launch(a)
            except FileNotFoundError as exc:
                raise ApiError(str(exc), 404) from exc
            except OSError as exc:
                raise ApiError(f"O Windows não abriu este app ({exc})") from exc

        def apply(d):
            d["items"] = [{"key": key, "t": int(time.time())}] + [x for x in d["items"] if x["key"] != key][:39]
        self.recent.update(apply)
        return msg

    def _launch_custom(self, c):
        if self.index.demo:
            return f"(demonstração) abriria {c['name']}"
        if c.get("type") == "url":
            if c.get("window", True):
                open_app_window(c["url"], maximized=True)
            else:
                import webbrowser
                webbrowser.open(c["url"])
            return "ok"
        path = c.get("path") or ""
        if not os.path.exists(path):
            raise ApiError("O arquivo deste atalho não existe mais", 404)
        if c.get("args"):
            subprocess.Popen([path] + c["args"].split(), cwd=os.path.dirname(path), close_fds=True)
        else:
            os.startfile(path)  # noqa: S606
        return "ok"

    def add_custom(self, body):
        name = (body.get("name") or "").strip()
        typ = body.get("type")
        if typ not in ("exe", "url"):
            raise ApiError("Tipo inválido")
        item = {"name": name, "type": typ, "tags": body.get("tags") or []}
        if typ == "url":
            url = (body.get("url") or "").strip()
            if not re.match(r"^https?://", url, re.I):
                url = "https://" + url
            item["url"] = url
            item["window"] = bool(body.get("window", True))
            if not name:
                item["name"] = urllib.parse.urlparse(url).hostname or url
        else:
            path = (body.get("path") or "").strip().strip('"')
            if not path:
                raise ApiError("Escolha o programa")
            if not self.index.demo and not os.path.exists(path):
                raise ApiError("Esse arquivo não existe")
            item["path"] = path
            item["args"] = (body.get("args") or "").strip()
            if not name:
                item["name"] = Path(path).stem
        key = body.get("key") or ("c" + uuid.uuid4().hex[:12])

        def apply(d):
            d["items"][key] = item
        self.custom.update(apply)
        if typ == "url":
            threading.Thread(target=self._fetch_favicon, args=(key, item["url"]), daemon=True).start()
        elif not self.index.demo:
            threading.Thread(target=self._exe_icon, args=(key, item["path"]), daemon=True).start()
        return {"key": key, **item}

    def _exe_icon(self, key, path):
        out = self.dir / "icones-proprios" / f"{key}.png"
        jobs = [{"src": path, "out": str(out), "alt": path}]
        tmp = self.dir / f"icon-{key}.json"
        tmp.write_text(json.dumps(jobs), encoding="utf-8")
        try:
            winapps.run_ps(winapps.ICON_PS, {"SPECTRA_ICON_JOBS": str(tmp)}, timeout=120)
        except Exception:  # noqa: BLE001
            pass
        finally:
            tmp.unlink(missing_ok=True)

    def _fetch_favicon(self, key, url):
        host = urllib.parse.urlparse(url).hostname or ""
        if not host:
            return
        try:
            req = urllib.request.Request(f"https://www.google.com/s2/favicons?domain={host}&sz=256",
                                         headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=10) as r:
                data = r.read()
            if len(data) > 300:
                (self.dir / "icones-proprios" / f"{key}.png").write_bytes(data)
        except OSError:
            pass

    def icon(self, key):
        own = self.dir / "icones-proprios" / f"{key}.png"
        if own.exists():
            return own
        return self.index.icon_file(key)

    # --- canais ----------------------------------------------------------------
    def channels(self):
        s = self.settings()
        data = sintonia.channels(s.get("sintonia"), int(s["perf"].get("perRow") or 30))
        rows = data["rows"]
        names = {a["key"]: a for a in self.all_apps()}
        recent = []
        for r in self.recent.data["items"]:
            a = names.get(r["key"])
            if a:
                recent.append({"key": a["key"], "title": a["name"], "desc": "Aberto " + _ago(r["t"]), "app": a["name"],
                               "launch": a["key"], "icon": f"/api/icon/{a['key']}"})
        rows["recent-apps"] = recent[:int(s["perf"].get("perRow") or 30)]
        return {"sintonia": {"ok": data["ok"], "error": data["error"],
                             "installed": any(a["kind"] == "sintonia" for a in self.index.list())},
                "rows": rows}

    def sintonia_app(self):
        for a in self.index.list():
            if a["kind"] == "sintonia":
                return a
        return None

    def open_sintonia(self, route=""):
        """Abre o Sintonia (numa página, se `route` vier). Liga o servidor dele se estiver fechado."""
        cfg = self.settings().get("sintonia") or {}
        base = (cfg.get("url") or "http://127.0.0.1:8787").rstrip("/")
        if self.index.demo:
            return "(demonstração) abriria o Sintonia " + route
        if not sintonia.running(cfg):
            a = self.sintonia_app()
            started = False
            if a and a.get("target") and "pythonw" in a["target"].lower() and os.path.exists(a["target"]):
                cwd = Path(os.environ.get("LOCALAPPDATA", "")) / "Sintonia" / "app"
                subprocess.Popen([a["target"], "-m", "sintonia", "--host", "127.0.0.1"], cwd=str(cwd) if cwd.is_dir() else None,
                                 creationflags=DETACHED | NO_WINDOW, close_fds=True)
                started = True
            elif a:
                self.index.launch(a)        # abre o Sintonia pelo atalho dele (já abre a janela)
                return "ok"
            if not started:
                raise ApiError("O Sintonia não está instalado neste PC. Veja Configurações › Atualizações.", 404)
            for _ in range(40):
                time.sleep(0.4)
                if sintonia.running(cfg):
                    break
        open_app_window(f"{base}/{route.lstrip('/')}" if route else base, maximized=True)
        return "ok"

    # --- sistema ---------------------------------------------------------------
    def system(self):
        out = {"time": int(time.time()), "battery": None, "online": None, "wifi": ""}
        if os.name == "nt":
            try:
                import ctypes

                class SPS(ctypes.Structure):
                    _fields_ = [("ACLineStatus", ctypes.c_byte), ("BatteryFlag", ctypes.c_byte),
                                ("BatteryLifePercent", ctypes.c_byte), ("SystemStatusFlag", ctypes.c_byte),
                                ("BatteryLifeTime", ctypes.c_ulong), ("BatteryFullLifeTime", ctypes.c_ulong)]
                sps = SPS()
                if ctypes.windll.kernel32.GetSystemPowerStatus(ctypes.byref(sps)):
                    pct = sps.BatteryLifePercent & 0xFF
                    if (sps.BatteryFlag & 0xFF) != 128 and pct <= 100:
                        out["battery"] = {"pct": pct, "charging": sps.ACLineStatus == 1}
            except Exception:  # noqa: BLE001
                pass
        t, online, wifi = self._net
        if time.time() - t > 30:
            online = _online()
            wifi = _wifi_name() if os.name == "nt" else ""
            self._net = (time.time(), online, wifi)
        out["online"], out["wifi"] = online, wifi
        return out

    def set_startup(self, on):
        if os.name != "nt":
            return
        startup = Path(os.environ["APPDATA"]) / r"Microsoft\Windows\Start Menu\Programs\Startup" / "Spectra.lnk"
        if not on:
            startup.unlink(missing_ok=True)
            return
        exe = Path(sys.executable)
        pyw = exe.with_name("pythonw.exe") if exe.with_name("pythonw.exe").exists() else exe
        app_dir = Path(__file__).resolve().parent.parent
        ico = app_dir.parent / "spectra.ico"
        ps = f"""
$s = New-Object -ComObject WScript.Shell
$l = $s.CreateShortcut('{_ps_quote(startup)}')
$l.TargetPath = '{_ps_quote(pyw)}'
$l.Arguments = '-m spectra --janela'
$l.WorkingDirectory = '{_ps_quote(app_dir)}'
$l.IconLocation = '{_ps_quote(ico)}'
$l.Description = 'Spectra - tela inicial'
$l.Save()
"""
        winapps.run_ps(ps, timeout=30)

    # --- wallpaper -------------------------------------------------------------
    def wallpaper_file(self, name):
        if name in BUILTIN_WALLS:
            return BUILTIN_WALLS[name]
        if name == "windows":
            p = Path(os.environ.get("APPDATA", "")) / r"Microsoft\Windows\Themes\TranscodedWallpaper"
            return p if p.exists() else None
        if name.startswith("file:"):
            p = self.walls / Path(name[5:]).name
            return p if p.exists() else None
        return None

    def wallpapers(self):
        items = [{"id": "paisagem", "name": "Paisagem", "type": "image"}]
        if self.wallpaper_file("windows"):
            items.append({"id": "windows", "name": "Papel de parede do Windows", "type": "image"})
        files = sorted(self.walls.iterdir(), key=lambda p: p.stat().st_mtime, reverse=True)
        for f in files:
            if f.suffix.lower() in IMG_EXT + VID_EXT:
                items.append({"id": f"file:{f.name}", "name": f.stem, "type": "video" if f.suffix.lower() in VID_EXT else "image"})
        return items

    def save_wallpaper(self, raw, name):
        ext = Path(name or "").suffix.lower()
        if ext not in IMG_EXT + VID_EXT:
            raise ApiError("Use uma imagem (JPG, PNG, WebP, GIF) ou um vídeo (MP4, WebM)")
        fname = re.sub(r"[^\w.-]+", "_", Path(name).stem)[:60] + "-" + uuid.uuid4().hex[:6] + ext
        (self.walls / fname).write_bytes(raw)
        return {"id": f"file:{fname}", "type": "video" if ext in VID_EXT else "image"}

    def wallpaper_from_url(self, url):
        if not re.match(r"^https?://", url or "", re.I):
            raise ApiError("Endereço inválido")
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                ctype = r.headers.get("Content-Type", "")
                data = r.read(60 * 1024 * 1024)
        except OSError as exc:
            raise ApiError(f"Não consegui baixar a imagem ({exc})") from exc
        ext = mimetypes.guess_extension(ctype.split(";")[0].strip()) or Path(urllib.parse.urlparse(url).path).suffix or ".jpg"
        if ext == ".jpe":
            ext = ".jpg"
        return self.save_wallpaper(data, "online" + ext)

    # --- backup ----------------------------------------------------------------
    def backup(self):
        return {"spectra": VERSION, "at": int(time.time()), "settings": self.settings(),
                "sections": self.sections.data["items"], "custom": self.custom.data["items"]}

    def restore(self, data):
        if not isinstance(data, dict) or "settings" not in data:
            raise ApiError("Esse arquivo não é um backup do Spectra")
        self.settings_store.data = merge_defaults(data["settings"], DEFAULT_SETTINGS)
        self.settings_store.save()
        if isinstance(data.get("sections"), list):
            self.sections.data["items"] = data["sections"]
            self.sections.save()
        if isinstance(data.get("custom"), dict):
            self.custom.data["items"] = data["custom"]
            self.custom.save()
        return self.state()


def _ago(ts):
    d = max(0, int(time.time()) - int(ts or 0))
    if d < 60:
        return "agora"
    if d < 3600:
        return f"há {d // 60} min"
    if d < 86400:
        return f"há {d // 3600} h"
    return f"há {d // 86400} d"


def _online():
    for host in (("1.1.1.1", 53), ("8.8.8.8", 53)):
        try:
            with socket.create_connection(host, timeout=1.5):
                return True
        except OSError:
            continue
    return False


def _wifi_name():
    try:
        r = subprocess.run(["netsh", "wlan", "show", "interfaces"], capture_output=True, timeout=5, creationflags=NO_WINDOW)
        txt = r.stdout.decode("cp850", "replace")
        m = re.search(r"^\s*SSID\s*:\s*(.+)$", txt, re.M)
        return m.group(1).strip() if m else ""
    except (OSError, subprocess.SubprocessError):
        return ""


def _ps_quote(p):
    return str(p).replace("'", "''")


# =============================================================================== rotas
ROUTES = []


def route(method, pattern):
    def deco(fn):
        ROUTES.append((method, re.compile(f"^{pattern}$"), fn))
        return fn
    return deco


@route("GET", r"/api/state")
def r_state(app, h, m, q, body):
    return app.state()


@route("POST", r"/api/settings")
def r_settings(app, h, m, q, body):
    return app.save_settings(body or {})


@route("POST", r"/api/sections")
def r_sections(app, h, m, q, body):
    items = body.get("items")
    if not isinstance(items, list):
        raise ApiError("Lista de seções inválida")
    app.sections.data["items"] = [norm_section(s) for s in items]
    app.sections.save()
    return app.get_sections()


@route("POST", r"/api/sections/reset")
def r_sections_reset(app, h, m, q, body):
    app.sections.data["items"] = DEFAULT_SECTIONS
    app.sections.save()
    return app.get_sections()


@route("GET", r"/api/apps")
def r_apps(app, h, m, q, body):
    if q.get("refresh") == "1":
        app.index.scan_async(force=True)
    return {"apps": app.all_apps(), "status": app.index.status()}


@route("GET", r"/api/icon/([\w-]+)")
def r_icon(app, h, m, q, body):
    p = app.icon(m.group(1))
    if not p:
        raise ApiError("sem ícone", 404)
    h.send_bytes(p.read_bytes(), "image/png", "max-age=3600")


@route("POST", r"/api/icon/([\w-]+)")
def r_icon_set(app, h, m, q, body):
    raw = base64.b64decode((body.get("data") or "").split(",")[-1] or b"")
    if len(raw) < 100:
        raise ApiError("Imagem inválida")
    (app.dir / "icones-proprios" / f"{m.group(1)}.png").write_bytes(raw)
    return {"ok": True}


@route("DELETE", r"/api/icon/([\w-]+)")
def r_icon_del(app, h, m, q, body):
    (app.dir / "icones-proprios" / f"{m.group(1)}.png").unlink(missing_ok=True)
    return {"ok": True}


@route("POST", r"/api/launch")
def r_launch(app, h, m, q, body):
    key = body.get("key") or ""
    a = app.index.get(key)
    if a and a["kind"] == "sintonia":
        res = app.open_sintonia("")

        def apply(d):
            d["items"] = [{"key": key, "t": int(time.time())}] + [x for x in d["items"] if x["key"] != key][:39]
        app.recent.update(apply)
        return {"ok": True, "message": res}
    return {"ok": True, "message": app.launch(key)}


@route("POST", r"/api/custom")
def r_custom(app, h, m, q, body):
    return app.add_custom(body)


@route("DELETE", r"/api/custom/([\w-]+)")
def r_custom_del(app, h, m, q, body):
    key = m.group(1)

    def apply(d):
        d["items"].pop(key, None)
    app.custom.update(apply)
    return {"ok": True}


@route("POST", r"/api/pick")
def r_pick(app, h, m, q, body):
    kind = body.get("kind")
    filt = {"exe": "Programas e atalhos (*.exe;*.lnk;*.bat;*.url)|*.exe;*.lnk;*.bat;*.cmd;*.url|Todos os arquivos (*.*)|*.*",
            "image": "Imagens (*.png;*.jpg;*.ico)|*.png;*.jpg;*.jpeg;*.ico;*.webp|Todos os arquivos (*.*)|*.*"}.get(kind)
    if not filt:
        raise ApiError("Tipo inválido")
    try:
        path = winapps.pick_file("Escolha o programa" if kind == "exe" else "Escolha a imagem", filt)
    except Exception as exc:  # noqa: BLE001
        raise ApiError(f"Não consegui abrir a janela de arquivos ({exc})") from exc
    return {"path": path}


@route("GET", r"/api/channels")
def r_channels(app, h, m, q, body):
    return app.channels()


@route("POST", r"/api/sintonia/open")
def r_sintonia_open(app, h, m, q, body):
    route_ = body.get("route") or ""
    if route_ and not re.match(r"^#/[\w/%.\-]*$", route_):
        raise ApiError("Endereço inválido")
    return {"ok": True, "message": app.open_sintonia(route_)}


@route("POST", r"/api/sintonia/install")
def r_sintonia_install(app, h, m, q, body):
    try:
        ver = updmod.install_sintonia()
    except updmod.UpdateError as exc:
        raise ApiError(str(exc)) from exc
    return {"ok": True, "version": ver}


@route("GET", r"/api/system")
def r_system(app, h, m, q, body):
    return app.system()


@route("GET", r"/api/wallpapers")
def r_walls(app, h, m, q, body):
    return {"items": app.wallpapers()}


@route("GET", r"/api/wallpaper/(.+)")
def r_wall(app, h, m, q, body):
    p = app.wallpaper_file(urllib.parse.unquote(m.group(1)))
    if not p:
        raise ApiError("Wallpaper não encontrado", 404)
    ctype = mimetypes.guess_type(p.name)[0] or "image/jpeg"
    if p.suffix.lower() in VID_EXT:
        return h.send_file_range(p, ctype)
    h.send_bytes(p.read_bytes(), ctype, "max-age=600")


@route("POST", r"/api/wallpaper/upload")
def r_wall_upload(app, h, m, q, body):
    return app.save_wallpaper(h.raw_body, q.get("name") or "imagem.jpg")


@route("POST", r"/api/wallpaper/url")
def r_wall_url(app, h, m, q, body):
    return app.wallpaper_from_url((body.get("url") or "").strip())


@route("DELETE", r"/api/wallpaper/(.+)")
def r_wall_del(app, h, m, q, body):
    name = urllib.parse.unquote(m.group(1))
    if name.startswith("file:"):
        (app.walls / Path(name[5:]).name).unlink(missing_ok=True)
    return {"items": app.wallpapers()}


@route("GET", r"/api/backup")
def r_backup(app, h, m, q, body):
    return app.backup()


@route("POST", r"/api/restore")
def r_restore(app, h, m, q, body):
    return app.restore(body)


@route("GET", r"/api/update/check")
def r_upd_check(app, h, m, q, body):
    return updmod.check(force=q.get("force") == "1")


@route("POST", r"/api/update/apply")
def r_upd_apply(app, h, m, q, body):
    try:
        ver = updmod.apply()
    except updmod.UpdateError as exc:
        raise ApiError(str(exc)) from exc
    updmod.restart_soon()
    return {"ok": True, "version": ver}


@route("POST", r"/api/open-url")
def r_open_url(app, h, m, q, body):
    url = body.get("url") or ""
    if not re.match(r"^https?://", url):
        raise ApiError("Endereço inválido")
    import webbrowser
    webbrowser.open(url)
    return {"ok": True}


@route("POST", r"/api/system-settings")
def r_sys_settings(app, h, m, q, body):
    if os.name == "nt":
        target = {"wifi": "ms-settings:network-wifi", "battery": "ms-settings:batterysaver", "main": "ms-settings:",
                  "notifications": "ms-settings:notifications"}.get(body.get("page") or "main", "ms-settings:")
        os.startfile(target)  # noqa: S606
    return {"ok": True}


@route("POST", r"/api/quit")
def r_quit(app, h, m, q, body):
    threading.Timer(0.3, lambda: os._exit(0)).start()
    return {"ok": True}


# =============================================================================== HTTP
class Handler(BaseHTTPRequestHandler):
    server_version = "Spectra/" + VERSION
    protocol_version = "HTTP/1.1"
    app = None
    raw_body = b""

    def log_message(self, fmt, *args):
        pass

    def send_json(self, data, status=200):
        raw = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        gz = "gzip" in self.headers.get("Accept-Encoding", "") and len(raw) > 2048
        if gz:
            raw = gzip.compress(raw, 5)
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        if gz:
            self.send_header("Content-Encoding", "gzip")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def send_bytes(self, data, ctype, cache="no-cache", status=200):
        gz = ctype.startswith(("text/", "application/javascript", "application/json", "image/svg")) \
            and "gzip" in self.headers.get("Accept-Encoding", "") and len(data) > 1024
        if gz:
            data = gzip.compress(data, 6)
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", cache)
        if gz:
            self.send_header("Content-Encoding", "gzip")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def send_file_range(self, path, ctype):
        size = os.path.getsize(path)
        rng = self.headers.get("Range")
        start, end, status = 0, size - 1, 200
        mm = re.match(r"bytes=(\d*)-(\d*)", rng or "")
        if mm:
            if mm.group(1):
                start = int(mm.group(1))
                if mm.group(2):
                    end = min(int(mm.group(2)), size - 1)
            elif mm.group(2):
                start = max(0, size - int(mm.group(2)))
            status = 206
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        with open(path, "rb") as f:
            f.seek(start)
            left = end - start + 1
            while left > 0:
                chunk = f.read(min(256 * 1024, left))
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except OSError:
                    return
                left -= len(chunk)

    def _local_only(self):
        # o Spectra controla apps deste PC: só aceita pedidos do próprio computador
        return self.client_address[0] in ("127.0.0.1", "::1", "::ffff:127.0.0.1")

    def handle_any(self, method):
        if not self._local_only():
            self.send_json({"error": "Acesso só por este computador"}, 403)
            return
        parsed = urllib.parse.urlsplit(self.path)
        path = parsed.path
        q = {k: v[-1] for k, v in urllib.parse.parse_qs(parsed.query).items()}
        if path.startswith("/api/"):
            # pedidos de outros sites abertos no navegador não podem mexer no Spectra
            origin = self.headers.get("Origin")
            if method != "GET" and origin and urllib.parse.urlsplit(origin).hostname not in ("127.0.0.1", "localhost"):
                self.send_json({"error": "Origem não permitida"}, 403)
                return
            length = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(length) if length else b""
            self.raw_body = raw
            body = {}
            if raw and "json" in (self.headers.get("Content-Type") or ""):
                try:
                    body = json.loads(raw.decode("utf-8"))
                except ValueError:
                    self.send_json({"error": "JSON inválido"}, 400)
                    return
            for meth, rx, fn in ROUTES:
                if meth != method:
                    continue
                m = rx.match(path)
                if not m:
                    continue
                try:
                    res = fn(self.app, self, m, q, body)
                    if res is not None:
                        self.send_json(res)
                except ApiError as exc:
                    self.send_json({"error": str(exc)}, exc.status)
                except Exception as exc:  # noqa: BLE001
                    traceback.print_exc()
                    self.send_json({"error": f"Erro inesperado: {exc}"}, 500)
                return
            self.send_json({"error": "Rota não encontrada"}, 404)
            return
        self.serve_static(path)

    def serve_static(self, path):
        rel = path.lstrip("/") or "index.html"
        f = (WEB_DIR / rel).resolve()
        if not str(f).startswith(str(WEB_DIR.resolve())) or not f.is_file():
            f = WEB_DIR / "index.html"
        ctype = mimetypes.guess_type(f.name)[0] or "application/octet-stream"
        if f.suffix == ".js":
            ctype = "application/javascript; charset=utf-8"
        elif f.suffix in (".css", ".html"):
            ctype += "; charset=utf-8"
        self.send_bytes(f.read_bytes(), ctype, "no-cache")

    def do_GET(self):
        self.handle_any("GET")

    def do_HEAD(self):
        self.handle_any("GET")

    def do_POST(self):
        self.handle_any("POST")

    def do_DELETE(self):
        self.handle_any("DELETE")


def make_server(app, host, port):
    handler = type("SpectraHandler", (Handler,), {"app": app})
    httpd = ThreadingHTTPServer((host, port), handler)
    httpd.daemon_threads = True
    return httpd
