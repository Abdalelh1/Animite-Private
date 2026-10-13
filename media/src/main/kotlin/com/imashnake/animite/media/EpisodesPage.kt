package com.imashnake.animite.media

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.graphics.toColorInt
import com.materialkolor.ktx.animateColorScheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.imashnake.animite.core.ui.LocalPaddings
import com.imashnake.animite.media.source.AnimeEpisode
import com.imashnake.animite.media.source.AnimeSearchResult
import com.imashnake.animite.media.source.AnimeStreamServer
import com.imashnake.animite.media.source.EpisodeJsonCache
import com.imashnake.animite.media.source.EpisodeSessionRepository
import com.imashnake.animite.media.source.EpisodeSourceProvider
import com.imashnake.animite.media.source.EpisodeSourcesManager
import com.imashnake.animite.media.source.SourceJsonParser
import com.imashnake.animite.media.source.StreamLinkType
import com.imashnake.animite.navigation.EpisodesRoute
import com.imashnake.animite.navigation.VideoPlayerRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun EpisodesPage(
    route: EpisodesRoute,
    onBack: () -> Unit,
    onNavigateToPlayer: (VideoPlayerRoute) -> Unit,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    isAmoled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colorInt = remember(route.colorHex) {
        try {
            route.colorHex?.takeIf { it.isNotBlank() }?.toColorInt()
        } catch (_: Exception) { null }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val titles = remember(route.animeTitle, route.otherTitlesJson) {
        val list = mutableListOf(route.animeTitle)
        try {
            if (route.otherTitlesJson.isNotEmpty() && route.otherTitlesJson != "[]") {
                val arr = JSONArray(route.otherTitlesJson)
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) list.add(s)
                }
            }
        } catch (_: Exception) {}
        list.filter { it.isNotBlank() }.distinct()
    }

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

    // Range pagination & search states
    var selectedRangeIndex by remember { mutableIntStateOf(0) }
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
            Log.d("ANIMITE_DIAG", "EPISODES_PAGE_LOADED_JSON_CACHE: count=${cached.episodes.size}")

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

    // Sort episodes according to order preference
    val sortedEpisodes = remember(episodes, isDescendingOrder) {
        if (isDescendingOrder) {
            episodes.sortedByDescending { it.number }
        } else {
            episodes.sortedBy { it.number }
        }
    }

    // Chunk size: 100 episodes per range (or 50 if <= 100)
    val chunkSize = remember(episodes.size) {
        if (episodes.size > 100) 100 else 50
    }

    val episodeRanges = remember(sortedEpisodes, chunkSize) {
        if (sortedEpisodes.size <= chunkSize) {
            emptyList()
        } else {
            sortedEpisodes.chunked(chunkSize).mapIndexed { index, chunk ->
                val firstNum = chunk.first().number.toInt()
                val lastNum = chunk.last().number.toInt()
                Pair(index, "$firstNum - $lastNum")
            }
        }
    }

    // Filtered episodes
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

    MaterialTheme(
        colorScheme = animateColorScheme(
            rememberColorSchemeFor(
                color = colorInt,
                useDarkTheme = useDarkTheme,
                isAmoled = isAmoled,
            ),
        ),
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding(),
            ) {
                // Rich Anime Hero Header with Properties & Theme
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                ) {
                    // Banner background with gradient fade
                    val bannerUrl = route.bannerImage?.takeIf { it.isNotBlank() } ?: route.fallbackThumbnail
                    if (bannerUrl.isNotBlank()) {
                        AsyncImage(
                            model = bannerUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.35f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                            MaterialTheme.colorScheme.background,
                                        ),
                                    ),
                                ),
                        )
                    }

                    // Content Row: Poster + Title & Properties
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Poster Thumbnail
                        val posterUrl = route.coverImage?.takeIf { it.isNotBlank() } ?: route.fallbackThumbnail
                        if (posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                            )
                        }

                        // Title and Property Chips (الخصائص)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = route.animeTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            // Metadata Properties Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val totalEps = route.totalEpisodes ?: episodes.size.takeIf { it > 0 }
                                if (totalEps != null && totalEps > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "$totalEps " + stringResource(R.string.episodes),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }

                                val sYear = route.seasonYear
                                if (!sYear.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = sYear,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Top Actions: Back Button, Search Toggle, Sort Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    isSearchExpanded = !isSearchExpanded
                                    if (!isSearchExpanded) {
                                        searchQuery = ""
                                        focusManager.clearFocus()
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                            ) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = stringResource(R.string.jump_to_ep),
                                    tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(
                                onClick = {
                                    isDescendingOrder = !isDescendingOrder
                                    selectedRangeIndex = 0
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_sort),
                                    contentDescription = if (isDescendingOrder) stringResource(R.string.sort_oldest_first) else stringResource(R.string.sort_newest_first),
                                    tint = if (isDescendingOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp),
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
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // Range Selector Tabs
            if (episodeRanges.isNotEmpty() && searchQuery.isBlank()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
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

            // Content Area: Grid of Episodes
            when {
                isLoadingEpisodes -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.searching_source),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                displayedEpisodes.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(displayedEpisodes, key = { it.url + it.number }) { ep ->
                            val epNumStr = if (ep.number % 1.0f == 0.0f) {
                                ep.number.toInt().toString()
                            } else {
                                ep.number.toString()
                            }

                            val thumbModel = ep.thumbnail ?: route.fallbackThumbnail.ifEmpty { null }

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
                                                    EpisodeSourcesManager.resolveStreamServersWithFallback(
                                                        episode = ep,
                                                        preferredProviderId = activeProvider?.id,
                                                    )
                                                }

                                                if (!isActive) return@launch

                                                if (resolved.success && resolved.servers.isNotEmpty()) {
                                                    val sList = resolved.servers
                                                    servers = sList

                                                    val directStreams = sList.filter {
                                                        it.isDirectVideo && it.linkType == StreamLinkType.DIRECT_VIDEO
                                                    }.distinctBy { it.quality }

                                                    val chosenStream = directStreams.firstOrNull { it.quality.contains("1080", true) }
                                                        ?: directStreams.firstOrNull { it.quality.contains("720", true) }
                                                        ?: directStreams.firstOrNull { it.quality.contains("HD", true) }
                                                        ?: directStreams.firstOrNull()
                                                        ?: sList.first()

                                                    EpisodeSessionRepository.setActiveEpisodes(episodes)
                                                    EpisodeSessionRepository.setActiveServers(sList)
                                                    onNavigateToPlayer(
                                                        VideoPlayerRoute(
                                                            animeTitle = matchedAnime?.title ?: route.animeTitle,
                                                            episodeNumber = epNumStr,
                                                            initialQuality = chosenStream.quality,
                                                            initialUrl = chosenStream.url,
                                                            serversJson = SourceJsonParser.encodeServers(sList),
                                                            episodesJson = SourceJsonParser.encodeEpisodes(episodes),
                                                            providerId = resolved.winningProviderId.ifEmpty { activeProvider?.id ?: "anime4up" },
                                                        )
                                                    )
                                                } else {
                                                    resolveErrorMessage = resolved.errorMessage ?: "تعذر تشغيل هذه الحلقة من أي مصدر متاح"
                                                }
                                            } catch (e: Exception) {
                                                if (!isActive) return@launch
                                                Log.e("ANIMITE_DIAG", "GET_SERVERS_ERROR: ${e.message}")
                                                resolveErrorMessage = "حدث خطأ أثناء استخراج رابط الحلقة، يرجى المحاولة لاحقاً"
                                            } finally {
                                                if (isActive) {
                                                    isFetchingServers = false
                                                }
                                            }
                                        }
                                    },
                            ) {
                                // Thumbnail Image
                                if (!thumbModel.isNullOrBlank()) {
                                    AsyncImage(
                                        model = thumbModel,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        alignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                                // Dark Scrim
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.2f),
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.8f),
                                                )
                                            )
                                        )
                                )

                                // Play Indicator Circle
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                // Episode Number Badge (Only Thumbnail and Number!)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
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
                            }
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
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
                                            EpisodeSourcesManager.resolveStreamServersWithFallback(
                                                episode = failedEp,
                                                preferredProviderId = activeProvider?.id,
                                            )
                                        }

                                        if (!isActive) return@launch

                                        if (resolved.success && resolved.servers.isNotEmpty()) {
                                            val sList = resolved.servers
                                            val directStreams = sList.filter {
                                                it.isDirectVideo && it.linkType == StreamLinkType.DIRECT_VIDEO
                                            }.distinctBy { it.quality }

                                            val chosenStream = directStreams.firstOrNull { it.quality.contains("1080", true) }
                                                ?: directStreams.firstOrNull { it.quality.contains("720", true) }
                                                ?: directStreams.firstOrNull { it.quality.contains("HD", true) }
                                                ?: directStreams.firstOrNull()
                                                ?: sList.first()

                                            EpisodeSessionRepository.setActiveEpisodes(episodes)
                                            EpisodeSessionRepository.setActiveServers(sList)
                                            onNavigateToPlayer(
                                                VideoPlayerRoute(
                                                    animeTitle = matchedAnime?.title ?: route.animeTitle,
                                                    episodeNumber = epNumStr,
                                                    initialQuality = chosenStream.quality,
                                                    initialUrl = chosenStream.url,
                                                    serversJson = SourceJsonParser.encodeServers(sList),
                                                    episodesJson = SourceJsonParser.encodeEpisodes(episodes),
                                                    providerId = resolved.winningProviderId.ifEmpty { activeProvider?.id ?: "anime4up" },
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
    }
}
}
