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
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class DetalleActivity : Activity() {

    private class Fila(
        val fondo: LinearLayout,
        val vista: View,
        val icono: TextView,
        val texto: TextView,
        val sub: TextView
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var lista: ListView
    private lateinit var tvEps: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvDesc: TextView
    private lateinit var botonOrden: TextView
    private lateinit var botonVer: TextView
    private lateinit var botonLista: TextView
    private lateinit var adaptador: AdaptadorEpisodios

    private var titulo = ""
    private var portada = ""
    private var descripcionActual = ""
    private var metaActual = ""
    private var episodiosJson = "[]"
    private var idItem = ""
    private var fuenteItem = ""
    private var busquedaServidores = 0
    private var episodios: List<Episodio> = emptyList()
    private var indiceAccion = 0
    private var descendente = true

    private val colorFila = Color.parseColor("#2B2840")
    private val colorFilaVisto = Color.parseColor("#1D1A2B")
    private val colorResalte = Color.parseColor("#A5B4FC")
    private val colorGris = Color.parseColor("#8A869C")

    private fun dp(v: Int) = Ui.dp(this, v)

    // "Episodio 8 - Ver Nombre largo 8" -> "Episodio 8"
    private fun nombreCorto(t: String): String =
        t.replace(Regex("(?i)\\s*[-–]\\s*ver\\s.*$"), "").trim().ifEmpty { t }

    private fun tiempo(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val seg = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%02d:%02d".format(m, seg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mistream", MODE_PRIVATE)

        titulo = intent.getStringExtra("item") ?: ""
        portada = intent.getStringExtra("portada") ?: ""
        descripcionActual = intent.getStringExtra("descripcion") ?: ""
        metaActual = intent.getStringExtra("meta") ?: ""
        episodiosJson = intent.getStringExtra("episodios") ?: "[]"
        idItem = intent.getStringExtra("id") ?: ""
        fuenteItem = intent.getStringExtra("fuente") ?: ""
        episodios = ModuloParser.episodiosDeJson(episodiosJson)
        descendente = prefs.getBoolean("eps_desc", true)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Ui.FONDO)

        // Barra de arriba: solo el botón de volver
        val barraSup = LinearLayout(this)
        barraSup.orientation = LinearLayout.HORIZONTAL
        barraSup.setPadding(dp(16), dp(10), dp(16), dp(2))
        val atras = TextView(this)
        atras.text = "←"
        atras.setTextColor(Color.WHITE)
        atras.textSize = 22f
        atras.gravity = Gravity.CENTER
        atras.background = Ui.fondo(this, colorFila, 22, Ui.BORDE)
        atras.setOnClickListener { finish() }
        barraSup.addView(atras, LinearLayout.LayoutParams(dp(44), dp(44)))
        raiz.addView(
            barraSup,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        lista = ListView(this)
        lista.divider = null
        lista.dividerHeight = 0
        lista.setBackgroundColor(Ui.FONDO)
        lista.selector = ColorDrawable(Color.TRANSPARENT)
        lista.addHeaderView(crearCabecera(), null, false)
        adaptador = AdaptadorEpisodios()
        lista.adapter = adaptador
        lista.setOnItemClickListener { _, _, pos, _ ->
            val real = adaptador.indiceReal(pos - lista.headerViewsCount)
            if (real >= 0) reproducir(real)
        }
        raiz.addView(
            lista,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(raiz)
        ponerTextos()
        actualizarFav()
        calcularAccion()
        if (episodios.isEmpty()) cargarEpisodiosRemotos() else prefetch()
    }

    private fun crearCabecera(): View {
        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.VERTICAL
        cab.setPadding(dp(16), dp(8), dp(16), 0)

        // Portada completa (sin recortar ni desenfocar) + título y descripción
        val fila = LinearLayout(this)
        fila.orientation = LinearLayout.HORIZONTAL

        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.background = Ui.fondo(this, Ui.PILDORA, 16)
        Ui.redondear(img, dp(16).toFloat())
        fila.addView(img, LinearLayout.LayoutParams(dp(120), dp(180)))
        ImageLoader.cargar(portada, img)

        val textos = LinearLayout(this)
        textos.orientation = LinearLayout.VERTICAL
        textos.setPadding(dp(16), dp(4), 0, 0)

        val tvTitulo = TextView(this)
        tvTitulo.text = titulo
        tvTitulo.setTextColor(Color.WHITE)
        tvTitulo.textSize = 22f
        tvTitulo.setTypeface(null, Typeface.BOLD)
        tvTitulo.maxLines = 5
        tvTitulo.ellipsize = TextUtils.TruncateAt.END
        textos.addView(tvTitulo)

        tvMeta = TextView(this)
        tvMeta.setTextColor(Color.parseColor("#C7C9D9"))
        tvMeta.textSize = 13f
        tvMeta.setTypeface(null, Typeface.BOLD)
        tvMeta.setPadding(0, dp(6), 0, 0)
        textos.addView(tvMeta)

        tvDesc = TextView(this)
        tvDesc.setTextColor(Color.parseColor("#A9A5BD"))
        tvDesc.textSize = 15f
        tvDesc.maxLines = 4
        tvDesc.ellipsize = TextUtils.TruncateAt.END
        tvDesc.setPadding(0, dp(12), 0, 0)
        tvDesc.setOnClickListener {
            if (tvDesc.maxLines == 4) {
                tvDesc.maxLines = Int.MAX_VALUE
                tvDesc.ellipsize = null
            } else {
                tvDesc.maxLines = 4
                tvDesc.ellipsize = TextUtils.TruncateAt.END
            }
        }
        textos.addView(tvDesc)

        fila.addView(
            textos,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        cab.addView(fila)

        // Botones: Ver desde el principio / Reanudar + Favoritos
        val botones = LinearLayout(this)
        botones.orientation = LinearLayout.HORIZONTAL
        botones.gravity = Gravity.CENTER_VERTICAL
        botones.setPadding(0, dp(22), 0, 0)

        botonVer = TextView(this)
        botonVer.text = "▶ Ver desde el principio"
        botonVer.setTextColor(Color.WHITE)
        botonVer.textSize = 16f
        botonVer.setTypeface(null, Typeface.BOLD)
        botonVer.gravity = Gravity.CENTER
        botonVer.maxLines = 1
        botonVer.ellipsize = TextUtils.TruncateAt.END
        botonVer.setPadding(dp(12), 0, dp(12), 0)
        botonVer.background = Ui.fondo(this, Ui.ROSA, 27)
        botonVer.setOnClickListener { reproducir(indiceAccion) }
        botones.addView(botonVer, LinearLayout.LayoutParams(0, dp(54), 1f))

        botonLista = TextView(this)
        botonLista.text = "☆ Favoritos"
        botonLista.setTextColor(Color.WHITE)
        botonLista.textSize = 16f
        botonLista.setTypeface(null, Typeface.BOLD)
        botonLista.gravity = Gravity.CENTER
        botonLista.setPadding(dp(20), 0, dp(20), 0)
        botonLista.background = Ui.fondo(this, Ui.FONDO, 27, Ui.BORDE)
        botonLista.setOnClickListener {
            val ahora = Favoritos.alternar(prefs, titulo)
            actualizarFav()
            if (ahora) mostrarDialogoCarpetasFavorito()
            Toast.makeText(this, if (ahora) "Agregado a Favoritos" else "Quitado de Favoritos", Toast.LENGTH_SHORT).show()
        }
        val lpLista = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(54)
        )
        lpLista.leftMargin = dp(10)
        botones.addView(botonLista, lpLista)
        cab.addView(botones)

        // Título de la sección + botón para invertir el orden
        val encabezado = LinearLayout(this)
        encabezado.orientation = LinearLayout.HORIZONTAL
        encabezado.gravity = Gravity.CENTER_VERTICAL
        encabezado.setPadding(0, dp(24), 0, dp(8))

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
        cab.addView(encabezado)

        // Secuelas, precuelas y otros títulos relacionados que declare el módulo.
        val itemActual = Repositorio.buscarItem(titulo, portada)
        val relacionados = itemActual?.relacionados.orEmpty()
        if (relacionados.isNotEmpty()) {
            val bloque = LinearLayout(this)
            bloque.orientation = LinearLayout.VERTICAL
            bloque.setPadding(0, dp(22), 0, dp(8))
            val encabezadoRelacion = TextView(this)
            encabezadoRelacion.text = "Relacionados"
            encabezadoRelacion.setTextColor(Ui.ROSA)
            encabezadoRelacion.textSize = 22f
            encabezadoRelacion.setTypeface(null, Typeface.BOLD)
            bloque.addView(encabezadoRelacion)
            relacionados.forEach { rel ->
                val filaRel = LinearLayout(this)
                filaRel.orientation = LinearLayout.VERTICAL
                filaRel.setPadding(dp(14), dp(12), dp(14), dp(12))
                filaRel.background = Ui.fondo(this, colorFila, 14)
                val nombreRel = TextView(this)
                nombreRel.text = rel.titulo
                nombreRel.setTextColor(Color.WHITE)
                nombreRel.textSize = 17f
                nombreRel.setTypeface(null, Typeface.BOLD)
                filaRel.addView(nombreRel)
                if (rel.relacion.isNotEmpty()) {
                    val tipoRel = TextView(this)
                    tipoRel.text = rel.relacion
                    tipoRel.setTextColor(colorGris)
                    tipoRel.textSize = 14f
                    filaRel.addView(tipoRel)
                }
                filaRel.setOnClickListener {
                    val destino = Repositorio.buscarItem(rel.titulo, rel.portada)
                        ?: Repositorio.buscarItem(rel.titulo)
                    val it = Intent(this, DetalleActivity::class.java)
                    it.putExtra("item", destino?.titulo ?: rel.titulo)
                    it.putExtra("portada", destino?.portada ?: rel.portada)
                    it.putExtra("descripcion", destino?.descripcion ?: "")
                    it.putExtra("episodios", ModuloParser.episodiosAJson(destino?.episodios ?: emptyList()))
                    it.putExtra("id", destino?.id ?: rel.id)
                    it.putExtra("fuente", destino?.fuente ?: rel.fuente.ifEmpty { itemActual?.fuente ?: fuenteItem })
                    startActivity(it)
                }
                val lpRel = LinearLayout.LayoutParams(-1, -2)
                lpRel.topMargin = dp(8)
                bloque.addView(filaRel, lpRel)
            }
            cab.addView(bloque)
        }

        return cab
    }

    private fun ponerTextos() {
        tvMeta.text = metaActual
        tvMeta.visibility = if (metaActual.isEmpty()) View.GONE else View.VISIBLE
        tvDesc.text = if (descripcionActual.isEmpty()) "Sin descripción" else descripcionActual
    }

    private fun mostrarDialogoCarpetasFavorito() {
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val filaCrear = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val entrada = EditText(this).apply { hint = "Nombre de grupo"; singleLine = true }
        filaCrear.addView(entrada, LinearLayout.LayoutParams(0, dp(52), 1f))
        val agregar = TextView(this).apply {
            text = "Agregar"; textSize = 16f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(170, 195, 255)); gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(8), 0)
        }
        filaCrear.addView(agregar)
        contenedor.addView(filaCrear)
        val radios = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val carpetas = Favoritos.nombresCarpetas(prefs)
        carpetas.forEach { nombre ->
            val radio = RadioButton(this).apply { text = nombre; textSize = 16f; setTextColor(Color.WHITE); tag = nombre }
            radios.addView(radio)
        }
        val scroll = ScrollView(this).apply { addView(radios) }
        contenedor.addView(scroll, LinearLayout.LayoutParams(-1, dp(310)))
        val dialogo = AlertDialog.Builder(this).setTitle("Agregar a favoritos")
            .setView(contenedor).setNegativeButton("Cerrar", null).create()
        agregar.setOnClickListener {
            val nombre = entrada.text.toString().trim()
            if (Favoritos.crearCarpeta(prefs, nombre)) {
                Favoritos.agregarACarpeta(prefs, nombre, titulo)
                Toast.makeText(this, "Carpeta creada y anime agregado", Toast.LENGTH_SHORT).show()
                dialogo.dismiss()
            } else Toast.makeText(this, "Escribe un nombre nuevo", Toast.LENGTH_SHORT).show()
        }
        radios.setOnCheckedChangeListener { group, checkedId ->
            val radio = group.findViewById<RadioButton>(checkedId)
            if (radio != null) {
                Favoritos.agregarACarpeta(prefs, radio.tag.toString(), titulo)
                Toast.makeText(this, "Agregado a ${radio.tag}", Toast.LENGTH_SHORT).show()
                dialogo.dismiss()
            }
        }
        dialogo.show()
    }

    private fun actualizarFav() {
        if (!::botonLista.isInitialized) return
        val fav = Favoritos.esFavorito(prefs, titulo)
        botonLista.text = if (fav) "★ En favoritos" else "☆ Favoritos"
        botonLista.setTextColor(if (fav) Ui.ROSA else Color.WHITE)
    }

    // Decide qué hace el botón grande: ver desde el principio, reanudar o continuar con el siguiente
    private fun calcularAccion() {
        indiceAccion = 0
        var texto = "▶ Ver desde el principio"
        if (episodios.isNotEmpty()) {
            val ult = Progreso.ultimo(prefs, titulo)
            var idx = if (ult != null) episodios.indexOfFirst { it.titulo == ult } else -1
            if (idx < 0) {
                // Compatibilidad: el último episodio que tenga una posición guardada
                for (i in episodios.indices) {
                    if (Progreso.posicion(prefs, titulo, episodios[i].titulo) > 0) idx = i
                }
            }
            if (idx >= 0) {
                val ep = episodios[idx]
                val pos = Progreso.posicion(prefs, titulo, ep.titulo)
                val visto = Progreso.esVisto(prefs, titulo, ep.titulo)
                if (pos > 0) {
                    indiceAccion = idx
                    texto = "▶ Reanudar " + nombreCorto(ep.titulo)
                } else if (visto && idx + 1 < episodios.size) {
                    indiceAccion = idx + 1
                    texto = "▶ Continuar " + nombreCorto(episodios[idx + 1].titulo)
                } else if (!visto) {
                    indiceAccion = idx
                    // Lo abriste y no lo terminaste: se vuelve a ese mismo episodio
                    texto = "▶ Reanudar " + nombreCorto(ep.titulo)
                }
            }
        }
        if (::botonVer.isInitialized) botonVer.text = texto
        if (::adaptador.isInitialized) adaptador.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        calcularAccion()
        actualizarFav()
    }

    // Módulos que traen solo el enlace del título: los episodios se piden al abrir
    private fun cargarEpisodiosRemotos() {
        // El enlace y el módulo llegan en el intent (también cuando el anime viene de un filtro o de la
        // búsqueda). Si faltan, se busca el título en la lista cargada.
        var fuente = fuenteItem
        var id = idItem
        if (fuente.isEmpty() || id.isEmpty()) {
            val item = Repositorio.buscarItem(titulo, portada)
            fuente = item?.fuente ?: ""
            id = item?.id ?: ""
        }
        if (fuente.isEmpty() || id.isEmpty()) {
            tvEps.text = "Sin episodios"
            return
        }
        tvEps.text = "Cargando episodios..."
        Thread {
            try {
                val eps = Motores.episodios(fuente, id)
                val extra = MotorHub.metaDe(fuente, id)
                runOnUiThread {
                    episodios = eps
                    episodiosJson = ModuloParser.episodiosAJson(eps)
                    tvEps.text = if (eps.isEmpty()) "Sin episodios" else "Episodios"
                    if (extra != null) {
                        if (extra.first.isNotEmpty()) descripcionActual = extra.first
                        if (extra.second.isNotEmpty()) {
                            metaActual = listOf(metaActual, extra.second)
                                .filter { it.isNotEmpty() }.joinToString(" · ")
                        }
                        ponerTextos()
                    }
                    adaptador.reordenar()
                    calcularAccion()
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
        val s = episodios.getOrNull(indiceAccion)?.servidores ?: return
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
            // Módulos AnimeHub: primero se buscan los servidores que funcionan y se elige uno
            s.size == 1 && s[0].tipo == "hub" -> elegirServidorHub(indice, s[0])
            s.size > 1 -> elegirServidorLista(indice, s)
            else -> abrirReproductor(indice)
        }
    }

    // Ventanita "Buscando servidores..." mientras se consultan y se comprueban
    private fun ventanaBuscando(alCancelar: () -> Unit): AlertDialog {
        val caja = LinearLayout(this)
        caja.orientation = LinearLayout.HORIZONTAL
        caja.gravity = Gravity.CENTER_VERTICAL
        caja.setPadding(dp(24), dp(20), dp(24), dp(20))
        caja.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(36), dp(36)))
        val t = TextView(this)
        t.text = "Buscando servidores..."
        t.setTextColor(Color.WHITE)
        t.textSize = 16f
        t.setPadding(dp(16), 0, 0, 0)
        caja.addView(t)
        val d = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setView(caja)
            .setOnCancelListener { alCancelar() }
            .create()
        d.show()
        return d
    }

    private fun elegirServidorHub(indice: Int, marcador: ServerLink) {
        val enCache = MotorHub.enCache(marcador)
        if (enCache != null) {
            mostrarSelector(indice, enCache)
            return
        }
        val mi = ++busquedaServidores
        val espera = ventanaBuscando { busquedaServidores++ }
        Thread {
            try {
                val lista = MotorHub.resolverVideos(marcador, prefs)
                runOnUiThread {
                    espera.dismiss()
                    if (isFinishing || mi != busquedaServidores) return@runOnUiThread
                    if (lista.isEmpty()) {
                        Toast.makeText(this, "No hay servidores funcionando para este episodio", Toast.LENGTH_LONG).show()
                    } else {
                        mostrarSelector(indice, lista)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    espera.dismiss()
                    if (isFinishing || mi != busquedaServidores) return@runOnUiThread
                    Toast.makeText(
                        this,
                        "No se pudieron cargar los servidores: " + (e.message ?: "error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // Módulos que ya traen varios servidores en el episodio: se comprueban y se elige
    private fun elegirServidorLista(indice: Int, servidores: List<ServerLink>) {
        val mi = ++busquedaServidores
        val espera = ventanaBuscando { busquedaServidores++ }
        Thread {
            val buenos = Servidores.filtrarFuncionales(servidores)
            runOnUiThread {
                espera.dismiss()
                if (isFinishing || mi != busquedaServidores) return@runOnUiThread
                if (buenos.isEmpty()) {
                    Toast.makeText(this, "No hay servidores funcionando para este episodio", Toast.LENGTH_LONG).show()
                } else {
                    mostrarSelector(indice, buenos)
                }
            }
        }.start()
    }

    // Elegir servidor: nombre a la izquierda y, al costado, la bandera (México = español, Japón = subtitulado)
    private fun mostrarSelector(indice: Int, servidores: List<ServerLink>) {
        Servidores.mostrarSelector(
            this, "Elegir servidor", servidores, prefs.getString("srv_pref", null),
            { which ->
                prefs.edit().putString("srv_pref", Servidores.claveVisible(servidores, which)).apply()
                abrirReproductor(indice)
            },
            {}
        )
    }

    private fun tituloEpisodio(indice: Int): String =
        titulo + " - " + (episodios.getOrNull(indice)?.titulo ?: "")

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

            val icono = TextView(this@DetalleActivity)
            icono.textSize = 15f
            icono.gravity = Gravity.CENTER
            fila.addView(icono, LinearLayout.LayoutParams(dp(44), dp(44)))

            val columna = LinearLayout(this@DetalleActivity)
            columna.orientation = LinearLayout.VERTICAL
            columna.setPadding(dp(16), 0, 0, 0)

            val texto = TextView(this@DetalleActivity)
            texto.textSize = 18f
            texto.setTypeface(null, Typeface.BOLD)
            texto.maxLines = 1
            texto.ellipsize = TextUtils.TruncateAt.END
            columna.addView(texto)

            val sub = TextView(this@DetalleActivity)
            sub.textSize = 13f
            sub.maxLines = 1
            columna.addView(sub)

            fila.addView(
                columna,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            envoltorio.addView(
                fila,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            val f = Fila(fila, envoltorio, icono, texto, sub)
            envoltorio.tag = f
            return f
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val f = if (convertView == null) crearFila() else convertView.tag as Fila
            val real = orden[position]
            val ep = episodios[real]
            val visto = Progreso.esVisto(prefs, titulo, ep.titulo)
            val pos = Progreso.posicion(prefs, titulo, ep.titulo)

            f.texto.text = nombreCorto(ep.titulo)

            val circ = GradientDrawable()
            circ.shape = GradientDrawable.OVAL

            when {
                // Visto: círculo rosa con ✓, fila apagada
                visto -> {
                    f.fondo.background = Ui.fondo(this@DetalleActivity, colorFilaVisto, 32)
                    circ.setColor(Ui.ROSA)
                    f.icono.text = "✓"
                    f.icono.setTextColor(Color.WHITE)
                    f.texto.setTextColor(colorGris)
                    f.sub.text = "Visto"
                    f.sub.setTextColor(Ui.ROSA)
                    f.sub.visibility = View.VISIBLE
                }
                // A medias: muestra en qué minuto te quedaste
                pos > 0 -> {
                    f.fondo.background = Ui.fondo(this@DetalleActivity, colorFila, 32)
                    circ.setColor(Color.TRANSPARENT)
                    circ.setStroke(dp(2), Ui.ROSA)
                    f.icono.text = "▶"
                    f.icono.setTextColor(Ui.ROSA)
                    f.texto.setTextColor(Color.WHITE)
                    f.sub.text = "Continuar en " + tiempo(pos)
                    f.sub.setTextColor(Ui.ROSA)
                    f.sub.visibility = View.VISIBLE
                }
                else -> {
                    f.fondo.background = Ui.fondo(this@DetalleActivity, colorFila, 32)
                    circ.setColor(Color.TRANSPARENT)
                    circ.setStroke(dp(2), Color.parseColor("#B8BCC8"))
                    f.icono.text = "▶"
                    f.icono.setTextColor(Color.parseColor("#B8BCC8"))
                    f.texto.setTextColor(if (real == indiceAccion) colorResalte else Color.WHITE)
                    f.sub.visibility = View.GONE
                }
            }
            f.icono.background = circ
            return f.vista
        }
    }
}
