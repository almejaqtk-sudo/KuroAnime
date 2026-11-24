package com.mistream.app

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private var indice = 0
    private var servidores: List<ServerLink> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val json = intent.getStringExtra("servidores") ?: "[]"
        servidores = ModuloParser.servidoresDeJson(json)
        if (servidores.isEmpty()) {
            Toast.makeText(this, "Sin servidores", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val vista = PlayerView(this)
        setContentView(vista)

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        vista.player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Falló: ${servidores[indice].nombre}",
                    Toast.LENGTH_SHORT
                ).show()
                indice++
                if (indice < servidores.size) {
                    reproducir(servidores[indice])
                } else {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Ningún servidor funcionó",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        reproducir(servidores[indice])
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

        val fuente = DefaultMediaSourceFactory(fuenteDatos)
            .createMediaSource(builder.build())
        exo.setMediaSource(fuente)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
