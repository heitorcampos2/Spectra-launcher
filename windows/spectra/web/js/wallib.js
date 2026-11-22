// 1.0.7: Biblioteca de wallpapers (igual ao Spectra do Android): Categorias, Classificação, Buscar e Meus wallpapers.
// Acervo público Wallhaven, só conteúdo seguro e em proporção de tela; a imagem escolhida é baixada em alta qualidade.
import { api } from './api.js';
import { btn, clear, h, modal, spinner, toast } from './ui.js';
import { S, applyWall, saveSettings } from './main.js';

const GROUPS = [['Destaques', '✨'], ['Natureza', '🌿'], ['Espaço e céu', '🌌'], ['Lugares', '🏙️'], ['Arte e estilo', '🎨'],
  ['Entretenimento', '🎮'], ['Veículos', '🏎️'], ['Animais', '🐯'], ['Estações e datas', '🍂']];

// [id, nome, emoji, busca, grupo]
const CATS = [
  ['top', 'Em alta', '🔥', null, 'Destaques'], ['dark', 'Dark / AMOLED', '🌑', 'dark minimalism', 'Destaques'],
  ['minimal', 'Minimalista', '◻️', 'minimalism', 'Destaques'], ['4k', 'Paisagens 4K', '🖼️', 'landscape 4k', 'Destaques'],
  ['colorful', 'Coloridos', '🌈', 'colorful', 'Destaques'], ['gradient', 'Degradês', '🟣', 'gradient', 'Destaques'],
  ['nature', 'Natureza', '🌿', 'nature landscape', 'Natureza'], ['ocean', 'Oceano', '🌊', 'ocean', 'Natureza'],
  ['mountains', 'Montanhas', '🏔️', 'mountains', 'Natureza'], ['forest', 'Florestas', '🌲', 'forest', 'Natureza'],
  ['beach', 'Praias', '🏖️', 'beach', 'Natureza'], ['waterfall', 'Cachoeiras', '💧', 'waterfall', 'Natureza'],
  ['lake', 'Lagos', '🏞️', 'lake', 'Natureza'], ['desert', 'Desertos', '🏜️', 'desert', 'Natureza'],
  ['sunset', 'Pôr do sol', '🌅', 'sunset', 'Natureza'], ['flowers', 'Flores', '🌺', 'flowers', 'Natureza'],
  ['rain', 'Chuva / Aconchego', '🌧️', 'rain', 'Natureza'],
  ['space', 'Espaço', '🌌', 'space', 'Espaço e céu'], ['galaxy', 'Galáxias', '🌀', 'galaxy', 'Espaço e céu'],
  ['planets', 'Planetas', '🪐', 'planet', 'Espaço e céu'], ['earth', 'Terra vista do espaço', '🌍', 'earth from space', 'Espaço e céu'],
  ['moon', 'Lua', '🌕', 'moon', 'Espaço e céu'], ['aurora', 'Aurora', '🌠', 'aurora', 'Espaço e céu'],
  ['stars', 'Céu estrelado', '✨', 'starry sky', 'Espaço e céu'], ['clouds', 'Nuvens', '☁️', 'clouds', 'Espaço e céu'],
  ['city', 'Cidades à noite', '🏙️', 'city night', 'Lugares'], ['skyline', 'Horizonte urbano', '🌆', 'skyline', 'Lugares'],
  ['architecture', 'Arquitetura', '🏛️', 'architecture', 'Lugares'], ['brazil', 'Brasil', '🇧🇷', 'brazil', 'Lugares'],
  ['japan', 'Japão', '⛩️', 'japan', 'Lugares'], ['europe', 'Europa', '🏰', 'europe', 'Lugares'],
  ['roads', 'Estradas', '🛣️', 'road', 'Lugares'], ['bridges', 'Pontes', '🌉', 'bridge', 'Lugares'],
  ['abstract', 'Abstrato', '🌀', 'abstract', 'Arte e estilo'], ['digital', 'Arte digital', '🎨', 'digital art', 'Arte e estilo'],
  ['fantasy', 'Fantasia', '🐉', 'fantasy art', 'Arte e estilo'], ['cyberpunk', 'Cyberpunk', '🌃', 'cyberpunk', 'Arte e estilo'],
  ['synthwave', 'Retrô / Synthwave', '🌇', 'synthwave', 'Arte e estilo'], ['neon', 'Neon', '💡', 'neon', 'Arte e estilo'],
  ['pixel', 'Pixel art', '👾', 'pixel art', 'Arte e estilo'], ['painting', 'Pintura', '🖌️', 'painting', 'Arte e estilo'],
  ['lowpoly', 'Low poly / geométrico', '🔷', 'low poly', 'Arte e estilo'], ['scifi', 'Ficção científica', '🚀', 'science fiction', 'Arte e estilo'],
  ['anime', 'Anime', '🌸', 'anime', 'Entretenimento'], ['games', 'Games', '🎮', 'video games', 'Entretenimento'],
  ['movies', 'Filmes', '🎬', 'movies', 'Entretenimento'], ['series', 'Séries', '📺', 'tv series', 'Entretenimento'],
  ['comics', 'Quadrinhos', '💥', 'comics', 'Entretenimento'], ['music', 'Música', '🎵', 'music', 'Entretenimento'],
  ['sports', 'Esportes', '⚽', 'sports', 'Entretenimento'],
  ['cars', 'Carros', '🏎️', 'cars', 'Veículos'], ['supercars', 'Superesportivos', '🏁', 'supercar', 'Veículos'],
  ['classic', 'Clássicos', '🚙', 'classic car', 'Veículos'], ['motorcycles', 'Motos', '🏍️', 'motorcycle', 'Veículos'],
  ['planes', 'Aviões', '✈️', 'aircraft', 'Veículos'], ['trains', 'Trens', '🚆', 'train', 'Veículos'],
  ['animals', 'Animais', '🐯', 'animals', 'Animais'], ['cats', 'Gatos', '🐱', 'cats', 'Animais'],
  ['dogs', 'Cachorros', '🐶', 'dogs', 'Animais'], ['birds', 'Pássaros', '🦜', 'birds', 'Animais'],
  ['wildlife', 'Vida selvagem', '🦁', 'wildlife', 'Animais'], ['sea', 'Vida marinha', '🐋', 'underwater', 'Animais'],
  ['autumn', 'Outono', '🍂', 'autumn', 'Estações e datas'], ['winter', 'Inverno / Neve', '❄️', 'winter snow', 'Estações e datas'],
  ['spring', 'Primavera', '🌷', 'spring', 'Estações e datas'], ['summer', 'Verão', '☀️', 'summer', 'Estações e datas'],
  ['christmas', 'Natal', '🎄', 'christmas', 'Estações e datas'], ['halloween', 'Halloween', '🎃', 'halloween', 'Estações e datas'],
];
const CAT_SORT = [['relevance', 'Relevância', '🎯'], ['top', 'Top do mês', '🏆'], ['new', 'Recentes', '🆕'], ['fav', 'Mais favoritados', '❤️'], ['random', 'Surpresa', '🎲']];
const RANKS = [['w', 'Top da semana', '🏆', 'toplist', '1w'], ['m', 'Top do mês', '🥇', 'toplist', '1M'], ['y', 'Top do ano', '👑', 'toplist', '1y'],
  ['fav', 'Mais favoritados', '❤️', 'favorites'], ['views', 'Mais vistos', '👁', 'views'], ['new', 'Recentes', '🆕', 'date_added'], ['random', 'Surpresa', '🎲', 'random']];
const RES = [['1920x1080', 'Full HD+'], ['2560x1440', '2K+'], ['3840x2160', '4K']];
const TABS = [['cat', 'Categorias', '🗂️'], ['rank', 'Classificação', '🏆'], ['search', 'Buscar', '🔍'], ['mine', 'Meus wallpapers', '🖼️']];

const st = { tab: 'cat', group: 'Destaques', cat: 'top', sort: 'relevance', rank: 'w', res: '1920x1080', theme: '', q: '' };
const seed = () => Math.random().toString(36).slice(2, 8);

function pill(label, emoji, on, fn) {
  return h('button.wl-pill', { type: 'button', class: on ? 'on' : '', onclick: fn }, emoji ? h('span.wl-em', emoji) : null, label);
}

export function openWallLibrary(onApplied) {
  const head = h('div.wl-head');
  const filters = h('div.wl-filters');
  const grid = h('div.wl-grid');
  const more = h('div.wl-more');
  const root = h('div.wl', head, filters, h('div.wl-scroll', grid, more));
  const m = modal(root, { cls: 'wl-modal', noFocus: true });
  let page = 1; let rseed = seed(); let token = 0;

  function params() {
    if (st.tab === 'cat') {
      const c = CATS.find((x) => x[0] === st.cat) || CATS[0];
      const p = { q: c[3] || '' };
      if (!c[3] && st.sort === 'relevance') Object.assign(p, { sorting: 'toplist', topRange: '1M' });
      else Object.assign(p, { relevance: { sorting: 'relevance' }, top: { sorting: 'toplist', topRange: '1M' }, new: { sorting: 'date_added' },
        fav: { sorting: 'favorites' }, random: { sorting: 'random', seed: rseed } }[st.sort]);
      return p;
    }
    if (st.tab === 'rank') {
      const r = RANKS.find((x) => x[0] === st.rank) || RANKS[0];
      const c = CATS.find((x) => x[0] === st.theme);
      return { sorting: r[3], topRange: r[4] || '', seed: rseed, atleast: st.res, q: c ? c[3] || '' : '' };
    }
    return { sorting: 'relevance', q: st.q };
  }

  async function load(reset) {
    const my = ++token;
    if (reset) { page = 1; clear(grid); }
    if (st.tab === 'mine') return mine();
    if (st.tab === 'search' && !st.q) { clear(more).append(h('p.wl-hint', 'Digite o que procura (ex.: dragão roxo, praia 4k, carros) e aperte Buscar.')); return; }
    clear(more).append(spinner('Carregando wallpapers…'));
    let r;
    try { r = await api.get(`/api/wallhaven?${new URLSearchParams({ ...params(), page })}`); } catch (e) {
      if (my !== token) return;
      clear(more).append(h('p.wl-hint', e.message), btn('Tentar de novo', { cls: 'ghost sm', onclick: () => load(false) }));
      return;
    }
    if (my !== token) return;
    r.items.forEach((it) => grid.append(thumb(it)));
    clear(more);
    if (!grid.children.length) more.append(h('p.wl-hint', 'Nada encontrado aqui. Tente outra categoria ou outra busca.'));
    else if (r.more) more.append(btn('Carregar mais', { cls: 'ghost', icon: 'chevD', onclick: () => { page += 1; load(false); } }));
    if (reset) grid.querySelector('button')?.focus({ preventScroll: true });
  }

  function thumb(it) {
    const b = h('button.wl-item', { type: 'button', title: `${it.w}×${it.h}`, onclick: () => preview(it) },
      h('img', { src: it.thumb, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer' }),
      h('span.wl-res', it.w >= 3840 ? '4K' : it.w >= 2560 ? '2K' : 'HD'));
    if (it.colors && it.colors[0]) b.style.background = it.colors[0];
    return b;
  }

  function preview(it) {
    const img = h('img.wl-full', { src: it.thumb, alt: '', referrerpolicy: 'no-referrer' });
    const big = new Image();
    big.referrerPolicy = 'no-referrer';
    big.onload = () => { img.src = it.full; };
    big.src = it.full;
    const p = modal(h('div.wl-prev', img,
      h('div.wl-prev-bar', h('span', `Wallhaven · ${it.w}×${it.h}`),
        btn('Aplicar', { cls: 'primary', icon: 'check', onclick: async () => {
          toast('Baixando em alta qualidade…', { ms: 2500 });
          try {
            const d = await api.post('/api/wallpaper/url', { url: it.full });
            await saveSettings({ wall: { type: 'image', image: d.id } });
            applyWall(true);
            toast('Wallpaper aplicado', { type: 'ok' });
            p.close();
            if (onApplied) onApplied();
          } catch (e) { toast(e.message, { type: 'error' }); }
        } }),
        btn('Voltar', { cls: 'ghost', icon: 'arrowL', onclick: () => p.close() }))), { cls: 'wl-prev-modal' });
  }

  async function mine() {
    clear(more);
    try { S.walls = (await api.get('/api/wallpapers')).items; } catch { /* ok */ }
    clear(grid);
    const cur = S.settings.wall;
    for (const w of S.walls || []) {
      const url = `/api/wallpaper/${encodeURIComponent(w.id)}`;
      const on = cur.type === 'image' && cur.image === w.id;
      const b = h('button.wl-item', { type: 'button', class: on ? 'on' : '', title: w.name, onclick: async () => {
        await saveSettings({ wall: { type: 'image', image: w.id } }); applyWall(true); toast('Wallpaper aplicado', { type: 'ok' }); mine();
      } }, w.type === 'video' ? h('video', { src: url, muted: true, preload: 'metadata' }) : h('img', { src: url, alt: '', loading: 'lazy' }),
      h('span.wl-res', on ? 'Em uso' : w.type === 'video' ? 'Vídeo' : w.name));
      grid.append(b);
    }
    if (!grid.children.length) more.append(h('p.wl-hint', 'Os wallpapers que você escolher aparecem aqui.'));
  }

  function draw() {
    clear(head).append(h('h2', 'Wallpapers'), h('div.wl-tabs', ...TABS.map(([id, l, e]) => pill(l, e, st.tab === id, () => { st.tab = id; draw(); load(true); }))),
      btn('Fechar', { cls: 'ghost', icon: 'close', onclick: () => m.close() }));
    clear(filters);
    const row = (...kids) => {
      const r = h('div.wl-row', ...kids);
      // rodinha do mouse rola a fileira de filtros para o lado
      r.addEventListener('wheel', (e) => { if (Math.abs(e.deltaY) > Math.abs(e.deltaX) && r.scrollWidth > r.clientWidth) { e.preventDefault(); r.scrollLeft += e.deltaY; } }, { passive: false });
      return r;
    };
    if (st.tab === 'cat') {
      filters.append(
        row(...GROUPS.map(([g, e]) => pill(g, e, st.group === g, () => { st.group = g; st.cat = CATS.find((c) => c[4] === g)[0]; draw(); load(true); }))),
        row(...CATS.filter((c) => c[4] === st.group).map((c) => pill(c[1], c[2], st.cat === c[0], () => { st.cat = c[0]; draw(); load(true); }))),
        row(...CAT_SORT.map(([id, l, e]) => pill(l, e, st.sort === id, () => { st.sort = id; if (id === 'random') rseed = seed(); draw(); load(true); }))));
    } else if (st.tab === 'rank') {
      filters.append(
        row(...RANKS.map(([id, l, e]) => pill(l, e, st.rank === id, () => { st.rank = id; if (id === 'random') rseed = seed(); draw(); load(true); })),
          h('span.wl-sep'), ...RES.map(([id, l]) => pill(l, '▣', st.res === id, () => { st.res = id; draw(); load(true); }))),
        row(pill('Todos os temas', '✦', !st.theme, () => { st.theme = ''; draw(); load(true); }),
          ...CATS.filter((c) => c[3]).map((c) => pill(c[1], c[2], st.theme === c[0], () => { st.theme = c[0]; draw(); load(true); }))));
    } else if (st.tab === 'search') {
      const inp = h('input.input', { type: 'search', placeholder: 'Buscar wallpapers (ex.: dragão roxo, praia 4k)', value: st.q, autocomplete: 'off' });
      const go = () => { st.q = inp.value.trim(); if (st.q.length < 2) { toast('Digite pelo menos 2 letras'); return; } load(true); };
      inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); go(); } });
      filters.append(row(inp, btn('Buscar', { cls: 'primary', icon: 'search', onclick: go }),
        btn('Google Imagens', { cls: 'ghost', icon: 'external', title: 'Abre o Google Imagens; copie o endereço da imagem e cole em "Endereço de uma imagem"',
          onclick: () => api.post('/api/open-url', { url: `https://www.google.com/search?tbm=isch&tbs=isz:l&q=${encodeURIComponent((inp.value.trim() || 'wallpaper') + ' wallpaper 4k')}` }) })));
      setTimeout(() => inp.focus(), 60);
    }
  }
  draw();
  load(true);
}
