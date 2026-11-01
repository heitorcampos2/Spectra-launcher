import { icon } from './icons.js';

// ---------- criação de elementos ----------
export function h(tag, attrs, ...kids) {
  const m = tag.match(/^([a-z0-9-]+)?((?:[.#][\w-]+)*)$/i);
  const el = document.createElement(m[1] || 'div');
  (m[2].match(/[.#][\w-]+/g) || []).forEach((t) => {
    if (t[0] === '.') el.classList.add(t.slice(1));
    else el.id = t.slice(1);
  });
  if (attrs && (typeof attrs !== 'object' || attrs instanceof Node || Array.isArray(attrs))) {
    kids.unshift(attrs);
    attrs = null;
  }
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (v === null || v === undefined || v === false) continue;
      if (k === 'class') el.className += (el.className ? ' ' : '') + v;
      else if (k === 'style' && typeof v === 'object') {
        // variáveis CSS (--c, --h) precisam de setProperty; Object.assign as ignora
        for (const [sk, sv] of Object.entries(v)) {
          if (sk.startsWith('--')) el.style.setProperty(sk, String(sv)); else el.style[sk] = sv;
        }
      }
      else if (k === 'html') el.innerHTML = v;
      else if (k === 'dataset') Object.assign(el.dataset, v);
      else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
      else if (v === true) el.setAttribute(k, '');
      else el.setAttribute(k, v);
    }
  }
  append(el, kids);
  return el;
}

export function append(el, kids) {
  for (const k of kids.flat(Infinity)) {
    if (k === null || k === undefined || k === false) continue;
    el.append(k instanceof Node ? k : document.createTextNode(String(k)));
  }
  return el;
}

export function ic(name, cls = '') {
  const span = document.createElement('span');
  span.className = 'icw';
  span.innerHTML = icon(name, cls);
  return span;
}

export function btn(label, opts = {}) {
  const b = h('button.btn', { class: opts.cls || '', type: 'button', title: opts.title || null, onclick: opts.onclick,
    disabled: opts.disabled || null, 'aria-label': opts.aria || opts.title || null });
  if (opts.icon) b.append(ic(opts.icon, opts.iconCls || ''));
  if (label) b.append(h('span', label));
  return b;
}

export function iconBtn(name, title, onclick, cls = '') {
  return h('button.icon-btn', { type: 'button', title, 'aria-label': title, onclick, class: cls }, ic(name, cls.includes('fav-on') ? 'filled' : ''));
}

export function clear(el) {
  while (el.firstChild) el.firstChild.remove();
  return el;
}

// ---------- imagens com cache do servidor ----------
export function b64url(s) {
  const bytes = new TextEncoder().encode(s);
  let bin = '';
  bytes.forEach((b) => { bin += String.fromCharCode(b); });
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function imgUrl(url) {
  if (!url) return '';
  if (url.startsWith('data:') || url.startsWith('/')) return url;
  return '/img/' + b64url(url);
}

export function initials(name) {
  const words = String(name || '?').replace(/[^\p{L}\p{N} ]/gu, ' ').trim().split(/\s+/).filter(Boolean);
  return ((words[0] || '?')[0] + (words[1] ? words[1][0] : '')).toUpperCase();
}

export function hueOf(text) {
  let x = 0;
  for (const ch of String(text)) x = (x * 31 + ch.charCodeAt(0)) % 360;
  return x;
}

// Sites de capa que não respondem: depois da primeira demora, as próximas capas dele vão direto pelo
// servidor do Sintonia (que guarda em cache e desiste rápido), sem deixar a tela esperando.
const SLOW_HOSTS = new Set();
const IMG_WAIT_MS = 4000;

/** Capas do TMDB vêm enormes (600×900 ou original); miniatura carrega muitas vezes mais rápido. */
export function thumbUrl(url, size = 'w342') {
  return String(url).replace(/(image\.tmdb\.org\/t\/p\/)(original|w\d+(?:_and_h\d+(?:_bestv2)?)?)\//i, `$1${size}/`);
}

function hostOf(url) {
  try { return new URL(url).host; } catch { return ''; }
}

export function pic(url, name, cls = '', { size = 'w342' } = {}) {
  const box = h('div.pic', { class: cls });
  const ph = h('span.pic-ph', { style: { '--h': hueOf(name) } }, initials(name));
  box.append(ph);
  if (url) {
    const full = /^https?:\/\//i.test(url) ? thumbUrl(url, size) : url;
    const host = hostOf(full);
    // carrega direto do provedor (não ocupa as conexões do Sintonia, que ficam livres para o vídeo);
    // se falhar ou demorar, tenta pelo cache do servidor
    const direct = /^https?:\/\//i.test(full) && !SLOW_HOSTS.has(host);
    const im = h('img', { loading: 'lazy', decoding: 'async', alt: '', referrerpolicy: 'no-referrer', src: direct ? full : imgUrl(full) });
    let viaProxy = !direct;
    let timer = null;
    const toProxy = () => {
      clearTimeout(timer);
      if (viaProxy) { im.remove(); return; }
      viaProxy = true;
      im.src = imgUrl(full);
    };
    im.addEventListener('load', () => { clearTimeout(timer); box.classList.add('loaded'); });
    im.addEventListener('error', toProxy);
    if (direct) {
      // conta o tempo só quando a capa entra na tela (loading=lazy)
      const io = new IntersectionObserver((ents) => {
        if (!ents.some((e) => e.isIntersecting)) return;
        io.disconnect();
        timer = setTimeout(() => {
          if (im.complete && im.naturalWidth) return;
          SLOW_HOSTS.add(host);
          toProxy();
        }, IMG_WAIT_MS);
      }, { rootMargin: '200px' });
      requestAnimationFrame(() => io.observe(box));
    }
    box.append(im);
  }
  return box;
}

// ---------- formatação ----------
export function fmtTime(ts, s24 = true) {
  const d = new Date(ts * 1000);
  return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', hour12: !s24 });
}

export function fmtDate(ts) {
  return new Date(ts * 1000).toLocaleDateString('pt-BR');
}

export function fmtDur(sec) {
  sec = Math.max(0, Math.floor(sec || 0));
  const h_ = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  const mm = String(m).padStart(h_ ? 2 : 1, '0');
  return (h_ ? h_ + ':' : '') + mm + ':' + String(s).padStart(2, '0');
}

export function fmtBytes(n) {
  if (!n) return '0 MB';
  const u = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return n.toFixed(i >= 2 ? 1 : 0).replace('.', ',') + ' ' + u[i];
}

export function norm(s) {
  return String(s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
}

export function debounce(fn, ms = 200) {
  let t;
  return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
}

// ---------- avisos ----------
export function toast(msg, opts = {}) {
  const root = document.getElementById('toasts');
  const el = h('div.toast', { class: opts.type || '', role: 'status' }, h('span', msg));
  if (opts.action) {
    el.append(h('button.toast-act', { type: 'button', onclick: () => { opts.action.fn(); el.remove(); } }, opts.action.label));
  }
  root.append(el);
  requestAnimationFrame(() => el.classList.add('show'));
  setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, opts.ms || 3200);
  return el;
}

// ---------- janelas ----------
const stack = [];

// Em tela cheia só aparece o que está dentro do elemento em tela cheia: leva as janelas e avisos
// para dentro do player (senão ficam escondidos atrás do vídeo) e devolve ao sair.
document.addEventListener('fullscreenchange', () => {
  const host = document.fullscreenElement || document.body;
  ['overlay-root', 'toasts'].forEach((id) => {
    const el = document.getElementById(id);
    if (el && el.parentNode !== host) host.append(el);
  });
});

export function modal(content, opts = {}) {
  const root = document.getElementById('overlay-root');
  const back = h('div.modal-back', { class: opts.sheet ? 'sheet' : '' });
  const box = h('div.modal', { class: opts.cls || '', role: 'dialog', 'aria-modal': 'true' });
  if (opts.title) {
    box.append(h('div.modal-head', h('h3', opts.title), iconBtn('close', 'Fechar', () => close())));
  }
  const body = h('div.modal-body');
  append(body, [content]);
  box.append(body);
  back.append(box);
  root.append(back);
  requestAnimationFrame(() => back.classList.add('show'));
  let done = false;
  function close(val) {
    if (done) return;
    done = true;
    back.classList.remove('show');
    setTimeout(() => back.remove(), 200);
    const i = stack.indexOf(close);
    if (i >= 0) stack.splice(i, 1);
    if (opts.onClose) opts.onClose(val);
  }
  back.addEventListener('click', (e) => { if (e.target === back && !opts.locked) close(); });
  stack.push(close);
  const f = box.querySelector('input, select, textarea, button.primary');
  if (f && !opts.noFocus) setTimeout(() => f.focus(), 50);
  return { close, box, body };
}

export function closeTopModal() {
  const c = stack[stack.length - 1];
  if (c) { c(); return true; }
  return false;
}

/** Menu de opções. items: [{label, icon, fn, danger, checked, disabled}] ou null para separador */
export function menu(items, opts = {}) {
  return new Promise((resolve) => {
    const list = h('div.menu-list');
    let m;
    for (const it of items) {
      if (!it) { list.append(h('div.menu-sep')); continue; }
      list.append(h('button.menu-item', {
        type: 'button', class: (it.danger ? 'danger ' : '') + (it.checked ? 'checked' : ''), disabled: it.disabled || null,
        onclick: () => { m.close(); resolve(it); if (it.fn) it.fn(); },
      }, it.icon ? ic(it.icon) : h('span.icw'), h('span.menu-label', it.label, it.sub ? h('small', it.sub) : null),
      it.checked ? ic('check', 'chk') : null));
    }
    m = modal(list, { title: opts.title, sheet: true, cls: 'menu-modal', onClose: () => resolve(null), noFocus: true });
  });
}

export function confirmBox(message, opts = {}) {
  return new Promise((resolve) => {
    let ok = false;
    const m = modal(h('div',
      h('p.confirm-msg', message),
      h('div.modal-actions',
        btn(opts.cancel || 'Cancelar', { cls: 'ghost', onclick: () => m.close() }),
        btn(opts.ok || 'Confirmar', { cls: opts.danger ? 'danger' : 'primary', onclick: () => { ok = true; m.close(); } }))),
    { title: opts.title, onClose: () => resolve(ok) });
  });
}

export function promptBox(label, opts = {}) {
  return new Promise((resolve) => {
    let val = null;
    const input = h('input.input', { type: opts.type || 'text', value: opts.value || '', placeholder: opts.placeholder || '',
      inputmode: opts.inputmode || null, maxlength: opts.maxlength || null, autocomplete: 'off' });
    const submit = () => { val = input.value; m.close(); };
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
    const m = modal(h('div', h('label.field-label', label), input, opts.hint ? h('p.hint', opts.hint) : null,
      h('div.modal-actions', btn('Cancelar', { cls: 'ghost', onclick: () => m.close() }),
        btn(opts.ok || 'Salvar', { cls: 'primary', onclick: submit }))),
    { title: opts.title, onClose: () => resolve(val) });
  });
}

export function pinBox(title = 'Digite o PIN') {
  return promptBox('PIN do controle parental', { title, type: 'password', inputmode: 'numeric', maxlength: 8, ok: 'Desbloquear' });
}

// ---------- listas grandes renderizadas por partes ----------
export function chunked(container, items, render, opts = {}) {
  const size = opts.size || 60;
  let i = 0;
  let obs = null;
  const sentinel = h('div.sentinel');
  function more() {
    const frag = document.createDocumentFragment();
    const batch = items.slice(i, i + size);
    batch.forEach((it, k) => frag.append(render(it, i + k)));
    i += batch.length;
    container.append(frag);
    if (opts.onBatch) opts.onBatch(batch);
    if (i >= items.length) { if (obs) obs.disconnect(); sentinel.remove(); }
    else if (opts.inside) container.append(sentinel);
    else container.after(sentinel);
  }
  more();
  if (i < items.length) {
    obs = new IntersectionObserver((ents) => { if (ents.some((e) => e.isIntersecting)) more(); },
      { root: opts.root || null, rootMargin: '800px' });
    obs.observe(sentinel);
  }
  return { destroy() { if (obs) obs.disconnect(); sentinel.remove(); } };
}

export function empty(text, sub, action) {
  return h('div.empty', ic('signal', 'empty-ic'), h('p', text), sub ? h('small', sub) : null, action || null);
}

export function spinner(text) {
  return h('div.loading', h('span.spin'), text ? h('span', text) : null);
}

export function toggle(checked, onchange, label) {
  const input = h('input', { type: 'checkbox', role: 'switch', 'aria-label': label || null });
  input.checked = !!checked;
  input.addEventListener('change', () => onchange(input.checked));
  return h('label.switch', input, h('span.slider'));
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const ta = h('textarea', { style: { position: 'fixed', opacity: 0 } });
    ta.value = text;
    document.body.append(ta);
    ta.select();
    document.execCommand('copy');
    ta.remove();
  }
  toast('Copiado');
}

export function downloadText(name, text, type = 'audio/x-mpegurl') {
  const a = h('a', { href: URL.createObjectURL(new Blob([text], { type })), download: name });
  document.body.append(a);
  a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
}
