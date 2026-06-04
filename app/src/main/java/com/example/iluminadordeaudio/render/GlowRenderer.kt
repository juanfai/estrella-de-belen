package com.example.iluminadordeaudio.render

import android.graphics.BlurMaskFilter
import com.example.iluminadordeaudio.render.VisualConfig
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Requiere canvas de software (no hardware-accelerated) para que BlurMaskFilter funcione.
 *
 * El efecto usa capas de elipses progresivas: la más ancha y achatada produce la estela
 * horizontal, las capas intermedias hacen la transición, y las capas más circulares forman
 * el resplandor central. Al ser todas elipses con blur del mismo color se fusionan de forma
 * natural sin parecer dos elementos superpuestos.
 */
class GlowRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawFrame(
        canvas: Canvas,
        amplitude: Float,
        bgColor: Int,
        glowColor: Int = Color.WHITE,
        clearBackground: Boolean = true
    ) {
        if (clearBackground) canvas.drawColor(bgColor)

        val norm = (amplitude * VisualConfig.SENSITIVITY).coerceIn(0f, 1f)
        if (norm == 0f) return

        val w   = canvas.width.toFloat()
        val h   = canvas.height.toFloat()
        val cx  = w / 2f
        val cy  = h / 2f
        // Escalar el área de referencia un 25 % para toda la forma
        val ref = minOf(w, h) * 1.25f

        drawFused(canvas, norm, glowColor, cx, cy, w, ref)
    }

    /**
     * Cada fila = [ halfWidth, halfHeight, alpha, blurRadius ]
     * Las primeras filas = estela horizontal (ancha y fina)
     * Las últimas filas  = núcleo circular brillante
     * Las intermedias    = zona de fusión
     */
    private fun drawFused(
        canvas: Canvas,
        norm: Float,
        glowColor: Int,
        cx: Float, cy: Float,
        w: Float, ref: Float
    ) {
        val layers = arrayOf(
            // ── Estela horizontal ─────────────────────────────────────────────
            floatArrayOf(w * 0.500f,          norm * ref * 0.028f, norm * 0.13f, norm * ref * 0.038f),
            floatArrayOf(norm * ref * 0.520f,  norm * ref * 0.050f, norm * 0.10f, norm * ref * 0.052f),
            // ── Zona de transición / fusión ───────────────────────────────────
            floatArrayOf(norm * ref * 0.380f,  norm * ref * 0.095f, norm * 0.13f, norm * ref * 0.058f),
            floatArrayOf(norm * ref * 0.260f,  norm * ref * 0.175f, norm * 0.16f, norm * ref * 0.056f),
            floatArrayOf(norm * ref * 0.180f,  norm * ref * 0.155f, norm * 0.25f, norm * ref * 0.050f),
            // ── Glow circular ─────────────────────────────────────────────────
            floatArrayOf(norm * ref * 0.105f,  norm * ref * 0.095f, norm * 0.50f, norm * ref * 0.034f),
            floatArrayOf(norm * ref * 0.052f,  norm * ref * 0.048f, norm * 0.88f, norm * ref * 0.015f),
            // ── Núcleo duro (sin blur) ────────────────────────────────────────
            floatArrayOf(norm * ref * 0.018f,  norm * ref * 0.017f, 1.00f,       0f),
        )

        layers.forEach { layer ->
            val hw    = layer[0]
            val hh    = layer[1]
            val alpha = layer[2]
            val blur  = layer[3]
            if (hw <= 0f || hh <= 0f) return@forEach
            paint.reset()
            paint.isAntiAlias = true
            paint.isDither    = true
            paint.color  = glowColor
            paint.alpha  = (alpha * 255).toInt().coerceIn(0, 255)
            paint.maskFilter = if (blur > 0f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
            canvas.drawOval(cx - hw, cy - hh, cx + hw, cy + hh, paint)
        }
    }
}
