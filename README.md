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
