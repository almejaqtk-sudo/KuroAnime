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
    val subtitulos: List<Subtitulo> = emptyList()
)

data class Episodio(val titulo: String, val servidores: List<ServerLink>)

data class Item(
    val titulo: String,
    val episodios: List<Episodio>,
    val portada: String = "",
    val descripcion: String = "",
    val modulo: String = "",
    val generos: List<String> = emptyList(),
    val tipo: String = "",
    val estado: String = "",
    val ultimo: String = ""
)

data class Modulo(val nombre: String, val items: List<Item>)

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
            subs
        )
    }

    private fun servidorAObjeto(s: ServerLink): JSONObject {
        val o = JSONObject()
        o.put("nombre", s.nombre)
        o.put("url", s.url)
        o.put("tipo", s.tipo)
        o.put("headers", JSONObject(s.headers))
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

    fun parse(texto: String): Modulo {
        val raiz = JSONObject(texto)
        val nombreModulo = raiz.optString("nombre", "Módulo")
        val items = mutableListOf<Item>()
        val arrItems = raiz.getJSONArray("items")
        for (i in 0 until arrItems.length()) {
            val oi = arrItems.getJSONObject(i)
            val eps = mutableListOf<Episodio>()
            val arrEps = oi.getJSONArray("episodios")
            for (j in 0 until arrEps.length()) {
                eps.add(episodioDeObjeto(arrEps.getJSONObject(j)))
            }
            val gs = mutableListOf<String>()
            val ga = oi.optJSONArray("generos")
            if (ga != null) {
                for (g in 0 until ga.length()) gs.add(ga.getString(g))
            }
            items.add(
                Item(
                    oi.getString("titulo"),
                    eps,
                    oi.optString("portada", ""),
                    oi.optString("descripcion", ""),
                    nombreModulo,
                    gs,
                    oi.optString("tipo", ""),
                    oi.optString("estado", ""),
                    oi.optString("ultimo", "")
                )
            )
        }
        return Modulo(nombreModulo, items)
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
