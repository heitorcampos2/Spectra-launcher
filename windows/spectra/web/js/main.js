// Spectra para Windows — tela inicial com seções, canais e navegação por teclado, mouse e controle
import { api } from './api.js';
import { btn, clear, closeTopModal, confirmBox, h, ic, menu, modal, promptBox, toast } from './ui.js';
import {
  appPicker, closeLayers, configureSection, layerOpen, moveFocusIn, openPanel, openSettings, pickIconFor,
  welcome,
} from './settings.js';

export const S = {
  settings: null, sections: [], custom: {}, apps: [], byKey: new Map(), ch: { rows: {}, sintonia: {} },
  sys: {}, version: '', demo: false, iconRev: 0, status: {}, walls: [],
};

export const CH = {
  next: { name: 'Ver a seguir', icon: '▶️', sub: 'Continuar assistindo no Sintonia' },
  'sintonia-live': { name: 'Sintonia · Canais favoritos', icon: '📡', sub: 'Seus canais ao vivo favoritos' },
  'sintonia-movies': { name: 'Sintonia · Filmes', icon: '🎬', sub: 'Favoritos e lançamentos bem avaliados' },
  'sintonia-series': { name: 'Sintonia · Séries', icon: '🍿', sub: 'Favoritas e lançamentos bem avaliados' },
  'recent-apps': { name: 'Usados recentemente', icon: '🕘', sub: 'Os últimos apps que você abriu' },
};
export const SIZE_K = { P: 0.8, M: 1, G: 1.25, GG: 1.55 };
export const KIND_LABEL = {
  chrome: 'App do Chrome', edge: 'App do Edge', brave: 'App do Brave', store: 'App da Microsoft Store',
  win: 'Programa do Windows', sintonia: 'TV ao vivo, filmes e séries', custom: 'Atalho adicionado por você',
};

// ------------------------------------------------------------------ dados
export async function saveSettings(patch) {
  S.settings = await api.post('/api/settings', patch);
  applyLook();
  return S.settings;
}
export async function saveSections() {
  S.sections = await api.post('/api/sections', { items: S.sections });
  render();
}
export async function loadApps(refresh) {
  const r = await api.get(`/api/apps${refresh ? '?refresh=1' : ''}`);
  S.apps = r.apps;
  S.byKey = new Map(r.apps.map((a) => [a.key, a]));
  const changed = r.status.iconRev !== S.status.iconRev || r.status.count !== S.status.count || r.status.rev !== S.status.rev;
  S.status = r.status;
  S.iconRev = r.status.iconRev;
  return changed;
}
async function loadChannels() {
  try { S.ch = await api.get('/api/channels'); } catch { /* sem canais */ }
}

let appsPoll = null;
export function watchScan() {
  clearTimeout(appsPoll);
  const tick = async () => {
    const was = S.status.scanning;
    let changed = false;
    try { changed = await loadApps(false); } catch { /* tenta de novo */ }
    if (was && !S.status.scanning) { try { S.sections = (await api.get('/api/state')).sections; } catch { /* ok */ } }
    if (changed || (was && !S.status.scanning)) render();
    if (S.status.scanning || !S.status.scannedAt) appsPoll = setTimeout(tick, 2500);
  };
  appsPoll = setTimeout(tick, 1500);
}

// ------------------------------------------------------------------ modelo das fileiras
export function homeOrder() {
  const ids = [...S.sections.map((s) => `sec:${s.id}`), ...S.settings.channels.map((c) => `ch:${c.id}`)];
  const out = (S.settings.homeOrder || []).filter((id) => ids.includes(id));
  ids.forEach((id) => { if (!out.includes(id)) out.push(id); });
  return out;
}

function pool(sec) {
  const all = S.apps;
  switch (sec.auto) {
    case 'tv': return all.filter((a) => (a.tags || []).includes('tv')).sort((a, b) => (b.kind === 'sintonia') - (a.kind === 'sintonia'));
    case 'chrome': return all.filter((a) => ['chrome', 'edge', 'brave'].includes(a.kind) || (a.kind === 'custom' && a.type === 'url'));
    case 'games': return all.filter((a) => (a.tags || []).includes('games'));
    case 'all': return all.slice().sort((a, b) => a.name.localeCompare(b.name, 'pt'));
    default: return [];
  }
}

export function sectionApps(sec) {
  const hidden = new Set(sec.hidden || []);
  const extra = (sec.apps || []).map((k) => S.byKey.get(k)).filter(Boolean);
  const seen = new Set();
  let list = [...extra, ...pool(sec)].filter((a) => {
    if (seen.has(a.key) || hidden.has(a.key)) return false;
    seen.add(a.key);
    return true;
  });
  if (sec.sort === 'az') list.sort((a, b) => a.name.localeCompare(b.name, 'pt'));
  else if (sec.sort === 'recent') {
    const rec = (S.ch.rows['recent-apps'] || []).map((x) => x.key);
    const pos = (k) => { const i = rec.indexOf(k); return i < 0 ? 999 : i; };
    list.sort((a, b) => pos(a.key) - pos(b.key));
  } else {
    const order = sec.order || [];
    const at = new Map(list.map((a) => [a.key, a]));
    list = [...order.map((k) => at.get(k)).filter(Boolean), ...list.filter((a) => !order.includes(a.key))];
  }
  return list.slice(0, sec.auto === 'all' ? 999 : 200);
}

function buildRows() {
  const rows = [];
  for (const id of homeOrder()) {
    const kind = id.startsWith('sec:') ? 'sec' : 'ch';
    const key = id.slice(id.indexOf(':') + 1);
    if (kind === 'sec') {
      const sec = S.sections.find((s) => s.id === key);
      if (!sec || !sec.visible) continue;
      const apps = sectionApps(sec);
      rows.push({ id, kind: 'sec', sec, title: sec.name, icon: sec.icon, showName: sec.showName,
        style: sec.style || S.settings.look.cardStyle, size: sec.size || S.settings.look.cardSize, layout: sec.layout,
        items: apps.map((a) => ({ type: 'app', key: a.key, app: a })) });
    } else {
      const c = S.settings.channels.find((x) => x.id === key);
      if (!c || !c.on || !CH[key]) continue;
      let items = (S.ch.rows[key] || []).map((m) => ({ type: 'media', key: m.key, m }));
      if (key === 'next' && !S.ch.sintonia?.ok) {
        const inst = S.ch.sintonia?.installed || S.apps.some((a) => a.kind === 'sintonia');
        items = [{ type: 'action', key: 'sintonia-open', label: inst ? 'Abrir o Sintonia' : 'Instalar o Sintonia',
          sub: inst ? (S.ch.sintonia?.error || 'Abra o Sintonia para ver o que continuar') : 'TV ao vivo, filmes e séries',
          icon: inst ? 'play' : 'download', fn: inst ? () => openSintonia('') : () => openSettings('updates') }];
      }
      if (!items.length) continue;
      rows.push({ id, kind: 'ch', ch: key, title: CH[key].name, icon: CH[key].icon, showName: true,
        style: 'card', size: S.settings.look.cardSize, items });
    }
  }
  return rows;
}

// ------------------------------------------------------------------ aparência
const TONES = ['grafite', 'amoled', 'indigo', 'vinho', 'azul-noite', 'floresta', 'vidro'];
export function applyLook() {
  const s = S.settings;
  const root = document.documentElement;
  const accent = (s.colors.autoAccent && autoAccent) || s.colors.accent || '#3b82f6';
  root.style.setProperty('--accent', accent);
  root.style.setProperty('--accent-2', shift(accent, 30));
  root.dataset.tone = TONES.includes(s.colors.tone) ? s.colors.tone : 'grafite';
  if (s.colors.panel) root.style.setProperty('--pane', hexA(s.colors.panel, s.colors.tone === 'vidro' ? 0.55 : 0.92));
  else root.style.removeProperty('--pane');
  root.style.setProperty('--cs', String(SIZE_K[s.look.cardSize] || 1));
  root.style.setProperty('--rad', `${s.look.radius}px`);
  root.style.setProperty('--gap', `${s.layout.cardGap}px`);
  root.style.setProperty('--ml', `${s.layout.marginLeft}px`);
  root.style.setProperty('--rowgap', `${s.layout.rowGap}px`);
  root.style.setProperty('--dim', String(1 - s.layout.dimOthers / 100));
  root.style.setProperty('--fsz', String((s.font.size || 100) / 100));
  root.style.setProperty('--font-ui', `'${s.font.family}', 'Figtree', 'Segoe UI', system-ui, sans-serif`);
  root.style.setProperty('--title-c', s.profile.titleColor || '#fff');
  root.style.setProperty('--tsz', `${s.profile.titleSize}px`);
  document.body.classList.toggle('bold', !!s.font.bold);
  document.body.classList.toggle('italic', !!s.font.italic);
  document.body.classList.toggle('noanim', !s.perf.animations);
  document.body.classList.toggle('name-focus', !!s.look.nameOnFocus);
  document.body.classList.toggle('noleft', !s.layout.leftHeaders);
}
function hexA(hex, a) {
  const n = parseInt(hex.replace('#', ''), 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`;
}
function shift(hex, deg) {
  const n = parseInt((hex || '#3b82f6').replace('#', ''), 16);
  let r = ((n >> 16) & 255) / 255; let g = ((n >> 8) & 255) / 255; let b = (n & 255) / 255;
  const mx = Math.max(r, g, b); const mn = Math.min(r, g, b); const l = (mx + mn) / 2;
  let hh = 0; let ss = 0;
  if (mx !== mn) {
    const d = mx - mn;
    ss = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn);
    hh = mx === r ? (g - b) / d + (g < b ? 6 : 0) : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
    hh *= 60;
  }
  return `hsl(${(hh + deg) % 360} ${Math.round(ss * 100)}% ${Math.round(l * 100)}%)`;
}
export function hue(text) {
  let x = 0;
  for (const c of text || '') x = (x * 31 + c.charCodeAt(0)) % 360;
  return x;
}

// ------------------------------------------------------------------ fundo (wallpaper, movimento e efeitos)
let autoAccent = '';
let wallKey = '';
let rotateT = null;
export function applyWall(force) {
  const w = S.settings.wall;
  const bg = document.getElementById('bg');
  bg.classList.toggle('blur', !!w.blur);
  bg.classList.toggle('pan', w.motion === 'pan');
  document.documentElement.style.setProperty('--shade', String((w.dim || 0) / 100));
  const key = JSON.stringify([w.type, w.image, w.color1, w.color2]);
  if (key !== wallKey || force) {
    wallKey = key;
    clear(bg);
    bg.style.background = w.type === 'gradient' ? `linear-gradient(135deg, ${w.color1}, ${w.color2})` : w.type === 'color' ? w.color1 : '#0b1220';
    if (w.type === 'image') {
      const info = S.walls.find((x) => x.id === w.image);
      const url = `/api/wallpaper/${encodeURIComponent(w.image || 'paisagem')}`;
      const isVideo = info ? info.type === 'video' : /\.(mp4|webm|mkv|mov)$/i.test(w.image || '');
      const el = isVideo ? h('video', { src: url, autoplay: true, muted: true, loop: true, playsinline: true })
        : h('img', { src: url, alt: '', crossorigin: 'anonymous' });
      if (isVideo) el.muted = true;
      const on = () => { el.classList.add('on'); if (!isVideo) pickAccent(el); };
      el.addEventListener(isVideo ? 'loadeddata' : 'load', on);
      el.addEventListener('error', () => { if (w.image !== 'paisagem') { S.settings.wall.image = 'paisagem'; applyWall(true); } });
      bg.append(el);
    }
  }
  clearInterval(rotateT);
  if (w.rotateMin > 0) {
    rotateT = setInterval(async () => {
      const imgs = S.walls.filter((x) => x.type === 'image');
      if (imgs.length < 2) return;
      const i = imgs.findIndex((x) => x.id === S.settings.wall.image);
      S.settings.wall.image = imgs[(i + 1) % imgs.length].id;
      applyWall();
    }, w.rotateMin * 60000);
  }
  startFx(w.effect);
  parallax();
}
function pickAccent(img) {
  try {
    const c = document.createElement('canvas');
    c.width = 24; c.height = 24;
    const g = c.getContext('2d');
    g.drawImage(img, 0, 0, 24, 24);
    const d = g.getImageData(0, 0, 24, 24).data;
    let best = null; let score = -1;
    for (let i = 0; i < d.length; i += 4) {
      const [r, gg, b] = [d[i], d[i + 1], d[i + 2]];
      const mx = Math.max(r, gg, b); const mn = Math.min(r, gg, b);
      const sat = mx ? (mx - mn) / mx : 0;
      const sc = sat * (mx / 255);
      if (sc > score && mx > 70) { score = sc; best = [r, gg, b]; }
    }
    if (best) {
      autoAccent = `#${best.map((v) => v.toString(16).padStart(2, '0')).join('')}`;
      if (S.settings.colors.autoAccent) applyLook();
    }
  } catch { /* imagem de outro site */ }
}
let fxRaf = 0;
function startFx(kind) {
  cancelAnimationFrame(fxRaf);
  const cv = document.getElementById('fx');
  const g = cv.getContext('2d');
  cv.width = innerWidth; cv.height = innerHeight;
  g.clearRect(0, 0, cv.width, cv.height);
  if (!kind || kind === 'none' || !S.settings.perf.animations) return;
  const n = kind === 'snow' ? 120 : 60;
  const ps = Array.from({ length: n }, () => ({ x: Math.random() * cv.width, y: Math.random() * cv.height,
    r: kind === 'snow' ? 1 + Math.random() * 2.5 : 1 + Math.random() * 2, vx: (Math.random() - 0.5) * 0.3,
    vy: kind === 'snow' ? 0.4 + Math.random() * 0.8 : -0.15 - Math.random() * 0.35, a: 0.25 + Math.random() * 0.55 }));
  const step = () => {
    g.clearRect(0, 0, cv.width, cv.height);
    for (const p of ps) {
      p.x += p.vx; p.y += p.vy;
      if (p.y > cv.height + 5) p.y = -5;
      if (p.y < -5) p.y = cv.height + 5;
      if (p.x < -5) p.x = cv.width + 5;
      if (p.x > cv.width + 5) p.x = -5;
      g.beginPath();
      g.fillStyle = kind === 'snow' ? `rgba(255,255,255,${p.a})` : `rgba(255,255,255,${p.a * 0.8})`;
      g.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      g.fill();
    }
    fxRaf = requestAnimationFrame(step);
  };
  step();
}
let mouseShift = [0, 0];
function parallax() {
  const w = S.settings.wall;
  const bg = document.getElementById('bg');
  if (w.motion !== 'parallax' || !S.settings.perf.animations) { bg.style.transform = ''; return; }
  const k = (w.parallax || 0) / 100;
  const x = -(F.col * 6 + mouseShift[0] * 30) * k;
  const y = -(F.row * 8 + mouseShift[1] * 20) * k;
  bg.style.transform = `translate(${Math.max(-38, Math.min(38, x))}px, ${Math.max(-38, Math.min(38, y))}px)`;
}

// ------------------------------------------------------------------ tela inicial
const F = { row: 0, col: 0, zone: 'rows', top: 0, rail: 0, moving: null };
const lastCol = {};
let rows = [];
let rowsPrev = [];
let dom = null;

function el(tag, ...a) { return h(tag, ...a); }

export function render() {
  if (!S.settings) return;
  applyLook();
  const home = document.getElementById('home');
  const curId = rows[F.row]?.id;
  rowsPrev = rows;
  rows = buildRows();
  if (curId) {
    const i = rows.findIndex((r) => r.id === curId);
    if (i >= 0) F.row = i;
  }
  F.row = Math.min(F.row, Math.max(0, rows.length - 1));
  // guarda a rolagem para a tela não pular ao redesenhar
  const keepTop = dom?.list.scrollTop || 0;
  const keepLeft = {};
  dom?.list.querySelectorAll('.row').forEach((r) => { const id = rowsPrev[Number(r.dataset.i)]?.id; if (id) keepLeft[id] = r.querySelector('.strip')?.scrollLeft || 0; });
  clear(home);
  const top = topbar();
  const rail = el('nav.rail', { 'aria-label': 'Seções' });
  const list = el('div.rows');
  rows.forEach((r, i) => {
    rail.append(el('button.rail-i', { type: 'button', title: r.title, dataset: { i }, onclick: () => { F.zone = 'rows'; setRow(i); } }, r.icon || '▦'));
    list.append(rowEl(r, i));
  });
  rail.append(el('div.rail-tools',
    el('button', { type: 'button', title: 'Configurar esta fileira', dataset: { t: 'cfg' }, onclick: () => rowConfig(rows[F.row]) }, '⚙'),
    el('button', { type: 'button', title: 'Mover esta fileira para cima', dataset: { t: 'up' }, onclick: () => moveRow(-1) }, '▲'),
    el('button', { type: 'button', title: 'Mover esta fileira para baixo', dataset: { t: 'down' }, onclick: () => moveRow(1) }, '▼')));
  if (!rows.length) {
    list.append(el('div.row-empty', S.status.scanning ? 'Lendo os apps do Windows…' : 'Nenhuma seção visível.',
      btn('Editar seções', { cls: 'primary sm', onclick: () => openSettings('sections') })));
  }
  home.append(top, el('div.body', rail, list));
  home.querySelector('.rows').addEventListener('scroll', () => {}, { passive: true });
  dom = { home, top, rail, list };
  list.style.scrollBehavior = 'auto';
  list.scrollTop = keepTop;
  list.querySelectorAll('.row').forEach((r) => {
    const st = r.querySelector('.strip');
    const left = keepLeft[rows[Number(r.dataset.i)]?.id];
    if (st && left) { st.style.scrollBehavior = 'auto'; st.scrollLeft = left; st.style.scrollBehavior = ''; }
  });
  rowsPrev = rows;
  requestAnimationFrame(() => { list.style.scrollBehavior = ''; });
  if (S.demo && !document.querySelector('.demo-flag')) document.body.append(el('div.demo-flag', 'Modo demonstração (fora do Windows)'));
  if (!document.querySelector('.hint-bar')) {
    document.body.append(el('div.hint-bar', el('kbd', '← ↑ → ↓'), 'navegar', el('kbd', 'Enter'), 'abrir',
      el('kbd', 'Botão direito'), 'opções', el('kbd', 'Esc'), 'menu', el('kbd', '/'), 'pesquisar'));
    setTimeout(() => document.querySelector('.hint-bar')?.remove(), 12000);
  }
  focusNow(true);
}

function rowEl(r, i) {
  const strip = el('div.strip', { class: `st-${r.style}` });
  const row = el('section.row', { class: r.layout === 'grid' ? 'grid' : '', dataset: { i } },
    r.showName && S.settings.layout.sectionNames ? el('h2.row-head', S.settings.layout.sectionIcons ? el('span.ri', r.icon) : null, r.title,
      r.kind === 'ch' ? el('small', CH[r.ch]?.sub || '') : null,
      el('button.cfg', { type: 'button', title: 'Configurar', onclick: (e) => { e.stopPropagation(); rowConfig(r); } }, '⚙')) : null,
    strip, el('div.row-info'));
  r.items.forEach((it, j) => strip.append(cardEl(r, it, i, j)));
  if (r.kind === 'sec' && !r.items.length) {
    strip.append(cardEl(r, { type: 'action', key: 'add', label: 'Adicionar apps', icon: 'plus', fn: () => appPicker(r.sec) }, i, 0));
    r.items.push({ type: 'action', key: 'add', label: 'Adicionar apps', icon: 'plus', fn: () => appPicker(r.sec) });
  }
  return row;
}

export function appIcon(a, cls = '') {
  const letter = () => el('span.letter', { class: cls, style: { '--hue': hue(a.name) } }, (a.name || '?').trim().charAt(0).toUpperCase());
  if (a.icon === false) return letter();
  const img = el('img', { src: `/api/icon/${a.key}?v=${S.iconRev}`, alt: '', loading: 'lazy', class: cls, draggable: 'false' });
  img.addEventListener('error', () => img.replaceWith(letter()));
  return img;
}

function cardEl(r, it, i, j) {
  let card;
  if (it.type === 'app') {
    const sz = r.sec?.sizes?.[it.key] || r.size;
    const k = (SIZE_K[sz] || 1) / (SIZE_K[S.settings.look.cardSize] || 1);
    card = el('button.card', { type: 'button', style: { '--k': k } },
      el('div.tile', appIcon(it.app), el('span.bname', it.app.name)), el('span.name', it.app.name));
  } else if (it.type === 'media') {
    const m = it.m;
    const cls = m.poster ? 'poster' : m.logo ? 'logo' : '';
    const tile = el('div.tile', { style: m.img ? { backgroundImage: `url("${String(m.img).replace(/"/g, '%22')}")` } : null },
      m.img ? null : (m.icon ? el('img', { src: m.icon, alt: '' }) : el('span.ph', m.title)),
      m.progress ? el('div.prog', el('i', { style: { width: `${Math.round(m.progress * 100)}%` } })) : null);
    if (m.icon && m.launch) {
      tile.style.backgroundImage = '';
      card = el('button.card', { type: 'button' }, el('div.tile', el('img', { src: `${m.icon}?v=${S.iconRev}`, alt: '', onerror: (e) => e.target.replaceWith(el('span.letter', { style: { '--hue': hue(m.title) } }, m.title.charAt(0))) })), el('span.name', m.title));
    } else {
      card = el('button.card.media', { type: 'button', class: cls }, tile, el('span.name', m.title), m.poster ? null : el('small', m.desc || ''));
    }
  } else {
    card = el('button.card.action', { type: 'button' }, el('div.tile', ic(it.icon || 'plus')), el('span.name', it.label));
  }
  card.dataset.r = i;
  card.dataset.c = j;
  card.addEventListener('click', () => { F.zone = 'rows'; F.row = i; F.col = j; activate(); });
  card.addEventListener('contextmenu', (e) => { e.preventDefault(); F.zone = 'rows'; F.row = i; F.col = j; focusNow(); options(); });
  card.addEventListener('mousemove', () => {
    if (!mouseMode || (F.zone === 'rows' && F.row === i && F.col === j) || F.moving) return;
    F.zone = 'rows'; F.row = i; F.col = j;
    focusNow(false, true);
  });
  return card;
}

function topbar() {
  const s = S.settings;
  const st = s.status;
  const items = [];
  const bar = el('header.topbar');
  bar.append(el('button.menu-btn', { type: 'button', title: 'Menu (Esc)', 'data-top': '', onclick: () => openPanel() }, el('img', { src: '/img/logo.png', alt: 'Spectra' })));
  if (s.profile.showTitle) {
    bar.append(el('div.home-title', { class: s.profile.titleBold ? '' : 'light' }, s.profile.titleEmoji || null, s.profile.title || 'Spectra'));
  }
  bar.append(el('div.grow'));
  const status = el('div.status');
  if (st.search) items.push(el('button.st-btn', { type: 'button', title: 'Pesquisar (/)', onclick: () => search() }, ic('search')));
  if (st.net) items.push(el('button.st-btn.net', { type: 'button', title: 'Rede', onclick: () => api.post('/api/system-settings', { page: 'wifi' }) }, ic('wifi')));
  if (st.battery) items.push(el('button.st-btn.batt', { type: 'button', title: 'Bateria', hidden: true, onclick: () => api.post('/api/system-settings', { page: 'battery' }) }));
  if (st.gear) items.push(el('button.st-btn', { type: 'button', title: 'Configurações do Spectra', onclick: () => openSettings() }, ic('gear')));
  if (st.avatar || s.profile.showAvatar) items.push(el('button.st-btn', { type: 'button', title: s.profile.name || 'Perfil', onclick: () => openSettings('profile') }, el('span.avatar', s.profile.avatar || '🙂'), s.profile.name ? el('span', s.profile.name) : null));
  items.forEach((b) => { b.dataset.top = ''; status.append(b); });
  if (st.clock) status.append(el('div.st-clock', el('b.clk'), st.date ? el('small.dt') : null));
  bar.append(status);
  setTimeout(drawStatus, 0);
  return bar;
}

function drawStatus() {
  const now = new Date();
  const clock24 = S.settings.general.clock24;
  document.querySelectorAll('.clk').forEach((c) => { c.textContent = now.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', hour12: !clock24 }); });
  document.querySelectorAll('.dt').forEach((d) => { d.textContent = now.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'short' }); });
  const net = document.querySelector('.st-btn.net');
  if (net) {
    net.style.opacity = S.sys.online === false ? '.45' : '1';
    net.title = S.sys.online === false ? 'Sem internet' : (S.sys.wifi ? `Wi-Fi: ${S.sys.wifi}` : 'Conectado');
  }
  const b = document.querySelector('.st-btn.batt');
  if (b) {
    const bt = S.sys.battery;
    b.hidden = !bt;
    if (bt) b.textContent = `${bt.charging ? '⚡' : '🔋'} ${bt.pct}%`;
  }
}

// ------------------------------------------------------------------ foco
let mouseMode = false;
function cardAt(r, c) { return dom?.list.querySelector(`.card[data-r="${r}"][data-c="${c}"]`); }

function setRow(i) {
  F.row = Math.max(0, Math.min(rows.length - 1, i));
  F.col = Math.min(lastCol[rows[F.row]?.id] ?? 0, (rows[F.row]?.items.length || 1) - 1);
  focusNow();
}

export function focusNow(scroll = true, fromMouse = false) {
  if (!dom) return;
  dom.home.querySelectorAll('.f').forEach((x) => x.classList.remove('f'));
  const hideAbove = S.settings.layout.hideAbove;
  dom.list.querySelectorAll('.row').forEach((r, i) => {
    r.classList.toggle('cur', i === F.row && F.zone === 'rows');
    r.classList.toggle('dim', i !== F.row);
    r.classList.toggle('gone', hideAbove && i < F.row);
  });
  dom.rail.querySelectorAll('.rail-i').forEach((b, i) => b.classList.toggle('on', i === F.row));
  dom.top.classList.toggle('hid', S.settings.status.hideOnScroll && F.row > 0 && F.zone === 'rows');
  if (F.zone === 'top') {
    const t = dom.top.querySelectorAll('[data-top]')[F.top];
    if (t) { t.classList.add('f'); t.focus({ preventScroll: true }); }
    return;
  }
  if (F.zone === 'rail') {
    const tools = dom.rail.querySelectorAll('.rail-tools button');
    const b = F.rail >= 0 ? tools[F.rail] : null;
    if (b) b.classList.add('f');
    return;
  }
  const r = rows[F.row];
  if (!r) return;
  F.col = Math.max(0, Math.min(F.col, r.items.length - 1));
  lastCol[r.id] = F.col;
  const card = cardAt(F.row, F.col);
  if (card) {
    card.classList.add('f');
    if (F.moving) card.classList.add('moving');
    if (!fromMouse && !layerOpen() && !document.querySelector('.search-ov')) card.focus({ preventScroll: true });
    if (scroll) {
      const strip = card.parentElement;
      if (!r.layout || r.layout !== 'grid') {
        const target = S.settings.layout.fixedFocus ? card.offsetLeft - 4 : Math.max(0, card.offsetLeft - strip.clientWidth / 2 + card.offsetWidth / 2);
        strip.scrollTo({ left: target });
      }
      const rowEl2 = card.closest('.row');
      if (S.settings.layout.fixedFocus || hideAbove) dom.list.scrollTo({ top: rowEl2.offsetTop - 6 });
      else rowEl2.scrollIntoView({ block: 'nearest' });
      const rr = rowEl2.getBoundingClientRect();
      const lr = dom.list.getBoundingClientRect();
      if (rr.top < lr.top || rr.bottom > lr.bottom - 40) dom.list.scrollTo({ top: rowEl2.offsetTop - lr.height * 0.25 });
    }
  }
  const info = dom.list.querySelector('.row.cur .row-info');
  if (info) {
    const it = r.items[F.col];
    clear(info);
    if (it?.type === 'app') info.append(el('b', it.app.name), el('span', KIND_LABEL[it.app.kind] || ''));
    else if (it?.type === 'media') info.append(el('b', it.m.title), el('span', [it.m.app, it.m.desc].filter(Boolean).join(' · ')));
    else if (it) info.append(el('b', it.label), it.sub ? el('span', it.sub) : null);
  }
  parallax();
}

function move(dir) {
  if (F.moving) return moveCard(dir);
  if (F.zone === 'top') {
    const n = dom.top.querySelectorAll('[data-top]').length;
    if (dir === 'left') F.top = Math.max(0, F.top - 1);
    else if (dir === 'right') F.top = Math.min(n - 1, F.top + 1);
    else if (dir === 'down') F.zone = 'rows';
    return focusNow();
  }
  if (F.zone === 'rail') {
    if (dir === 'right') { F.zone = 'rows'; return focusNow(); }
    if (dir === 'up') { if (F.rail > 0) F.rail -= 1; else if (F.rail === 0) F.rail = -1; else setRow(F.row - 1); }
    if (dir === 'down') { if (F.rail < 0) F.rail = 0; else F.rail = Math.min(2, F.rail + 1); }
    return focusNow(false);
  }
  const r = rows[F.row];
  if (!r) { if (dir === 'up') { F.zone = 'top'; focusNow(); } return; }
  if (r.layout === 'grid' && (dir === 'up' || dir === 'down')) {
    // grade: sobe/desce dentro da seção antes de trocar de fileira
    const cards = [...dom.list.querySelectorAll(`.card[data-r="${F.row}"]`)];
    const cur = cards[F.col];
    if (cur) {
      const y = cur.offsetTop; const x = cur.offsetLeft;
      const cand = cards.filter((c) => (dir === 'down' ? c.offsetTop > y + 5 : c.offsetTop < y - 5));
      if (cand.length) {
        const ty = dir === 'down' ? Math.min(...cand.map((c) => c.offsetTop)) : Math.max(...cand.map((c) => c.offsetTop));
        const line = cand.filter((c) => Math.abs(c.offsetTop - ty) < 5);
        line.sort((a, b) => Math.abs(a.offsetLeft - x) - Math.abs(b.offsetLeft - x));
        F.col = Number(line[0].dataset.c);
        return focusNow();
      }
    }
  }
  if (dir === 'left') {
    if (F.col > 0) F.col -= 1;
    else if (S.settings.layout.leftHeaders) { F.zone = 'rail'; F.rail = -1; return focusNow(false); }
  } else if (dir === 'right') F.col = Math.min(r.items.length - 1, F.col + 1);
  else if (dir === 'up') {
    if (F.row === 0) { F.zone = 'top'; F.top = 0; return focusNow(); }
    return setRow(F.row - 1);
  } else if (dir === 'down') return setRow(F.row + 1);
  focusNow();
}

function activate() {
  if (F.moving) return finishMove();
  if (F.zone === 'top') { dom.top.querySelectorAll('[data-top]')[F.top]?.click(); return; }
  if (F.zone === 'rail') { dom.rail.querySelectorAll('.rail-tools button')[F.rail]?.click(); return; }
  const it = rows[F.row]?.items[F.col];
  if (!it) return;
  focusNow(false);
  if (it.type === 'app') launch(it.app);
  else if (it.type === 'media') openMedia(it.m);
  else if (it.fn) it.fn();
}

export async function launch(a) {
  const card = cardAt(F.row, F.col);
  if (card) { card.animate([{ transform: 'scale(1.09)' }, { transform: 'scale(.96)' }, { transform: 'scale(1.09)' }], { duration: 260 }); }
  try {
    const r = await api.post('/api/launch', { key: a.key });
    toast(r.message && r.message !== 'ok' ? r.message : `Abrindo ${a.name}…`, { ms: 1800 });
    setTimeout(refreshChannels, 1500);
  } catch (e) { toast(e.message, { type: 'error', ms: 5000 }); }
}

export async function openSintonia(route) {
  try {
    toast('Abrindo o Sintonia…', { ms: 2500 });
    await api.post('/api/sintonia/open', { route });
  } catch (e) { toast(e.message, { type: 'error', ms: 6000 }); }
}

function openMedia(m) {
  if (m.launch) {
    const a = S.byKey.get(m.launch);
    if (a) return launch(a);
  }
  return openSintonia(m.open || '');
}

// ------------------------------------------------------------------ opções do cartão (botão direito / tecla Menu)
async function options() {
  const r = rows[F.row];
  const it = r?.items[F.col];
  if (!it) return;
  if (it.type === 'media' || it.type === 'action') {
    const ch = await menu([
      { label: 'Abrir', icon: 'play', fn: activate },
      r.kind === 'ch' ? { label: 'Ocultar este canal', icon: 'eyeOff', fn: () => toggleChannel(r.ch, false) } : null,
      { label: 'Organizar canais', icon: 'listCheck', fn: () => openSettings('channels') },
    ].filter(Boolean), { title: it.m?.title || it.label });
    return ch;
  }
  const a = it.app;
  const sec = r.sec;
  const items = [
    { label: 'Abrir', icon: 'play', fn: activate },
    sec.sort === 'manual' && { label: 'Mover', sub: '◀ ▶ e Enter para soltar', icon: 'sort', fn: () => startMove() },
    { label: 'Tamanho deste cartão', sub: sec.sizes?.[a.key] ? `Agora: ${sec.sizes[a.key]}` : 'Padrão da seção', icon: 'grid', fn: () => cardSize(sec, a) },
    { label: 'Adicionar a outra seção…', icon: 'plus', fn: () => addToSection(a, sec) },
    { label: sec.auto ? 'Ocultar desta seção' : 'Remover desta seção', icon: 'eyeOff', fn: () => removeFrom(sec, a) },
    null,
    { label: 'Trocar ícone…', icon: 'brush', fn: () => pickIconFor(a) },
    a.kind === 'custom' && { label: 'Excluir este atalho', icon: 'trash', danger: true, fn: () => delCustom(a) },
    { label: `Configurar a seção "${sec.name}"`, icon: 'sliders', fn: () => configureSection(sec) },
  ].filter((x) => x !== false);
  return menu(items, { title: a.name });
}

async function cardSize(sec, a) {
  const cur = sec.sizes?.[a.key] || '';
  const ch = await menu([['', 'Padrão da seção'], ['P', 'Pequeno'], ['M', 'Médio'], ['G', 'Grande'], ['GG', 'Gigante']]
    .map(([v, l]) => ({ label: l, value: v, checked: v === cur })), { title: `Tamanho de ${a.name}` });
  if (!ch) return;
  sec.sizes = { ...(sec.sizes || {}) };
  if (ch.value) sec.sizes[a.key] = ch.value; else delete sec.sizes[a.key];
  await saveSections();
}

async function addToSection(a, from) {
  const others = S.sections.filter((s) => s.id !== from.id);
  const ch = await menu([...others.map((s) => ({ label: `${s.icon} ${s.name}`, value: s.id })), null,
    { label: 'Nova seção…', icon: 'plus', value: '__new' }], { title: `Adicionar ${a.name} em…` });
  if (!ch) return;
  let sec = S.sections.find((s) => s.id === ch.value);
  if (ch.value === '__new') {
    const name = await promptBox('Nome da seção', { title: 'Nova seção', placeholder: 'Ex.: Trabalho' });
    if (!name) return;
    sec = { id: `s${Date.now().toString(36)}`, name, icon: '📁', auto: '', apps: [], visible: true };
    S.sections.push(sec);
  }
  sec.apps = [...new Set([...(sec.apps || []), a.key])];
  sec.hidden = (sec.hidden || []).filter((k) => k !== a.key);
  await saveSections();
  toast(`${a.name} adicionado em ${sec.name}`, { type: 'ok' });
}

async function removeFrom(sec, a) {
  sec.apps = (sec.apps || []).filter((k) => k !== a.key);
  if (sec.auto) sec.hidden = [...new Set([...(sec.hidden || []), a.key])];
  sec.order = (sec.order || []).filter((k) => k !== a.key);
  await saveSections();
  toast(`${a.name} saiu de ${sec.name}`, { action: { label: 'Desfazer', fn: async () => {
    sec.hidden = (sec.hidden || []).filter((k) => k !== a.key);
    if (!sec.auto) sec.apps = [...(sec.apps || []), a.key];
    await saveSections();
  } }, ms: 5000 });
}

async function delCustom(a) {
  if (!(await confirmBox(`Excluir o atalho "${a.name}"?`, { ok: 'Excluir', danger: true }))) return;
  await api.del(`/api/custom/${a.key}`);
  await loadApps();
  render();
}

export async function toggleChannel(id, on) {
  const chans = S.settings.channels.map((c) => (c.id === id ? { ...c, on } : c));
  await saveSettings({ channels: chans });
  render();
}

function startMove() {
  F.moving = { row: F.row };
  focusNow();
  toast('Use ◀ ▶ para mover e Enter para soltar', { ms: 3000 });
}
function moveCard(dir) {
  const r = rows[F.row];
  if (dir !== 'left' && dir !== 'right') return;
  const to = F.col + (dir === 'left' ? -1 : 1);
  if (to < 0 || to >= r.items.length) return;
  const items = r.items;
  [items[F.col], items[to]] = [items[to], items[F.col]];
  const strip = cardAt(F.row, F.col).parentElement;
  const a = cardAt(F.row, F.col); const b = cardAt(F.row, to);
  if (dir === 'left') strip.insertBefore(a, b); else strip.insertBefore(b, a);
  a.dataset.c = to; b.dataset.c = F.col;
  F.col = to;
  focusNow();
}
async function finishMove() {
  const r = rows[F.moving.row];
  F.moving = null;
  r.sec.order = r.items.filter((x) => x.type === 'app').map((x) => x.key);
  if (!r.sec.auto) r.sec.apps = r.sec.order.slice();
  await saveSections();
}

async function moveRow(d) {
  const order = homeOrder();
  const id = rows[F.row]?.id;
  if (!id) return;
  // troca com a próxima fileira visível na direção
  const target = rows[F.row + d]?.id;
  if (!target) return;
  const a = order.indexOf(id); const b = order.indexOf(target);
  [order[a], order[b]] = [order[b], order[a]];
  await saveSettings({ homeOrder: order });
  F.row += d;
  render();
}

function rowConfig(r) {
  if (!r) return;
  if (r.kind === 'sec') configureSection(r.sec);
  else openSettings('channels');
}

// ------------------------------------------------------------------ pesquisa
export function search(initial = '') {
  if (document.querySelector('.search-ov')) return;
  const input = el('input.input', { type: 'search', placeholder: 'Pesquisar apps, canais, filmes…', value: initial, autocomplete: 'off' });
  const res = el('div.search-res');
  const web = btn('Pesquisar na web', { cls: 'ghost search-web', icon: 'external', onclick: () => {
    api.post('/api/open-url', { url: `https://www.google.com/search?q=${encodeURIComponent(input.value)}` }); close();
  } });
  const ov = el('div.search-ov', input, res, web);
  let found = [];
  const close = () => { ov.classList.remove('show'); setTimeout(() => ov.remove(), 200); document.removeEventListener('keydown', onKey, true); };
  const norm = (s) => (s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const draw = () => {
    clear(res);
    const q = norm(input.value.trim());
    if (!q) { found = []; return; }
    const apps = S.apps.filter((a) => norm(a.name).includes(q)).slice(0, 24).map((a) => ({ type: 'app', a }));
    const media = Object.values(S.ch.rows || {}).flat().filter((m) => m && !m.launch && norm(m.title).includes(q)).slice(0, 12).map((m) => ({ type: 'media', m }));
    found = [...apps, ...media];
    found.forEach((f, i) => {
      const c = f.type === 'app'
        ? el('button.card', { type: 'button', class: i === 0 ? 'f' : '' }, el('div.tile', appIcon(f.a)), el('span.name', f.a.name))
        : el('button.card.media', { type: 'button', class: (f.m.poster ? 'poster ' : '') + (i === 0 ? 'f' : '') }, el('div.tile', { style: { backgroundImage: f.m.img ? `url("${f.m.img}")` : '' } }), el('span.name', f.m.title));
      c.addEventListener('click', () => { close(); if (f.type === 'app') launch(f.a); else openMedia(f.m); });
      res.append(c);
    });
  };
  const onKey = (e) => {
    if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(); }
    else if (e.key === 'Enter' && found[0] && document.activeElement === input) {
      e.preventDefault(); e.stopPropagation();
      close();
      if (found[0].type === 'app') launch(found[0].a); else openMedia(found[0].m);
    } else if (['ArrowDown', 'ArrowRight', 'ArrowLeft', 'ArrowUp'].includes(e.key) && document.activeElement !== input) {
      e.preventDefault(); e.stopPropagation();
      moveFocusIn(ov, e.key.replace('Arrow', '').toLowerCase());
    } else if (e.key === 'ArrowDown' && document.activeElement === input) {
      e.preventDefault(); e.stopPropagation();
      res.querySelector('.card')?.focus();
    }
  };
  document.addEventListener('keydown', onKey, true);
  input.addEventListener('input', draw);
  ov.addEventListener('click', (e) => { if (e.target === ov) close(); });
  document.body.append(ov);
  requestAnimationFrame(() => ov.classList.add('show'));
  input.focus();
  draw();
}

// ------------------------------------------------------------------ teclado, mouse e controle
let enterDown = 0;
let longT = null;
function onKey(e) {
  wake();
  if (document.querySelector('.search-ov')) return;
  if (e.key === 'F11') { e.preventDefault(); toggleFull(); return; }
  if (layerOpen()) {
    if (e.key === 'Escape') { e.preventDefault(); closeLayers(); return; }
    const k = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right' }[e.key];
    const t = e.target;
    const typing = t && (t.tagName === 'INPUT' && !['checkbox', 'radio', 'color', 'range'].includes(t.type) || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT');
    if (k && !typing) { e.preventDefault(); moveFocusIn(null, k); }
    return;
  }
  mouseMode = false;
  document.body.classList.add('mouse-hide');
  const k = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right' }[e.key];
  if (k) { e.preventDefault(); move(k); return; }
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    if (e.repeat) return;
    enterDown = Date.now();
    clearTimeout(longT);
    longT = setTimeout(() => { enterDown = 0; if (F.zone === 'rows' && !F.moving) options(); }, 650);
    return;
  }
  if (e.key === 'ContextMenu' || (e.shiftKey && e.key === 'F10') || e.key === 'o') { e.preventDefault(); if (F.zone === 'rows') options(); return; }
  if (e.key === 'Escape' || e.key === 'Backspace') {
    e.preventDefault();
    if (F.moving) { F.moving = null; render(); return; }
    openPanel();
    return;
  }
  if (e.key === 'Home') { setRow(0); return; }
  if (e.key === 'End') { setRow(rows.length - 1); return; }
  if (e.key === 'PageDown') { setRow(F.row + 3); return; }
  if (e.key === 'PageUp') { setRow(F.row - 3); return; }
  if (e.key === '/' || (e.ctrlKey && e.key.toLowerCase() === 'f')) { e.preventDefault(); search(); return; }
  if (e.key === ',' && e.ctrlKey) { e.preventDefault(); openSettings(); return; }
  if (e.key.length === 1 && /[\p{L}\p{N}]/u.test(e.key) && !e.ctrlKey && !e.altKey && !e.metaKey) { e.preventDefault(); search(e.key); }
}
function onKeyUp(e) {
  if ((e.key === 'Enter' || e.key === ' ') && enterDown) {
    clearTimeout(longT);
    enterDown = 0;
    if (!layerOpen() && !document.querySelector('.search-ov')) activate();
  }
}
function toggleFull() {
  if (document.fullscreenElement) document.exitFullscreen(); else document.documentElement.requestFullscreen().catch(() => {});
}

// Controle (Xbox/PlayStation) pelo Gamepad API: direcional/analógico, A abrir, B voltar, X opções, Y pesquisar, Start menu
const pad = { prev: {}, t: {}, raf: 0 };
function padLoop() {
  const gps = navigator.getGamepads ? [...navigator.getGamepads()].filter(Boolean) : [];
  if (!gps.length) { pad.raf = 0; return; }
  const gp = gps[0];
  const ax = gp.axes || [];
  const st = {
    up: gp.buttons[12]?.pressed || ax[1] < -0.6, down: gp.buttons[13]?.pressed || ax[1] > 0.6,
    left: gp.buttons[14]?.pressed || ax[0] < -0.6, right: gp.buttons[15]?.pressed || ax[0] > 0.6,
    a: gp.buttons[0]?.pressed, b: gp.buttons[1]?.pressed, x: gp.buttons[2]?.pressed, y: gp.buttons[3]?.pressed,
    start: gp.buttons[9]?.pressed, back: gp.buttons[8]?.pressed,
  };
  const now = performance.now();
  const keyMap = { up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight', a: 'Enter', b: 'Escape', x: 'ContextMenu', y: '/', start: 'Escape', back: ',' };
  for (const [k, on] of Object.entries(st)) {
    const was = pad.prev[k];
    const dirKey = ['up', 'down', 'left', 'right'].includes(k);
    if (on && (!was || (dirKey && now - (pad.t[k] || 0) > (pad.t[`${k}r`] ? 110 : 380)))) {
      pad.t[`${k}r`] = was;
      pad.t[k] = now;
      fire(keyMap[k], k === 'back');
    }
    if (!on) pad.t[`${k}r`] = false;
    pad.prev[k] = on;
  }
  pad.raf = requestAnimationFrame(padLoop);
}
function fire(key, ctrl) {
  const target = document.activeElement || document.body;
  if (document.querySelector('.search-ov') || layerOpen()) {
    if (key === 'Enter') { target.click?.(); return; }
    target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ctrlKey: !!ctrl }));
    return;
  }
  const ev = { key, repeat: false, preventDefault() {}, ctrlKey: !!ctrl, shiftKey: false, altKey: false, metaKey: false, target };
  onKey(ev);
  if (key === 'Enter') onKeyUp(ev);
}
window.addEventListener('gamepadconnected', () => { toast('Controle conectado 🎮', { ms: 2000 }); if (!pad.raf) pad.raf = requestAnimationFrame(padLoop); });

// ------------------------------------------------------------------ modo ocioso
let lastInput = Date.now();
let idleEl = null;
function wake() {
  lastInput = Date.now();
  if (idleEl) { idleEl.classList.remove('show'); document.body.classList.remove('idle-on'); const x = idleEl; idleEl = null; setTimeout(() => x.remove(), 800); }
}
function idleCheck() {
  const min = S.settings?.general.idleMin || 0;
  if (!min || idleEl || layerOpen()) return;
  if (Date.now() - lastInput > min * 60000) {
    idleEl = el('div.idle', S.settings.general.idleClock ? el('div', el('b.clk'), el('small.dt')) : null);
    document.body.append(idleEl);
    document.body.classList.add('idle-on');
    requestAnimationFrame(() => idleEl.classList.add('show'));
    drawStatus();
  }
}

// ------------------------------------------------------------------ início
async function refreshChannels() {
  const before = JSON.stringify(S.ch.rows);
  await loadChannels();
  if (JSON.stringify(S.ch.rows) !== before) render();
}
async function checkNewApps() {
  const before = new Set(S.apps.map((a) => a.key));
  let changed = false;
  try { changed = await loadApps(false); } catch { return; }
  if (S.status.scanning) watchScan();
  if (!changed) return;
  try { S.sections = (await api.get('/api/state')).sections; } catch { /* ok */ }
  const fresh = S.apps.filter((a) => !before.has(a.key));
  render();
  if (before.size && fresh.length && fresh.length <= 5) toast(`Novo app: ${fresh.map((a) => a.name).join(', ')}`, { type: 'ok', ms: 4000 });
}
async function refreshSystem() {
  try { S.sys = await api.get('/api/system'); } catch { /* ok */ }
  drawStatus();
}
export async function reloadAll() {
  await loadApps();
  const st = await api.get('/api/state');
  S.settings = st.settings; S.sections = st.sections; S.custom = st.custom; S.version = st.version; S.demo = st.demo; S.dataDir = st.dataDir; S.device = st.device;
  try { S.walls = (await api.get('/api/wallpapers')).items; } catch { S.walls = []; }
  applyWall(true);
  render();
}

async function boot() {
  try {
    await reloadAll();
  } catch (e) {
    document.getElementById('home').append(el('div.row-empty', `Não consegui abrir o Spectra: ${e.message}`));
    return;
  }
  loadChannels().then(render);
  refreshSystem();
  if (S.status.scanning || !S.status.scannedAt) watchScan();
  setInterval(drawStatus, 15000);
  setInterval(refreshSystem, 60000);
  setInterval(refreshChannels, 90000);
  // apps instalados agora (ex.: "Instalar app" no Chrome) aparecem sem reabrir o Spectra
  setInterval(checkNewApps, 8000);
  setInterval(idleCheck, 5000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) { refreshChannels(); checkNewApps(); } });
  window.addEventListener('focus', () => { refreshChannels(); checkNewApps(); });
  document.addEventListener('keydown', onKey);
  document.addEventListener('keyup', onKeyUp);
  document.addEventListener('mousemove', (e) => {
    wake();
    if (!mouseMode) { mouseMode = true; document.body.classList.remove('mouse-hide'); }
    mouseShift = [e.clientX / innerWidth - 0.5, e.clientY / innerHeight - 0.5];
    if (S.settings.wall.motion === 'parallax') parallax();
  });
  document.addEventListener('mousedown', wake);
  document.addEventListener('wheel', wake, { passive: true });
  window.addEventListener('resize', () => startFx(S.settings.wall.effect));
  if (navigator.getGamepads && [...navigator.getGamepads()].some(Boolean)) pad.raf = requestAnimationFrame(padLoop);
  if (!S.settings.onboarded) setTimeout(welcome, 600);
}

export { closeTopModal, modal };
boot();
