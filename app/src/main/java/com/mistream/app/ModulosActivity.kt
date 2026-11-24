package com.mistream.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONException

class ModulosActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var barraTabs: LinearLayout
    private lateinit var listaCont: LinearLayout
    private lateinit var campo: EditText
    private var categoriaSel = 0

    private val teal = Color.parseColor("#5EDDC8")
    private val chip = Color.parseColor("#2A2640")

    private fun dp(v: Int) = Ui.dp(this, v)

    private val plantilla = """Quiero que me crees un módulo para mi app KuroAnime. Un módulo es un archivo JSON con este formato exacto (responde solo con el JSON, sin explicaciones ni bloques de código):

{
  "nombre": "Nombre del módulo",
  "items": [
    {
      "titulo": "Título",
      "portada": "https://enlace-a-la-imagen.jpg",
      "descripcion": "Sinopsis corta",
      "generos": ["Acción"],
      "tipo": "Serie",
      "estado": "En emisión",
      "ultimo": "Episodio 1",
      "episodios": [
        {
          "titulo": "Episodio 1",
          "servidores": [
            { "nombre": "Servidor 1", "url": "https://enlace-al-video.mp4", "tipo": "mp4" }
          ]
        }
      ]
    }
  ]
}

Reglas: el "tipo" del servidor puede ser mp4, hls o dash. Puedes añadir "headers" y "subtitulos" si hacen falta. Usa solo contenido que sea mío, de dominio público o con una licencia que permita verlo. Estos son los títulos y enlaces que quiero incluir:
"""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mistream", MODE_PRIVATE)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Ui.FONDO)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(16), dp(40), dp(16), dp(32))
        scroll.addView(raiz)

        // Cabecera: logo + volver
        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.HORIZONTAL
        cab.gravity = Gravity.CENTER_VERTICAL
        val logo = LinearLayout(this)
        logo.orientation = LinearLayout.HORIZONTAL
        val kuro = TextView(this)
        kuro.text = "Kuro"
        kuro.setTextColor(Color.WHITE)
        kuro.textSize = 28f
        kuro.setTypeface(null, Typeface.BOLD)
        val anime = TextView(this)
        anime.text = "Anime"
        anime.setTextColor(Ui.ROSA)
        anime.textSize = 28f
        anime.setTypeface(null, Typeface.BOLD)
        logo.addView(kuro)
        logo.addView(anime)
        cab.addView(
            logo,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val volver = Ui.pildora(this, "‹ Volver", 14f)
        volver.setOnClickListener { finish() }
        cab.addView(volver)
        raiz.addView(cab)

        // Título
        val titulo = TextView(this)
        titulo.text = "Módulos"
        titulo.setTextColor(Color.WHITE)
        titulo.textSize = 34f
        titulo.setTypeface(null, Typeface.BOLD)
        titulo.setPadding(dp(2), dp(20), 0, dp(12))
        raiz.addView(titulo)

        // Pestañas de categoría
        barraTabs = LinearLayout(this)
        barraTabs.orientation = LinearLayout.HORIZONTAL
        raiz.addView(barraTabs)

        // Lista de módulos
        listaCont = LinearLayout(this)
        listaCont.orientation = LinearLayout.VERTICAL
        listaCont.setPadding(0, dp(14), 0, 0)
        raiz.addView(listaCont)

        // Instalar
        val tInstalar = TextView(this)
        tInstalar.text = "Instalar un módulo"
        tInstalar.setTextColor(Color.WHITE)
        tInstalar.textSize = 18f
        tInstalar.setTypeface(null, Typeface.BOLD)
        tInstalar.setPadding(dp(2), dp(24), 0, dp(10))
        raiz.addView(tInstalar)

        campo = EditText(this)
        campo.hint = "Pega aquí el código del módulo o una URL"
        campo.setHintTextColor(Ui.GRIS)
        campo.setTextColor(Color.WHITE)
        campo.typeface = Typeface.MONOSPACE
        campo.textSize = 13f
        campo.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        campo.minLines = 6
        campo.maxLines = 10
        campo.gravity = Gravity.TOP or Gravity.START
        campo.background = Ui.fondo(this, Ui.PILDORA, 14, Ui.BORDE)
        campo.setPadding(dp(14), dp(12), dp(14), dp(12))
        raiz.addView(campo)

        val bInstalar = boton("Instalar código pegado", Ui.ROSA, Ui.FONDO)
        bInstalar.setOnClickListener { instalar(campo.text.toString()) }
        raiz.addView(bInstalar, lpBoton())

        val bArchivo = boton("Instalar desde archivo", chip, Color.WHITE)
        bArchivo.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            startActivityForResult(intent, 101)
        }
        raiz.addView(bArchivo, lpBoton())

        val bPlantilla = boton("Copiar plantilla para pedirle módulos a tu IA", chip, Color.WHITE)
        bPlantilla.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("plantilla", plantilla))
            Toast.makeText(this, "Plantilla copiada", Toast.LENGTH_SHORT).show()
        }
        raiz.addView(bPlantilla, lpBoton())

        val ayuda = TextView(this)
        ayuda.text = "Un módulo es un archivo JSON con tu catálogo: títulos, portadas y enlaces de episodios. " +
            "Instala solo módulos que hayas creado tú o de personas de confianza, y con contenido que tengas derecho a ver."
        ayuda.setTextColor(Ui.GRIS)
        ayuda.textSize = 13f
        ayuda.setPadding(dp(2), dp(16), dp(2), 0)
        raiz.addView(ayuda)

        setContentView(scroll)
        refrescar()
    }

    private fun lpBoton(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.topMargin = dp(10)
        return p
    }

    private fun boton(texto: String, fondo: Int, colorTexto: Int): TextView {
        val t = TextView(this)
        t.text = texto
        t.setTextColor(colorTexto)
        t.textSize = 16f
        t.setTypeface(null, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(12), dp(14), dp(12), dp(14))
        t.background = Ui.fondo(this, fondo, 24)
        return t
    }

    private fun chica(texto: String, color: Int): TextView {
        val t = TextView(this)
        t.text = texto
        t.setTextColor(color)
        t.textSize = 16f
        t.setTypeface(null, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.background = Ui.fondo(this, chip, 18)
        val p = LinearLayout.LayoutParams(dp(36), dp(36))
        p.leftMargin = dp(6)
        t.layoutParams = p
        return t
    }

    private fun instalar(texto: String) {
        try {
            val nombre = Repositorio.instalarTexto(
                prefs, texto, Repositorio.CATEGORIAS[categoriaSel]
            )
            campo.setText("")
            refrescar()
            Toast.makeText(this, "Instalado: $nombre", Toast.LENGTH_SHORT).show()
        } catch (e: JSONException) {
            Toast.makeText(
                this,
                "No es un módulo válido. Revisa que sea JSON con \"nombre\" e \"items\".",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "No se pudo instalar: " + (e.message ?: "error"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 101 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val texto = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
            if (texto == null) {
                Toast.makeText(this, "No se pudo leer el archivo", Toast.LENGTH_LONG).show()
                return
            }
            instalar(texto)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo leer el archivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun refrescar() {
        val todos = Repositorio.leerInstalados(prefs)

        barraTabs.removeAllViews()
        for ((i, cat) in Repositorio.CATEGORIAS.withIndex()) {
            val n = todos.count { it.categoria == cat }
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL

            val t = TextView(this)
            t.text = "$cat  $n"
            t.gravity = Gravity.CENTER
            t.textSize = 13f
            t.maxLines = 1
            t.setTypeface(null, Typeface.BOLD)
            t.setTextColor(if (i == categoriaSel) Ui.ROSA else Color.WHITE)
            t.setPadding(0, dp(10), 0, dp(10))
            col.addView(t)

            val linea = View(this)
            linea.setBackgroundColor(if (i == categoriaSel) Ui.ROSA else Ui.BORDE)
            col.addView(
                linea,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    if (i == categoriaSel) dp(3) else dp(1)
                )
            )
            col.setOnClickListener {
                categoriaSel = i
                refrescar()
            }
            barraTabs.addView(
                col,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        listaCont.removeAllViews()
        val delCat = todos.filter { it.categoria == Repositorio.CATEGORIAS[categoriaSel] }
        if (delCat.isEmpty()) {
            val vacio = TextView(this)
            vacio.text = "No hay módulos en esta categoría."
            vacio.setTextColor(Ui.GRIS)
            vacio.textSize = 14f
            vacio.setPadding(dp(2), dp(8), 0, dp(8))
            listaCont.addView(vacio)
        } else {
            for (m in delCat) {
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                p.bottomMargin = dp(8)
                listaCont.addView(tarjeta(m), p)
            }
        }
    }

    private fun tarjeta(m: Repositorio.Instalado): View {
        val fila = LinearLayout(this)
        fila.orientation = LinearLayout.HORIZONTAL
        fila.gravity = Gravity.CENTER_VERTICAL
        fila.setPadding(dp(10), dp(8), dp(10), dp(8))
        fila.background = if (m.activo) Ui.fondo(this, Ui.PILDORA, 14, teal)
        else Ui.fondo(this, Ui.PILDORA, 14)

        val avatar = TextView(this)
        avatar.text = m.nombre.take(1).uppercase()
        avatar.gravity = Gravity.CENTER
        avatar.textSize = 18f
        avatar.setTypeface(null, Typeface.BOLD)
        avatar.setTextColor(if (m.activo) teal else Ui.GRIS)
        avatar.background = Ui.fondo(this, chip, 10)
        fila.addView(avatar, LinearLayout.LayoutParams(dp(40), dp(40)))

        val centro = LinearLayout(this)
        centro.orientation = LinearLayout.VERTICAL
        centro.setPadding(dp(10), 0, dp(4), 0)

        val filaNombre = LinearLayout(this)
        filaNombre.orientation = LinearLayout.HORIZONTAL
        filaNombre.gravity = Gravity.CENTER_VERTICAL

        val nombre = TextView(this)
        nombre.text = m.nombre
        nombre.setTextColor(Color.WHITE)
        nombre.textSize = 15f
        nombre.setTypeface(null, Typeface.BOLD)
        nombre.maxLines = 1
        nombre.ellipsize = TextUtils.TruncateAt.END
        filaNombre.addView(nombre)

        if (m.activo) {
            val tag = TextView(this)
            tag.text = "activo"
            tag.setTextColor(teal)
            tag.textSize = 10f
            tag.setTypeface(null, Typeface.BOLD)
            tag.setPadding(dp(6), dp(1), dp(6), dp(1))
            tag.background = Ui.fondo(this, Ui.PILDORA, 10, teal)
            val pt = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            pt.leftMargin = dp(6)
            filaNombre.addView(tag, pt)
        }
        centro.addView(filaNombre)

        val sub = TextView(this)
        sub.text = if (m.tipo == "url") {
            try {
                java.net.URI(m.valor).host ?: m.valor
            } catch (e: Exception) {
                m.valor
            }
        } else {
            "Código pegado"
        }
        sub.setTextColor(Ui.GRIS)
        sub.textSize = 12f
        sub.maxLines = 1
        sub.ellipsize = TextUtils.TruncateAt.END
        centro.addView(sub)

        fila.addView(
            centro,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val bMover = chica("⇄", Color.WHITE)
        bMover.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Mover a...")
                .setItems(Repositorio.CATEGORIAS.toTypedArray()) { _, i ->
                    Repositorio.cambiarCategoria(prefs, m.id, Repositorio.CATEGORIAS[i])
                    refrescar()
                }
                .show()
        }
        fila.addView(bMover)

        val bActivo = chica(if (m.activo) "✓" else "○", if (m.activo) teal else Ui.GRIS)
        bActivo.setOnClickListener {
            Repositorio.alternarActivo(prefs, m.id)
            refrescar()
        }
        fila.addView(bActivo)

        val bBorrar = chica("🗑", Color.WHITE)
        bBorrar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Quitar módulo")
                .setMessage(m.nombre)
                .setPositiveButton("Quitar") { _, _ ->
                    Repositorio.eliminar(prefs, m.id)
                    refrescar()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        fila.addView(bBorrar)

        return fila
    }
}
