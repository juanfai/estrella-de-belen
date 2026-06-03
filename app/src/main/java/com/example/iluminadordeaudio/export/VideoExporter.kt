package com.example.iluminadordeaudio.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.iluminadordeaudio.audio.AudioAnalyzer
import com.example.iluminadordeaudio.audio.AudioDecoder
import com.example.iluminadordeaudio.render.GlowRenderer
import java.io.File
import java.nio.ByteBuffer

/**
 * Pipeline de exportación en dos pasos:
 *  1. Render + encode de video → archivo temporal de video-only
 *  2. Remux: video temporal + audio original → MP4 final en galería
 *
 * Progreso 0..0.7 → encode de video
 * Progreso 0.7..1 → copia de audio
 */
class VideoExporter(
    private val context: Context,
    private val audioUri: Uri,
    private val bgColor: Int = android.graphics.Color.BLACK,
    private val glowColor: Int = android.graphics.Color.WHITE,
    private val fps: Int = 30,
    private val width: Int = 1080,
    private val height: Int = 1920,
    private val outputName: String = "AudioVisual"
) {

    fun export(onProgress: (Float) -> Unit): Uri {
        val decodeResult = AudioDecoder().decodeToPcm(context, audioUri)
        val rmsFrames = AudioAnalyzer().rmsPerFrame(decodeResult.pcm, decodeResult.sampleRate, fps)
        val totalFrames = rmsFrames.size
        val videoDurationUs = totalFrames * 1_000_000L / fps

        // Paso 1: codificar video a archivo temporal
        val videoTemp = File(context.cacheDir, "video_temp_${System.currentTimeMillis()}.mp4")
        encodeVideo(rmsFrames, videoTemp) { frame ->
            onProgress(frame.toFloat() / totalFrames * 0.7f)
        }

        // Paso 2: remuxear video + audio
        val outputFile = File(context.cacheDir, "output_${System.currentTimeMillis()}.mp4")
        remuxVideoAndAudio(videoTemp, videoDurationUs, outputFile) { audioProgress ->
            onProgress(0.7f + audioProgress * 0.3f)
        }

        videoTemp.delete()
        return saveToGallery(outputFile).also { outputFile.delete() }
    }

    private fun encodeVideo(rmsFrames: FloatArray, outputFile: File, onFrame: (Int) -> Unit) {
        val encoder = VideoEncoder(width, height, fps, outputFile)
        val renderer = GlowRenderer()

        // El mismo ataque/decaimiento del preview para que el video exportado sea idéntico
        var smoothedAmp = 0f
        rmsFrames.forEachIndexed { i, targetAmp ->
            val factor = if (targetAmp >= smoothedAmp) 0.35f else 0.07f
            smoothedAmp += (targetAmp - smoothedAmp) * factor

            val canvas = encoder.inputSurface.lockCanvas(null)
            renderer.drawFrame(canvas, smoothedAmp, bgColor, glowColor)
            encoder.inputSurface.unlockCanvasAndPost(canvas)
            encoder.drainEncoder(false)
            onFrame(i)
        }

        encoder.drainEncoder(true)
        encoder.release()
    }

    private fun remuxVideoAndAudio(
        videoFile: File,
        videoDurationUs: Long,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        val muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Extraer pista de video del temporal
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFile.path)
        val videoTrackIdx = (0 until videoExtractor.trackCount).first { i ->
            videoExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                ?.startsWith("video/") == true
        }
        videoExtractor.selectTrack(videoTrackIdx)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackIdx)

        // Extraer pista de audio del archivo original
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(context, audioUri, null)
        val audioTrackIdx = (0 until audioExtractor.trackCount).firstOrNull { i ->
            audioExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }
        val audioFormat = audioTrackIdx?.let { audioExtractor.getTrackFormat(it) }
        audioTrackIdx?.let { audioExtractor.selectTrack(it) }

        // Registrar tracks y arrancar muxer
        val muxVideoTrack = muxer.addTrack(videoFormat)
        val muxAudioTrack = audioFormat?.let { muxer.addTrack(it) } ?: -1
        muxer.start()

        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()

        // Copiar video
        while (true) {
            val size = videoExtractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = videoExtractor.sampleTime
            info.flags = videoExtractor.sampleFlags
            muxer.writeSampleData(muxVideoTrack, buffer, info)
            videoExtractor.advance()
        }
        videoExtractor.release()

        // Copiar audio, cortado a la duración del video
        if (audioTrackIdx != null && muxAudioTrack >= 0) {
            while (true) {
                val size = audioExtractor.readSampleData(buffer, 0)
                if (size < 0) break
                val pts = audioExtractor.sampleTime
                if (pts > videoDurationUs) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = pts
                info.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxAudioTrack, buffer, info)
                audioExtractor.advance()
                onProgress((pts.toFloat() / videoDurationUs).coerceIn(0f, 1f))
            }
        }
        audioExtractor.release()

        muxer.stop()
        muxer.release()
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
