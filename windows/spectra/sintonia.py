"""Canais do Sintonia na tela inicial (como os canais do Android TV).

Lê o Sintonia que roda neste PC (http://127.0.0.1:8787): continuar assistindo, canais favoritos e
sugestões de filmes e séries. Abrir um item abre o Sintonia já na página dele.
"""

import json
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

_cache = {}
_lock = threading.Lock()


class Unavailable(Exception):
    pass


def _get(base, path, password="", timeout=6):
    req = urllib.request.Request(base.rstrip("/") + path, headers={"X-Sintonia-Senha": password or "",
                                                                     "User-Agent": "Spectra-Windows"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if exc.code == 401:
            raise Unavailable("O Sintonia pede senha: informe em Configurações › Canais") from exc
        raise Unavailable(f"O Sintonia respondeu {exc.code}") from exc
    except (OSError, ValueError) as exc:
        raise Unavailable("O Sintonia não está aberto") from exc


def _cached(key, ttl, fn):
    with _lock:
        hit = _cache.get(key)
        if hit and time.time() - hit[0] < ttl:
            return hit[1]
    val = fn()
    with _lock:
        _cache[key] = (time.time(), val)
    return val


def _fmt(sec):
    sec = int(sec or 0)
    h, m = divmod(sec // 60, 60)
    return f"{h}:{m:02d}:{sec % 60:02d}" if h else f"{m}:{sec % 60:02d}"


def _clean(name):
    import re
    return re.sub(r"\s*[(\[](19|20)\d\d[)\]]\s*$", "", name or "").strip()


def channels(cfg, limit=30):
    """Devolve {'ok', 'error', 'rows': {id: [itens]}} com os canais do Sintonia."""
    base = (cfg or {}).get("url") or "http://127.0.0.1:8787"
    pw = (cfg or {}).get("password") or ""
    try:
        st = _cached(("state", base), 20, lambda: _get(base, "/api/state", pw))
    except Unavailable as exc:
        return {"ok": False, "error": str(exc), "rows": {}}
    src = st.get("active")
    if not src:
        return {"ok": False, "error": "Adicione uma lista no Sintonia", "rows": {}}
    try:
        user = _get(base, f"/api/src/{src}/user", pw)
    except Unavailable as exc:
        return {"ok": False, "error": str(exc), "rows": {}}
    rows = {"next": [], "sintonia-live": [], "sintonia-movies": [], "sintonia-series": []}

    # Ver a seguir: filmes e episódios em andamento (um por série), mais recentes primeiro
    seen = set()
    for x in user.get("history") or []:
        kind = x.get("kind")
        key = ("s", x.get("seriesId")) if kind == "episode" else ("m", x.get("id"))
        if key in seen:
            continue
        seen.add(key)
        pos, dur = float(x.get("pos") or 0), float(x.get("dur") or 0)
        if dur and pos / dur > 0.95:
            continue
        if pos < 30:
            continue
        ep = kind == "episode"
        rows["next"].append({
            "key": f"{kind}{x.get('id')}", "title": _clean(x.get("seriesName") if ep else x.get("name")),
            "desc": (f"T{x.get('season')} E{x.get('episode')} · " if ep else "") + f"Continuar de {_fmt(pos)}",
            "img": x.get("img") or "", "progress": round(pos / dur, 3) if dur else 0, "app": "Sintonia",
            "wide": True, "open": f"#/serie/{urllib.parse.quote(str(x.get('seriesId') or ''))}" if ep
            else f"#/movie/{urllib.parse.quote(str(x.get('id')))}", "ts": x.get("ts") or 0})
        if len(rows["next"]) >= limit:
            break

    def catalog(kind):
        return _cached(("cat", base, src, kind), 1800, lambda: _get(base, f"/api/src/{src}/catalog/{kind}", pw, 40))

    favs = (user.get("favorites") or {})
    if favs.get("live"):
        try:
            by_id = {str(c["id"]): c for c in catalog("live").get("items", [])}
            for cid in favs["live"][:limit]:
                c = by_id.get(str(cid))
                if c:
                    rows["sintonia-live"].append({"key": f"live{cid}", "title": c.get("name", ""), "desc": "Ao vivo",
                                                  "img": c.get("logo") or "", "app": "Sintonia", "logo": True,
                                                  "open": "#/live"})
        except Unavailable:
            pass
    for kind, row, route in (("movie", "sintonia-movies", "movie"), ("series", "sintonia-series", "serie")):
        try:
            cat = catalog(kind)
        except Unavailable:
            continue
        if cat.get("lazy"):
            continue
        items = [i for i in cat.get("items", []) if i.get("poster") and float(i.get("rating") or 0) >= 6.5]
        items.sort(key=lambda i: (i.get("added") or 0), reverse=True)
        fav_ids = [str(i) for i in favs.get(kind) or []]
        by_id = {str(i["id"]): i for i in cat.get("items", [])}
        picks = [by_id[i] for i in fav_ids if i in by_id] + items
        done = set()
        for it in picks:
            if str(it["id"]) in done:
                continue
            done.add(str(it["id"]))
            rating = float(it.get("rating") or 0)
            rows[row].append({"key": f"{kind}{it['id']}", "title": _clean(it.get("name")),
                              "desc": " · ".join(x for x in (str(it.get("year") or ""), f"★ {rating:.1f}".replace(".", ",") if rating else "") if x),
                              "img": it.get("poster") or "", "app": "Sintonia", "poster": True,
                              "open": f"#/{route}/{urllib.parse.quote(str(it['id']))}"})
            if len(rows[row]) >= min(limit, 20):
                break
    return {"ok": True, "error": "", "rows": rows}


def running(cfg):
    try:
        _get((cfg or {}).get("url") or "http://127.0.0.1:8787", "/api/state", (cfg or {}).get("password") or "", 2)
        return True
    except Unavailable:
        return False
