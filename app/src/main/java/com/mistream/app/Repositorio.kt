package com.mistream.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

object Repositorio {

    class Resultado(val modulos: List<Modulo>, val errores: List<String>)

    fun leerUrls(prefs: SharedPreferences): MutableList<String> {
        val lista = mutableListOf<String>()
        val guardado = prefs.getString("modulos", null)
        if (guardado != null) {
            val arr = JSONArray(guardado)
            for (i in 0 until arr.length()) lista.add(arr.getString(i))
        } else {
            val vieja = prefs.getString("url", "") ?: ""
            if (vieja.isNotEmpty()) {
                lista.add(vieja)
                guardarUrls(prefs, lista)
            }
        }
        return lista
    }

    fun guardarUrls(prefs: SharedPreferences, lista: List<String>) {
        val arr = JSONArray()
        for (u in lista) arr.put(u)
        prefs.edit()
            .putString("modulos", arr.toString())
            .putInt("modulos_ver", prefs.getInt("modulos_ver", 0) + 1)
            .apply()
    }

    fun cargarTodo(urls: List<String>): Resultado {
        val modulos = mutableListOf<Modulo>()
        val errores = mutableListOf<String>()
        for (u in urls) cargarUna(u, true, modulos, errores)
        return Resultado(modulos, errores)
    }

    private fun cargarUna(
        url: String,
        permitirIndice: Boolean,
        modulos: MutableList<Modulo>,
        errores: MutableList<String>
    ) {
        try {
            val texto = URL(url).readText()
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
        } catch (e: Exception) {
            errores.add(url.substringAfterLast("/") + ": " + (e.message ?: "error"))
        }
    }
}
