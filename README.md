# KuroAnime

## Títulos relacionados (precuela / secuela)

Los módulos JSON pueden declarar títulos relacionados dentro de cada elemento usando
`relacionados`, por ejemplo:

```json
"relacionados": [
  {
    "titulo": "Jujutsu Kaisen 0 Movie",
    "relacion": "Precuela",
    "portada": "https://ejemplo.com/portada.jpg",
    "id": "id-del-titulo",
    "fuente": "https://ejemplo.com/modulo.js"
  },
  {
    "titulo": "Jujutsu Kaisen 2nd Season",
    "relacion": "Secuela"
  }
]
```

Los campos `titulo` y `relacion` son los principales; `portada`, `id` y `fuente`
permiten identificar y abrir el título relacionado cuando el módulo los proporciona.


## Relaciones automáticas entre animes

La pantalla de detalles consulta AniList para mostrar precuelas, secuelas, temporadas,
películas, OVAs, ONAs, especiales y spin-offs relacionados. También combina esos
resultados con las relaciones que entregue el módulo instalado. Esta consulta requiere
conexión a Internet; si AniList no responde o no encuentra coincidencias, la sección
no se muestra. Al tocar un título relacionado, KuroAnime intenta localizarlo en el
módulo actual antes de abrir su ficha.
