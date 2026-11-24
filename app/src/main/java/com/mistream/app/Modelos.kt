package com.mistream.app

import org.json.JSONArray
import org.json.JSONObject

data class ServerLink(
    val nombre: String,
    val url: String,
    val tipo: String = "auto",
    val headers: Map<String, String> = emptyMap()
)

data class Episodio(val titulo: String, val servidores: List<ServerLink>)
data class Item(val titulo: String, val episodios: List<Episodio>)
data class Modulo(val nombre: String, val items: List<Item>)

object ModuloParser {

    private fun parseServidor(o: JSONObject): ServerLink {
        val headers = mutableMapOf<String, String>()
        val ho = o.optJSONObject("headers")
        if (ho != null) {
            for (k in ho.keys()) headers[k] = ho.getString(k)
        }
        return ServerLink(
            o.optString("nombre", "Servidor"),
            o.getString("url"),
            o.optString("tipo", "auto"),
            headers
        )
    }

    fun parse(texto: String): Modulo {
        val raiz = JSONObject(texto)
        val items = mutableListOf<Item>()
        val arrItems = raiz.getJSONArray("items")
        for (i in 0 until arrItems.length()) {
            val oi = arrItems.getJSONObject(i)
            val eps = mutableListOf<Episodio>()
            val arrEps = oi.getJSONArray("episodios")
            for (j in 0 until arrEps.length()) {
                val oe = arrEps.getJSONObject(j)
                val servs = mutableListOf<ServerLink>()
                val arrS = oe.getJSONArray("servidores")
                for (k in 0 until arrS.length()) {
                    servs.add(parseServidor(arrS.getJSONObject(k)))
                }
                eps.add(Episodio(oe.getString("titulo"), servs))
            }
            items.add(Item(oi.getString("titulo"), eps))
        }
        return Modulo(raiz.optString("nombre", "Módulo"), items)
    }

    fun servidoresAJson(lista: List<ServerLink>): String {
        val arr = JSONArray()
        for (s in lista) {
            val o = JSONObject()
            o.put("nombre", s.nombre)
            o.put("url", s.url)
            o.put("tipo", s.tipo)
            o.put("headers", JSONObject(s.headers))
            arr.put(o)
        }
        return arr.toString()
    }

    fun servidoresDeJson(texto: String): List<ServerLink> {
        val arr = JSONArray(texto)
        val lista = mutableListOf<ServerLink>()
        for (i in 0 until arr.length()) lista.add(parseServidor(arr.getJSONObject(i)))
        return lista
    }
}
