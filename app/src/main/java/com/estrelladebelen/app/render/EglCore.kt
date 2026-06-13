package com.estrelladebelen.app.render

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface

/**
 * Gestión del contexto EGL necesario para renderizar con OpenGL ES 2.0
 * hacia la superficie de entrada de MediaCodec.
 */
class EglCore {

    val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val config: EGLConfig
    val context: EGLContext

    init {
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE,         8,
            EGL14.EGL_GREEN_SIZE,       8,
            EGL14.EGL_BLUE_SIZE,        8,
            EGL14.EGL_ALPHA_SIZE,       8,
            EGL14.EGL_RENDERABLE_TYPE,  EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,     EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0)
        config = configs[0]!!

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
    }

    /** Crea una EGLSurface a partir de una android.view.Surface (ej. MediaCodec input surface). */
    fun createWindowSurface(surface: Any): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        return EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
    }

    fun makeCurrent(surface: EGLSurface) {
        EGL14.eglMakeCurrent(display, surface, surface, context)
    }

    /**
     * Establece el timestamp de presentación (en nanosegundos) para el próximo swapBuffers.
     * Permite timestamps exactos en el encoder — mucho mejor que el reloj del sistema.
     */
    fun setPresentationTime(surface: EGLSurface, nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, nanos)
    }

    /** Envía el frame renderizado al encoder. */
    fun swapBuffers(surface: EGLSurface) {
        EGL14.eglSwapBuffers(display, surface)
    }

    fun destroySurface(surface: EGLSurface) {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
    }

    fun release() {
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}
