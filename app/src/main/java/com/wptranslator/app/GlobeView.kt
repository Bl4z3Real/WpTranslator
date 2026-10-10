package com.wptranslator.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Globo a puntini in stile Metro (stilizzato, generato proceduralmente). */
class GlobeView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4F4F4F.toInt() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) / 2f
        if (r <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val step = r / 22f
        var y = -r
        while (y <= r) {
            var x = -r
            while (x <= r) {
                if (hypot(x, y) <= r) {
                    val nx = x / r
                    val ny = y / r
                    val v = sin(nx * 5.1 + 1.3) * cos(ny * 4.3) +
                        sin((nx + ny) * 7.0) * 0.5 + cos(nx * 9.0 - ny * 3.0) * 0.3
                    if (v > 0.15) canvas.drawCircle(cx + x, cy + y, step * 0.3f, paint)
                }
                x += step
            }
            y += step
        }
    }
}
