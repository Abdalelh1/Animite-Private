package com.imashnake.animite.raw.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.imashnake.animite.navigation.VideoPlayerRoute
import com.imashnake.animite.raw.model.RawAnimeRelease
import com.imashnake.animite.raw.model.RawPlaybackAvailability
import com.imashnake.animite.raw.model.RawQuality
import com.imashnake.animite.raw.model.RawSourceDiagnostic
import com.imashnake.animite.raw.model.RawSourceId
import com.imashnake.animite.raw.subtitle.ImportedSubtitle
import com.imashnake.animite.raw.subtitle.OnlineSubtitle
import androidx.compose.runtime.mutableIntStateOf

@Composable
fun RawAnimeScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: (VideoPlayerRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RawAnimeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val currentTab by viewModel.currentTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val releases by viewModel.releases.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val importedSubtitles by viewModel.importedSubtitles.collectAsState()
    val onlineSubtitles by viewModel.onlineSubtitles.collectAsState()
    val isSearchingOnlineSubtitles by viewModel.isSearchingOnlineSubtitles.collectAsState()
    val onlineSubtitleError by viewModel.onlineSubtitleError.collectAsState()
    val onlineSubtitleLanguage by viewModel.onlineSubtitleLanguage.collectAsState()

    // SAF Document Picker launcher for subtitle files
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importSubtitle(uri) { success, msg ->
                Toast.makeText(context, msg ?: if (success) "تم الاستيراد بنجاح" else "فشل الاستيراد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Raw Anime (أنمي غير مترجم)",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.fetchDiagnostics() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Tab Row
                ScrollableTabRow(
                    selectedTabIndex = currentTab.ordinal,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[currentTab.ordinal]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = currentTab == RawSectionTab.RELEASES,
                        onClick = { viewModel.setTab(RawSectionTab.RELEASES) },
                        text = { Text("الإصدارات (Releases)") }
                    )
                    Tab(
                        selected = currentTab == RawSectionTab.SUBTITLES,
                        onClick = { viewModel.setTab(RawSectionTab.SUBTITLES) },
                        text = { Text("الترجمة (${importedSubtitles.size})") }
                    )
                    Tab(
                        selected = currentTab == RawSectionTab.DIAGNOSTICS,
                        onClick = { viewModel.setTab(RawSectionTab.DIAGNOSTICS) },
                        text = { Text("تشخيص المصادر") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                RawSectionTab.RELEASES -> {
                    ReleasesTabContent(
                        searchQuery = searchQuery,
                        onQueryChange = viewModel::onQueryChange,
                        selectedSource = selectedSource,
                        onSelectSource = viewModel::setSourceFilter,
                        selectedQuality = selectedQuality,
                        onSelectQuality = viewModel::setQualityFilter,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        releases = releases,
                        onPlayRelease = { rel, directUrl ->
                            onNavigateToPlayer(
                                VideoPlayerRoute(
                                    animeTitle = rel.animeTitle,
                                    episodeNumber = rel.episodeNumber ?: "1",
                                    initialUrl = directUrl,
                                    initialQuality = rel.bestQuality.label,
                                    providerId = rel.sourceId.name,
                                )
                            )
                        },
                        onCopyMagnet = { magnet ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Magnet URI", magnet)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "تم نسخ رابط Magnet للحافظة", Toast.LENGTH_SHORT).show()
                        },
                        onOpenExternalTorrent = { uriStr ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "لم يتم العثور على تطبيق تورنت لتشغيل الرابط", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                RawSectionTab.SUBTITLES -> {
                    SubtitlesTabContent(
                        importedSubtitles = importedSubtitles,
                        onlineSubtitles = onlineSubtitles,
                        isSearchingOnline = isSearchingOnlineSubtitles,
                        onlineSubtitleError = onlineSubtitleError,
                        onlineSubtitleLanguage = onlineSubtitleLanguage,
                        onSearchOnline = viewModel::searchOnlineSubtitles,
                        onSetLanguage = viewModel::setOnlineSubtitleLanguage,
                        onDownloadOnlineSub = { sub ->
                            viewModel.downloadOnlineSubtitle(sub) { success, msg ->
                                Toast.makeText(context, msg ?: if (success) "تم التنزيل بنجاح" else "فشل التنزيل", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenUrl = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onImportClick = {
                            subtitlePickerLauncher.launch(
                                arrayOf(
                                    "application/x-subrip",
                                    "text/x-ssa",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        onAdjustDelay = viewModel::adjustSubtitleDelay,
                        onDeleteSubtitle = viewModel::deleteSubtitle
                    )
                }
                RawSectionTab.DIAGNOSTICS -> {
                    DiagnosticsTabContent(
                        diagnostics = diagnostics,
                        onRefresh = viewModel::fetchDiagnostics
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleasesTabContent(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedSource: RawSourceId?,
    onSelectSource: (RawSourceId?) -> Unit,
    selectedQuality: RawQuality?,
    onSelectQuality: (RawQuality?) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    releases: List<RawAnimeRelease>,
    onPlayRelease: (RawAnimeRelease, String) -> Unit,
    onCopyMagnet: (String) -> Unit,
    onOpenExternalTorrent: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    val filteredReleases = remember(releases, selectedQuality) {
        if (selectedQuality == null) releases
        else releases.filter { it.downloadOptions.any { d -> d.quality == selectedQuality } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search TextField
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("ابحث عن أنمي راو أو رقم حلقة...", color = Color.Gray) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Source Filters
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedSource == null,
                    onClick = { onSelectSource(null) },
                    label = { Text("جميع المصادر") }
                )
            }
            items(RawSourceId.entries.toTypedArray()) { src ->
                FilterChip(
                    selected = selectedSource == src,
                    onClick = { onSelectSource(if (selectedSource == src) null else src) },
                    label = { Text(src.displayName) }
                )
            }
        }

        // Quality Filters
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedQuality == null,
                    onClick = { onSelectQuality(null) },
                    label = { Text("جميع الجودات") }
                )
            }
            items(listOf(RawQuality.Q_1080P, RawQuality.Q_720P, RawQuality.Q_480P)) { q ->
                FilterChip(
                    selected = selectedQuality == q,
                    onClick = { onSelectQuality(if (selectedQuality == q) null else q) },
                    label = { Text(q.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Loading or Content
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (errorMessage != null && filteredReleases.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (filteredReleases.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد إصدارات حالياً",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredReleases, key = { it.id }) { item ->
                    RawReleaseCard(
                        release = item,
                        onPlay = { directUrl -> onPlayRelease(item, directUrl) },
                        onCopyMagnet = onCopyMagnet,
                        onOpenExternal = onOpenExternalTorrent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RawReleaseCard(
    release: RawAnimeRelease,
    onPlay: (String) -> Unit,
    onCopyMagnet: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Source & Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source Name
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = release.sourceId.displayName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (!release.episodeNumber.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "حلقة ${release.episodeNumber}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Anime Show Title
            Text(
                text = release.animeTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Release filename/raw title
            Text(
                text = release.rawTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata info: date, size, seeders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                release.releaseDate?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                release.downloadOptions.firstOrNull()?.formattedSize?.let { size ->
                    Text(
                        text = "الحجم: $size",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                if (release.seeders != null) {
                    Text(
                        text = "Seeds: ${release.seeders}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Available Qualities & Actions
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (opt in release.downloadOptions) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = opt.resolutionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val directUrl = release.directPlayableUrl
                if (directUrl != null) {
                    Button(
                        onClick = { onPlay(directUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تشغيل مباشر")
                    }
                } else {
                    // Torrent only notice & options
                    release.primaryMagnet?.let { magnet ->
                        OutlinedButton(
                            onClick = { onCopyMagnet(magnet) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ Magnet", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onOpenExternal(magnet) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("فتح بالتورنت", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitlesTabContent(
    importedSubtitles: List<ImportedSubtitle>,
    onlineSubtitles: List<OnlineSubtitle>,
    isSearchingOnline: Boolean,
    onlineSubtitleError: String?,
    onlineSubtitleLanguage: String,
    onSearchOnline: (String, Int?) -> Unit,
    onSetLanguage: (String) -> Unit,
    onDownloadOnlineSub: (OnlineSubtitle) -> Unit,
    onOpenUrl: (String) -> Unit,
    onImportClick: () -> Unit,
    onAdjustDelay: (ImportedSubtitle, Long) -> Unit,
    onDeleteSubtitle: (String) -> Unit,
) {
    var subTabSelection by remember { mutableIntStateOf(0) } // 0 = Online Search, 1 = Imported / Local
    var queryText by remember { mutableStateOf("") }
    var episodeText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sub-Tab Switcher
        ScrollableTabRow(
            selectedTabIndex = subTabSelection,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp,
            divider = {},
            indicator = { tabPositions ->
                if (subTabSelection < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[subTabSelection]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            Tab(
                selected = subTabSelection == 0,
                onClick = { subTabSelection = 0 },
                text = {
                    Text(
                        text = "بحث أونلاين (OpenSubtitles)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            )
            Tab(
                selected = subTabSelection == 1,
                onClick = { subTabSelection = 1 },
                text = {
                    Text(
                        text = "الملفات المحفوظة (${importedSubtitles.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (subTabSelection == 0) {
            // Online Search Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ابحث عن ترجمات عربية أو إنجليزية عبر OpenSubtitles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = queryText,
                            onValueChange = { queryText = it },
                            placeholder = { Text("اسم الأنمي (مثال: Frieren, Naruto)", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = episodeText,
                            onValueChange = { episodeText = it.filter { char -> char.isDigit() } },
                            placeholder = { Text("حلقة", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.width(70.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Language Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val languages = listOf(
                            "ar,en" to "الكل",
                            "ar" to "العربية 🇸🇦",
                            "en" to "English 🇬🇧"
                        )
                        languages.forEach { (code, label) ->
                            val isSelected = onlineSubtitleLanguage == code
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSetLanguage(code) },
                                label = { Text(label, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                val ep = episodeText.toIntOrNull()
                                onSearchOnline(queryText, ep)
                            },
                            enabled = queryText.isNotBlank() && !isSearchingOnline,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSearchingOnline) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("بحث", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (onlineSubtitleError != null && onlineSubtitles.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = onlineSubtitleError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (onlineSubtitles.isEmpty() && !isSearchingOnline && onlineSubtitleError == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "أدخل اسم الأنمي واضغط بحث لعرض ملفات الترجمة من OpenSubtitles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(onlineSubtitles, key = { it.id }) { sub ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (sub.languageCode == "ar") {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        }
                                    ) {
                                        Text(
                                            text = sub.languageName,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    if (sub.fps > 0.0) {
                                        Text(
                                            text = "${sub.fps} FPS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = sub.fileName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (sub.release.isNotBlank() && sub.release != sub.fileName) {
                                    Text(
                                        text = "الإصدار: ${sub.release}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { onDownloadOnlineSub(sub) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تنزيل واستيراد", fontSize = 12.sp)
                                    }

                                    sub.pageUrl?.let { pageUrl ->
                                        OutlinedButton(
                                            onClick = { onOpenUrl(pageUrl) },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("عرض الصفحة", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Local / Imported Subtitles Section
            Button(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("استيراد ملف ترجمة محلي (.srt, .ass, .ssa)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "يتم الكشف التلقائي عن ترميز ملفات الترجمة (Windows-1256 / UTF-8) وتصحيحها فوراً لمنع تشوه النصوص العربية. يمكنك أيضاً ضبط تأخير الترجمة بالملي ثانية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (importedSubtitles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد ملفات ترجمة محفوظة بعد.\nاستخدم زر الاستيراد أعلاه أو ابحث ونزّل من OpenSubtitles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(importedSubtitles, key = { it.id }) { sub ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = sub.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onDeleteSubtitle(sub.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = sub.languageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = sub.format.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (sub.delayMs != 0L) {
                                        Text(
                                            text = "التأخير: ${if (sub.delayMs > 0) "+" else ""}${sub.delayMs}ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Delay Adjust Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onAdjustDelay(sub, -500L) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("-0.5s", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustDelay(sub, 500L) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("+0.5s", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustDelay(sub, -sub.delayMs) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("إعادة ضبط", fontSize = 11.sp)
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

@Composable
private fun DiagnosticsTabContent(
    diagnostics: List<RawSourceDiagnostic>,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "حالة مصادر الـ Raw المستقلة",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (diagnostics.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(diagnostics) { diag ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = diag.sourceId.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                val statusColor = if (diag.isReachable) Color(0xFF4CAF50) else Color(0xFFE53935)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (diag.isReachable) "متصل" else "غير متاح",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = statusColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "الرابط: ${diag.sourceId.baseUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "التشخيص: ${diag.statusMessage}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (diag.isReachable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )

                            if (diag.isBlockedByCloudflare) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "ملاحظة: هذا المصدر محمي بـ Cloudflare Bot Challenge ولا يمكن الوصول إليه تلقائياً من خوادم التطبيق دون تخطي القيود.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFA000)
                                )
                            } else if (diag.isSiteDefunct) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "ملاحظة: خادم الموقع الأصلي يعيد رمز 404، وقد يكون النطاق متوقفاً أو تم تعديل بنية مساراته.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFA000)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
