package com.mistream.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

object ImageLoader {

    private const val ANCHO_MAX = 400

    // Caché en memoria: 1/8 de la memoria disponible
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Máximo 4 descargas a la vez
    private val pool = Executors.newFixedThreadPool(4)
    private var carpeta: File? = null

    private fun dir(): File {
        carpeta?.let { return it }
        val d = File(KuroApp.contexto.cacheDir, "portadas")
        d.mkdirs()
        carpeta = d
        return d
    }

    private fun nombreArchivo(url: String): String {
        val h = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        return h.joinToString("") { "%02x".format(it) }
    }

    // Reduce la imagen al tamaño que se necesita: carga rápido y gasta poca memoria
    private fun decodificar(bytes: ByteArray): Bitmap? {
        val medida = BitmapFactory.Options()
        medida.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medida)
        var muestra = 1
        while (medida.outWidth / (muestra * 2) >= ANCHO_MAX) muestra *= 2
        val o = BitmapFactory.Options()
        o.inSampleSize = muestra
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    }

    // Lee la imagen del disco o la descarga (se llama desde un hilo del pool)
    private fun obtener(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        val archivo = File(dir(), nombreArchivo(url))
        val bytes = if (archivo.exists()) {
            archivo.readBytes()
        } else {
            val con = URL(url).openConnection() as HttpURLConnection
            con.connectTimeout = 10000
            con.readTimeout = 10000
            con.setRequestProperty("User-Agent", "Mozilla/5.0")
            val b = con.inputStream.use { it.readBytes() }
            con.disconnect()
            try {
                archivo.writeBytes(b)
            } catch (e: Exception) {
                // sin caché en disco, no pasa nada
            }
            b
        }
        return decodificar(bytes)
    }

    // Versión diminuta de la imagen: al estirarla se ve borrosa (para el fondo de la portada)
    private fun desenfocar(bmp: Bitmap): Bitmap {
        val ancho = 32
        val alto = (ancho * bmp.height / bmp.width).coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, ancho, alto, true)
    }

    fun cargar(url: String, vista: ImageView) = poner(url, url, vista, false)

    fun cargarDesenfocada(url: String, vista: ImageView) = poner(url + "#borrosa", url, vista, true)

    private fun poner(clave: String, url: String, vista: ImageView, borrosa: Boolean) {
        vista.tag = clave
        vista.setImageDrawable(null)
        if (url.isEmpty()) return

        val guardada = cache.get(clave)
        if (guardada != null) {
            vista.setImageBitmap(guardada)
            return
        }

        pool.execute {
            try {
                // Si la lista ya reutilizó esta vista para otra imagen, no se descarga
                if (vista.tag != clave) return@execute
                val original = obtener(url) ?: return@execute
                val listo = if (borrosa) desenfocar(original) else original
                cache.put(clave, listo)
                vista.post {
                    if (vista.tag == clave) vista.setImageBitmap(listo)
                }
            } catch (e: Exception) {
                // Si falla, queda el fondo gris
            }
        }
    }
}
