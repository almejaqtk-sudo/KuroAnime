package com.mistream.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
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
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private var episodios: List<Episodio> = emptyList()
    private var itemTitulo = ""
    private var indiceEp = 0
    private var servidores: List<ServerLink> = emptyList()
    private var indiceServ = 0
    private var posInicial = 0L
    private lateinit var botonSiguiente: Button
    private lateinit var prefs: SharedPreferences

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

        val raiz = FrameLayout(this)
        val vista = PlayerView(this)
        raiz.addView(vista)

        botonSiguiente = Button(this)
        botonSiguiente.text = "Siguiente ▶"
        botonSiguiente.visibility = View.GONE
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.setMargins(0, 48, 48, 0)
        raiz.addView(botonSiguiente, lp)
        setContentView(raiz)

        vista.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibilidad ->
                botonSiguiente.visibility =
                    if (visibilidad == View.VISIBLE && haySiguiente()) View.VISIBLE
                    else View.GONE
            }
        )

        botonSiguiente.setOnClickListener {
            guardarPosicion()
            cargarEpisodio(indiceEp + 1)
        }

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        vista.player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Falló ${servidores[indiceServ].nombre}: ${motivo(error)}",
                    Toast.LENGTH_LONG
                ).show()
                indiceServ++
                if (indiceServ < servidores.size) {
                    reproducir(servidores[indiceServ])
                } else {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Ningún servidor funcionó",
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

    private fun haySiguiente() = indiceEp < episodios.size - 1

    private fun clave() = "pos_" + itemTitulo + "_" + episodios[indiceEp].titulo

    private fun cargarEpisodio(i: Int) {
        indiceEp = i
        servidores = episodios[i].servidores
        indiceServ = 0
        posInicial = prefs.getLong(clave(), 0L)
        botonSiguiente.visibility = View.GONE
        Toast.makeText(this, episodios[i].titulo, Toast.LENGTH_SHORT).show()
        if (servidores.isEmpty()) {
            Toast.makeText(this, "Sin servidores", Toast.LENGTH_LONG).show()
            return
        }
        if (servidores.size == 1 && servidores[0].tipo == "hub") {
            resolverHub(servidores[0], i)
            return
        }
        reproducir(servidores[0])
    }

    // Episodios de módulos AnimeHub: los servidores reales se piden al reproducir
    private fun resolverHub(marcador: ServerLink, i: Int) {
        Toast.makeText(this, "Buscando servidores...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val lista = MotorHub.resolverVideos(marcador, prefs)
                runOnUiThread {
                    if (isFinishing || indiceEp != i) return@runOnUiThread
                    val propios = lista.filter { it.tipo != "embed" && it.tipo != "external" }
                    if (propios.isNotEmpty()) {
                        servidores = propios
                        indiceServ = 0
                        reproducir(propios[0])
                    } else if (lista.isNotEmpty()) {
                        Toast.makeText(
                            this,
                            "Este episodio solo tiene reproductor web; se abre en el navegador",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lista[0].url)))
                    } else {
                        Toast.makeText(this, "Sin servidores", Toast.LENGTH_LONG).show()
                    }
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

    override fun onStop() {
        super.onStop()
        guardarPosicion()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
