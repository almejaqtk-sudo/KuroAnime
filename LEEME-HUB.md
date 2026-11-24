# Módulos estilo AnimeHub en KuroAnime

Archivos nuevos: `MotorHub.kt`, `KuroApp.kt`, `assets/hub.html`, `modulo-hub-ejemplo.js`.
Archivos modificados: `AndroidManifest.xml`, `Repositorio.kt`, `DetalleActivity.kt`, `ModulosActivity.kt`, `PlayerActivity.kt`.

Uso: Módulos > pega el código del .js (o "Instalar desde archivo"). Si define `var modulo = {...}` usa el motor
Rhino de siempre; si no, lo trata como módulo AnimeHub y lo ejecuta en un WebView oculto.
Prueba primero con `modulo-hub-ejemplo.js`.
