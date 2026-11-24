package com.mistream.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class DetalleActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var botonContinuar: TextView
    private lateinit var botonFav: TextView
    private lateinit var tvEps: TextView
    private lateinit var lista: ListView
    private var titulo = ""
    private var episodiosJson = "[]"
    private var episodios: List<Episodio> = emptyList()
    private var indiceContinuar = 0

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mistream", MODE_PRIVATE)

        titulo = intent.getStringExtra("item") ?: ""
        val portada = intent.getStringExtra("portada") ?: ""
        val descripcion = intent.getStringExtra("descripcion") ?: ""
        val meta = intent.getStringExtra("meta") ?: ""
        episodiosJson = intent.getStringExtra("episodios") ?: "[]"
        episodios = ModuloParser.episodiosDeJson(episodiosJson)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Ui.FONDO)
        raiz.setPadding(dp(16), dp(40), dp(16), dp(16))

        // Cabecera: portada + textos
        val cabecera = LinearLayout(this)
        cabecera.orientation = LinearLayout.HORIZONTAL

        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.background = Ui.fondo(this, Ui.PILDORA, 14)
        Ui.redondear(img, dp(14).toFloat())
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

        if (meta.isNotEmpty()) {
            val tvMeta = TextView(this)
            tvMeta.text = meta
            tvMeta.setTextColor(Ui.ROSA)
            tvMeta.textSize = 13f
            tvMeta.setTypeface(null, Typeface.BOLD)
            tvMeta.setPadding(0, dp(4), 0, 0)
            textos.addView(tvMeta)
        }

        val tvDesc = TextView(this)
        tvDesc.text = if (descripcion.isEmpty()) "Sin descripción" else descripcion
        tvDesc.setTextColor(Color.parseColor("#B9B5CC"))
        tvDesc.textSize = 14f
        tvDesc.setPadding(0, dp(8), 0, 0)
        tvDesc.maxLines = 7
        textos.addView(tvDesc)

        cabecera.addView(
            textos,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        raiz.addView(cabecera)

        // Acciones: continuar + Mi lista
        val acciones = LinearLayout(this)
        acciones.orientation = LinearLayout.HORIZONTAL
        acciones.gravity = Gravity.CENTER_VERTICAL

        botonContinuar = TextView(this)
        botonContinuar.setTextColor(Color.WHITE)
        botonContinuar.textSize = 15f
        botonContinuar.setTypeface(null, Typeface.BOLD)
        botonContinuar.gravity = Gravity.CENTER
        botonContinuar.maxLines = 1
        botonContinuar.setPadding(dp(12), dp(12), dp(12), dp(12))
        botonContinuar.background = Ui.fondo(this, Ui.ROSA, 22)
        botonContinuar.setOnClickListener { reproducir(indiceContinuar) }
        val lpContinuar = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lpContinuar.rightMargin = dp(8)
        acciones.addView(botonContinuar, lpContinuar)

        botonFav = Ui.pildora(this, "☆ Mi lista")
        botonFav.setOnClickListener {
            val ahora = Favoritos.alternar(prefs, titulo)
            actualizarFav()
            Toast.makeText(
                this,
                if (ahora) "Agregado a Mi lista" else "Quitado de Mi lista",
                Toast.LENGTH_SHORT
            ).show()
        }
        acciones.addView(botonFav)

        val lpAcciones = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpAcciones.setMargins(0, dp(16), 0, dp(12))
        raiz.addView(acciones, lpAcciones)

        // Lista de episodios
        tvEps = TextView(this)
        tvEps.text = "Episodios"
        tvEps.setTextColor(Color.WHITE)
        tvEps.textSize = 18f
        tvEps.setTypeface(null, Typeface.BOLD)
        raiz.addView(tvEps)

        lista = ListView(this)
        lista.setOnItemClickListener { _, _, pos, _ -> reproducir(pos) }
        raiz.addView(
            lista,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        setContentView(raiz)
        mostrarLista()
        actualizarFav()

        if (episodios.isEmpty()) cargarEpisodiosRemotos(portada)
    }

    override fun onResume() {
        super.onResume()
        actualizarBoton()
        actualizarFav()
    }

    private fun mostrarLista() {
        lista.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            episodios.map { it.titulo }
        )
    }

    // Módulos JS: los episodios se piden al abrir el título
    private fun cargarEpisodiosRemotos(portada: String) {
        val item = Repositorio.buscarItem(titulo, portada)
        if (item == null || item.fuente.isEmpty() || item.id.isEmpty()) {
            tvEps.text = "Sin episodios"
            actualizarBoton()
            return
        }
        tvEps.text = "Cargando episodios..."
        Thread {
            try {
                val eps = Motores.episodios(item.fuente, item.id)
                runOnUiThread {
                    episodios = eps
                    episodiosJson = ModuloParser.episodiosAJson(eps)
                    mostrarLista()
                    tvEps.text = if (eps.isEmpty()) "Sin episodios" else "Episodios"
                    actualizarBoton()
                    prefetch()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvEps.text = "No se pudieron cargar los episodios"
                    Toast.makeText(
                        this,
                        "Error: " + (e.message ?: "desconocido"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun actualizarFav() {
        botonFav.text = if (Favoritos.esFavorito(prefs, titulo)) "★ En Mi lista" else "☆ Mi lista"
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

    // Pide los servidores del episodio que probablemente vas a ver, así al tocarlo ya están listos
    private fun prefetch() {
        val s = episodios.getOrNull(indiceContinuar)?.servidores ?: return
        if (s.size == 1 && s[0].tipo == "hub") {
            Thread {
                try {
                    MotorHub.resolverVideos(s[0], prefs)
                } catch (e: Exception) {
                    // se intentará de nuevo al tocar el episodio
                }
            }.start()
        }
    }

    private fun reproducir(indice: Int) {
        if (indice !in episodios.indices) {
            Toast.makeText(this, "Sin episodios", Toast.LENGTH_SHORT).show()
            return
        }
        val s = episodios[indice].servidores
        when {
            s.size == 1 && s[0].tipo == "hub" -> elegirServidorHub(indice, s[0])
            s.size > 1 -> mostrarSelector(indice, s)
            else -> abrirReproductor(indice)
        }
    }

    // Módulos AnimeHub: busca los servidores y los muestra para elegir
    private fun elegirServidorHub(indice: Int, marcador: ServerLink) {
        var cancelado = false
        val carga = AlertDialog.Builder(this)
            .setMessage("Buscando servidores...")
            .setOnCancelListener { cancelado = true }
            .create()
        carga.show()
        Thread {
            try {
                val lista = MotorHub.resolverVideos(marcador, prefs)
                runOnUiThread {
                    carga.dismiss()
                    if (cancelado || isFinishing) return@runOnUiThread
                    if (lista.isEmpty()) {
                        Toast.makeText(this, "Sin servidores", Toast.LENGTH_LONG).show()
                    } else {
                        mostrarSelector(indice, lista)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    carga.dismiss()
                    Toast.makeText(
                        this,
                        "No se pudieron cargar los servidores: " + (e.message ?: "error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun mostrarSelector(indice: Int, lista: List<ServerLink>) {
        val pref = prefs.getString("srv_pref", null)
        val etiquetas = lista.map { s ->
            (if (s.nombre == pref) "★ " else "") + s.nombre + when (s.tipo) {
                "embed" -> "  · web"
                "external" -> "  · navegador"
                else -> ""
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Elegir servidor")
            .setItems(etiquetas) { _, which ->
                prefs.edit().putString("srv_pref", lista[which].nombre).apply()
                abrirReproductor(indice)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirReproductor(indice: Int) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("item", titulo)
        intent.putExtra("episodios", episodiosJson)
        intent.putExtra("indice", indice)
        startActivity(intent)
    }
}
