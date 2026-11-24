package com.mistream.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.URL

class MainActivity : Activity() {

    private lateinit var campoUrl: EditText
    private lateinit var grilla: GridView
    private var modulo: Modulo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mistream", MODE_PRIVATE)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Color.parseColor("#121212"))
        raiz.setPadding(24, 96, 24, 16)

        campoUrl = EditText(this)
        campoUrl.hint = "URL del módulo (.json)"
        campoUrl.setHintTextColor(Color.GRAY)
        campoUrl.setTextColor(Color.WHITE)
        campoUrl.setSingleLine()
        campoUrl.setText(prefs.getString("url", ""))

        val boton = Button(this)
        boton.text = "Cargar módulo"

        grilla = GridView(this)
        grilla.numColumns = 3
        grilla.verticalSpacing = 8
        grilla.horizontalSpacing = 8
        grilla.setPadding(0, 16, 0, 0)

        raiz.addView(campoUrl)
        raiz.addView(boton)
        raiz.addView(
            grilla,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        setContentView(raiz)

        boton.setOnClickListener {
            val url = campoUrl.text.toString().trim()
            prefs.edit().putString("url", url).apply()
            cargar(url)
        }

        grilla.setOnItemClickListener { _, _, pos, _ ->
            val m = modulo ?: return@setOnItemClickListener
            val item = m.items[pos]
            val intent = Intent(this, DetalleActivity::class.java)
            intent.putExtra("item", item.titulo)
            intent.putExtra("portada", item.portada)
            intent.putExtra("descripcion", item.descripcion)
            intent.putExtra("episodios", ModuloParser.episodiosAJson(item.episodios))
            startActivity(intent)
        }

        val guardada = campoUrl.text.toString().trim()
        if (guardada.isNotEmpty()) cargar(guardada)
    }

    private fun cargar(url: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "Escribe la URL del módulo", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val texto = URL(url).readText()
                val m = ModuloParser.parse(texto)
                runOnUiThread {
                    modulo = m
                    grilla.adapter = AdaptadorGrilla(m.items)
                    Toast.makeText(this, "Módulo: ${m.nombre}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private inner class AdaptadorGrilla(private val items: List<Item>) : BaseAdapter() {

        private val anchoImg = resources.displayMetrics.widthPixels / 3 - 40
        private val altoImg = anchoImg * 3 / 2

        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]

            val celda = LinearLayout(this@MainActivity)
            celda.orientation = LinearLayout.VERTICAL
            celda.setPadding(8, 8, 8, 16)

            val img = ImageView(this@MainActivity)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.setBackgroundColor(Color.parseColor("#2A2A2A"))
            celda.addView(
                img,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, altoImg
                )
            )
            ImageLoader.cargar(item.portada, img)

            val txt = TextView(this@MainActivity)
            txt.text = item.titulo
            txt.setTextColor(Color.WHITE)
            txt.textSize = 13f
            txt.maxLines = 2
            txt.ellipsize = TextUtils.TruncateAt.END
            txt.setPadding(0, 8, 0, 0)
            celda.addView(
                txt,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            return celda
        }
    }
}
