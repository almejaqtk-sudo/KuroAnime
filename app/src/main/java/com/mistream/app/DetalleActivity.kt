package com.mistream.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class DetalleActivity : Activity() {

    private class Fila(val vista: View, val icono: TextView, val texto: TextView)

    private lateinit var prefs: SharedPreferences
    private lateinit var lista: ListView
    private lateinit var tvEps: TextView
    private lateinit var botonOrden: TextView
    private lateinit var botonFav: TextView
    private lateinit var adaptador: AdaptadorEpisodios

    private var titulo = ""
    private var portada = ""
    private var episodiosJson = "[]"
    private var episodios: List<Episodio> = emptyList()
    private var indiceContinuar = 0
    private var descendente = true

    private val colorFila = Color.parseColor("#2B2840")
    private val colorResalte = Color.parseColor("#A5B4FC")

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun circulo(texto: String, accion: () -> Unit): TextView {
        val t = TextView(this)
        t.text = texto
        t.setTextColor(Color.WHITE)
        t.textSize = 22f
        t.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(Color.parseColor("#99000000"))
        t.background = g
        t.setOnClickListener { accion() }
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mistream", MODE_PRIVATE)

        titulo = intent.getStringExtra("item") ?: ""
        portada = intent.getStringExtra("portada") ?: ""
        val descripcion = intent.getStringExtra("descripcion") ?: ""
        val meta = intent.getStringExtra("meta") ?: ""
        episodiosJson = intent.getStringExtra("episodios") ?: "[]"
        episodios = ModuloParser.episodiosDeJson(episodiosJson)
        descendente = prefs.getBoolean("eps_desc", true)

        val raiz = FrameLayout(this)
        raiz.setBackgroundColor(Ui.FONDO)

        lista = ListView(this)
        lista.divider = null
        lista.dividerHeight = 0
        lista.setBackgroundColor(Ui.FONDO)
        lista.selector = ColorDrawable(Color.TRANSPARENT)
        lista.addHeaderView(crearCabecera(descripcion, meta), null, false)
        adaptador = AdaptadorEpisodios()
        lista.adapter = adaptador
        lista.setOnItemClickListener { _, _, pos, _ ->
            val real = adaptador.indiceReal(pos - lista.headerViewsCount)
            if (real >= 0) reproducir(real)
        }
        raiz.addView(
            lista,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Botones flotantes: volver (izquierda) y Mi lista (derecha)
        val atras = circulo("←") { finish() }
        val lpAtras = FrameLayout.LayoutParams(dp(44), dp(44))
        lpAtras.gravity = Gravity.TOP or Gravity.START
        lpAtras.setMargins(dp(16), dp(12), 0, 0)
        raiz.addView(atras, lpAtras)

        botonFav = circulo("♡") {
            val ahora = Favoritos.alternar(prefs, titulo)
            actualizarFav()
            Toast.makeText(
                this,
                if (ahora) "Agregado a Mi lista" else "Quitado de Mi lista",
                Toast.LENGTH_SHORT
            ).show()
        }
        val lpFav = FrameLayout.LayoutParams(dp(44), dp(44))
        lpFav.gravity = Gravity.TOP or Gravity.END
        lpFav.setMargins(0, dp(12), dp(16), 0)
        raiz.addView(botonFav, lpFav)

        setContentView(raiz)
        actualizarFav()
        calcularContinuar()
        if (episodios.isEmpty()) cargarEpisodiosRemotos()
    }

    private fun crearCabecera(descripcion: String, meta: String): View {
        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.VERTICAL
        cab.clipChildren = false

        // Fondo: la portada desenfocada
        val banner = FrameLayout(this)
        val fondoImg = ImageView(this)
        fondoImg.scaleType = ImageView.ScaleType.CENTER_CROP
        banner.addView(
            fondoImg,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val velo = View(this)
        velo.setBackgroundColor(Color.parseColor("#66000000"))
        banner.addView(
            velo,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        ImageLoader.cargarDesenfocada(portada, fondoImg)
        cab.addView(
            banner,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220))
        )

        // Tarjeta con esquinas redondeadas que se monta sobre el fondo
        val tarjeta = LinearLayout(this)
        tarjeta.orientation = LinearLayout.VERTICAL
        tarjeta.clipChildren = false
        val r = dp(28).toFloat()
        val g = GradientDrawable()
        g.setColor(Ui.FONDO)
        g.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        tarjeta.background = g
        tarjeta.setPadding(dp(16), 0, dp(16), 0)
        val lpTarjeta = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lpTarjeta.topMargin = -dp(28)
        cab.addView(tarjeta, lpTarjeta)

        val fila = LinearLayout(this)
        fila.orientation = LinearLayout.HORIZONTAL
        fila.gravity = Gravity.CENTER_VERTICAL
        fila.clipChildren = false

        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.background = Ui.fondo(this, Ui.PILDORA, 14)
        Ui.redondear(img, dp(14).toFloat())
        val lpImg = LinearLayout.LayoutParams(dp(110), dp(165))
        lpImg.topMargin = -dp(80)
        fila.addView(img, lpImg)
        ImageLoader.cargar(portada, img)

        val textos = LinearLayout(this)
        textos.orientation = LinearLayout.VERTICAL
        textos.setPadding(dp(16), dp(8), 0, dp(8))

        val tvTitulo = TextView(this)
        tvTitulo.text = titulo
        tvTitulo.setTextColor(Color.WHITE)
        tvTitulo.textSize = 20f
        tvTitulo.setTypeface(null, Typeface.BOLD)
        tvTitulo.maxLines = 4
        tvTitulo.ellipsize = TextUtils.TruncateAt.END
        textos.addView(tvTitulo)

        if (meta.isNotEmpty()) {
            val tvMeta = TextView(this)
            tvMeta.text = meta
            tvMeta.setTextColor(Color.parseColor("#C7C9D9"))
            tvMeta.textSize = 14f
            tvMeta.setTypeface(null, Typeface.BOLD)
            tvMeta.setPadding(0, dp(6), 0, 0)
            textos.addView(tvMeta)
        }
        fila.addView(
            textos,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        tarjeta.addView(fila)

        // Descripción (solo si existe; toca para ver completa)
        if (descripcion.isNotEmpty()) {
            val tvDesc = TextView(this)
            tvDesc.text = descripcion
            tvDesc.setTextColor(Color.parseColor("#B9B5CC"))
            tvDesc.textSize = 14f
            tvDesc.maxLines = 3
            tvDesc.ellipsize = TextUtils.TruncateAt.END
            tvDesc.setPadding(0, dp(12), 0, 0)
            tvDesc.setOnClickListener {
                if (tvDesc.maxLines == 3) {
                    tvDesc.maxLines = Int.MAX_VALUE
                    tvDesc.ellipsize = null
                } else {
                    tvDesc.maxLines = 3
                    tvDesc.ellipsize = TextUtils.TruncateAt.END
                }
            }
            tarjeta.addView(tvDesc)
        }

        // Título de la sección + botón para invertir el orden
        val encabezado = LinearLayout(this)
        encabezado.orientation = LinearLayout.HORIZONTAL
        encabezado.gravity = Gravity.CENTER_VERTICAL
        encabezado.setPadding(0, dp(20), 0, dp(8))

        tvEps = TextView(this)
        tvEps.text = "Episodios"
        tvEps.setTextColor(Color.WHITE)
        tvEps.textSize = 22f
        tvEps.setTypeface(null, Typeface.BOLD)
        encabezado.addView(
            tvEps,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        botonOrden = TextView(this)
        botonOrden.text = if (descendente) "↓" else "↑"
        botonOrden.setTextColor(Color.WHITE)
        botonOrden.textSize = 26f
        botonOrden.gravity = Gravity.CENTER
        botonOrden.setPadding(dp(14), dp(4), dp(14), dp(4))
        botonOrden.setOnClickListener {
            descendente = !descendente
            prefs.edit().putBoolean("eps_desc", descendente).apply()
            botonOrden.text = if (descendente) "↓" else "↑"
            adaptador.reordenar()
            Toast.makeText(
                this,
                if (descendente) "Más recientes primero" else "Más antiguos primero",
                Toast.LENGTH_SHORT
            ).show()
        }
        encabezado.addView(botonOrden)
        tarjeta.addView(encabezado)

        return cab
    }

    private fun actualizarFav() {
        if (!::botonFav.isInitialized) return
        val fav = Favoritos.esFavorito(prefs, titulo)
        botonFav.text = if (fav) "♥" else "♡"
        botonFav.setTextColor(if (fav) Ui.ROSA else Color.WHITE)
    }

    // El episodio resaltado es el último que empezaste a ver (o el primero)
    private fun calcularContinuar() {
        var ultimo = -1
        for (i in episodios.indices) {
            val clave = "pos_" + titulo + "_" + episodios[i].titulo
            if (prefs.getLong(clave, 0L) > 0) ultimo = i
        }
        indiceContinuar = if (ultimo >= 0) ultimo else 0
        if (::adaptador.isInitialized) adaptador.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        calcularContinuar()
        actualizarFav()
    }

    // Módulos que traen solo el enlace del título: los episodios se piden al abrir
    private fun cargarEpisodiosRemotos() {
        val item = Repositorio.buscarItem(titulo, portada)
        if (item == null || item.fuente.isEmpty() || item.id.isEmpty()) {
            tvEps.text = "Sin episodios"
            return
        }
        tvEps.text = "Cargando episodios..."
        Thread {
            try {
                val eps = Motores.episodios(item.fuente, item.id)
                runOnUiThread {
                    episodios = eps
                    episodiosJson = ModuloParser.episodiosAJson(eps)
                    tvEps.text = if (eps.isEmpty()) "Sin episodios" else "Episodios"
                    adaptador.reordenar()
                    calcularContinuar()
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
                val servidores = MotorHub.resolverVideos(marcador, prefs)
                runOnUiThread {
                    carga.dismiss()
                    if (cancelado || isFinishing) return@runOnUiThread
                    if (servidores.isEmpty()) {
                        Toast.makeText(this, "Sin servidores", Toast.LENGTH_LONG).show()
                    } else {
                        mostrarSelector(indice, servidores)
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

    private fun mostrarSelector(indice: Int, servidores: List<ServerLink>) {
        val pref = prefs.getString("srv_pref", null)
        val etiquetas = servidores.map { s ->
            (if (s.nombre == pref) "★ " else "") + s.nombre + when (s.tipo) {
                "embed" -> "  · web"
                "external" -> "  · navegador"
                else -> ""
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Elegir servidor")
            .setItems(etiquetas) { _, which ->
                prefs.edit().putString("srv_pref", servidores[which].nombre).apply()
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

    // ---------- Lista de episodios (filas redondeadas) ----------

    private inner class AdaptadorEpisodios : BaseAdapter() {
        private var orden: List<Int> = emptyList()

        init {
            reordenar()
        }

        fun reordenar() {
            val base = episodios.indices.toList()
            orden = if (descendente) base.reversed() else base
            notifyDataSetChanged()
        }

        fun indiceReal(pos: Int): Int = orden.getOrElse(pos) { -1 }

        override fun getCount(): Int = orden.size

        override fun getItem(position: Int): Any = orden[position]

        override fun getItemId(position: Int): Long = position.toLong()

        private fun crearFila(): Fila {
            val envoltorio = FrameLayout(this@DetalleActivity)
            envoltorio.setPadding(dp(16), dp(6), dp(16), dp(6))

            val fila = LinearLayout(this@DetalleActivity)
            fila.orientation = LinearLayout.HORIZONTAL
            fila.gravity = Gravity.CENTER_VERTICAL
            fila.setPadding(dp(14), dp(10), dp(14), dp(10))
            fila.background = Ui.fondo(this@DetalleActivity, colorFila, 32)

            val icono = TextView(this@DetalleActivity)
            icono.text = "▶"
            icono.setTextColor(Color.parseColor("#B8BCC8"))
            icono.textSize = 15f
            icono.gravity = Gravity.CENTER
            val circ = GradientDrawable()
            circ.shape = GradientDrawable.OVAL
            circ.setColor(Color.TRANSPARENT)
            circ.setStroke(dp(2), Color.parseColor("#B8BCC8"))
            icono.background = circ
            fila.addView(icono, LinearLayout.LayoutParams(dp(44), dp(44)))

            val texto = TextView(this@DetalleActivity)
            texto.setTextColor(Color.WHITE)
            texto.textSize = 18f
            texto.setTypeface(null, Typeface.BOLD)
            texto.maxLines = 2
            texto.ellipsize = TextUtils.TruncateAt.END
            texto.setPadding(dp(16), 0, 0, 0)
            fila.addView(
                texto,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            envoltorio.addView(
                fila,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            val f = Fila(envoltorio, icono, texto)
            envoltorio.tag = f
            return f
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val f = if (convertView == null) crearFila() else convertView.tag as Fila
            val real = orden[position]
            f.texto.text = episodios[real].titulo
            f.texto.setTextColor(if (real == indiceContinuar) colorResalte else Color.WHITE)
            return f.vista
        }
    }
}
