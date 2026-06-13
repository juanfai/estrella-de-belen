package com.estrelladebelen.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.estrelladebelen.app.R
import com.estrelladebelen.app.data.model.Meditation
import com.estrelladebelen.app.ui.components.MeditationCard
import com.estrelladebelen.app.ui.theme.*
import java.util.Calendar

@Composable
fun HomeScreen(
    userName: String,
    favorites: List<String>,
    downloads: List<String>,
    onMeditationClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(LavenderBackground)
    ) {
        // Header
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                Text(
                    text = greeting(userName),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light,
                    color = PurpleTextPrimary
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LavenderTextSecond
                )
            }
        }

        // Featured meditation
        uiState.featured?.let { featured ->
            item(span = { GridItemSpan(2) }) {
                FeaturedCard(
                    meditation = featured,
                    onClick = { onMeditationClick(featured.id) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // Duration filter chips
        item(span = { GridItemSpan(2) }) {
            DurationFilterRow(
                activeFilter = uiState.activeFilter,
                onFilterSelected = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        if (uiState.isLoading) {
            item(span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LavenderPrimary)
                }
            }
        } else {
            itemsIndexed(uiState.meditations) { index, meditation ->
                val paddingStart = if (index % 2 == 0) 20.dp else 0.dp
                val paddingEnd   = if (index % 2 != 0) 20.dp else 0.dp
                MeditationCard(
                    meditation    = meditation,
                    isFavorite    = meditation.id in favorites,
                    isDownloaded  = meditation.id in downloads,
                    onClick       = { onMeditationClick(meditation.id) },
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
    val haloColor = runCatching { android.graphics.Color.parseColor(meditation.haloColor) }.getOrDefault(0xFF5E1FFF.toInt())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(haloColor).copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_featured_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = meditation.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
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
            FilterChip(
                selected = activeFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = stringResource(filter.labelRes()),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LavenderPrimary,
                    selectedLabelColor = Color.White
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
