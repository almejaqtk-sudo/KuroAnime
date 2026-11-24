package com.mistream.app

import android.app.Application
import android.content.Context

class KuroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        contexto = applicationContext
    }

    companion object {
        lateinit var contexto: Context
    }
}
