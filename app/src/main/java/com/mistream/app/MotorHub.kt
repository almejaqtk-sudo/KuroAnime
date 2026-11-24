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

    private val contador = AtomicInteger(0)
    private val cargados: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val codigos = ConcurrentHashMap<String, String>()
    private val paginaLista = AtomicBoolean(false)
    private val candado = Any()
    private var webView: WebView? = null

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

    // Catálogo inicial: home(1), o search("", 1, {}) si no hay home
    fun cargar(codigo: String): Modulo {
        val clave = claveDe(codigo)
        val inf = info(codigo)
        val nombre = inf.optString("name", "Módulo")
        val texto = if (inf.optBoolean("tieneHome")) {
            llamar(clave, codigo, "home", "[1]")
        } else {
            llamar(clave, codigo, "search", "[\"\",1,{}]")
        }
        val arr = JSONArray(texto)
        val items = mutableListOf<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val titulo = o.optString("title", "")
            val url = o.optString("url", "")
            if (titulo.isEmpty() || url.isEmpty()) continue
            val j = JSONObject()
            j.put("titulo", titulo)
            j.put("portada", o.optString("cover", ""))
            j.put("id", url)
            j.put("ultimo", o.optString("sub", ""))
            items.add(ModuloParser.itemDeObjeto(j, nombre, codigo))
        }
        return Modulo(nombre, items)
    }

    // Se llama al abrir un título: details(url). Cada episodio queda como un marcador "hub"
    // y sus videos se piden al reproducirlo (resolverVideos).
    fun episodios(codigo: String, detalleUrl: String): List<Episodio> {
        val clave = claveDe(codigo)
        val d = JSONObject(llamar(clave, codigo, "details", JSONArray().put(detalleUrl).toString()))
        val arr = d.optJSONArray("episodes") ?: JSONArray()
        val lista = mutableListOf<Episodio>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val url = e.optString("url", "")
            if (url.isEmpty()) continue
            val num = if (e.isNull("number")) "" else e.get("number").toString()
            val t = e.optString("title", "").trim()
            val titulo = when {
                num.isEmpty() -> t.ifEmpty { "Episodio ${i + 1}" }
                t.isEmpty() ||
                    Regex("(?i)^\\s*(episodio|episode|ep\\.?|capítulo)?\\s*" + Regex.escape(num) + "\\s*$")
                        .matches(t) -> "Episodio $num"
                else -> "Episodio $num - $t"
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
    fun resolverVideos(marcador: ServerLink, prefs: SharedPreferences): List<ServerLink> {
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

            var tipo = v.optString("type", "").lowercase()
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

            val idioma = v.optString("lang", "")
            val nombre = v.optString("label", "").ifEmpty { "Servidor ${i + 1}" } +
                (if (idioma.isNotEmpty()) " ($idioma)" else "")
            val enlace = ServerLink(nombre, url, tipo, headers, subs)
            if (tipo == "embed" || tipo == "external") web.add(enlace) else buenos.add(enlace)
        }
        return (buenos + web).take(12)
    }
}

// Puente JS -> Kotlin (debe ser pública para que el WebView pueda usarla)
internal class PuenteHub {

    @JavascriptInterface
    fun http(id: Int, url: String, opts: String) {
        MotorHub.red.execute {
            try {
                val o = JSONObject(opts)
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
