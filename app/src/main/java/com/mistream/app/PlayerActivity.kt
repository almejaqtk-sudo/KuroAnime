package com.mistream.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var vistaExo: PlayerView
    private lateinit var raiz: FrameLayout
    private lateinit var prefs: SharedPreferences

    private var episodios: List<Episodio> = emptyList()
    private var itemTitulo = ""
    private var indiceEp = 0
    private var servidores: List<ServerLink> = emptyList()
    private var indiceServ = 0
    private var posInicial = 0L

    // Servidores del episodio actual (para el selector)
    private var listaServidores: List<ServerLink> = emptyList()
    private var reproduciendo = false

    // Barra de controles propia
    private lateinit var barra: LinearLayout
    private lateinit var bMenu: Button
    private lateinit var bAnterior: Button
    private lateinit var bSiguiente: Button
    private lateinit var bServidores: Button
    private lateinit var bTamano: Button
    private var controlesVisibles = false
    private var menuWebAbierto = false

    // Tamaño del video (ExoPlayer)
    private val modosTamano = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    )
    private val nombresTamano = listOf(
        "Ajustar (se ve completo)",
        "Rellenar / Zoom (recorta bordes)",
        "Alargar (estira el video)",
        "Ajustar al ancho",
        "Ajustar al alto"
    )
    private var idxTamano = 0

    // Tamaño en los servidores web
    private val nombresTamanoWeb = listOf(
        "Ajustar (se ve completo)",
        "Rellenar / Zoom (recorta bordes)",
        "Alargar (estira el video)",
        "Zoom 125%",
        "Zoom 150%"
    )
    private var idxTamanoWeb = 0
    private val ocultadorMenu = Runnable {
        menuWebAbierto = false
        actualizarBarra()
    }

    // Servidores web (embed) dentro de la misma pantalla
    private var web: WebView? = null
    private var modoWeb = false
    private var urlsWeb: List<ServerLink> = emptyList()
    private var indiceWeb = 0
    private var fallosWeb = 0
    private var vistaCompleta: View? = null
    private var callbackCompleta: WebChromeClient.CustomViewCallback? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        prefs = getSharedPreferences("mistream", MODE_PRIVATE)

        itemTitulo = intent.getStringExtra("item") ?: ""
        indiceEp = intent.getIntExtra("indice", 0)
        episodios = ModuloParser.episodiosDeJson(
            intent.getStringExtra("episodios") ?: "[]"
        )
        if (episodios.isEmpty() || indiceEp !in episodios.indices) {
            Toast.makeText(this, "Sin episodios", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        idxTamano = prefs.getInt("resize_idx", 0).coerceIn(0, modosTamano.size - 1)
        idxTamanoWeb = prefs.getInt("resize_web", 0).coerceIn(0, nombresTamanoWeb.size - 1)

        raiz = FrameLayout(this)
        raiz.setBackgroundColor(Color.BLACK)
        vistaExo = PlayerView(this)
        vistaExo.resizeMode = modosTamano[idxTamano]
        raiz.addView(vistaExo)

        // Botón ☰ (solo en modo web, para mostrar/ocultar la barra)
        bMenu = boton("☰") {
            menuWebAbierto = !menuWebAbierto
            actualizarBarra()
            barra.removeCallbacks(ocultadorMenu)
            if (menuWebAbierto) barra.postDelayed(ocultadorMenu, 6000)
        }
        val lpMenu = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lpMenu.gravity = Gravity.TOP or Gravity.START
        lpMenu.setMargins(24, 24, 0, 0)
        raiz.addView(bMenu, lpMenu)

        // Barra: anterior · episodios · siguiente · servidores · tamaño
        barra = LinearLayout(this)
        barra.orientation = LinearLayout.HORIZONTAL
        bAnterior = boton("◀ Anterior") {
            guardarPosicion()
            cargarEpisodio(indiceEp - 1)
        }
        val bEpisodios = boton("Episodios") { mostrarEpisodios() }
        bSiguiente = boton("Siguiente ▶") {
            guardarPosicion()
            cargarEpisodio(indiceEp + 1)
        }
        bServidores = boton("Servidores") {
            if (listaServidores.isNotEmpty()) mostrarSelector()
        }
        bTamano = boton("Tamaño") { mostrarTamanos() }
        for (b in listOf(bAnterior, bEpisodios, bSiguiente, bServidores, bTamano)) {
            val lpb = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lpb.setMargins(8, 0, 8, 0)
            barra.addView(b, lpb)
        }
        val lpBarra = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lpBarra.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lpBarra.setMargins(0, 24, 0, 0)
        raiz.addView(barra, lpBarra)
        setContentView(raiz)

        vistaExo.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibilidad ->
                controlesVisibles = visibilidad == View.VISIBLE
                actualizarBarra()
            }
        )
        actualizarBarra()

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        vistaExo.player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val nombre = servidores.getOrNull(indiceServ)?.nombre ?: ""
                Toast.makeText(
                    this@PlayerActivity,
                    "Falló $nombre: ${motivo(error)}",
                    Toast.LENGTH_LONG
                ).show()
                indiceServ++
                if (indiceServ < servidores.size) {
                    reproducir(servidores[indiceServ])
                } else {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Ningún servidor funcionó. Prueba con el botón Servidores.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onPlaybackStateChanged(estado: Int) {
                if (estado == Player.STATE_ENDED) {
                    prefs.edit().remove(clave()).apply()
                    if (haySiguiente()) cargarEpisodio(indiceEp + 1)
                }
            }
        })

        cargarEpisodio(indiceEp)
    }

    private fun boton(texto: String, accion: () -> Unit): Button {
        val b = Button(this)
        b.text = texto
        b.isAllCaps = false
        b.textSize = 12f
        b.alpha = 0.9f
        b.setOnClickListener { accion() }
        return b
    }

    private fun haySiguiente() = indiceEp < episodios.size - 1

    private fun clave() = "pos_" + itemTitulo + "_" + episodios[indiceEp].titulo

    private fun actualizarBarra() {
        if (!::barra.isInitialized) return
        val mostrar = if (modoWeb) menuWebAbierto else controlesVisibles
        barra.visibility = if (mostrar) View.VISIBLE else View.GONE
        bMenu.visibility = if (modoWeb) View.VISIBLE else View.GONE
        bAnterior.visibility = if (indiceEp > 0) View.VISIBLE else View.GONE
        bSiguiente.visibility = if (haySiguiente()) View.VISIBLE else View.GONE
        bServidores.visibility = if (listaServidores.size > 1) View.VISIBLE else View.GONE
        bTamano.visibility = View.VISIBLE
    }

    // Escala del iframe para simular ajustar / rellenar / alargar / zoom en los servidores web
    private fun transformWeb(idx: Int): String {
        val w = raiz.width.toFloat()
        val h = raiz.height.toFloat()
        val f = if (h > 0f) Math.max(1f, (w / h) / (16f / 9f)) else 1.25f
        return when (idx) {
            1 -> "scale(" + f + ")"
            2 -> "scale(" + f + ",1)"
            3 -> "scale(1.25)"
            4 -> "scale(1.5)"
            else -> "none"
        }
    }

    private fun mostrarTamanos() {
        val nombres = if (modoWeb) nombresTamanoWeb else nombresTamano
        val actual = if (modoWeb) idxTamanoWeb else idxTamano
        AlertDialog.Builder(this)
            .setTitle("Tamaño de pantalla")
            .setSingleChoiceItems(nombres.toTypedArray(), actual) { d, which ->
                d.dismiss()
                aplicarTamano(which)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun aplicarTamano(idx: Int) {
        if (modoWeb) {
            idxTamanoWeb = idx
            prefs.edit().putInt("resize_web", idx).apply()
            web?.evaluateJavascript(
                "var f=document.getElementById('f');if(f){f.style.transform='" +
                    transformWeb(idx) + "';}",
                null
            )
            Toast.makeText(this, nombresTamanoWeb[idx], Toast.LENGTH_SHORT).show()
        } else {
            idxTamano = idx
            vistaExo.resizeMode = modosTamano[idx]
            prefs.edit().putInt("resize_idx", idx).apply()
            Toast.makeText(this, nombresTamano[idx], Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarEpisodios() {
        val titulos = episodios.map { it.titulo }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Episodios")
            .setSingleChoiceItems(titulos, indiceEp) { d, which ->
                d.dismiss()
                if (which != indiceEp) {
                    guardarPosicion()
                    cargarEpisodio(which)
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun detenerTodo() {
        player?.stop()
        player?.clearMediaItems()
        ocultarWeb()
    }

    private fun cargarEpisodio(i: Int) {
        if (i !in episodios.indices) return
        indiceEp = i
        detenerTodo()
        servidores = episodios[i].servidores
        indiceServ = 0
        listaServidores = emptyList()
        reproduciendo = false
        posInicial = prefs.getLong(clave(), 0L)
        Toast.makeText(this, episodios[i].titulo, Toast.LENGTH_SHORT).show()
        if (servidores.isEmpty()) {
            Toast.makeText(this, "Sin servidores", Toast.LENGTH_LONG).show()
            actualizarBarra()
            return
        }
        if (servidores.size == 1 && servidores[0].tipo == "hub") {
            actualizarBarra()
            resolverHub(servidores[0], i)
            return
        }
        // Módulos con varios servidores: primero el que elegiste
        val pref = prefs.getString("srv_pref", null)
        listaServidores = servidores.sortedBy { if (it.nombre == pref) 0 else 1 }
        reproduciendo = true
        actualizarBarra()
        usarServidor(0)
    }

    // Mientras ves un episodio, deja listos los servidores del siguiente
    private fun prefetchSiguiente() {
        val sig = episodios.getOrNull(indiceEp + 1)?.servidores ?: return
        if (sig.size == 1 && sig[0].tipo == "hub") {
            Thread {
                try {
                    MotorHub.resolverVideos(sig[0], prefs)
                } catch (e: Exception) {
                    // se intentará al llegar a ese episodio
                }
            }.start()
        }
    }

    // Episodios de módulos AnimeHub: los servidores reales se piden al reproducir
    private fun resolverHub(marcador: ServerLink, i: Int) {
        Toast.makeText(this, "Buscando servidores...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val lista = MotorHub.resolverVideos(marcador, prefs)
                runOnUiThread {
                    if (isFinishing || indiceEp != i) return@runOnUiThread
                    listaServidores = lista
                    actualizarBarra()
                    if (lista.isEmpty()) {
                        Toast.makeText(this, "Sin servidores", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    // Si ya elegiste un servidor antes y este episodio lo tiene, se usa solo
                    val pref = prefs.getString("srv_pref", null)
                    val idx = lista.indexOfFirst { it.nombre == pref }
                    if (idx >= 0) usarServidor(idx) else mostrarSelector()
                    prefetchSiguiente()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "No se pudieron cargar los videos: " + (e.message ?: "error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun mostrarSelector() {
        val etiquetas = listaServidores.map { s ->
            s.nombre + when (s.tipo) {
                "embed" -> "  · web"
                "external" -> "  · navegador"
                else -> ""
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Elegir servidor")
            .setItems(etiquetas) { _, which ->
                prefs.edit().putString("srv_pref", listaServidores[which].nombre).apply()
                usarServidor(which)
            }
            .setNegativeButton("Cancelar", null)
            .setOnCancelListener { if (!reproduciendo) finish() }
            .show()
    }

    private fun usarServidor(idx: Int) {
        if (idx !in listaServidores.indices) return
        val elegido = listaServidores[idx]
        when (elegido.tipo) {
            "embed" -> {
                val webs = listaServidores.filter { it.tipo == "embed" }
                mostrarWeb(webs.filter { it === elegido } + webs.filter { it !== elegido })
            }
            "external" -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(elegido.url)))
            }
            else -> {
                val propios = listaServidores.filter { it.tipo != "embed" && it.tipo != "external" }
                servidores = propios.filter { it === elegido } + propios.filter { it !== elegido }
                indiceServ = 0
                reproduciendo = true
                reproducir(servidores[0])
            }
        }
    }

    // ---------- Servidores web (embed) dentro de esta pantalla ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun asegurarWeb(): WebView {
        web?.let { return it }
        val w = WebView(this)
        w.setBackgroundColor(Color.BLACK)
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return true
                return request.isForMainFrame && host != "localhost"
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.url.toString() == urlsWeb.getOrNull(indiceWeb)?.url &&
                    errorResponse.statusCode >= 400
                ) falloWeb()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.url.toString() == urlsWeb.getOrNull(indiceWeb)?.url) falloWeb()
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (vistaCompleta != null) {
                    callback.onCustomViewHidden()
                    return
                }
                vistaCompleta = view
                callbackCompleta = callback
                web?.visibility = View.GONE
                raiz.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            override fun onHideCustomView() {
                salirPantallaCompleta()
            }
        }
        // Va encima del reproductor y debajo de la barra de controles
        raiz.addView(
            w,
            1,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        web = w
        return w
    }

    private fun mostrarWeb(lista: List<ServerLink>) {
        if (lista.isEmpty()) return
        player?.pause()
        guardarPosicion()
        urlsWeb = lista
        indiceWeb = 0
        fallosWeb = 0
        reproduciendo = true
        modoWeb = true
        menuWebAbierto = true
        barra.removeCallbacks(ocultadorMenu)
        barra.postDelayed(ocultadorMenu, 5000)
        vistaExo.visibility = View.GONE
        asegurarWeb().visibility = View.VISIBLE
        cargarWeb()
        actualizarBarra()
    }

    private fun cargarWeb() {
        val srv = urlsWeb[indiceWeb]
        Toast.makeText(this, "Servidor: " + srv.nombre, Toast.LENGTH_SHORT).show()
        val seguro = srv.url.replace("&", "&amp;").replace("\"", "&quot;")
        // Igual que AnimeHub: el reproductor va en un iframe dentro de una página propia
        val html = "<!DOCTYPE html><html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}" +
            "iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0;" +
            "transform-origin:center center}</style>" +
            "</head><body><iframe id=\"f\" style=\"transform:" + transformWeb(idxTamanoWeb) + "\" " +
            "src=\"" + seguro + "\" " +
            "sandbox=\"allow-scripts allow-same-origin allow-forms allow-presentation\" " +
            "allow=\"autoplay; fullscreen; encrypted-media; picture-in-picture\" " +
            "allowfullscreen></iframe></body></html>"
        web?.loadDataWithBaseURL("https://localhost/", html, "text/html", "utf-8", null)
    }

    private fun falloWeb() {
        fallosWeb++
        if (fallosWeb >= urlsWeb.size) {
            Toast.makeText(
                this,
                "Ningún servidor web abrió. Prueba con ☰ y Servidores.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        indiceWeb = (indiceWeb + 1) % urlsWeb.size
        cargarWeb()
    }

    private fun ocultarWeb() {
        modoWeb = false
        menuWebAbierto = false
        web?.let {
            it.loadUrl("about:blank")
            it.visibility = View.GONE
        }
        salirPantallaCompleta()
        vistaExo.visibility = View.VISIBLE
        actualizarBarra()
    }

    private fun salirPantallaCompleta() {
        val v = vistaCompleta ?: return
        raiz.removeView(v)
        vistaCompleta = null
        val cb = callbackCompleta
        callbackCompleta = null
        cb?.onCustomViewHidden()
        if (modoWeb) web?.visibility = View.VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (vistaCompleta != null) {
            salirPantallaCompleta()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ---------- ExoPlayer ----------

    private fun guardarPosicion() {
        val exo = player ?: return
        val pos = exo.currentPosition
        val dur = exo.duration
        if (pos > 5000 && (dur <= 0 || pos < dur - 10000)) {
            prefs.edit().putLong(clave(), pos).apply()
        } else {
            prefs.edit().remove(clave()).apply()
        }
    }

    private fun motivo(e: PlaybackException): String {
        val c = e.cause
        return when {
            c is HttpDataSource.InvalidResponseCodeException -> when (c.responseCode) {
                403 -> "403 acceso denegado (¿faltan headers?)"
                404 -> "404 no existe"
                else -> "HTTP ${c.responseCode}"
            }
            c is HttpDataSource.HttpDataSourceException -> "sin conexión o servidor caído"
            else -> e.errorCodeName
        }
    }

    private fun reproducir(servidor: ServerLink) {
        val exo = player ?: return
        if (modoWeb) ocultarWeb()

        val fuenteDatos = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(servidor.headers)

        val mime = when (servidor.tipo) {
            "hls" -> MimeTypes.APPLICATION_M3U8
            "dash" -> MimeTypes.APPLICATION_MPD
            "mp4" -> MimeTypes.VIDEO_MP4
            else -> null
        }

        val builder = MediaItem.Builder().setUri(servidor.url)
        if (mime != null) builder.setMimeType(mime)

        if (servidor.subtitulos.isNotEmpty()) {
            builder.setSubtitleConfigurations(
                servidor.subtitulos.map {
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                        .setMimeType(
                            if (it.url.endsWith(".srt", true)) MimeTypes.APPLICATION_SUBRIP
                            else MimeTypes.TEXT_VTT
                        )
                        .setLanguage(it.idioma)
                        .setLabel(it.nombre)
                        .build()
                }
            )
        }

        val fuente = DefaultMediaSourceFactory(fuenteDatos)
            .createMediaSource(builder.build())
        exo.setMediaSource(fuente, posInicial)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        web?.onPause()
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    override fun onStop() {
        super.onStop()
        guardarPosicion()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        web?.let {
            raiz.removeView(it)
            it.destroy()
        }
        web = null
    }
}
