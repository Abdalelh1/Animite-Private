package com.imashnake.animite.social.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.Comment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Twitter/X-style social profile, skinned with Animite's own Material theme
 * (dynamic colour scheme + Cairo/Animite typography) — no Twitter branding
 * colours are hardcoded; every colour below is a theme token so the screen
 * follows the app's time-of-day palette, dark/AMOLED and system schemes.
 *
 * Layout (single column, content capped at 600.dp and centred on large
 * screens, mirroring Twitter's profile):
 *  - app bar: display name + post count, back / share / overflow actions
 *  - cover banner with the avatar overlapping its bottom edge
 *  - action row: Edit Profile (own) or Message + Follow/Following (others)
 *  - name, @handle, bio, joined date, Following/Followers stats
 *  - Posts / Replies / Media tabs in a pager with an animated indicator
 *
 * "Posts" are the comments this user authored (see CommentRepository
 * .getCommentsByUser); Media aggregates the anime/episodes they posted about.
 * See docs/profile-redesign/DESIGN.md for tokens, states and QA checklists.
 */
private enum class ProfileTab(val title: String) {
    POSTS("Posts"),
    REPLIES("Replies"),
    MEDIA("Media"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onNavigateToFollowers: (userId: String) -> Unit,
    onNavigateToFollowing: (userId: String) -> Unit,
    onNavigateToChat: (userId: String, displayName: String, avatar: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val user by viewModel.userProfile.collectAsState()
    val posts by viewModel.userPosts.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val avatarUploadState by viewModel.avatarUploadState.collectAsState()
    val isUploadingAvatar = avatarUploadState is AvatarUploadState.Loading

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImageSelected(context, uri)
        } else {
            viewModel.onPhotoPickerCancelled()
        }
    }

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onBannerImageSelected(context, uri)
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showUnfollowDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            when (msg) {
                is ProfileUiMessage.Success -> snackbarHostState.showSnackbar(msg.message)
                is ProfileUiMessage.Error -> snackbarHostState.showSnackbar(msg.message)
            }
            viewModel.clearUiMessage()
        }
    }

    LaunchedEffect(avatarUploadState) {
        if (avatarUploadState is AvatarUploadState.Error) {
            val errorState = avatarUploadState as AvatarUploadState.Error
            val result = snackbarHostState.showSnackbar(
                message = errorState.message,
                actionLabel = if (errorState.canRetry) "Retry" else null,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retryAvatarUpload()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = user?.displayName ?: "Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (posts != null) {
                            Text(
                                text = "${formatCount(posts!!.size)} posts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out ${user?.displayName} on Animite! @${user?.username}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }

                    if (!viewModel.isCurrentUser) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isBlocked) "Unblock User" else "Block User") },
                                    onClick = {
                                        showMenu = false
                                        if (isBlocked) viewModel.unblockUser() else showBlockConfirmDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Report User", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showReportDialog = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (user == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val profile = user!!
            val allPosts = posts
            val topLevelPosts = remember(allPosts) { allPosts?.filter { it.parentId == null } }
            val replies = remember(allPosts) { allPosts?.filter { it.parentId != null } }
            val mediaGroups = remember(allPosts) { allPosts?.let { groupMediaByAnime(it) } }

            // Centre the column and cap its width on tablets/foldables.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                ) {
                    ProfileHeader(
                        profile = profile,
                        isCurrentUser = viewModel.isCurrentUser,
                        isFollowing = isFollowing,
                        isUploadingAvatar = isUploadingAvatar,
                        onAvatarClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onBannerClick = {
                            bannerPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onEditClick = { showEditDialog = true },
                        onFollowClick = { viewModel.toggleFollow() },
                        onUnfollowClick = { showUnfollowDialog = true },
                        onMessageClick = {
                            onNavigateToChat(profile.uid, profile.displayName, profile.photoUrl)
                        },
                        onFollowersClick = { onNavigateToFollowers(profile.uid) },
                        onFollowingClick = { onNavigateToFollowing(profile.uid) },
                    )

                    val pagerState = rememberPagerState(pageCount = { ProfileTab.entries.size })

                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                    ) {
                        ProfileTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    Text(
                                        text = tab.title,
                                        fontWeight = if (pagerState.currentPage == index) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Medium
                                        },
                                        color = if (pagerState.currentPage == index) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        when (ProfileTab.entries[page]) {
                            ProfileTab.POSTS -> PostsTabContent(
                                posts = topLevelPosts,
                                profile = profile,
                                emptyText = if (viewModel.isCurrentUser) {
                                    "You haven't posted yet.\nComment on an anime to start the conversation."
                                } else {
                                    "No posts yet."
                                }
                            )

                            ProfileTab.REPLIES -> PostsTabContent(
                                posts = replies,
                                profile = profile,
                                emptyText = "No replies yet."
                            )

                            ProfileTab.MEDIA -> MediaTabContent(
                                groups = mediaGroups,
                                emptyText = "No media yet.\nAnime and episodes this user posts about will appear here."
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Profile Dialog
    if (showEditDialog && user != null) {
        EditProfileDialog(
            user = user!!,
            onDismiss = { showEditDialog = false },
            onSave = { displayName, bio, isPrivate, allowMessages ->
                viewModel.updateProfile(displayName, bio, isPrivate, allowMessages)
                showEditDialog = false
            },
            onChangeAvatar = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            isUploadingAvatar = isUploadingAvatar
        )
    }

    // Unfollow Confirmation Dialog (Twitter asks before unfollowing)
    if (showUnfollowDialog && user != null) {
        AlertDialog(
            onDismissRequest = { showUnfollowDialog = false },
            title = { Text("Unfollow ${user!!.handle}?") },
            text = { Text("Their posts will no longer show up in your home timeline. You can still view their profile.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.toggleFollow()
                        showUnfollowDialog = false
                    }
                ) {
                    Text("Unfollow", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfollowDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block Confirmation Dialog
    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { Text("Block ${user?.displayName}?") },
            text = { Text("They will no longer be able to message you or see your comments, and you will unfollow each other.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.blockUser()
                        showBlockConfirmDialog = false
                    }
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Report Dialog
    if (showReportDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report User") },
            text = {
                Column {
                    Text("Please explain why you are reporting this account:")
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        placeholder = { Text("e.g. Spam, harassment, inappropriate content...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (reason.isNotBlank()) {
                            viewModel.reportUser(reason)
                            showReportDialog = false
                        }
                    }
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileHeader(
    profile: AnimiteUser,
    isCurrentUser: Boolean,
    isFollowing: Boolean,
    isUploadingAvatar: Boolean,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit,
    onEditClick: () -> Unit,
    onFollowClick: () -> Unit,
    onUnfollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
) {
    val context = LocalContext.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Responsive breakpoints: compact phones shrink cover + avatar.
        val compact = maxWidth < 360.dp
        val bannerHeight = if (compact) 120.dp else 150.dp
        val avatarSize = if (compact) 68.dp else 84.dp

        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight + avatarSize / 2 + 12.dp)
            ) {
                // Cover banner (own profile: tap to change)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bannerHeight)
                        .align(Alignment.TopCenter)
                        .then(
                            if (isCurrentUser) {
                                Modifier.clickable { onBannerClick() }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (!profile.bannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.bannerUrl,
                            contentDescription = "Profile cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                        )
                    }

                    if (isCurrentUser) {
                        Surface(
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Change cover photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                // Avatar (overlapping the banner) + action buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .align(Alignment.BottomCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(avatarSize)) {
                        AsyncImage(
                            model = profile.photoUrl,
                            contentDescription = "Profile picture of ${profile.displayName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = isCurrentUser && !isUploadingAvatar) {
                                    onAvatarClick()
                                }
                        )

                        if (isUploadingAvatar) {
                            Surface(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                                shape = CircleShape,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        if (isCurrentUser) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    .clickable(enabled = !isUploadingAvatar) { onAvatarClick() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Change profile picture",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (isCurrentUser) {
                        OutlinedButton(
                            onClick = onEditClick,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit profile", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onMessageClick,
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Chat,
                                    contentDescription = "Send a message",
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            FollowToggleButton(
                                isFollowing = isFollowing,
                                onFollowClick = onFollowClick,
                                onUnfollowClick = onUnfollowClick
                            )
                        }
                    }
                }
            }

            // Identity block
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (profile.isPrivate) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Private account",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = profile.handle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (profile.bio.isNotBlank()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                // Meta row: joined date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Joined ${joinedDateText(profile.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Stats row (Twitter order: Following, then Followers)
                Row(
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    StatItem(
                        count = profile.followingCount,
                        label = "Following",
                        onClick = onFollowingClick
                    )
                    StatItem(
                        count = profile.followersCount,
                        label = "Followers",
                        onClick = onFollowersClick
                    )
                }

                // Linked AniList account (AniList's own brand colours, scoped to this card)
                if (profile.isAnilistLinked) {
                    Card(
                        onClick = {
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://anilist.co/user/${profile.anilistUsername}")
                            )
                            context.startActivity(browserIntent)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0B1622)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0xFF02A9FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("AL", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Linked AniList Account",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF8BA0B2)
                                    )
                                    Text(
                                        text = profile.anilistUsername.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Text(
                                text = "View ↗",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF02A9FF),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (profile.isPrivate) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "This account is private.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Text(
            text = formatCount(count),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Follow pill. While following it is outlined; pressing it previews the
 * destructive "Unfollow" state in the error colour (Twitter's hover
 * behaviour, translated to touch), and tapping it asks for confirmation
 * instead of unfollowing immediately.
 */
@Composable
private fun FollowToggleButton(
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    onUnfollowClick: () -> Unit,
) {
    if (!isFollowing) {
        Button(
            onClick = onFollowClick,
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Follow", fontWeight = FontWeight.Bold)
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        OutlinedButton(
            onClick = onUnfollowClick,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(24.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = if (isPressed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        ) {
            Text(
                text = if (isPressed) "Unfollow" else "Following",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PostsTabContent(
    posts: List<Comment>?,
    profile: AnimiteUser,
    emptyText: String,
) {
    when {
        posts == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        posts.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(posts, key = { it.id }) { comment ->
                    ProfilePostRow(comment = comment, profile = profile)
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfilePostRow(comment: Comment, profile: AnimiteUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        AsyncImage(
            model = profile.photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${profile.handle} · ${relativeTimeText(comment.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = comment.displayContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Where the post was made: an anime discussion or a single episode.
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = if (comment.targetType == "episode" && !comment.episodeNumber.isNullOrBlank()) {
                        "Episode ${comment.episodeNumber} · Anime #${comment.animeId}"
                    } else {
                        "Anime #${comment.animeId}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = "Replies",
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatCount(comment.replyCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = "Likes",
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatCount(comment.likesCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class MediaGroup(
    val animeId: Int,
    val postsCount: Int,
    val episodes: List<String>,
    val hasEpisodePosts: Boolean,
)

private fun groupMediaByAnime(posts: List<Comment>): List<MediaGroup> =
    posts.groupBy { it.animeId }
        .map { (animeId, group) ->
            MediaGroup(
                animeId = animeId,
                postsCount = group.size,
                episodes = group.mapNotNull { it.episodeNumber }.distinct().take(3),
                hasEpisodePosts = group.any { it.targetType == "episode" }
            )
        }
        .sortedByDescending { it.postsCount }

@Composable
private fun MediaTabContent(groups: List<MediaGroup>?, emptyText: String) {
    when {
        groups == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        groups.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(groups, key = { it.animeId }) { group ->
                    MediaTile(group = group)
                }
            }
        }
    }
}

@Composable
private fun MediaTile(group: MediaGroup) {
    val gradientPairs = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.secondaryContainer,
    )
    val (start, end) = gradientPairs[kotlin.math.abs(group.animeId) % gradientPairs.size]

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Brush.linearGradient(listOf(start, end)))
            .padding(8.dp)
    ) {
        if (group.hasEpisodePosts) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Episode posts",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(18.dp)
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Text(
                text = "${group.postsCount}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = "Anime #${group.animeId}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (group.episodes.isNotEmpty()) {
                Text(
                    text = "Ep. ${group.episodes.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Twitter-style compact counts: 950, 1.2K, 3.4M. */
private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> trimTrailingZero(count / 1_000_000.0) + "M"
    count >= 1_000 -> trimTrailingZero(count / 1_000.0) + "K"
    else -> count.toString()
}

private fun trimTrailingZero(value: Double): String {
    val text = String.format(Locale.US, "%.1f", value)
    return text.removeSuffix(".0")
}

private fun joinedDateText(createdAtMillis: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(createdAtMillis))

private fun relativeTimeText(createdAtMillis: Long): String {
    val diffMillis = System.currentTimeMillis() - createdAtMillis
    val minutes = diffMillis / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        days < 365 -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(createdAtMillis))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(createdAtMillis))
    }
}
