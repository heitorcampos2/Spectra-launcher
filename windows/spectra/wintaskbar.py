"""Identidade própria na barra de tarefas do Windows (ícone e "fixar" do app, não do Chrome).

A janela do app é uma janela do Chrome/Edge em modo aplicativo (--app); sem isto o Windows a agrupa
com o Chrome e, ao fixar, fixa o Chrome. Aqui damos à janela um AppUserModelID próprio, com comando,
nome e ícone de "reabrir", e gravamos o mesmo ID nos atalhos (Área de Trabalho, Menu Iniciar e o que
estiver fixado na barra). Assim a barra mostra o ícone do app e "Fixar" fixa o atalho do app.
Só Windows; em outros sistemas não faz nada. Nenhuma falha aqui impede o app de abrir.
"""
import ctypes
import os
import sys
import threading
import time
from ctypes import wintypes

IS_WIN = os.name == "nt"


class GUID(ctypes.Structure):
    _fields_ = [("Data1", wintypes.DWORD), ("Data2", wintypes.WORD), ("Data3", wintypes.WORD), ("Data4", ctypes.c_ubyte * 8)]

    def __init__(self, s):
        super().__init__()
        import uuid
        u = uuid.UUID(s)
        self.Data1, self.Data2, self.Data3 = u.fields[0], u.fields[1], u.fields[2]
        for i, b in enumerate(u.bytes[8:]):
            self.Data4[i] = b


class PROPERTYKEY(ctypes.Structure):
    _fields_ = [("fmtid", GUID), ("pid", wintypes.DWORD)]


class PROPVARIANT(ctypes.Structure):
    _fields_ = [("vt", ctypes.c_ushort), ("r1", ctypes.c_ushort), ("r2", ctypes.c_ushort), ("r3", ctypes.c_ushort),
                ("pwszVal", ctypes.c_wchar_p), ("pad", ctypes.c_void_p)]


_AUMID_FMT = "{9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3}"
PKEY_ID = PROPERTYKEY(GUID(_AUMID_FMT), 5)
PKEY_RELAUNCH_CMD = PROPERTYKEY(GUID(_AUMID_FMT), 2)
PKEY_RELAUNCH_ICON = PROPERTYKEY(GUID(_AUMID_FMT), 3)
PKEY_RELAUNCH_NAME = PROPERTYKEY(GUID(_AUMID_FMT), 4)
IID_IPropertyStore = GUID("{886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99}")
VT_LPWSTR = 31
GPS_READWRITE = 2


def _vcall(ptr, index, restype, *objs):
    """Chama o método [index] da vtable de um objeto COM; cada argumento é passado por endereço."""
    vtbl = ctypes.cast(ptr, ctypes.POINTER(ctypes.POINTER(ctypes.c_void_p))).contents
    fn = ctypes.WINFUNCTYPE(restype, ctypes.c_void_p, *([ctypes.c_void_p] * len(objs)))(vtbl[index])
    return fn(ptr, *[ctypes.addressof(o) for o in objs])


def _set_props(store, props):
    """props: lista de (PROPERTYKEY, texto). Grava cada uma no IPropertyStore e faz Commit."""
    keep = []
    for key, val in props:
        pv = PROPVARIANT()
        pv.vt = VT_LPWSTR
        pv.pwszVal = val
        keep.append(pv)
        _vcall(store, 6, ctypes.HRESULT, key, pv)  # SetValue
    _vcall(store, 7, ctypes.HRESULT)  # Commit
    _vcall(store, 2, ctypes.c_ulong)  # Release


def _get_aumid(store):
    pv = PROPVARIANT()
    try:
        _vcall(store, 5, ctypes.HRESULT, PKEY_ID, pv)  # GetValue
        return pv.pwszVal if pv.vt == VT_LPWSTR else None
    except OSError:
        return None


def brand_window(hwnd, app_id, relaunch_cmd, icon, name):
    store = ctypes.c_void_p()
    ctypes.windll.shell32.SHGetPropertyStoreForWindow(wintypes.HWND(hwnd), ctypes.byref(IID_IPropertyStore), ctypes.byref(store))
    if not store.value:
        return False
    # comando/ícone/nome de reabrir primeiro e o ID por último (ordem pedida pela documentação do Windows)
    _set_props(store.value, [(PKEY_RELAUNCH_CMD, relaunch_cmd), (PKEY_RELAUNCH_ICON, icon + ",0"),
                             (PKEY_RELAUNCH_NAME, name), (PKEY_ID, app_id)])
    return True


def window_app_id(hwnd):
    store = ctypes.c_void_p()
    ctypes.windll.shell32.SHGetPropertyStoreForWindow(wintypes.HWND(hwnd), ctypes.byref(IID_IPropertyStore), ctypes.byref(store))
    if not store.value:
        return None
    v = _get_aumid(store.value)
    _vcall(store.value, 2, ctypes.c_ulong)
    return v


def brand_shortcut(lnk, app_id):
    """Grava o AppUserModelID num atalho .lnk (para a barra ligar a janela a este atalho)."""
    store = ctypes.c_void_p()
    hr = ctypes.windll.shell32.SHGetPropertyStoreFromParsingName(ctypes.c_wchar_p(str(lnk)), None, GPS_READWRITE,
                                                                 ctypes.byref(IID_IPropertyStore), ctypes.byref(store))
    if hr != 0 or not store.value:
        return False
    if _get_aumid(store.value) == app_id:
        _vcall(store.value, 2, ctypes.c_ulong)
        return True
    _set_props(store.value, [(PKEY_ID, app_id)])
    return True


def _app_windows(title_word):
    """Janelas do Chrome/Edge em modo aplicativo cujo título tem a palavra do app."""
    user32 = ctypes.windll.user32
    out = []
    proto = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)

    def cb(hwnd, _):
        if not user32.IsWindowVisible(hwnd) or user32.GetWindow(hwnd, 4):  # GW_OWNER: ignora janelas filhas/diálogos
            return True
        cls = ctypes.create_unicode_buffer(64)
        user32.GetClassNameW(hwnd, cls, 64)
        if cls.value != "Chrome_WidgetWin_1":
            return True
        n = user32.GetWindowTextLengthW(hwnd)
        buf = ctypes.create_unicode_buffer(n + 1)
        user32.GetWindowTextW(hwnd, buf, n + 1)
        t = buf.value
        # janela normal do navegador termina com " - Google Chrome"/" - Microsoft Edge"; a do app não
        if title_word.lower() in t.lower() and not t.endswith(("Google Chrome", "Microsoft Edge", "Chromium")):
            out.append(hwnd)
        return True

    user32.EnumWindows(proto(cb), 0)
    return out


def _shortcut_dirs():
    ap = os.environ.get("APPDATA", "")
    return [os.path.join(os.path.expanduser("~"), "Desktop"),
            os.path.join(os.environ.get("USERPROFILE", ""), "OneDrive", "Desktop"),
            os.path.join(os.environ.get("USERPROFILE", ""), "OneDrive", "Área de Trabalho"),
            os.path.join(ap, r"Microsoft\Windows\Start Menu\Programs"),
            os.path.join(ap, r"Microsoft\Internet Explorer\Quick Launch\User Pinned\TaskBar")]


def fix_shortcuts(app_id, lnk_name):
    """Grava o ID nos atalhos do app (Área de Trabalho, Menu Iniciar e fixado na barra)."""
    n = 0
    folder = lnk_name[:-4]   # Menu Iniciar: Programs\Sintonia\Sintonia.lnk
    for d in _shortcut_dirs():
        for p in (os.path.join(d, lnk_name), os.path.join(d, folder, lnk_name)):
            if not os.path.isfile(p):
                continue
            try:
                n += 1 if brand_shortcut(p, app_id) else 0
            except OSError:
                pass
    return n


def refresh_icon(icon):
    """Se o pacote trouxe um ícone novo (pela atualização automática), põe no lugar do ícone dos atalhos."""
    pkg_ico = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.path.basename(icon))
    try:
        if os.path.isfile(pkg_ico):
            new = open(pkg_ico, "rb").read()
            if not os.path.isfile(icon) or open(icon, "rb").read() != new:
                with open(icon, "wb") as f:
                    f.write(new)
                ctypes.windll.shell32.SHChangeNotify(0x08000000, 0, None, None)   # SHCNE_ASSOCCHANGED: Windows relê os ícones
    except OSError:
        pass


def start(app_id, title_word, lnk_name, relaunch_args, icon):
    """Liga a identidade própria: atalhos agora e, numa linha de fundo, as janelas que forem abrindo."""
    if not IS_WIN:
        return
    refresh_icon(icon)
    exe = sys.executable
    if exe.lower().endswith("python.exe") and os.path.isfile(exe[:-10] + "pythonw.exe"):
        exe = exe[:-10] + "pythonw.exe"
    relaunch = f'"{exe}" {relaunch_args}'

    def loop():
        try:
            ctypes.windll.ole32.CoInitialize(None)
        except OSError:
            pass
        try:
            fix_shortcuts(app_id, lnk_name)
        except Exception:  # noqa: BLE001
            pass
        t0 = time.time()
        while True:
            try:
                for hwnd in _app_windows(title_word):
                    if window_app_id(hwnd) != app_id:
                        brand_window(hwnd, app_id, relaunch, icon, title_word)
            except Exception:  # noqa: BLE001
                pass
            # nos primeiros minutos confere sempre (janela abrindo); depois, a cada 5 s
            time.sleep(1 if time.time() - t0 < 120 else 5)

    threading.Thread(target=loop, name="wintaskbar", daemon=True).start()
