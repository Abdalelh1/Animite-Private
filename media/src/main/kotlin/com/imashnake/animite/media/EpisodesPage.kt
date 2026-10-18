package com.imashnake.animite.media

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import com.imashnake.animite.media.R
import com.materialkolor.ktx.animateColorScheme
import com.imashnake.animite.media.source.AnimeEpisode
import com.imashnake.animite.media.source.AnimeSearchResult
import com.imashnake.animite.media.source.AnimeStreamServer
import com.imashnake.animite.media.source.EpisodeJsonCache
import com.imashnake.animite.media.source.EpisodeSessionRepository
import com.imashnake.animite.media.source.EpisodeSourceProvider
import com.imashnake.animite.media.source.EpisodeSourcesManager
import com.imashnake.animite.media.source.EpisodeType
import com.imashnake.animite.media.source.classification.EpisodeClassificationRepository
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import com.imashnake.animite.media.source.AnimeDiagnosticSummary
import com.imashnake.animite.media.source.EpisodeAvailabilityState
import com.imashnake.animite.media.source.SmartStreamDiagnostics
import androidx.compose.ui.graphics.vector.ImageVector
import com.imashnake.animite.navigation.EpisodesRoute
import com.imashnake.animite.navigation.VideoPlayerRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

enum class EpisodeWatchFilter {
    ALL,
    UNWATCHED,
    WATCHED,
}

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
    var resolveErrorMessage by remember { mutableStateOf<String?>(null) }
    var currentResolveJob by remember { mutableStateOf<Job?>(null) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }

    // UI interactive filters & states
    var isDescendingOrder by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(EpisodeWatchFilter.ALL) }

    val anilistThumbnails = remember(route.streamingEpisodesJson) {
        val map = mutableMapOf<String, String>()
        try {
            if (route.streamingEpisodesJson.isNotBlank() && route.streamingEpisodesJson != "{}") {
                val obj = org.json.JSONObject(route.streamingEpisodesJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val url = obj.optString(key)
                    if (url.isNotBlank()) {
                        map[key] = url
                    }
                }
            }
        } catch (_: Exception) {}
        map
    }

    val maxAiredEpisode = remember(route.nextAiringEpisode) {
        val nextEp = route.nextAiringEpisode
        if (nextEp != null && nextEp > 0) nextEp - 1 else null
    }

    // Load episodes from cache and fetch from multi-source aggregator
    LaunchedEffect(titles) {
        if (titles.isEmpty()) return@LaunchedEffect

        val cached = withContext(Dispatchers.IO) {
            EpisodeJsonCache.getCachedEpisodes(context, titles)
        }
        if (cached != null && cached.episodes.isNotEmpty()) {
            val filteredCached = if (maxAiredEpisode != null && maxAiredEpisode >= 0) {
                cached.episodes.filter { it.number <= maxAiredEpisode }
            } else {
                cached.episodes
            }
            matchedAnime = AnimeSearchResult(
                id = cached.matchedUrl,
                title = cached.animeTitle,
                url = cached.matchedUrl,
            )
            episodes = filteredCached
            activeProvider = EpisodeSourcesManager.getProvider(cached.providerId)

            if (!EpisodeJsonCache.isCacheExpired(cached)) {
                return@LaunchedEffect
            }
        } else {
            isLoadingEpisodes = true
        }

        searchError = null

        withContext(Dispatchers.IO) {
            try {
                val aggResult = EpisodeSourcesManager.findBestAggregatedEpisodes(
                    titles = titles,
                    maxAiredEpisode = maxAiredEpisode,
                    totalEpisodes = route.totalEpisodes,
                )
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

    // Strictly deduplicate by canonical integer/float episode number so every number appears exactly once
    val currentSourceEpisodes = remember(episodes, route.totalEpisodes, maxAiredEpisode, titles) {
        val classifiedList = episodes.map { ep ->
            if (ep.episodeType == EpisodeType.REGULAR || ep.episodeType == EpisodeType.UNCLASSIFIED) {
                val detected = EpisodeClassificationRepository.classify(titles, ep.number)
                ep.copy(episodeType = detected)
            } else {
                ep
            }
        }

        val uniqueEps = classifiedList.groupBy {
            val n = it.number
            if (kotlin.math.abs(n - n.toInt()) < 0.05f) n.toInt().toFloat()
            else (Math.round(n * 10f) / 10f)
        }.map { (canonicalNum, list) ->
            if (list.size == 1) list.first().copy(number = canonicalNum)
            else {
                val allSources = list.flatMap { it.sources }.distinctBy { it.episodeUrl }
                val bestThumb = list.mapNotNull { it.thumbnail }
                    .firstOrNull { it.isNotBlank() && !it.contains("default") && !it.contains("banner") }
                    ?: list.firstNotNullOfOrNull { it.thumbnail }
                val bestTitle = list.mapNotNull { it.title }.maxByOrNull { it.length } ?: list.first().title
                val bestType = list.map { it.episodeType }.firstOrNull { it != EpisodeType.REGULAR && it != EpisodeType.UNCLASSIFIED } ?: list.first().episodeType
                list.first().copy(
                    number = canonicalNum,
                    sources = allSources,
                    thumbnail = bestThumb,
                    title = bestTitle,
                    episodeType = bestType,
                )
            }
        }.sortedBy { it.number }

        // Filter out future/un-aired episodes if air schedule is known
        val airedEps = if (maxAiredEpisode != null && maxAiredEpisode >= 0) {
            uniqueEps.filter { it.number <= maxAiredEpisode }
        } else {
            uniqueEps
        }

        val maxEpisodes = route.totalEpisodes
        if (maxEpisodes != null && maxEpisodes > 0 && airedEps.size > maxEpisodes) {
            airedEps.filter { it.number <= maxEpisodes }
        } else {
            airedEps
        }
    }

    // Sort order
    val sortedEpisodes = remember(currentSourceEpisodes, isDescendingOrder) {
        if (isDescendingOrder) {
            currentSourceEpisodes.sortedByDescending { it.number }
        } else {
            currentSourceEpisodes.sortedBy { it.number }
        }
    }

    // Watch progress evaluation
    val hasAnyHistory = remember(currentSourceEpisodes) {
        EpisodeWatchManager.hasAnyWatchHistory(context, route.animeId)
    }

    val lastWatchedNum = remember(currentSourceEpisodes) {
        EpisodeWatchManager.getLastWatchedEpisode(context, route.animeId)
    }

    val continueWatchingEp = remember(lastWatchedNum, currentSourceEpisodes) {
        if (lastWatchedNum != null) {
            currentSourceEpisodes.firstOrNull { kotlin.math.abs(it.number - lastWatchedNum) < 0.05f }
        } else null
    }

    // Identify the single latest in-progress episode
    val currentInProgressEpNum = remember(lastWatchedNum, sortedEpisodes) {
        if (lastWatchedNum != null && EpisodeWatchManager.isInProgress(context, route.animeId, lastWatchedNum)) {
            lastWatchedNum
        } else {
            sortedEpisodes.firstOrNull { EpisodeWatchManager.isInProgress(context, route.animeId, it.number) }?.number
        }
    }

    // Filtered episodes based on watch status and search query
    val displayedEpisodes by remember(sortedEpisodes, activeFilter, searchQuery) {
        derivedStateOf {
            val baseList = when (activeFilter) {
                EpisodeWatchFilter.ALL -> sortedEpisodes
                EpisodeWatchFilter.UNWATCHED -> sortedEpisodes.filter { !EpisodeWatchManager.isWatched(context, route.animeId, it.number) }
                EpisodeWatchFilter.WATCHED -> sortedEpisodes.filter { EpisodeWatchManager.isWatched(context, route.animeId, it.number) }
            }

            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().toFloatOrNull()
                if (q != null) {
                    baseList.filter { kotlin.math.abs(it.number - q) < 0.05f }
                } else {
                    baseList.filter {
                        val numStr = if (it.number % 1f == 0f) it.number.toInt().toString() else it.number.toString()
                        numStr.contains(searchQuery.trim())
                    }
                }
            } else {
                baseList
            }
        }
    }

    // Playback launcher
    fun launchPlayback(ep: AnimeEpisode) {
        currentResolveJob?.cancel()
        selectedEpisode = ep
        isFetchingServers = true
        resolveErrorMessage = null

        val epNumInt = ep.number.toInt()
        val epNumStr = if (ep.number % 1.0f == 0.0f) epNumInt.toString() else ep.number.toString()

        // Track episode in history as in-progress (e.g. 0.15f if unwatched)
        if (!EpisodeWatchManager.isWatched(context, route.animeId, ep.number)) {
            val currProg = EpisodeWatchManager.getProgress(context, route.animeId, ep.number)
            if (currProg < 0.1f) {
                EpisodeWatchManager.saveProgress(context, route.animeId, ep.number, 0.15f)
            }
        }
        EpisodeWatchManager.recordContinueWatching(
            context = context,
            animeId = route.animeId,
            animeTitle = matchedAnime?.title ?: route.animeTitle,
            coverImage = ep.thumbnail ?: route.coverImage ?: route.bannerImage,
            episodeNumber = ep.number,
            progress = EpisodeWatchManager.getProgress(context, route.animeId, ep.number).coerceAtLeast(0.15f)
        )

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
                    val chosenStream = resolved.bestStream ?: sList.first()

                    EpisodeSessionRepository.setActiveEpisodes(currentSourceEpisodes)
                    EpisodeSessionRepository.setActiveServers(sList)
                    EpisodeSessionRepository.setActivePlaybackResult(resolved)
                    EpisodeSessionRepository.setActiveInitialUrl(chosenStream.url)
                    EpisodeSessionRepository.setActiveInitialQuality(chosenStream.quality)

                    onNavigateToPlayer(
                        VideoPlayerRoute(
                            animeTitle = matchedAnime?.title ?: route.animeTitle,
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
                resolveErrorMessage = "حدث خطأ أثناء استخراج رابط الحلقة، يرجى المحاولة لاحقاً"
            } finally {
                if (isActive) {
                    isFetchingServers = false
                }
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
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surface,
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding(),
                ) {
                    // 1) TOP BAR: Circular Back, Centered Title, Circular Search & Sort Buttons (44dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Circular Back Button (44dp)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp),
                            onClick = onBack,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // Work Name in Center (Single Line with Ellipsis, LTR direction for Latin text)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = route.animeTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        // Circular Search Button (44dp)
                        Surface(
                            shape = CircleShape,
                            color = if (isSearchExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp),
                            onClick = {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) searchQuery = ""
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "بحث",
                                    tint = if (isSearchExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Circular Sort Order Button (44dp)
                        Surface(
                            shape = CircleShape,
                            color = if (isDescendingOrder) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp),
                            onClick = { isDescendingOrder = !isDescendingOrder },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val barColor = if (isDescendingOrder) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
                                    val stroke = 2.dp.toPx()
                                    drawLine(barColor, Offset(2.dp.toPx(), 4.dp.toPx()), Offset(18.dp.toPx(), 4.dp.toPx()), strokeWidth = stroke)
                                    drawLine(barColor, Offset(5.dp.toPx(), 10.dp.toPx()), Offset(15.dp.toPx(), 10.dp.toPx()), strokeWidth = stroke)
                                    drawLine(barColor, Offset(8.dp.toPx(), 16.dp.toPx()), Offset(12.dp.toPx(), 16.dp.toPx()), strokeWidth = stroke)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Source Refresh Button (44dp)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp),
                            onClick = {
                                coroutineScope.launch {
                                    isLoadingEpisodes = true
                                    searchError = null
                                    withContext(Dispatchers.IO) {
                                        EpisodeJsonCache.clearAnimeCache(context, titles)
                                        try {
                                            val aggResult = EpisodeSourcesManager.findBestAggregatedEpisodes(
                                                titles = titles,
                                                maxAiredEpisode = maxAiredEpisode,
                                                totalEpisodes = route.totalEpisodes,
                                            )
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
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "تحديث المصادر",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Diagnostic Summary Button (44dp)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp),
                            onClick = { showDiagnosticDialog = true },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "تشخيص المصادر",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // 6) Search Input (when search icon is pressed)
                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "ابحث برقم الحلقة...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "مسح",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Search,
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    },
                                ),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // 2) CHIPS ROW (32dp): Episodes Count, Year, Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val epsCountText = "${route.totalEpisodes ?: currentSourceEpisodes.size} حلقة"
                        val yearText = route.seasonYear ?: "2026"
                        val statusText = if (route.nextAiringEpisode != null) "مستمر" else "مكتمل"

                        listOf(epsCountText, yearText, statusText).forEachIndexed { idx, text ->
                            if (idx > 0) Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // 3) "CONTINUE WATCHING" CARD (primaryContainer, 20dp corners, height ~92dp)
                    if (continueWatchingEp != null) {
                        val continueProg = EpisodeWatchManager.getProgress(context, route.animeId, continueWatchingEp.number).coerceIn(0.1f, 1f)
                        val epNumInt = continueWatchingEp.number.toInt()
                        val epNumStr = if (continueWatchingEp.number % 1f == 0f) epNumInt.toString() else continueWatchingEp.number.toString()

                        val anilistThumb = anilistThumbnails[epNumStr] ?: anilistThumbnails[epNumInt.toString()]
                        val thumbModel = when {
                            !anilistThumb.isNullOrBlank() -> anilistThumb
                            !continueWatchingEp.thumbnail.isNullOrBlank() -> continueWatchingEp.thumbnail
                            !route.coverImage.isNullOrBlank() -> route.coverImage
                            !route.fallbackThumbnail.isNullOrBlank() -> route.fallbackThumbnail
                            !route.bannerImage.isNullOrBlank() -> route.bannerImage
                            else -> null
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { launchPlayback(continueWatchingEp) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 1) START (Right in RTL): Thumbnail (rounded 14dp)
                                AsyncImage(
                                    model = thumbModel,
                                    contentDescription = continueWatchingEp.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(92.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )

                                // 2) CENTER: "تابع المشاهدة" + "الحلقة N" + Thin Progress bar
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    Text(
                                        text = "تابع المشاهدة",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Start,
                                    )
                                    Text(
                                        text = "الحلقة $epNumStr",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Start,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val cardProgColor = MaterialTheme.colorScheme.primary
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(CircleShape)
                                                .background(cardProgColor.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(continueProg.coerceIn(0f, 1f))
                                                    .fillMaxHeight()
                                                    .clip(CircleShape)
                                                    .background(cardProgColor),
                                            )
                                        }
                                    }
                                }

                                // 3) END (Left in RTL): Primary circular play button (48dp)
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp),
                                    onClick = { launchPlayback(continueWatchingEp) },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "تشغيل",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4) FILTER CHIPS: الكل (محدد) | غير مشاهدة | مشاهدة
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Chip "الكل"
                        val isAllSelected = activeFilter == EpisodeWatchFilter.ALL
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .height(36.dp)
                                .clickable { activeFilter = EpisodeWatchFilter.ALL },
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "الكل",
                                    fontSize = 13.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Chip "غير مشاهدة"
                        val isUnwatchedSelected = activeFilter == EpisodeWatchFilter.UNWATCHED
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isUnwatchedSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .height(36.dp)
                                .clickable { activeFilter = EpisodeWatchFilter.UNWATCHED },
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "غير مشاهدة",
                                    fontSize = 13.sp,
                                    fontWeight = if (isUnwatchedSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isUnwatchedSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Chip "مشاهدة" (only shown if there is watch history / saved episodes)
                        if (hasAnyHistory) {
                            val isWatchedSelected = activeFilter == EpisodeWatchFilter.WATCHED
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isWatchedSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .height(36.dp)
                                    .clickable { activeFilter = EpisodeWatchFilter.WATCHED },
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "مشاهدة",
                                        fontSize = 13.sp,
                                        fontWeight = if (isWatchedSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isWatchedSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Loading indicator while aggregating
                    if (isLoadingEpisodes && episodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = stringResource(R.string.searching_source),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (displayedEpisodes.isEmpty()) {
                        // Empty State
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "لم يتم العثور على الحلقة المطلوبة" else "لا توجد حلقات متاحة",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // 5) LAZYCOLUMN: Episode items (row height ~72dp)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(displayedEpisodes, key = { it.number }) { ep ->
                                val epNumInt = ep.number.toInt()
                                val epNumStr = if (ep.number % 1.0f == 0.0f) epNumInt.toString() else ep.number.toString()

                                val isWatched = EpisodeWatchManager.isWatched(context, route.animeId, ep.number)
                                val progress = EpisodeWatchManager.getProgress(context, route.animeId, ep.number)
                                val hasProgress = progress in 0.05f..0.89f && !isWatched
                                val isCurrentInProgress = currentInProgressEpNum != null && kotlin.math.abs(ep.number - currentInProgressEpNum) < 0.05f

                                val anilistThumb = anilistThumbnails[epNumStr] ?: anilistThumbnails[epNumInt.toString()]
                                val providerThumb = ep.thumbnail?.takeIf {
                                    it.isNotBlank() &&
                                    !it.contains("default", ignoreCase = true) &&
                                    !it.contains("placeholder", ignoreCase = true) &&
                                    !it.contains("logo", ignoreCase = true)
                                }
                                val thumbModel = when {
                                    !anilistThumb.isNullOrBlank() -> anilistThumb
                                    !providerThumb.isNullOrBlank() -> providerThumb
                                    !route.coverImage.isNullOrBlank() -> route.coverImage
                                    !route.fallbackThumbnail.isNullOrBlank() -> route.fallbackThumbnail
                                    !route.bannerImage.isNullOrBlank() -> route.bannerImage
                                    else -> null
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isCurrentInProgress) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    } else {
                                        Color.Transparent
                                    },
                                    border = if (isCurrentInProgress) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    } else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .clickable { launchPlayback(ep) },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 1) START (Right in RTL): 96x54 Thumbnail with rounded 10dp
                                        AsyncImage(
                                            model = thumbModel,
                                            contentDescription = ep.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(96.dp)
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .alpha(if (isWatched) 0.72f else 1f),
                                        )

                                        // 2) CENTER: "الحلقة N" + Subtitle + Progress bar if has progress
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 12.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.Start,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = "الحلقة $epNumStr",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Start,
                                                )

                                                // Episode Classification Badge (Non-color only: Icon + Text)
                                                val badgeData: Triple<String, ImageVector, Color>? = when (ep.episodeType) {
                                                    EpisodeType.FILLER -> Triple(stringResource(R.string.badge_filler), Icons.Rounded.HourglassEmpty, Color(0xFFF59E0B))
                                                    EpisodeType.MIXED -> Triple(stringResource(R.string.badge_mixed), Icons.AutoMirrored.Rounded.CompareArrows, Color(0xFF14B8A6))
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
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(badgeData.third.copy(alpha = 0.85f))
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
                                                                modifier = Modifier.size(10.dp),
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

                                                if (ep.availabilityState == EpisodeAvailabilityState.UNAVAILABLE) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFDC2626).copy(alpha = 0.85f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            text = "غير متوفر",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val statusSubtitle = when {
                                                isWatched -> "24 د • تمت المشاهدة"
                                                hasProgress -> {
                                                    val remMins = (24 * (1f - progress)).toInt().coerceAtLeast(1)
                                                    "24 د • متبقي $remMins د"
                                                }
                                                else -> "24 د • لم تُشاهد"
                                            }
                                            Text(
                                                text = statusSubtitle,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Start,
                                            )
                                            if (hasProgress) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val barProgColor = MaterialTheme.colorScheme.primary
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(0.9f)
                                                            .height(3.dp)
                                                            .clip(CircleShape)
                                                            .background(barProgColor.copy(alpha = 0.25f)),
                                                        contentAlignment = Alignment.CenterStart,
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                                                .fillMaxHeight()
                                                                .clip(CircleShape)
                                                                .background(barProgColor),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 3) END (Left in RTL): Action Button (40dp)
                                        Surface(
                                            shape = CircleShape,
                                            color = when {
                                                isWatched -> MaterialTheme.colorScheme.primaryContainer
                                                isCurrentInProgress -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                            modifier = Modifier.size(40.dp),
                                            onClick = { launchPlayback(ep) },
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isWatched) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "تمت المشاهدة",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "تشغيل",
                                                        tint = if (isCurrentInProgress) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Resolving Server Loading Overlay / Dialog
                if (isFetchingServers) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black.copy(alpha = 0.5f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 8.dp,
                                modifier = Modifier.padding(32.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp),
                                    )
                                    Text(
                                        text = "جاري تحضير الحلقة...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                // Error Dialog
                if (resolveErrorMessage != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { resolveErrorMessage = null },
                        title = {
                            Text(
                                text = "تعذر تشغيل الحلقة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        text = {
                            Text(
                                text = resolveErrorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.Button(
                                onClick = {
                                    resolveErrorMessage = null
                                    selectedEpisode?.let { launchPlayback(it) }
                                },
                            ) {
                                Text("إعادة المحاولة")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { resolveErrorMessage = null },
                            ) {
                                Text("إلغاء")
                            }
                        },
                    )
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
                                    com.imashnake.animite.media.source.DiagnosticRow(label = "عدد الحلقات الموحد:", value = "${summary.unifiedEpisodeCount} حلقة")
                                    com.imashnake.animite.media.source.DiagnosticRow(label = "الحلقات المكررة المحذوفة:", value = "${summary.duplicateEpisodesRemoved}")
                                    com.imashnake.animite.media.source.DiagnosticRow(label = "حلقات بدون مشغل متاح:", value = "${summary.episodesWithoutPlayableCandidates}")
                                    com.imashnake.animite.media.source.DiagnosticRow(label = "حالة الذاكرة المؤقتة:", value = summary.cacheStatus)

                                    if (summary.episodesDiscoveredPerProvider.isNotEmpty()) {
                                        Text(
                                            text = "الحلقات المكتشفة لكل مصدر:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                        summary.episodesDiscoveredPerProvider.forEach { (provId, count) ->
                                            com.imashnake.animite.media.source.DiagnosticRow(
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
    }
}
