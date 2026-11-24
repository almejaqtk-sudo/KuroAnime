package com.mistream.app

import android.annotation.SuppressLint
import android.app.Activity
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
import android.widget.Toast

// Reproduce dentro de la app los servidores "embed" (páginas con su propio reproductor web)
class EmbedActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var raiz: FrameLayout
    private var urls: List<String> = emptyList()
    private var nombres: List<String> = emptyList()
    private var referer = ""
    private var indice = 0
    private var fallos = 0
    private var hostInicial = ""
    private var vistaCompleta: View? = null
    private var callbackCompleta: WebChromeClient.CustomViewCallback? = null

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
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

        urls = intent.getStringArrayExtra("urls")?.toList() ?: emptyList()
        nombres = intent.getStringArrayExtra("nombres")?.toList() ?: emptyList()
        referer = intent.getStringExtra("referer") ?: ""
        if (urls.isEmpty()) {
            finish()
            return
        }

        raiz = FrameLayout(this)
        raiz.setBackgroundColor(Color.BLACK)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            // Bloquea que la página nos saque a otros sitios (anuncios / redirecciones)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return true
                return request.isForMainFrame && host != hostInicial
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.url.toString() == urls[indice] && errorResponse.statusCode >= 400) falloServidor()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame || request.url.toString() == urls[indice]) falloServidor()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (vistaCompleta != null) {
                    callback.onCustomViewHidden()
                    return
                }
                vistaCompleta = view
                callbackCompleta = callback
                web.visibility = View.GONE
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
        raiz.addView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (urls.size > 1) {
            val boton = Button(this)
            boton.text = "Otro servidor"
            boton.alpha = 0.8f
            boton.setOnClickListener {
                fallos = 0
                indice = (indice + 1) % urls.size
                cargar()
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.TOP or Gravity.END
            lp.setMargins(0, 48, 48, 0)
            raiz.addView(boton, lp)
        }

        setContentView(raiz)
        cargar()
    }

    private fun falloServidor() {
        fallos++
        if (fallos >= urls.size) {
            Toast.makeText(
                this,
                "Ningún servidor web abrió. Prueba otro capítulo.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        indice = (indice + 1) % urls.size
        cargar()
    }

    private fun cargar() {
        val nombre = nombres.getOrElse(indice) { "Servidor " + (indice + 1) }
        Toast.makeText(this, "Servidor: $nombre", Toast.LENGTH_SHORT).show()
        val url = urls[indice]
        hostInicial = "localhost"
        val seguro = url.replace("&", "&amp;").replace("\"", "&quot;")
        // Igual que AnimeHub: el reproductor va en un iframe dentro de una página propia
        val html = "<!DOCTYPE html><html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#000}" +
            "iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>" +
            "</head><body><iframe src=\"" + seguro + "\" " +
            "sandbox=\"allow-scripts allow-same-origin allow-forms allow-presentation\" " +
            "allow=\"autoplay; fullscreen; encrypted-media; picture-in-picture\" " +
            "allowfullscreen></iframe></body></html>"
        web.loadDataWithBaseURL("https://localhost/", html, "text/html", "utf-8", null)
    }

    private fun salirPantallaCompleta() {
        val v = vistaCompleta ?: return
        raiz.removeView(v)
        vistaCompleta = null
        callbackCompleta?.onCustomViewHidden()
        callbackCompleta = null
        web.visibility = View.VISIBLE
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

    override fun onPause() {
        super.onPause()
        if (::web.isInitialized) web.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::web.isInitialized) {
            raiz.removeView(web)
            web.destroy()
        }
    }
}
