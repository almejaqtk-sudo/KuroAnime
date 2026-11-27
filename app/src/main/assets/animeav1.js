// AnimeAV1 (módulo integrado en KuroAnime) - versión 4 (episodio 0 para películas, sin Mega, Mp4upload/HLS primero)
const BASE_URL = 'https://animeav1.com';

/* ---------- FILTROS (los mismos que tiene la web en /catalogo) ---------- */
// Parámetro de URL que usa la web para cada filtro: /catalogo?genre=accion&status=emision&category=tv-anime&page=2
const FILTER_PARAM = { genre: 'genre', type: 'category', status: 'status' };

const slugify = (s) =>
  s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[()]/g, '').trim().replace(/\s+/g, '-');

// Géneros tal como los muestra la web. El valor (v) es el "slug" de la URL.
// Si algún género no devuelve resultados, corrige aquí su slug: { v: 'slug-real', t: 'Nombre' }
const GENRE_NAMES = [
  'Acción', 'Antropomórfico', 'Artes Marciales', 'Aventura', 'Carreras', 'Ciencia Ficción', 'Comedia',
  'Deportes', 'Detectives', 'Drama', 'Elenco adulto', 'Escolares', 'Espacial', 'Fantasía', 'Gastronomía',
  'Gore', 'Harem', 'Histórico', 'Idols (Hombre)', 'Idols (Mujer)', 'Isekai', 'Josei', 'Juegos',
  'Mahou Shoujo', 'Mecha', 'Militar', 'Misterio', 'Mitología', 'Música', 'Parodia', 'Psicológico',
  'Recuentos de la vida', 'Romance', 'Samurai', 'Seinen', 'Shoujo', 'Shounen', 'Sobrenatural',
  'Superpoderes', 'Suspenso', 'Terror', 'Vampiros', 'Yaoi', 'Yuri'
];

const FILTERS = [
  { id: 'genre', label: 'Género', multi: false, options: GENRE_NAMES.map((n) => ({ v: slugify(n), t: n })) },
  {
    id: 'type', label: 'Tipo', multi: false,
    options: [
      { v: 'tv-anime', t: 'TV Anime' }, { v: 'pelicula', t: 'Película' }, { v: 'ova', t: 'OVA' },
      { v: 'ona', t: 'ONA' }, { v: 'especial', t: 'Especial' }
    ]
  },
  {
    id: 'status', label: 'Estado', multi: false,
    options: [
      { v: 'emision', t: 'En emisión' }, { v: 'finalizado', t: 'Finalizado' }, { v: 'proximamente', t: 'Próximamente' }
    ]
  }
];

const isStaticAsset = (u) => /\.(js|css|png|jpe?g|gif|webp|svg|ico|woff2?|ttf|map)(\?|$)/i.test(String(u || ''));

const isNotFound = (e) => !!e && (e.status === 404 || /\b404\b/.test(String(e.message || '')));

/* ---------- SERVIDORES ---------- */
const FAST_SERVERS = [
  { priority: 0, name: 'Mp4upload', match: /mp4upload/i, type: 'embed' },
  { priority: 1, name: 'HLS', match: /zilla-networks/i, type: 'embed' },
  { priority: 2, name: 'Streamwish', match: /(streamwish|wishembed|hglink|swiftplayers|alions|filelions|vidhide|dhtpre|hlswish)/i, type: 'embed' },
  { priority: 5, name: 'Okru', match: /(ok\.ru|od\.sdk|okcdn)/i, type: 'embed' },
  { priority: 6, name: 'YourUpload', match: /yourupload/i, type: 'embed' },
  { priority: 7, name: 'Filemoon', match: /(filemoon|moonplayer|kerapoxy)/i, type: 'embed' },
  { priority: 8, name: 'Streamtape', match: /(streamtape|strtape|strcloud)/i, type: 'embed' },
  { priority: 9, name: 'Doodstream', match: /(dood|ds2play|d0000d|do7go)/i, type: 'embed' }
];

// Si el host no se reconoce, se usa el nombre que la web le pone al servidor (si está en esta lista)
const NAMED_SERVERS = {
  mp4upload: 0, hls: 1, streamwish: 2, okru: 5, yourupload: 6,
  filemoon: 7, streamtape: 8, doodstream: 9
};

const BLOCKED_SERVERS = [
  // Mega no carga en la app: fuera
  /(mega\.nz|mega\.io|mega\.co\.nz)/i,
  // No cargan en la app: fuera (UPNShare, Byse, Voe)
  /(upnshare|upns\.pro|upn\.one|uns\.bio)/i,
  /\/\/[a-z0-9.-]*byse[a-z0-9-]*\.[a-z]{2,}/i,
  /\/\/(?:[a-z0-9-]+\.)*voe(?:-[a-z0-9-]+)?\.[a-z]{2,}(?:[\/:?#]|$)/i,
  /(fembed|feurl|\/\/fe\.|ncdn|streamango|openload|rapidvideo|verystream|clipwatching|jetload|mixdrop|upstream|uqload|sbplay|sbembed|streamsb|embedrise|animeav1\.com\/(assets|static|img|js|css))/i
];

function classifyFastServer(rawUrl, nameHint) {
  const url = String(rawUrl || '').trim();
  if (!url || !/^https?:\/\//i.test(url)) return null;

  for (const blocked of BLOCKED_SERVERS) {
    if (blocked.test(url)) return null;
  }

  for (const srv of FAST_SERVERS) {
    if (srv.match.test(url)) {
      return {
        priority: srv.priority,
        label: srv.name,
        url,
        type: srv.type
      };
    }
  }

  if (/\.m3u8(\?|$)/i.test(url)) {
    return { priority: 1, label: 'HLS Rápido', url, type: 'hls' };
  }

  if (/\.mp4(\?|$)/i.test(url) && !/(thumb|poster|preview|cover|ads|promo|sample)/i.test(url)) {
    return { priority: 0, label: 'MP4 Rápido', url, type: 'mp4' };
  }

  // Host desconocido pero la web lo llama con un nombre conocido (UPNShare, Byse, Voe...)
  const hint = String(nameHint || '').toLowerCase().replace(/[^a-z0-9]/g, '');
  if (hint && Object.prototype.hasOwnProperty.call(NAMED_SERVERS, hint) && !/\.(js|css|png|jpe?g|gif|webp|svg|ico|woff2?)(\?|$)/i.test(url)) {
    const nm = Object.keys(NAMED_SERVERS).find((k) => k === hint);
    const label = { mp4upload: 'Mp4upload', hls: 'HLS', streamwish: 'Streamwish', okru: 'Okru',
      yourupload: 'YourUpload', filemoon: 'Filemoon', streamtape: 'Streamtape', doodstream: 'Doodstream' }[nm];
    return { priority: NAMED_SERVERS[nm], label, url, type: hint === 'hls' && /\.m3u8/i.test(url) ? 'hls' : 'embed' };
  }

  return null;
}


// Texto de contexto (atributos, clases y texto corto del elemento y sus padres) para que la app detecte "sub" / "latino" / "español"
function ctxLang(el) {
  let t = '';
  let n = el;
  for (let i = 0; i < 4 && n && n.getAttribute; i++) {
    t += ' ' + ['data-lang', 'data-audio', 'data-language', 'data-idioma', 'aria-label', 'title']
      .map((a) => n.getAttribute(a) || '').join(' ');
    if (i < 2) {
      const own = (n.textContent || '').replace(/\s+/g, ' ').trim();
      if (own.length <= 40) t += ' ' + own;
    }
    n = n.parentElement;
  }
  return t;
}

function pickText(doc, selectors) {
  for (const sel of selectors) {
    const el = doc.querySelector(sel);
    if (el && el.textContent && el.textContent.trim()) {
      return el.textContent.trim();
    }
  }
  return '';
}

function pickAttr(doc, selectors, attr) {
  for (const sel of selectors) {
    const el = doc.querySelector(sel);
    const val = el ? el.getAttribute(attr) : '';
    if (val && val.trim()) {
      return val.trim();
    }
  }
  return '';
}

// Portada real: prueba los atributos "lazy" antes que src y descarta marcadores de posición
function imgUrl(img, baseUrl) {
  if (!img) return '';
  const bad = (u) => !u || /^data:/i.test(u) || /(placeholder|blank|loading|lazy\.|noimage|default)/i.test(u);
  const fromSet = (v) => String(v || '').trim().split(/\s+/)[0];
  const cand = ['data-src', 'data-lazy-src', 'data-original', 'data-lazy', 'data-thumb', 'data-poster']
    .map((a) => img.getAttribute(a) || '')
    .concat([fromSet(img.getAttribute('data-srcset')), img.getAttribute('src') || '', fromSet(img.getAttribute('srcset'))]);
  const raw = cand.find((u) => !bad(u)) || '';
  return raw ? api.abs(raw, baseUrl) : '';
}

function parseCardList(doc, baseUrl) {
  const cardSelectors = [
    'article',
    '.anime-card',
    // AnimeAV1 usa /media/ para las fichas del catálogo (no /anime/).
    '.grid a[href*="/media/"]',
    'a[href*="/media/"]',
    // Compatibilidad con versiones antiguas del sitio.
    '.grid a[href*="/anime/"]',
    'a[href*="/anime/"]'
  ];

  let cards = [];
  for (const sel of cardSelectors) {
    const found = Array.from(doc.querySelectorAll(sel));
    if (found.length > 0) {
      cards = found;
      break;
    }
  }

  const seen = new Set();
  const items = [];

  for (const card of cards) {
    const link = card.matches('a')
      ? card
      : card.querySelector('a[href*="/media/"]') ||
        card.querySelector('a[href*="/anime/"]') ||
        card.querySelector('a');
    const href = link ? link.getAttribute('href') || '' : '';
    if (!href || href === '#' || href.startsWith('javascript:')) continue;

    const absUrl = api.abs(href, baseUrl);
    if (seen.has(absUrl)) continue;
    seen.add(absUrl);

    const img = card.querySelector('img');
    const cover = imgUrl(img, baseUrl);

    const title = (
      card.querySelector('h2, h3, h4, .title, .anime-title')?.textContent ||
      img?.getAttribute('alt') ||
      link?.getAttribute('title') ||
      link?.textContent ||
      ''
    ).trim();

    if (!title) continue;

    const sub = (
      card.querySelector('.type, .badge, .meta, .sub, span')?.textContent || ''
    ).trim();

    items.push({
      title,
      cover,
      url: absUrl,
      sub: sub && sub !== title ? sub : undefined
    });
  }

  return items;
}

function extractEpisodes(doc, htmlText, animeUrl) {
  const episodes = [];
  const seenUrls = new Set();
  const seenNumbers = new Set();

  const animeSlug = animeUrl
    .replace(/^https?:\/\/[^/]+/i, '')
    .replace(/\/+$/, '')
    .split('/')
    .pop() || '';

  const addEpisode = (number, title, epUrl) => {
    const num = Number(number);
    if (!Number.isFinite(num) || num < 0) return; // el episodio 0 existe (películas, especiales)
    const cleanUrl = epUrl ? api.abs(epUrl, animeUrl) : `${animeUrl.replace(/\/+$/, '')}/episodio-${num}`;
    if (seenUrls.has(cleanUrl) && seenNumbers.has(num)) return;
    seenUrls.add(cleanUrl);
    seenNumbers.add(num);
    episodes.push({
      number: num,
      title: (title || `Episodio ${num}`).trim(),
      url: cleanUrl
    });
  };

  const allLinks = Array.from(doc.querySelectorAll('a[href]'));
  for (const a of allLinks) {
    const href = (a.getAttribute('href') || '').trim();
    if (!href || href === '#' || href.startsWith('javascript:')) continue;

    const text = (a.textContent || '').replace(/\s+/g, ' ').trim();
    const looksLikeEp =
      /\/(episodio|ep|ver|watch|capitulo|cap)[\/-]/i.test(href) ||
      /-(episodio|ep|capitulo|cap)-\d+/i.test(href) ||
      (animeSlug && href.includes(animeSlug) && /\/\d+\/?$/.test(href)) ||
      /^(episodio|ep\.?|cap\.?|capítulo)\s*\d+/i.test(text);

    if (!looksLikeEp) continue;

    const numMatch =
      href.match(/(?:episodio|ep|capitulo|cap)[-_\/](\d+(?:\.\d+)?)/i) ||
      href.match(/\/(\d+(?:\.\d+)?)\/?$/) ||
      text.match(/(?:episodio|ep\.?|cap\.?|capítulo)\s*(\d+(?:\.\d+)?)/i) ||
      text.match(/\b(\d+(?:\.\d+)?)\b/);

    if (numMatch) {
      addEpisode(
        numMatch[1],
        text || `Episodio ${numMatch[1]}`,
        href
      );
    }
  }

  const dataEls = Array.from(
    doc.querySelectorAll('[data-episode], [data-ep], [data-href], [data-url]')
  );
  for (const el of dataEls) {
    const epAttr = el.getAttribute('data-episode') || el.getAttribute('data-ep') || '';
    const hrefAttr = el.getAttribute('data-href') || el.getAttribute('data-url') || '';
    const txt = (el.textContent || '').replace(/\s+/g, ' ').trim();

    const numMatch =
      epAttr.match(/(\d+(?:\.\d+)?)/) ||
      hrefAttr.match(/(?:episodio|ep|cap)[-_\/](\d+(?:\.\d+)?)/i) ||
      txt.match(/(?:episodio|ep\.?|cap\.?)\s*(\d+(?:\.\d+)?)/i);

    if (numMatch) {
      addEpisode(
        numMatch[1],
        txt || `Episodio ${numMatch[1]}`,
        hrefAttr || undefined
      );
    }
  }

  // Pasada directa: cualquier /media/<slug>/<n> que aparezca en el HTML (incluye el episodio 0 de las películas)
  if (episodes.length === 0 && htmlText && animeSlug) {
    const esc = animeSlug.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const re = new RegExp('/media/' + esc + '/(\\d+(?:\\.\\d+)?)(?![\\d.])', 'g');
    let mm;
    while ((mm = re.exec(htmlText.replace(/\\\//g, '/')))) {
      addEpisode(mm[1], `Episodio ${mm[1]}`, `${animeUrl.replace(/\/+$/, '')}/${mm[1]}`);
    }
  }

  if (episodes.length === 0 && htmlText) {
    const cleanHtml = htmlText.replace(/\\\//g, '/');
    const urlMatches = cleanHtml.match(/https?:\/\/[^\s"'<>]+\/(?:episodio|ep|ver|watch)[\/-][^\s"'<>]+/gi) || [];
    for (const u of urlMatches) {
      const m = u.match(/(?:episodio|ep|ver|watch)[-_\/](\d+(?:\.\d+)?)/i);
      if (m) {
        addEpisode(m[1], `Episodio ${m[1]}`, u);
      }
    }
  }

  if (episodes.length === 0) {
    const bodyText = doc.body ? doc.body.textContent || '' : '';
    const countMatch = bodyText.match(/(?:episodios?|capítulos?|capitulos?)\s*[:\-]?\s*(\d{1,4})\b/i);
    if (countMatch) {
      const total = Number(countMatch[1]);
      if (total > 0 && total <= 200) {
        for (let i = 1; i <= total; i++) {
          addEpisode(i, `Episodio ${i}`, `${animeUrl.replace(/\/+$/, '')}/episodio-${i}`);
        }
      }
    }
  }

  episodes.sort((a, b) => a.number - b.number);
  return episodes;
}

/* ---------- HORARIO SEMANAL (/horario) ---------- */
const WEEK = ['lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado', 'domingo'];
const WEEK_LABEL = { lunes: 'Lunes', martes: 'Martes', miercoles: 'Miércoles', jueves: 'Jueves', viernes: 'Viernes', sabado: 'Sábado', domingo: 'Domingo' };

function dayOf(text) {
  const n = String(text || '').replace(/\s+/g, ' ').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  if (!n || n.length > 20) return '';
  const m = n.match(/^(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\b/);
  return m ? m[1] : '';
}

function scheduleCard(a, baseUrl) {
  let u;
  try { u = new URL(api.abs(a.getAttribute('href') || '', baseUrl)); } catch (_) { return null; }
  if (!/^\/media\/[^/]+\/?$/.test(u.pathname)) return null; // solo fichas de anime, no episodios
  const box = a.closest('article, li') || a;
  const img = a.querySelector('img') || box.querySelector('img');
  const title = (
    a.querySelector('h2, h3, h4, .title')?.textContent ||
    box.querySelector('h2, h3, h4, .title')?.textContent ||
    img?.getAttribute('alt') ||
    a.getAttribute('title') ||
    a.textContent ||
    ''
  ).replace(/\s+/g, ' ').trim();
  if (!title) return null;
  const raw = img ? img.getAttribute('data-src') || img.getAttribute('src') || '' : '';
  return { title, cover: raw && !/^data:/i.test(raw) ? api.abs(raw, baseUrl) : '', url: u.href };
}

function parseSchedule(doc, baseUrl) {
  const byDay = {};
  const push = (day, item) => {
    if (!day || !item) return;
    const list = (byDay[day] = byDay[day] || []);
    if (!list.some((x) => x.url === item.url)) list.push(item);
  };
  const result = () =>
    WEEK.filter((d) => byDay[d] && byDay[d].length).map((d) => ({ day: WEEK_LABEL[d], items: byDay[d] }));

  // A) pestañas que apuntan a su panel (aria-controls, href="#id" o data-target)
  doc.querySelectorAll('[role="tab"], [data-day], [data-tab], a[href^="#"], button').forEach((tab) => {
    const day = dayOf(tab.textContent);
    if (!day) return;
    const id = (tab.getAttribute('aria-controls') || tab.getAttribute('data-target') || tab.getAttribute('href') || '').replace(/^#/, '');
    const panel = id ? doc.getElementById(id) : null;
    if (panel) panel.querySelectorAll('a[href*="/media/"]').forEach((a) => push(day, scheduleCard(a, baseUrl)));
  });
  if (Object.keys(byDay).length >= 2) return result();

  // B) encabezados de día seguidos de sus tarjetas, en orden de documento
  Object.keys(byDay).forEach((k) => delete byDay[k]);
  let current = '';
  doc.body.querySelectorAll('*').forEach((el) => {
    if (el.children.length === 0) {
      const d = dayOf(el.textContent);
      if (d) { current = d; return; }
    }
    if (current && el.matches('a[href*="/media/"]')) push(current, scheduleCard(el, baseUrl));
  });
  if (Object.keys(byDay).length >= 2) return result();

  // C) un contenedor con 7 paneles (lunes → domingo)
  Object.keys(byDay).forEach((k) => delete byDay[k]);
  for (const el of Array.from(doc.body.querySelectorAll('*'))) {
    if (el.children.length !== 7) continue;
    const kids = Array.from(el.children);
    if (kids.filter((k) => k.querySelector('a[href*="/media/"]')).length < 4) continue;
    kids.forEach((k, i) => k.querySelectorAll('a[href*="/media/"]').forEach((a) => push(WEEK[i], scheduleCard(a, baseUrl))));
    break;
  }
  return result();
}

/* ---------- HORARIO: lectura de JSON y descubrimiento de la API ---------- */
const JS_DAYS = ['domingo', 'lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado'];

// Decodifica el formato "devalue" que usa SvelteKit en /__data.json
function devalueDecode(arr) {
  const memo = new Map();
  const rev = (i) => {
    if (i === -1) return undefined;
    if (i === -2) return NaN;
    if (i === -3) return Infinity;
    if (i === -4) return -Infinity;
    if (i === -5) return -0;
    if (memo.has(i)) return memo.get(i);
    const v = arr[i];
    if (v === null || typeof v !== 'object') { memo.set(i, v); return v; }
    if (Array.isArray(v)) {
      if (typeof v[0] === 'string') {
        if (v[0] === 'Date') { memo.set(i, v[1]); return v[1]; }
        if (v[0] === 'BigInt') { const n = Number(v[1]); memo.set(i, n); return n; }
        if (v[0] === 'Set') { const s = []; memo.set(i, s); v.slice(1).forEach((x) => s.push(rev(x))); return s; }
        if (v[0] === 'Map' || v[0] === 'RegExp' || v[0] === 'Object') { memo.set(i, null); return null; }
      }
      const out = [];
      memo.set(i, out);
      v.forEach((x) => out.push(rev(x)));
      return out;
    }
    const obj = {};
    memo.set(i, obj);
    Object.keys(v).forEach((k) => { obj[k] = rev(v[k]); });
    return obj;
  };
  return arr.length ? rev(0) : null;
}

function findAnime(o, depth = 0) {
  if (!o || typeof o !== 'object' || Array.isArray(o) || depth > 2) return null;
  const slug = typeof o.slug === 'string' ? o.slug : '';
  const urlStr = typeof o.url === 'string' ? o.url : '';
  const title = [o.title, o.name, o.titulo].find((x) => typeof x === 'string' && x.trim());
  if ((slug || urlStr) && title) return o;
  for (const v of Object.values(o)) {
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      const f = findAnime(v, depth + 1);
      if (f) return f;
    }
  }
  return null;
}

function animeItem(o, baseUrl) {
  const a = findAnime(o);
  if (!a) return null;
  const title = [a.title, a.name, a.titulo].find((x) => typeof x === 'string' && x.trim()).trim();
  const slug = typeof a.slug === 'string' ? a.slug : '';
  const url = slug ? api.abs('/media/' + slug, baseUrl) : api.abs(a.url, baseUrl);
  let cover = '';
  for (const k of ['cover', 'poster', 'image', 'imagen', 'thumbnail', 'portada']) {
    if (typeof a[k] === 'string' && a[k]) { cover = a[k]; break; }
  }
  if (!cover && /^\d+$/.test(String(a.id == null ? '' : a.id))) cover = 'https://cdn.animeav1.com/covers/' + a.id + '.jpg';
  return { title, cover: cover ? api.abs(cover, baseUrl) : '', url };
}

// Busca en un JSON los animes agrupados por día (claves "lunes"… o campos de día/fecha)
function scheduleFromJson(root, baseUrl) {
  const byDay = {};
  const push = (day, item) => {
    if (!day || !item) return;
    const list = (byDay[day] = byDay[day] || []);
    if (!list.some((x) => x.url === item.url)) list.push(item);
  };
  const finish = () =>
    WEEK.filter((d) => byDay[d] && byDay[d].length).map((d) => ({ day: WEEK_LABEL[d], items: byDay[d] }));

  // 1) objeto con claves de día: { lunes: [...], martes: [...] }
  const keyed = (node, depth) => {
    if (!node || typeof node !== 'object' || depth > 5) return false;
    if (!Array.isArray(node)) {
      const dayKeys = Object.keys(node).filter((k) => dayOf(k));
      if (dayKeys.length >= 2) {
        dayKeys.forEach((k) => {
          const v = node[k];
          (Array.isArray(v) ? v : Object.values(v || {})).forEach((e) => push(dayOf(k), animeItem(e, baseUrl)));
        });
        return true;
      }
    }
    return Object.values(node).some((v) => keyed(v, depth + 1));
  };
  if (keyed(root, 0)) {
    const r = finish();
    if (r.length >= 2) return r;
    Object.keys(byDay).forEach((k) => delete byDay[k]);
  }

  // 2) entradas con un campo de día u hora de emisión
  const entries = [];
  const walk = (node, depth) => {
    if (!node || typeof node !== 'object' || depth > 7) return;
    if (Array.isArray(node)) { node.forEach((n) => walk(n, depth + 1)); return; }
    for (const [k, v] of Object.entries(node)) {
      if (/day|dia|dow|broadcast|airing|aired|emisi|estreno|schedule|horario/i.test(k) && (typeof v === 'string' || typeof v === 'number')) {
        entries.push({ node, key: k, v });
      }
    }
    Object.values(node).forEach((v) => walk(v, depth + 1));
  };
  walk(root, 0);
  const sundayFirst = entries.some((e) => typeof e.v === 'number' && e.v === 0 && /day|dia|dow/i.test(e.key));
  entries.forEach(({ node, key, v }) => {
    let day = '';
    if (typeof v === 'string') {
      day = dayOf(v);
      if (!day && /\d{4}-\d{2}-\d{2}/.test(v)) {
        const t = Date.parse(v);
        if (!Number.isNaN(t)) day = JS_DAYS[new Date(t).getDay()];
      }
    } else if (v > 1e12) day = JS_DAYS[new Date(v).getDay()];
    else if (v > 1e9) day = JS_DAYS[new Date(v * 1000).getDay()];
    else if (/day|dia|dow/i.test(key) && v >= 0 && v <= 7) day = sundayFirst ? JS_DAYS[v] : v >= 1 ? WEEK[v - 1] : 'domingo';
    if (day) push(day, animeItem(node, baseUrl));
  });
  return finish();
}

// Rutas de API que aparecen en la página y en los scripts de la ruta (SvelteKit)
async function discoverEndpoints(html, doc, pageUrl) {
  const found = [];
  const add = (u) => {
    try {
      const full = api.abs(u, pageUrl);
      if (!found.includes(full) && !isStaticAsset(full) && !/google|discord|facebook|cloudflare|gstatic|\/horario\/?$/i.test(full)) found.push(full);
    } catch (_) {}
  };
  const re = /["'`]((?:https?:\/\/[a-z0-9.-]*animeav1[a-z0-9.-]*)?\/(?:api|ajax)\/[A-Za-z0-9_\-\/.?=&%]*)["'`]/g;
  let m;
  while ((m = re.exec(html))) add(m[1]);

  const srcs = Array.from(doc.querySelectorAll('link[rel="modulepreload"][href], script[src]'))
    .map((el) => el.getAttribute('href') || el.getAttribute('src'))
    .filter((h) => h && /\/(nodes|chunks|entry)\//.test(h));
  const re2 = /["'`]((?:https?:\/\/[a-z0-9.-]*animeav1[a-z0-9.-]*)?\/(?:(?:api|ajax)\/[A-Za-z0-9_\-\/.?=&%]*|[A-Za-z0-9_\-\/]*(?:horario|schedule|calendar)[A-Za-z0-9_\-\/.?=&%]*))["'`]/g;
  for (const s of srcs.slice(0, 10)) {
    try {
      const js = await api.text(api.abs(s, pageUrl));
      let mm;
      while ((mm = re2.exec(js))) add(mm[1]);
    } catch (_) {}
  }
  return { found, srcs };
}

return {
  id: 'animeav1',
  name: 'AnimeAV1',
  version: 10,
  baseUrl: BASE_URL,

  filters: FILTERS,

  async home(page = 1) {
    try {
      const url = api.abs(`/catalogo?page=${page}`, BASE_URL);
      const doc = await api.html(url);
      return parseCardList(doc, BASE_URL);
    } catch (e) {
      if (page > 1 && isNotFound(e)) return []; // ya no hay más páginas
      throw e;
    }
  },

  // Animes en emisión agrupados por día de la semana
  async schedule() {
    const url = api.abs('/horario', BASE_URL);
    const html = await api.text(url);
    const doc = new DOMParser().parseFromString(html, 'text/html');

    // 1) si la página ya trae las tarjetas
    const direct = parseSchedule(doc, url);
    if (direct.length >= 2) return direct;

    // 2) la página las carga con JS: buscar la API en la página y en sus scripts
    const { found, srcs } = await discoverEndpoints(html, doc, url);
    const defaults = ['/horario/__data.json', '/api/horario', '/api/schedule', '/api/schedules', '/api/calendar'].map((p) => api.abs(p, BASE_URL));
    const tried = [];
    for (const ep of found.concat(defaults).filter((v, i, a) => a.indexOf(v) === i).slice(0, 14)) {
      const short = ep.replace(BASE_URL, '');
      try {
        const text = await api.text(ep, { headers: { Accept: 'application/json' } });
        let data;
        try { data = JSON.parse(text); } catch (_) { tried.push(short + ' (no JSON)'); continue; }
        if (data && Array.isArray(data.nodes)) {
          for (const node of data.nodes) {
            if (node && Array.isArray(node.data)) {
              const r = scheduleFromJson(devalueDecode(node.data), BASE_URL);
              if (r.length >= 2) return r;
            }
          }
        }
        const r = scheduleFromJson(data, BASE_URL);
        if (r.length >= 2) return r;
        tried.push(short + ' (sin días)');
      } catch (e) {
        tried.push(short + ' (' + (e && e.status ? e.status : 'error') + ')');
      }
    }
    throw new Error('probé: ' + (tried.join(' | ') || '-') + ' · scripts: ' + srcs.slice(0, 3).join(' | '));
  },

  // Episodios recién publicados (sección "Recientemente Actualizado" de la portada), en orden de salida
  // page 1 = lo recién publicado en la portada. page 2, 3... = animes del día anterior, antepenúltimo... (según /horario)
  async latest(page = 1) {
    const p = Math.max(1, Number(page) || 1);
    if (p > 1) {
      const d = new Date();
      d.setDate(d.getDate() - (p - 1));
      const label = WEEK_LABEL[JS_DAYS[d.getDay()]];
      let days = [];
      try { days = await this.schedule(); } catch (_) { return []; }
      const entry = days.find((x) => x.day === label);
      return entry ? entry.items.map((it) => Object.assign({}, it, { note: label })) : [];
    }
    const url = api.abs('/', BASE_URL);
    const html = await api.text(url);
    const doc = new DOMParser().parseFromString(html, 'text/html');
    const epInfo = (a) => {
      const h = a.getAttribute('href') || '';
      if (h.includes('#')) return null;
      try {
        const u = new URL(api.abs(h, url));
        const m = u.pathname.match(/^\/media\/([^/]+)\/(\d+(?:\.\d+)?)\/?$/);
        return m ? { slug: m[1], ep: m[2], full: u.href } : null;
      } catch (_) { return null; }
    };
    const out = [];
    const seen = new Set();
    doc.querySelectorAll('a[href*="/media/"]').forEach((a) => {
      const info = epInfo(a);
      if (!info || seen.has(info.full)) return;

      // tarjeta: sube mientras el contenedor solo tenga este episodio
      let box = a;
      for (let i = 0; i < 4 && box.parentElement; i++) {
        const parent = box.parentElement;
        const others = Array.from(parent.querySelectorAll('a[href*="/media/"]'))
          .map(epInfo)
          .filter((x) => x && x.full !== info.full);
        if (others.length) break;
        box = parent;
      }
      const img = box.querySelector('img');
      const raw = img ? img.getAttribute('data-src') || img.getAttribute('src') || '' : '';

      let title = (a.getAttribute('aria-label') || a.getAttribute('title') || a.textContent || '')
        .replace(/\s+/g, ' ').trim().replace(/^Ver\s+/i, '');
      title = title.replace(new RegExp('\\s+' + info.ep.replace('.', '\\.') + '$'), '').trim();
      if (!title) {
        const h = box.querySelector('h2, h3, h4');
        title = h ? h.textContent.replace(/\s+/g, ' ').trim() : '';
      }
      if (!title) title = info.slug.replace(/-/g, ' ');

      seen.add(info.full);
      out.push({
        title,
        // la miniatura del episodio (thumbnails/ID) y la portada (covers/ID) comparten el mismo ID
        cover: raw ? api.abs(raw.replace('/thumbnails/', '/covers/'), url) : '',
        url: api.abs('/media/' + info.slug, BASE_URL),
        note: 'Episodio ' + info.ep,
        epUrl: info.full
      });
    });
    return out;
  },

  async search(query, page = 1, filters = {}) {
    const q = (query || '').trim();

    // Filtros elegidos -> parámetros de la URL (género, tipo, estado)
    const params = [];
    for (const id of Object.keys(FILTER_PARAM)) {
      const sel = filters && filters[id];
      const val = Array.isArray(sel) ? sel[0] : sel;
      if (val) params.push(`${FILTER_PARAM[id]}=${encodeURIComponent(val)}`);
    }

    const load = async (searchKey) => {
      const qs = params.slice();
      if (q) qs.push(`${searchKey}=${encodeURIComponent(q)}`);
      qs.push(`page=${page}`);
      const doc = await api.html(api.abs(`/catalogo?${qs.join('&')}`, BASE_URL));
      return parseCardList(doc, BASE_URL);
    };

    try {
      const items = await load('search');
      if (items.length > 0 || !q || page > 1) return items;
      return await load('q');
    } catch (e) {
      if (page > 1 && isNotFound(e)) return []; // ya no hay más páginas
      throw e;
    }
  },

  async details(url) {
    const absUrl = api.abs(url, BASE_URL);
    const htmlText = await api.text(absUrl);
    const doc = new DOMParser().parseFromString(htmlText, 'text/html');

    const title = pickText(doc, ['h1', '.anime-title', 'header h1', 'main h1']);

    // Portada: og:image o la primera imagen que no sea el logo del sitio
    const goodImg = (u) => !!u && !/^data:/i.test(u) && !/logo|favicon|placeholder/i.test(u);
    const imgCandidates = [pickAttr(doc, ['meta[property="og:image"]'], 'content')].concat(
      Array.from(doc.querySelectorAll('.poster img, .cover img, article img, main img, img')).map(
        (el) => el.getAttribute('data-src') || el.getAttribute('src') || ''
      )
    );
    const coverRaw = imgCandidates.find(goodImg) || '';
    const cover = coverRaw ? api.abs(coverRaw, absUrl) : '';

    const bannerRaw = pickAttr(
      doc,
      ['.banner img', '.backdrop img', '.cover-bg img'],
      'src'
    );
    const banner = bannerRaw ? api.abs(bannerRaw, absUrl) : undefined;

    const description = pickText(doc, [
      '.synopsis',
      '.description',
      '[class*="synopsis"]',
      '[class*="description"]',
      'p'
    ]);

    const genres = Array.from(
      doc.querySelectorAll('a[href*="/catalogo?genre="], a[href*="genre="], .genres a, .genre')
    )
      .map((el) => (el.textContent || '').trim())
      .filter(Boolean);

    const metaText = doc.body ? doc.body.textContent || '' : '';
    const yearMatch = metaText.match(/\b(19\d\d|20\d\d)\b/);
    const year = yearMatch ? Number(yearMatch[1]) : undefined;

    let status;
    if (/en emisión/i.test(metaText)) {
      status = 'En emisión';
    } else if (/finalizado|completado/i.test(metaText)) {
      status = 'Finalizado';
    }

    let episodes = extractEpisodes(doc, htmlText, absUrl);

    // Último recurso: la ficha no enseña la lista pero la página del episodio existe (películas, OVAs, especiales)
    if (episodes.length === 0) {
      const base = absUrl.replace(/\/+$/, '');
      for (const n of [0, 1]) {
        try {
          await api.text(`${base}/${n}`);
          episodes.push({ number: n, title: `Episodio ${n}`, url: `${base}/${n}` });
        } catch (_) { /* no existe */ }
      }
    }

    return {
      title,
      cover,
      banner,
      description,
      genres: [...new Set(genres)],
      year,
      status,
      episodes
    };
  },

  async videos(episodeUrl, ctx) {
    const absUrl = api.abs(episodeUrl, BASE_URL);
    const html = await api.text(absUrl);
    const doc = new DOMParser().parseFromString(html, 'text/html');

    const found = [];
    const seen = new Set();

    const addSource = (rawUrl, langHint = '', nameHint = '') => {
      if (!rawUrl) return;
      const cleaned = String(rawUrl)
        .replace(/\\\//g, '/')
        .replace(/\\u0026/g, '&')
        .replace(/&amp;/g, '&')
        .trim();

      if (!cleaned || cleaned.startsWith('data:') || cleaned.startsWith('javascript:')) return;
      const full = api.abs(cleaned, absUrl);
      if (!full) return;
      if (seen.has(full)) {
        if (langHint) {
          const ex = found.find((f) => f.url === full);
          if (ex && !ex.lang) ex.lang = langHint;
        }
        return;
      }
      seen.add(full);

      const classified = classifyFastServer(full, nameHint);
      if (classified) {
        classified.lang = langHint;
        found.push(classified);
      }
    };

    doc.querySelectorAll('iframe').forEach((iframe) => {
      addSource(
        iframe.getAttribute('src') ||
        iframe.getAttribute('data-src') ||
        iframe.getAttribute('data-lazy-src') ||
        '',
        ctxLang(iframe)
      );
    });

    doc.querySelectorAll('[data-embed], [data-player], [data-src], [data-url], [data-link]').forEach((el) => {
      addSource(
        el.getAttribute('data-embed') ||
        el.getAttribute('data-player') ||
        el.getAttribute('data-src') ||
        el.getAttribute('data-url') ||
        el.getAttribute('data-link') ||
        '',
        ctxLang(el)
      );
    });

    const unescaped = html.replace(/\\\//g, '/').replace(/\\u0026/g, '&');
    // La web agrupa los servidores bajo "SUB" y "DUB": cada URL toma el último marcador que aparece antes
    const markers = [];
    const markerRe = /["']?\b(SUB|DUB)\b["']?\s*:/g;
    let mk;
    while ((mk = markerRe.exec(unescaped))) markers.push({ i: mk.index, lang: mk[1] });
    const langAt = (idx) => {
      let l = '';
      for (const m of markers) {
        if (m.i < idx) l = m.lang;
        else break;
      }
      return l;
    };
    const urlRe = /https?:\/\/[^\s"'<>\\]+/gi;
    let um;
    while ((um = urlRe.exec(unescaped))) {
      // nombre del servidor que la web guarda junto a la URL: "server":"UPNShare","url":"..."
      const around = unescaped.slice(Math.max(0, um.index - 140), um.index + um[0].length + 140);
      const nm = around.match(/["'](?:server|name|label|title|provider)["']\s*:\s*["']([^"']{2,24})["']/i);
      addSource(um[0], langAt(um.index), nm ? nm[1] : '');
    }

    found.sort((a, b) => a.priority - b.priority);

    return found.map(({ label, url, type, lang }) => ({
      label,
      url,
      type,
      lang
    }));
  }
};
