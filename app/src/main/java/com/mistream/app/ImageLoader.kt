package com.mistream.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL

object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cargar(url: String, vista: ImageView) {
        vista.tag = url
        vista.setImageDrawable(null)
        if (url.isEmpty()) return

        val guardada = cache.get(url)
        if (guardada != null) {
            vista.setImageBitmap(guardada)
            return
        }

        Thread {
            try {
                val con = URL(url).openConnection() as HttpURLConnection
                con.connectTimeout = 10000
                con.readTimeout = 10000
                con.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bmp = con.inputStream.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    cache.put(url, bmp)
                    vista.post {
                        if (vista.tag == url) vista.setImageBitmap(bmp)
                    }
                }
            } catch (e: Exception) {
                // Si falla, queda el fondo gris
            }
        }.start()
    }
}
