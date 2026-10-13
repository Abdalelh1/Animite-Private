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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaEpisodesSection(
    titles: List<String>,
    onNavigateToPlayer: (VideoPlayerRoute) -> Unit,
    modifier: Modifier = Modifier,
    fallbackThumbnail: String? = null,
) {
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

    // Range pagination & search states
    var selectedRangeIndex by remember { mutableIntStateOf(0) }
    var isDescendingOrder by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Multi-source automated aggregator: fetches matching anime and episodes seamlessly
    LaunchedEffect(titles) {
        if (titles.isEmpty()) return@LaunchedEffect

        isLoadingEpisodes = true
        searchError = null
        episodes = emptyList()
        matchedAnime = null
        activeProvider = null

        withContext(Dispatchers.IO) {
            try {
                val aggResult = EpisodeSourcesManager.findBestAggregatedEpisodes(titles)
                if (aggResult != null) {
                    matchedAnime = aggResult.matchedAnime
                    episodes = aggResult.episodes
                    activeProvider = aggResult.provider
                    Log.d("ANIMITE_DIAG", "AGGREGATOR_SUCCESS: provider=${aggResult.provider.id}, episodesCount=${aggResult.episodes.size}")
                } else {
                    searchError = "NOT_FOUND"
                }
            } catch (e: Exception) {
                Log.e("ANIMITE_DIAG", "AGGREGATOR_ERROR: ${e.message}")
                searchError = e.localizedMessage ?: "ERROR"
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

    // Chunk size for ranges: 100 episodes per range (or 50 if <= 100)
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
                                    val prov = activeProvider ?: EpisodeSourcesManager.providers.first()
                                    selectedEpisode = ep
                                    isFetchingServers = true

                                    coroutineScope.launch {
                                        try {
                                            val sList = withContext(Dispatchers.IO) {
                                                prov.getStreamServers(ep.url)
                                            }
                                            servers = sList

                                            val directStreams = sList.filter {
                                                it.isDirectVideo && it.linkType == StreamLinkType.DIRECT_VIDEO
                                            }.distinctBy { it.quality }

                                            val chosenStream = directStreams.firstOrNull { it.quality.contains("1080", true) }
                                                ?: directStreams.firstOrNull { it.quality.contains("720", true) }
                                                ?: directStreams.firstOrNull { it.quality.contains("HD", true) }
                                                ?: directStreams.firstOrNull()
                                                ?: sList.firstOrNull()

                                            if (chosenStream != null) {
                                                val jsonArr = JSONArray().apply {
                                                    sList.forEach { s ->
                                                        put(JSONObject().apply {
                                                            put("name", s.name)
                                                            put("url", s.url)
                                                            put("quality", s.quality)
                                                            put("isDirectVideo", s.isDirectVideo)
                                                            put("linkType", s.linkType.name)
                                                            if (s.headers.isNotEmpty()) {
                                                                val hObj = JSONObject()
                                                                s.headers.forEach { (k, v) -> hObj.put(k, v) }
                                                                put("headers", hObj)
                                                            }
                                                        })
                                                    }
                                                }
                                                val epJsonArr = JSONArray().apply {
                                                    episodes.forEach { epItem ->
                                                        put(JSONObject().apply {
                                                            put("number", epItem.number.toString())
                                                            put("title", epItem.title ?: "")
                                                            put("url", epItem.url)
                                                            if (!epItem.thumbnail.isNullOrBlank()) {
                                                                put("thumbnail", epItem.thumbnail)
                                                            }
                                                        })
                                                    }
                                                }
                                                onNavigateToPlayer(
                                                    VideoPlayerRoute(
                                                        animeTitle = matchedAnime?.title ?: titles.firstOrNull() ?: "",
                                                        episodeNumber = epNumStr,
                                                        initialQuality = chosenStream.quality,
                                                        initialUrl = chosenStream.url,
                                                        serversJson = jsonArr.toString(),
                                                        episodesJson = epJsonArr.toString(),
                                                        providerId = prov.id,
                                                    )
                                                )
                                            } else {
                                                showServerDialog = true
                                            }
                                        } catch (e: Exception) {
                                            Log.e("ANIMITE_DIAG", "GET_SERVERS_ERROR: ${e.message}")
                                            servers = emptyList()
                                            showServerDialog = true
                                        } finally {
                                            isFetchingServers = false
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

                            // Episode Number Badge (صورة الحلقة ورقمها فقط)
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
        Dialog(onDismissRequest = { isFetchingServers = false }) {
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

    // Quality Selection Dialog
    if (showServerDialog && selectedEpisode != null) {
        val epNumStr = selectedEpisode?.let { ep ->
            if (ep.number % 1.0f == 0.0f) ep.number.toInt().toString() else ep.number.toString()
        } ?: ""

        Dialog(onDismissRequest = { showServerDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.select_quality),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.episode_label, epNumStr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        IconButton(onClick = { showServerDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    val directStreams = remember(servers) {
                        servers.filter { it.isDirectVideo && it.linkType == StreamLinkType.DIRECT_VIDEO }
                            .distinctBy { it.quality }
                    }

                    if (directStreams.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_playable_quality),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            directStreams.forEach { stream ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                        .clickable {
                                            val prov = activeProvider ?: EpisodeSourcesManager.providers.first()
                                            val jsonArr = JSONArray().apply {
                                                directStreams.forEach { s ->
                                                    put(JSONObject().apply {
                                                        put("name", s.name)
                                                        put("url", s.url)
                                                        put("quality", s.quality)
                                                        put("isDirectVideo", s.isDirectVideo)
                                                        put("linkType", s.linkType.name)
                                                        if (s.headers.isNotEmpty()) {
                                                            val hObj = JSONObject()
                                                            s.headers.forEach { (k, v) -> hObj.put(k, v) }
                                                            put("headers", hObj)
                                                        }
                                                    })
                                                }
                                            }
                                            val epJsonArr = JSONArray().apply {
                                                episodes.forEach { ep ->
                                                    put(JSONObject().apply {
                                                        put("number", ep.number.toString())
                                                        put("title", ep.title ?: "")
                                                        put("url", ep.url)
                                                        if (!ep.thumbnail.isNullOrBlank()) {
                                                            put("thumbnail", ep.thumbnail)
                                                        }
                                                    })
                                                }
                                            }
                                            showServerDialog = false
                                            onNavigateToPlayer(
                                                VideoPlayerRoute(
                                                    animeTitle = matchedAnime?.title ?: titles.firstOrNull() ?: "",
                                                    episodeNumber = epNumStr,
                                                    initialQuality = stream.quality,
                                                    initialUrl = stream.url,
                                                    serversJson = jsonArr.toString(),
                                                    episodesJson = epJsonArr.toString(),
                                                    providerId = prov.id,
                                                )
                                            )
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Text(
                                            text = stream.quality,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            text = "HD",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
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
}
