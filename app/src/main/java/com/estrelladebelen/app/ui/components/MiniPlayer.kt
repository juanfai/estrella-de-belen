package com.estrelladebelen.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estrelladebelen.app.R
import com.estrelladebelen.app.ui.screens.player.PlayerUiState
import com.estrelladebelen.app.ui.theme.LavenderPrimary
import com.estrelladebelen.app.ui.theme.LavenderSurface
import com.estrelladebelen.app.ui.theme.PurpleTextPrimary

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onPlayerClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.meditation != null,
        enter = slideInVertically { it },
        exit  = slideOutVertically { it },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LavenderSurface)
                .clickable(onClick = onPlayerClick)
        ) {
            // Progress bar behind content
            if (state.progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.progressFraction)
                        .height(56.dp)
                        .background(LavenderPrimary.copy(alpha = 0.12f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.meditation?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = PurpleTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.player_now_playing),
                        style = MaterialTheme.typography.bodySmall,
                        color = PurpleTextPrimary.copy(alpha = 0.55f)
                    )
                }

                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying)
                            stringResource(R.string.player_pause)
                        else
                            stringResource(R.string.player_play),
                        tint = LavenderPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
