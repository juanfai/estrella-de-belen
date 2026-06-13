package com.estrelladebelen.app.ui.screens.player

import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.estrelladebelen.app.R
import com.estrelladebelen.app.render.GlowPreviewRenderer
import com.estrelladebelen.app.ui.theme.PlayerBackground
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    meditationId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var controlsVisible by remember { mutableStateOf(false) }
    val glowRenderer = remember { GlowPreviewRenderer() }

    // Keep screen on while player is active
    DisposableEffect(Unit) {
        val window = (context as? ComponentActivity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Load meditation
    LaunchedEffect(meditationId) {
        viewModel.loadMeditation(context, meditationId)
    }

    // Start breathing animation when screen first opens (before audio starts)
    LaunchedEffect(Unit) {
        viewModel.startBreathingIfIdle()
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3_000)
            controlsVisible = false
        }
    }

    // Drive the glow renderer from ViewModel state
    LaunchedEffect(uiState.glowAmplitude, uiState.haloAmplitude) {
        glowRenderer.drawFrame(
            uiState.glowAmplitude,
            uiState.haloAmplitude,
            uiState.stretchY,
            uiState.glowColor,
            uiState.haloColor
        )
    }

    DisposableEffect(Unit) {
        onDispose { glowRenderer.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerBackground)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { controlsVisible = !controlsVisible }
    ) {
        // Full-screen GL animation
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = glowRenderer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls overlay — only visible on tap
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit  = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControls(
                uiState = uiState,
                onBack = onBack,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo
            )
        }
    }
}

@Composable
private fun PlayerControls(
    uiState: PlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = Color.White
            )
        }

        // Title
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = uiState.meditation?.title ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
            uiState.meditation?.description?.let { desc ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }

        // Center play/pause button
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
        ) {
            Icon(
                imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (uiState.isPlaying)
                    stringResource(R.string.player_pause)
                else
                    stringResource(R.string.player_play),
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
        }

        // Bottom controls: seek bar + time
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(uiState.positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = formatTime(uiState.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Slider(
                value = uiState.progressFraction,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
