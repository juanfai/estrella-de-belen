package com.estrelladebelen.app.render

import android.graphics.Color as AColor

object VisualConfig {

    // Glow is always white. Halo is chosen per meditation.
    val GLOW_COLOR         = hex("#ffffff")
    val DEFAULT_HALO_COLOR = hex("#9890B8")  // Lilac
    val BG_COLOR           = hex("#3A3F68")  // Midnight

    const val SENSITIVITY        = 1.8f
    const val GLOW_BRIGHTNESS    = 0.72f
    const val STRETCH_THRESHOLD  = 0.44f
    const val STRETCH_FACTOR     = 0.77f

    const val GLOW_ATTACK        = 0.35f
    const val GLOW_DECAY         = 0.07f
    const val HALO_ATTACK        = 0.35f
    const val HALO_DECAY         = 0.025f

    fun Int.toGlRgb() = floatArrayOf(
        AColor.red(this)   / 255f,
        AColor.green(this) / 255f,
        AColor.blue(this)  / 255f
    )

    fun parseColor(hex: String): Int = AColor.parseColor(hex)

    private fun hex(code: String): Int = AColor.parseColor(code)
}
