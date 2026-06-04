package com.example.iluminadordeaudio.audio

import android.media.MediaPlayer
import java.io.File

class AudioPlayer {
    private var player: MediaPlayer? = null

    fun play(file: File, startFraction: Float = 0f, onComplete: () -> Unit) {
        release()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { onComplete() }
                if (startFraction > 0f && duration > 0) {
                    seekTo((startFraction * duration).toInt())
                }
                start()
            }
        } catch (e: Exception) {
            player?.release(); player = null
        }
    }

    fun pause()  { player?.pause() }
    fun resume() { player?.start() }

    fun seekTo(fraction: Float) {
        val dur = player?.duration?.takeIf { it > 0 } ?: return
        player?.seekTo((fraction * dur).toInt())
    }

    val currentPositionFraction: Float get() {
        val p = player ?: return 0f
        val dur = p.duration.takeIf { it > 0 } ?: return 0f
        return (p.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
    }

    fun release() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
    }
}
