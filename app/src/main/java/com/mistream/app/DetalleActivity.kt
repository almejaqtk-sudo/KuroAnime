package com.mistream.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class DetalleActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var botonContinuar: Button
    private var titulo = ""
    private var episodiosJson = "[]"
    private var episodios: List<Episodio> = emptyList()
    private var indiceContinuar = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mistream", MODE_PRIVATE)

        titulo = intent.getStringExtra("item") ?: ""
        val portada = intent.getStringExtra("portada") ?: ""
        val descripcion = intent.getStringExtra("descripcion") ?: ""
        episodiosJson = intent.getStringExtra("episodios") ?: "[]"
        episodios = ModuloParser.episodiosDeJson(episodiosJson)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Color.parseColor("#121212"))
        raiz.setPadding(dp(16), dp(48), dp(16), dp(16))

        val cabecera = LinearLayout(this)
        cabecera.orientation = LinearLayout.HORIZONTAL

        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.setBackgroundColor(Color.parseColor("#2A2A2A"))
        cabecera.addView(img, LinearLayout.LayoutParams(dp(120), dp(180)))
        ImageLoader.cargar(portada, img)

        val textos = LinearLayout(this)
        textos.orientation = LinearLayout.VERTICAL
        textos.setPadding(dp(16), 0, 0, 0)

        val tvTitulo = TextView(this)
        tvTitulo.text = titulo
        tvTitulo.setTextColor(Color.WHITE)
        tvTitulo.textSize = 22f
        tvTitulo.setTypeface(null, Typeface.BOLD)
        textos.addView(tvTitulo)

        val tvDesc = TextView(this)
        tvDesc.text = if (descripcion.isEmpty()) "Sin descripción" else descripcion
        tvDesc.setTextColor(Color.parseColor("#BBBBBB"))
        tvDesc.textSize = 14f
        tvDesc.setPadding(0, dp(8), 0, 0)
        textos.addView(tvDesc)

        cabecera.addView(
            textos,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        raiz.addView(cabecera)

        botonContinuar = Button(this)
        val lpBoton = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpBoton.setMargins(0, dp(16), 0, dp(8))
        raiz.addView(botonContinuar, lpBoton)
        botonContinuar.setOnClickListener { reproducir(indiceContinuar) }

        val tvEps = TextView(this)
        tvEps.text = "Episodios"
        tvEps.setTextColor(Color.WHITE)
        tvEps.textSize = 16f
        tvEps.setTypeface(null, Typeface.BOLD)
        raiz.addView(tvEps)

        val lista = ListView(this)
        lista.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            episodios.map { it.titulo }
        )
        lista.setOnItemClickListener { _, _, pos, _ -> reproducir(pos) }
        raiz.addView(
            lista,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        setContentView(raiz)
    }

    override fun onResume() {
        super.onResume()
        actualizarBoton()
    }

    private fun actualizarBoton() {
        if (episodios.isEmpty()) {
            botonContinuar.visibility = View.GONE
            return
        }
        botonContinuar.visibility = View.VISIBLE
        var ultimo = -1
        for (i in episodios.indices) {
            val clave = "pos_" + titulo + "_" + episodios[i].titulo
            if (prefs.getLong(clave, 0L) > 0) ultimo = i
        }
        if (ultimo >= 0) {
            indiceContinuar = ultimo
            botonContinuar.text = "Continuar: " + episodios[ultimo].titulo
        } else {
            indiceContinuar = 0
            botonContinuar.text = "▶ Ver desde el principio"
        }
    }

    private fun reproducir(indice: Int) {
        if (indice !in episodios.indices) {
            Toast.makeText(this, "Sin episodios", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("item", titulo)
        intent.putExtra("episodios", episodiosJson)
        intent.putExtra("indice", indice)
        startActivity(intent)
    }
}
