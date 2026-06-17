package com.estrelladebelen.app.ui.screens.home
import androidx.compose.ui.tooling.preview.Preview
import com.estrelladebelen.app.ui.theme.EstrellaDeBelénTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
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
    onPaywallClick: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    HomeScreenContent(
        uiState            = uiState,
        userName           = userName,
        favorites          = favorites,
        onMeditationClick  = onMeditationClick,
        onFavoriteClick    = onFavoriteClick,
        onDownloadClick    = { id -> viewModel.downloadMeditation(context, id) },
        onFilterSelected   = viewModel::setFilter,
        onCategorySelected = viewModel::setCategory,
        onPaywallClick     = onPaywallClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    userName: String,
    favorites: List<String>,
    onMeditationClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
    onFilterSelected: (DurationFilter) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onPaywallClick: () -> Unit
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val hasActiveFilters = uiState.activeFilter != DurationFilter.ALL || uiState.activeCategory != null

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
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 28.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                BadgedBox(
                    badge = {
                        if (hasActiveFilters) Badge()
                    }
                ) {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filtros",
                            tint = if (hasActiveFilters)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        uiState.featured?.let { featured ->
            val featuredLocked = !featured.isFree && !uiState.isSubscribed
            item(span = { GridItemSpan(2) }) {
                FeaturedCard(
                    meditation = featured,
                    isPremiumLocked = featuredLocked,
                    onClick = { if (featuredLocked) onPaywallClick() else onMeditationClick(featured.id) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
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
                val isPremiumLocked = !meditation.isFree && !uiState.isSubscribed
                MeditationCard(
                    meditation      = meditation,
                    isFavorite      = meditation.id in favorites,
                    isDownloaded    = meditation.id in uiState.downloads,
                    isDownloading   = meditation.id in uiState.downloadingIds,
                    isPremiumLocked = isPremiumLocked,
                    onClick         = { if (isPremiumLocked) onPaywallClick() else onMeditationClick(meditation.id) },
                    onFavoriteClick = { onFavoriteClick(meditation.id) },
                    onDownloadClick = { onDownloadClick(meditation.id) },
                    modifier = Modifier.padding(start = paddingStart, end = paddingEnd)
                )
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtros",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (hasActiveFilters) {
                        TextButton(onClick = {
                            onFilterSelected(DurationFilter.ALL)
                            onCategorySelected(null)
                        }) {
                            Text("Limpiar")
                        }
                    }
                }

                Text(
                    text = "Duración",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                DurationFilterRow(
                    activeFilter = uiState.activeFilter,
                    onFilterSelected = onFilterSelected
                )

                if (uiState.availableCategories.isNotEmpty()) {
                    Text(
                        text = "Categoría",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    CategoryFilterRow(
                        categories = uiState.availableCategories,
                        activeCategory = uiState.activeCategory,
                        onCategorySelected = onCategorySelected
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedCard(
    meditation: Meditation,
    isPremiumLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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

            if (isPremiumLocked) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 10.dp),
                    color = Color(0xFF7FD8F8),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = stringResource(R.string.badge_premium),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0D3344),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                if (!isPremiumLocked) {
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
                }
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
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier
    ) {
        items(DurationFilter.entries) { filter ->
            val selected = activeFilter == filter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = stringResource(filter.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
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

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    activeCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        item {
            FilterChip(
                selected = activeCategory == null,
                onClick = { onCategorySelected(null) },
                label = {
                    Text(
                        text = stringResource(R.string.filter_all),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        items(categories) { cat ->
            FilterChip(
                selected = activeCategory == cat,
                onClick = { onCategorySelected(if (activeCategory == cat) null else cat) },
                label = {
                    Text(
                        text = cat.replaceFirstChar { it.uppercase() },
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
    Meditation(id = "1", title = "Paz interior",     category = "paz",   durationSeconds = 300,  haloColor = "#7C3AED", createdAt = System.currentTimeMillis(), isFree = true),
    Meditation(id = "2", title = "Sueño profundo",   category = "sueño", durationSeconds = 600,  haloColor = "#3B82F6"),
    Meditation(id = "3", title = "Concentración",    category = "foco",  durationSeconds = 1200, haloColor = "#10B981"),
    Meditation(id = "4", title = "Relajación total", category = "paz",   durationSeconds = 900,  haloColor = "#F97316"),
)

private val previewUiState = HomeUiState(
    isLoading           = false,
    meditations         = previewMeditations,
    featured            = previewMeditations.first(),
    downloads           = listOf("2"),
    downloadingIds      = setOf("3"),
    isSubscribed        = false,
    availableCategories = listOf("paz", "sueño", "foco")
)

@Preview(showBackground = true, showSystemUi = true, name = "Home")
@Composable
private fun HomeScreenPreview() {
    EstrellaDeBelénTheme {
        HomeScreenContent(
            uiState            = previewUiState,
            userName           = "Juan",
            favorites          = listOf("1"),
            onMeditationClick  = {},
            onFavoriteClick    = {},
            onDownloadClick    = {},
            onFilterSelected   = {},
            onCategorySelected = {},
            onPaywallClick     = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home - cargando")
@Composable
private fun HomeScreenLoadingPreview() {
    EstrellaDeBelénTheme {
        HomeScreenContent(
            uiState            = HomeUiState(isLoading = true),
            userName           = "Juan",
            favorites          = emptyList(),
            onMeditationClick  = {},
            onFavoriteClick    = {},
            onDownloadClick    = {},
            onFilterSelected   = {},
            onCategorySelected = {},
            onPaywallClick     = {}
        )
    }
}
