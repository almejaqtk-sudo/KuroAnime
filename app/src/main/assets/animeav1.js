// AnimeAV1 (módulo integrado en KuroAnime) - versión 1
// Formato AnimeHub: home / search / genres / details / videos
const BASE = 'https://animeav1.com';
const H = { 'Accept-Language': 'es-ES,es;q=0.9' };

// Respaldo: se usa solo si la página no deja leer sus filtros. Lo normal es leerlos del propio sitio.
const GENEROS_BASE = [
  ['Acción', 'accion'], ['Aventuras', 'aventuras'], ['Ciencia Ficción', 'ciencia-ficcion'],
  ['Comedia', 'comedia'], ['Deportes', 'deportes'], ['Drama', 'drama'], ['Ecchi', 'ecchi'],
  ['Escolares', 'escolares'], ['Fantasía', 'fantasia'], ['Gore', 'gore'], ['Harem', 'harem'],
  ['Histórico', 'historico'], ['Isekai', 'isekai'], ['Magia', 'magia'], ['Mecha', 'mecha'],
  ['Militar', 'militar'], ['Misterio', 'misterio'], ['Música', 'musica'], ['Psicológico', 'psicologico'],
  ['Romance', 'romance'], ['Seinen', 'seinen'], ['Shoujo', 'shoujo'], ['Shounen', 'shounen'],
  ['Sobrenatural', 'sobrenatural'], ['Superpoderes', 'superpoderes'], ['Suspenso', 'suspenso'],
  ['Terror', 'terror'], ['Vampiros', 'vampiros']
].map((g) => ({ name: g[0], id: g[1] }));

const NOMBRES_FILTRO = {
  genre: 'Género', category: 'Tipo', type: 'Tipo', year: 'Año', status: 'Estado',
  order: 'Ordenar por', sort: 'Ordenar por'
};
// Parámetros que no son filtros de verdad
const NO_FILTRO = { search: 1, q: 1, query: 1, page: 1, letter: 1, letra: 1 };

const limpiar = (t) => String(t || '').replace(/\s+/g, ' ').trim();
const ruta = (href) => String(href || '').replace(/^https?:\/\/[^\/]+/, '').split('#')[0].split('?')[0];

function parseLista(doc) {
  const orden = [];
  const mapa = {};
  doc.querySelectorAll('a[href*="/media/"]').forEach((a) => {
    const m = ruta(a.getAttribute('href')).match(/^\/media\/([^\/]+)\/?$/);
    if (!m) return; // los enlaces de episodio (/media/slug/3) no se cuentan
    const slug = m[1];
    const cont = a.closest('article') || a.parentElement || a;
    let it = mapa[slug];
    if (!it) {
      it = { title: '', cover: '', url: slug, sub: '' };
      mapa[slug] = it;
      orden.push(it);
    }
    if (!it.title) {
      const h = cont.querySelector('h2,h3');
      it.title = limpiar(h ? h.textContent : '') || limpiar(a.textContent).replace(/^Ver\s+/i, '');
    }
    if (!it.cover) {
      const img = cont.querySelector('img[src*="/covers/"]') || cont.querySelector('img');
      if (img) it.cover = api.abs(img.getAttribute('src') || '', BASE);
    }
    if (!it.sub) {
      const t = (cont.textContent || '').match(/(TV Anime|ONA|OVA|Película|Pelicula|Especial|Movie)/i);
      if (t) it.sub = t[1];
    }
  });
  return orden.filter((x) => x.title);
}

// Convierte los filtros elegidos en la app ({ genre: 'accion', status: 'emision' }) en parámetros de la URL
function paramsFiltros(filters) {
  const q = [];
  Object.keys(filters || {}).forEach((k) => {
    if (filters[k] !== undefined && filters[k] !== null && filters[k] !== '') {
      q.push(encodeURIComponent(k) + '=' + encodeURIComponent(filters[k]));
    }
  });
  return q;
}

const normal = (t) => String(t || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, ' ').trim();

return {
  id: 'animeav1',
  name: 'AnimeAV1',
  version: '1.1.0',
  baseUrl: BASE, // la app saca de aquí el icono de la página

  // Catálogo. filters = { genre, category, status, year, order } según lo que tenga el sitio
  async home(page, filters) {
    const q = [];
    if (page && page > 1) q.push('page=' + page);
    paramsFiltros(filters).forEach((x) => q.push(x));
    const doc = await api.html(BASE + '/catalogo' + (q.length ? '?' + q.join('&') : ''), { headers: H });
    return parseLista(doc);
  },

  // Búsqueda por nombre. Prueba los nombres de parámetro que usa el sitio y se queda con el que
  // de verdad filtra (si el sitio ignora el parámetro devuelve el catálogo normal y se descarta).
  async search(query, page, filters) {
    const pag = page || 1;
    const extra = paramsFiltros(filters);
    if (!query) return this.home(pag, filters);
    let normalDoc = null;
    for (const nombre of ['search', 'q', 'query']) {
      const q = [nombre + '=' + encodeURIComponent(query), 'page=' + pag].concat(extra);
      const items = parseLista(await api.html(BASE + '/catalogo?' + q.join('&'), { headers: H }));
      if (!items.length) continue;
      if (!normalDoc) {
        normalDoc = parseLista(await api.html(BASE + '/catalogo?page=' + pag, { headers: H }));
      }
      const igual = items.slice(0, 5).every((x, i) => normalDoc[i] && normalDoc[i].url === x.url);
      if (!igual) return items;
    }
    return [];
  },

  // Todos los filtros que tiene el sitio: [{ key, name, options: [{ name, id }] }]
  async filters() {
    const grupos = {};
    const orden = [];
    const poner = (clave, nombre, valor) => {
      const base = clave.replace(/\[\]$/, '');
      if (NO_FILTRO[base] || !valor) return;
      if (!grupos[clave]) { grupos[clave] = { key: clave, name: NOMBRES_FILTRO[base] || (base.charAt(0).toUpperCase() + base.slice(1)), options: [], vistos: {} }; orden.push(clave); }
      const g = grupos[clave];
      if (g.vistos[valor]) return;
      g.vistos[valor] = true;
      g.options.push({ name: limpiar(nombre) || valor, id: valor });
    };
    try {
      const doc = await api.html(BASE + '/catalogo', { headers: H });
      doc.querySelectorAll('input[name]').forEach((i) => {
        const t = (i.getAttribute('type') || '').toLowerCase();
        if (t !== 'radio' && t !== 'checkbox') return;
        const l = i.closest('label');
        poner(i.getAttribute('name'), l ? l.textContent : (i.getAttribute('aria-label') || ''), i.getAttribute('value'));
      });
      doc.querySelectorAll('select[name]').forEach((s) => {
        s.querySelectorAll('option').forEach((o) => poner(s.getAttribute('name'), o.textContent, o.getAttribute('value')));
      });
      doc.querySelectorAll('a[href*="catalogo?"]').forEach((a) => {
        const href = String(a.getAttribute('href') || '');
        const m = href.match(/[?&]([a-zA-Z_\[\]]+)=([^&#]+)/);
        if (m && /^(genre|category|status|year|type|order)(\[\])?$/.test(m[1])) {
          poner(m[1], a.textContent, decodeURIComponent(m[2]));
        }
      });
    } catch (e) {}
    const res = orden.map((k) => grupos[k]).filter((g) => g.options.length >= 2)
      .map((g) => ({ key: g.key, name: g.name, options: g.options }));
    const tiene = (k) => res.some((g) => g.key.replace(/\[\]$/, '') === k);
    // Lo que el sitio no dejó leer se completa con valores de respaldo
    if (!tiene('genre')) res.unshift({ key: 'genre', name: 'Género', options: GENEROS_BASE });
    if (!tiene('category') && !tiene('type')) {
      res.push({ key: 'category', name: 'Tipo', options: [
        { name: 'TV Anime', id: 'tv-anime' }, { name: 'Película', id: 'pelicula' },
        { name: 'OVA', id: 'ova' }, { name: 'ONA', id: 'ona' }, { name: 'Especial', id: 'especial' }
      ] });
    }
    if (!tiene('status')) {
      res.push({ key: 'status', name: 'Estado', options: [
        { name: 'En emisión', id: 'emision' }, { name: 'Finalizado', id: 'finalizado' }
      ] });
    }
    return res;
  },

  // Se mantiene por compatibilidad con versiones anteriores de la app
  async genres() {
    const f = (await this.filters()).filter((g) => g.key === 'genre')[0];
    return f ? f.options : GENEROS_BASE;
  },

  async details(slug) {
    const doc = await api.html(BASE + '/media/' + slug, { headers: H });
    const h1 = doc.querySelector('h1');
    const generos = [];
    doc.querySelectorAll('a[href*="catalogo?genre="]').forEach((a) => {
      const t = limpiar(a.textContent);
      if (t && generos.indexOf(t) < 0) generos.push(t);
    });
    let descripcion = '';
    const ps = doc.querySelectorAll('p');
    for (let i = 0; i < ps.length; i++) {
      const t = limpiar(ps[i].textContent);
      if (t.length > 60) { descripcion = t; break; }
    }
    const vistos = {};
    const eps = [];
    doc.querySelectorAll('a[href*="/media/' + slug + '/"]').forEach((a) => {
      const m = ruta(a.getAttribute('href')).match(/\/media\/[^\/]+\/(\d+(?:\.\d+)?)\/?$/);
      if (!m || vistos[m[1]]) return;
      vistos[m[1]] = true;
      eps.push({
        number: parseFloat(m[1]),
        title: 'Episodio ' + m[1],
        url: BASE + '/media/' + slug + '/' + m[1]
      });
    });
    eps.sort((a, b) => a.number - b.number);
    return {
      id: slug,
      title: limpiar(h1 ? h1.textContent : '') || slug,
      description: descripcion,
      genres: generos,
      episodes: eps
    };
  },

  // Devuelve los servidores del episodio. Primero los que reproduce la app directamente (rápidos),
  // después los reproductores web.
  async videos(epUrl) {
    const html = await api.text(epUrl, { headers: H });
    const limpia = (u) => String(u).replace(/\\u002F/gi, '/').replace(/\\\//g, '/').replace(/&amp;/g, '&');
    const directos = [];
    const webs = [];
    const vistos = {};
    const poner = (lang, nombre, url) => {
      url = limpia(url);
      if (!/^https?:\/\//i.test(url) || vistos[url]) return;
      vistos[url] = true;
      const z = url.match(/^(https?:\/\/player\.zilla-networks\.com)\/play\/([A-Za-z0-9]+)/);
      if (z) {
        // Lista de reproducción directa: carga mucho más rápido que el reproductor web
        directos.push({
          label: nombre + ' directo',
          url: z[1] + '/m3u8/' + z[2],
          type: 'hls',
          lang: lang,
          headers: { Referer: z[1] + '/' }
        });
      }
      webs.push({ label: nombre, url: url, type: 'embed', lang: lang });
    };

    const bloque = /\b(SUB|DUB)["']?\s*:\s*\[([\s\S]*?)\]/g;
    let b;
    while ((b = bloque.exec(html))) {
      const objetos = b[2].match(/\{[^{}]*\}/g) || [];
      objetos.forEach((o) => {
        const s = o.match(/["']?server["']?\s*:\s*["']([^"']+)["']/);
        const u = o.match(/["']?url["']?\s*:\s*["']([^"']+)["']/);
        if (s && u) poner(b[1], s[1], u[1]);
      });
    }
    // Respaldo: cualquier reproductor de zilla que aparezca en la página
    const z2 = html.match(/https?:\/\/player\.zilla-networks\.com\/play\/[A-Za-z0-9]+/g) || [];
    z2.forEach((u) => poner('SUB', 'HLS', u));

    const rank = (v) => (v.lang === 'SUB' ? 0 : 1);
    const orden = (a, b2) => rank(a) - rank(b2);
    directos.sort(orden);
    webs.sort(orden);
    return directos.concat(webs);
  }
};
