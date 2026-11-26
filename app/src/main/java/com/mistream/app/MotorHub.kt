package com.mistream.app

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// Ejecuta módulos JS "estilo AnimeHub" (async/await, api.json/text/html, home/search/details/videos)
// dentro de un WebView oculto. Las peticiones HTTP las hace Kotlin, así que no hay CORS.
// IMPORTANTE: todas las funciones públicas se llaman desde un hilo secundario, nunca desde el principal.
object MotorHub {

    internal const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    class Espera {
        val latch = CountDownLatch(1)
        @Volatile var ok = false
        @Volatile var texto = ""
    }

    internal val main = Handler(Looper.getMainLooper())
    internal val red = Executors.newCachedThreadPool()
    internal val cuerpos = ConcurrentHashMap<Int, String>()
    internal val esperas = ConcurrentHashMap<Int, Espera>()
    internal val cacheHttp = ConcurrentHashMap<String, EntradaHttp>()

    private val contador = AtomicInteger(0)
    private val cargados: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val codigos = ConcurrentHashMap<String, String>()
    private val paginaLista = AtomicBoolean(false)
    private val candado = Any()
    private var webView: WebView? = null

    // Arranca el WebView con anticipación (se llama desde un hilo secundario)
    fun precalentar() {
        try {
            preparar()
        } catch (e: Exception) {
            // se intentará de nuevo cuando haga falta
        }
    }

    // Los módulos de KuroAnime definen "var modulo = {...}"; los de AnimeHub terminan con "return {...}"
    fun esFormatoHub(codigo: String): Boolean =
        !Regex("""\b(var|let|const)\s+modulo\s*=|\bmodulo\s*=\s*\{""").containsMatchIn(codigo)

    fun claveDe(codigo: String): String {
        val h = MessageDigest.getInstance("SHA-1").digest(codigo.toByteArray())
        return h.joinToString("") { "%02x".format(it) }.take(12)
    }

    internal fun avisar(script: String) {
        main.post { webView?.evaluateJavascript(script, null) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun preparar() {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "MotorHub no se puede llamar desde el hilo principal"
        }
        if (paginaLista.get()) return
        synchronized(candado) {
            if (paginaLista.get()) return
            val html = KuroApp.contexto.assets.open("hub.html").bufferedReader().use { it.readText() }
            val espera = CountDownLatch(1)
            main.post {
                val wv = WebView(KuroApp.contexto)
                wv.settings.javaScriptEnabled = true
                wv.addJavascriptInterface(PuenteHub(), "KuroBridge")
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        espera.countDown()
                    }
                }
                webView = wv
                wv.loadDataWithBaseURL("https://kuro.local/", html, "text/html", "utf-8", null)
            }
            if (!espera.await(20, TimeUnit.SECONDS)) {
                throw RuntimeException("El motor WebView no arrancó")
            }
            paginaLista.set(true)
        }
    }

    private fun llamar(clave: String, codigo: String, metodo: String, args: String): String {
        preparar()
        codigos[clave] = codigo
        val id = contador.incrementAndGet()
        val espera = Espera()
        esperas[id] = espera
        val enviar = if (cargados.contains(clave)) "" else codigo
        val script = "__kuroCall(" + id + "," + JSONObject.quote(clave) + "," +
            JSONObject.quote(enviar) + "," + JSONObject.quote(metodo) + "," +
            JSONObject.quote(args) + ")"
        avisar(script)
        val llego = espera.latch.await(90, TimeUnit.SECONDS)
        esperas.remove(id)
        if (!llego) throw RuntimeException("Tiempo agotado en $metodo()")
        if (!espera.ok) throw RuntimeException(espera.texto)
        cargados.add(clave)
        return espera.texto
    }

    private fun info(codigo: String): JSONObject {
        val clave = claveDe(codigo)
        return JSONObject(llamar(clave, codigo, "__info", "[]"))
    }

    // Se usa al instalar: compila el módulo y devuelve su nombre
    fun nombreDe(codigo: String): String = info(codigo).optString("name", "Módulo")

    private fun itemsDeTexto(texto: String, nombre: String, codigo: String): List<Item> {
        val arr = JSONArray(texto)
        val items = mutableListOf<Item>()
        val vistos = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val titulo = o.optString("title", "")
            val url = o.optString("url", "")
            if (titulo.isEmpty() || url.isEmpty() || !vistos.add(url)) continue
            val j = JSONObject()
            j.put("titulo", titulo)
            j.put("portada", o.optString("cover", ""))
            j.put("id", url)
            j.put("ultimo", o.optString("sub", "").ifEmpty { o.optString("note", "") })
            j.put("tipo", o.optString("type", ""))
            j.put("estado", o.optString("status", ""))
            o.optJSONArray("genres")?.let { j.put("generos", it) }
            items.add(ModuloParser.itemDeObjeto(j, nombre, codigo))
        }
        return items
    }

    // Catálogo inicial: home(1), o search("", 1, {}) si no hay home. Si el módulo tiene genres(), también los géneros.
    fun cargar(codigo: String): Modulo {
        val clave = claveDe(codigo)
        val inf = info(codigo)
        val nombre = inf.optString("name", "Módulo")
        // "Últimos episodios": si el módulo tiene latest() se usa; si falla o viene vacío, el catálogo
        var recientes = ""
        if (inf.optBoolean("tieneLatest")) {
            try {
                val t = llamar(clave, codigo, "latest", "[]")
                if (JSONArray(t).length() > 0) recientes = t
            } catch (e: Exception) {
                // se usa el catálogo
            }
        }
        val texto: String = if (recientes.isNotEmpty()) {
            recientes
        } else if (inf.optBoolean("tieneHome")) {
            llamar(clave, codigo, "home", "[1]")
        } else {
            llamar(clave, codigo, "search", "[\"\",1,{}]")
        }
        val items = itemsDeTexto(texto, nombre, codigo)

        val filtros = mutableListOf<Filtro>()
        try {
            if (inf.optBoolean("tieneFilters")) {
                // El módulo dice qué filtros tiene su sitio: [{ key, name, options: [{ name, id }] }]
                val fa = JSONArray(llamar(clave, codigo, "filters", "[]"))
                for (i in 0 until fa.length()) {
                    val f = fa.optJSONObject(i) ?: continue
                    val key = f.optString("key", "").ifEmpty { f.optString("id", "") }
                    val oa = f.optJSONArray("options") ?: continue
                    val ops = mutableListOf<Pair<String, String>>()
                    for (k in 0 until oa.length()) {
                        val o = oa.optJSONObject(k) ?: continue
                        val n = o.optString("name", "").ifEmpty { o.optString("t", "") }
                        val gid = o.optString("id", "").ifEmpty { o.optString("v", n) }
                        if (n.isNotEmpty() && gid.isNotEmpty()) ops.add(Pair(n, gid))
                    }
                    if (key.isNotEmpty() && ops.isNotEmpty()) {
                        val nf = f.optString("name", "").ifEmpty { f.optString("label", key) }
                        filtros.add(Filtro(key, nf, ops))
                    }
                }
            }
            // Módulos que solo tienen genres(): se convierten en el filtro "Género"
            if (filtros.none { it.clave == "genre" } && inf.optBoolean("tieneGenres")) {
                val ga = JSONArray(llamar(clave, codigo, "genres", "[]"))
                val ops = mutableListOf<Pair<String, String>>()
                for (i in 0 until ga.length()) {
                    val g = ga.optJSONObject(i) ?: continue
                    val n = g.optString("name", "")
                    val gid = g.optString("id", n)
                    if (n.isNotEmpty() && gid.isNotEmpty()) ops.add(Pair(n, gid))
                }
                if (ops.isNotEmpty()) filtros.add(0, Filtro("genre", "Género", ops))
            }
        } catch (e: Exception) {
            // sin filtros del módulo: la app usa lo que traen los títulos cargados
        }
        return Modulo(nombre, items, codigo, filtros, iconoDe(inf), inf.optString("version", ""))
    }

    // Imagen del módulo: la que él declare o el favicon de su página
    private fun iconoDe(inf: JSONObject): String {
        val icon = inf.optString("icon", "")
        if (icon.startsWith("http")) return icon
        val base = inf.optString("baseUrl", "")
        val host = try { URL(base).host } catch (e: Exception) { "" }
        return if (host.isEmpty()) "" else "https://www.google.com/s2/favicons?domain=$host&sz=128"
    }

    private fun filtrosJson(filtros: Map<String, String>): JSONObject {
        val o = JSONObject()
        for ((k, v) in filtros) o.put(k, v)
        return o
    }

    // Una página del catálogo con los filtros elegidos (genre, category, status...). Los filtra el sitio.
    // Con filtros se usa search("", página, filtros); si el módulo no devuelve nada así, se prueba home().
    fun catalogo(codigo: String, filtros: Map<String, String>, pagina: Int): List<Item> {
        val clave = claveDe(codigo)
        val inf = info(codigo)
        val nombre = inf.optString("name", "Módulo")
        val tieneHome = inf.optBoolean("tieneHome")
        val tieneSearch = inf.optBoolean("tieneSearch")

        fun porSearch(): List<Item> {
            val args = JSONArray().put("").put(pagina).put(filtrosJson(filtros))
            return itemsDeTexto(llamar(clave, codigo, "search", args.toString()), nombre, codigo)
        }

        fun porHome(): List<Item> {
            val args = JSONArray().put(pagina).put(filtrosJson(filtros))
            return itemsDeTexto(llamar(clave, codigo, "home", args.toString()), nombre, codigo)
        }

        return when {
            filtros.isNotEmpty() && tieneSearch -> {
                val r = try { porSearch() } catch (e: Exception) { if (tieneHome) emptyList<Item>() else throw e }
                if (r.isEmpty() && tieneHome) porHome() else r
            }
            tieneHome -> porHome()
            else -> porSearch()
        }
    }

    // Botón "Cargar más" de la pantalla principal. pagina 2 = el día anterior (módulos con latest(page)),
    // pagina 3 = el día de antes... Si el módulo no tiene latest(), se piden más páginas de home().
    fun recientes(codigo: String, pagina: Int): List<Item> {
        val clave = claveDe(codigo)
        val inf = info(codigo)
        val nombre = inf.optString("name", "Módulo")
        return when {
            inf.optBoolean("tieneLatest") ->
                itemsDeTexto(llamar(clave, codigo, "latest", JSONArray().put(pagina).toString()), nombre, codigo)
            inf.optBoolean("tieneHome") ->
                itemsDeTexto(llamar(clave, codigo, "home", JSONArray().put(pagina).toString()), nombre, codigo)
            else -> emptyList()
        }
    }

    // Búsqueda por nombre en el sitio. Devuelve null si el módulo no sabe buscar.
    fun buscar(codigo: String, consulta: String, filtros: Map<String, String>, pagina: Int): List<Item>? {
        if (!esFormatoHub(codigo)) return null
        val clave = claveDe(codigo)
        val inf = info(codigo)
        if (!inf.optBoolean("tieneSearch")) return null
        val nombre = inf.optString("name", "Módulo")
        val args = JSONArray().put(consulta).put(pagina).put(filtrosJson(filtros))
        return itemsDeTexto(llamar(clave, codigo, "search", args.toString()), nombre, codigo)
    }

    // Descripción y géneros que devolvió details() la última vez (para la pantalla del anime)
    private val detalles = ConcurrentHashMap<String, Pair<String, String>>()

    fun metaDe(codigo: String, id: String): Pair<String, String>? {
        if (!esFormatoHub(codigo)) return null
        return detalles[claveDe(codigo) + "|" + id]
    }

    // Se llama al abrir un título: details(url). Cada episodio queda como un marcador "hub"
    // y sus videos se piden al reproducirlo (resolverVideos).
    fun episodios(codigo: String, detalleUrl: String): List<Episodio> {
        val clave = claveDe(codigo)
        val d = JSONObject(llamar(clave, codigo, "details", JSONArray().put(detalleUrl).toString()))
        val gs = d.optJSONArray("genres")
        val gtxt = if (gs == null) "" else (0 until gs.length()).map { gs.optString(it) }
            .filter { it.isNotEmpty() }.joinToString(", ")
        detalles[clave + "|" + detalleUrl] = Pair(d.optString("description", ""), gtxt)
        val arr = d.optJSONArray("episodes") ?: JSONArray()
        val lista = mutableListOf<Episodio>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val url = e.optString("url", "")
            if (url.isEmpty()) continue
            val num = if (e.isNull("number")) "" else e.get("number").toString().removeSuffix(".0")
            val t = e.optString("title", "").trim()
            // Solo "Episodio N" (sin el nombre largo del anime)
            val titulo = when {
                num == "0" && arr.length() == 1 -> "Película"
                num.isNotEmpty() -> "Episodio $num"
                t.isNotEmpty() -> t.replace(Regex("(?i)\\s*[-–]\\s*ver\\s.*$"), "").trim()
                else -> "Episodio ${i + 1}"
            }
            val marcador = ServerLink(
                nombre = "Cargando servidores",
                url = url,
                tipo = "hub",
                headers = mapOf("mod" to clave, "detalle" to detalleUrl, "ep" to e.toString())
            )
            lista.add(Episodio(titulo, listOf(marcador)))
        }
        return lista
    }

    private fun buscarCodigo(clave: String, prefs: SharedPreferences): String? {
        codigos[clave]?.let { return it }
        return Repositorio.leerInstalados(prefs)
            .map { it.valor }
            .firstOrNull { !it.trimStart().startsWith("http") && claveDe(it) == clave }
    }

    // videos(episodeUrl, ctx): devuelve los servidores reales (primero los reproducibles)
    private class EntradaVideos(val t: Long, val lista: List<ServerLink>)
    private val cacheVideos = ConcurrentHashMap<String, EntradaVideos>()

    // Servidores de un episodio. Se guardan 10 minutos para que abrir el reproductor sea instantáneo.
    fun enCache(marcador: ServerLink): List<ServerLink>? {
        val clave = (marcador.headers["mod"] ?: "") + "|" + marcador.url
        val c = cacheVideos[clave] ?: return null
        return if (System.currentTimeMillis() - c.t < 10 * 60 * 1000) c.lista else null
    }

    fun resolverVideos(marcador: ServerLink, prefs: SharedPreferences): List<ServerLink> {
        val clave = (marcador.headers["mod"] ?: "") + "|" + marcador.url
        val c = cacheVideos[clave]
        if (c != null && System.currentTimeMillis() - c.t < 10 * 60 * 1000) return c.lista
        val lista = resolverVideosRed(marcador, prefs)
        if (lista.isNotEmpty()) cacheVideos[clave] = EntradaVideos(System.currentTimeMillis(), lista)
        return lista
    }

    private fun resolverVideosRed(marcador: ServerLink, prefs: SharedPreferences): List<ServerLink> {
        val clave = marcador.headers["mod"] ?: throw IllegalArgumentException("Marcador inválido")
        val codigo = buscarCodigo(clave, prefs)
            ?: throw IllegalStateException("No encuentro el módulo. Vuelve a instalarlo.")
        val ep = JSONObject(marcador.headers["ep"] ?: "{}")
        val args = JSONArray()
            .put(marcador.url)
            .put(ep)
            .put(marcador.headers["detalle"] ?: "")
        val arr = JSONArray(llamar(clave, codigo, "videos", args.toString()))

        val vistos = mutableSetOf<String>()
        val buenos = mutableListOf<ServerLink>()
        val web = mutableListOf<ServerLink>()
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            val url = v.optString("url", "").trim()
            if (url.isEmpty() || !vistos.add(url.substringBefore("#"))) continue
            if (Regex("""\.(jpe?g|png|gif|webp|svg|ico|css|js)(\?|$)""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(url)) continue
            // Mega no carga en la app: se descarta
            if (Servidores.esMega(url, v.optString("label", ""))) continue

            var tipo = v.optString("type", "").lowercase()
            // Los que se abren en el navegador ya no se usan: solo directos y web (embed)
            if (tipo == "external" || tipo == "browser" || tipo == "navegador") continue
            if (tipo.isEmpty()) {
                tipo = when {
                    url.contains(".m3u8", true) -> "hls"
                    url.contains(".mpd", true) -> "dash"
                    else -> "mp4"
                }
            }
            val headers = mutableMapOf<String, String>()
            val ho = v.optJSONObject("headers")
            if (ho != null) for (k in ho.keys()) headers[k] = ho.optString(k)
            val ref = v.optString("referer", "")
            if (ref.isNotEmpty() && !headers.containsKey("Referer")) headers["Referer"] = ref

            val subs = mutableListOf<Subtitulo>()
            val sa = v.optJSONArray("subtitles") ?: v.optJSONArray("subs")
            if (sa != null) {
                for (k in 0 until sa.length()) {
                    val so = sa.optJSONObject(k) ?: continue
                    val su = so.optString("url", "")
                    if (su.isEmpty()) continue
                    subs.add(
                        Subtitulo(
                            su,
                            so.optString("lang", so.optString("idioma", "es")),
                            so.optString("label", so.optString("nombre", "Subtítulos"))
                        )
                    )
                }
            }

            val etiqueta = v.optString("label", "").ifEmpty { "Servidor ${i + 1}" }
            val idioma = Servidores.normalizarIdioma(v.optString("lang", ""), etiqueta)
            val enlace = ServerLink(etiqueta, url, tipo, headers, subs, idioma)
            if (tipo == "embed") web.add(enlace) else buenos.add(enlace)
        }
        // Solo se muestran los servidores que responden (los caídos se descartan)
        return Servidores.filtrarFuncionales((buenos + web).take(20)).take(14)
    }
}

internal class EntradaHttp(val t: Long, val codigo: Int, val cuerpo: String, val headers: String)

// Puente JS -> Kotlin (debe ser pública para que el WebView pueda usarla)
internal class PuenteHub {

    @JavascriptInterface
    fun http(id: Int, url: String, opts: String) {
        MotorHub.red.execute {
            try {
                val o = JSONObject(opts)
                // Las peticiones GET repetidas se responden desde memoria (5 minutos)
                val claveCache = if (o.optString("method", "GET") == "GET") {
                    url + "|" + (o.optJSONObject("headers")?.toString() ?: "")
                } else null
                val guardada = if (claveCache != null) MotorHub.cacheHttp[claveCache] else null
                if (guardada != null && System.currentTimeMillis() - guardada.t < 5 * 60 * 1000) {
                    MotorHub.cuerpos[id] = guardada.cuerpo
                    MotorHub.avisar(
                        "__kuroHttpDone(" + id + "," + guardada.codigo + "," +
                            JSONObject.quote(guardada.headers) + ")"
                    )
                    return@execute
                }
                val con = URL(url).openConnection() as HttpURLConnection
                con.connectTimeout = 20000
                con.readTimeout = 30000
                con.instanceFollowRedirects = true
                con.setRequestProperty("User-Agent", MotorHub.USER_AGENT)
                val metodo = o.optString("method", "GET")
                con.requestMethod = metodo
                val h = o.optJSONObject("headers")
                if (h != null) for (k in h.keys()) con.setRequestProperty(k, h.optString(k))
                val cuerpo = if (o.isNull("body")) null else o.getString("body")
                if (cuerpo != null && metodo != "GET" && metodo != "HEAD") {
                    con.doOutput = true
                    con.outputStream.use { it.write(cuerpo.toByteArray()) }
                }
                val codigo = con.responseCode
                val flujo = if (codigo in 200..399) con.inputStream else con.errorStream
                val texto = flujo?.bufferedReader()?.use { it.readText() } ?: ""
                val hs = JSONObject()
                for ((k, v) in con.headerFields) {
                    if (k != null && v.isNotEmpty()) hs.put(k, v.last())
                }
                con.disconnect()
                if (claveCache != null && codigo in 200..299 && texto.length < 1500000) {
                    if (MotorHub.cacheHttp.size > 60) MotorHub.cacheHttp.clear()
                    MotorHub.cacheHttp[claveCache] =
                        EntradaHttp(System.currentTimeMillis(), codigo, texto, hs.toString())
                }
                MotorHub.cuerpos[id] = texto
                MotorHub.avisar(
                    "__kuroHttpDone(" + id + "," + codigo + "," + JSONObject.quote(hs.toString()) + ")"
                )
            } catch (e: Exception) {
                MotorHub.avisar(
                    "__kuroHttpDone(" + id + ",0," + JSONObject.quote(e.message ?: e.toString()) + ")"
                )
            }
        }
    }

    @JavascriptInterface
    fun tomar(id: Int): String = MotorHub.cuerpos.remove(id) ?: ""

    @JavascriptInterface
    fun resultado(id: Int, ok: Boolean, texto: String) {
        val e = MotorHub.esperas[id] ?: return
        e.ok = ok
        e.texto = texto
        e.latch.countDown()
    }

    @JavascriptInterface
    fun log(texto: String) {
        Log.d("KuroHub", texto)
    }
}

// Elige el motor según el formato del módulo: Rhino (KuroAnime) o WebView (AnimeHub)
object Motores {
    fun nombreDe(codigo: String): String =
        if (MotorHub.esFormatoHub(codigo)) MotorHub.nombreDe(codigo) else MotorJs.nombreDe(codigo)

    fun cargar(codigo: String): Modulo =
        if (MotorHub.esFormatoHub(codigo)) MotorHub.cargar(codigo) else MotorJs.cargar(codigo)

    fun episodios(codigo: String, id: String): List<Episodio> =
        if (MotorHub.esFormatoHub(codigo)) MotorHub.episodios(codigo, id) else MotorJs.episodios(codigo, id)
}
