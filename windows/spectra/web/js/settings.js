// Painel MENU, configurações (com prévia ao vivo), editores de seções e canais, adicionar apps
import { api } from './api.js';
import { btn, clear, closeTopModal, confirmBox, h, ic, menu, modal, promptBox, toast, toggle } from './ui.js';
import {
  CH, KIND_LABEL, S, appIcon, applyLook, applyWall, homeOrder, hue, launch, loadApps, openSintonia, reloadAll, render,
  saveSections, saveSettings, search, sectionApps, watchScan,
} from './main.js';

const el = h;
const EMOJIS = ['⭐', '📺', '🎬', '🍿', '🎮', '🌐', '🎵', '📻', '📡', '💼', '📁', '🧰', '🛠️', '📚', '✏️', '🖼️', '📷', '💬', '📧', '🗓️',
  '🏠', '🧪', '💻', '🖥️', '⚙️', '🔒', '🛒', '💰', '📈', '🧠', '🤖', '🐉', '🦊', '🐱', '🐶', '🌙', '☀️', '🔥', '❤️', '▦'];
const AVATARS = ['🐉', '🙂', '😎', '🦊', '🐱', '🐶', '🐼', '🦁', '🐯', '🐸', '👾', '🤖', '🎧', '🎮', '🌟', '👑', '🧑‍💻', '👩', '👨', '🧒'];
const COLORS = ['#3b82f6', '#2563eb', '#0ea5e9', '#06b6d4', '#14b8a6', '#10b981', '#22c55e', '#84cc16',
  '#eab308', '#f59e0b', '#f97316', '#ef4444', '#e11d48', '#ec4899', '#d946ef', '#a855f7',
  '#8b5cf6', '#6366f1', '#64748b', '#94a3b8', '#f43f5e', '#fb7185', '#fda4af', '#fbbf24',
  '#a3e635', '#4ade80', '#2dd4bf', '#38bdf8', '#818cf8', '#c084fc', '#f0abfc', '#ffffff'];
const TONES = [['grafite', 'Grafite'], ['amoled', 'AMOLED'], ['indigo', 'Índigo'], ['vinho', 'Vinho'], ['azul-noite', 'Azul-noite'],
  ['floresta', 'Floresta'], ['vidro', 'Vidro']];
const AUTO = [['', 'Nenhum (só os que você escolher)'], ['tv', 'TV e streaming (automático)'], ['chrome', 'Apps do Chrome e sites'],
  ['games', 'Jogos (automático)'], ['all', 'Todos os apps']];

// ================================================================== camadas (painel, configurações, janelas)
let panelEl = null;
let settingsEl = null;
export function layerOpen() {
  return !!(panelEl || settingsEl || document.querySelector('#overlay-root .modal-back'));
}
export function closeLayers() {
  if (closeTopModal()) return;
  if (settingsEl) { closeSettings(); return; }
  if (panelEl) closePanel();
}

/** Navegação espacial (setas/controle) dentro da camada de cima. */
export function moveFocusIn(container, dir) {
  const backs = document.querySelectorAll('#overlay-root .modal-back');
  const root = container || backs[backs.length - 1] || settingsEl || panelEl;
  if (!root) return;
  const all = [...root.querySelectorAll('button, input, select, textarea, a[href], summary, [tabindex="0"]')]
    .filter((x) => !x.disabled && x.offsetParent !== null && x.getClientRects().length);
  if (!all.length) return;
  const cur = document.activeElement;
  if (!root.contains(cur) || cur === root) { all[0].focus(); mark(all[0]); return; }
  const a = cur.getBoundingClientRect();
  const ax = a.left + a.width / 2; const ay = a.top + a.height / 2;
  let best = null; let bestD = Infinity;
  for (const x of all) {
    if (x === cur) continue;
    const b = x.getBoundingClientRect();
    const bx = b.left + b.width / 2; const by = b.top + b.height / 2;
    const dx = bx - ax; const dy = by - ay;
    let ok = false; let main = 0; let side = 0;
    if (dir === 'down') { ok = b.top >= a.bottom - 4 || dy > 8; main = dy; side = dx; }
    if (dir === 'up') { ok = b.bottom <= a.top + 4 || dy < -8; main = -dy; side = dx; }
    if (dir === 'right') { ok = b.left >= a.right - 4 || (dx > 8 && Math.abs(dy) < a.height); main = dx; side = dy; }
    if (dir === 'left') { ok = b.right <= a.left + 4 || (dx < -8 && Math.abs(dy) < a.height); main = -dx; side = dy; }
    if (!ok || main <= 0) continue;
    const d = main + Math.abs(side) * 2.2;
    if (d < bestD) { bestD = d; best = x; }
  }
  if (best) { best.focus(); mark(best); best.scrollIntoView({ block: 'nearest' }); }
}
function mark(x) {
  document.querySelectorAll('.f-ring').forEach((y) => y.classList.remove('f-ring'));
  x.classList.add('f-ring');
  x.addEventListener('blur', () => x.classList.remove('f-ring'), { once: true });
}

// ================================================================== painel MENU
export function openPanel() {
  if (panelEl) { closePanel(); return; }
  const back = el('div.panel-back', { onclick: closePanel });
  const sint = S.apps.find((a) => a.kind === 'sintonia');
  const item = (icon, label, sub, fn) => el('button.p-item', { type: 'button', onclick: () => { closePanel(); fn(); } },
    el('span.pi', icon), el('span', label, sub ? el('small', sub) : null));
  const list = el('div.panel-list',
    item('🔍', 'Pesquisar', 'Apps, canais, filmes ou na web', () => search()),
    item('➕', 'Adicionar app ou site', 'Programas do Windows, atalhos e sites', () => addAppDialog()),
    item('☰', 'Editar seções', 'Criar, ordenar e personalizar', () => openSettings('sections')),
    item('📺', 'Editar canais', 'Ver a seguir e canais do Sintonia', () => openSettings('channels')),
    item('🖼️', 'Wallpaper e fundo', null, () => openSettings('wall')),
    item('🎨', 'Cores e tema', null, () => openSettings('colors')),
    item('⚙️', 'Configurações', null, () => openSettings()),
    el('div.p-sep'),
    sint ? item('📡', 'Abrir o Sintonia', 'TV ao vivo, filmes e séries', () => openSintonia('')) : item('📡', 'Instalar o Sintonia', 'Sugestão: player de IPTV do mesmo criador', () => openSettings('updates')),
    item('⟳', 'Atualizações', `Versão ${S.version}`, () => openSettings('updates')),
    item('🔄', 'Atualizar lista de apps', S.status.count ? `${S.status.count} apps encontrados` : null, async () => { await loadApps(true); watchScan(); toast('Procurando apps do Windows…'); }),
    item('⛶', 'Tela cheia', 'F11', () => { if (document.fullscreenElement) document.exitFullscreen(); else document.documentElement.requestFullscreen().catch(() => {}); }),
    item('⏻', 'Fechar o Spectra', null, async () => {
      if (await confirmBox('Fechar o Spectra? Os apps abertos continuam abertos.', { ok: 'Fechar' })) {
        await api.post('/api/quit').catch(() => {});
        window.close();
        document.body.innerHTML = '<p style="padding:40px;color:#fff;font-size:20px">Spectra fechado. Pode fechar esta janela.</p>';
      }
    }));
  panelEl = el('aside.panel', { role: 'dialog', 'aria-label': 'Menu' },
    el('div.panel-head', el('img', { src: '/img/logo.png', alt: '' }), el('div', el('b', 'Spectra'), el('small', `para Windows · ${S.version}`))), list);
  panelEl.back = back;
  document.body.append(back, panelEl);
  requestAnimationFrame(() => { back.classList.add('show'); panelEl.classList.add('show'); });
  setTimeout(() => { const f = list.querySelector('button'); f?.focus(); if (f) mark(f); }, 60);
}
function closePanel() {
  if (!panelEl) return;
  const p = panelEl; panelEl = null;
  p.classList.remove('show'); p.back.classList.remove('show');
  setTimeout(() => { p.remove(); p.back.remove(); }, 250);
}

// ================================================================== configurações
const CATS = [
  ['profile', 'Perfil e título', '✦', 'Nome, avatar, título e emoji da tela inicial.'],
  ['look', 'Cartões', '▭', 'Estilo, tamanho e cantos dos cartões.'],
  ['colors', 'Cores', '🎨', 'Cor de destaque, cor automática do wallpaper e tom dos painéis.'],
  ['font', 'Fonte', 'A', 'Família da fonte, negrito, itálico e tamanho do texto.'],
  ['layout', 'Layout e foco', '▤', 'Cabeçalhos à esquerda, nomes das seções, espaçamentos, margens, opacidade e foco.'],
  ['status', 'Barra de status', '📶', 'Pesquisa, rede, bateria, engrenagem, relógio e avatar no canto superior direito.'],
  ['wall', 'Wallpaper e fundo', '🖼', 'Imagem, vídeo, papel de parede do Windows, cor ou degradê, movimento e efeitos.'],
  ['sections', 'Seções', '☰', 'Criar, ordenar e personalizar as seções da tela inicial.'],
  ['channels', 'Canais', '📺', 'Ver a seguir e os canais do Sintonia como fileiras próprias.'],
  ['general', 'Geral', '⚙', 'Modo ocioso e relógio.'],
  ['perf', 'Desempenho', '⚡', 'Animações e itens por fileira.'],
  ['startup', 'Inicialização e janela', '🏠', 'Abrir com o Windows, tela cheia e lista de apps.'],
  ['updates', 'Atualizações', '⟳', 'Versão mais nova do Spectra no GitHub e sugestão do Sintonia.'],
  ['backup', 'Backup', '💾', 'Salve ou restaure todas as configurações em um arquivo.'],
  ['about', 'Sobre', 'ℹ', 'Spectra para Windows por Heitor Campos (github.com/heitorcampos2).'],
];
let curCat = 'profile';
let mainEl = null;

export function openSettings(cat) {
  if (panelEl) closePanel();
  if (cat) curCat = cat;
  if (settingsEl) { drawCat(); return; }
  const cats = el('nav.set-cats', el('h2', 'Configurações'),
    ...CATS.map(([id, label, icon]) => el('button.set-cat', { type: 'button', dataset: { id }, onclick: () => { curCat = id; drawCat(); } }, el('i', icon), label)));
  mainEl = el('div.set-main');
  settingsEl = el('aside.settings', { role: 'dialog', 'aria-label': 'Configurações' }, cats, mainEl);
  document.body.append(settingsEl);
  requestAnimationFrame(() => settingsEl.classList.add('show'));
  drawCat();
  setTimeout(() => { const b = cats.querySelector('.set-cat.on'); b?.focus(); if (b) mark(b); }, 80);
}
function closeSettings() {
  if (!settingsEl) return;
  const s = settingsEl; settingsEl = null;
  s.classList.remove('show');
  setTimeout(() => s.remove(), 250);
  render();
}

function drawCat() {
  settingsEl.querySelectorAll('.set-cat').forEach((b) => b.classList.toggle('on', b.dataset.id === curCat));
  const [, label, , desc] = CATS.find((c) => c[0] === curCat) || CATS[0];
  const keep = mainEl.scrollTop;
  clear(mainEl);
  mainEl.append(el('div.set-main-head', el('h2', label), el('button.icon-btn', { type: 'button', title: 'Fechar (Esc)', onclick: closeSettings }, ic('close'))),
    el('p.set-desc', desc));
  const page = PAGES[curCat];
  if (page) page(mainEl);
  mainEl.scrollTop = keep;
}
function redraw() { if (settingsEl) drawCat(); }

// --- helpers de linha ---------------------------------------------------------
function get(path) { return path.split('.').reduce((o, k) => (o == null ? o : o[k]), S.settings); }
function patchFor(path, value) {
  const keys = path.split('.');
  const out = {};
  let o = out;
  keys.forEach((k, i) => { if (i === keys.length - 1) o[k] = value; else { o[k] = {}; o = o[k]; } });
  return out;
}
async function set(path, value, opts = {}) {
  try {
    await saveSettings(patchFor(path, value));
  } catch (e) { toast(e.message, { type: 'error', ms: 5000 }); }
  if (path.startsWith('wall.') || path.startsWith('perf.')) applyWall();
  render();
  if (opts.redraw) redraw();
}
const group = (title, ...rows) => el('section.set-group', title ? el('h3', title) : null, ...rows);
function row(label, sub, control) {
  return el('div.set-row', el('div.set-txt', el('span', label), sub ? el('small', sub) : null), control || null);
}
function toggleRow(label, path, sub) { return row(label, sub, toggle(get(path), (v) => set(path, v), label)); }
function seg(label, path, options, sub, opts) {
  const box = el('div.seg');
  const draw = () => {
    clear(box);
    options.forEach(([v, l]) => box.append(el('button', { type: 'button', class: get(path) === v ? 'on' : '', onclick: async () => { await set(path, v, opts); draw(); } }, l)));
  };
  draw();
  return row(label, sub, box);
}
function stepper(label, path, min, max, step, unit = '', sub) {
  const val = el('b');
  const show = () => { val.textContent = `${get(path)}${unit}`; };
  show();
  const ch = async (d) => { const v = Math.max(min, Math.min(max, (Number(get(path)) || 0) + d)); await set(path, v); show(); };
  return row(label, sub, el('div.stepper', el('button', { type: 'button', 'aria-label': 'menos', onclick: () => ch(-step) }, '−'), val,
    el('button', { type: 'button', 'aria-label': 'mais', onclick: () => ch(step) }, '+')));
}
function textRow(label, path, placeholder) {
  const inp = el('input.input', { value: get(path) || '', placeholder: placeholder || '', style: { maxWidth: '260px', height: '44px' } });
  inp.addEventListener('change', () => set(path, inp.value));
  return row(label, null, inp);
}
function colorRow(label, path, allowEmpty) {
  const inp = el('input.color-in', { type: 'color', value: get(path) || '#3b82f6' });
  inp.addEventListener('change', () => set(path, inp.value));
  return row(label, null, el('div', { style: { display: 'flex', gap: '8px', alignItems: 'center' } }, inp,
    allowEmpty ? btn('Padrão', { cls: 'ghost sm', onclick: () => { set(path, ''); } }) : null));
}
function actionRow(label, sub, fn, icon = 'chevR') {
  return el('button.set-row.action', { type: 'button', onclick: fn }, el('div.set-txt', el('span', label), sub ? el('small', sub) : null), ic(icon));
}
function emojiGrid(list, cur, onPick) {
  const box = el('div.emojis');
  list.forEach((e) => box.append(el('button', { type: 'button', class: e === cur ? 'on' : '', onclick: () => {
    box.querySelectorAll('.on').forEach((x) => x.classList.remove('on'));
    box.querySelector(`[data-e="${CSS.escape(e)}"]`)?.classList.add('on');
    onPick(e);
  }, dataset: { e } }, e)));
  return box;
}

// --- páginas ------------------------------------------------------------------
const PAGES = {
  profile(m) {
    m.append(group('Perfil',
      textRow('Seu nome', 'profile.name', 'Ex.: Heitor'),
      row('Avatar', null, null), el('div', { style: { padding: '0 22px 14px' } }, emojiGrid(AVATARS, get('profile.avatar'), (e) => set('profile.avatar', e))),
      toggleRow('Mostrar avatar no topo', 'profile.showAvatar')),
    group('Título da tela inicial',
      toggleRow('Mostrar título', 'profile.showTitle'),
      textRow('Texto do título', 'profile.title', 'Spectra'),
      textRow('Emoji do título', 'profile.titleEmoji', 'Ex.: 🐉'),
      toggleRow('Título em negrito', 'profile.titleBold'),
      stepper('Tamanho do título', 'profile.titleSize', 18, 64, 2, ' px'),
      colorRow('Cor do título', 'profile.titleColor', true)));
  },
  look(m) {
    m.append(group('Cartões',
      seg('Estilo dos cartões', 'look.cardStyle', [['card', 'Cartão'], ['icon', 'Só ícone'], ['banner', 'Banner'], ['round', 'Redondo']]),
      seg('Tamanho dos cartões', 'look.cardSize', [['P', 'P'], ['M', 'M'], ['G', 'G'], ['GG', 'GG']]),
      stepper('Cantos arredondados', 'look.radius', 0, 40, 2, ' px'),
      toggleRow('Nome do app só ao focar', 'look.nameOnFocus', 'Mostra o nome embaixo do cartão só no selecionado')));
  },
  colors(m) {
    const sw = el('div.colors');
    const drawSw = () => {
      clear(sw);
      COLORS.forEach((c) => sw.append(el('button', { type: 'button', title: c, style: { '--c': c }, class: get('colors.accent') === c ? 'on' : '',
        onclick: async () => { await set('colors.accent', c); drawSw(); } })));
    };
    drawSw();
    m.append(group('Cor de destaque', sw, colorRow('Cor personalizada', 'colors.accent'),
      toggleRow('Cor de destaque automática', 'colors.autoAccent', 'Usa a cor mais viva do wallpaper')),
    group('Menus e painéis',
      seg('Tom dos painéis', 'colors.tone', TONES),
      colorRow('Cor dos menus e painéis', 'colors.panel', true)));
  },
  font(m) {
    m.append(group('Fonte',
      seg('Família', 'font.family', [['Figtree', 'Figtree'], ['Inter', 'Inter'], ['Poppins', 'Poppins'], ['Nunito', 'Nunito'], ['Montserrat', 'Montserrat'], ['Roboto', 'Roboto'], ['Segoe UI', 'Segoe UI']]),
      toggleRow('Negrito (audacioso)', 'font.bold'),
      toggleRow('Itálico', 'font.italic'),
      stepper('Tamanho do texto', 'font.size', 80, 150, 5, '%')));
  },
  layout(m) {
    m.append(group('Seções',
      toggleRow('Cabeçalhos à esquerda', 'layout.leftHeaders', 'Ícones das seções na lateral, com ⚙ configurar e ▲▼ mover'),
      toggleRow('Exibir o nome das seções', 'layout.sectionNames'),
      toggleRow('Exibir ícone antes do nome', 'layout.sectionIcons'),
      stepper('Espaçamento vertical', 'layout.rowGap', 0, 80, 2, ' px')),
    group('Foco',
      toggleRow('Manter o foco em posição fixa', 'layout.fixedFocus', 'O cartão selecionado fica sempre no início da fileira'),
      toggleRow('Ocultar fileiras acima da seleção', 'layout.hideAbove'),
      stepper('Opacidade das fileiras não selecionadas', 'layout.dimOthers', 0, 90, 5, '% mais claro')),
    group('Margens',
      stepper('Margem inicial', 'layout.marginLeft', 20, 160, 4, ' px'),
      stepper('Espaçamento dos cartões', 'layout.cardGap', 4, 48, 2, ' px')));
  },
  status(m) {
    m.append(group('Itens do canto superior direito',
      toggleRow('Botão de busca', 'status.search'),
      toggleRow('Rede / Wi-Fi', 'status.net'),
      toggleRow('Bateria (notebooks)', 'status.battery'),
      toggleRow('Engrenagem (configurações)', 'status.gear'),
      toggleRow('Relógio', 'status.clock'),
      toggleRow('Data', 'status.date'),
      toggleRow('Avatar do perfil', 'status.avatar'),
      toggleRow('Ocultar barra ao rolar', 'status.hideOnScroll')));
  },
  async wall(m) {
    const grid = el('div.walls');
    const drawWalls = async () => {
      try { S.walls = (await api.get('/api/wallpapers')).items; } catch { /* ok */ }
      clear(grid);
      for (const w of S.walls) {
        const url = `/api/wallpaper/${encodeURIComponent(w.id)}`;
        const b = el('button.wall', { type: 'button', class: get('wall.type') === 'image' && get('wall.image') === w.id ? 'on' : '',
          style: w.type === 'image' ? { backgroundImage: `url("${url}")` } : null,
          onclick: async () => { await saveSettings({ wall: { type: 'image', image: w.id } }); applyWall(); drawWalls(); } },
        w.type === 'video' ? el('video', { src: url, muted: true, preload: 'metadata' }) : null, el('span', w.name),
        w.id.startsWith('file:') ? el('span.del', { role: 'button', title: 'Apagar', onclick: async (e) => {
          e.stopPropagation();
          await api.del(`/api/wallpaper/${encodeURIComponent(w.id)}`);
          if (get('wall.image') === w.id) { await saveSettings({ wall: { image: 'paisagem' } }); applyWall(); }
          drawWalls();
        } }, '×') : null);
        grid.append(b);
      }
    };
    const file = el('input', { type: 'file', accept: 'image/*,video/mp4,video/webm', hidden: true });
    file.addEventListener('change', async () => {
      const f = file.files[0];
      if (!f) return;
      toast('Enviando o wallpaper…');
      try {
        const res = await fetch(`/api/wallpaper/upload?name=${encodeURIComponent(f.name)}`, { method: 'POST', body: f, headers: { 'Content-Type': 'application/octet-stream' } });
        const d = await res.json();
        if (!res.ok) throw new Error(d.error || 'Erro');
        await saveSettings({ wall: { type: 'image', image: d.id } });
        await drawWalls();
        applyWall();
        toast('Wallpaper aplicado', { type: 'ok' });
      } catch (e) { toast(e.message, { type: 'error' }); }
      file.value = '';
    });
    const urlIn = el('input.input', { placeholder: 'Endereço de uma imagem (https://…)' });
    m.append(
      group('Escolher wallpaper', grid,
        el('div.inline-form', btn('Imagem ou vídeo do PC…', { cls: 'primary sm', icon: 'upload', onclick: () => file.click() }),
          btn('Procurar no Google Imagens', { cls: 'ghost sm', icon: 'external', onclick: () => api.post('/api/open-url', { url: 'https://www.google.com/search?tbm=isch&tbs=isz:l&q=wallpaper+4k+paisagem' }) })),
        el('div.inline-form', urlIn, btn('Usar', { cls: 'sm', onclick: async () => {
          try {
            const d = await api.post('/api/wallpaper/url', { url: urlIn.value.trim() });
            await saveSettings({ wall: { type: 'image', image: d.id } });
            urlIn.value = '';
            await drawWalls(); applyWall();
          } catch (e) { toast(e.message, { type: 'error' }); }
        } })), file),
      group('Fundo',
        seg('Tipo de fundo', 'wall.type', [['image', 'Imagem/vídeo'], ['color', 'Cor sólida'], ['gradient', 'Degradê']]),
        colorRow('Cor de fundo 1', 'wall.color1'), colorRow('Cor de fundo 2 (degradê)', 'wall.color2')),
      group('Movimento e efeitos',
        seg('Movimento', 'wall.motion', [['none', 'Parado'], ['parallax', 'Parallax'], ['pan', 'Movimento suave']]),
        stepper('Intensidade do parallax', 'wall.parallax', 0, 100, 5, '%'),
        seg('Efeito por cima da imagem', 'wall.effect', [['none', 'Nenhum'], ['particles', 'Partículas'], ['snow', 'Neve']]),
        stepper('Escurecimento', 'wall.dim', 0, 85, 5, '%'),
        toggleRow('Desfoque suave', 'wall.blur'),
        seg('Trocar a cada', 'wall.rotateMin', [[0, 'Nunca'], [5, '5 min'], [15, '15 min'], [30, '30 min'], [60, '1 h']], 'Alterna entre as imagens da lista')));
    drawWalls();
  },
  sections(m) {
    const list = el('div.ed-list');
    const draw = () => {
      clear(list);
      const order = homeOrder();
      order.forEach((id, i) => {
        const isSec = id.startsWith('sec:');
        const key = id.slice(id.indexOf(':') + 1);
        const sec = isSec ? S.sections.find((s) => s.id === key) : null;
        const chn = !isSec ? S.settings.channels.find((c) => c.id === key) : null;
        if (!sec && !chn) return;
        const on = isSec ? sec.visible : chn.on;
        const count = isSec ? sectionApps(sec).length : (S.ch.rows[key] || []).length;
        const sub = isSec ? `${sec.auto ? AUTO.find((a) => a[0] === sec.auto)?.[1].split(' (')[0] : 'Manual'} · ${count} apps` : `Canal · ${count} itens`;
        list.append(el('div.ed-item', { class: on ? '' : 'off' },
          el('span.ei', isSec ? sec.icon : CH[key].icon), el('div.et', el('b', isSec ? sec.name : CH[key].name), el('small', sub)),
          toggle(on, async (v) => {
            if (isSec) { sec.visible = v; await saveSections(); } else { await saveSettings({ channels: S.settings.channels.map((c) => (c.id === key ? { ...c, on: v } : c)) }); render(); }
            draw();
          }, 'Visível'),
          el('button.icon-btn', { type: 'button', title: 'Subir', disabled: i === 0 || null, onclick: () => reorder(order, i, -1).then(draw) }, '▲'),
          el('button.icon-btn', { type: 'button', title: 'Descer', disabled: i === order.length - 1 || null, onclick: () => reorder(order, i, 1).then(draw) }, '▼'),
          isSec ? el('button.icon-btn', { type: 'button', title: 'Configurar', onclick: () => configureSection(sec, draw) }, ic('sliders')) : el('span', { style: { width: '36px' } })));
      });
    };
    draw();
    m.append(el('div.modal-actions', { style: { justifyContent: 'flex-start', marginTop: 0, marginBottom: '14px' } },
      btn('Nova seção', { cls: 'primary sm', icon: 'plus', onclick: () => newSection().then(draw) }),
      btn('Adicionar app ou site', { cls: 'sm', icon: 'plus', onclick: () => addAppDialog() }),
      btn('Restaurar seções padrão', { cls: 'ghost sm', onclick: async () => {
        if (!(await confirmBox('Voltar às seções padrão? As seções criadas por você serão apagadas.', { ok: 'Restaurar', danger: true }))) return;
        S.sections = await api.post('/api/sections/reset');
        render(); draw();
      } })), list);
  },
  channels(m) {
    const list = el('div.ed-list');
    const draw = () => {
      clear(list);
      S.settings.channels.forEach((c) => {
        const n = (S.ch.rows[c.id] || []).length;
        list.append(el('div.ed-item', { class: c.on ? '' : 'off' }, el('span.ei', CH[c.id]?.icon || '▦'),
          el('div.et', el('b', CH[c.id]?.name || c.id), el('small', `${CH[c.id]?.sub || ''} · ${n} itens agora`)),
          toggle(c.on, async (v) => { await saveSettings({ channels: S.settings.channels.map((x) => (x.id === c.id ? { ...x, on: v } : x)) }); render(); draw(); }, 'Mostrar')));
      });
    };
    draw();
    const st = S.ch.sintonia || {};
    m.append(group('Canais na tela inicial', el('div', { style: { padding: '6px 16px 12px' } }, list),
      el('p.hint', { style: { margin: '0 22px 12px' } }, 'A posição de cada canal na tela inicial muda em Seções (▲▼) ou pelos botões ▲▼ da lateral.')),
    group('Sintonia',
      row('Situação', st.ok ? 'Conectado: os canais se atualizam sozinhos' : (st.error || 'Procurando o Sintonia…'), st.ok ? el('span.val', '✔') : btn('Abrir o Sintonia', { cls: 'sm', onclick: () => openSintonia('') })),
      textRow('Endereço do Sintonia', 'sintonia.url', 'http://127.0.0.1:8787'),
      textRow('Senha do Sintonia (se tiver)', 'sintonia.password', '')));
  },
  general(m) {
    m.append(group('Modo ocioso',
      seg('Depois de', 'general.idleMin', [[0, 'Desligado'], [1, '1 min'], [5, '5 min'], [10, '10 min'], [30, '30 min']], 'Mostra só o wallpaper e um relógio grande'),
      toggleRow('Relógio grande no modo ocioso', 'general.idleClock')),
    group('Relógio', toggleRow('Formato 24 horas', 'general.clock24')));
  },
  perf(m) {
    m.append(group('Desempenho',
      toggleRow('Animações', 'perf.animations', 'Desligue em PCs mais lentos'),
      stepper('Itens por fileira nos canais', 'perf.perRow', 5, 60, 5)));
  },
  startup(m) {
    const st = S.status || {};
    m.append(group('Windows',
      toggleRow('Abrir o Spectra junto com o Windows', 'general.startWithWindows', 'Cria um atalho na pasta Inicializar do Windows'),
      toggleRow('Abrir em tela cheia', 'general.fullscreen', 'Vale na próxima vez que o Spectra abrir (F11 alterna agora)'),
      actionRow('Tela cheia agora', 'F11', () => { if (document.fullscreenElement) document.exitFullscreen(); else document.documentElement.requestFullscreen().catch(() => {}); }, 'fullscreen')),
    group('Apps do Windows',
      row('Apps encontrados', st.scanning ? 'Procurando…' : (st.scannedAt ? `Lista lida em ${new Date(st.scannedAt * 1000).toLocaleString('pt-BR')}` : ''), el('span.val', String(st.count || 0))),
      st.error ? el('p.hint', { style: { margin: '0 22px 10px', color: 'var(--warn)' } }, st.error) : null,
      actionRow('Atualizar lista de apps', 'Lê de novo o Menu Iniciar, os apps da Store e os apps do Chrome/Edge', async () => { await loadApps(true); watchScan(); toast('Procurando apps…'); setTimeout(redraw, 3000); }, 'refresh'),
      actionRow('Adicionar app ou site', 'Programa (.exe), atalho ou site', () => addAppDialog(), 'plus')),
    group('Spectra',
      actionRow('Fechar o Spectra', 'Os apps abertos continuam abertos', async () => {
        if (await confirmBox('Fechar o Spectra?', { ok: 'Fechar' })) { await api.post('/api/quit').catch(() => {}); window.close(); }
      }, 'close')));
  },
  async updates(m) {
    const box = el('div', el('p.hint', { style: { margin: '6px 22px 12px' } }, 'Procurando no GitHub…'));
    m.append(group(`Spectra para Windows ${S.version}`, box), sintoniaCard());
    try {
      const u = await api.get('/api/update/check?force=1');
      clear(box);
      if (u.available) {
        box.append(el('p', { style: { margin: '6px 22px' } }, 'Versão nova: ', el('b', u.latest), u.date ? ` (${u.date.split('-').reverse().join('/')})` : ''),
          u.notes?.length ? el('ul', { style: { margin: '6px 22px 6px 40px' } }, ...u.notes.map((n) => el('li', n))) : null,
          el('div.inline-form', btn('Atualizar agora', { cls: 'primary sm', icon: 'download', onclick: async (e) => {
            e.currentTarget.disabled = true;
            e.currentTarget.textContent = 'Baixando…';
            try {
              const r = await api.post('/api/update/apply');
              toast(`Atualizado para a ${r.version}. Reabrindo…`, { ms: 6000 });
              setTimeout(waitReload, 2500);
            } catch (err) { toast(err.message, { type: 'error', ms: 6000 }); redraw(); }
          } }), btn('Ver no GitHub', { cls: 'ghost sm', onclick: () => api.post('/api/open-url', { url: u.page }) })));
      } else {
        box.append(el('p.hint', { style: { margin: '6px 22px 12px' } }, u.error || `Você já está na versão mais nova (${u.current}).`),
          el('div.inline-form', btn('Ver no GitHub', { cls: 'ghost sm', icon: 'external', onclick: () => api.post('/api/open-url', { url: u.page }) })));
      }
    } catch (e) { clear(box); box.append(el('p.hint', { style: { margin: '6px 22px 12px' } }, e.message)); }
  },
  backup(m) {
    const file = el('input', { type: 'file', accept: '.json,application/json', hidden: true });
    file.addEventListener('change', async () => {
      const f = file.files[0];
      if (!f) return;
      try {
        const data = JSON.parse(await f.text());
        await api.post('/api/restore', data);
        await reloadAll();
        toast('Configurações restauradas', { type: 'ok' });
        redraw();
      } catch (e) { toast(e.message || 'Arquivo inválido', { type: 'error' }); }
      file.value = '';
    });
    m.append(group('Backup',
      actionRow('Salvar backup', 'Baixa um arquivo .json com seções, canais, aparência e atalhos', async () => {
        const data = await api.get('/api/backup');
        const a = el('a', { href: URL.createObjectURL(new Blob([JSON.stringify(data, null, 1)], { type: 'application/json' })),
          download: `spectra-backup-${new Date().toISOString().slice(0, 10)}.json` });
        document.body.append(a); a.click(); setTimeout(() => a.remove(), 500);
      }, 'download'),
      actionRow('Restaurar backup', 'Escolha um arquivo salvo antes', () => file.click(), 'upload'), file));
  },
  about(m) {
    m.append(el('section.set-group', el('div', { style: { display: 'flex', gap: '18px', alignItems: 'center', padding: '16px 22px' } },
      el('img', { src: '/img/logo.png', alt: '', style: { width: '76px', height: '76px', borderRadius: '18px' } }),
      el('div', el('h3', { style: { padding: 0, fontSize: '22px' } }, 'Spectra para Windows'), el('p', `Versão ${S.version}`),
        el('p', 'Desenvolvido por ', el('b', 'Heitor Campos'), ' · ', el('a', { href: '#', onclick: (e) => { e.preventDefault(); api.post('/api/open-url', { url: 'https://github.com/heitorcampos2' }); } }, 'github.com/heitorcampos2'))))),
    group('Atalhos do teclado e do controle', ...[
      ['Setas / direcional', 'Navegar'], ['Enter / A', 'Abrir'], ['Segurar Enter, botão direito, tecla Menu / X', 'Opções do cartão'],
      ['Esc / B / Start', 'Menu lateral e voltar'], ['/ ou digitar / Y', 'Pesquisar'], ['Ctrl + , / Back', 'Configurações'], ['F11', 'Tela cheia'],
      ['Home / End / PgUp / PgDn', 'Primeira, última e pular fileiras'],
    ].map(([k, d]) => row(d, null, el('kbd', { style: { fontSize: '13px' } }, k)))),
    group('Informações técnicas', row('Pasta de dados', S.dataDir || '', null), row('Computador', S.device || '', null)));
  },
};

async function waitReload() {
  for (let i = 0; i < 60; i += 1) {
    await new Promise((r) => setTimeout(r, 1500));
    try { await api.get('/api/state'); location.reload(); return; } catch { /* reabrindo */ }
  }
}

function sintoniaCard() {
  const sint = S.apps.find((a) => a.kind === 'sintonia');
  return el('section.set-group', el('h3', 'Sugestão: Sintonia'),
    el('div', { style: { display: 'flex', gap: '16px', alignItems: 'center', padding: '6px 22px 14px' } },
      el('img', { src: '/img/sintonia.png', alt: '', style: { width: '56px', height: '56px', borderRadius: '14px' } }),
      el('div', { style: { flex: 1 } }, el('p', 'Player de IPTV do mesmo criador: TV ao vivo, filmes e séries. O que você assiste nele aparece aqui em "Ver a seguir".'),
        el('small.muted', sint ? 'Instalado neste PC.' : 'Não instalado neste PC.'))),
    el('div.inline-form',
      sint ? btn('Abrir o Sintonia', { cls: 'primary sm', icon: 'play', onclick: () => openSintonia('') }) : null,
      btn(sint ? 'Instalar versão mais nova' : 'Instalar o Sintonia', { cls: sint ? 'sm' : 'primary sm', icon: 'download', onclick: async (e) => {
        const b = e.currentTarget;
        b.disabled = true; b.lastChild.textContent = 'Baixando…';
        try {
          const r = await api.post('/api/sintonia/install');
          toast(`Instalador do Sintonia ${r.version} aberto: siga a janela do instalador.`, { ms: 8000 });
          setTimeout(() => { loadApps(true); watchScan(); }, 20000);
        } catch (err) { toast(err.message, { type: 'error', ms: 7000 }); }
        b.disabled = false; b.lastChild.textContent = sint ? 'Instalar versão mais nova' : 'Instalar o Sintonia';
      } }),
      btn('Ver no GitHub', { cls: 'ghost sm', onclick: () => api.post('/api/open-url', { url: 'https://github.com/heitorcampos2/Sintonia' }) })));
}

async function reorder(order, i, d) {
  const j = i + d;
  if (j < 0 || j >= order.length) return;
  [order[i], order[j]] = [order[j], order[i]];
  await saveSettings({ homeOrder: order });
  render();
}

async function newSection() {
  const name = await promptBox('Nome da seção', { title: 'Nova seção', placeholder: 'Ex.: Trabalho, Estudos, Streaming' });
  if (!name) return;
  const sec = { id: `s${Date.now().toString(36)}`, name: name.trim(), icon: '📁', auto: '', apps: [], visible: true };
  S.sections.push(sec);
  await saveSections();
  const order = homeOrder();
  await saveSettings({ homeOrder: order });
  configureSection(S.sections.find((s) => s.id === sec.id) || sec);
}

// ================================================================== seção: configuração lateral
export function configureSection(sec, after) {
  const s = JSON.parse(JSON.stringify(sec));
  const name = el('input.input', { value: s.name });
  const segIn = (label, key, opts) => {
    const box = el('div.seg');
    const draw = () => { clear(box); opts.forEach(([v, l]) => box.append(el('button', { type: 'button', class: (s[key] ?? '') === v ? 'on' : '', onclick: () => { s[key] = v; draw(); } }, l))); };
    draw();
    return row(label, null, box);
  };
  const tog = (label, key) => row(label, null, toggle(s[key], (v) => { s[key] = v; }, label));
  const body = el('div',
    el('label.field-label', 'Nome'), name,
    el('label.field-label', { style: { display: 'block', marginTop: '14px' } }, 'Ícone'), emojiGrid(EMOJIS, s.icon, (e) => { s.icon = e; }),
    el('div', { style: { marginTop: '10px' } },
      tog('Visível na tela inicial', 'visible'),
      tog('Exibir o nome da seção', 'showName'),
      segIn('Disposição', 'layout', [['row', 'Linha deslizante'], ['grid', 'Grade']]),
      segIn('Tamanho', 'size', [['', 'Padrão'], ['P', 'P'], ['M', 'M'], ['G', 'G'], ['GG', 'GG']]),
      segIn('Estilo', 'style', [['', 'Padrão'], ['card', 'Cartão'], ['icon', 'Ícone'], ['banner', 'Banner'], ['round', 'Redondo']]),
      segIn('Ordenar', 'sort', [['manual', 'Manual'], ['az', 'A–Z'], ['recent', 'Mais usados']]),
      segIn('Conteúdo automático', 'auto', AUTO.map(([v, l]) => [v, l.split(' (')[0]]))));
  let m;
  const save = async () => {
    s.name = name.value.trim() || s.name;
    const i = S.sections.findIndex((x) => x.id === s.id);
    if (i >= 0) S.sections[i] = s; else S.sections.push(s);
    await saveSections();
    m.close();
    if (after) after();
    redraw();
  };
  body.append(el('div.modal-actions', { style: { justifyContent: 'space-between' } },
    el('div', { style: { display: 'flex', gap: '8px', flexWrap: 'wrap' } },
      btn('Gerenciar apps', { cls: 'sm', icon: 'listCheck', onclick: async () => { await save(); appPicker(S.sections.find((x) => x.id === s.id)); } }),
      btn('Usar tamanho e estilo em todas', { cls: 'ghost sm', title: 'Definir como padrão', onclick: async () => {
        await saveSettings({ look: { cardSize: s.size || S.settings.look.cardSize, cardStyle: s.style || S.settings.look.cardStyle } });
        S.sections.forEach((x) => { x.size = ''; x.style = ''; });
        s.size = ''; s.style = '';
        await save();
        toast('Aplicado a todas as seções', { type: 'ok' });
      } }),
      btn('Excluir seção', { cls: 'ghost sm', icon: 'trash', onclick: async () => {
        if (!(await confirmBox(`Excluir a seção "${s.name}"? Os apps continuam instalados.`, { ok: 'Excluir', danger: true }))) return;
        S.sections = S.sections.filter((x) => x.id !== s.id);
        await saveSections();
        m.close();
        if (after) after();
        redraw();
      } })),
    btn('Salvar', { cls: 'primary', onclick: save })));
  m = modal(body, { title: `Seção ${s.icon} ${s.name}`, cls: 'wide' });
}

// ================================================================== escolher apps de uma seção
export function appPicker(sec) {
  if (!sec) return;
  const inSec = new Set(sectionApps(sec).map((a) => a.key));
  const q = el('input.input', { type: 'search', placeholder: 'Procurar app…' });
  const grid = el('div.app-pick');
  const filter = el('div.seg');
  let kind = '';
  const kinds = [['', 'Todos'], ['chrome', 'Chrome/Edge'], ['store', 'Store'], ['win', 'Programas'], ['custom', 'Adicionados']];
  const drawFilter = () => { clear(filter); kinds.forEach(([v, l]) => filter.append(el('button', { type: 'button', class: kind === v ? 'on' : '', onclick: () => { kind = v; drawFilter(); draw(); } }, l))); };
  const norm = (x) => (x || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  const draw = () => {
    clear(grid);
    const t = norm(q.value.trim());
    S.apps.filter((a) => (!t || norm(a.name).includes(t)) && (!kind || a.kind === kind || (kind === 'chrome' && ['edge', 'brave'].includes(a.kind)) || (kind === 'win' && a.kind === 'sintonia')))
      .sort((a, b) => (inSec.has(b.key) - inSec.has(a.key)) || a.name.localeCompare(b.name, 'pt'))
      .forEach((a) => {
        const b = el('button', { type: 'button', class: inSec.has(a.key) ? 'on' : '', title: `${a.name} · ${KIND_LABEL[a.kind] || ''}`, onclick: () => {
          if (inSec.has(a.key)) inSec.delete(a.key); else inSec.add(a.key);
          b.classList.toggle('on', inSec.has(a.key));
        } }, appIcon(a), el('span', a.name));
        grid.append(b);
      });
  };
  q.addEventListener('input', draw);
  drawFilter(); draw();
  let m;
  const save = async () => {
    const auto = new Set(sectionApps({ ...sec, apps: [], hidden: [], order: [] }).map((a) => a.key));
    sec.apps = [...inSec].filter((k) => !auto.has(k));
    sec.hidden = [...auto].filter((k) => !inSec.has(k));
    if (!sec.auto) sec.order = [...(sec.order || []).filter((k) => inSec.has(k)), ...sec.apps.filter((k) => !(sec.order || []).includes(k))];
    await saveSections();
    m.close();
    toast(`${sec.name}: ${inSec.size} apps`, { type: 'ok' });
    redraw();
  };
  m = modal(el('div', el('div.pick-tools', q, filter,
    btn('Adicionar programa ou site…', { cls: 'sm', icon: 'plus', onclick: () => { m.close(); addAppDialog(sec); } })), grid,
  el('div.modal-actions', btn('Cancelar', { cls: 'ghost', onclick: () => m.close() }), btn('Salvar', { cls: 'primary', onclick: save }))),
  { title: `Apps em ${sec.icon} ${sec.name}`, cls: 'big', noFocus: true });
  setTimeout(() => q.focus(), 60);
}

// ================================================================== adicionar programa (.exe) ou site
export function addAppDialog(target) {
  let type = 'exe';
  const name = el('input.input', { placeholder: 'Nome (opcional)' });
  const path = el('input.input', { placeholder: 'C:\\Programas\\app.exe ou um atalho .lnk' });
  const args = el('input.input', { placeholder: 'Argumentos (opcional)' });
  const url = el('input.input', { placeholder: 'https://www.youtube.com' });
  let win = true;
  const secSel = el('select.input');
  S.sections.forEach((s) => secSel.append(el('option', { value: s.id, selected: (target ? s.id === target.id : s.id === 'fav') || null }, `${s.icon} ${s.name}`)));
  const tabs = el('div.seg');
  const form = el('div.form-fields');
  const draw = () => {
    clear(tabs);
    [['exe', 'Programa do Windows'], ['url', 'Site como app']].forEach(([v, l]) => tabs.append(el('button', { type: 'button', class: type === v ? 'on' : '', onclick: () => { type = v; draw(); } }, l)));
    clear(form);
    if (type === 'exe') {
      form.append(el('div.field', el('label.field-label', 'Programa'), el('div', { style: { display: 'flex', gap: '8px' } }, path,
        btn('Procurar…', { cls: 'sm', onclick: async () => {
          try { const r = await api.post('/api/pick', { kind: 'exe' }); if (r.path) { path.value = r.path; if (!name.value) name.value = r.path.split('\\').pop().replace(/\.(exe|lnk|bat|cmd|url)$/i, ''); } } catch (e) { toast(e.message, { type: 'error' }); }
        } }))), el('div.field', el('label.field-label', 'Argumentos'), args));
    } else {
      form.append(el('div.field', el('label.field-label', 'Endereço do site'), url),
        row('Abrir em janela própria (como app)', 'Sem barra do navegador, igual aos apps do Chrome', toggle(win, (v) => { win = v; }, 'Janela própria')));
    }
    form.append(el('div.field', el('label.field-label', 'Nome'), name), el('div.field', el('label.field-label', 'Colocar na seção'), secSel));
  };
  draw();
  let m;
  const add = async () => {
    try {
      const it = await api.post('/api/custom', type === 'exe' ? { type, path: path.value, args: args.value, name: name.value } : { type, url: url.value, name: name.value, window: win });
      const sec = S.sections.find((s) => s.id === secSel.value);
      if (sec) { sec.apps = [...new Set([...(sec.apps || []), it.key])]; }
      await loadApps();
      await saveSections();
      m.close();
      toast(`${it.name} adicionado`, { type: 'ok' });
      setTimeout(async () => { await loadApps(); render(); }, 4000);
      redraw();
    } catch (e) { toast(e.message, { type: 'error', ms: 5000 }); }
  };
  m = modal(el('div', tabs, form, el('p.hint', 'Os apps do Menu Iniciar, da Microsoft Store e do Chrome/Edge já aparecem sozinhos: use isto para programas soltos (.exe) e sites.'),
    el('div.modal-actions', btn('Cancelar', { cls: 'ghost', onclick: () => m.close() }), btn('Adicionar', { cls: 'primary', onclick: add }))),
  { title: 'Adicionar app ou site', cls: 'wide' });
}

// ================================================================== trocar o ícone de um app
export function pickIconFor(a) {
  const file = el('input', { type: 'file', accept: 'image/*' });
  file.addEventListener('change', async () => {
    const f = file.files[0];
    if (!f) return;
    const img = new Image();
    img.onload = async () => {
      const c = document.createElement('canvas');
      const n = 256; c.width = n; c.height = n;
      const g = c.getContext('2d');
      const k = Math.min(n / img.width, n / img.height);
      g.drawImage(img, (n - img.width * k) / 2, (n - img.height * k) / 2, img.width * k, img.height * k);
      try {
        await api.post(`/api/icon/${a.key}`, { data: c.toDataURL('image/png') });
        await loadApps(); S.iconRev = Date.now();
        render();
        toast('Ícone trocado', { type: 'ok' });
      } catch (e) { toast(e.message, { type: 'error' }); }
    };
    img.src = URL.createObjectURL(f);
  });
  menu([{ label: 'Escolher imagem…', icon: 'upload', fn: () => file.click() },
    { label: 'Voltar ao ícone original', icon: 'replay', fn: async () => { await api.del(`/api/icon/${a.key}`); S.iconRev = Date.now(); render(); } }], { title: `Ícone de ${a.name}` });
}

// ================================================================== boas-vindas
export function welcome() {
  let m;
  const t1 = toggle(false, (v) => { start = v; }, 'Abrir com o Windows');
  const t2 = toggle(false, (v) => { full = v; }, 'Tela cheia');
  let start = false; let full = false;
  m = modal(el('div.wel', el('img', { src: '/img/logo.png', alt: '' }), el('h2', 'Bem-vindo ao Spectra'),
    el('p', { style: { color: 'var(--text-2)', textAlign: 'center' } }, 'A tela inicial do Spectra Launcher, agora no PC.'),
    el('ul',
      el('li', 'Seus apps do Windows, da Microsoft Store e os apps instalados pelo Chrome/Edge já aparecem nas seções.'),
      el('li', 'O Sintonia mostra "Ver a seguir", canais favoritos, filmes e séries como fileiras (igual ao Android TV).'),
      el('li', 'Use setas, Enter, botão direito, mouse ou controle (Xbox/PlayStation). Esc abre o menu.')),
    row('Abrir junto com o Windows', null, t1), row('Abrir em tela cheia', null, t2),
    el('div.modal-actions', btn('Começar', { cls: 'primary', onclick: async () => {
      await saveSettings({ onboarded: true, general: { startWithWindows: start, fullscreen: full } }).catch((e) => toast(e.message, { type: 'error' }));
      m.close();
      render();
    } }))), { title: '', locked: true });
}
