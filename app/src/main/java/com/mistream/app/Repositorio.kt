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

    val CATEGORIAS = listOf("Anime", "Películas", "Series", "Otros")

    // Títulos cargados en memoria, para encontrarlos al abrir el detalle
    @Volatile
    private var registro: List<Item> = emptyList()

    fun buscarItem(titulo: String, portada: String): Item? =
        registro.firstOrNull { it.titulo == titulo && it.portada == portada }

    // Títulos que llegaron después (por ejemplo, al filtrar por género): se pueden abrir igual
    fun agregarARegistro(nuevos: List<Item>) {
        registro = registro + nuevos
    }

    // ---- Módulo AnimeAV1 integrado en la app ----
    private const val VERSION_INTEGRADO = 1
    private const val MARCA_INTEGRADO = "// AnimeAV1 (módulo integrado en KuroAnime)"

    // Instala (o actualiza) el módulo AnimeAV1 que viene dentro de la app y lo deja de primero.
    // Los demás módulos de AnimeAV1 que ya tenías se desactivan (no se borran) para no ver todo repetido.
    @Synchronized
    fun asegurarIntegrado(prefs: SharedPreferences) {
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

    fun instalarTexto(prefs: SharedPreferences, texto: String, categoria: String): String {
        val t = texto.trim()
        if (t.isEmpty()) throw IllegalArgumentException("Está vacío")
        val lista = leerInstalados(prefs)

        if (t.startsWith("http")) {
            if (lista.any { it.valor == t }) throw IllegalArgumentException("Ya está instalado")
            val nombre = nombreDeUrl(t)
            lista.add(Instalado(nuevoId(), nombre, categoria, "url", t, true))
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
        lista.add(Instalado(nuevoId(), nombre, categoria, "codigo", t, true))
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
                val gArr = JSONArray()
                for ((gn, gi) in m.generos) {
                    val go = JSONObject()
                    go.put("n", gn)
                    go.put("i", gi)
                    gArr.put(go)
                }
                mo.put("generos", gArr)
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
                            fuente = fuente
                        )
                    )
                }
                val generos = mutableListOf<Pair<String, String>>()
                val gArr = mo.optJSONArray("generos")
                if (gArr != null) {
                    for (k in 0 until gArr.length()) {
                        val go = gArr.optJSONObject(k) ?: continue
                        generos.add(Pair(go.optString("n"), go.optString("i")))
                    }
                }
                modulos.add(Modulo(mo.getString("nombre"), lista, fuente, generos))
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
                    modulos.add(ModuloParser.parse(texto))
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
