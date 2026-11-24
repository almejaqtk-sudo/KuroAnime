var BASE = "https://archive.org";

function limpiar(texto) {
  if (texto instanceof Array) texto = texto.join(" ");
  return String(texto || "").replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").substring(0, 300);
}

var modulo = {
  nombre: "Internet Archive (películas)",

  // Lista de títulos. Devuelve un arreglo de objetos con id, titulo, portada, etc.
  items: function () {
    var url = BASE + "/advancedsearch.php?q=" +
      encodeURIComponent("collection:feature_films AND mediatype:movies") +
      "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=description" +
      "&sort%5B%5D=downloads+desc&rows=24&output=json";
    var docs = http.json(url).response.docs;
    var lista = [];
    for (var i = 0; i < docs.length; i++) {
      var d = docs[i];
      lista.push({
        id: d.identifier,
        titulo: String(d.title || d.identifier),
        portada: BASE + "/services/img/" + d.identifier,
        descripcion: limpiar(d.description),
        tipo: "Película",
        estado: "Finalizado",
        ultimo: "Película"
      });
    }
    return lista;
  },

  // Episodios de un título (se llama al abrirlo). Devuelve titulo + servidores.
  episodios: function (id) {
    var meta = http.json(BASE + "/metadata/" + encodeURIComponent(id));
    var archivos = meta.files || [];
    var eps = [];
    for (var i = 0; i < archivos.length && eps.length < 12; i++) {
      var a = archivos[i];
      if (a.name && /\.mp4$/i.test(a.name)) {
        eps.push({
          titulo: a.name.replace(/\.mp4$/i, ""),
          servidores: [{
            nombre: "Internet Archive",
            url: BASE + "/download/" + encodeURIComponent(id) + "/" + encodeURIComponent(a.name),
            tipo: "mp4"
          }]
        });
      }
    }
    return eps;
  }
};
