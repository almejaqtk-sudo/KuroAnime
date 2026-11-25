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

    private fun descargar(url: String, referer: String): ByteArray? {
        val con = URL(url).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 15000
            con.readTimeout = 15000
            con.instanceFollowRedirects = true
            con.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            con.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            con.setRequestProperty("Referer", referer)
            val codigo = con.responseCode
            if (codigo !in 200..299) return null
            val tipo = con.contentType?.lowercase().orEmpty()
            if (tipo.isNotEmpty() && !tipo.startsWith("image/") && !tipo.contains("octet-stream")) return null
            return con.inputStream.use { it.readBytes() }
        } finally {
            con.disconnect()
        }
    }

    // Lee la imagen del disco o la descarga (se llama desde un hilo del pool).
    // Primero prueba el origen del CDN y, si hay hotlink protection, el Referer del módulo VeoHentai.
    private fun obtener(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        val archivo = File(dir(), nombreArchivo(url))
        val bytes = if (archivo.exists()) {
            archivo.readBytes()
        } else {
            val parsed = URL(url)
            val origen = "${parsed.protocol}://${parsed.host}/"
            val b = descargar(url, origen) ?: descargar(url, "https://veohentai.com/") ?: return null
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
