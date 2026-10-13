package com.imashnake.animite.media.source

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.imashnake.animite.core.ui.LocalPaddings
import com.imashnake.animite.media.R
import com.imashnake.animite.navigation.VideoPlayerRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaEpisodesSection(
    titles: List<String>,
    onNavigateToPlayer: (VideoPlayerRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedProvider by remember { mutableStateOf(EpisodeSourcesManager.providers.first()) }

    var isLoadingEpisodes by remember { mutableStateOf(false) }
    var episodes by remember { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
    var matchedAnime by remember { mutableStateOf<AnimeSearchResult?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var selectedEpisode by remember { mutableStateOf<AnimeEpisode?>(null) }
    var isFetchingServers by remember { mutableStateOf(false) }
    var servers by remember { mutableStateOf<List<AnimeStreamServer>>(emptyList()) }
    var showServerDialog by remember { mutableStateOf(false) }

    // Fetch episodes when provider or titles change
    LaunchedEffect(selectedProvider, titles) {
        if (titles.isEmpty()) return@LaunchedEffect

        isLoadingEpisodes = true
        searchError = null
        episodes = emptyList()
        matchedAnime = null

        withContext(Dispatchers.IO) {
            try {
                val (match, _) = EpisodeSourcesManager.autoFindAnime(selectedProvider, titles)
                if (match != null) {
                    matchedAnime = match
                    val fetchedEpisodes = selectedProvider.getEpisodes(match.url)
                    episodes = fetchedEpisodes
                } else {
                    searchError = "NOT_FOUND"
                }
            } catch (e: Exception) {
                searchError = e.localizedMessage ?: "ERROR"
            } finally {
                isLoadingEpisodes = false
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LocalPaddings.current.medium),
    ) {
        // Section Header
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
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.episode_sources),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (episodes.isNotEmpty()) {
                Text(
                    text = "${episodes.size} ${stringResource(R.string.episodes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Provider Selector Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EpisodeSourcesManager.providers.forEach { provider ->
                val isSelected = provider.id == selectedProvider.id
                val chipBg = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
                val chipText = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(chipBg)
                        .clickable {
                            if (!isSelected) {
                                selectedProvider = provider
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${provider.name} (${if (provider.language == "ar") "عربي" else "EN"})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = chipText,
                    )
                }
            }
        }

        // Content Area (Loading, Empty, or Episodes Grid/List)
        when {
            isLoadingEpisodes -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.searching_source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            episodes.isNotEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    matchedAnime?.let {
                        Text(
                            text = it.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Episodes Responsive Grid
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        episodes.forEach { ep ->
                            val epNumStr = if (ep.number % 1.0f == 0.0f) {
                                ep.number.toInt().toString()
                            } else {
                                ep.number.toString()
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                    .clickable {
                                        android.util.Log.d("ANIMITE_DIAG", "EPISODE_CLICK: number=${ep.number}")
                                        android.util.Log.d("ANIMITE_DIAG", "SOURCE_SELECTED: providerId=${selectedProvider.id}")
                                        selectedEpisode = ep
                                        isFetchingServers = true
                                        coroutineScope.launch {
                                            try {
                                                android.util.Log.d("ANIMITE_DIAG", "GET_SERVERS_START: providerId=${selectedProvider.id}")
                                                val sList = withContext(Dispatchers.IO) {
                                                    selectedProvider.getStreamServers(ep.url)
                                                }
                                                android.util.Log.d("ANIMITE_DIAG", "GET_SERVERS_RESULT: count=${sList.size}")
                                                servers = sList

                                                val directStreams = sList.filter { it.isDirectVideo && it.linkType == StreamLinkType.DIRECT_VIDEO }
                                                    .distinctBy { it.quality }

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
                                                            providerId = selectedProvider.id,
                                                        )
                                                    )
                                                } else {
                                                    showServerDialog = true
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("ANIMITE_DIAG", "GET_SERVERS_ERROR: ${e.javaClass.simpleName}: ${e.message}")
                                                servers = emptyList()
                                                showServerDialog = true
                                            } finally {
                                                isFetchingServers = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.episode_label, epNumStr),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_episodes_found),
                        style = MaterialTheme.typography.bodySmall,
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
                shape = RoundedCornerShape(16.dp),
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

    // Server Selection Dialog
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
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                        .clickable {
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
                                                    providerId = selectedProvider.id,
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
