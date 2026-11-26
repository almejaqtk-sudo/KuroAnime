package com.mistream.app

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
            e.contains("latino") || e.contains("castellano") || Regex("\\bdub\\b").containsMatchIn(e) -> DUB
            e.contains("subtitul") || Regex("\\bsub\\b").containsMatchIn(e) -> SUB
            else -> ""
        }
    }

    // Audio en español -> México, subtitulado -> Japón
    fun bandera(s: ServerLink): String = when (normalizarIdioma(s.idioma, s.nombre)) {
        DUB -> "\uD83C\uDDF2\uD83C\uDDFD"
        SUB -> "\uD83C\uDDEF\uD83C\uDDF5"
        else -> ""
    }

    // Texto de la píldora que va al costado del servidor
    private fun textoIdioma(s: ServerLink): String = when (normalizarIdioma(s.idioma, s.nombre)) {
        DUB -> "Español latino"
        SUB -> "Sub español"
        else -> ""
    }

    // Clave que identifica a un servidor (nombre + idioma): el mismo servidor en SUB y en DUB son distintos
    fun clave(s: ServerLink): String = s.nombre + "|" + normalizarIdioma(s.idioma, s.nombre)

    // Nombres como se ven en la lista: si el mismo servidor sale varias veces en el mismo idioma,
    // el segundo se llama "Mp4upload 2", el tercero "Mp4upload 3"...
    fun nombresVisibles(lista: List<ServerLink>): List<String> {
        val cuenta = HashMap<String, Int>()
        return lista.map { s ->
            // El idioma ya va en la píldora: se quita del nombre ("Streamwish · Latino" -> "Streamwish")
            val base = s.nombre.replace(
                Regex("\\s*[·|\\-–—]\\s*(latino|espa[ñn]ol|castellano|subtitulado|sub)\\s*$", RegexOption.IGNORE_CASE), ""
            ).trim().ifEmpty { s.nombre.trim() }
            val k = base + "|" + normalizarIdioma(s.idioma, s.nombre)
            val n = (cuenta[k] ?: 0) + 1
            cuenta[k] = n
            if (n > 1) "$base $n" else base
        }
    }

    // Clave del servidor número i de la lista (distingue "Mp4upload" de "Mp4upload 2")
    fun claveVisible(lista: List<ServerLink>, i: Int): String {
        val s = lista[i]
        return nombresVisibles(lista)[i] + "|" + normalizarIdioma(s.idioma, s.nombre)
    }

    // Posición del servidor guardado como preferido, o -1 si este episodio no lo tiene
    fun indiceDePref(lista: List<ServerLink>, pref: String?): Int {
        if (pref == null) return -1
        return lista.indices.indexOfFirst { claveVisible(lista, it) == pref }
    }

    // Busca el servidor que se estaba usando aunque cambie el número o el idioma:
    // primero el idéntico, luego el mismo nombre ("Mp4upload 2" = "Mp4upload") con el mismo idioma o con otro
    fun indiceParecido(lista: List<ServerLink>, pref: String?): Int {
        if (pref == null) return -1
        val exacto = indiceDePref(lista, pref)
        if (exacto >= 0) return exacto
        val sinNumero = Regex("\\s+\\d+$")
        val nombre = pref.substringBeforeLast("|").replace(sinNumero, "").trim().lowercase()
        val idioma = pref.substringAfterLast("|", "")
        val nombres = nombresVisibles(lista).map { it.replace(sinNumero, "").trim().lowercase() }
        val mismos = lista.indices.filter { nombres[it] == nombre }
        if (mismos.isEmpty()) return -1
        return mismos.firstOrNull { normalizarIdioma(lista[it].idioma, lista[it].nombre) == idioma } ?: mismos.first()
    }

    // ---------- Selector (hoja inferior: círculo + nombre + idioma) ----------

    private val FONDO_HOJA = Color.parseColor("#1F1D36")
    private val ANILLO = Color.parseColor("#8E8AA8")
    private val TURQUESA = Color.parseColor("#2DD4BF")
    private val TEXTO_LAT = Color.parseColor("#5EEAD4")

    private class ScrollMax(act: Activity, val maxAlto: Int) : ScrollView(act) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(maxAlto, View.MeasureSpec.AT_MOST)
            )
        }
    }

    private fun circulo(act: Activity, marcado: Boolean): View {
        val dp = { v: Int -> Ui.dp(act, v) }
        val caja = FrameLayout(act)
        val aro = View(act)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(Color.TRANSPARENT)
        g.setStroke(dp(2), if (marcado) Ui.ROSA else ANILLO)
        aro.background = g
        caja.addView(aro, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
        if (marcado) {
            val punto = View(act)
            val p = GradientDrawable()
            p.shape = GradientDrawable.OVAL
            p.setColor(Ui.ROSA)
            punto.background = p
            caja.addView(punto, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
        }
        return caja
    }

    private fun pildoraIdioma(act: Activity, s: ServerLink): View? {
        val texto = textoIdioma(s)
        if (texto.isEmpty()) return null
        val dp = { v: Int -> Ui.dp(act, v) }
        val t = TextView(act)
        t.text = bandera(s) + "  " + texto
        t.setTextColor(if (normalizarIdioma(s.idioma, s.nombre) == DUB) TEXTO_LAT else Ui.ROSA)
        t.textSize = 16f
        t.setTypeface(null, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        val g = GradientDrawable()
        g.cornerRadius = dp(30).toFloat()
        g.setColor(Color.TRANSPARENT)
        g.setStroke(dp(2), TURQUESA)
        t.background = g
        return t
    }

    fun mostrarSelector(
        act: Activity,
        titulo: String,
        lista: List<ServerLink>,
        preferido: String?,
        alElegir: (Int) -> Unit,
        alCancelar: () -> Unit = {}
    ) {
        val dp = { v: Int -> Ui.dp(act, v) }
        val pantalla = act.resources.displayMetrics
        val d = Dialog(act)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val hoja = LinearLayout(act)
        hoja.orientation = LinearLayout.VERTICAL
        val fondo = GradientDrawable()
        fondo.setColor(FONDO_HOJA)
        val r = dp(28).toFloat()
        fondo.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        hoja.background = fondo
        hoja.setPadding(dp(22), dp(24), dp(22), dp(16))

        // Cabecera: título y X para cerrar
        val cab = LinearLayout(act)
        cab.orientation = LinearLayout.HORIZONTAL
        cab.gravity = Gravity.CENTER_VERTICAL
        val t = TextView(act)
        t.text = titulo
        t.setTextColor(Color.WHITE)
        t.textSize = 22f
        t.setTypeface(null, Typeface.BOLD)
        cab.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val x = TextView(act)
        x.text = "✕"
        x.setTextColor(Color.WHITE)
        x.textSize = 24f
        x.gravity = Gravity.CENTER
        x.setOnClickListener { d.cancel() }
        cab.addView(x, LinearLayout.LayoutParams(dp(48), dp(48)))
        hoja.addView(cab)

        val nombres = nombresVisibles(lista)
        val marcado = indiceDePref(lista, preferido)
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL

        for (i in lista.indices) {
            val s = lista[i]
            val fila = LinearLayout(act)
            fila.orientation = LinearLayout.HORIZONTAL
            fila.gravity = Gravity.CENTER_VERTICAL
            fila.setPadding(0, dp(14), 0, dp(14))

            fila.addView(circulo(act, i == marcado), LinearLayout.LayoutParams(dp(36), dp(36)))
            val n = TextView(act)
            n.text = nombres[i]
            n.setTextColor(Color.WHITE)
            n.textSize = 20f
            n.setTypeface(null, Typeface.BOLD)
            n.maxLines = 1
            n.setPadding(dp(16), 0, dp(8), 0)
            fila.addView(n, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            pildoraIdioma(act, s)?.let {
                fila.addView(it, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            fila.setOnClickListener {
                // Se marca el círculo y enseguida se cierra
                fila.removeViewAt(0)
                fila.addView(circulo(act, true), 0, LinearLayout.LayoutParams(dp(36), dp(36)))
                fila.postDelayed({
                    d.dismiss()
                    alElegir(i)
                }, 140)
            }
            col.addView(fila, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val scroll = ScrollMax(act, (pantalla.heightPixels * 0.62f).toInt())
        scroll.addView(col)
        val lpScroll = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpScroll.topMargin = dp(12)
        hoja.addView(scroll, lpScroll)

        d.setContentView(hoja)
        d.setOnCancelListener { alCancelar() }
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setGravity(Gravity.BOTTOM)
            w.setLayout(minOf(pantalla.widthPixels, dp(560)), ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        d.show()
    }

    // ---------- Comprobar cuáles servidores funcionan ----------

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    // Un video más corto que esto se considera de relleno (los de "10 segundos") y se descarta
    private const val MIN_SEGUNDOS = 60.0
    private const val MIN_BYTES = 4_000_000L

    private enum class Estado { BUENO, MALO, CORTO, DUDOSO }

    private val marcasError = listOf(
        "file not found", "video not found", "file was deleted", "file is no longer available",
        "has been removed", "has been deleted", "video no existe", "archivo no existe", "no encontrado",
        "no longer exists", "does not exist", "video unavailable", "video is unavailable",
        "video not available", "no disponible", "ya no está disponible", "was removed", "file removed"
    )

    private fun abrir(url: String, headers: Map<String, String>, rango: String? = null): HttpURLConnection {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 5000
        con.readTimeout = 6000
        con.instanceFollowRedirects = true
        con.setRequestProperty("User-Agent", UA)
        for ((k, v) in headers) con.setRequestProperty(k, v)
        if (rango != null) con.setRequestProperty("Range", rango)
        return con
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

    // MP4 directo: responde, es video (no una página) y no es un clip diminuto
    private fun comprobarMp4(url: String, headers: Map<String, String>): Estado {
        val con = abrir(url, headers, "bytes=0-1023")
        try {
            val codigo = con.responseCode
            if (codigo !in 200..299) return Estado.MALO
            val tipo = (con.contentType ?: "").lowercase()
            if (tipo.startsWith("text/") || tipo.contains("html") || tipo.contains("json")) return Estado.MALO
            val total = con.getHeaderField("Content-Range")?.substringAfterLast("/")?.trim()?.toLongOrNull()
                ?: if (codigo == 200) con.getHeaderField("Content-Length")?.trim()?.toLongOrNull() else null
            if (total != null && total in 1 until MIN_BYTES) return Estado.CORTO
            return Estado.BUENO
        } finally {
            con.disconnect()
        }
    }

    // HLS: lista válida, dura más de un minuto (si es VOD) y el primer trozo se descarga
    private fun comprobarHls(url: String, headers: Map<String, String>, nivel: Int = 0): Estado {
        val con = abrir(url, headers)
        val texto = try {
            if (con.responseCode !in 200..299) return Estado.MALO
            String(con.inputStream.use { it.readNBytesCompat(300_000) })
        } finally {
            con.disconnect()
        }
        if (!texto.contains("#EXTM3U")) return Estado.MALO
        val lineas = texto.lines().map { it.trim() }

        if (texto.contains("#EXT-X-STREAM-INF")) {
            if (nivel >= 2) return Estado.BUENO
            val i = lineas.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
            val uri = lineas.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return Estado.BUENO
            return comprobarHls(URL(URL(url), uri).toString(), headers, nivel + 1)
        }

        var total = 0.0
        var primero: String? = null
        for (l in lineas) {
            if (l.startsWith("#EXTINF:")) {
                total += l.removePrefix("#EXTINF:").substringBefore(",").trim().toDoubleOrNull() ?: 0.0
            } else if (l.isNotEmpty() && !l.startsWith("#") && primero == null) {
                primero = l
            }
        }
        if (primero == null) return Estado.MALO
        if (texto.contains("#EXT-X-ENDLIST") && total < MIN_SEGUNDOS) return Estado.CORTO

        val seg = abrir(URL(URL(url), primero).toString(), headers, "bytes=0-1")
        try {
            val c = seg.responseCode
            if (c == 404 || c == 410 || c >= 500) return Estado.MALO
            if (c == 403) return Estado.DUDOSO
        } finally {
            seg.disconnect()
        }
        return Estado.BUENO
    }

    // Duración que declara la página del reproductor (solo si la dice de forma clara)
    private fun duracionDeclarada(cuerpo: String): Double? {
        Regex("""video:duration["']?\s+content=["'](\d+)["']""").find(cuerpo)?.let {
            return it.groupValues[1].toDoubleOrNull()
        }
        Regex("""content=["'](\d+)["']\s+property=["']video:duration["']""").find(cuerpo)?.let {
            return it.groupValues[1].toDoubleOrNull()
        }
        Regex(""""duration"\s*:\s*"?(\d{1,2}):(\d{2}):(\d{2})"?""").find(cuerpo)?.let {
            val g = it.groupValues
            return g[1].toDouble() * 3600 + g[2].toDouble() * 60 + g[3].toDouble()
        }
        if (cuerpo.contains("videoobject")) {
            Regex(""""duration"\s*:\s*"pt(?:(\d+)h)?(?:(\d+)m)?(\d+(?:\.\d+)?)s?"""").find(cuerpo)?.let {
                val g = it.groupValues
                return (g[1].toDoubleOrNull() ?: 0.0) * 3600 + (g[2].toDoubleOrNull() ?: 0.0) * 60 +
                    (g[3].toDoubleOrNull() ?: 0.0)
            }
        }
        return null
    }

    // Servidor web (embed): no responde 404/caído, no dice que el archivo se borró y, si la página deja ver
    // el video real o su duración, que no sea uno de relleno. Un 403 se conserva (puede ser una protección
    // que sí deja pasar al WebView).
    private fun comprobarEmbed(s: ServerLink): Estado {
        val con = abrir(s.url, s.headers)
        val crudo = try {
            val codigo = con.responseCode
            if (codigo == 404 || codigo == 410 || codigo >= 500) return Estado.MALO
            if (codigo !in 200..299) return Estado.BUENO
            String(con.inputStream.use { it.readNBytesCompat(150_000) })
        } finally {
            con.disconnect()
        }
        val cuerpo = crudo.lowercase()
        if (cuerpo.length < 3000 && marcasError.any { cuerpo.contains(it) }) return Estado.MALO
        val dur = duracionDeclarada(cuerpo)
        if (dur != null && dur > 0 && dur < MIN_SEGUNDOS) return Estado.CORTO

        // Video directo que aparece dentro de la página: solo se descarta si es claramente de relleno
        val limpio = crudo.replace("\\/", "/")
        val m = Regex("""https?://[^"'\s<>\\]+?\.(?:mp4|m3u8)[^"'\s<>\\]*""", RegexOption.IGNORE_CASE)
            .findAll(limpio)
            .map { it.value }
            .firstOrNull { !Regex("(thumb|poster|preview|sprite|sample|trailer|promo)", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        if (m != null) {
            val u = URL(s.url)
            val ref = mapOf("Referer" to (u.protocol + "://" + u.host + "/"))
            val interno = try {
                if (m.contains(".m3u8", true)) comprobarHls(m, ref) else comprobarMp4(m, ref)
            } catch (e: Exception) {
                Estado.DUDOSO
            }
            if (interno == Estado.CORTO) return Estado.CORTO
        }
        return Estado.BUENO
    }

    private fun evaluar(s: ServerLink): Estado = try {
        var tipo = s.tipo
        if (tipo == "auto" || tipo.isEmpty()) {
            tipo = when {
                s.url.contains(".m3u8", true) -> "hls"
                s.url.contains(".mpd", true) -> "dash"
                else -> "mp4"
            }
        }
        when (tipo) {
            "embed" -> comprobarEmbed(s)
            "hls" -> comprobarHls(s.url, s.headers)
            "dash" -> {
                val con = abrir(s.url, s.headers)
                try { if (con.responseCode in 200..299) Estado.BUENO else Estado.MALO } finally { con.disconnect() }
            }
            else -> comprobarMp4(s.url, s.headers)
        }
    } catch (e: Exception) {
        // Sin respuesta a tiempo o sin conexión: no se sabe, se deja como "dudoso"
        Estado.DUDOSO
    }

    // Mega no abre en la app (necesita su propio visor): se descarta siempre
    fun esMega(url: String, nombre: String = ""): Boolean =
        Regex("""(^|[/.])mega\.(nz|io|co\.nz)([/:?#]|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url) ||
            nombre.trim().lowercase().let { it == "mega" || it.startsWith("mega ") || it.startsWith("mega·") }

    fun esMp4upload(s: ServerLink): Boolean =
        s.nombre.lowercase().contains("mp4upload") || s.url.lowercase().contains("mp4upload")

    // Orden de los servidores: primero Mp4upload, luego los servidores web (embed), después el resto
    // (siempre en el orden en que venían)
    private fun prioridad(s: ServerLink): Int = when {
        esMp4upload(s) -> 0
        s.tipo == "embed" -> 1
        else -> 2
    }

    fun ordenar(lista: List<ServerLink>): List<ServerLink> = lista.sortedBy { prioridad(it) } // estable

    // Se queda solo con los servidores que sirven: sin los que se abren en el navegador, sin los caídos,
    // sin los que no son video y sin los de relleno (de unos 10 segundos). Si no se pudo comprobar
    // ninguno (sin conexión, por ejemplo), se devuelven los que quedaron "dudosos".
    fun filtrarFuncionales(lista: List<ServerLink>): List<ServerLink> {
        val candidatos = ordenar(lista.filter { it.tipo != "external" && !esMega(it.url, it.nombre) })
        if (candidatos.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(minOf(candidatos.size, 8))
        try {
            val tareas = candidatos.map { s -> Callable { evaluar(s) } }
            val resultados = pool.invokeAll(tareas, 15, TimeUnit.SECONDS)
            val estados = resultados.map { f ->
                if (f.isCancelled) Estado.DUDOSO else try { f.get() } catch (e: Exception) { Estado.DUDOSO }
            }
            // Mp4upload tarda en responder y a veces se agota el tiempo: si no está claramente caído, se conserva
            val buenos = candidatos.filterIndexed { i, s ->
                estados[i] == Estado.BUENO || (estados[i] == Estado.DUDOSO && esMp4upload(s))
            }
            if (buenos.isNotEmpty()) return buenos
            return candidatos.filterIndexed { i, _ -> estados[i] == Estado.DUDOSO }
        } finally {
            pool.shutdownNow()
        }
    }
}
