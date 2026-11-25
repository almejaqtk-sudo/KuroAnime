package com.mistream.app

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.net.HttpURLConnection
import java.net.URL

object MotorJs {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) KuroAnime"

    // Lo que ven los módulos: http.get(url, headers), http.json(url, headers) y log(texto)
    private const val PRELUDIO = """
var http = {
  get: function (url, headers) {
    return __get(String(url), headers ? JSON.stringify(headers) : "");
  },
  json: function (url, headers) {
    return JSON.parse(http.get(url, headers));
  }
};
function log(x) { __log(String(x)); }
"""

    private fun descargar(url: String, headersJson: String?): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 15000
        con.readTimeout = 15000
        con.instanceFollowRedirects = true
        con.setRequestProperty("User-Agent", USER_AGENT)
        if (!headersJson.isNullOrEmpty()) {
            val h = JSONObject(headersJson)
            for (k in h.keys()) con.setRequestProperty(k, h.getString(k))
        }
        try {
            val codigo = con.responseCode
            val flujo = if (codigo in 200..299) con.inputStream else con.errorStream
            val texto = flujo?.bufferedReader()?.use { it.readText() } ?: ""
            if (codigo !in 200..299) throw RuntimeException("HTTP $codigo en $url")
            return texto
        } finally {
            con.disconnect()
        }
    }

    private fun funcion(accion: (Array<Any?>) -> Any?): BaseFunction =
        object : BaseFunction() {
            override fun call(
                cx: Context,
                scope: Scriptable,
                thisObj: Scriptable?,
                args: Array<Any?>
            ): Any? = accion(args)
        }

    private fun crearScope(cx: Context): Scriptable {
        // Entorno seguro: el módulo no tiene acceso a clases de Java ni a la app
        val scope = cx.initSafeStandardObjects()

        ScriptableObject.putProperty(scope, "__get", funcion { a ->
            val url = Context.toString(a.getOrNull(0))
            val h = Context.toString(a.getOrNull(1))
            val headers = if (h == "" || h == "null" || h == "undefined") null else h
            descargar(url, headers)
        })
        ScriptableObject.putProperty(scope, "__log", funcion { a ->
            Log.d("KuroJS", Context.toString(a.getOrNull(0)))
            null
        })

        cx.evaluateString(scope, PRELUDIO, "prelude", 1, null)
        return scope
    }

    private fun <T> conMotor(bloque: (Context, Scriptable) -> T): T {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            val scope = crearScope(cx)
            return bloque(cx, scope)
        } finally {
            Context.exit()
        }
    }

    private fun evaluar(cx: Context, scope: Scriptable, codigo: String): String =
        Context.toString(cx.evaluateString(scope, codigo, "kuro", 1, null))

    // Ejecuta el código del módulo y comprueba que defina "modulo" con items()
    private fun prepararModulo(cx: Context, scope: Scriptable, codigo: String): String {
        cx.evaluateString(scope, codigo, "modulo", 1, null)
        val ok = evaluar(
            cx, scope,
            "(typeof modulo === 'object' && modulo !== null && typeof modulo.items === 'function') ? 'si' : 'no'"
        )
        if (ok != "si") {
            throw IllegalArgumentException(
                "El módulo JS debe definir: var modulo = { nombre: \"...\", items: function() { ... } }"
            )
        }
        return evaluar(cx, scope, "String(modulo.nombre || 'Módulo JS')")
    }

    // Se usa al instalar: valida el código y devuelve el nombre
    fun nombreDe(codigo: String): String = conMotor { cx, scope ->
        prepararModulo(cx, scope, codigo)
    }

    // Carga el catálogo: llama a modulo.items()
    fun cargar(codigo: String): Modulo = conMotor { cx, scope ->
        val nombre = prepararModulo(cx, scope, codigo)
        val texto = evaluar(cx, scope, "JSON.stringify(modulo.items())")
        if (texto == "undefined") {
            throw IllegalArgumentException("items() debe devolver una lista de títulos")
        }
        val arr = JSONArray(texto)
        val items = mutableListOf<Item>()
        for (i in 0 until arr.length()) {
            items.add(ModuloParser.itemDeObjeto(arr.getJSONObject(i), nombre, codigo))
        }
        Modulo(nombre, items)
    }

    // Pide páginas anteriores a módulos JavaScript que implementan home(página).
    fun home(codigo: String, pagina: Int): List<Item> = conMotor { cx, scope ->
        val nombre = prepararModulo(cx, scope, codigo)
        val tieneHome = evaluar(cx, scope, "typeof modulo.home === 'function' ? 'si' : 'no'")
        if (tieneHome != "si") return@conMotor emptyList<Item>()
        val texto = evaluar(cx, scope, "JSON.stringify(modulo.home(" + pagina.coerceAtLeast(1) + "))")
        if (texto == "undefined" || texto == "null") return@conMotor emptyList<Item>()
        val arr = JSONArray(texto)
        val items = mutableListOf<Item>()
        for (i in 0 until arr.length()) items.add(ModuloParser.itemDeObjeto(arr.getJSONObject(i), nombre, codigo))
        items
    }

    // Llama a modulo.episodios(id) cuando abres un título
    fun episodios(codigo: String, id: String): List<Episodio> = conMotor { cx, scope ->
        prepararModulo(cx, scope, codigo)
        val existe = evaluar(cx, scope, "typeof modulo.episodios === 'function' ? 'si' : 'no'")
        if (existe != "si") {
            throw IllegalArgumentException("El módulo no define episodios(id)")
        }
        val texto = evaluar(
            cx, scope,
            "JSON.stringify(modulo.episodios(" + JSONObject.quote(id) + "))"
        )
        if (texto == "undefined") {
            throw IllegalArgumentException("episodios(id) debe devolver una lista")
        }
        ModuloParser.episodiosDeJson(texto)
    }
}
