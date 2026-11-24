package com.mistream.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import java.net.URL

class MainActivity : Activity() {

    private lateinit var campoUrl: EditText
    private lateinit var lista: ListView
    private var modulo: Modulo? = null
    private var itemActual: Item? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mistream", MODE_PRIVATE)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(32, 120, 32, 32)

        campoUrl = EditText(this)
        campoUrl.hint = "URL del módulo (.json)"
        campoUrl.setSingleLine()
        campoUrl.setText(prefs.getString("url", ""))

        val boton = Button(this)
        boton.text = "Cargar módulo"

        lista = ListView(this)

        raiz.addView(campoUrl)
        raiz.addView(boton)
        raiz.addView(
            lista,
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

        lista.setOnItemClickListener { _, _, pos, _ ->
            val actual = itemActual
            if (actual == null) {
                val m = modulo ?: return@setOnItemClickListener
                mostrarEpisodios(m.items[pos])
            } else {
                val ep = actual.episodios[pos]
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putExtra("titulo", ep.titulo)
                intent.putExtra("servidores", ModuloParser.servidoresAJson(ep.servidores))
                startActivity(intent)
            }
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
                    mostrarItems()
                    Toast.makeText(this, "Módulo: ${m.nombre}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun mostrarItems() {
        itemActual = null
        val titulos = modulo?.items?.map { it.titulo } ?: emptyList()
        lista.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, titulos)
    }

    private fun mostrarEpisodios(item: Item) {
        itemActual = item
        val titulos = item.episodios.map { it.titulo }
        lista.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, titulos)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (itemActual != null) mostrarItems() else super.onBackPressed()
    }
}
