package com.mistream.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.UUID

object Repositorio {

    class Resultado(val modulos: List<Modulo>, val errores: List<String>)

    class Instalado(
        val id: String,
        val nombre: String,
        var categoria: String,
        val tipo: String, // "url" o "codigo"
        val valor: String, // la URL o el texto del módulo (JSON o JS)
        var activo: Boolean
    )

    val CATEGORIAS = listOf("Anime", "Películas", "Hentai", "Adultos")

    // Decide en qué sección va un módulo según su nombre y la dirección de su página
    fun detectarCategoria(nombre: String, texto: String): String {
        val host = try {
            val m = Regex("""https?://([^/\s'"`]+)""").find(texto)
            m?.groupValues?.get(1) ?: ""
        } catch (e: Exception) {
            ""
        }
        val t = (nombre + " " + host).lowercase()
        return when {
            t.contains("hentai") -> "Hentai"
            Regex("jav|porn|xxx|xvideo|xnxx|xhamster|adult|erotic|nsfw|sexo|sex").containsMatchIn(t) -> "Adultos"
            Regex("pelis|pelicula|movie|cine|film|serie|cuevana|repelis|flix").containsMatchIn(t) -> "Películas"
            else -> "Anime"
        }
    }

    // Una sola vez: los módulos que estaban en secciones que ya no existen (Series, Otros) o en "Anime"
    // por defecto se acomodan según lo que son
    private fun migrarCategorias(prefs: SharedPreferences) {
        if (prefs.getBoolean("cat_v2", false)) return
        try {
            val lista = leerInstalados(prefs)
            var cambio = false
            for (m in lista) {
                if (m.categoria !in CATEGORIAS || m.categoria == "Anime") {
                    val nueva = detectarCategoria(m.nombre, if (m.tipo == "url") m.valor else m.valor.take(4000))
                    if (nueva != m.categoria) {
                        m.categoria = nueva
                        cambio = true
                    }
                }
            }
            if (cambio) guardarInstalados(prefs, lista)
            prefs.edit().putBoolean("cat_v2", true).apply()
        } catch (e: Exception) {
            // se intentará la próxima vez
        }
    }

    // Títulos cargados en memoria, para encontrarlos al abrir el detalle
    @Volatile
    private var registro: List<Item> = emptyList()

    fun buscarItem(titulo: String, portada: String): Item? =
        registro.firstOrNull { it.titulo == titulo && it.portada == portada }

    fun buscarItem(titulo: String): Item? =
        registro.firstOrNull { it.titulo.equals(titulo, ignoreCase = true) }

    // Busca dentro del registro únicamente del módulo que originó la ficha actual.
    fun buscarItemEnFuente(titulo: String, portada: String = "", fuente: String): Item? {
        if (fuente.isBlank()) return null
        val candidatos = registro.filter { it.fuente == fuente }
        return candidatos.firstOrNull {
            it.titulo.equals(titulo, ignoreCase = true) &&
                (portada.isBlank() || it.portada == portada)
        } ?: candidatos.firstOrNull { it.titulo.equals(titulo, ignoreCase = true) }
    }

    // Títulos que llegaron después (por ejemplo, al filtrar por género): se pueden abrir igual
    fun agregarARegistro(nuevos: List<Item>) {
        registro = registro + nuevos
    }

    /**
     * Busca un título relacionado exclusivamente en el módulo que originó la ficha actual.
     * `fuenteActual` es el código JS/Hub o la URL del módulo individual, no un índice global.
     * Debe llamarse desde un hilo secundario porque puede realizar peticiones de red.
     */
    fun buscarEnModulo(
        fuenteActual: String,
        consulta: String,
        alternativas: List<String> = emptyList()
    ): Item? {
        val fuente = fuenteActual.trim()
        val consultas = (listOf(consulta) + alternativas).map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { normalizarTitulo(it) }
        if (fuente.isBlank() || consultas.isEmpty()) return null

        // Primero se revisan los elementos que ya están cargados, pero solo de esta fuente.
        for (q in consultas) buscarItemEnFuente(q, fuente = fuente)?.let { return it }

        return try {
            val texto = if (fuente.startsWith("http", true)) {
                val conexion = URL(fuente).openConnection() as java.net.HttpURLConnection
                conexion.connectTimeout = 12000
                conexion.readTimeout = 15000
                conexion.instanceFollowRedirects = true
                conexion.setRequestProperty("User-Agent", "AniKai/1.0")
                try {
                    if (conexion.responseCode !in 200..299) return null
                    conexion.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conexion.disconnect()
                }
            } else fuente

            val limpio = texto.trim()
            val encontrados = if (limpio.startsWith("{")) {
                val raiz = JSONObject(limpio)
                // Un índice contiene varios módulos: no se expande para evitar una búsqueda global.
                if (raiz.has("modulos")) return null
                if (raiz.has("items")) {
                    val catalogo = ModuloParser.parse(limpio, fuente).items
                    catalogo.filter { item -> consultas.any { coincideTitulo(item.titulo, it) } }
                } else emptyList()
            } else {
                // Cada consulta se ejecuta contra el mismo módulo; nunca se recorren otros módulos.
                val salida = mutableListOf<Item>()
                for (q in consultas) {
                    val resultados = Motores.buscar(limpio, q).map { item ->
                        if (item.fuente.isBlank()) item.copy(fuente = fuente) else item
                    }
                    salida.addAll(resultados)
                    val exacto = salida.firstOrNull { item ->
                        consultas.any { normalizarTitulo(item.titulo) == normalizarTitulo(it) }
                    }
                    if (exacto != null) break
                }
                salida
            }

            val exacto = encontrados.firstOrNull { item ->
                consultas.any { normalizarTitulo(item.titulo) == normalizarTitulo(it) }
            }
            val resultado = exacto ?: encontrados.firstOrNull { item ->
                val t = normalizarTitulo(item.titulo)
                t.length >= 4 && consultas.any { q ->
                    val n = normalizarTitulo(q)
                    n.length >= 4 && (t.contains(n) || n.contains(t))
                }
            }
            if (resultado != null) agregarARegistro(listOf(resultado))
            resultado
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Busca un título relacionado en TODOS los módulos instalados y activos.
     * Acepta módulos KuroAnime (Rhino), AnimeHub (WebView), catálogos JSON
     * e índices JSON que contienen varias fuentes.
     * Debe llamarse desde un hilo secundario porque puede realizar peticiones de red.
     */
    fun buscarEnTodos(
        prefs: SharedPreferences,
        consulta: String,
        fuenteActual: String = ""
    ): Item? {
        val objetivo = normalizarTitulo(consulta)
        if (objetivo.isBlank()) return null

        // Primero aprovechamos los elementos ya cargados en memoria.
        registro.firstOrNull { normalizarTitulo(it.titulo) == objetivo }?.let { return it }

        val fuentes = leerInstalados(prefs)
            .filter { it.activo }
            .map { it.valor }
            .toMutableList()
        // Incluye la fuente de la ficha aunque el usuario la haya desactivado mientras estaba abierta.
        if (fuenteActual.isNotBlank() && fuenteActual !in fuentes) fuentes.add(0, fuenteActual)

        val encontrados = mutableListOf<Item>()
        val visitadas = HashSet<String>()
        for (fuente in fuentes) {
            try {
                buscarEnFuente(fuente, consulta, encontrados, visitadas, 0)
                val exacto = encontrados.firstOrNull { normalizarTitulo(it.titulo) == objetivo }
                if (exacto != null) {
                    agregarARegistro(listOf(exacto))
                    return exacto
                }
            } catch (_: Exception) {
                // Un módulo que falle no debe impedir buscar en los demás.
            }
        }

        // Solo aceptamos coincidencias parciales claras; nunca abrimos el primer resultado al azar.
        val parcial = encontrados
            .distinctBy { normalizarTitulo(it.titulo) + "|" + it.fuente }
            .firstOrNull {
                val t = normalizarTitulo(it.titulo)
                t.length >= 4 && (t.contains(objetivo) || objetivo.contains(t))
            }
        if (parcial != null) agregarARegistro(listOf(parcial))
        return parcial
    }

    private fun buscarEnFuente(
        fuenteOriginal: String,
        consulta: String,
        salida: MutableList<Item>,
        visitadas: MutableSet<String>,
        profundidad: Int
    ) {
        if (profundidad > 3 || fuenteOriginal.isBlank()) return
        val clave = fuenteOriginal.hashCode().toString()
        if (!visitadas.add(clave)) return

        val texto = if (fuenteOriginal.trimStart().startsWith("http", true)) {
            val conexion = URL(fuenteOriginal.trim()).openConnection() as java.net.HttpURLConnection
            conexion.connectTimeout = 12000
            conexion.readTimeout = 15000
            conexion.instanceFollowRedirects = true
            conexion.setRequestProperty("User-Agent", "KuroAnime/1.0")
            try {
                if (conexion.responseCode !in 200..299) return
                conexion.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conexion.disconnect()
            }
        } else fuenteOriginal

        val limpio = texto.trim()
        if (limpio.startsWith("{")) {
            val raiz = JSONObject(limpio)
            val indice = raiz.optJSONArray("modulos")
            if (indice != null) {
                for (i in 0 until indice.length()) {
                    buscarEnFuente(indice.optString(i), consulta, salida, visitadas, profundidad + 1)
                }
                return
            }
            // Los módulos JSON contienen un catálogo estático: se filtra por título.
            if (raiz.has("items")) {
                val modulo = ModuloParser.parse(limpio)
                salida.addAll(modulo.items.filter { coincideTitulo(it.titulo, consulta) })
                return
            }
        }

        // Motores.buscar detecta automáticamente si el módulo usa Rhino o AnimeHub/WebView.
        salida.addAll(Motores.buscar(limpio, consulta))
    }

    private fun coincideTitulo(titulo: String, consulta: String): Boolean {
        val a = normalizarTitulo(titulo)
        val b = normalizarTitulo(consulta)
        return a == b || (a.length >= 4 && (a.contains(b) || b.contains(a)))
    }

    private fun normalizarTitulo(valor: String): String =
        java.text.Normalizer.normalize(valor.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    // ---- Módulo AnimeAV1 integrado en la app ----
    private const val VERSION_INTEGRADO = 5
    private const val MARCA_INTEGRADO = "// AnimeAV1 (módulo integrado en KuroAnime)"

    // Instala (o actualiza) el módulo AnimeAV1 que viene dentro de la app y lo deja de primero.
    // Los demás módulos de AnimeAV1 que ya tenías se desactivan (no se borran) para no ver todo repetido.
    @Synchronized
    fun asegurarIntegrado(prefs: SharedPreferences) {
        migrarCategorias(prefs)
        if (prefs.getInt("integrado_av1", 0) >= VERSION_INTEGRADO) return
        try {
            val codigo = KuroApp.contexto.assets.open("animeav1.js")
                .bufferedReader().use { it.readText() }
            val lista = leerInstalados(prefs)
            val previo = lista.firstOrNull { it.valor.startsWith(MARCA_INTEGRADO) }
            val activo = previo?.activo ?: true
            lista.removeAll { it.valor.startsWith(MARCA_INTEGRADO) }
            for (m in lista) {
                if (m.nombre.contains("animeav1", ignoreCase = true)) m.activo = false
            }
            lista.add(0, Instalado(nuevoId(), "AnimeAV1", "Anime", "codigo", codigo, activo))
            guardarInstalados(prefs, lista)
            prefs.edit().putInt("integrado_av1", VERSION_INTEGRADO).apply()
        } catch (e: Exception) {
            // si falla, se intentará la próxima vez que abras la app
        }
    }

    private fun nuevoId(): String = UUID.randomUUID().toString().take(8)

    private fun nombreDeUrl(u: String): String {
        val n = u.substringAfterLast("/").removeSuffix(".json").removeSuffix(".js")
        return if (n.isEmpty()) u else n
    }

    fun leerInstalados(prefs: SharedPreferences): MutableList<Instalado> {
        val lista = mutableListOf<Instalado>()
        val guardado = prefs.getString("mods2", null)
        if (guardado != null) {
            val arr = JSONArray(guardado)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lista.add(
                    Instalado(
                        o.getString("id"),
                        o.getString("nombre"),
                        o.optString("categoria", "Anime"),
                        o.getString("tipo"),
                        o.getString("valor"),
                        o.optBoolean("activo", true)
                    )
                )
            }
        } else {
            // Migración desde la lista antigua de URLs
            val viejas = mutableListOf<String>()
            val g = prefs.getString("modulos", null)
            if (g != null) {
                val arr = JSONArray(g)
                for (i in 0 until arr.length()) viejas.add(arr.getString(i))
            } else {
                val v = prefs.getString("url", "") ?: ""
                if (v.isNotEmpty()) viejas.add(v)
            }
            for (u in viejas) {
                lista.add(Instalado(nuevoId(), nombreDeUrl(u), "Anime", "url", u, true))
            }
            if (lista.isNotEmpty()) guardarInstalados(prefs, lista)
        }
        return lista
    }

    fun guardarInstalados(prefs: SharedPreferences, lista: List<Instalado>) {
        val arr = JSONArray()
        for (m in lista) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("nombre", m.nombre)
            o.put("categoria", m.categoria)
            o.put("tipo", m.tipo)
            o.put("valor", m.valor)
            o.put("activo", m.activo)
            arr.put(o)
        }
        prefs.edit()
            .putString("mods2", arr.toString())
            .putInt("modulos_ver", prefs.getInt("modulos_ver", 0) + 1)
            .apply()
    }

    // Lo que MainActivity carga: URLs o el texto del módulo, solo de los activos
    fun leerUrls(prefs: SharedPreferences): MutableList<String> =
        leerInstalados(prefs).filter { it.activo }.map { it.valor }.toMutableList()

    fun instalarTexto(prefs: SharedPreferences, texto: String, categoria: String? = null): String {
        val t = texto.trim()
        if (t.isEmpty()) throw IllegalArgumentException("Está vacío")
        val lista = leerInstalados(prefs)

        if (t.startsWith("http")) {
            if (lista.any { it.valor == t }) throw IllegalArgumentException("Ya está instalado")
            val nombre = nombreDeUrl(t)
            lista.add(Instalado(nuevoId(), nombre, categoria ?: detectarCategoria(nombre, t), "url", t, true))
            guardarInstalados(prefs, lista)
            return nombre
        }

        val nombre = if (t.startsWith("{")) {
            // Módulo JSON
            val raiz = JSONObject(t)
            if (raiz.has("modulos")) {
                raiz.optString("nombre", "Índice de módulos")
            } else {
                ModuloParser.parse(t).nombre
            }
        } else {
            // Módulo JavaScript
            Motores.nombreDe(t)
        }
        lista.add(Instalado(nuevoId(), nombre, categoria ?: detectarCategoria(nombre, t.take(4000)), "codigo", t, true))
        guardarInstalados(prefs, lista)
        return nombre
    }

    fun cambiarCategoria(prefs: SharedPreferences, id: String, categoria: String) {
        val lista = leerInstalados(prefs)
        lista.firstOrNull { it.id == id }?.categoria = categoria
        guardarInstalados(prefs, lista)
    }

    fun alternarActivo(prefs: SharedPreferences, id: String) {
        val lista = leerInstalados(prefs)
        val m = lista.firstOrNull { it.id == id } ?: return
        m.activo = !m.activo
        guardarInstalados(prefs, lista)
    }

    fun eliminar(prefs: SharedPreferences, id: String) {
        val lista = leerInstalados(prefs)
        lista.removeAll { it.id == id }
        guardarInstalados(prefs, lista)
    }

    fun cargarTodo(fuentes: List<String>): Resultado {
        // Los módulos se cargan a la vez (máximo 4), no uno detrás de otro
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val tareas = fuentes.map { f ->
            pool.submit(java.util.concurrent.Callable {
                val m = mutableListOf<Modulo>()
                val e = mutableListOf<String>()
                cargarUna(f, true, m, e)
                Pair(m.toList(), e.toList())
            })
        }
        val modulos = mutableListOf<Modulo>()
        val errores = mutableListOf<String>()
        for (t in tareas) {
            val r = t.get()
            modulos.addAll(r.first)
            errores.addAll(r.second)
        }
        pool.shutdown()
        // Si todo falló (sin internet, por ejemplo) se conserva el catálogo anterior
        if (modulos.isNotEmpty()) registro = modulos.flatMap { it.items }
        return Resultado(modulos, errores)
    }

    // ---- Catálogo guardado en disco: al abrir la app se muestra al instante ----
    private fun firma(fuentes: List<String>): String =
        fuentes.joinToString("\u0001").hashCode().toString()

    fun guardarCache(fuentes: List<String>, r: Resultado) {
        try {
            val raiz = JSONObject()
            raiz.put("firma", firma(fuentes))
            val arr = JSONArray()
            for (m in r.modulos) {
                val mo = JSONObject()
                mo.put("nombre", m.nombre)
                mo.put("fuente", m.fuente.ifEmpty { m.items.firstOrNull()?.fuente ?: "" })
                mo.put("icono", m.icono)
                mo.put("version", m.version)
                val fArr = JSONArray()
                for (f in m.filtros) {
                    val fo = JSONObject()
                    fo.put("k", f.clave)
                    fo.put("n", f.nombre)
                    val oa = JSONArray()
                    for ((on, oi) in f.opciones) {
                        oa.put(JSONObject().put("n", on).put("i", oi))
                    }
                    fo.put("o", oa)
                    fArr.put(fo)
                }
                mo.put("filtros", fArr)
                val lista = JSONArray()
                for (item in m.items) {
                    val o = JSONObject()
                    o.put("titulo", item.titulo)
                    o.put("portada", item.portada)
                    o.put("descripcion", item.descripcion)
                    o.put("modulo", item.modulo)
                    o.put("tipo", item.tipo)
                    o.put("estado", item.estado)
                    o.put("ultimo", item.ultimo)
                    o.put("id", item.id)
                    o.put("generos", JSONArray(item.generos))
                    val rels = JSONArray()
                    for (rel in item.relacionados) rels.put(JSONObject().put("titulo", rel.titulo).put("relacion", rel.relacion).put("portada", rel.portada).put("id", rel.id).put("fuente", rel.fuente).put("alternativas", JSONArray(rel.alternativas)))
                    o.put("relacionados", rels)
                    o.put("ep", JSONArray(ModuloParser.episodiosAJson(item.episodios)))
                    lista.put(o)
                }
                mo.put("items", lista)
                arr.put(mo)
            }
            raiz.put("modulos", arr)
            File(KuroApp.contexto.filesDir, "catalogo.json").writeText(raiz.toString())
        } catch (e: Exception) {
            // sin caché, no pasa nada
        }
    }

    fun leerCache(fuentes: List<String>): Resultado? {
        try {
            val f = File(KuroApp.contexto.filesDir, "catalogo.json")
            if (!f.exists()) return null
            val raiz = JSONObject(f.readText())
            if (raiz.optString("firma") != firma(fuentes)) return null
            val modulos = mutableListOf<Modulo>()
            val arr = raiz.getJSONArray("modulos")
            for (i in 0 until arr.length()) {
                val mo = arr.getJSONObject(i)
                val fuente = mo.optString("fuente", "")
                val its = mo.getJSONArray("items")
                val lista = mutableListOf<Item>()
                for (j in 0 until its.length()) {
                    val o = its.getJSONObject(j)
                    val gs = mutableListOf<String>()
                    val ga = o.optJSONArray("generos")
                    if (ga != null) for (k in 0 until ga.length()) gs.add(ga.getString(k))
                    val rels = mutableListOf<Relacionado>()
                    val ra = o.optJSONArray("relacionados")
                    if (ra != null) for (r in 0 until ra.length()) {
                        val ro = ra.optJSONObject(r) ?: continue
                        rels.add(Relacionado(ro.optString("titulo"), ro.optString("relacion"), ro.optString("portada"), ro.optString("id"), ro.optString("fuente"), (ro.optJSONArray("alternativas")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList())))
                    }
                    lista.add(
                        Item(
                            titulo = o.getString("titulo"),
                            episodios = ModuloParser.episodiosDeJson(
                                (o.optJSONArray("ep") ?: JSONArray()).toString()
                            ),
                            portada = o.optString("portada", ""),
                            descripcion = o.optString("descripcion", ""),
                            modulo = o.optString("modulo", ""),
                            generos = gs,
                            tipo = o.optString("tipo", ""),
                            estado = o.optString("estado", ""),
                            ultimo = o.optString("ultimo", ""),
                            id = o.optString("id", ""),
                            fuente = fuente,
                            relacionados = rels
                        )
                    )
                }
                val filtros = mutableListOf<Filtro>()
                val fArr = mo.optJSONArray("filtros")
                if (fArr != null) {
                    for (k in 0 until fArr.length()) {
                        val fo = fArr.optJSONObject(k) ?: continue
                        val oa = fo.optJSONArray("o") ?: continue
                        val ops = mutableListOf<Pair<String, String>>()
                        for (q in 0 until oa.length()) {
                            val oo = oa.optJSONObject(q) ?: continue
                            ops.add(Pair(oo.optString("n"), oo.optString("i")))
                        }
                        filtros.add(Filtro(fo.optString("k"), fo.optString("n"), ops))
                    }
                }
                modulos.add(
                    Modulo(
                        mo.getString("nombre"), lista, fuente, filtros,
                        mo.optString("icono", ""), mo.optString("version", "")
                    )
                )
            }
            if (modulos.isEmpty()) return null
            registro = modulos.flatMap { it.items }
            return Resultado(modulos, emptyList())
        } catch (e: Exception) {
            return null
        }
    }

    private fun cargarUna(
        fuente: String,
        permitirIndice: Boolean,
        modulos: MutableList<Modulo>,
        errores: MutableList<String>
    ) {
        val esUrl = fuente.trimStart().startsWith("http")
        try {
            val texto = if (esUrl) URL(fuente.trim()).readText() else fuente
            if (texto.trimStart().startsWith("{")) {
                val raiz = JSONObject(texto)
                val indice = raiz.optJSONArray("modulos")
                if (indice != null) {
                    if (permitirIndice) {
                        for (i in 0 until indice.length()) {
                            cargarUna(indice.getString(i), false, modulos, errores)
                        }
                    }
                } else {
                    modulos.add(ModuloParser.parse(texto, fuente))
                }
            } else {
                modulos.add(Motores.cargar(texto))
            }
        } catch (e: Exception) {
            val etiqueta = if (esUrl) fuente.substringAfterLast("/") else "módulo pegado"
            errores.add(etiqueta + ": " + (e.message ?: "error"))
        }
    }
}
