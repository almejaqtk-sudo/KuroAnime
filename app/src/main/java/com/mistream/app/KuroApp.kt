package com.mistream.app

import android.app.Application
import android.content.Context

class KuroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        contexto = applicationContext
        // Arranca el motor de módulos en segundo plano para que el primer módulo no espere
        val prefs = getSharedPreferences("mistream", MODE_PRIVATE)
        Thread {
            try {
                if (Repositorio.leerInstalados(prefs).isNotEmpty()) MotorHub.precalentar()
            } catch (e: Exception) {
                // si falla, se arranca cuando haga falta
            }
        }.start()
    }

    companion object {
        lateinit var contexto: Context
    }
}
