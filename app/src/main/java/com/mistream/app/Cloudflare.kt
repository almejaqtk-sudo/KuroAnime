package com.mistream.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Paso de Cloudflare para los módulos AnimeHub.
// Cuando un sitio responde con el desafío de Cloudflare, se abre la página en un WebView con el MISMO
// User-Agent que usa MotorHub. Al conseguir la cookie cf_clearance, las peticiones de Kotlin la envían
// y el sitio responde normal. La cookie dura horas/días, así que esto pasa muy de vez en cuando.
object Cloudflare {

    private val candados = ConcurrentHashMap<String, Any>()
    private val ultimoFallo = ConcurrentHashMap<String, Long>()

    @Volatile internal var urlPendiente: String = ""
    @Volatile internal var clearanceInicial: String? = null
    @Volatile internal var latch: CountDownLatch? = null
    @Volatile internal var resuelto = false

    fun esDesafio(codigo: Int, cuerpo: String, headers: Map<String?, List<String>>): Boolean {
        if (codigo != 403 && codigo != 503 && codigo != 429) return false
        val mitigado = headers.keys.any { it != null && it.equals("cf-mitigated", true) }
        if (mitigado) return true
        val server = headers.entries.firstOrNull { it.key?.equals("server", true) == true }
            ?.value?.joinToString(" ") ?: ""
        val c = cuerpo.take(30000)
        val marcas = c.contains("challenge-platform") || c.contains("cf_chl_opt") || c.contains("cf-chl") ||
            c.contains("Just a moment") || c.contains("Un momento")
        return marcas && (server.contains("cloudflare", true) || c.contains("cloudflare", true))
    }

    fun cookies(url: String): String? = try {
        CookieManager.getInstance().getCookie(url)
    } catch (e: Exception) {
        null
    }

    fun guardarCookies(url: String, setCookie: List<String>?) {
        if (setCookie.isNullOrEmpty()) return
        try {
            val cm = CookieManager.getInstance()
            for (c in setCookie) cm.setCookie(url, c)
            cm.flush()
        } catch (e: Exception) {
            // sin cookies persistentes
        }
    }

    internal fun clearance(cookies: String?): String? =
        cookies?.split(";")?.map { it.trim() }?.firstOrNull { it.startsWith("cf_clearance=") }

    // Se llama desde un hilo secundario. Devuelve true si ya hay una cf_clearance nueva para reintentar.
    fun resolver(url: String, cookieUsada: String?): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Cloudflare.resolver en el hilo principal" }
        val host = try { URL(url).host } catch (e: Exception) { return false }
        synchronized(candados.getOrPut(host) { Any() }) {
            // Otro hilo ya consiguió una cookie nueva mientras esperábamos
            val actual = clearance(cookies(url))
            if (actual != null && actual != clearance(cookieUsada)) return true
            // Si acaba de fallar, no se vuelve a abrir la ventana enseguida
            val f = ultimoFallo[host]
            if (f != null && System.currentTimeMillis() - f < 30_000) return false

            val espera = CountDownLatch(1)
            latch = espera
            resuelto = false
            urlPendiente = url
            clearanceInicial = actual
            try {
                val i = Intent(KuroApp.contexto, CloudflareActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                KuroApp.contexto.startActivity(i)
            } catch (e: Exception) {
                ultimoFallo[host] = System.currentTimeMillis()
                return false
            }
            espera.await(70, TimeUnit.SECONDS)
            latch = null
            val ok = resuelto
            if (!ok) ultimoFallo[host] = System.currentTimeMillis() else ultimoFallo.remove(host)
            return ok
        }
    }
}

// Ventana que muestra la página mientras Cloudflare comprueba el navegador.
// Casi siempre se cierra sola en unos segundos; si aparece la casilla "No soy un robot", basta tocarla.
class CloudflareActivity : Activity() {

    private var web: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var terminado = false

    private val revisar = object : Runnable {
        override fun run() {
            if (terminado) return
            val c = Cloudflare.clearance(Cloudflare.cookies(Cloudflare.urlPendiente))
            if (c != null && c != Cloudflare.clearanceInicial) {
                Cloudflare.resuelto = true
                // un instante para que el sitio termine de guardar sus cookies
                handler.postDelayed({ terminar() }, 800)
                return
            }
            handler.postDelayed(this, 500)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = Cloudflare.urlPendiente
        if (url.isEmpty()) { finish(); return }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val aviso = TextView(this).apply {
            text = "Verificando el sitio (Cloudflare)…\nSi aparece una casilla, tócala. Se cerrará sola."
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(24, 48, 24, 24)
        }
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = MotorHub.USER_AGENT
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = WebViewClient()
        web = wv
        raiz.addView(aviso, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        raiz.addView(wv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(raiz)

        wv.loadUrl(url)
        handler.postDelayed(revisar, 500)
        // límite: si no se resuelve en 60 s, se cierra
        handler.postDelayed({ terminar() }, 60_000)
    }

    private fun terminar() {
        if (terminado) return
        terminado = true
        try { CookieManager.getInstance().flush() } catch (e: Exception) {}
        finish()
    }

    override fun onBackPressed() {
        terminar()
    }

    override fun onDestroy() {
        terminado = true
        handler.removeCallbacksAndMessages(null)
        web?.stopLoading()
        web?.destroy()
        web = null
        Cloudflare.latch?.countDown()
        super.onDestroy()
    }
}
