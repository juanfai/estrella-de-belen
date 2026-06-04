package com.example.iluminadordeaudio.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null

    fun start(): File {
        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        recorder = build().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        return file
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { recorder?.pause() } catch (_: Exception) {}
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { recorder?.resume() } catch (_: Exception) {}
        }
    }

    fun getMaxAmplitude(): Int = recorder?.maxAmplitude ?: 0

    fun stop() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
    }

    fun cancel(file: File) {
        stop()
        file.delete()
    }

    private fun build() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }
}
