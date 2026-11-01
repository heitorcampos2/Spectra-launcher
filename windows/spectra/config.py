"""Pasta de dados e configurações padrão do Spectra para Windows."""

import copy
import os
from pathlib import Path


def data_dir(custom=None):
    if custom:
        p = Path(custom)
    elif os.name == "nt":
        p = Path(os.environ.get("APPDATA") or Path.home()) / "Spectra"
    else:
        p = Path(os.environ.get("XDG_CONFIG_HOME") or Path.home() / ".config") / "spectra"
    p.mkdir(parents=True, exist_ok=True)
    return p


# Mesmas opções do Spectra Launcher do Android TV (Configurações), adaptadas ao PC.
DEFAULT_SETTINGS = {
    "profile": {"name": "", "avatar": "🐉", "showAvatar": False, "title": "Spectra", "showTitle": False,
                "titleBold": True, "titleSize": 30, "titleColor": "", "titleEmoji": ""},
    "look": {"cardStyle": "card", "cardSize": "M", "radius": 14, "nameOnFocus": True},
    "layout": {"leftHeaders": True, "sectionNames": True, "sectionIcons": True, "rowGap": 26, "fixedFocus": False,
               "hideAbove": False, "dimOthers": 55, "marginLeft": 56, "cardGap": 16},
    "status": {"search": True, "net": True, "battery": True, "gear": True, "clock": True, "date": True,
               "avatar": False, "hideOnScroll": False},
    "wall": {"type": "image", "image": "paisagem", "color1": "#0b1220", "color2": "#1e293b", "motion": "parallax",
             "parallax": 18, "effect": "none", "dim": 30, "blur": False, "rotateMin": 0},
    "colors": {"accent": "#3b82f6", "autoAccent": False, "tone": "grafite", "panel": ""},
    "font": {"family": "Figtree", "bold": False, "italic": False, "size": 100},
    "general": {"idleMin": 0, "idleClock": True, "startWithWindows": False, "fullscreen": False, "clock24": True},
    "perf": {"animations": True, "perRow": 30, "hq": True},
    "channels": [
        {"id": "next", "on": True},
        {"id": "sintonia-live", "on": True},
        {"id": "sintonia-movies", "on": True},
        {"id": "sintonia-series", "on": True},
        {"id": "recent-apps", "on": True},
    ],
    "sintonia": {"url": "http://127.0.0.1:8787", "password": ""},
    # ordem das fileiras da tela inicial (seções "sec:" e canais "ch:")
    "homeOrder": ["ch:next", "sec:fav", "sec:tv", "ch:sintonia-live", "sec:chrome", "ch:sintonia-movies",
                  "ch:sintonia-series", "sec:games", "ch:recent-apps", "sec:all"],
    "onboarded": False,
}

# Seções iniciais (como no Android: Favoritos, TV automática, apps do Chrome e todos os apps)
DEFAULT_SECTIONS = [
    {"id": "fav", "name": "Favoritos", "icon": "⭐", "auto": "", "apps": [], "visible": True},
    {"id": "tv", "name": "TV", "icon": "📺", "auto": "tv", "apps": [], "visible": True},
    {"id": "chrome", "name": "Apps do Chrome", "icon": "🌐", "auto": "chrome", "apps": [], "visible": True},
    {"id": "games", "name": "Jogos", "icon": "🎮", "auto": "games", "apps": [], "visible": True},
    {"id": "all", "name": "Todos os apps", "icon": "▦", "auto": "all", "apps": [], "visible": True},
]
SECTION_DEFAULTS = {"showName": True, "size": "", "style": "", "sort": "manual", "hidden": [], "layout": "row",
                    "sizes": {}, "order": [], "visible": True, "apps": [], "auto": "", "icon": "▦"}


def merge_defaults(data, defaults):
    """Completa `data` com o que falta em `defaults` (recursivo para dicionários)."""
    if not isinstance(data, dict) or not isinstance(defaults, dict):
        return copy.deepcopy(data if data is not None else defaults)
    out = copy.deepcopy(defaults)
    for k, v in data.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = merge_defaults(v, out[k])
        else:
            out[k] = copy.deepcopy(v)
    return out


def norm_section(s):
    return merge_defaults(s, SECTION_DEFAULTS)
