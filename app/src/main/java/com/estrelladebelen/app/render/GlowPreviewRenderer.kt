package com.estrelladebelen.app.render

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import com.estrelladebelen.app.render.VisualConfig.toGlRgb

class GlowPreviewRenderer : TextureView.SurfaceTextureListener {

    private val thread  = HandlerThread("GlPreview").also { it.start() }
    private val handler = Handler(thread.looper)

    @Volatile private var ready     = false
    private var egl:        EglCore?                    = null
    private var eglSurface: android.opengl.EGLSurface? = null
    private var renderer:   GlowShaderRenderer?         = null

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        handler.post {
            val e = EglCore()
            val s = e.createWindowSurface(st)
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
            renderer?.release();    renderer   = null
            eglSurface?.let { egl?.destroySurface(it) }; eglSurface = null
            egl?.release();         egl        = null
        }
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    fun drawFrame(amp: Float, hamp: Float, stretchY: Float, glowColor: Int, haloColor: Int) {
        if (!ready) return
        handler.removeCallbacksAndMessages(TOKEN)
        handler.postAtTime({
            if (!ready) return@postAtTime
            renderer?.render(
                amp, hamp,
                glowColor.toGlRgb(),
                haloColor.toGlRgb(),
                VisualConfig.BG_COLOR.toGlRgb(),
                stretchY
            )
            eglSurface?.let { egl?.swapBuffers(it) }
        }, TOKEN, 0)
    }

    fun release() { thread.quitSafely() }

    private companion object {
        val TOKEN = "frame"
    }
}
