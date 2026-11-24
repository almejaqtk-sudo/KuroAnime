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

// Así describirá cada módulo un servidor de video
data class ServerLink(
    val nombre: String,
    val url: String,
    val tipo: String = "auto", // auto, mp4, hls, dash
    val headers: Map<String, String> = emptyMap()
)

@OptIn(UnstableApi::class)
class MainActivity : Activity() {

    private var player: ExoPlayer? = null
    private var indice = 0

    // Enlaces de prueba (videos de muestra libres)
    private val servidores = listOf(
        ServerLink(
            "Servidor roto (prueba de fallback)",
            "https://example.com/no-existe.mp4"
        ),
        ServerLink(
            "Prueba HLS",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
            tipo = "hls"
        ),
        ServerLink(
            "Prueba MP4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            tipo = "mp4"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val vista = PlayerView(this)
        setContentView(vista)

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        vista.player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@MainActivity,
                    "Falló: ${servidores[indice].nombre}",
                    Toast.LENGTH_SHORT
                ).show()
                indice++
                if (indice < servidores.size) {
                    reproducir(servidores[indice])
                } else {
                    Toast.makeText(
                        this@MainActivity,
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

        val item = MediaItem.Builder()
            .setUri(servidor.url)
            .apply { if (mime != null) setMimeType(mime) }
            .build()

        val fuente = DefaultMediaSourceFactory(fuenteDatos).createMediaSource(item)
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
