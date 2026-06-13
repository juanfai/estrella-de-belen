package com.estrelladebelen.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.sqrt

// Procesar 1 de cada STRIDE grupos de muestras para calcular RMS.
// El renderer aplica attack/decay smoothing de todas formas, así que
// reducir la resolución de muestreo 4× no tiene efecto visual perceptible.
private const val STRIDE = 4

class AudioDecoder {

    /**
     * Decodifica el audio en streaming calculando RMS por frame directamente —
     * sin acumular todo el PCM en RAM. Soporta archivos de cualquier duración.
     *
     * @return Par (rmsFrames normalizado [0..1], sampleRate)
     */
    fun decodeToRms(
        context: Context, uri: Uri, fps: Int,
        onProgress: ((Float) -> Unit)? = null
    ): Pair<FloatArray, Int> {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        val trackIndex = findAudioTrack(extractor)
            ?: error("No se encontró pista de audio en el archivo")
        extractor.selectTrack(trackIndex)

        val format       = extractor.getTrackFormat(trackIndex)
        val mime         = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate   = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        // Duración total para reportar progreso (puede no estar disponible en todos los formatos)
        val totalUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else 0L

        // Con stride STRIDE procesamos 1 de cada STRIDE muestras → misma cantidad de frames RMS,
        // pero cada frame se calcula con sampleRate/fps/STRIDE muestras en vez de sampleRate/fps.
        val samplesPerRmsFrame = (sampleRate / fps / STRIDE).coerceAtLeast(1)
        val skipShorts         = channelCount * (STRIDE - 1)
        val rmsAccum           = ArrayList<Float>()
        var frameSumSq         = 0.0
        var frameSamples       = 0

        val info      = MediaCodec.BufferInfo()
        var inputEOS  = false
        var outputEOS = false

        while (!outputEOS) {
            if (!inputEOS) {
                val idx = codec.dequeueInputBuffer(10_000L)
                if (idx >= 0) {
                    val buf  = codec.getInputBuffer(idx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEOS = true
                    } else {
                        if (totalUs > 0 && onProgress != null) {
                            onProgress((extractor.sampleTime.toFloat() / totalUs).coerceIn(0f, 1f))
                        }
                        codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val idx = codec.dequeueOutputBuffer(info, 10_000L)
            if (idx >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEOS = true
                if (info.size > 0) {
                    val outBuf = codec.getOutputBuffer(idx)!!
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val sb = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

                    // Procesar 1 de cada STRIDE grupos de muestras.
                    // sb.position() es O(1) en ShortBuffer → no lee los shorts saltados.
                    while (sb.remaining() >= channelCount) {
                        var sum = 0L
                        for (c in 0 until channelCount) sum += sb.get().toLong()
                        if (skipShorts > 0 && sb.remaining() >= skipShorts)
                            sb.position(sb.position() + skipShorts)

                        val mono = (sum.toDouble() / channelCount)
                        frameSumSq += mono * mono
                        frameSamples++
                        if (frameSamples >= samplesPerRmsFrame) {
                            rmsAccum.add(sqrt(frameSumSq / frameSamples).toFloat())
                            frameSumSq = 0.0; frameSamples = 0
                        }
                    }
                }
                codec.releaseOutputBuffer(idx, false)
            }
        }

        // Último frame parcial
        if (frameSamples > 0) rmsAccum.add(sqrt(frameSumSq / frameSamples).toFloat())

        codec.stop(); codec.release(); extractor.release()

        // Normalizar a [0, 1]
        val raw = rmsAccum.toFloatArray()
        val max = raw.maxOrNull()?.takeIf { it > 0f } ?: 1f
        return Pair(FloatArray(raw.size) { (raw[it] / max).coerceIn(0f, 1f) }, sampleRate)
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
}
