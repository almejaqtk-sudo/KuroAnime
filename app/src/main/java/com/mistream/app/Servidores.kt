package com.mistream.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Todo lo de los servidores de video: idioma/bandera, selector y comprobación de cuáles funcionan
object Servidores {

    const val DUB = "DUB"
    const val SUB = "SUB"

    // Convierte lo que diga el módulo ("DUB", "lat", "Latino", "sub"...) en DUB, SUB o vacío
    fun normalizarIdioma(lang: String, etiqueta: String = ""): String {
        val l = lang.trim().lowercase()
        val dub = setOf("dub", "dubbed", "doblado", "lat", "latino", "es-mx", "es_mx", "mx", "es", "spa", "esp", "castellano", "audio")
        val sub = setOf("sub", "subbed", "subtitulado", "jp", "ja", "jpn", "jap", "japones", "japonés", "sub-es", "sub-esp")
        if (l in dub) return DUB
        if (l in sub) return SUB
        val e = etiqueta.lowercase()
        return when {
            l.contains("dub") || l.contains("latin") -> DUB
            l.contains("sub") -> SUB
            e.contains("latino") || Regex("\\bdub\\b").containsMatchIn(e) -> DUB
            Regex("\\bsub\\b").containsMatchIn(e) -> SUB
            else -> ""
        }
    }

    // Audio en español -> México, subtitulado -> Japón
    fun bandera(s: ServerLink): String = when (normalizarIdioma(s.idioma, s.nombre)) {
        DUB -> "\uD83C\uDDF2\uD83C\uDDFD"
        SUB -> "\uD83C\uDDEF\uD83C\uDDF5"
        else -> ""
    }

    // Clave que identifica a un servidor (nombre + idioma): el mismo servidor en SUB y en DUB son distintos
    fun clave(s: ServerLink): String = s.nombre + "|" + normalizarIdioma(s.idioma, s.nombre)

    private fun tipoTexto(s: ServerLink): String {
        val modo = when (s.tipo) {
            "embed" -> "Web"
            "external" -> "Navegador"
            else -> "Directo"
        }
        val idioma = when (normalizarIdioma(s.idioma, s.nombre)) {
            DUB -> "Español"
            SUB -> "Subtitulado"
            else -> ""
        }
        return if (idioma.isEmpty()) modo else "$modo · $idioma"
    }

    // ---------- Selector con banderas ----------

    fun mostrarSelector(
        act: Activity,
        titulo: String,
        lista: List<ServerLink>,
        preferido: String?,
        alElegir: (Int) -> Unit,
        alCancelar: () -> Unit = {}
    ) {
        val dp = { v: Int -> Ui.dp(act, v) }
        val lv = ListView(act)
        lv.divider = null
        lv.setBackgroundColor(Color.TRANSPARENT)
        var dialogo: AlertDialog? = null

        lv.adapter = object : BaseAdapter() {
            override fun getCount() = lista.size
            override fun getItem(position: Int): Any = lista[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val s = lista[position]
                val fila = LinearLayout(act)
                fila.orientation = LinearLayout.HORIZONTAL
                fila.gravity = Gravity.CENTER_VERTICAL
                fila.setPadding(dp(18), dp(12), dp(18), dp(12))

                val col = LinearLayout(act)
                col.orientation = LinearLayout.VERTICAL
                val nombre = TextView(act)
                nombre.text = (if (clave(s) == preferido) "★ " else "") + s.nombre
                nombre.setTextColor(Color.WHITE)
                nombre.textSize = 17f
                nombre.setTypeface(null, Typeface.BOLD)
                nombre.maxLines = 1
                col.addView(nombre)
                val sub = TextView(act)
                sub.text = tipoTexto(s)
                sub.setTextColor(Ui.GRIS)
                sub.textSize = 12f
                col.addView(sub)
                fila.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                // La bandera va al costado del servidor
                val b = TextView(act)
                b.text = bandera(s)
                b.textSize = 26f
                b.gravity = Gravity.CENTER
                fila.addView(b, LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT))
                return fila
            }
        }
        lv.setOnItemClickListener { _, _, pos, _ ->
            dialogo?.dismiss()
            alElegir(pos)
        }

        val d = AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(titulo)
            .setView(lv)
            .setNegativeButton("Cancelar", null)
            .setOnCancelListener { alCancelar() }
            .create()
        dialogo = d
        d.show()
    }

    // ---------- Comprobar cuáles servidores funcionan ----------

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val marcasError = listOf(
        "file not found", "video not found", "file was deleted", "file is no longer available",
        "has been removed", "has been deleted", "video no existe", "archivo no existe", "no encontrado"
    )

    private fun funciona(s: ServerLink): Boolean {
        if (s.tipo == "external") return true
        val directo = s.tipo != "embed"
        var con: HttpURLConnection? = null
        return try {
            con = URL(s.url).openConnection() as HttpURLConnection
            con.connectTimeout = 6000
            con.readTimeout = 7000
            con.instanceFollowRedirects = true
            con.setRequestProperty("User-Agent", UA)
            for ((k, v) in s.headers) con.setRequestProperty(k, v)
            if (directo && s.tipo != "hls") con.setRequestProperty("Range", "bytes=0-1023")
            val codigo = con.responseCode
            if (directo) {
                if (codigo !in 200..299) return false
                if (s.tipo == "hls") {
                    val cab = con.inputStream.use { it.readNBytesCompat(512) }
                    String(cab).contains("#EXTM3U")
                } else true
            } else {
                // Páginas web: solo se descartan las que claramente están caídas. Un 403 puede ser
                // una protección que sí deja pasar al WebView, así que ese se conserva.
                if (codigo == 404 || codigo == 410 || codigo >= 500) return false
                if (codigo in 200..299) {
                    val cuerpo = String(con.inputStream.use { it.readNBytesCompat(20000) }).lowercase()
                    !(cuerpo.length < 3000 && marcasError.any { cuerpo.contains(it) })
                } else true
            }
        } catch (e: Exception) {
            false
        } finally {
            con?.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val buf = ByteArray(max)
        var total = 0
        while (total < max) {
            val n = read(buf, total, max - total)
            if (n <= 0) break
            total += n
        }
        return buf.copyOf(total)
    }

    // Se queda solo con los servidores que responden. Si ninguno responde, devuelve la lista original
    // (puede ser un problema de conexión y no de los servidores).
    fun filtrarFuncionales(lista: List<ServerLink>): List<ServerLink> {
        if (lista.size <= 1) return lista
        val pool = Executors.newFixedThreadPool(minOf(lista.size, 8))
        try {
            val tareas = lista.map { s -> Callable { funciona(s) } }
            val resultados = pool.invokeAll(tareas, 10, TimeUnit.SECONDS)
            val buenos = lista.filterIndexed { i, _ ->
                val f = resultados[i]
                !f.isCancelled && try { f.get() } catch (e: Exception) { false }
            }
            return if (buenos.isEmpty()) lista else buenos
        } finally {
            pool.shutdownNow()
        }
    }
}
