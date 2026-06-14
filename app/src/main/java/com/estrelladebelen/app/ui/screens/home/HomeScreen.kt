package com.estrelladebelen.app.ui.screens.home
import androidx.compose.ui.tooling.preview.Preview
import com.estrelladebelen.app.ui.theme.EstrellaDeBelénTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.estrelladebelen.app.R
import com.estrelladebelen.app.data.model.Meditation
import com.estrelladebelen.app.ui.components.MeditationCard
import java.util.Calendar

@Composable
fun HomeScreen(
    userName: String,
    favorites: List<String>,
    onMeditationClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    HomeScreenContent(
        uiState           = uiState,
        userName          = userName,
        favorites         = favorites,
        onMeditationClick = onMeditationClick,
        onFavoriteClick   = onFavoriteClick,
        onDownloadClick   = { id -> viewModel.downloadMeditation(context, id) },
        onFilterSelected  = viewModel::setFilter
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    userName: String,
    favorites: List<String>,
    onMeditationClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
    onFilterSelected: (DurationFilter) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp)) {
                Text(
                    text = greeting(userName),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        uiState.featured?.let { featured ->
            item(span = { GridItemSpan(2) }) {
                FeaturedCard(
                    meditation = featured,
                    onClick = { onMeditationClick(featured.id) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        item(span = { GridItemSpan(2) }) {
            DurationFilterRow(
                activeFilter = uiState.activeFilter,
                onFilterSelected = onFilterSelected,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        if (uiState.isLoading) {
            item(span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            itemsIndexed(uiState.meditations) { index, meditation ->
                val paddingStart = if (index % 2 == 0) 20.dp else 0.dp
                val paddingEnd   = if (index % 2 != 0) 20.dp else 0.dp
                MeditationCard(
                    meditation      = meditation,
                    isFavorite      = meditation.id in favorites,
                    isDownloaded    = meditation.id in uiState.downloads,
                    isDownloading   = meditation.id in uiState.downloadingIds,
                    onClick         = { onMeditationClick(meditation.id) },
                    onFavoriteClick = { onFavoriteClick(meditation.id) },
                    onDownloadClick = { onDownloadClick(meditation.id) },
                    modifier = Modifier.padding(start = paddingStart, end = paddingEnd)
                )
            }
        }
    }
}

@Composable
private fun FeaturedCard(meditation: Meditation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(180.dp),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (meditation.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = meditation.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = stringResource(R.string.home_featured_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meditation.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.card_duration, meditation.durationMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun DurationFilterRow(
    activeFilter: DurationFilter,
    onFilterSelected: (DurationFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        DurationFilter.entries.forEach { filter ->
            val selected = activeFilter == filter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = stringResource(filter.labelRes()),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

private fun DurationFilter.labelRes(): Int = when (this) {
    DurationFilter.ALL    -> R.string.filter_all
    DurationFilter.SHORT  -> R.string.filter_5min
    DurationFilter.MEDIUM -> R.string.filter_10min
    DurationFilter.LONG   -> R.string.filter_20min
}

private fun greeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val salutation = when (hour) {
        in 6..12  -> "Buenos días"
        in 13..19 -> "Buenas tardes"
        else      -> "Buenas noches"
    }
    return if (name.isNotBlank()) "$salutation, $name" else salutation
}

// ─── Preview ───────────────────────────────────────────────

private val previewMeditations = listOf(
    Meditation(id = "1", title = "Paz interior",     category = "paz",   durationSeconds = 300,  haloColor = "#7C3AED", createdAt = System.currentTimeMillis()),
    Meditation(id = "2", title = "Sueño profundo",   category = "sueño", durationSeconds = 600,  haloColor = "#3B82F6"),
    Meditation(id = "3", title = "Concentración",    category = "foco",  durationSeconds = 1200, haloColor = "#10B981"),
    Meditation(id = "4", title = "Relajación total", category = "paz",   durationSeconds = 900,  haloColor = "#F97316"),
)

private val previewUiState = HomeUiState(
    isLoading   = false,
    meditations = previewMeditations,
    featured    = previewMeditations.first(),
    downloads   = listOf("2"),
    downloadingIds = setOf("3")
)

@Preview(showBackground = true, showSystemUi = true, name = "Home")
@Composable
private fun HomeScreenPreview() {
    EstrellaDeBelénTheme {
        HomeScreenContent(
            uiState           = previewUiState,
            userName          = "Juan",
            favorites         = listOf("1"),
            onMeditationClick = {},
            onFavoriteClick   = {},
            onDownloadClick   = {},
            onFilterSelected  = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home - cargando")
@Composable
private fun HomeScreenLoadingPreview() {
    EstrellaDeBelénTheme {
        HomeScreenContent(
            uiState           = HomeUiState(isLoading = true),
            userName          = "Juan",
            favorites         = emptyList(),
            onMeditationClick = {},
            onFavoriteClick   = {},
            onDownloadClick   = {},
            onFilterSelected  = {}
        )
    }
}
