package com.mistream.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
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
        val modulos = mutableListOf<Modulo>()
        val errores = mutableListOf<String>()
        for (f in fuentes) cargarUna(f, true, modulos, errores)
        registro = modulos.flatMap { it.items }
        return Resultado(modulos, errores)
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
