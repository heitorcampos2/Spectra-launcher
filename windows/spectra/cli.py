"""Linha de comando: python -m spectra [--janela] [--porta 8790] [--parar]."""

import argparse
import json
import os
import signal
import socket
import sys
import threading
import time
import urllib.request

from . import VERSION
from .config import data_dir


def _pidfile(dados):
    return data_dir(dados) / "spectra.pid"


def _running(port):
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/api/state", timeout=3) as r:
            return "version" in json.loads(r.read().decode())
    except Exception:  # noqa: BLE001
        return False


def _quit(port):
    try:
        req = urllib.request.Request(f"http://127.0.0.1:{port}/api/quit", data=b"{}", method="POST",
                                     headers={"Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=4).read()
    except Exception:  # noqa: BLE001
        return False
    for _ in range(30):
        with socket.socket() as s:
            s.settimeout(0.3)
            if s.connect_ex(("127.0.0.1", port)) != 0:
                return True
        time.sleep(0.2)
    return True


def _fullscreen(dados):
    try:
        with open(data_dir(dados) / "settings.json", encoding="utf-8") as f:
            return bool(json.load(f).get("general", {}).get("fullscreen"))
    except (OSError, ValueError):
        return False


def main(argv=None):
    p = argparse.ArgumentParser(prog="spectra", description="Spectra: tela inicial para o PC.")
    p.add_argument("--porta", type=int, default=int(os.environ.get("SPECTRA_PORTA", 8790)))
    p.add_argument("--dados", default=None, help="pasta das configurações")
    p.add_argument("--janela", action="store_true", help="abre em janela própria (Chrome/Edge)")
    p.add_argument("--abrir", action="store_true", help="abre no navegador padrão")
    p.add_argument("--tela-cheia", action="store_true", help="abre em tela cheia")
    p.add_argument("--parar", action="store_true", help="fecha o Spectra que está rodando")
    p.add_argument("--versao", action="version", version=f"Spectra para Windows {VERSION}")
    a = p.parse_args(argv)

    if sys.stdout is None or sys.stderr is None:      # pythonw: sem console
        log = open(data_dir(a.dados) / "spectra.log", "a", encoding="utf-8", buffering=1)
        sys.stdout = sys.stdout or log
        sys.stderr = sys.stderr or log

    if a.parar:
        print("Spectra fechado." if _quit(a.porta) else "O Spectra não estava aberto.")
        return

    from .server import App, make_server, open_app_window

    url = f"http://127.0.0.1:{a.porta}"
    full = a.tela_cheia or _fullscreen(a.dados)
    tries = 40 if os.environ.get("SPECTRA_REINICIO") else 1
    httpd, err, app = None, None, None
    for _ in range(tries):
        try:
            if _running(a.porta) and not os.environ.get("SPECTRA_REINICIO"):
                break
            app = app or App(a.dados, a.porta)
            httpd = make_server(app, "127.0.0.1", a.porta)
            break
        except OSError as exc:
            err = exc
            time.sleep(0.5)
    if httpd is None:
        if _running(a.porta):
            if a.janela or a.abrir:
                open_app_window(url, fullscreen=full) if a.janela else __import__("webbrowser").open(url)
            return
        print(f"Não foi possível iniciar o Spectra na porta {a.porta}: {err}")
        sys.exit(1)

    print(f"Spectra {VERSION} rodando em {url}\nPara fechar: Ctrl+C", flush=True)
    try:
        _pidfile(a.dados).write_text(str(os.getpid()))
    except OSError:
        pass
    if a.janela:
        threading.Timer(0.6, lambda: open_app_window(url, fullscreen=full)).start()
    elif a.abrir:
        threading.Timer(0.6, lambda: __import__("webbrowser").open(url)).start()

    def _term(*_):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, _term)
    try:
        httpd.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        print("\nSpectra encerrado.")
    finally:
        httpd.server_close()
