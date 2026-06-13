package com.estrelladebelen.app.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.estrelladebelen.app.R
import com.estrelladebelen.app.data.local.entity.DownloadedMeditation
import com.estrelladebelen.app.render.VisualConfig
import com.estrelladebelen.app.ui.theme.Fog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayClick: (meditationId: String) -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Estado para el diálogo de confirmación
    var meditationToDelete by remember { mutableStateOf<DownloadedMeditation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_downloads)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        // Mostrar Diálogo si hay una meditación seleccionada para borrar
        meditationToDelete?.let { meditation ->
            AlertDialog(
                onDismissRequest = { meditationToDelete = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.removeDownload(context, meditation)
                            meditationToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { meditationToDelete = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                title = { Text(stringResource(R.string.delete_dialog_title)) },
                text = { Text(stringResource(R.string.delete_dialog_message, meditation.title)) }
            )
        }

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize() // Ocupa todo el espacio disponible bajo la TopAppBar
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp), // Margen a los costados para que el texto no toque los bordes
                contentAlignment = Alignment.Center // Centro perfecto tanto horizontal como vertical
            ) {
                Column(
                    modifier = Modifier.offset(y = (-20).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.downloads_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center // Asegura que el texto largo se centre
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.downloads_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center // Asegura que el subtítulo se centre
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // La animación "animateItem" requiere que los items tengan una Key única
                items(
                    items = downloads,
                    key = { it.meditationId }
                ) { item ->
                    Box(modifier = Modifier.animateItem(
                        fadeOutSpec = tween(300),
                        placementSpec = tween(300)
                    )) {
                        DownloadItem(
                            item = item,
                            onPlayClick = { onPlayClick(item.meditationId) },
                            onDeleteClick = { meditationToDelete = item }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(
    item: DownloadedMeditation,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val haloColor = runCatching { Color(android.graphics.Color.parseColor(item.haloColor)) }
        .getOrDefault(Color(VisualConfig.DEFAULT_HALO_COLOR))
    val minutes = item.durationSeconds / 60

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ... (Resto de tu diseño de Box y Column igual que antes)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(haloColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(haloColor)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.card_duration, minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onPlayClick) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.player_play),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = Fog,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}