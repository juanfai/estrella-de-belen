package com.estrelladebelen.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estrelladebelen.app.R
import com.estrelladebelen.app.data.model.Meditation
import com.estrelladebelen.app.ui.theme.LavenderSurface
import com.estrelladebelen.app.ui.theme.LavenderTextSecond

@Composable
fun MeditationCard(
    meditation: Meditation,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haloInt     = runCatching { android.graphics.Color.parseColor(meditation.haloColor) }.getOrDefault(0xFF5E1FFF.toInt())
    val glowCompose = Color.White.copy(alpha = 0.85f)
    val haloCompose = Color(haloInt).copy(alpha = 0.9f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LavenderSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Glow color preview circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(glowCompose, haloCompose))
                    )
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = meditation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (meditation.isNew) {
                    NewBadge()
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.card_duration, meditation.durationMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = LavenderTextSecond
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (isFavorite) Color(haloInt) else LavenderTextSecond,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = stringResource(R.string.action_download),
                        tint = if (isDownloaded) Color(haloInt) else LavenderTextSecond,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Text(
            text = stringResource(R.string.badge_new),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
