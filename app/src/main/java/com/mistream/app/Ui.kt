package com.mistream.app

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.TextView

object Ui {
    val FONDO = Color.parseColor("#12101C")
    val BARRA = Color.parseColor("#0D0B15")
    val PILDORA = Color.parseColor("#1D1A2B")
    val BORDE = Color.parseColor("#3B3654")
    val ROSA = Color.parseColor("#FF5C8A")
    val GRIS = Color.parseColor("#8A869C")

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun fondo(ctx: Context, color: Int, radioDp: Int, borde: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(ctx, radioDp).toFloat()
        if (borde != 0) g.setStroke(dp(ctx, 1).coerceAtLeast(1), borde)
        return g
    }

    fun pildora(ctx: Context, texto: String, tam: Float = 15f): TextView {
        val t = TextView(ctx)
        t.text = texto
        t.setTextColor(Color.WHITE)
        t.textSize = tam
        t.setTypeface(null, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        t.background = fondo(ctx, PILDORA, 22, BORDE)
        return t
    }

    fun redondear(vista: View, radioPx: Float) {
        vista.clipToOutline = true
        vista.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radioPx)
            }
        }
    }
}
