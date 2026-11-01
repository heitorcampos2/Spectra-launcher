"""Atualização pelo GitHub (heitorcampos2/Spectra-launcher, pasta windows/).

O repositório tem windows/version.json com a versão mais nova do Spectra para Windows.
O próprio Spectra baixa o pacote, troca os arquivos (com cópia de segurança) e reabre.
Também instala o Sintonia (sugestão em Configurações › Atualizações).
"""

import io
import json
import os
import shutil
import sys
import tempfile
import threading
import time
import urllib.request
import zipfile
from pathlib import Path

from . import VERSION

UPDATE_URL = os.environ.get("SPECTRA_UPDATE_URL") or "https://raw.githubusercontent.com/heitorcampos2/Spectra-launcher/main/windows/version.json"
PAGE = "https://github.com/heitorcampos2/Spectra-launcher"
SINTONIA_JSON = "https://raw.githubusercontent.com/heitorcampos2/Sintonia/main/version.json"
PKG_DIR = Path(__file__).resolve().parent

_cache = {"at": 0, "data": None, "error": ""}
_lock = threading.Lock()


class UpdateError(Exception):
    pass


def vtuple(v):
    out = []
    for p in str(v or "0").strip().lstrip("vV").split("."):
        num = "".join(ch for ch in p if ch.isdigit())
        out.append(int(num or 0))
    while len(out) < 3:
        out.append(0)
    return tuple(out)


def platform():
    return "windows" if os.name == "nt" else "linux"


def _get(url, timeout=20):
    req = urllib.request.Request(url, headers={"User-Agent": f"Spectra-Windows/{VERSION}", "Cache-Control": "no-cache"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def check(force=False):
    """Compara com a versão publicada (guarda a resposta por 3 horas)."""
    with _lock:
        if force or not _cache["data"] or time.time() - _cache["at"] > 3 * 3600:
            try:
                data = json.loads(_get(UPDATE_URL, 15).decode("utf-8"))
                if not isinstance(data, dict) or not data.get("version"):
                    raise ValueError("version.json inválido")
                _cache.update(at=time.time(), data=data, error="")
            except Exception as exc:  # noqa: BLE001
                _cache.update(at=time.time(), error=f"Não consegui consultar o GitHub ({exc})")
        data = _cache["data"] or {}
    latest = data.get("version") or ""
    plat = platform()
    return {
        "current": VERSION, "latest": latest, "available": bool(latest) and vtuple(latest) > vtuple(VERSION),
        "notes": data.get("notes") or [], "date": data.get("date") or "", "files": data.get("files") or {},
        "page": data.get("page") or PAGE, "platform": plat,
        "canApply": plat in ("windows", "linux") and os.access(PKG_DIR, os.W_OK),
        "error": _cache["error"] if not data else "",
    }


def apply():
    """Baixa o pacote, troca os arquivos do Spectra (com cópia de segurança) e devolve a versão nova."""
    info = check(force=True)
    if not info["available"]:
        raise UpdateError("Você já está na versão mais nova")
    if not info["canApply"]:
        raise UpdateError("Sem permissão para trocar os arquivos. Baixe o pacote novo no GitHub.")
    url = info["files"].get("windows") or info["files"].get("pc")
    if not url:
        raise UpdateError("O pacote do Windows não está no GitHub ainda")
    try:
        raw = _get(url, 120)
    except Exception as exc:  # noqa: BLE001
        raise UpdateError(f"Não consegui baixar a atualização ({exc})") from exc
    with tempfile.TemporaryDirectory() as tmp:
        try:
            with zipfile.ZipFile(io.BytesIO(raw)) as z:
                z.extractall(tmp)
        except zipfile.BadZipFile as exc:
            raise UpdateError("O arquivo baixado está corrompido. Tente de novo.") from exc
        found = list(Path(tmp).glob("*/spectra/__init__.py")) + list(Path(tmp).glob("spectra/__init__.py"))
        if not found:
            raise UpdateError("O pacote baixado não tem o Spectra")
        new_pkg = found[0].parent
        new_ver = ""
        for line in found[0].read_text(encoding="utf-8").splitlines():
            if line.startswith("VERSION"):
                new_ver = line.split("=", 1)[1].strip().strip("\"'")
        if vtuple(new_ver) <= vtuple(VERSION):
            raise UpdateError("O pacote do GitHub não é mais novo que este")
        backup = PKG_DIR.with_name("spectra-anterior")
        shutil.rmtree(backup, ignore_errors=True)
        shutil.copytree(PKG_DIR, backup, ignore=shutil.ignore_patterns("__pycache__"))
        try:
            shutil.copytree(new_pkg, PKG_DIR, dirs_exist_ok=True)
        except Exception as exc:  # noqa: BLE001
            shutil.copytree(backup, PKG_DIR, dirs_exist_ok=True)   # volta como estava
            raise UpdateError(f"Não consegui trocar os arquivos ({exc})") from exc
    return new_ver


def restart_soon():
    """Reabre o servidor com o código novo (mesmos argumentos)."""
    import subprocess
    env = dict(os.environ, SPECTRA_REINICIO="1")
    args = [sys.executable, "-m", "spectra"] + [a for a in sys.argv[1:] if a not in ("--abrir", "--janela")]
    flags = 0
    if os.name == "nt":
        flags = getattr(subprocess, "DETACHED_PROCESS", 0) | getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)

    def go():
        subprocess.Popen(args, cwd=os.getcwd(), env=env, close_fds=True, creationflags=flags,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL)
        os._exit(0)
    threading.Timer(1.0, go).start()


def install_sintonia():
    """Baixa o Sintonia para Windows do GitHub e abre o instalador dele (janela do instalador visível)."""
    if os.name != "nt":
        raise UpdateError("A instalação automática do Sintonia é só no Windows")
    try:
        info = json.loads(_get(SINTONIA_JSON, 15).decode("utf-8"))
        url = (info.get("files") or {}).get("windows")
        ver = info.get("version") or ""
        if not url:
            raise UpdateError("O pacote do Sintonia para Windows não foi encontrado no GitHub")
        raw = _get(url, 180)
    except UpdateError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise UpdateError(f"Não consegui baixar o Sintonia ({exc})") from exc
    dest = Path(tempfile.gettempdir()) / f"Sintonia-{ver or 'novo'}"
    shutil.rmtree(dest, ignore_errors=True)
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            z.extractall(dest)
    except zipfile.BadZipFile as exc:
        raise UpdateError("O arquivo baixado está corrompido. Tente de novo.") from exc
    bats = list(dest.glob("*/Instalar Sintonia.bat")) + list(dest.glob("Instalar Sintonia.bat"))
    if not bats:
        raise UpdateError("O pacote baixado não tem o instalador do Sintonia")
    import subprocess
    subprocess.Popen(["cmd.exe", "/c", "start", "", str(bats[0])], cwd=str(bats[0].parent),
                     creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    return ver
