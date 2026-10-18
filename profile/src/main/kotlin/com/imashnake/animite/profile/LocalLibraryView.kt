package com.imashnake.animite.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.imashnake.animite.core.ui.LocalPaddings
import com.imashnake.animite.core.ui.component.EmptyContentState
import com.imashnake.animite.core.ui.ext.crossfadeModel
import com.imashnake.animite.media.LibraryEntryEditSheet
import com.imashnake.animite.media.LibraryStatus
import com.imashnake.animite.media.LocalLibraryEntry
import com.imashnake.animite.media.MediaPage
import com.imashnake.animite.settings.SettingsPage

private enum class LibrarySortOption {
    RECENTLY_UPDATED,
    TITLE,
    SCORE,
    PROGRESS
}

@Composable
fun LocalLibraryView(
    entries: List<LocalLibraryEntry>,
    onNavigateToMediaItem: (MediaPage) -> Unit,
    onNavigateToSettings: (SettingsPage) -> Unit,
    onQuickIncrementEpisode: (Int, Int) -> Unit,
    onConnectAniList: () -> Unit,
    isLoggedIn: Boolean = false,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(LibrarySortOption.RECENTLY_UPDATED) }
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    var editingEntry by remember { mutableStateOf<LocalLibraryEntry?>(null) }

    val tabs = remember(entries) {
        listOf(
            "all" to entries.size,
            "watching" to entries.count { it.status == LibraryStatus.WATCHING },
            "completed" to entries.count { it.status == LibraryStatus.COMPLETED },
            "plan_to_watch" to entries.count { it.status == LibraryStatus.PLAN_TO_WATCH },
            "on_hold" to entries.count { it.status == LibraryStatus.ON_HOLD },
            "favorites" to entries.count { it.isFavorite }
        )
    }

    // Filter by tab
    val tabFiltered = remember(entries, selectedTabIndex) {
        when (selectedTabIndex) {
            1 -> entries.filter { it.status == LibraryStatus.WATCHING }
            2 -> entries.filter { it.status == LibraryStatus.COMPLETED }
            3 -> entries.filter { it.status == LibraryStatus.PLAN_TO_WATCH }
            4 -> entries.filter { it.status == LibraryStatus.ON_HOLD }
            5 -> entries.filter { it.isFavorite }
            else -> entries
        }
    }

    // Filter by search
    val searchFiltered = remember(tabFiltered, searchQuery) {
        if (searchQuery.isBlank()) tabFiltered
        else tabFiltered.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
    }

    // Sort
    val sortedEntries = remember(searchFiltered, sortOption) {
        when (sortOption) {
            LibrarySortOption.RECENTLY_UPDATED -> searchFiltered.sortedByDescending { it.lastUpdated }
            LibrarySortOption.TITLE -> searchFiltered.sortedBy { it.title.lowercase() }
            LibrarySortOption.SCORE -> searchFiltered.sortedByDescending { it.score ?: 0f }
            LibrarySortOption.PROGRESS -> searchFiltered.sortedByDescending { it.currentEpisode }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // Top Header: Local Profile & Settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                ) {
                    Text(
                        text = "★",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.my_library),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.guest_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isSearchActive = !isSearchActive }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Box {
                    IconButton(onClick = { isSortMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    DropdownMenu(
                        expanded = isSortMenuExpanded,
                        onDismissRequest = { isSortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_recently_updated)) },
                            onClick = {
                                sortOption = LibrarySortOption.RECENTLY_UPDATED
                                isSortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_title)) },
                            onClick = {
                                sortOption = LibrarySortOption.TITLE
                                isSortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_score)) },
                            onClick = {
                                sortOption = LibrarySortOption.SCORE
                                isSortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_progress)) },
                            onClick = {
                                sortOption = LibrarySortOption.PROGRESS
                                isSortMenuExpanded = false
                            }
                        )
                    }
                }

                IconButton(onClick = { onNavigateToSettings(SettingsPage) }) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Search Bar (Collapsible)
        AnimatedVisibility(visible = isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_library_hint)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        // Stats Bar Summary
        StatsSummaryRow(
            totalCount = entries.size,
            watchingCount = entries.count { it.status == LibraryStatus.WATCHING },
            completedCount = entries.count { it.status == LibraryStatus.COMPLETED },
            favoriteCount = entries.count { it.isFavorite },
            onConnectAniList = onConnectAniList,
            isLoggedIn = isLoggedIn,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Status Tabs Row
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 20.dp,
            divider = {},
            containerColor = Color.Transparent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
                )
            }
        ) {
            tabs.forEachIndexed { index, (key, count) ->
                val tabTitle = when (key) {
                    "watching" -> stringResource(R.string.tab_watching)
                    "completed" -> stringResource(R.string.tab_completed)
                    "plan_to_watch" -> stringResource(R.string.tab_plan_to_watch)
                    "on_hold" -> stringResource(R.string.tab_on_hold)
                    "favorites" -> stringResource(R.string.tab_favorites)
                    else -> stringResource(R.string.tab_all)
                }

                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tabTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                            )
                            if (count > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Content: Grid or Empty State
        if (sortedEntries.isEmpty()) {
            EmptyContentState(
                message = if (searchQuery.isNotBlank()) "لا توجد نتائج تطابق بحثك"
                else stringResource(R.string.empty_library_message) + "\n" + stringResource(R.string.empty_library_suggestion),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = sortedEntries,
                    key = { it.animeId }
                ) { entry ->
                    LocalLibraryCard(
                        entry = entry,
                        onClick = {
                            onNavigateToMediaItem(
                                MediaPage(
                                    id = entry.animeId,
                                    source = "local_library",
                                    mediaType = "ANIME"
                                )
                            )
                        },
                        onQuickIncrement = {
                            val nextEp = entry.currentEpisode + 1
                            onQuickIncrementEpisode(entry.animeId, nextEp)
                        },
                        onEdit = { editingEntry = entry }
                    )
                }
            }
        }
    }

    // Quick Edit Bottom Sheet
    editingEntry?.let { entry ->
        LibraryEntryEditSheet(
            animeId = entry.animeId,
            animeTitle = entry.title,
            coverImage = entry.coverImage,
            bannerImage = entry.bannerImage,
            totalEpisodes = entry.totalEpisodes,
            onDismissRequest = { editingEntry = null },
            onEntryChanged = { editingEntry = null }
        )
    }
}

@Composable
private fun StatsSummaryRow(
    totalCount: Int,
    watchingCount: Int,
    completedCount: Int,
    favoriteCount: Int,
    onConnectAniList: () -> Unit,
    isLoggedIn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(label = stringResource(R.string.tab_all), value = totalCount.toString())
                StatItem(label = stringResource(R.string.tab_watching), value = watchingCount.toString())
                StatItem(label = stringResource(R.string.tab_completed), value = completedCount.toString())
                StatItem(label = stringResource(R.string.tab_favorites), value = favoriteCount.toString())
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onConnectAniList() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isLoggedIn) stringResource(R.string.view_anilist_profile) else stringResource(R.string.connect_anilist),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LocalLibraryCard(
    entry: LocalLibraryEntry,
    onClick: () -> Unit,
    onQuickIncrement: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                AsyncImage(
                    model = crossfadeModel(entry.coverImage),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )

                // Status Tag Top-Start
                val (statusColor, statusText) = when (entry.status) {
                    LibraryStatus.WATCHING -> Color(0xFF2E7D32) to stringResource(R.string.tab_watching)
                    LibraryStatus.COMPLETED -> Color(0xFF1565C0) to stringResource(R.string.tab_completed)
                    LibraryStatus.PLAN_TO_WATCH -> Color(0xFF6A1B9A) to stringResource(R.string.tab_plan_to_watch)
                    LibraryStatus.ON_HOLD -> Color(0xFFEF6C00) to stringResource(R.string.tab_on_hold)
                    LibraryStatus.DROPPED -> Color(0xFFC62828) to stringResource(R.string.tab_dropped)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Heart Icon if Favorite
                if (entry.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Favorite",
                        tint = Color(0xFFE91E63),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }

                // Score pill top-end (if not favorite, or next to it)
                val entryScore = entry.score
                if (entryScore != null && entryScore > 0f) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(if (entry.isFavorite) Alignment.BottomEnd else Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = String.format("%.1f", entryScore),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Episode progress badge bottom-start
                val totalEps = entry.totalEpisodes
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = if (totalEps != null && totalEps > 0) {
                            "حلقة ${entry.currentEpisode} / $totalEps"
                        } else {
                            "حلقة ${entry.currentEpisode}"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Progress Bar at bottom
                val progressRatio = if (totalEps != null && totalEps > 0) {
                    (entry.currentEpisode.toFloat() / totalEps.toFloat()).coerceIn(0f, 1f)
                } else if (entry.status == LibraryStatus.COMPLETED) 1f else 0f

                LinearProgressIndicator(
                    progress = { progressRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }

            // Title & Quick Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Quick +1 Episode increment button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onQuickIncrement() }
                ) {
                    Text(
                        text = "+1",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
