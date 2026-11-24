package com.mistream.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
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

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var buscador: EditText
    private lateinit var grilla: GridView
    private lateinit var estado: TextView
    private var todos: List<Item> = emptyList()
    private var visibles: List<Item> = emptyList()
    private var versionCargada = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mistream", MODE_PRIVATE)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Color.parseColor("#121212"))
        raiz.setPadding(24, 96, 24, 16)

        val fila = LinearLayout(this)
        fila.orientation = LinearLayout.HORIZONTAL

        buscador = EditText(this)
        buscador.hint = "Buscar..."
        buscador.setHintTextColor(Color.GRAY)
        buscador.setTextColor(Color.WHITE)
        buscador.setSingleLine()
        fila.addView(
            buscador,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val botonModulos = Button(this)
        botonModulos.text = "Módulos"
        fila.addView(
            botonModulos,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        raiz.addView(fila)

        estado = TextView(this)
        estado.setTextColor(Color.GRAY)
        estado.textSize = 12f
        estado.setPadding(8, 8, 8, 0)
        raiz.addView(estado)

        grilla = GridView(this)
        grilla.numColumns = 3
        grilla.verticalSpacing = 8
        grilla.horizontalSpacing = 8
        grilla.setPadding(0, 16, 0, 0)
        raiz.addView(
            grilla,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        setContentView(raiz)

        botonModulos.setOnClickListener {
            startActivity(Intent(this, ModulosActivity::class.java))
        }

        buscador.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filtrar()
            }
        })

        grilla.setOnItemClickListener { _, _, pos, _ ->
            val item = visibles[pos]
            val intent = Intent(this, DetalleActivity::class.java)
            intent.putExtra("item", item.titulo)
            intent.putExtra("portada", item.portada)
            intent.putExtra("descripcion", item.descripcion)
            intent.putExtra("episodios", ModuloParser.episodiosAJson(item.episodios))
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        val urls = Repositorio.leerUrls(prefs)
        val ver = prefs.getInt("modulos_ver", 0)
        if (ver != versionCargada) {
            versionCargada = ver
            recargar(urls)
        }
    }

    private fun recargar(urls: List<String>) {
        if (urls.isEmpty()) {
            todos = emptyList()
            filtrar()
            return
        }
        estado.text = "Cargando módulos..."
        Thread {
            val r = Repositorio.cargarTodo(urls)
            runOnUiThread {
                todos = r.modulos.flatMap { it.items }
                filtrar()
                if (r.errores.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "No cargó: " + r.errores.joinToString(" | "),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun filtrar() {
        val q = buscador.text.toString().trim().lowercase()
        visibles = if (q.isEmpty()) todos else todos.filter { it.titulo.lowercase().contains(q) }
        grilla.adapter = AdaptadorGrilla(visibles)
        estado.text = when {
            todos.isEmpty() -> "Sin contenido. Toca Módulos para agregar uno."
            q.isEmpty() -> "${visibles.size} títulos"
            else -> "${visibles.size} resultados"
        }
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
