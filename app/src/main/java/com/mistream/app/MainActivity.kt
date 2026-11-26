package com.mistream.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var cabecera: LinearLayout
    private lateinit var filtros: LinearLayout
    private lateinit var scrollFiltros: HorizontalScrollView
    private lateinit var buscador: EditText
    private lateinit var titulo: TextView
    private lateinit var grilla: GridView
    private lateinit var pModulo: TextView
    private val pestanas = mutableListOf<TextView>()

    private var pestana = 0

    // Botón "Cargar más" del inicio: va trayendo los días anteriores (o más páginas si el módulo no tiene días)
    private lateinit var botonMas: TextView
    private var itemsMas: List<Item> = emptyList()
    private var paginaMas = 1
    private var cargandoMas = false
    private var tokenMas = 0
    private var alFinal = false
    private var paginaCatalogo = 0
    private var todos: List<Item> = emptyList()
    private var nombresModulos: List<String> = emptyList()
    private var moduloSel: String? = null
    private var visibles: List<Item> = emptyList()
    private var versionCargada = -1
    private var modulosCargados: List<Modulo> = emptyList()

    // Filtros locales (solo si ningún módulo trae los filtros de su sitio): Género / Tipo / Estado
    private val generosSel = mutableSetOf<String>()
    private val tiposSel = mutableSetOf<String>()
    private val estadosSel = mutableSetOf<String>()

    // Filtros del propio sitio: nombre del filtro ("Género") -> opción elegida ("Acción").
    // El sitio hace el filtrado, así que trae todas las páginas y no solo lo ya cargado.
    private val seleccion = LinkedHashMap<String, String>()
    private var itemsFiltro: List<Item> = emptyList()
    private var paginaFiltro = 1
    private var cargandoFiltro = false
    private var finFiltro = false
    private var tokenFiltro = 0

    // Búsqueda por nombre en el sitio de cada módulo
    private val handler = Handler(Looper.getMainLooper())
    private val lanzarBusqueda = Runnable { iniciarBusqueda() }
    private var consulta = ""
    private var itemsBusqueda: List<Item> = emptyList()
    private var paginaBusqueda = 1
    private var cargandoBusqueda = false
    private var finBusqueda = false
    private var tokenBusqueda = 0

    // Un filtro tal como lo ve la persona, con las opciones de todos los módulos que lo tienen:
    // opción -> lista de (módulo, parámetro del sitio, valor en el sitio)
    private class FiltroUI(val nombre: String) {
        val opciones = LinkedHashMap<String, MutableList<Triple<Modulo, String, String>>>()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun lpPildora(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.setMargins(0, 0, dp(8), 0)
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

        pModulo = Ui.pildora(this, "Módulos ▾")
        cabecera.addView(pModulo)
        contenido.addView(cabecera)

        // Buscador (solo en la pestaña Buscar)
        buscador = EditText(this)
        buscador.hint = "Buscar título..."
        buscador.setHintTextColor(Ui.GRIS)
        buscador.setTextColor(Color.WHITE)
        buscador.setSingleLine()
        buscador.imeOptions = EditorInfo.IME_ACTION_SEARCH
        buscador.background = Ui.fondo(this, Ui.PILDORA, 22, Ui.BORDE)
        buscador.setPadding(dp(16), dp(10), dp(16), dp(10))
        buscador.visibility = View.GONE
        val lpBuscador = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpBuscador.topMargin = dp(4)
        contenido.addView(buscador, lpBuscador)

        // Filtros: uno por cada filtro que tenga el sitio del módulo (Género, Tipo, Estado, Año...)
        filtros = LinearLayout(this)
        filtros.orientation = LinearLayout.HORIZONTAL
        scrollFiltros = HorizontalScrollView(this)
        scrollFiltros.isHorizontalScrollBarEnabled = false
        scrollFiltros.addView(
            filtros,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val lpFiltros = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpFiltros.topMargin = dp(14)
        contenido.addView(scrollFiltros, lpFiltros)

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
        // El último renglón sube por encima del botón para que no lo tape
        grilla.clipToPadding = false
        grilla.setPadding(0, 0, 0, dp(84))

        botonMas = TextView(this)
        botonMas.text = "Cargar más"
        botonMas.setTextColor(Color.WHITE)
        botonMas.textSize = 18f
        botonMas.setTypeface(null, Typeface.BOLD)
        botonMas.gravity = Gravity.CENTER
        botonMas.background = Ui.fondo(this, Ui.PILDORA, 18)
        botonMas.visibility = View.GONE
        botonMas.setOnClickListener { cargarMas() }

        val marcoGrilla = FrameLayout(this)
        marcoGrilla.addView(
            grilla,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val lpMas = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(56), Gravity.BOTTOM
        )
        lpMas.bottomMargin = dp(12)
        marcoGrilla.addView(botonMas, lpMas)
        contenido.addView(
            marcoGrilla,
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

        buscador.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Espera un momento a que termines de escribir antes de buscar en el sitio
                handler.removeCallbacks(lanzarBusqueda)
                handler.postDelayed(lanzarBusqueda, 450)
            }
        })
        buscador.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(lanzarBusqueda)
                iniciarBusqueda()
                true
            } else false
        }

        grilla.setOnItemClickListener { _, _, pos, _ ->
            if (pos in visibles.indices) abrirDetalle(visibles[pos])
        }

        // Al llegar al final se piden más títulos (filtro del sitio o búsqueda)
        grilla.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {
                alFinal = total > 0 && first + visible >= total
                mostrarBotonMas()
                if (total <= 0 || first + visible < total - 3) return
                if (pestana == 0 && seleccion.isNotEmpty() && !cargandoFiltro && !finFiltro) {
                    cargarFiltro(false)
                } else if (pestana == 1 && consulta.isNotEmpty() && !cargandoBusqueda && !finBusqueda) {
                    cargarBusqueda(false)
                }
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
        reiniciarMas()
        todos = r.modulos.flatMap { it.items }
        nombresModulos = r.modulos.map { it.nombre }.distinct()
        // Siempre hay un módulo elegido: el último que usaste o, si no hay, el primero de la lista
        val sel = moduloSel
        if (sel == null || !nombresModulos.contains(sel)) {
            val guardado = prefs.getString("modulo_sel", null)
            moduloSel = if (guardado != null && nombresModulos.contains(guardado)) guardado
            else ordenModulos().firstOrNull()?.second?.firstOrNull() ?: nombresModulos.firstOrNull()
        }
        pModulo.text = (moduloSel ?: "Módulos") + " ▾"
        reconstruirFiltros()
        actualizar()
    }

    private fun moduloPasa(item: Item): Boolean {
        val sel = moduloSel
        return sel == null || item.modulo == sel
    }

    // Filtros locales: solo se usan cuando ningún módulo trae los filtros de su sitio
    private fun pasaFiltrosLocales(item: Item): Boolean {
        if (!moduloPasa(item)) return false
        if (generosSel.isNotEmpty() && item.generos.none { generosSel.contains(it) }) return false
        if (tiposSel.isNotEmpty() && !tiposSel.contains(item.tipo)) return false
        if (estadosSel.isNotEmpty() && !estadosSel.contains(item.estado)) return false
        return true
    }

    // Módulos que saben buscar por nombre en su sitio (formato AnimeHub)
    private fun modulosBuscables(): List<Modulo> = modulosCargados.filter {
        it.fuente.isNotEmpty() && MotorHub.esFormatoHub(it.fuente) &&
            (moduloSel == null || it.nombre == moduloSel)
    }

    private fun actualizar() {
        val favs = Favoritos.leer(prefs)

        when (pestana) {
            0 -> {
                if (seleccion.isNotEmpty()) {
                    visibles = itemsFiltro.filter { moduloPasa(it) }
                    val texto = seleccion.entries.joinToString(" · ") { it.key + ": " + it.value }
                    titulo.text = when {
                        visibles.isEmpty() && cargandoFiltro -> "Cargando $texto..."
                        visibles.isEmpty() -> "Sin resultados en $texto"
                        else -> texto
                    }
                } else {
                    val base = todos.filter { pasaFiltrosLocales(it) }
                    val ya = base.map { it.id + "|" + it.titulo }.toSet()
                    visibles = base + itemsMas.filter { moduloPasa(it) && (it.id + "|" + it.titulo) !in ya }
                    titulo.text = "Últimos episodios"
                }
            }
            1 -> {
                if (consulta.isEmpty()) {
                    visibles = todos.filter { moduloPasa(it) }
                    titulo.text = "Todo el catálogo"
                } else {
                    // Resultados del sitio + coincidencias locales de módulos que no saben buscar
                    val buscables = modulosBuscables().map { it.nombre }.toSet()
                    val q = consulta.lowercase()
                    val locales = todos.filter {
                        moduloPasa(it) && it.modulo !in buscables && it.titulo.lowercase().contains(q)
                    }
                    visibles = itemsBusqueda + locales
                    titulo.text = when {
                        visibles.isEmpty() && cargandoBusqueda -> "Buscando \"$consulta\"..."
                        visibles.isEmpty() -> "Sin resultados para \"$consulta\""
                        else -> "Resultados para \"$consulta\""
                    }
                }
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

        cabecera.visibility = if (pestana <= 1) View.VISIBLE else View.GONE
        scrollFiltros.visibility =
            if (pestana == 0 && filtros.childCount > 0) View.VISIBLE else View.GONE
        buscador.visibility = if (pestana == 1) View.VISIBLE else View.GONE

        grilla.adapter = AdaptadorGrilla(visibles)
        alFinal = visibles.size <= 9
        mostrarBotonMas()
        grilla.post {
            alFinal = visibles.isNotEmpty() && grilla.lastVisiblePosition >= visibles.size - 1
            mostrarBotonMas()
        }

        for ((i, t) in pestanas.withIndex()) {
            t.setTextColor(if (i == pestana) Ui.ROSA else Ui.GRIS)
        }
    }

    // ---------- Selector de módulo (con la imagen de cada página) ----------

    // Módulos agrupados por sección (Anime, Películas, Hentai, Adultos), en ese orden
    private fun ordenModulos(): List<Pair<String, List<String>>> {
        val instalados = Repositorio.leerInstalados(prefs).filter { it.activo }
        val porCategoria = nombresModulos.groupBy { n ->
            instalados.firstOrNull { it.nombre == n }?.categoria ?: "Anime"
        }
        val orden = Repositorio.CATEGORIAS.filter { porCategoria.containsKey(it) } +
            porCategoria.keys.filter { it !in Repositorio.CATEGORIAS }
        return orden.map { Pair(it, porCategoria[it] ?: emptyList()) }
    }

    private fun dialogoModulo() {
        val dialogo = AlertDialog.Builder(this).create()
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val caja = LinearLayout(this)
        caja.orientation = LinearLayout.VERTICAL
        caja.setPadding(dp(14), dp(16), dp(14), dp(8))
        caja.background = Ui.fondo(this, Color.parseColor("#262A36"), 28)

        val puzzle = TextView(this)
        puzzle.text = "🧩"
        puzzle.textSize = 22f
        puzzle.gravity = Gravity.CENTER
        caja.addView(puzzle)

        fun elegir(nombre: String) {
            dialogo.dismiss()
            if (nombre == moduloSel) return
            moduloSel = nombre
            prefs.edit().putString("modulo_sel", nombre).apply()
            pModulo.text = nombre + " ▾"
            // Cada módulo tiene sus propios filtros: se limpia lo que estaba elegido
            seleccion.clear()
            generosSel.clear(); tiposSel.clear(); estadosSel.clear()
            tokenFiltro++
            itemsFiltro = emptyList()
            cargandoFiltro = false
            finFiltro = false
            reiniciarMas()
            reconstruirFiltros()
            if (consulta.isNotEmpty()) iniciarBusqueda() else actualizar()
        }

        val lista = LinearLayout(this)
        lista.orientation = LinearLayout.VERTICAL
        for ((cat, nombres) in ordenModulos()) {
            val cab = TextView(this)
            cab.text = cat.uppercase()
            cab.setTextColor(Color.parseColor("#C7CBE0"))
            cab.textSize = 12f
            cab.setTypeface(null, Typeface.BOLD)
            cab.gravity = Gravity.CENTER
            cab.setPadding(0, dp(12), 0, dp(4))
            lista.addView(cab)
            for (n in nombres) {
                val m = modulosCargados.firstOrNull { it.nombre == n }
                lista.addView(
                    filaModulo(n, m?.icono ?: "", m?.version ?: "", moduloSel == n) { elegir(n) }
                )
            }
        }

        val maxAlto = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(
                    widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(maxAlto, View.MeasureSpec.AT_MOST)
                )
            }
        }
        scroll.addView(lista)
        caja.addView(scroll)

        val cancelar = TextView(this)
        cancelar.text = "Cancelar"
        cancelar.setTextColor(Color.parseColor("#C7D2FE"))
        cancelar.textSize = 15f
        cancelar.setTypeface(null, Typeface.BOLD)
        cancelar.gravity = Gravity.END
        cancelar.setPadding(dp(12), dp(12), dp(14), dp(8))
        cancelar.setOnClickListener { dialogo.dismiss() }
        caja.addView(cancelar)

        dialogo.setView(caja)
        dialogo.show()
        // Ventana más angosta y compacta
        dialogo.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.82f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun filaModulo(
        nombre: String,
        icono: String,
        version: String,
        marcado: Boolean,
        alTocar: () -> Unit
    ): View {
        val fila = LinearLayout(this)
        fila.orientation = LinearLayout.HORIZONTAL
        fila.gravity = Gravity.CENTER_VERTICAL
        fila.setPadding(dp(6), dp(5), dp(6), dp(5))
        fila.setOnClickListener { alTocar() }

        // Imagen de la página; si no carga se ve la inicial del nombre
        val lado = dp(44)
        val marco = FrameLayout(this)
        val inicial = TextView(this)
        inicial.text = nombre.take(1).uppercase()
        inicial.setTextColor(Color.WHITE)
        inicial.textSize = 18f
        inicial.setTypeface(null, Typeface.BOLD)
        inicial.gravity = Gravity.CENTER
        inicial.background = Ui.fondo(this, Color.parseColor("#3B3654"), 14)
        marco.addView(inicial, FrameLayout.LayoutParams(lado, lado))
        if (icono.isNotEmpty()) {
            val img = ImageView(this)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            Ui.redondear(img, dp(14).toFloat())
            marco.addView(img, FrameLayout.LayoutParams(lado, lado))
            ImageLoader.cargar(icono, img)
        }
        fila.addView(marco, LinearLayout.LayoutParams(lado, lado))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), 0, 0, 0)
        val tn = TextView(this)
        tn.text = nombre
        tn.setTextColor(if (marcado) Ui.ROSA else Color.parseColor("#E3E5F0"))
        tn.textSize = 16f
        tn.setTypeface(null, Typeface.BOLD)
        col.addView(tn)
        if (version.isNotEmpty()) {
            val tv = TextView(this)
            tv.text = version
            tv.setTextColor(Color.parseColor("#9A9DB0"))
            tv.textSize = 12f
            tv.setTypeface(null, Typeface.BOLD)
            col.addView(tv)
        }
        fila.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return fila
    }

    // ---------- Filtros del sitio (Género, Tipo, Estado, Año...) ----------

    // Todos los filtros de los módulos activos, agrupados por nombre
    private fun filtrosRemotos(): List<FiltroUI> {
        val mapa = LinkedHashMap<String, FiltroUI>()
        for (m in modulosCargados) {
            if (moduloSel != null && m.nombre != moduloSel) continue
            if (m.fuente.isEmpty() || !MotorHub.esFormatoHub(m.fuente)) continue
            for (f in m.filtros) {
                val ui = mapa.getOrPut(f.nombre) { FiltroUI(f.nombre) }
                for ((nombreOp, valor) in f.opciones) {
                    ui.opciones.getOrPut(nombreOp) { mutableListOf() }
                        .add(Triple(m, f.clave, valor))
                }
            }
        }
        return mapa.values.toList()
    }

    private fun textoPildora(nombre: String, n: Int) = "$nombre ($n) ▾"

    // Crea las píldoras de filtro que corresponden al módulo elegido
    private fun reconstruirFiltros() {
        filtros.removeAllViews()
        val remotos = filtrosRemotos()
        if (remotos.isNotEmpty()) {
            seleccion.keys.retainAll(remotos.map { it.nombre }.toSet())
            for (f in remotos) {
                val p = Ui.pildora(this, textoPildora(f.nombre, if (seleccion.containsKey(f.nombre)) 1 else 0), 13f)
                p.setOnClickListener { dialogoFiltroRemoto(f, p) }
                filtros.addView(p, lpPildora())
            }
        } else {
            seleccion.clear()
            // Sin filtros del sitio: se usan los datos de los títulos ya cargados.
            // Solo aparecen los filtros que tengan opciones.
            val locales = listOf(
                Triple("Género", todos.filter { moduloPasa(it) }.flatMap { it.generos }.distinct().sorted(), generosSel),
                Triple("Tipo", todos.filter { moduloPasa(it) }.map { it.tipo }.filter { it.isNotEmpty() }.distinct().sorted(), tiposSel),
                Triple("Estado", todos.filter { moduloPasa(it) }.map { it.estado }.filter { it.isNotEmpty() }.distinct().sorted(), estadosSel)
            )
            for ((nombre, opciones, sel) in locales) {
                if (opciones.isEmpty()) {
                    sel.clear()
                    continue
                }
                val p = Ui.pildora(this, textoPildora(nombre, sel.size), 13f)
                p.setOnClickListener { dialogoFiltroLocal(nombre, opciones, sel, p) }
                filtros.addView(p, lpPildora())
            }
        }
        scrollFiltros.visibility =
            if (pestana == 0 && filtros.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun dialogoFiltroRemoto(f: FiltroUI, boton: TextView) {
        val nombres = f.opciones.keys.toList()
        AlertDialog.Builder(this)
            .setTitle(f.nombre)
            .setSingleChoiceItems(
                nombres.toTypedArray(),
                seleccion[f.nombre]?.let { nombres.indexOf(it) } ?: -1
            ) { d, which ->
                d.dismiss()
                seleccion[f.nombre] = nombres[which]
                aplicarSeleccion()
            }
            .setNeutralButton("Limpiar") { _, _ ->
                seleccion.remove(f.nombre)
                aplicarSeleccion()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun aplicarSeleccion() {
        tokenFiltro++
        itemsFiltro = emptyList()
        paginaFiltro = 1
        finFiltro = false
        cargandoFiltro = false
        reconstruirFiltros()
        if (seleccion.isEmpty()) actualizar() else cargarFiltro(true)
    }

    // Módulos que tienen TODAS las opciones elegidas, con los parámetros que hay que mandarle a cada uno
    private fun destinosFiltro(): List<Pair<Modulo, Map<String, String>>> {
        val remotos = filtrosRemotos().associateBy { it.nombre }
        var candidatos: LinkedHashMap<String, Pair<Modulo, LinkedHashMap<String, String>>>? = null
        for ((nombreFiltro, nombreOpcion) in seleccion) {
            val encontrados = remotos[nombreFiltro]?.opciones?.get(nombreOpcion) ?: emptyList()
            val nuevos = LinkedHashMap<String, Pair<Modulo, LinkedHashMap<String, String>>>()
            for ((modulo, clave, valor) in encontrados) {
                val previos = candidatos
                val parametros: LinkedHashMap<String, String> =
                    if (previos == null) LinkedHashMap() else (previos[modulo.nombre]?.second ?: continue)
                parametros[clave] = valor
                nuevos[modulo.nombre] = Pair(modulo, parametros)
            }
            candidatos = nuevos
        }
        return candidatos?.values?.map { Pair(it.first, it.second as Map<String, String>) } ?: emptyList()
    }

    private fun cargarFiltro(primera: Boolean) {
        if (cargandoFiltro) return
        val destinos = destinosFiltro()
        if (destinos.isEmpty()) {
            Toast.makeText(this, "Este módulo no tiene esa combinación de filtros", Toast.LENGTH_SHORT).show()
            return
        }
        cargandoFiltro = true
        val token = tokenFiltro
        val pagina = paginaFiltro
        if (primera) actualizar()
        Thread {
            val nuevos = mutableListOf<Item>()
            var error: String? = null
            for ((modulo, parametros) in destinos) {
                try {
                    nuevos.addAll(MotorHub.catalogo(modulo.fuente, parametros, pagina))
                } catch (e: Exception) {
                    error = e.message ?: "error"
                }
            }
            val mensaje = error
            runOnUiThread {
                if (token != tokenFiltro) return@runOnUiThread
                cargandoFiltro = false
                val existentes = itemsFiltro.map { it.id + "|" + it.titulo }.toSet()
                val unicos = nuevos.filter { (it.id + "|" + it.titulo) !in existentes }
                if (unicos.isEmpty()) {
                    finFiltro = true
                } else {
                    itemsFiltro = itemsFiltro + unicos
                    paginaFiltro = pagina + 1
                    Repositorio.agregarARegistro(unicos)
                }
                if (mensaje != null && itemsFiltro.isEmpty()) {
                    Toast.makeText(this, "No cargó el filtro: $mensaje", Toast.LENGTH_LONG).show()
                }
                val posicion = if (primera) 0 else grilla.firstVisiblePosition
                actualizar()
                if (!primera) grilla.setSelection(posicion)
            }
        }.start()
    }

    private fun dialogoFiltroLocal(
        nombre: String,
        opciones: List<String>,
        sel: MutableSet<String>,
        boton: TextView
    ) {
        val marcados = BooleanArray(opciones.size) { sel.contains(opciones[it]) }
        AlertDialog.Builder(this)
            .setTitle(nombre)
            .setMultiChoiceItems(opciones.toTypedArray(), marcados) { _, i, marcado ->
                if (marcado) sel.add(opciones[i]) else sel.remove(opciones[i])
            }
            .setPositiveButton("Aplicar") { _, _ ->
                boton.text = textoPildora(nombre, sel.size)
                actualizar()
            }
            .setNeutralButton("Limpiar") { _, _ ->
                sel.clear()
                boton.text = textoPildora(nombre, 0)
                actualizar()
            }
            .show()
    }

    // ---------- Búsqueda por nombre ----------

    private fun iniciarBusqueda() {
        val q = buscador.text.toString().trim()
        consulta = q
        tokenBusqueda++
        itemsBusqueda = emptyList()
        paginaBusqueda = 1
        finBusqueda = false
        cargandoBusqueda = false
        if (q.isEmpty() || modulosBuscables().isEmpty()) {
            finBusqueda = true
            actualizar()
        } else {
            cargarBusqueda(true)
        }
    }

    private fun cargarBusqueda(primera: Boolean) {
        if (cargandoBusqueda || finBusqueda || consulta.isEmpty()) return
        val modulos = modulosBuscables()
        if (modulos.isEmpty()) return
        cargandoBusqueda = true
        val token = tokenBusqueda
        val pagina = paginaBusqueda
        val q = consulta
        if (primera) actualizar()
        Thread {
            val nuevos = mutableListOf<Item>()
            var error: String? = null
            for (m in modulos) {
                try {
                    MotorHub.buscar(m.fuente, q, emptyMap(), pagina)?.let { nuevos.addAll(it) }
                } catch (e: Exception) {
                    error = e.message ?: "error"
                }
            }
            val mensaje = error
            runOnUiThread {
                if (token != tokenBusqueda) return@runOnUiThread
                cargandoBusqueda = false
                val existentes = itemsBusqueda.map { it.id + "|" + it.titulo }.toSet()
                val unicos = nuevos.filter { (it.id + "|" + it.titulo) !in existentes }
                if (unicos.isEmpty()) {
                    finBusqueda = true
                } else {
                    itemsBusqueda = itemsBusqueda + unicos
                    paginaBusqueda = pagina + 1
                    Repositorio.agregarARegistro(unicos)
                }
                if (mensaje != null && itemsBusqueda.isEmpty()) {
                    Toast.makeText(this, "No se pudo buscar: $mensaje", Toast.LENGTH_LONG).show()
                }
                val posicion = if (primera) 0 else grilla.firstVisiblePosition
                actualizar()
                if (!primera) grilla.setSelection(posicion)
            }
        }.start()
    }

    // ---------- Botón "Cargar más" (días anteriores) ----------

    private fun reiniciarMas() {
        tokenMas++
        itemsMas = emptyList()
        paginaMas = 1
        paginaCatalogo = 0
        cargandoMas = false
        if (::botonMas.isInitialized) botonMas.text = "Cargar más"
    }

    private fun moduloMas(): Modulo? = modulosCargados.firstOrNull {
        it.nombre == moduloSel && it.fuente.isNotEmpty() && MotorHub.esFormatoHub(it.fuente)
    }

    // Se ve solo en Inicio, sin filtros, cuando llegas al final de la lista
    private fun mostrarBotonMas() {
        if (!::botonMas.isInitialized) return
        val ok = pestana == 0 && seleccion.isEmpty() && alFinal && visibles.isNotEmpty() && moduloMas() != null
        botonMas.visibility = if (ok) View.VISIBLE else View.GONE
    }

    private fun cargarMas() {
        if (cargandoMas) return
        val m = moduloMas() ?: return
        cargandoMas = true
        botonMas.text = "Cargando..."
        val token = tokenMas
        val desde = paginaMas
        val conocidos = (todos + itemsMas).map { it.id + "|" + it.titulo }.toSet()
        val catDesde = paginaCatalogo
        Thread {
            var cat = catDesde
            var nuevos: List<Item> = emptyList()
            var error: String? = null
            var usada = desde
            // Si ese día no trae nada se pasa al anterior (hasta 7 intentos)
            for (i in 1..7) {
                usada = desde + i
                try {
                    nuevos = MotorHub.recientes(m.fuente, usada).filter { (it.id + "|" + it.titulo) !in conocidos }
                } catch (e: Exception) {
                    error = e.message ?: "error"
                    break
                }
                if (nuevos.isNotEmpty()) break
            }
            // Si los días anteriores no traen nada nuevo, se sigue con más títulos del catálogo
            if (nuevos.isEmpty() && error == null) {
                for (k in 1..3) {
                    cat++
                    try {
                        nuevos = MotorHub.catalogo(m.fuente, emptyMap(), cat)
                            .filter { (it.id + "|" + it.titulo) !in conocidos }
                    } catch (e: Exception) {
                        error = e.message ?: "error"
                        break
                    }
                    if (nuevos.isNotEmpty()) break
                }
            }
            val mensaje = error
            runOnUiThread {
                if (token != tokenMas) return@runOnUiThread
                cargandoMas = false
                botonMas.text = "Cargar más"
                paginaMas = usada
                paginaCatalogo = cat
                if (nuevos.isNotEmpty()) {
                    itemsMas = itemsMas + nuevos
                    Repositorio.agregarARegistro(nuevos)
                } else {
                    Toast.makeText(
                        this,
                        if (mensaje != null) "No se pudo cargar más: $mensaje" else "No hay más títulos",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                val posicion = grilla.firstVisiblePosition
                actualizar()
                grilla.setSelection(posicion)
            }
        }.start()
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
        // Se manda el enlace y el módulo directamente: así el anime abre aunque venga de un filtro o de la búsqueda
        intent.putExtra("id", item.id)
        intent.putExtra("fuente", item.fuente)
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
