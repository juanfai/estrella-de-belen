package com.example.iluminadordeaudio.render

import android.graphics.Color as AColor

/**
 * Configuración visual centralizada.
 * Todos los colores y parámetros del efecto se modifican aquí.
 */
object VisualConfig {

    // ── Colores (notación hexadecimal web #RRGGBB) ────────────────────────────

    /** Destello central (glow) */
    val GLOW_COLOR  = hex("#FFFFFF")   // blanco

    /** Halo exterior — persiste después del pico de volumen */
    val HALO_COLOR  = hex("#5e1fff")   // azul-violeta

    /** Fondo */
    val BG_COLOR    = hex("#000000")   // negro

    // ── Sensibilidad y forma ──────────────────────────────────────────────────

    /** Multiplicador de amplitud antes de renderizar.
     *  Más alto = efecto más reactivo al volumen. */
    const val SENSITIVITY = 1.8f

    /** Umbral de amplitud (0-1) a partir del cual la imagen empieza a estirarse. */
    const val STRETCH_THRESHOLD = 0.44f

    /** Factor máximo de estiramiento vertical.
     *  0 = sin efecto, 0.77 = stretch notable en picos. */
    const val STRETCH_FACTOR = 0.77f

    // ── Velocidades de animación ──────────────────────────────────────────────

    /** Qué tan rápido sube la intensidad del glow al subir el volumen. */
    const val GLOW_ATTACK = 0.35f

    /** Qué tan rápido baja la intensidad del glow al bajar el volumen. */
    const val GLOW_DECAY  = 0.07f

    /** Qué tan rápido sube la intensidad del halo. */
    const val HALO_ATTACK = 0.35f

    /** Qué tan rápido baja el halo — más bajo que GLOW_DECAY para el efecto de "persistencia". */
    const val HALO_DECAY_PREVIEW = 0.025f   // a 60 fps
    const val HALO_DECAY_EXPORT  = 0.025f   // a 30 fps (equivalente aproximado)

    // ── Utilidades ────────────────────────────────────────────────────────────

    /** Convierte un color Android Int al formato RGB float[3] que usa OpenGL ES. */
    fun Int.toGlRgb() = floatArrayOf(
        AColor.red(this)   / 255f,
        AColor.green(this) / 255f,
        AColor.blue(this)  / 255f
    )

    private fun hex(code: String): Int = AColor.parseColor(code)
}
