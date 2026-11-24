package com.mistream.app

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class ModulosActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var lista: ListView
    private lateinit var campo: EditText
    private var urls = mutableListOf<String>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mistream", MODE_PRIVATE)
        urls = Repositorio.leerUrls(prefs)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Color.parseColor("#121212"))
        raiz.setPadding(dp(16), dp(48), dp(16), dp(16))

        val titulo = TextView(this)
        titulo.text = "Módulos"
        titulo.setTextColor(Color.WHITE)
        titulo.textSize = 22f
        titulo.setTypeface(null, Typeface.BOLD)
        raiz.addView(titulo)

        campo = EditText(this)
        campo.hint = "URL del módulo o índice (.json)"
        campo.setHintTextColor(Color.GRAY)
        campo.setTextColor(Color.WHITE)
        campo.setSingleLine()
        raiz.addView(campo)

        val botonAgregar = Button(this)
        botonAgregar.text = "Agregar"
        raiz.addView(botonAgregar)

        val ayuda = TextView(this)
        ayuda.text = "Toca un módulo de la lista para quitarlo."
        ayuda.setTextColor(Color.GRAY)
        ayuda.textSize = 12f
        ayuda.setPadding(0, dp(8), 0, dp(8))
        raiz.addView(ayuda)

        lista = ListView(this)
        raiz.addView(
            lista,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        setContentView(raiz)

        botonAgregar.setOnClickListener {
            val u = campo.text.toString().trim()
            when {
                !u.startsWith("http") ->
                    Toast.makeText(this, "La URL debe empezar con http", Toast.LENGTH_SHORT).show()
                urls.contains(u) ->
                    Toast.makeText(this, "Ya está agregado", Toast.LENGTH_SHORT).show()
                else -> {
                    urls.add(u)
                    Repositorio.guardarUrls(prefs, urls)
                    campo.setText("")
                    refrescar()
                    Toast.makeText(this, "Módulo agregado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        lista.setOnItemClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setTitle("Quitar módulo")
                .setMessage(urls[pos])
                .setPositiveButton("Quitar") { _, _ ->
                    urls.removeAt(pos)
                    Repositorio.guardarUrls(prefs, urls)
                    refrescar()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        refrescar()
    }

    private fun refrescar() {
        lista.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, urls)
    }
}
