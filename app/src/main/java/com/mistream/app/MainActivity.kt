package com.mistream.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texto = TextView(this)
        texto.text = "MiStream funcionando"
        texto.textSize = 24f
        texto.setPadding(48, 200, 48, 48)
        setContentView(texto)
    }
}
