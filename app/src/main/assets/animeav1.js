// AnimeAV1 (módulo integrado en KuroAnime) - versión 1
// Formato AnimeHub: home / search / genres / details / videos
const BASE = 'https://animeav1.com';
const H = { 'Accept-Language': 'es-ES,es;q=0.9' };

// Se usa solo si la página no deja leer la lista de géneros
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

return {
  id: 'animeav1',
  name: 'AnimeAV1',
  version: 1,

  // Catálogo. filters.genre = slug del género (por ejemplo "comedia")
  async home(page, filters) {
    const q = [];
    if (page && page > 1) q.push('page=' + page);
    if (filters && filters.genre) q.push('genre=' + encodeURIComponent(filters.genre));
    const doc = await api.html(BASE + '/catalogo' + (q.length ? '?' + q.join('&') : ''), { headers: H });
    return parseLista(doc);
  },

  async search(query, page, filters) {
    const q = ['page=' + (page || 1)];
    if (query) q.push('search=' + encodeURIComponent(query));
    if (filters && filters.genre) q.push('genre=' + encodeURIComponent(filters.genre));
    const doc = await api.html(BASE + '/catalogo?' + q.join('&'), { headers: H });
    return parseLista(doc);
  },

  // Lista de géneros del sitio: [{ name, id }]
  async genres() {
    try {
      const doc = await api.html(BASE + '/catalogo', { headers: H });
      const hallados = {};
      doc.querySelectorAll('input[name^="genre"]').forEach((i) => {
        const v = i.getAttribute('value');
        if (!v) return;
        const l = i.closest('label');
        hallados[v] = limpiar(l ? l.textContent : '') || v;
      });
      doc.querySelectorAll('select[name^="genre"] option').forEach((o) => {
        const v = o.getAttribute('value');
        if (v) hallados[v] = limpiar(o.textContent) || v;
      });
      doc.querySelectorAll('a[href*="genre="]').forEach((a) => {
        const m = String(a.getAttribute('href') || '').match(/[?&]genre=([^&#]+)/);
        if (m) hallados[decodeURIComponent(m[1])] = limpiar(a.textContent) || m[1];
      });
      const ids = Object.keys(hallados);
      if (ids.length >= 5) return ids.map((k) => ({ name: hallados[k], id: k }));
    } catch (e) {}
    return GENEROS_BASE;
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
