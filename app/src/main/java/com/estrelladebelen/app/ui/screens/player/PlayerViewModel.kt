package com.estrelladebelen.app.ui.screens.player

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.estrelladebelen.app.data.model.Meditation
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.data.repository.MeditationRepository
import com.estrelladebelen.app.render.VisualConfig
import com.estrelladebelen.app.service.MediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

data class PlayerUiState(
    val meditation: Meditation? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val glowAmplitude: Float = 0f,
    val haloAmplitude: Float = 0f,
    val stretchY: Float = 1f,
    val glowColor: Int = VisualConfig.GLOW_COLOR,
    val haloColor: Int = VisualConfig.DEFAULT_HALO_COLOR,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false
) {
    val progressFraction: Float
        get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}

class PlayerViewModel : ViewModel() {

    private val repository: MeditationRepository = AppContainer.meditationRepository

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var animationJob: Job? = null
    private var progressJob: Job? = null
    private var smoothedGlow  = 0f
    private var smoothedHalo  = 0f
    private var breathingTime = 0f

    fun loadMeditation(context: Context, meditationId: String) {
        viewModelScope.launch {
            val meditation = repository.getById(meditationId) ?: return@launch
            val haloColor = runCatching { VisualConfig.parseColor(meditation.haloColor) }
                .getOrDefault(VisualConfig.DEFAULT_HALO_COLOR)
            _uiState.value = _uiState.value.copy(
                meditation = meditation,
                glowColor  = VisualConfig.GLOW_COLOR,
                haloColor  = haloColor,
                durationMs = meditation.durationSeconds * 1000L
            )
            connectService(context, meditation)
        }
    }

    private fun connectService(context: Context, meditation: Meditation) {
        val token = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)
            if (meditation.audioUrl.isNotBlank()) {
                controller?.setMediaItem(MediaItem.fromUri(meditation.audioUrl))
                controller?.prepare()
                controller?.play()
            } else {
                // No audio URL yet (stub) — run breathing animation only
                startBreathingAnimation()
            }
        }, { command -> command.run() })
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            if (isPlaying) startAudioDrivenAnimation() else animationJob?.cancel()
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                _uiState.value = _uiState.value.copy(
                    durationMs = controller?.duration?.takeIf { it > 0 }
                        ?: _uiState.value.durationMs
                )
                startProgressTracking()
            }
        }
    }

    fun togglePlayPause() {
        val ctrl = controller
        if (ctrl == null) {
            // Stub mode: toggle breathing animation
            val playing = !_uiState.value.isPlaying
            _uiState.value = _uiState.value.copy(isPlaying = playing)
            if (playing) startBreathingAnimation() else animationJob?.cancel()
            return
        }
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekTo(fraction: Float) {
        val durationMs = _uiState.value.durationMs
        if (durationMs > 0) controller?.seekTo((fraction * durationMs).toLong())
    }

    // Breathing animation used when there is no audio (stub) or as idle animation
    private fun startBreathingAnimation() {
        animationJob?.cancel()
        _uiState.value = _uiState.value.copy(isPlaying = true)
        animationJob = viewModelScope.launch {
            while (isActive) {
                breathingTime += 0.018f
                val target = (sin(breathingTime.toDouble()) * 0.35f + 0.45f).toFloat().coerceIn(0f, 1f)
                smoothedGlow = smoothedGlow + (target - smoothedGlow) * VisualConfig.GLOW_ATTACK
                smoothedHalo = smoothedHalo + (target * 0.85f - smoothedHalo) * VisualConfig.HALO_ATTACK

                val stretch = if (smoothedGlow > VisualConfig.STRETCH_THRESHOLD)
                    1f + (smoothedGlow - VisualConfig.STRETCH_THRESHOLD) * VisualConfig.STRETCH_FACTOR
                else 1f

                _uiState.value = _uiState.value.copy(
                    glowAmplitude = smoothedGlow,
                    haloAmplitude = smoothedHalo,
                    stretchY = stretch
                )
                delay(16L)
            }
        }
    }

    private fun startAudioDrivenAnimation() {
        animationJob?.cancel()
        // TODO: use pre-computed RMS frames from Firestore synchronized to playback position.
        // For now, fall back to breathing animation.
        startBreathingAnimation()
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val pos = controller?.currentPosition ?: 0L
                _uiState.value = _uiState.value.copy(positionMs = pos)
                delay(500L)
            }
        }
    }

    fun startBreathingIfIdle() {
        if (!_uiState.value.isPlaying) startBreathingAnimation()
    }

    override fun onCleared() {
        animationJob?.cancel()
        progressJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        controllerFuture?.cancel(true)
    }
}
