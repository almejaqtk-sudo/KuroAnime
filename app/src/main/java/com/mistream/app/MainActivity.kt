package com.mistream.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var cabecera: LinearLayout
    private lateinit var filtros: LinearLayout
    private lateinit var buscador: EditText
    private lateinit var titulo: TextView
    private lateinit var grilla: GridView
    private lateinit var pModulo: TextView
    private lateinit var pGenero: TextView
    private lateinit var pTipo: TextView
    private lateinit var pEstado: TextView
    private val pestanas = mutableListOf<TextView>()

    private var pestana = 0
    private var todos: List<Item> = emptyList()
    private var nombresModulos: List<String> = emptyList()
    private var moduloSel: String? = null
    private val generosSel = mutableSetOf<String>()
    private val tiposSel = mutableSetOf<String>()
    private val estadosSel = mutableSetOf<String>()
    private var visibles: List<Item> = emptyList()
    private var versionCargada = -1

    // Filtro de género que hace el propio sitio (trae todas las páginas, no solo lo ya cargado)
    private var modulosCargados: List<Modulo> = emptyList()
    private var generoSel: String? = null
    private var itemsGenero: List<Item> = emptyList()
    private var paginaGenero = 1
    private var cargandoGenero = false
    private var finGenero = false
    private var tokenGenero = 0

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun pesoLp(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        p.setMargins(dp(3), 0, dp(3), 0)
        return p
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mistream", MODE_PRIVATE)
        Repositorio.asegurarIntegrado(prefs)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Ui.FONDO)

        val contenido = LinearLayout(this)
        contenido.orientation = LinearLayout.VERTICAL
        contenido.setPadding(dp(14), dp(40), dp(14), 0)

        // Cabecera: logo + selector de módulo
        cabecera = LinearLayout(this)
        cabecera.orientation = LinearLayout.HORIZONTAL
        cabecera.gravity = Gravity.CENTER_VERTICAL

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
        cabecera.addView(
            logo,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        pModulo = Ui.pildora(this, "Todos ▾")
        cabecera.addView(pModulo)
        contenido.addView(cabecera)

        // Buscador (solo en la pestaña Buscar)
        buscador = EditText(this)
        buscador.hint = "Buscar título..."
        buscador.setHintTextColor(Ui.GRIS)
        buscador.setTextColor(Color.WHITE)
        buscador.setSingleLine()
        buscador.background = Ui.fondo(this, Ui.PILDORA, 22, Ui.BORDE)
        buscador.setPadding(dp(16), dp(10), dp(16), dp(10))
        buscador.visibility = View.GONE
        val lpBuscador = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpBuscador.topMargin = dp(4)
        contenido.addView(buscador, lpBuscador)

        // Filtros
        filtros = LinearLayout(this)
        filtros.orientation = LinearLayout.HORIZONTAL
        pGenero = Ui.pildora(this, "Género (0) ▾", 13f)
        pTipo = Ui.pildora(this, "Tipo (0) ▾", 13f)
        pEstado = Ui.pildora(this, "Estado (0) ▾", 13f)
        filtros.addView(pGenero, pesoLp())
        filtros.addView(pTipo, pesoLp())
        filtros.addView(pEstado, pesoLp())
        val lpFiltros = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpFiltros.topMargin = dp(14)
        contenido.addView(filtros, lpFiltros)

        // Título de sección
        titulo = TextView(this)
        titulo.setTextColor(Color.WHITE)
        titulo.textSize = 20f
        titulo.setTypeface(null, Typeface.BOLD)
        titulo.setPadding(dp(2), dp(18), 0, dp(10))
        contenido.addView(titulo)

        // Cuadrícula
        grilla = GridView(this)
        grilla.numColumns = 3
        grilla.horizontalSpacing = dp(10)
        grilla.verticalSpacing = dp(14)
        grilla.selector = ColorDrawable(Color.TRANSPARENT)
        grilla.setPadding(0, 0, 0, dp(8))
        contenido.addView(
            grilla,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        // Barra inferior
        val barra = LinearLayout(this)
        barra.orientation = LinearLayout.HORIZONTAL
        barra.setBackgroundColor(Ui.BARRA)
        barra.setPadding(0, dp(8), 0, dp(10))
        val nombres = listOf("⌂\nInicio", "🔍\nBuscar", "★\nMi lista", "⚙\nMódulos")
        for ((i, n) in nombres.withIndex()) {
            val t = TextView(this)
            t.text = n
            t.gravity = Gravity.CENTER
            t.textSize = 12f
            t.setTypeface(null, Typeface.BOLD)
            t.setOnClickListener { seleccionar(i) }
            pestanas.add(t)
            barra.addView(
                t,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        raiz.addView(
            contenido,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        raiz.addView(
            barra,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(raiz)

        // Acciones
        pModulo.setOnClickListener { dialogoModulo() }
        pGenero.setOnClickListener { abrirGeneros() }
        pTipo.setOnClickListener {
            dialogoFiltro("Tipo", todos.map { it.tipo }.filter { it.isNotEmpty() }.distinct().sorted(), tiposSel, pTipo)
        }
        pEstado.setOnClickListener {
            dialogoFiltro("Estado", todos.map { it.estado }.filter { it.isNotEmpty() }.distinct().sorted(), estadosSel, pEstado)
        }

        buscador.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                actualizar()
            }
        })

        grilla.setOnItemClickListener { _, _, pos, _ ->
            if (pos in visibles.indices) abrirDetalle(visibles[pos])
        }

        // Al llegar al final de un género se piden más títulos
        grilla.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {
                if (pestana == 0 && generoSel != null && !cargandoGenero && !finGenero &&
                    total > 0 && first + visible >= total - 3
                ) cargarGenero(false)
            }
        })

        actualizar()
    }

    override fun onResume() {
        super.onResume()
        val urls = Repositorio.leerUrls(prefs)
        val ver = prefs.getInt("modulos_ver", 0)
        if (ver != versionCargada) {
            versionCargada = ver
            recargar(urls)
        } else if (pestana == 2) {
            actualizar()
        }
    }

    private fun seleccionar(i: Int) {
        if (i == 3) {
            startActivity(Intent(this, ModulosActivity::class.java))
            return
        }
        pestana = i
        actualizar()
    }

    private fun recargar(urls: List<String>) {
        if (urls.isEmpty()) {
            todos = emptyList()
            nombresModulos = emptyList()
            actualizar()
            return
        }
        titulo.text = "Cargando módulos..."
        Thread {
            // 1) El catálogo de la última vez se muestra al instante
            val guardado = Repositorio.leerCache(urls)
            if (guardado != null) runOnUiThread { aplicarResultado(guardado) }
            // 2) Se actualiza con lo más nuevo
            val r = Repositorio.cargarTodo(urls)
            if (r.modulos.isNotEmpty()) Repositorio.guardarCache(urls, r)
            runOnUiThread {
                if (r.modulos.isNotEmpty() || guardado == null) aplicarResultado(r)
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

    private fun aplicarResultado(r: Repositorio.Resultado) {
        modulosCargados = r.modulos
        todos = r.modulos.flatMap { it.items }
        nombresModulos = r.modulos.map { it.nombre }.distinct()
        val sel = moduloSel
        if (sel != null && !nombresModulos.contains(sel)) moduloSel = null
        pModulo.text = (moduloSel ?: "Todos") + " ▾"
        actualizar()
    }

    private fun pasaFiltros(item: Item): Boolean {
        val sel = moduloSel
        if (sel != null && item.modulo != sel) return false
        if (generoSel == null && generosSel.isNotEmpty() &&
            item.generos.none { generosSel.contains(it) }
        ) return false
        if (tiposSel.isNotEmpty() && !tiposSel.contains(item.tipo)) return false
        if (estadosSel.isNotEmpty() && !estadosSel.contains(item.estado)) return false
        return true
    }

    private fun actualizar() {
        val q = buscador.text.toString().trim().lowercase()
        val favs = Favoritos.leer(prefs)

        when (pestana) {
            0 -> {
                val g = generoSel
                visibles = (if (g != null) itemsGenero else todos).filter { pasaFiltros(it) }
                titulo.text = if (g != null) "Género: $g" else "Últimos episodios"
                if (g != null && visibles.isEmpty() && !cargandoGenero) {
                    titulo.text = "Sin resultados en $g"
                }
            }
            1 -> {
                visibles = if (q.isEmpty()) todos else todos.filter { it.titulo.lowercase().contains(q) }
                titulo.text = if (q.isEmpty()) "Todo el catálogo" else "Resultados"
            }
            else -> {
                visibles = todos.filter { favs.contains(it.titulo) }
                titulo.text = "Mi lista"
            }
        }
        if (todos.isEmpty()) {
            titulo.text = "Sin contenido. Toca Módulos para agregar uno."
        } else if (pestana == 2 && visibles.isEmpty()) {
            titulo.text = "Mi lista vacía. Abre un título y toca ☆ Mi lista."
        }

        cabecera.visibility = if (pestana == 0) View.VISIBLE else View.GONE
        filtros.visibility = if (pestana == 0) View.VISIBLE else View.GONE
        buscador.visibility = if (pestana == 1) View.VISIBLE else View.GONE

        grilla.adapter = AdaptadorGrilla(visibles)

        for ((i, t) in pestanas.withIndex()) {
            t.setTextColor(if (i == pestana) Ui.ROSA else Ui.GRIS)
        }
    }

    private fun dialogoModulo() {
        val opciones = listOf("Todos") + nombresModulos
        AlertDialog.Builder(this)
            .setTitle("Módulo")
            .setItems(opciones.toTypedArray()) { _, i ->
                moduloSel = if (i == 0) null else opciones[i]
                pModulo.text = (moduloSel ?: "Todos") + " ▾"
                // Cada módulo tiene sus propios géneros: se quita el filtro de género anterior
                elegirGenero(null)
            }
            .show()
    }

    // Géneros disponibles (nombre -> módulos que lo tienen y su id en el sitio)
    private fun destinosGeneros(): LinkedHashMap<String, MutableList<Pair<Modulo, String>>> {
        val mapa = LinkedHashMap<String, MutableList<Pair<Modulo, String>>>()
        for (m in modulosCargados) {
            if (moduloSel != null && m.nombre != moduloSel) continue
            if (m.fuente.isEmpty()) continue
            for ((nombre, id) in m.generos) {
                mapa.getOrPut(nombre) { mutableListOf() }.add(Pair(m, id))
            }
        }
        return mapa
    }

    private fun abrirGeneros() {
        val mapa = destinosGeneros()
        if (mapa.isEmpty()) {
            // Módulos sin lista de géneros: se usan los géneros de los títulos ya cargados
            dialogoFiltro(
                "Género",
                todos.flatMap { it.generos }.distinct().sorted(),
                generosSel,
                pGenero
            )
            return
        }
        val nombres = mapa.keys.toList()
        AlertDialog.Builder(this)
            .setTitle("Género")
            .setSingleChoiceItems(nombres.toTypedArray(), (generoSel?.let { nombres.indexOf(it) } ?: -1)) { d, which ->
                d.dismiss()
                elegirGenero(nombres[which])
            }
            .setNeutralButton("Limpiar") { _, _ -> elegirGenero(null) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun elegirGenero(nombre: String?) {
        generoSel = nombre
        tokenGenero++
        itemsGenero = emptyList()
        paginaGenero = 1
        finGenero = false
        cargandoGenero = false
        generosSel.clear()
        pGenero.text = if (nombre == null) "Género (0) ▾" else "Género (1) ▾"
        if (nombre == null) {
            actualizar()
        } else {
            cargarGenero(true)
        }
    }

    private fun cargarGenero(primera: Boolean) {
        val nombre = generoSel ?: return
        if (cargandoGenero) return
        val destinos = destinosGeneros()[nombre] ?: return
        cargandoGenero = true
        val token = tokenGenero
        val pagina = paginaGenero
        if (primera) {
            actualizar()
            titulo.text = "Cargando $nombre..."
        }
        Thread {
            val nuevos = mutableListOf<Item>()
            var error: String? = null
            for ((modulo, id) in destinos) {
                try {
                    nuevos.addAll(MotorHub.catalogo(modulo.fuente, id, pagina))
                } catch (e: Exception) {
                    error = e.message ?: "error"
                }
            }
            val mensaje = error
            runOnUiThread {
                if (token != tokenGenero) return@runOnUiThread
                cargandoGenero = false
                val existentes = itemsGenero.map { it.id + "|" + it.titulo }.toSet()
                val unicos = nuevos.filter { (it.id + "|" + it.titulo) !in existentes }
                if (unicos.isEmpty()) {
                    finGenero = true
                } else {
                    itemsGenero = itemsGenero + unicos
                    paginaGenero = pagina + 1
                    Repositorio.agregarARegistro(unicos)
                }
                if (mensaje != null && itemsGenero.isEmpty()) {
                    Toast.makeText(this, "No cargó el género: $mensaje", Toast.LENGTH_LONG).show()
                }
                val posicion = if (primera) 0 else grilla.firstVisiblePosition
                actualizar()
                if (!primera) grilla.setSelection(posicion)
            }
        }.start()
    }

    private fun dialogoFiltro(
        nombre: String,
        opciones: List<String>,
        sel: MutableSet<String>,
        boton: TextView
    ) {
        if (opciones.isEmpty()) {
            Toast.makeText(this, "No hay opciones de $nombre en estos módulos", Toast.LENGTH_SHORT).show()
            return
        }
        val marcados = BooleanArray(opciones.size) { sel.contains(opciones[it]) }
        AlertDialog.Builder(this)
            .setTitle(nombre)
            .setMultiChoiceItems(opciones.toTypedArray(), marcados) { _, i, marcado ->
                if (marcado) sel.add(opciones[i]) else sel.remove(opciones[i])
            }
            .setPositiveButton("Aplicar") { _, _ ->
                boton.text = "$nombre (${sel.size}) ▾"
                actualizar()
            }
            .setNeutralButton("Limpiar") { _, _ ->
                sel.clear()
                boton.text = "$nombre (0) ▾"
                actualizar()
            }
            .show()
    }

    private fun abrirDetalle(item: Item) {
        val meta = listOf(item.tipo, item.estado, item.generos.joinToString(", "))
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        val intent = Intent(this, DetalleActivity::class.java)
        intent.putExtra("item", item.titulo)
        intent.putExtra("portada", item.portada)
        intent.putExtra("descripcion", item.descripcion)
        intent.putExtra("meta", meta)
        intent.putExtra("episodios", ModuloParser.episodiosAJson(item.episodios))
        startActivity(intent)
    }

    private inner class AdaptadorGrilla(private val items: List<Item>) : BaseAdapter() {

        private val ancho = (resources.displayMetrics.widthPixels - dp(14) * 2 - dp(10) * 2) / 3
        private val alto = ancho * 3 / 2

        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]

            val celda = LinearLayout(this@MainActivity)
            celda.orientation = LinearLayout.VERTICAL

            val img = ImageView(this@MainActivity)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.background = Ui.fondo(this@MainActivity, Ui.PILDORA, 14)
            Ui.redondear(img, dp(14).toFloat())
            celda.addView(
                img,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, alto)
            )
            ImageLoader.cargar(item.portada, img)

            val txt = TextView(this@MainActivity)
            txt.text = item.titulo
            txt.setTextColor(Color.WHITE)
            txt.textSize = 14f
            txt.setTypeface(null, Typeface.BOLD)
            txt.setLines(2)
            txt.ellipsize = TextUtils.TruncateAt.END
            txt.setPadding(dp(2), dp(8), 0, 0)
            celda.addView(txt)

            val etiqueta = if (item.ultimo.isNotEmpty()) item.ultimo
            else (item.episodios.lastOrNull()?.titulo ?: "")
            val ep = TextView(this@MainActivity)
            ep.text = etiqueta
            ep.setTextColor(Ui.ROSA)
            ep.textSize = 14f
            ep.setTypeface(null, Typeface.BOLD)
            ep.maxLines = 1
            ep.setPadding(dp(2), dp(2), 0, 0)
            celda.addView(ep)

            return celda
        }
    }
}
