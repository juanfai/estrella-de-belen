package com.example.iluminadordeaudio.audio

import kotlin.math.sqrt

class AudioAnalyzer {

    fun rmsPerFrame(pcm: ShortArray, sampleRate: Int, fps: Int): FloatArray {
        val samplesPerFrame = sampleRate / fps
        if (samplesPerFrame <= 0 || pcm.isEmpty()) return FloatArray(0)

        val frameCount = pcm.size / samplesPerFrame
        val rms = FloatArray(frameCount) { i ->
            val start = i * samplesPerFrame
            val end = minOf(start + samplesPerFrame, pcm.size)
            var sumSq = 0.0
            for (j in start until end) {
                val s = pcm[j].toDouble()
                sumSq += s * s
            }
            sqrt(sumSq / (end - start)).toFloat()
        }

        // Normalizar a [0, 1] usando el máximo del archivo completo
        val maxRms = rms.maxOrNull()?.takeIf { it > 0f } ?: 1f
        return FloatArray(rms.size) { i -> (rms[i] / maxRms).coerceIn(0f, 1f) }
    }
}
