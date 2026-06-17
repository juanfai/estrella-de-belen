package com.estrelladebelen.app.ui.screens.player

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.estrelladebelen.app.audio.AudioDecoder
import com.estrelladebelen.app.data.model.Meditation
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.data.repository.MeditationRepository
import com.estrelladebelen.app.render.VisualConfig
import com.estrelladebelen.app.service.MediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val isDownloaded: Boolean = false,
    val playbackEnded: Boolean = false
) {
    val progressFraction: Float
        get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}

private const val RMS_FPS = 30

class PlayerViewModel : ViewModel() {

    private val repository: MeditationRepository = AppContainer.meditationRepository
    private val userRepo                         = AppContainer.userRepository

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var animationJob: Job? = null
    private var progressJob: Job? = null
    private var decodeJob: Job? = null
    private var smoothedGlow  = 0f
    private var smoothedHalo  = 0f
    private var breathingTime = 0f

    @Volatile private var rmsFrames: FloatArray? = null

    // True only after STATE_READY is received for the current session.
    // Guards against media3 replaying STATE_ENDED / isPlaying=false from the
    // previous session to a newly registered listener.
    private var sessionReady = false

    fun loadMeditation(context: Context, meditationId: String) {
        // Synchronous cleanup: cancel all jobs and release the old controller
        // so its listener can't fire events that would cancel the new animation.
        sessionReady = false
        animationJob?.cancel();  animationJob = null
        progressJob?.cancel();   progressJob  = null
        decodeJob?.cancel();     decodeJob    = null
        rmsFrames    = null
        smoothedGlow = 0f
        smoothedHalo = 0f
        breathingTime = 0f
        controller?.removeListener(playerListener)
        controller?.release();   controller = null
        controllerFuture?.cancel(true); controllerFuture = null

        viewModelScope.launch {
            val meditation = repository.getById(meditationId) ?: return@launch
            val haloColor = runCatching { VisualConfig.parseColor(meditation.haloColor) }
                .getOrDefault(VisualConfig.DEFAULT_HALO_COLOR)
            _uiState.value = _uiState.value.copy(
                meditation    = meditation,
                positionMs    = 0L,
                durationMs    = meditation.durationSeconds * 1000L,
                glowAmplitude = 0f,
                haloAmplitude = 0f,
                stretchY      = 1f,
                glowColor     = VisualConfig.GLOW_COLOR,
                haloColor     = haloColor,
                playbackEnded = false
            )
            connectService(context, meditation)
        }
    }

    private fun connectService(context: Context, meditation: Meditation) {
        if (meditation.audioUrl.isNotBlank()) startDecoding(context, meditation.audioUrl)

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
                startBreathingAnimation()
            }
        }, { command -> command.run() })
    }

    private fun startDecoding(context: Context, audioUrl: String) {
        decodeJob?.cancel()
        rmsFrames = null
        decodeJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val (frames, _) = AudioDecoder().decodeToRms(context, Uri.parse(audioUrl), fps = RMS_FPS)
                rmsFrames = frames
                if (_uiState.value.isPlaying) {
                    withContext(Dispatchers.Main) { startAudioDrivenAnimation() }
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                if (rmsFrames != null) startAudioDrivenAnimation()
                // else: still decoding — startDecoding() will call startAudioDrivenAnimation() when ready
            } else if (sessionReady) {
                // Only cancel animation on a real pause/stop, not on the spurious
                // isPlaying=false that media3 delivers when a new listener is registered
                // while the previous session's ExoPlayer is still in STATE_ENDED.
                animationJob?.cancel()
            }
        }
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    sessionReady = true
                    _uiState.value = _uiState.value.copy(
                        durationMs = controller?.duration?.takeIf { it > 0 }
                            ?: _uiState.value.durationMs
                    )
                    startProgressTracking()
                }
                Player.STATE_ENDED -> {
                    if (!sessionReady) return  // Stale ENDED from previous session — ignore.
                    sessionReady = false
                    animationJob?.cancel()
                    val durationMinutes = (_uiState.value.durationMs / 60_000L).toInt().coerceAtLeast(1)
                    val finishedId = _uiState.value.meditation?.id
                    _uiState.value = _uiState.value.copy(
                        meditation    = null,
                        isPlaying     = false,
                        playbackEnded = true
                    )
                    viewModelScope.launch {
                        userRepo.recordSession(durationMinutes)
                        finishedId?.let { userRepo.markAsSeen(it) }
                    }
                }
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
        progressJob?.cancel()  // position is updated in this loop at 60fps instead
        val frames = rmsFrames ?: return

        animationJob = viewModelScope.launch {
            while (isActive) {
                val posMs = controller?.currentPosition ?: 0L
                val idx   = ((posMs / 1000.0) * RMS_FPS).toInt().coerceIn(0, frames.size - 1)
                val raw   = (frames[idx] * VisualConfig.SENSITIVITY).coerceIn(0f, 1f)

                smoothedGlow += (raw - smoothedGlow) *
                    if (raw > smoothedGlow) VisualConfig.GLOW_ATTACK else VisualConfig.GLOW_DECAY
                smoothedHalo += (raw * 0.85f - smoothedHalo) *
                    if (raw * 0.85f > smoothedHalo) VisualConfig.HALO_ATTACK else VisualConfig.HALO_DECAY

                val stretch = if (smoothedGlow > VisualConfig.STRETCH_THRESHOLD)
                    1f + (smoothedGlow - VisualConfig.STRETCH_THRESHOLD) * VisualConfig.STRETCH_FACTOR
                else 1f

                _uiState.value = _uiState.value.copy(
                    positionMs    = posMs,
                    glowAmplitude = smoothedGlow,
                    haloAmplitude = smoothedHalo,
                    stretchY      = stretch
                )
                delay(16L)
            }
        }
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

    fun stopPlayback() {
        animationJob?.cancel()
        progressJob?.cancel()
        controller?.stop()  // reset ExoPlayer to IDLE so next session starts clean
    }

    override fun onCleared() {
        animationJob?.cancel()
        progressJob?.cancel()
        decodeJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        controllerFuture?.cancel(true)
    }
}
