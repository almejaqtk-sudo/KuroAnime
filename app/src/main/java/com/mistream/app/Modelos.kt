package com.mistream.app

import org.json.JSONArray
import org.json.JSONObject

data class Subtitulo(
    val url: String,
    val idioma: String = "es",
    val nombre: String = "Subtítulos"
)

data class ServerLink(
    val nombre: String,
    val url: String,
    val tipo: String = "auto",
    val headers: Map<String, String> = emptyMap(),
    val subtitulos: List<Subtitulo> = emptyList(),
    // Idioma del servidor: "DUB" (audio en español), "SUB" (subtitulado) o vacío si no se sabe
    val idioma: String = ""
)

data class Episodio(val titulo: String, val servidores: List<ServerLink>)

data class Relacionado(val titulo: String, val relacion: String = "", val portada: String = "", val id: String = "", val fuente: String = "")

data class Item(
    val titulo: String,
    val episodios: List<Episodio>,
    val portada: String = "",
    val descripcion: String = "",
    val modulo: String = "",
    val generos: List<String> = emptyList(),
    val tipo: String = "",
    val estado: String = "",
    val ultimo: String = "",
    val id: String = "",
    val fuente: String = "",
    val relacionados: List<Relacionado> = emptyList()
)

// Un filtro que tiene el sitio (Género, Tipo, Estado, Año...). clave = nombre del parámetro del sitio,
// opciones = (texto que se ve, valor que se manda al sitio)
data class Filtro(
    val clave: String,
    val nombre: String,
    val opciones: List<Pair<String, String>>
)

data class Modulo(
    val nombre: String,
    val items: List<Item>,
    // Código del módulo (para pedirle más páginas) y los filtros que tiene su sitio
    val fuente: String = "",
    val filtros: List<Filtro> = emptyList(),
    val icono: String = "",
    val version: String = ""
)

object ModuloParser {

    private fun servidorDeObjeto(o: JSONObject): ServerLink {
        val headers = mutableMapOf<String, String>()
        val ho = o.optJSONObject("headers")
        if (ho != null) {
            for (k in ho.keys()) headers[k] = ho.getString(k)
        }
        val subs = mutableListOf<Subtitulo>()
        val sa = o.optJSONArray("subtitulos")
        if (sa != null) {
            for (i in 0 until sa.length()) {
                val so = sa.getJSONObject(i)
                subs.add(
                    Subtitulo(
                        so.getString("url"),
                        so.optString("idioma", "es"),
                        so.optString("nombre", "Subtítulos")
                    )
                )
            }
        }
        return ServerLink(
            o.optString("nombre", "Servidor"),
            o.getString("url"),
            o.optString("tipo", "auto"),
            headers,
            subs,
            o.optString("idioma", "")
        )
    }

    private fun servidorAObjeto(s: ServerLink): JSONObject {
        val o = JSONObject()
        o.put("nombre", s.nombre)
        o.put("url", s.url)
        o.put("tipo", s.tipo)
        o.put("headers", JSONObject(s.headers))
        o.put("idioma", s.idioma)
        val sa = JSONArray()
        for (sub in s.subtitulos) {
            val so = JSONObject()
            so.put("url", sub.url)
            so.put("idioma", sub.idioma)
            so.put("nombre", sub.nombre)
            sa.put(so)
        }
        o.put("subtitulos", sa)
        return o
    }

    private fun episodioDeObjeto(oe: JSONObject): Episodio {
        val servs = mutableListOf<ServerLink>()
        val arrS = oe.getJSONArray("servidores")
        for (k in 0 until arrS.length()) {
            servs.add(servidorDeObjeto(arrS.getJSONObject(k)))
        }
        return Episodio(oe.getString("titulo"), servs)
    }

    // Convierte un título (de JSON o de un módulo JS) en un Item.
    // Los episodios son opcionales: si faltan, se piden después con episodios(id).
    fun itemDeObjeto(oi: JSONObject, nombreModulo: String, fuente: String = ""): Item {
        val eps = mutableListOf<Episodio>()
        val arrEps = oi.optJSONArray("episodios")
        if (arrEps != null) {
            for (j in 0 until arrEps.length()) {
                eps.add(episodioDeObjeto(arrEps.getJSONObject(j)))
            }
        }
        val gs = mutableListOf<String>()
        val ga = oi.optJSONArray("generos")
        if (ga != null) {
            for (g in 0 until ga.length()) gs.add(ga.getString(g))
        }
        val relacionados = mutableListOf<Relacionado>()
        val ra = oi.optJSONArray("relacionados")
        if (ra != null) for (r in 0 until ra.length()) {
            val ro = ra.optJSONObject(r) ?: continue
            relacionados.add(Relacionado(
                ro.optString("titulo", ""),
                ro.optString("relacion", ro.optString("tipo", "")),
                ro.optString("portada", ""),
                ro.optString("id", ""),
                ro.optString("fuente", "")
            ))
        }
        return Item(
            oi.getString("titulo"),
            eps,
            oi.optString("portada", ""),
            oi.optString("descripcion", ""),
            nombreModulo,
            gs,
            oi.optString("tipo", ""),
            oi.optString("estado", ""),
            oi.optString("ultimo", ""),
            oi.optString("id", ""),
            fuente,
            relacionados
        )
    }

    fun parse(texto: String, fuente: String = ""): Modulo {
        val raiz = JSONObject(texto)
        val nombreModulo = raiz.optString("nombre", "Módulo")
        val items = mutableListOf<Item>()
        val arrItems = raiz.getJSONArray("items")
        for (i in 0 until arrItems.length()) {
            items.add(itemDeObjeto(arrItems.getJSONObject(i), nombreModulo, fuente))
        }
        return Modulo(nombreModulo, items, fuente)
    }

    fun episodiosAJson(lista: List<Episodio>): String {
        val arr = JSONArray()
        for (e in lista) {
            val o = JSONObject()
            o.put("titulo", e.titulo)
            val sa = JSONArray()
            for (s in e.servidores) sa.put(servidorAObjeto(s))
            o.put("servidores", sa)
            arr.put(o)
        }
        return arr.toString()
    }

    fun episodiosDeJson(texto: String): List<Episodio> {
        val arr = JSONArray(texto)
        val lista = mutableListOf<Episodio>()
        for (i in 0 until arr.length()) {
            lista.add(episodioDeObjeto(arr.getJSONObject(i)))
        }
        return lista
    }
}
