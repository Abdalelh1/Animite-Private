package com.imashnake.animite.media.source

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.imashnake.animite.core.ui.LocalPaddings
import com.imashnake.animite.media.R
import com.imashnake.animite.navigation.VideoPlayerRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.TextButton
import com.imashnake.animite.media.source.classification.EpisodeClassificationRepository

enum class EpisodeFilterCategory(val labelRes: Int) {
    ALL(R.string.filter_all),
    CANON(R.string.filter_canon),
    FILLER(R.string.filter_filler),
    MIXED(R.string.filter_mixed),
    SPECIALS(R.string.filter_specials),
    MOVIES(R.string.filter_movies),
    RECAPS(R.string.filter_recaps);

    fun matches(type: EpisodeType): Boolean {
        return when (this) {
            ALL -> true
            CANON -> type.isCanon
            FILLER -> type == EpisodeType.FILLER
            MIXED -> type == EpisodeType.MIXED
            SPECIALS -> type == EpisodeType.SPECIAL || type == EpisodeType.OVA || type == EpisodeType.ONA
            MOVIES -> type == EpisodeType.MOVIE
            RECAPS -> type == EpisodeType.RECAP
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaEpisodesSection(
    titles: List<String>,
    onNavigateToPlayer: (VideoPlayerRoute) -> Unit,
    modifier: Modifier = Modifier,
    fallbackThumbnail: String? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var isLoadingEpisodes by remember { mutableStateOf(false) }
    var episodes by remember { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
    var matchedAnime by remember { mutableStateOf<AnimeSearchResult?>(null) }
    var activeProvider by remember { mutableStateOf<EpisodeSourceProvider?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var selectedEpisode by remember { mutableStateOf<AnimeEpisode?>(null) }
    var isFetchingServers by remember { mutableStateOf(false) }
    var servers by remember { mutableStateOf<List<AnimeStreamServer>>(emptyList()) }
    var showServerDialog by remember { mutableStateOf(false) }
    var resolveErrorMessage by remember { mutableStateOf<String?>(null) }
    var currentResolveJob by remember { mutableStateOf<Job?>(null) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }

    // Range pagination & search states
    var selectedRangeIndex by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf(EpisodeFilterCategory.ALL) }
    var isDescendingOrder by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Multi-source automated aggregator with instant JSON Cache
    LaunchedEffect(titles) {
        if (titles.isEmpty()) return@LaunchedEffect

        // 1. Instant load from local JSON cache (0ms latency)
        val cached = withContext(Dispatchers.IO) {
            EpisodeJsonCache.getCachedEpisodes(context, titles)
        }
        if (cached != null && cached.episodes.isNotEmpty()) {
            matchedAnime = AnimeSearchResult(
                id = cached.matchedUrl,
                title = cached.animeTitle,
                url = cached.matchedUrl,
            )
            episodes = cached.episodes
            activeProvider = EpisodeSourcesManager.getProvider(cached.providerId)
            Log.d("ANIMITE_DIAG", "LOADED_INSTANT_FROM_JSON_CACHE: count=${cached.episodes.size}")

            if (!EpisodeJsonCache.isCacheExpired(cached)) {
                return@LaunchedEffect
            }
        } else {
            isLoadingEpisodes = true
        }

        searchError = null

        withContext(Dispatchers.IO) {
            try {
                val aggResult = EpisodeSourcesManager.findBestAggregatedEpisodes(titles)
                if (aggResult != null) {
                    matchedAnime = aggResult.matchedAnime
                    episodes = aggResult.episodes
                    activeProvider = aggResult.provider

                    // Save to local JSON cache for instant future loads
                    EpisodeJsonCache.saveEpisodes(
                        context = context,
                        title = aggResult.matchedAnime.title,
                        providerId = aggResult.provider.id,
                        matchedUrl = aggResult.matchedAnime.url,
                        episodes = aggResult.episodes,
                    )
                    Log.d("ANIMITE_DIAG", "AGGREGATOR_SUCCESS_SAVED_JSON: provider=${aggResult.provider.id}, count=${aggResult.episodes.size}")
                } else if (episodes.isEmpty()) {
                    searchError = "NOT_FOUND"
                }
            } catch (e: Exception) {
                Log.e("ANIMITE_DIAG", "AGGREGATOR_ERROR: ${e.message}")
                if (episodes.isEmpty()) {
                    searchError = e.localizedMessage ?: "ERROR"
                }
            } finally {
                isLoadingEpisodes = false
            }
        }
    }

    // Current source episodes: classify, sanitize, and deduplicate by (episodeType, number)
    val currentSourceEpisodes = remember(episodes, titles) {
        val classifiedList = episodes.map { ep ->
            if (ep.episodeType == EpisodeType.REGULAR || ep.episodeType == EpisodeType.UNCLASSIFIED) {
                val detected = EpisodeClassificationRepository.classify(titles, ep.number, ep.title, ep.url)
                ep.copy(episodeType = detected)
            } else {
                ep
            }
        }

        classifiedList.groupBy { Pair(it.episodeType, it.number) }.map { (_, list) ->
            if (list.size == 1) list.first()
            else {
                val allSources = list.flatMap { it.sources }.distinctBy { it.episodeUrl }
                val bestThumb = list.mapNotNull { it.thumbnail }
                    .firstOrNull { it.isNotBlank() && !it.contains("default") && !it.contains("banner") }
                    ?: list.firstNotNullOfOrNull { it.thumbnail }
                val bestTitle = list.mapNotNull { it.title }.maxByOrNull { it.length } ?: list.first().title
                list.first().copy(sources = allSources, thumbnail = bestThumb, title = bestTitle)
            }
        }.sortedWith(
            compareBy<AnimeEpisode> {
                when (it.episodeType) {
                    EpisodeType.CANON, EpisodeType.REGULAR, EpisodeType.FILLER, EpisodeType.MIXED, EpisodeType.UNCLASSIFIED -> 0
                    EpisodeType.RECAP -> 1
                    EpisodeType.SPECIAL -> 2
                    EpisodeType.OVA, EpisodeType.ONA -> 3
                    EpisodeType.MOVIE -> 4
                }
            }.thenBy { it.number }
        )
    }

    // Filter by selected category (Default: ALL)
    val filteredByCategoryEpisodes = remember(currentSourceEpisodes, selectedFilter) {
        if (selectedFilter == EpisodeFilterCategory.ALL) {
            currentSourceEpisodes
        } else {
            currentSourceEpisodes.filter { selectedFilter.matches(it.episodeType) }
        }
    }

    // Sort episodes according to order preference
    val sortedEpisodes = remember(filteredByCategoryEpisodes, isDescendingOrder) {
        if (isDescendingOrder) {
            filteredByCategoryEpisodes.sortedByDescending { it.number }
        } else {
            filteredByCategoryEpisodes.sortedBy { it.number }
        }
    }

    // Chunk size for ranges: 100 episodes per range (or 50 if <= 100)
    val chunkSize = remember(sortedEpisodes.size) {
        if (sortedEpisodes.size > 100) 100 else 50
    }

    val episodeRanges = remember(sortedEpisodes, chunkSize) {
        if (sortedEpisodes.size <= chunkSize) {
            emptyList()
        } else {
            sortedEpisodes.chunked(chunkSize).mapIndexed { index, chunk ->
                val firstNum = chunk.first().number.toInt()
                val lastNum = chunk.last().number.toInt()
                val label = if (firstNum <= lastNum) "$firstNum - $lastNum" else "$firstNum - $lastNum"
                Pair(index, label)
            }
        }
    }

    // Filtered episodes based on search query or selected range
    val displayedEpisodes by remember(sortedEpisodes, episodeRanges, selectedRangeIndex, searchQuery) {
        derivedStateOf {
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().toFloatOrNull()
                if (q != null) {
                    sortedEpisodes.filter { it.number == q }
                } else {
                    sortedEpisodes.filter {
                        val numStr = if (it.number % 1f == 0f) it.number.toInt().toString() else it.number.toString()
                        numStr.contains(searchQuery.trim())
                    }
                }
            } else if (episodeRanges.isNotEmpty()) {
                val clampedIndex = selectedRangeIndex.coerceIn(0, (sortedEpisodes.size - 1) / chunkSize)
                sortedEpisodes.chunked(chunkSize).getOrElse(clampedIndex) { sortedEpisodes }
            } else {
                sortedEpisodes
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LocalPaddings.current.medium),
    ) {
        // Section Header (No source names displayed!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.tv),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.episodes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (episodes.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${episodes.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (episodes.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Quick Search Button (especially for > 100 episodes)
                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) {
                                searchQuery = ""
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.jump_to_ep),
                            tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Sort Order Button
                    IconButton(
                        onClick = {
                            isDescendingOrder = !isDescendingOrder
                            selectedRangeIndex = 0
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sort),
                            contentDescription = if (isDescendingOrder) stringResource(R.string.sort_oldest_first) else stringResource(R.string.sort_newest_first),
                            tint = if (isDescendingOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Source Refresh Button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoadingEpisodes = true
                                searchError = null
                                withContext(Dispatchers.IO) {
                                    EpisodeJsonCache.clearAnimeCache(context, titles)
                                    try {
                                        val aggResult = EpisodeSourcesManager.findBestAggregatedEpisodes(titles)
                                        if (aggResult != null) {
                                            matchedAnime = aggResult.matchedAnime
                                            episodes = aggResult.episodes
                                            activeProvider = aggResult.provider
                                            EpisodeJsonCache.saveEpisodes(
                                                context = context,
                                                title = aggResult.matchedAnime.title,
                                                providerId = aggResult.provider.id,
                                                matchedUrl = aggResult.matchedAnime.url,
                                                episodes = aggResult.episodes,
                                            )
                                        }
                                    } catch (e: Exception) {
                                        searchError = e.localizedMessage
                                    } finally {
                                        isLoadingEpisodes = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "تحديث المصادر",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Diagnostic Summary Button
                    IconButton(
                        onClick = { showDiagnosticDialog = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "تشخيص المصادر",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Quick Search Box (Collapsible)
        AnimatedVisibility(visible = isSearchExpanded) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.ep_number_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        }

        // Category Filter Chips (الكل، الأساسي، الفلر، المختلط، الخاص، الأفلام، الملخصات)
        if (episodes.isNotEmpty() && searchQuery.isBlank()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(EpisodeFilterCategory.entries) { category ->
                    val isSelected = category == selectedFilter
                    val chipBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        label = "catChipBg",
                    )
                    val chipTextColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "catChipTextColor",
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(chipBg)
                            .clickable {
                                selectedFilter = category
                                selectedRangeIndex = 0
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(category.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = chipTextColor,
                        )
                    }
                }
            }
        }

        // Range Selector Tabs (When episodes > chunkSize)
        if (episodeRanges.isNotEmpty() && searchQuery.isBlank()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(episodeRanges) { (rangeIdx, rangeLabel) ->
                    val isSelected = rangeIdx == selectedRangeIndex
                    val chipBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        label = "chipBg",
                    )
                    val chipTextColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "chipTextColor",
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .clickable { selectedRangeIndex = rangeIdx }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = rangeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = chipTextColor,
                        )
                    }
                }
            }
        }

        // Content Area
        when {
            isLoadingEpisodes -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.searching_source),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            displayedEpisodes.isNotEmpty() -> {
                // Responsive Grid of Episodes: ONLY Thumbnail Image and Episode Number!
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            // Dynamically adapt height based on number of items
                            when {
                                displayedEpisodes.size <= 3 -> 120.dp
                                displayedEpisodes.size <= 6 -> 230.dp
                                displayedEpisodes.size <= 12 -> 340.dp
                                else -> 440.dp
                            }
                        ),
                ) {
                    items(displayedEpisodes, key = { it.url + it.number }) { ep ->
                        val epNumStr = if (ep.number % 1.0f == 0.0f) {
                            ep.number.toInt().toString()
                        } else {
                            ep.number.toString()
                        }

                        val thumbModel = ep.thumbnail ?: fallbackThumbnail

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable {
                                    currentResolveJob?.cancel()
                                    selectedEpisode = ep
                                    isFetchingServers = true
                                    resolveErrorMessage = null

                                    currentResolveJob = coroutineScope.launch {
                                        try {
                                            val resolved = withContext(Dispatchers.IO) {
                                                EpisodeSourcesManager.resolveAllEpisodeQualities(
                                                    episode = ep,
                                                    preferredProviderId = activeProvider?.id,
                                                )
                                            }

                                            if (!isActive) return@launch

                                            if (resolved.success && resolved.allDirectServers.isNotEmpty()) {
                                                val sList = resolved.allDirectServers
                                                servers = sList
                                                val chosenStream = resolved.bestStream ?: sList.first()

                                                EpisodeSessionRepository.setActiveEpisodes(episodes)
                                                EpisodeSessionRepository.setActiveServers(sList)
                                                EpisodeSessionRepository.setActivePlaybackResult(resolved)
                                                EpisodeSessionRepository.setActiveInitialUrl(chosenStream.url)
                                                EpisodeSessionRepository.setActiveInitialQuality(chosenStream.quality)
                                                onNavigateToPlayer(
                                                    VideoPlayerRoute(
                                                        animeTitle = matchedAnime?.title ?: titles.firstOrNull() ?: "",
                                                        episodeNumber = epNumStr,
                                                        initialQuality = chosenStream.quality,
                                                        initialUrl = "",
                                                        serversJson = "[]",
                                                        episodesJson = "[]",
                                                        providerId = resolved.participatingProviders.firstOrNull() ?: activeProvider?.id ?: "anime4up",
                                                    )
                                                )
                                            } else {
                                                resolveErrorMessage = resolved.errorMessage ?: "تعذر تشغيل هذه الحلقة من أي مصدر متاح"
                                            }
                                        } catch (e: Exception) {
                                            if (!isActive) return@launch
                                            Log.e("ANIMITE_DIAG", "GET_SERVERS_ERROR: ${e.message}")
                                            resolveErrorMessage = "حدث خطأ أثناء استخراج رابط الحلقة"
                                        } finally {
                                            if (isActive) {
                                                isFetchingServers = false
                                            }
                                        }
                                    }
                                },
                        ) {
                            // Episode Thumbnail Image
                            if (!thumbModel.isNullOrBlank()) {
                                AsyncImage(
                                    model = thumbModel,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            // Dark Gradient Overlay for readability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.25f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.75f),
                                            )
                                        )
                                    )
                            )

                            // Episode Number Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = epNumStr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                )
                            }

                            // Episode Classification Badge (Non-color only: Icon + Text)
                            val badgeData: Triple<String, androidx.compose.ui.graphics.vector.ImageVector, Color>? = when (ep.episodeType) {
                                EpisodeType.FILLER -> Triple(stringResource(R.string.badge_filler), Icons.Rounded.HourglassEmpty, Color(0xFFF59E0B))
                                EpisodeType.MIXED -> Triple(stringResource(R.string.badge_mixed), Icons.Rounded.CompareArrows, Color(0xFF14B8A6))
                                EpisodeType.SPECIAL -> Triple(stringResource(R.string.badge_special), Icons.Rounded.Star, Color(0xFFA855F7))
                                EpisodeType.OVA -> Triple(stringResource(R.string.badge_ova), Icons.Rounded.VideoLibrary, Color(0xFF8B5CF6))
                                EpisodeType.ONA -> Triple(stringResource(R.string.badge_ona), Icons.Rounded.Language, Color(0xFF6366F1))
                                EpisodeType.MOVIE -> Triple(stringResource(R.string.badge_movie), Icons.Rounded.Movie, Color(0xFFEF4444))
                                EpisodeType.RECAP -> Triple(stringResource(R.string.badge_recap), Icons.Rounded.History, Color(0xFF3B82F6))
                                EpisodeType.CANON -> Triple(stringResource(R.string.badge_canon), Icons.Rounded.CheckCircle, Color(0xFF10B981))
                                EpisodeType.UNCLASSIFIED, EpisodeType.REGULAR -> null
                            }

                            if (badgeData != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeData.third.copy(alpha = 0.90f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Icon(
                                            imageVector = badgeData.second,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(11.dp),
                                        )
                                        Text(
                                            text = badgeData.first,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }

                            // Episode Availability Badge
                            if (ep.availabilityState == EpisodeAvailabilityState.UNAVAILABLE) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFDC2626).copy(alpha = 0.90f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "غير متوفر",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                    )
                                }
                            }

                            // Play Indicator icon
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_episodes_found_for_anime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // Loading Servers Dialog
    if (isFetchingServers) {
        Dialog(onDismissRequest = {
            currentResolveJob?.cancel()
            isFetchingServers = false
        }) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                    )
                    Text(
                        text = stringResource(R.string.loading_stream),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    // Error & Retry Dialog when episode resolution fails across all sources
    if (resolveErrorMessage != null && selectedEpisode != null) {
        val failedEp = selectedEpisode!!
        val epNumStr = if (failedEp.number % 1.0f == 0.0f) failedEp.number.toInt().toString() else failedEp.number.toString()

        Dialog(onDismissRequest = { resolveErrorMessage = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "تعذر تشغيل الحلقة $epNumStr",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = resolveErrorMessage ?: "لم نتمكن من العثور على رابط صالح للتشغيل من أي مصدر متوفر.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                resolveErrorMessage = null
                                isFetchingServers = true
                                currentResolveJob = coroutineScope.launch {
                                    try {
                                        val resolved = withContext(Dispatchers.IO) {
                                            EpisodeSourcesManager.resolveAllEpisodeQualities(
                                                episode = failedEp,
                                                preferredProviderId = activeProvider?.id,
                                            )
                                        }

                                        if (!isActive) return@launch

                                        if (resolved.success && resolved.allDirectServers.isNotEmpty()) {
                                            val sList = resolved.allDirectServers
                                            val chosenStream = resolved.bestStream ?: sList.first()

                                            EpisodeSessionRepository.setActiveEpisodes(episodes)
                                            EpisodeSessionRepository.setActiveServers(sList)
                                            EpisodeSessionRepository.setActivePlaybackResult(resolved)
                                            EpisodeSessionRepository.setActiveInitialUrl(chosenStream.url)
                                            EpisodeSessionRepository.setActiveInitialQuality(chosenStream.quality)
                                            onNavigateToPlayer(
                                                VideoPlayerRoute(
                                                    animeTitle = matchedAnime?.title ?: titles.firstOrNull() ?: "",
                                                    episodeNumber = epNumStr,
                                                    initialQuality = chosenStream.quality,
                                                    initialUrl = "",
                                                    serversJson = "[]",
                                                    episodesJson = "[]",
                                                    providerId = resolved.participatingProviders.firstOrNull() ?: activeProvider?.id ?: "anime4up",
                                                )
                                            )
                                        } else {
                                            resolveErrorMessage = resolved.errorMessage ?: "تعذر تشغيل هذه الحلقة من أي مصدر متاح"
                                        }
                                    } catch (e: Exception) {
                                        if (!isActive) return@launch
                                        resolveErrorMessage = "حدث خطأ أثناء استخراج رابط الحلقة"
                                    } finally {
                                        if (isActive) {
                                            isFetchingServers = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("إعادة المحاولة")
                        }

                        OutlinedButton(
                            onClick = { resolveErrorMessage = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("إلغاء")
                        }
                    }
                }
            }
        }

        // Diagnostic Summary Dialog
        if (showDiagnosticDialog) {
            val summary = remember(titles, episodes) {
                titles.firstNotNullOfOrNull { SmartStreamDiagnostics.getAnimeSummary(it) }
                    ?: matchedAnime?.title?.let { SmartStreamDiagnostics.getAnimeSummary(it) }
                    ?: AnimeDiagnosticSummary(
                        animeTitle = matchedAnime?.title ?: titles.firstOrNull() ?: "",
                        episodesDiscoveredPerProvider = mapOf(activeProvider?.id.orEmpty() to episodes.size),
                        unifiedEpisodeCount = episodes.size,
                        duplicateEpisodesRemoved = 0,
                        episodesWithoutPlayableCandidates = episodes.count { it.availabilityState == EpisodeAvailabilityState.UNAVAILABLE },
                        validatedStreamCount = 0,
                        failedProviders = emptyMap(),
                        lastSuccessfulCheck = System.currentTimeMillis(),
                        isFromCache = false,
                        cacheStatus = "مباشر (Live)",
                    )
            }

            Dialog(onDismissRequest = { showDiagnosticDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = "تشخيص مصادر الأنمي",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DiagnosticRow(label = "عدد الحلقات الموحد:", value = "${summary.unifiedEpisodeCount} حلقة")
                            DiagnosticRow(label = "الحلقات المكررة المحذوفة:", value = "${summary.duplicateEpisodesRemoved}")
                            DiagnosticRow(label = "حلقات بدون مشغل متاح:", value = "${summary.episodesWithoutPlayableCandidates}")
                            DiagnosticRow(label = "حالة الذاكرة المؤقتة:", value = summary.cacheStatus)

                            if (summary.episodesDiscoveredPerProvider.isNotEmpty()) {
                                Text(
                                    text = "الحلقات المكتشفة لكل مصدر:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                summary.episodesDiscoveredPerProvider.forEach { (provId, count) ->
                                    DiagnosticRow(
                                        label = "• ${EpisodeSourcesManager.getProviderDisplayName(provId)}:",
                                        value = "$count حلقة"
                                    )
                                }
                            }

                            if (summary.failedProviders.isNotEmpty()) {
                                Text(
                                    text = "المصادر المتعثرة:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                summary.failedProviders.forEach { (provId, reason) ->
                                    Text(
                                        text = "• ${EpisodeSourcesManager.getProviderDisplayName(provId)}: $reason",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { showDiagnosticDialog = false }) {
                                Text("إغلاق")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
