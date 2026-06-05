package com.example.iluminadordeaudio.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.iluminadordeaudio.audio.AudioDecoder
import com.example.iluminadordeaudio.render.EglCore
import com.example.iluminadordeaudio.render.GlowShaderRenderer
import com.example.iluminadordeaudio.render.VisualConfig
import com.example.iluminadordeaudio.render.VisualConfig.toGlRgb
import java.io.File
import java.nio.ByteBuffer

class VideoExporter(
    private val context: Context,
    private val audioUri: Uri,
    private val bgColor: Int = android.graphics.Color.BLACK,
    private val glowColor: Int = android.graphics.Color.WHITE,
    private val fps: Int = 30,
    private val width: Int = 1080,
    private val height: Int = 1920,
    private val outputName: String = "AudioVisual",
    private val precomputedRms: FloatArray? = null
) {
    companion object {
        // Únicos formatos de audio que MediaMuxer acepta directo en contenedor MP4.
        // MP3, FLAC, WAV, OGG y otros necesitan ser transcodificados a AAC primero.
        private val DIRECTLY_MUXABLE_AUDIO = setOf(
            "audio/mp4a-latm",  // AAC / M4A
            "audio/3gpp",       // AMR-NB
            "audio/amr-wb",     // AMR-WB
        )
    }

    fun export(onProgress: (Float) -> Unit): Uri {
        val rmsFrames = if (precomputedRms != null && precomputedRms.isNotEmpty()) {
            onProgress(0.02f)
            precomputedRms
        } else {
            AudioDecoder().decodeToRms(context, audioUri, fps) { p ->
                onProgress(p * 0.20f)
            }.first
        }
        val totalFrames     = rmsFrames.size
        val videoDurationUs = totalFrames * 1_000_000L / fps

        // externalCacheDir tiene mucho más espacio que cacheDir interno.
        // 30 min a 8 Mbps = ~1.8 GB de video temp + output → necesita ~3.7 GB libres.
        val tempDir = context.externalCacheDir ?: context.cacheDir
        val ts      = System.currentTimeMillis()

        val encodeStart = if (precomputedRms != null && precomputedRms.isNotEmpty()) 0.02f else 0.20f
        val encodeEnd   = if (precomputedRms != null && precomputedRms.isNotEmpty()) 0.72f else 0.90f

        val videoTemp    = File(tempDir, "video_temp_$ts.mp4")
        val audioAacTemp = File(tempDir, "audio_aac_$ts.m4a")  // solo se usa si hay transcodificación
        val outputFile   = File(tempDir, "output_$ts.mp4")

        try {
            val videoFormat = encodeVideo(rmsFrames, videoTemp) { frame ->
                onProgress(encodeStart + (frame.toFloat() / totalFrames) * (encodeEnd - encodeStart))
            }
            remuxVideoAndAudio(videoTemp, videoDurationUs, videoFormat, audioAacTemp, outputFile) { p ->
                onProgress(encodeEnd + p * (1f - encodeEnd))
            }
            return saveToGallery(outputFile)
        } finally {
            try { videoTemp.delete()    } catch (_: Exception) {}
            try { audioAacTemp.delete() } catch (_: Exception) {}
            try { outputFile.delete()   } catch (_: Exception) {}
        }
    }

    /**
     * Renderiza y codifica todos los frames RMS a un MP4 de video-only.
     * Devuelve el [MediaFormat] capturado del encoder, que contiene csd-0/csd-1
     * (SPS/PPS de H.264). Pasarlo directamente al muxer de salida evita el error
     * "Failed to add track" que ocurre cuando se lee el format del archivo temporal
     * (puede estar incompleto en algunos dispositivos o si el archivo quedó corrupto).
     */
    private fun encodeVideo(
        rmsFrames: FloatArray,
        outputFile: File,
        onFrame: (Int) -> Unit
    ): MediaFormat {
        val encoder    = VideoEncoder(width, height, fps, outputFile)
        val egl        = EglCore()
        val eglSurface = egl.createWindowSurface(encoder.inputSurface)
        egl.makeCurrent(eglSurface)
        val renderer = GlowShaderRenderer(width, height)

        val haloRgb = VisualConfig.HALO_COLOR.toGlRgb()
        val glowRgb = VisualConfig.GLOW_COLOR.toGlRgb()
        val bgRgb   = VisualConfig.BG_COLOR.toGlRgb()
        var smoothedAmp = 0f
        var haloAmp     = 0f

        try {
            rmsFrames.forEachIndexed { i, targetAmp ->
                val wFactor = if (targetAmp >= smoothedAmp) VisualConfig.GLOW_ATTACK else VisualConfig.GLOW_DECAY
                smoothedAmp += (targetAmp - smoothedAmp) * wFactor
                val hFactor = if (targetAmp >= haloAmp) VisualConfig.HALO_ATTACK else VisualConfig.HALO_DECAY_EXPORT
                haloAmp += (targetAmp - haloAmp) * hFactor

                val amp    = (smoothedAmp * VisualConfig.SENSITIVITY).coerceIn(0f, 1f)
                val hamp   = (haloAmp     * VisualConfig.SENSITIVITY).coerceIn(0f, 1f)
                val excess = ((smoothedAmp - VisualConfig.STRETCH_THRESHOLD) /
                              (1f - VisualConfig.STRETCH_THRESHOLD)).coerceIn(0f, 1f)
                val stretch = 1f + excess * VisualConfig.STRETCH_FACTOR

                renderer.render(amp, hamp, glowRgb, haloRgb, bgRgb, stretch)
                egl.setPresentationTime(eglSurface, i.toLong() * 1_000_000_000L / fps)
                egl.swapBuffers(eglSurface)
                encoder.drainEncoder(false)
                onFrame(i)
            }
            encoder.drainEncoder(true)
            return encoder.capturedFormat
                ?: error("El encoder no emitió formato de salida")
        } finally {
            try { renderer.release()             } catch (_: Exception) {}
            try { egl.destroySurface(eglSurface) } catch (_: Exception) {}
            try { egl.release()                  } catch (_: Exception) {}
            encoder.release()  // propaga si muxer.stop() falla → archivo correcto o excepción clara
        }
    }

    private fun remuxVideoAndAudio(
        videoFile: File,
        videoDurationUs: Long,
        videoFormat: MediaFormat,
        audioAacTemp: File,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        // Detectar si el audio necesita transcodificación a AAC
        val probeExt = MediaExtractor()
        probeExt.setDataSource(context, audioUri, null)
        val probeTrack = (0 until probeExt.trackCount).firstOrNull { i ->
            probeExt.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        val probeMime = probeTrack?.let { probeExt.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
        probeExt.release()

        val needsTranscode = probeMime != null && probeMime !in DIRECTLY_MUXABLE_AUDIO
        if (needsTranscode) {
            // MP3, FLAC, WAV, etc. → transcodificar a AAC-LC antes de muxear.
            // La transcodificación ocupa el primer 50% del progreso del remux.
            transcodeAudio(audioUri, audioAacTemp, videoDurationUs) { p ->
                onProgress(p * 0.5f)
            }
        }

        // Extraer video del temporal
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFile.path)
        val videoTrackIdx = (0 until videoExtractor.trackCount).firstOrNull { i ->
            videoExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: error(
            "El archivo de video temporal no tiene pistas válidas. " +
            "Verificá que haya al menos ${videoFile.length() * 2 / 1_000_000} MB libres de almacenamiento."
        )
        videoExtractor.selectTrack(videoTrackIdx)

        // Abrir audio: desde el AAC transcodificado o desde el original según corresponda
        val audioExtractor = MediaExtractor()
        val audioTrackIdx: Int?
        val audioFormat: MediaFormat?
        if (needsTranscode && audioAacTemp.exists() && audioAacTemp.length() > 0) {
            audioExtractor.setDataSource(audioAacTemp.path)
            audioTrackIdx = if (audioExtractor.trackCount > 0) 0 else null
        } else if (!needsTranscode) {
            audioExtractor.setDataSource(context, audioUri, null)
            audioTrackIdx = (0 until audioExtractor.trackCount).firstOrNull { i ->
                audioExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } else {
            audioTrackIdx = null
        }
        audioFormat = audioTrackIdx?.let { audioExtractor.getTrackFormat(it) }
        audioTrackIdx?.let { audioExtractor.selectTrack(it) }

        val muxer         = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxVideoTrack = muxer.addTrack(videoFormat)
        val muxAudioTrack = audioFormat?.let {
            try { muxer.addTrack(it) } catch (_: Exception) { -1 }
        } ?: -1
        muxer.start()

        val buffer = ByteBuffer.allocate(10 * 1024 * 1024)  // 10 MB cubre I-frames grandes
        val info   = MediaCodec.BufferInfo()

        // Copiar video con PTS normalizados a base-0
        var videoFrameIdx = 0L
        while (true) {
            val size = videoExtractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0; info.size = size
            info.presentationTimeUs = videoFrameIdx * 1_000_000L / fps
            info.flags  = videoExtractor.sampleFlags
            muxer.writeSampleData(muxVideoTrack, buffer, info)
            videoExtractor.advance()
            videoFrameIdx++
        }
        videoExtractor.release()

        // Copiar audio cortado a la duración del video.
        // Si hubo transcodificación, el progreso empieza desde 50% (transcode ocupó 0→50%).
        val audioCopyStart = if (needsTranscode) 0.5f else 0f
        if (muxAudioTrack >= 0) {
            while (true) {
                val size = audioExtractor.readSampleData(buffer, 0)
                if (size < 0) break
                val pts = audioExtractor.sampleTime
                if (pts > videoDurationUs) break
                info.offset = 0; info.size = size
                info.presentationTimeUs = pts
                info.flags  = audioExtractor.sampleFlags
                muxer.writeSampleData(muxAudioTrack, buffer, info)
                audioExtractor.advance()
                val copyProgress = (pts.toFloat() / videoDurationUs).coerceIn(0f, 1f)
                onProgress(audioCopyStart + copyProgress * (1f - audioCopyStart))
            }
        }
        audioExtractor.release()

        muxer.stop()
        muxer.release()
    }

    /**
     * Transcodifica el audio en [srcUri] a AAC-LC 192 kbps y lo guarda en [destFile] (M4A).
     *
     * Pipeline: MediaExtractor → MediaCodec decoder (formato original → PCM 16-bit)
     *                          → MediaCodec encoder (PCM → AAC-LC)
     *                          → MediaMuxer → archivo M4A
     *
     * Necesario para formatos no soportados por MediaMuxer en MP4: MP3, FLAC, WAV, OGG, etc.
     */
    private fun transcodeAudio(
        srcUri: Uri,
        destFile: File,
        maxDurationUs: Long,
        onProgress: (Float) -> Unit = {}
    ) {
        val srcExtractor = MediaExtractor()
        srcExtractor.setDataSource(context, srcUri, null)
        val srcTrackIdx = (0 until srcExtractor.trackCount).first { i ->
            srcExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        srcExtractor.selectTrack(srcTrackIdx)
        val srcFormat  = srcExtractor.getTrackFormat(srcTrackIdx)
        val srcMime    = srcFormat.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = srcFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels   = if (srcFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            srcFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        // Decoder: formato original → PCM 16-bit LE
        val decoder = MediaCodec.createDecoderByType(srcMime)
        decoder.configure(srcFormat, null, null, 0)
        decoder.start()

        // Encoder: PCM → AAC-LC 192 kbps
        val aacFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(destFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxTrackIdx  = -1
        var muxerStarted = false

        val decInfo = MediaCodec.BufferInfo()
        val encInfo = MediaCodec.BufferInfo()
        var decInputDone  = false
        var decOutputDone = false
        var encInputDone  = false   // EOS ya enviado al encoder → no enviar más input
        var encOutputDone = false

        // Cola de chunks PCM entre decoder y encoder
        val pcmQueue          = ArrayDeque<ByteArray>()
        var pcmOffset         = 0       // offset dentro de pcmQueue.first()
        var encPtsUs          = 0L      // PTS acumulado para el encoder
        val bytesPerPcmSample = 2 * channels  // 16-bit × N canales

        try {
            while (!encOutputDone) {

                // 1. Extractor → decoder input
                if (!decInputDone) {
                    val idx = decoder.dequeueInputBuffer(10_000L)
                    if (idx >= 0) {
                        val buf  = decoder.getInputBuffer(idx)!!
                        val size = srcExtractor.readSampleData(buf, 0)
                        if (size < 0 || srcExtractor.sampleTime > maxDurationUs) {
                            decoder.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decInputDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, size, srcExtractor.sampleTime, 0)
                            srcExtractor.advance()
                        }
                    }
                }

                // 2. Decoder output → pcmQueue (10ms timeout: evita busy-wait cuando el decoder
                //    está procesando su último bloque después de recibir EOS)
                if (!decOutputDone) {
                    val idx = decoder.dequeueOutputBuffer(decInfo, 10_000L)
                    if (idx >= 0) {
                        if (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decOutputDone = true
                        if (decInfo.size > 0) {
                            val buf   = decoder.getOutputBuffer(idx)!!
                            val bytes = ByteArray(decInfo.size)
                            buf.position(decInfo.offset)
                            buf.get(bytes)
                            pcmQueue.addLast(bytes)
                        }
                        decoder.releaseOutputBuffer(idx, false)
                    }
                }

                // 3. pcmQueue → encoder input
                // encInputDone evita enviar EOS múltiples veces: mandar EOS dos veces puede
                // dejar el encoder en un estado inconsistente y congelar el loop.
                if (!encInputDone && (pcmQueue.isNotEmpty() || (decOutputDone && pcmQueue.isEmpty()))) {
                    val idx = encoder.dequeueInputBuffer(10_000L)
                    if (idx >= 0) {
                        val encBuf = encoder.getInputBuffer(idx)!!
                        encBuf.clear()
                        if (pcmQueue.isNotEmpty()) {
                            // Llenar el buffer del encoder con chunks PCM disponibles
                            var written = 0
                            while (pcmQueue.isNotEmpty() && written < encBuf.capacity()) {
                                val chunk   = pcmQueue.first()
                                val avail   = chunk.size - pcmOffset
                                val toWrite = minOf(avail, encBuf.capacity() - written)
                                encBuf.put(chunk, pcmOffset, toWrite)
                                written   += toWrite
                                pcmOffset += toWrite
                                if (pcmOffset >= chunk.size) {
                                    pcmQueue.removeFirst(); pcmOffset = 0
                                }
                            }
                            encoder.queueInputBuffer(idx, 0, written, encPtsUs, 0)
                            encPtsUs += (written / bytesPerPcmSample) * 1_000_000L / sampleRate
                        } else {
                            // Decoder terminó y la cola está vacía → señalar EOS al encoder (una sola vez)
                            encoder.queueInputBuffer(idx, 0, 0, encPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            encInputDone = true
                        }
                    }
                }

                // 4. Encoder output → muxer
                val idx = encoder.dequeueOutputBuffer(encInfo, 10_000L)
                when {
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxTrackIdx  = muxer.addTrack(encoder.outputFormat)
                        muxer.start(); muxerStarted = true
                    }
                    idx >= 0 -> {
                        if (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encOutputDone = true
                        val isConfig = encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (encInfo.size > 0 && muxerStarted && !isConfig) {
                            val encBuf = encoder.getOutputBuffer(idx)!!
                            encBuf.position(encInfo.offset)
                            encBuf.limit(encInfo.offset + encInfo.size)
                            muxer.writeSampleData(muxTrackIdx, encBuf, encInfo)
                            // Reportar progreso basado en PTS del audio codificado
                            if (maxDurationUs > 0) {
                                onProgress((encInfo.presentationTimeUs.toFloat() / maxDurationUs).coerceIn(0f, 1f))
                            }
                        }
                        encoder.releaseOutputBuffer(idx, false)
                    }
                }
            }
        } finally {
            try { decoder.stop();  decoder.release()  } catch (_: Exception) {}
            try { encoder.stop();  encoder.release()  } catch (_: Exception) {}
            try { srcExtractor.release()              } catch (_: Exception) {}
            try { if (muxerStarted) muxer.stop()      } catch (_: Exception) {}
            try { muxer.release()                     } catch (_: Exception) {}
        }
    }

    private fun saveToGallery(file: File): Uri {
        val safe = outputName.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim().ifBlank { "AudioVisual" }
        val displayName = "${safe}_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/IluminadorDeAudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver
            .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo crear entrada en MediaStore")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().copyTo(out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        return uri
    }
}
