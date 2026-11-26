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


    private fun leerCarpetas(prefs: SharedPreferences): org.json.JSONObject =
        try { org.json.JSONObject(prefs.getString("favoritos_carpetas", "{}") ?: "{}") } catch (_: Exception) { org.json.JSONObject() }

    fun nombresCarpetas(prefs: SharedPreferences): List<String> {
        val o = leerCarpetas(prefs); val out = mutableListOf<String>()
        val it = o.keys(); while (it.hasNext()) out.add(it.next())
        return out.sorted()
    }

    fun crearCarpeta(prefs: SharedPreferences, nombre: String): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        val o = leerCarpetas(prefs)
        if (o.has(n)) return false
        o.put(n, JSONArray())
        prefs.edit().putString("favoritos_carpetas", o.toString()).apply()
        return true
    }

    fun agregarACarpeta(prefs: SharedPreferences, carpeta: String, titulo: String) {
        val o = leerCarpetas(prefs); val a = o.optJSONArray(carpeta) ?: JSONArray()
        for (i in 0 until a.length()) if (a.optString(i) == titulo) { o.put(carpeta, a); prefs.edit().putString("favoritos_carpetas", o.toString()).apply(); return }
        a.put(titulo); o.put(carpeta, a)
        prefs.edit().putString("favoritos_carpetas", o.toString()).apply()
    }

    fun titulosCarpeta(prefs: SharedPreferences, carpeta: String): Set<String> {
        val a = leerCarpetas(prefs).optJSONArray(carpeta) ?: return emptySet()
        return (0 until a.length()).map { a.optString(it) }.toSet()
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

// Progreso de cada anime: episodios vistos, posición donde te quedaste y último episodio abierto
object Progreso {

    private const val UMBRAL_VISTO = 0.85

    fun clavePos(titulo: String, ep: String) = "pos_" + titulo + "_" + ep
    private fun claveVisto(titulo: String, ep: String) = "visto_" + titulo + "_" + ep

    fun posicion(prefs: SharedPreferences, titulo: String, ep: String): Long =
        prefs.getLong(clavePos(titulo, ep), 0L)

    fun esVisto(prefs: SharedPreferences, titulo: String, ep: String): Boolean =
        prefs.getBoolean(claveVisto(titulo, ep), false)

    fun marcarVisto(prefs: SharedPreferences, titulo: String, ep: String) {
        prefs.edit().putBoolean(claveVisto(titulo, ep), true).remove(clavePos(titulo, ep)).apply()
    }

    fun guardarUltimo(prefs: SharedPreferences, titulo: String, ep: String) {
        prefs.edit().putString("ult_" + titulo, ep).apply()
    }

    fun ultimo(prefs: SharedPreferences, titulo: String): String? =
        prefs.getString("ult_" + titulo, null)

    // Llamado mientras ves un episodio: guarda dónde vas, o lo marca como visto si ya casi termina
    fun actualizar(prefs: SharedPreferences, titulo: String, ep: String, pos: Long, dur: Long) {
        if (dur > 0 && pos >= dur * UMBRAL_VISTO) {
            marcarVisto(prefs, titulo, ep)
        } else if (pos > 5000) {
            prefs.edit().putLong(clavePos(titulo, ep), pos).apply()
        }
    }
}
