package com.mistream.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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

    // Controles de arriba: volver (círculo) y botones oscuros a la derecha
    private lateinit var bAtras: TextView
    private lateinit var barra: LinearLayout
    private lateinit var bAnterior: TextView
    private lateinit var bSiguiente: TextView
    private lateinit var bEscala: TextView
    private lateinit var bServidores: TextView
    private lateinit var bRetro: TextView
    private lateinit var bAdelante: TextView
    private var controlesVisibles = false
    private var barraWebVisible = false
    private val ocultadorBarra = Runnable {
        barraWebVisible = false
        actualizarBarra()
    }

    // Escala: solo 3 opciones (la misma para video directo y para servidores web)
    private val modosEscala = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,   // Ajustar: se ve completo
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,  // Rellenar: llena la pantalla recortando bordes
        AspectRatioFrameLayout.RESIZE_MODE_FILL   // Alargar: estira el video a toda la pantalla
    )
    private val nombresEscala = listOf("Ajustar", "Rellenar", "Alargar")
    private var idxEscala = 0

    // Servidores web (embed) dentro de la misma pantalla
    private var web: WebView? = null
    private var modoWeb = false
    private var urlsWeb: List<ServerLink> = emptyList()
    private var indiceWeb = 0
    private var fallosWeb = 0
    private var escalaWebSoportada = false
    private var vistaCompleta: View? = null
    private var callbackCompleta: WebChromeClient.CustomViewCallback? = null

    private fun dp(v: Int) = Ui.dp(this, v)

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

        idxEscala = prefs.getInt("escala_idx", 0).coerceIn(0, modosEscala.size - 1)

        raiz = FrameLayout(this)
        raiz.setBackgroundColor(Color.BLACK)
        vistaExo = PlayerView(this)
        vistaExo.resizeMode = modosEscala[idxEscala]
        raiz.addView(vistaExo)

        // Volver: círculo oscuro arriba a la izquierda
        bAtras = TextView(this)
        bAtras.text = "←"
        bAtras.setTextColor(Color.parseColor("#C7D2FE"))
        bAtras.textSize = 22f
        bAtras.gravity = Gravity.CENTER
        val circ = GradientDrawable()
        circ.shape = GradientDrawable.OVAL
        circ.setColor(Color.parseColor("#B3181B2A"))
        bAtras.background = circ
        bAtras.setOnClickListener { finish() }
        val lpAtras = FrameLayout.LayoutParams(dp(46), dp(46))
        lpAtras.gravity = Gravity.TOP or Gravity.START
        lpAtras.setMargins(dp(16), dp(12), 0, 0)
        raiz.addView(bAtras, lpAtras)

        // Botones oscuros arriba a la derecha
        barra = LinearLayout(this)
        barra.orientation = LinearLayout.HORIZONTAL
        bAnterior = pill("‹ Anterior") {
            guardarPosicion()
            cargarEpisodio(indiceEp - 1)
        }
        bSiguiente = pill("Siguiente ›") {
            guardarPosicion()
            cargarEpisodio(indiceEp + 1)
        }
        bEscala = pill("Escala") { cambiarEscala() }
        val bEpisodios = pill("Episodios") { mostrarEpisodios() }
        bServidores = pill("Servidor") {
            if (listaServidores.isNotEmpty()) mostrarSelector()
        }
        for (b in listOf(bAnterior, bSiguiente, bEscala, bEpisodios, bServidores)) {
            val lpb = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lpb.leftMargin = dp(8)
            barra.addView(b, lpb)
        }
        val lpBarra = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lpBarra.gravity = Gravity.TOP or Gravity.END
        lpBarra.setMargins(0, dp(12), dp(16), 0)
        raiz.addView(barra, lpBarra)

        // Retroceder / adelantar 10 segundos (a los lados del centro de la pantalla)
        bRetro = botonSalto("↺ 10") { saltar(-10000) }
        val lpRetro = FrameLayout.LayoutParams(dp(76), dp(76))
        lpRetro.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        lpRetro.setMargins(dp(150), 0, 0, 0)
        raiz.addView(bRetro, lpRetro)
        bAdelante = botonSalto("10 ↻") { saltar(10000) }
        val lpAdelante = FrameLayout.LayoutParams(dp(76), dp(76))
        lpAdelante.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        lpAdelante.setMargins(0, 0, dp(150), 0)
        raiz.addView(bAdelante, lpAdelante)
        setContentView(raiz)

        // Con video directo, los botones aparecen y desaparecen junto con los controles
        vistaExo.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibilidad ->
                controlesVisibles = visibilidad == View.VISIBLE
                actualizarBarra()
            }
        )
        actualizarBarra()

        // Empieza a reproducir con muy poco búfer (arranca antes) y salta de 10 en 10 segundos
        val carga = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 60000, 1000, 2000)
            .build()
        val exo = ExoPlayer.Builder(this)
            .setLoadControl(carga)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
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
                        "Ningún servidor funcionó. Prueba con el botón Servidor.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onPlaybackStateChanged(estado: Int) {
                if (estado == Player.STATE_ENDED) {
                    Progreso.marcarVisto(prefs, itemTitulo, episodios[indiceEp].titulo)
                    if (haySiguiente()) cargarEpisodio(indiceEp + 1)
                }
            }
        })

        cargarEpisodio(indiceEp)
    }

    // Botón oscuro redondeado con texto claro
    private fun pill(texto: String, accion: () -> Unit): TextView {
        val t = TextView(this)
        t.text = texto
        t.setTextColor(Color.parseColor("#C7D2FE"))
        t.textSize = 14f
        t.setTypeface(null, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setPadding(dp(18), dp(11), dp(18), dp(11))
        t.background = Ui.fondo(this, Color.parseColor("#B3181B2A"), 30)
        t.setOnClickListener { accion() }
        return t
    }

    // Círculo oscuro con texto para los botones de ±10 segundos
    private fun botonSalto(texto: String, accion: () -> Unit): TextView {
        val t = TextView(this)
        t.text = texto
        t.setTextColor(Color.parseColor("#C7D2FE"))
        t.textSize = 16f
        t.setTypeface(null, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        val c = GradientDrawable()
        c.shape = GradientDrawable.OVAL
        c.setColor(Color.parseColor("#B3181B2A"))
        t.background = c
        t.visibility = View.GONE
        t.setOnClickListener { accion() }
        return t
    }

    private fun saltar(ms: Long) {
        if (modoWeb) {
            // Servidores web: se manda la flecha izquierda/derecha al reproductor de la página.
            // Depende de cada reproductor; en la mayoría de los casos mueve 10 segundos.
            val tecla = if (ms < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            web?.let {
                it.requestFocus()
                it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, tecla))
                it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, tecla))
            }
            mostrarBarraWeb()
            return
        }
        val exo = player ?: return
        val dur = exo.duration
        var destino = exo.currentPosition + ms
        if (destino < 0) destino = 0
        if (dur > 0 && destino > dur - 1000) destino = dur - 1000
        exo.seekTo(destino)
        vistaExo.showController()
    }

    private fun haySiguiente() = indiceEp < episodios.size - 1

    private fun clave() = Progreso.clavePos(itemTitulo, episodios[indiceEp].titulo)

    private fun actualizarBarra() {
        if (!::barra.isInitialized) return
        val mostrar = if (modoWeb) barraWebVisible else controlesVisibles
        val v = if (mostrar) View.VISIBLE else View.GONE
        barra.visibility = v
        bAtras.visibility = v
        val vSalto = if (mostrar && listaServidores.isNotEmpty()) View.VISIBLE else View.GONE
        bRetro.visibility = vSalto
        bAdelante.visibility = vSalto
        bAnterior.visibility = if (indiceEp > 0) View.VISIBLE else View.GONE
        bSiguiente.visibility = if (haySiguiente()) View.VISIBLE else View.GONE
        bServidores.visibility = if (listaServidores.size > 1) View.VISIBLE else View.GONE
        bEscala.text = "Escala: " + nombresEscala[idxEscala]
    }

    // En servidores web los botones salen al tocar la pantalla y se ocultan solos
    private fun mostrarBarraWeb() {
        barraWebVisible = true
        actualizarBarra()
        barra.removeCallbacks(ocultadorBarra)
        barra.postDelayed(ocultadorBarra, 4000)
    }

    // ---------- Escala: Ajustar / Rellenar / Alargar ----------

    // Servidores web: se le pide al <video> de la página que se ajuste (object-fit). Solo cambia el video:
    // la línea de tiempo y los botones del reproductor de la página no se agrandan ni se salen de cuadro.
    private fun modoCss(idx: Int): String = when (idx) {
        1 -> "cover"   // Rellenar
        2 -> "fill"    // Alargar
        else -> "contain" // Ajustar
    }

    // Script que se mete en la página del reproductor (y en sus iframes) antes de que cargue
    private val scriptEscala = """
(function(){
  if (window.__kuroEscala) return; window.__kuroEscala = true;
  var modo = 'contain', st = null;
  function aplicar(){
    try {
      if (!st || !st.isConnected) {
        st = document.createElement('style');
        (document.head || document.documentElement).appendChild(st);
      }
      st.textContent = 'video{object-fit:' + modo + ' !important;}';
    } catch(e) {}
  }
  function reenviar(){
    for (var i = 0; i < window.frames.length; i++) {
      try { window.frames[i].postMessage({kuroEscala: modo}, '*'); } catch(e) {}
    }
  }
  window.addEventListener('message', function(e){
    var d = e.data;
    if (d && typeof d === 'object' && d.kuroEscala) { modo = d.kuroEscala; aplicar(); reenviar(); }
  });
  aplicar();
  document.addEventListener('DOMContentLoaded', aplicar);
  setInterval(function(){ aplicar(); reenviar(); }, 1500);
})();
"""

    private fun cambiarEscala() {
        idxEscala = (idxEscala + 1) % modosEscala.size
        prefs.edit().putInt("escala_idx", idxEscala).apply()
        aplicarEscala()
        actualizarBarra()
        if (modoWeb) {
            if (!escalaWebSoportada && idxEscala != 0) {
                Toast.makeText(
                    this,
                    "La escala en servidores web necesita un WebView más nuevo (actualiza Android System WebView)",
                    Toast.LENGTH_LONG
                ).show()
            }
            mostrarBarraWeb()
        }
    }

    private fun aplicarEscala() {
        vistaExo.resizeMode = modosEscala[idxEscala]
        web?.evaluateJavascript(
            "window.postMessage({kuroEscala:'" + modoCss(idxEscala) + "'},'*');",
            null
        )
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
        Progreso.guardarUltimo(prefs, itemTitulo, episodios[i].titulo)
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
        listaServidores = servidores.sortedBy { if (Servidores.clave(it) == pref) 0 else 1 }
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
                    var idx = lista.indexOfFirst { Servidores.clave(it) == pref }
                    if (idx < 0 && pref != null) {
                        // Ese servidor no está en este episodio: se busca otro del mismo idioma
                        val idioma = pref.substringAfterLast("|", "")
                        if (idioma.isNotEmpty()) {
                            idx = lista.indexOfFirst {
                                Servidores.normalizarIdioma(it.idioma, it.nombre) == idioma &&
                                    it.tipo != "embed" && it.tipo != "external"
                            }
                            if (idx < 0) {
                                idx = lista.indexOfFirst {
                                    Servidores.normalizarIdioma(it.idioma, it.nombre) == idioma
                                }
                            }
                        }
                    }
                    when {
                        idx >= 0 -> usarServidor(idx)
                        // Sin servidor elegido: se usa el primero que no sea web (carga más rápido)
                        lista[0].tipo != "embed" && lista[0].tipo != "external" -> usarServidor(0)
                        else -> mostrarSelector()
                    }
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
        Servidores.mostrarSelector(
            this, "Elegir servidor", listaServidores, prefs.getString("srv_pref", null),
            { which ->
                prefs.edit().putString("srv_pref", Servidores.clave(listaServidores[which])).apply()
                usarServidor(which)
            },
            { if (!reproduciendo) finish() }
        )
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

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
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

        // Para ajustar solo el video (sin tocar la línea de tiempo de la página)
        escalaWebSoportada = try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(w, scriptEscala, setOf("*"))
                true
            } else false
        } catch (e: Exception) {
            false
        }

        // Un toque en la pantalla muestra los botones (y el toque sigue llegando al reproductor)
        w.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_UP) mostrarBarraWeb()
            false
        }

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
        // Va encima del reproductor y debajo de los botones
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
        Progreso.marcarVisto(prefs, itemTitulo, episodios[indiceEp].titulo)
        vistaExo.visibility = View.GONE
        asegurarWeb().visibility = View.VISIBLE
        cargarWeb()
        mostrarBarraWeb()
    }

    private fun cargarWeb() {
        val srv = urlsWeb[indiceWeb]
        Toast.makeText(this, "Servidor: " + srv.nombre, Toast.LENGTH_SHORT).show()
        val seguro = srv.url.replace("&", "&amp;").replace("\"", "&quot;")
        // Igual que AnimeHub: el reproductor va en un iframe dentro de una página propia
        val html = "<!DOCTYPE html><html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}" +
            "iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>" +
            "</head><body><iframe id=\"f\" " +
            "src=\"" + seguro + "\" " +
            "sandbox=\"allow-scripts allow-same-origin allow-forms allow-presentation\" " +
            "allow=\"autoplay; fullscreen; encrypted-media; picture-in-picture\" " +
            "allowfullscreen></iframe>" +
            "<script>window.postMessage({kuroEscala:'" + modoCss(idxEscala) + "'},'*');</script>" +
            "</body></html>"
        web?.loadDataWithBaseURL("https://localhost/", html, "text/html", "utf-8", null)
    }

    private fun falloWeb() {
        fallosWeb++
        if (fallosWeb >= urlsWeb.size) {
            Toast.makeText(
                this,
                "Ningún servidor web abrió. Toca la pantalla y elige Servidor.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        indiceWeb = (indiceWeb + 1) % urlsWeb.size
        cargarWeb()
    }

    private fun ocultarWeb() {
        modoWeb = false
        barraWebVisible = false
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
        // Sin nada cargado (por ejemplo, justo al cambiar de episodio) no se toca el progreso
        if (modoWeb || exo.mediaItemCount == 0 || indiceEp !in episodios.indices) return
        Progreso.actualizar(
            prefs, itemTitulo, episodios[indiceEp].titulo,
            exo.currentPosition, exo.duration
        )
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
