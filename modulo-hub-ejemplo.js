// Módulo de prueba con el formato de AnimeHub (Internet Archive, películas de dominio público)
const BASE = 'https://archive.org';
const lista = (docs) => docs.map((d) => ({
  title: String(d.title || d.identifier),
  cover: BASE + '/services/img/' + d.identifier,
  url: d.identifier,
  sub: 'Película'
}));
return {
  id: 'archive-hub',
  name: 'Archive (formato AnimeHub)',
  version: 1,
  async home(page) {
    const u = BASE + '/advancedsearch.php?q=' + encodeURIComponent('collection:feature_films AND mediatype:movies') +
      '&fl%5B%5D=identifier&fl%5B%5D=title&sort%5B%5D=downloads+desc&rows=24&page=' + (page || 1) + '&output=json';
    const j = await api.json(u);
    return lista(j.response.docs);
  },
  async search(query, page) {
    const u = BASE + '/advancedsearch.php?q=' + encodeURIComponent('title:(' + query + ') AND mediatype:movies') +
      '&fl%5B%5D=identifier&fl%5B%5D=title&rows=24&page=' + (page || 1) + '&output=json';
    const j = await api.json(u);
    return lista(j.response.docs);
  },
  async details(id) {
    const m = await api.json(BASE + '/metadata/' + encodeURIComponent(id));
    const eps = (m.files || []).filter((f) => /\.mp4$/i.test(f.name)).slice(0, 12)
      .map((f, i) => ({ number: i + 1, title: f.name.replace(/\.mp4$/i, ''), url: f.name }));
    return { id: id, title: (m.metadata && m.metadata.title) || id, description: '', episodes: eps };
  },
  async videos(fileName, ctx) {
    const id = ctx.detail.id;
    return [{
      label: 'Archive MP4',
      url: BASE + '/download/' + encodeURIComponent(id) + '/' + encodeURIComponent(fileName),
      type: 'mp4'
    }];
  }
};
