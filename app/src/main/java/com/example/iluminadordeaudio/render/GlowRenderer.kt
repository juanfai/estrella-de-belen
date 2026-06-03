package com.example.iluminadordeaudio.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

/**
 * Requiere canvas de software para que BlurMaskFilter funcione.
 * Todos los tamaños son relativos a min(w, h) → escala igual en preview y en export.
 */
class GlowRenderer {

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint  = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawFrame(
        canvas: Canvas,
        amplitude: Float,
        bgColor: Int,
        glowColor: Int = Color.WHITE
    ) {
        val w  = canvas.width.toFloat()
        val h  = canvas.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        // Sensibilidad +50 % antes de clampear
        val norm = (amplitude * 1.5f).coerceIn(0f, 1f)
        val ref  = minOf(w, h)   // dimensión de referencia para escalar

        canvas.drawColor(bgColor)
        if (norm == 0f) return

        drawTrail(canvas, norm, glowColor, cx, cy, w, ref)
        drawGlow(canvas, norm, glowColor, cx, cy, ref)
    }

    private fun drawTrail(
        canvas: Canvas,
        norm: Float,
        glowColor: Int,
        cx: Float, cy: Float, w: Float, ref: Float
    ) {
        val alpha = (norm * 210).toInt().coerceIn(0, 255)
        val r = Color.red(glowColor); val g = Color.green(glowColor); val b = Color.blue(glowColor)
        trailPaint.shader = LinearGradient(
            0f, cy, w, cy,
            intArrayOf(Color.argb(0, r, g, b), Color.argb(alpha, r, g, b),
                       Color.argb(alpha, r, g, b), Color.argb(0, r, g, b)),
            floatArrayOf(0f, 0.25f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        val trailH = ref * (0.006f + norm * 0.028f)
        canvas.drawRect(0f, cy - trailH / 2f, w, cy + trailH / 2f, trailPaint)
    }

    private fun drawGlow(
        canvas: Canvas,
        norm: Float,
        glowColor: Int,
        cx: Float, cy: Float, ref: Float
    ) {
        // Cada entrada: (radio, alpha, factor de blur)
        // Las capas exteriores crean el halo difuso (~1/3 de pantalla).
        // Las capas interiores crean el núcleo brillante notorio.
        val layers = listOf(
            Triple(norm * ref * 0.45f,  norm * 0.05f, 0.55f),   // halo exterior suave
            Triple(norm * ref * 0.28f,  norm * 0.13f, 0.55f),   // halo medio
            Triple(norm * ref * 0.15f,  norm * 0.35f, 0.50f),   // brillo interior
            Triple(norm * ref * 0.07f,  norm * 0.85f, 0.38f),   // núcleo brillante
            Triple(norm * ref * 0.030f, 0.98f,        0.20f),   // punto central intenso
            Triple(norm * ref * 0.012f, 1.00f,        0.00f),   // núcleo duro sin blur (punto blanco puro)
        )
        layers.forEach { (radius, alpha, blurFactor) ->
            if (radius <= 0f) return@forEach
            glowPaint.reset()
            glowPaint.isAntiAlias = true
            glowPaint.color = glowColor
            glowPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            val blurR = radius * blurFactor
            glowPaint.maskFilter = if (blurR > 0f) BlurMaskFilter(blurR, BlurMaskFilter.Blur.NORMAL) else null
            canvas.drawCircle(cx, cy, radius, glowPaint)
        }
    }
}
