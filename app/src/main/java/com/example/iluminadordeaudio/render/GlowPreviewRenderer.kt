package com.example.iluminadordeaudio.render

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import com.example.iluminadordeaudio.render.VisualConfig.toGlRgb

/**
 * Renderiza el efecto de glow en un TextureView usando OpenGL ES (mismo shader que el export).
 * Todo el trabajo GL ocurre en un HandlerThread dedicado para no bloquear el hilo principal.
 */
class GlowPreviewRenderer : TextureView.SurfaceTextureListener {

    private val thread  = HandlerThread("GlPreview").also { it.start() }
    private val handler = Handler(thread.looper)

    @Volatile private var ready     = false
    private var egl:        EglCore?            = null
    private var eglSurface: android.opengl.EGLSurface? = null
    private var renderer:   GlowShaderRenderer? = null

    // ── SurfaceTextureListener ────────────────────────────────────────────────

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        handler.post {
            val e = EglCore()
            val s = e.createWindowSurface(st)   // SurfaceTexture es aceptado directamente
            e.makeCurrent(s)
            egl = e; eglSurface = s
            renderer = GlowShaderRenderer(w, h)
            ready = true
        }
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
        handler.post {
            ready = false
            renderer?.release()
            renderer = GlowShaderRenderer(w, h)
            ready = true
        }
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        handler.post {
            ready = false
            renderer?.release();     renderer   = null
            eglSurface?.let { egl?.destroySurface(it) }; eglSurface = null
            egl?.release();          egl        = null
        }
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    // ── Renderizado ───────────────────────────────────────────────────────────

    /**
     * Solicita renderizar un frame. Si hay un frame pendiente en la cola se descarta
     * para evitar acumulación (el frame nuevo siempre gana).
     */
    fun drawFrame(amp: Float, hamp: Float, stretchY: Float) {
        if (!ready) return
        handler.removeCallbacksAndMessages(TOKEN)
        handler.postAtTime({
            if (!ready) return@postAtTime
            renderer?.render(
                amp, hamp,
                VisualConfig.GLOW_COLOR.toGlRgb(),
                VisualConfig.HALO_COLOR.toGlRgb(),
                VisualConfig.BG_COLOR.toGlRgb(),
                stretchY
            )
            eglSurface?.let { egl?.swapBuffers(it) }
        }, TOKEN, 0)
    }

    fun release() {
        thread.quitSafely()
    }

    private companion object {
        val TOKEN = "frame"   // tag para poder cancelar frames pendientes
    }
}
