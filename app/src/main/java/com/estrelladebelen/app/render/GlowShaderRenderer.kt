package com.estrelladebelen.app.render

import android.opengl.GLES20
import com.estrelladebelen.app.render.VisualConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renderer OpenGL ES 2.0 que reproduce el efecto de glow mediante Gaussianas analíticas
 * en el fragment shader — sin BlurMaskFilter, sin CPU.
 *
 * Cada pixel del output se calcula como la suma de 8 capas de Gaussianas elípticas
 * para el halo violeta y 8 para el glow blanco. Las sigmas se derivan de los parámetros
 * originales de GlowRenderer (hw, hh, blurRadius) → effectiveSigma ≈ hw + blurRadius/2.
 */
class GlowShaderRenderer(private val width: Int, private val height: Int) {

    private val program: Int
    private val quadBuffer: Int
    private val posAttr: Int
    private val uSize: Int; private val uAmp: Int; private val uHamp: Int
    private val uGlow: Int; private val uHalo: Int; private val uBg: Int
    private val uStretch: Int; private val uGlowBrightness: Int

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERT_SRC)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAG_SRC)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs); GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)

        posAttr  = GLES20.glGetAttribLocation(program,  "a_pos")
        uSize          = GLES20.glGetUniformLocation(program, "u_size")
        uAmp           = GLES20.glGetUniformLocation(program, "u_amp")
        uHamp          = GLES20.glGetUniformLocation(program, "u_hamp")
        uGlow          = GLES20.glGetUniformLocation(program, "u_glow")
        uHalo          = GLES20.glGetUniformLocation(program, "u_halo")
        uBg            = GLES20.glGetUniformLocation(program, "u_bg")
        uStretch       = GLES20.glGetUniformLocation(program, "u_stretch")
        uGlowBrightness = GLES20.glGetUniformLocation(program, "u_glow_brightness")

        // Full-screen quad (triangle strip): BL, BR, TL, TR
        val verts = floatArrayOf(-1f, -1f,  1f, -1f,  -1f, 1f,  1f, 1f)
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).rewind()
        val vbo = IntArray(1); GLES20.glGenBuffers(1, vbo, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES20.GL_STATIC_DRAW)
        quadBuffer = vbo[0]

        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * Renderiza un frame.
     * @param amp       amplitud suavizada del glow blanco [0, 1] — ya con factor 1.8× aplicado
     * @param hamp      amplitud suavizada del halo violeta [0, 1]
     * @param glowRgb   color del glow (ej. [1,1,1] = blanco)
     * @param haloRgb   color del halo (ej. [0.627, 0, 1] = violeta)
     * @param bgRgb     color de fondo (ej. [0,0,0] = negro)
     * @param stretchY  factor de estiramiento vertical (1.0 = sin stretch)
     */
    fun render(
        amp: Float, hamp: Float,
        glowRgb: FloatArray, haloRgb: FloatArray, bgRgb: FloatArray,
        stretchY: Float
    ) {
        GLES20.glClearColor(bgRgb[0], bgRgb[1], bgRgb[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)
        GLES20.glUniform2f(uSize, width.toFloat(), height.toFloat())
        GLES20.glUniform1f(uAmp, amp)
        GLES20.glUniform1f(uHamp, hamp)
        GLES20.glUniform3fv(uGlow, 1, glowRgb, 0)
        GLES20.glUniform3fv(uHalo, 1, haloRgb, 0)
        GLES20.glUniform3fv(uBg,   1, bgRgb,   0)
        GLES20.glUniform1f(uStretch, stretchY)
        GLES20.glUniform1f(uGlowBrightness, VisualConfig.GLOW_BRIGHTNESS)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBuffer)
        GLES20.glEnableVertexAttribArray(posAttr)
        GLES20.glVertexAttribPointer(posAttr, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posAttr)
    }

    fun release() {
        val vbo = IntArray(1) { quadBuffer }
        GLES20.glDeleteBuffers(1, vbo, 0)
        GLES20.glDeleteProgram(program)
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src); GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val VERT_SRC = """
attribute vec2 a_pos;
void main() { gl_Position = vec4(a_pos, 0.0, 1.0); }
"""

        /**
         * Fragment shader: suma de Gaussianas elípticas analíticas.
         *
         * Las sigmas de cada capa se derivan de los parámetros del GlowRenderer original:
         *   effectiveSigma ≈ halfAxis + blurRadius/2
         *
         * Esto reproduce visualmente el efecto de BlurMaskFilter con cálculo exacto en GPU.
         * La función eg(p, sx, sy) calcula exp(-0.5 * ((x/sx)² + (y/sy)²)).
         */
        private const val FRAG_SRC = """
precision highp float;
uniform vec2  u_size;
uniform float u_amp;
uniform float u_hamp;
uniform vec3  u_glow;
uniform vec3  u_halo;
uniform vec3  u_bg;
uniform float u_stretch;
uniform float u_glow_brightness;   // intensidad del destello blanco (0-1)

float eg(vec2 p, float sx, float sy) {
    float sx2 = sx * sx + 0.01;
    float sy2 = sy * sy + 0.01;
    return exp(-0.5 * (p.x * p.x / sx2 + p.y * p.y / sy2));
}

void main() {
    vec2 ctr = u_size * 0.5;
    vec2 raw = gl_FragCoord.xy - ctr;
    // Aplicar estiramiento vertical (divide y → el glow se extiende en y)
    vec2 p = vec2(raw.x, raw.y / max(u_stretch, 0.01));

    float ref = min(u_size.x, u_size.y) * 1.25;
    float a   = u_amp;
    float h   = u_hamp;
    float w   = u_size.x;

    vec3 c = u_bg;

    // ── Halo (sigmas 1.5× más anchas que el glow para que sea visible alrededor)
    // Cuando h > a (halo se encoge más lento), el anillo violeta rodea al glow blanco.
    c += u_halo * (h * 0.18) * eg(p, w * 0.50,        h * ref * 0.066);
    c += u_halo * (h * 0.14) * eg(p, h * ref * 0.820,  h * ref * 0.114);
    c += u_halo * (h * 0.18) * eg(p, h * ref * 0.614,  h * ref * 0.186);
    c += u_halo * (h * 0.22) * eg(p, h * ref * 0.432,  h * ref * 0.305);
    c += u_halo * (h * 0.32) * eg(p, h * ref * 0.308,  h * ref * 0.270);
    c += u_halo * (h * 0.65) * eg(p, h * ref * 0.183,  h * ref * 0.168);
    c += u_halo * (h * 1.00) * eg(p, h * ref * 0.090,  h * ref * 0.084);
    c += u_halo * 1.00        * eg(p, h * ref * 0.027,  h * ref * 0.026);

    // ── Glow blanco (modulado por u_glow_brightness) ─────────────────────────
    float gb = u_glow_brightness;
    c += u_glow * (a * 0.13 * gb) * eg(p, w * 0.50,        a * ref * 0.047);
    c += u_glow * (a * 0.10 * gb) * eg(p, a * ref * 0.546,  a * ref * 0.076);
    c += u_glow * (a * 0.13 * gb) * eg(p, a * ref * 0.409,  a * ref * 0.124);
    c += u_glow * (a * 0.16 * gb) * eg(p, a * ref * 0.288,  a * ref * 0.203);
    c += u_glow * (a * 0.25 * gb) * eg(p, a * ref * 0.205,  a * ref * 0.180);
    c += u_glow * (a * 0.50 * gb) * eg(p, a * ref * 0.122,  a * ref * 0.112);
    c += u_glow * (a * 0.88 * gb) * eg(p, a * ref * 0.060,  a * ref * 0.056);
    c += u_glow * (0.96    * gb)  * eg(p, a * ref * 0.018,  a * ref * 0.017);

    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
    }
}
