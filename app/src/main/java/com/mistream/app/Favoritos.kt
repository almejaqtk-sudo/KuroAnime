package com.mistream.app

import android.content.SharedPreferences
import org.json.JSONArray

object Favoritos {

    fun leer(prefs: SharedPreferences): MutableSet<String> {
        val set = linkedSetOf<String>()
        val guardado = prefs.getString("favoritos", null)
        if (guardado != null) {
            val arr = JSONArray(guardado)
            for (i in 0 until arr.length()) set.add(arr.getString(i))
        }
        return set
    }

    private fun guardar(prefs: SharedPreferences, set: Set<String>) {
        val arr = JSONArray()
        for (t in set) arr.put(t)
        prefs.edit().putString("favoritos", arr.toString()).apply()
    }

    fun esFavorito(prefs: SharedPreferences, titulo: String): Boolean =
        leer(prefs).contains(titulo)

    fun alternar(prefs: SharedPreferences, titulo: String): Boolean {
        val set = leer(prefs)
        val ahora = if (set.contains(titulo)) {
            set.remove(titulo)
            false
        } else {
            set.add(titulo)
            true
        }
        guardar(prefs, set)
        return ahora
    }
}
