package com.imashnake.animite.social.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import com.imashnake.animite.social.model.AuthState
import com.imashnake.animite.social.ui.auth.AuthContent
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.core.view.WindowCompat

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.Comment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
private enum class ProfileTab(@param:androidx.annotation.StringRes val titleRes: Int) {
    POSTS(com.imashnake.animite.social.R.string.posts),
    REPLIES(com.imashnake.animite.social.R.string.replies),
    MEDIA(com.imashnake.animite.social.R.string.media),
    LIKES(com.imashnake.animite.social.R.string.likes),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToFollowers: (userId: String) -> Unit,
    onNavigateToFollowing: (userId: String) -> Unit,
    onNavigateToChat: (userId: String, displayName: String, avatar: String?) -> Unit,
    onNavigateToEditProfile: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToAccountSettings: (() -> Unit)? = null,
    onNavigateToOwnerControlCenter: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsState()
    val user by viewModel.userProfile.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val posts by viewModel.userPosts.collectAsState()
    val animeStats by viewModel.animeStats.collectAsState()
    val libraryEntries by viewModel.libraryEntries.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val avatarUploadState by viewModel.avatarUploadState.collectAsState()
    val isUploadingAvatar = avatarUploadState is AvatarUploadState.Loading

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }

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

    val liveCoverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onLiveCoverSelected(context, uri)
        } else {
            viewModel.onLiveCoverPickerCancelled()
        }
    }

    val liveCoverUploadState by viewModel.liveCoverUploadState.collectAsState()
    val isUploadingLiveCover = liveCoverUploadState is LiveCoverUploadState.Uploading || liveCoverUploadState is LiveCoverUploadState.Validating

    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showUnfollowDialog by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showAvatarOptionsDialog by remember { mutableStateOf(false) }
    var showCoverOptionsDialog by remember { mutableStateOf(false) }
    var showRoleManagementDialog by remember { mutableStateOf(false) }

    LaunchedEffect(liveCoverUploadState) {
        if (liveCoverUploadState is LiveCoverUploadState.Error) {
            val errorState = liveCoverUploadState as LiveCoverUploadState.Error
            val result = snackbarHostState.showSnackbar(
                message = errorState.message,
                actionLabel = if (errorState.canRetry) "Retry" else null,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retryLiveCoverUpload()
            }
        }
    }

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
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity() ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)
        val originalStatusBarColor = window.statusBarColor
        val originalAppearanceLightStatusBars = insetsController.isAppearanceLightStatusBars

        // 1. Transparent status bar so cover image flows seamlessly behind it
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // 2. High-contrast light (white) status bar icons against the cover image
        insetsController.isAppearanceLightStatusBars = false

        onDispose {
            window.statusBarColor = originalStatusBarColor
            insetsController.isAppearanceLightStatusBars = originalAppearanceLightStatusBars
        }
    }

    val bottomPadding = if (onBack == null) {
        dimensionResource(com.imashnake.animite.navigation.R.dimen.navigation_bar_height) +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    } else {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { _ ->
        val isOwnProfile = viewModel.isViewingOwnProfile
        if (isOwnProfile && authState is AuthState.Unauthenticated) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.TopCenter
            ) {
                AuthContent(
                    onSettingsClick = onNavigateToSettings,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 540.dp)
                        .padding(bottom = bottomPadding)
                )
            }
        } else if (user == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "جاري التحقق والمزامنة...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            key(viewModel.targetUserId) {
                val profile = user!!
            val allPosts = posts
            val topLevelPosts = remember(allPosts) { allPosts?.filter { it.parentId == null } }
            val replies = remember(allPosts) { allPosts?.filter { it.parentId != null } }
            val mediaGroups = remember(allPosts) { allPosts?.let { groupMediaByAnime(it) } }

            // Centre the column and cap its width on tablets/foldables.
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                ) {
                    val accentColor = remember(profile.customization.accentColor) {
                        try {
                            Color(android.graphics.Color.parseColor(profile.customization.accentColor))
                        } catch (e: Exception) {
                            Color(0xFF00D1FF)
                        }
                    }

                    ProfileHeader(
                        profile = profile,
                        stats = animeStats,
                        accentColor = accentColor,
                        isCurrentUser = viewModel.isCurrentUser,
                        isFollowing = isFollowing,
                        isUploadingAvatar = isUploadingAvatar,
                        onBack = onBack,
                        onShare = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Check out ${profile.displayName} on Animite! @${profile.username}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
                        },
                        onSettingsClick = if (viewModel.isCurrentUser) onNavigateToSettings else null,
                        onMoreClick = if (!viewModel.isCurrentUser) { { showMenu = true } } else null,
                        showMenu = showMenu,
                        onDismissMenu = { showMenu = false },
                        isBlocked = isBlocked,
                        onUnblockClick = { viewModel.unblockUser() },
                        onBlockClick = { showBlockConfirmDialog = true },
                        onReportClick = { showReportDialog = true },
                        onAvatarClick = {
                            if (viewModel.isCurrentUser) {
                                if (!profile.photoUrl.isNullOrBlank()) {
                                    showAvatarOptionsDialog = true
                                } else {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            } else {
                                if (!profile.photoUrl.isNullOrBlank()) {
                                    previewImageUrl = profile.photoUrl
                                }
                            }
                        },
                        onBannerClick = {
                            if (viewModel.isCurrentUser) {
                                showCoverOptionsDialog = true
                            } else {
                                if (!profile.liveCoverPosterUrl.isNullOrBlank()) {
                                    previewImageUrl = profile.liveCoverPosterUrl
                                } else if (!profile.bannerUrl.isNullOrBlank()) {
                                    previewImageUrl = profile.bannerUrl
                                }
                            }
                        },
                        onEditClick = {
                            if (onNavigateToEditProfile != null) {
                                onNavigateToEditProfile()
                            } else {
                                showEditDialog = true
                            }
                        },
                        onFollowClick = { viewModel.toggleFollow() },
                        onUnfollowClick = { showUnfollowDialog = true },
                        onMessageClick = {
                            onNavigateToChat(profile.uid, profile.displayName, profile.photoUrl)
                        },
                        onFollowersClick = { onNavigateToFollowers(profile.uid) },
                        onFollowingClick = { onNavigateToFollowing(profile.uid) },
                        canManageRoles = (currentUserProfile?.isOwner == true || currentUserProfile?.isAdmin == true) && !viewModel.isCurrentUser,
                        onManageRolesClick = { showRoleManagementDialog = true },
                        onOwnerControlCenterClick = if (currentUserProfile?.isOwner == true) onNavigateToOwnerControlCenter else null,
                    )

                    val pagerState = rememberPagerState(pageCount = { ProfileTab.entries.size })
                    androidx.compose.material3.TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        indicator = { tabPositions ->
                            if (pagerState.currentPage < tabPositions.size) {
                                androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    height = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        divider = {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ProfileTab.entries.forEachIndexed { index, tab ->
                            val isSelected = pagerState.currentPage == index
                            Tab(
                                selected = isSelected,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(tab.titleRes),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                                        maxLines = 1,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                                    androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.my_posts_empty)
                                } else {
                                    androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.no_posts_yet)
                                },
                                contentPadding = PaddingValues(bottom = bottomPadding)
                            )

                            ProfileTab.REPLIES -> PostsTabContent(
                                posts = replies,
                                profile = profile,
                                emptyText = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.no_replies_yet),
                                contentPadding = PaddingValues(bottom = bottomPadding)
                            )

                            ProfileTab.MEDIA -> MediaTabContent(
                                groups = mediaGroups,
                                emptyText = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.no_media_yet),
                                contentPadding = PaddingValues(bottom = bottomPadding)
                            )

                            ProfileTab.LIKES -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.no_posts_yet),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

    // Edit Profile Dialog
    if (showEditDialog && user != null) {
        EditProfileDialog(
            user = user!!,
            onDismiss = { showEditDialog = false },
            onSave = { displayName, bio, location, websiteUrl, isPrivate, allowMessages, username ->
                viewModel.updateProfile(displayName, bio, location, websiteUrl, isPrivate, allowMessages, username)
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

    // Avatar Options Dialog (for current user: View photo vs Change photo)
    if (showAvatarOptionsDialog && user != null) {
        AlertDialog(
            onDismissRequest = { showAvatarOptionsDialog = false },
            title = { Text(user!!.displayName, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            showAvatarOptionsDialog = false
                            previewImageUrl = user?.photoUrl
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("عرض الصورة", style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(
                        onClick = {
                            showAvatarOptionsDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تغيير الصورة", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarOptionsDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Cover Options Dialog (for current user)
    if (showCoverOptionsDialog && user != null) {
        AlertDialog(
            onDismissRequest = { showCoverOptionsDialog = false },
            title = {
                Text(
                    text = "خيارات غلاف الملف الشخصي",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isUploadingLiveCover) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(
                                text = if (liveCoverUploadState is LiveCoverUploadState.Validating) "جاري التحقق من الوسائط..." else "جاري رفع الغلاف المتحرك...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        TextButton(
                            onClick = {
                                showCoverOptionsDialog = false
                                liveCoverPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("تعيين غلاف متحرك (فيديو MP4 أو GIF)", style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        TextButton(
                            onClick = {
                                showCoverOptionsDialog = false
                                bannerPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("تعيين غلاف ثابت (صورة)", style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        if (!user!!.liveCoverUrl.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    showCoverOptionsDialog = false
                                    viewModel.removeLiveCover()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text("إزالة الغلاف المتحرك", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (!user!!.liveCoverPosterUrl.isNullOrBlank() || !user!!.bannerUrl.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    showCoverOptionsDialog = false
                                    previewImageUrl = user!!.liveCoverPosterUrl ?: user!!.bannerUrl
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = null)
                                    Text("عرض صورة الغلاف", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCoverOptionsDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Role Management Dialog (for Owner / Admin)
    if (showRoleManagementDialog && user != null) {
        var selectedRole by remember { mutableStateOf(user!!.role) }
        var customBadge by remember { mutableStateOf(user!!.customBadgeTitle ?: "") }
        var isVerifiedChecked by remember { mutableStateOf(user!!.isVerified) }

        AlertDialog(
            onDismissRequest = { showRoleManagementDialog = false },
            title = {
                Text(
                    text = "إدارة الرتب والشارات",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "المستخدم: ${user!!.displayName} (@${user!!.username})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Role selector
                    Text("الرتبة:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("user" to "مستخدم", "moderator" to "مشرف", "admin" to "أدمن", "owner" to "مالك").forEach { (roleKey, roleTitle) ->
                            val isSelected = selectedRole.equals(roleKey, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedRole = roleKey }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = roleTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Custom Badge Title
                    Text("لقب الشارة المخصص (اختياري):", style = MaterialTheme.typography.labelMedium)
                    androidx.compose.material3.OutlinedTextField(
                        value = customBadge,
                        onValueChange = { customBadge = it },
                        placeholder = { Text("مثال: مشرف الفعاليات / المطور") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Verification Checkbox / Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isVerifiedChecked = !isVerifiedChecked },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("شارة التوثيق الزرقاء", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.Checkbox(
                            checked = isVerifiedChecked,
                            onCheckedChange = { isVerifiedChecked = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUserRole(
                            targetUid = user!!.uid,
                            role = selectedRole,
                            customBadgeTitle = customBadge.trim().ifBlank { null },
                            isVerified = isVerifiedChecked
                        )
                        showRoleManagementDialog = false
                    }
                ) {
                    Text("حفظ التغييرات")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoleManagementDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Full Screen Image Viewer Modal with Smooth Transitions
    previewImageUrl?.let { imageUrl ->
        FullScreenImageViewer(
            imageUrl = imageUrl,
            onDismiss = { previewImageUrl = null }
        )
    }

    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.sign_out_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.sign_out_confirm_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        viewModel.signOut()
                        runCatching {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                            GoogleSignIn.getClient(context, gso).signOut()
                        }
                        if (onBack != null) {
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.sign_out),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ProfileHeader(
    profile: AnimiteUser,
    stats: UserAnimeStats,
    accentColor: Color,
    isCurrentUser: Boolean,
    isFollowing: Boolean,
    isUploadingAvatar: Boolean,
    onBack: (() -> Unit)?,
    onShare: () -> Unit,
    onSettingsClick: (() -> Unit)?,
    onMoreClick: (() -> Unit)?,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    isBlocked: Boolean,
    onUnblockClick: () -> Unit,
    onBlockClick: () -> Unit,
    onReportClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit,
    onEditClick: () -> Unit,
    onFollowClick: () -> Unit,
    onUnfollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
    canManageRoles: Boolean = false,
    onManageRolesClick: (() -> Unit)? = null,
    onOwnerControlCenterClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val statusBarInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        val statusBarTopDp = statusBarInsets.asPaddingValues().calculateTopPadding()
        val compact = maxWidth < 360.dp
        val baseBannerHeight = if (compact) 160.dp else 190.dp
        val bannerHeight = baseBannerHeight + statusBarTopDp
        val avatarSize = if (compact) 76.dp else 92.dp
        val squircleShape = RoundedCornerShape(26.dp)

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight + avatarSize / 2 + 10.dp)
            ) {
                // 1. Cover banner extending seamlessly behind status bar with Live Cover & Animated Media
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bannerHeight)
                        .align(Alignment.TopCenter)
                ) {
                    LiveCoverPlayer(
                        liveCoverUrl = profile.liveCoverUrl,
                        posterUrl = profile.liveCoverPosterUrl,
                        mediaType = profile.liveCoverMediaType,
                        fallbackBannerUrl = profile.bannerUrl,
                        isOwner = isCurrentUser,
                        onCoverClick = onBannerClick,
                        onAddCoverClick = if (isCurrentUser) onBannerClick else null,
                    )
                }

                // 2. Translucent floating top navigation buttons, safely positioned below status bar / cutout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(statusBarInsets)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (onBack != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.42f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.back),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(38.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.42f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            IconButton(onClick = onBannerClick, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Expand cover",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.42f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            IconButton(onClick = onShare, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        if (isCurrentUser && onSettingsClick != null) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.42f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                IconButton(onClick = onSettingsClick, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.settings),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (onMoreClick != null) {
                            Box {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.42f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    IconButton(onClick = onMoreClick, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = onDismissMenu
                                ) {
                                    if (!isCurrentUser) {
                                        DropdownMenuItem(
                                            text = { Text(if (isBlocked) "Unblock User" else "Block User") },
                                            onClick = {
                                                onDismissMenu()
                                                if (isBlocked) onUnblockClick() else onBlockClick()
                                            }
                                        )
                                        if (canManageRoles && onManageRolesClick != null) {
                                            DropdownMenuItem(
                                                text = { Text("تعيين الرتبة والشارة (إدارة)", color = MaterialTheme.colorScheme.primary) },
                                                onClick = {
                                                    onDismissMenu()
                                                    onManageRolesClick()
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Report User", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                onDismissMenu()
                                                onReportClick()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Apple-style Squircle Avatar overlapping the banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .align(Alignment.BottomStart)
                ) {
                    // Squircle Avatar
                    Box(modifier = Modifier.size(avatarSize)) {
                        AsyncImage(
                            model = profile.photoUrl,
                            contentDescription = "Profile picture of ${profile.displayName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(squircleShape)
                                .border(4.dp, MaterialTheme.colorScheme.background, squircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant, squircleShape)
                                .clickable(enabled = !isUploadingAvatar) {
                                    onAvatarClick()
                                }
                        )

                        if (isUploadingAvatar) {
                            Surface(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                                shape = squircleShape,
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
                    }
                }
            }

            // 4. Identity & Metadata Block with Inline Action Buttons
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                // Name row with Action Buttons (✉ Message / Follow / Edit) aligned to the end
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                        if (profile.isOwner) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFD700).copy(alpha = 0.2f),
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = "Owner",
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        } else if (profile.isAdmin) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFAB47BC).copy(alpha = 0.2f),
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Moderator",
                                        tint = Color(0xFFAB47BC),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        } else if (profile.isPremium) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF9800).copy(alpha = 0.2f),
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Premium Member",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        } else if (profile.isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Verified account",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                        if (profile.isPrivate) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Private account",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Action buttons matching the mockup
                    if (isCurrentUser) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (profile.isOwner && onOwnerControlCenterClick != null) {
                                Button(
                                    onClick = onOwnerControlCenterClick,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFFD700),
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "مركز المالك",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                            Button(
                                onClick = onEditClick,
                                shape = RoundedCornerShape(24.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.edit_profile),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Direct Message icon pill/box
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { onMessageClick() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                                        contentDescription = "Send a message",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            FollowToggleButton(
                                isFollowing = isFollowing,
                                onFollowClick = onFollowClick,
                                onUnfollowClick = onUnfollowClick
                            )
                        }
                    }
                }

                // @handle and Role / Badge Pill
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                    ) {
                        Text(
                            text = "@${profile.handle.removePrefix("@")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    profile.badgeLabel?.let { label ->
                        val pillBg = when {
                            profile.isOwner -> Color(0xFFFFD700).copy(alpha = 0.18f)
                            profile.isModerator -> Color(0xFFAB47BC).copy(alpha = 0.18f)
                            profile.isPremium -> Color(0xFFFF9800).copy(alpha = 0.18f)
                            profile.isVerifiedUser -> Color(0xFF00B0FF).copy(alpha = 0.18f)
                            else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                        }
                        val pillBorder = when {
                            profile.isOwner -> Color(0xFFFFD700).copy(alpha = 0.55f)
                            profile.isModerator -> Color(0xFFAB47BC).copy(alpha = 0.55f)
                            profile.isPremium -> Color(0xFFFF9800).copy(alpha = 0.55f)
                            profile.isVerifiedUser -> Color(0xFF00B0FF).copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                        }
                        val pillFg = when {
                            profile.isOwner -> Color(0xFFFFD700)
                            profile.isModerator -> Color(0xFFAB47BC)
                            profile.isPremium -> Color(0xFFFF9800)
                            profile.isVerifiedUser -> Color(0xFF00B0FF)
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                        val pillIcon = when {
                            profile.isOwner -> Icons.Default.Security
                            profile.isModerator -> Icons.Default.Star
                            profile.isPremium -> Icons.Default.Star
                            profile.isVerifiedUser -> Icons.Default.CheckCircle
                            else -> Icons.Rounded.Person
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = pillBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, pillBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = pillIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = pillFg
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = pillFg
                                )
                            }
                        }
                    }
                }

                // Inline Stats row (e.g. "217 Following   590 Followers" matching reference)
                Row(
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        count = profile.followingCount,
                        label = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.following),
                        onClick = onFollowingClick
                    )
                    StatItem(
                        count = profile.followersCount,
                        label = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.followers),
                        onClick = onFollowersClick
                    )
                }

                // Bio
                if (profile.bio.isNotBlank()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Meta row: location, website, joined date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!profile.location.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = profile.location!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!profile.websiteUrl.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                runCatching {
                                    val uri = Uri.parse(profile.websiteUrl)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = profile.websiteUrl!!.removePrefix("https://").removePrefix("http://"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                com.imashnake.animite.social.R.string.joined,
                                joinedDateText(profile.createdAt)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Linked AniList account
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
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp)
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
                                        text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.linked_anilist_account),
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
                                text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.view_online),
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
                            .padding(top = 8.dp, bottom = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.this_account_is_private),
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
private fun LibraryTabWithStats(
    stats: UserAnimeStats,
    entries: List<com.imashnake.animite.social.model.FirestoreLibraryEntry>,
    accentColor: Color,
) {
    var selectedFilter by remember { mutableStateOf<String?>("ALL") }

    val filteredEntries = remember(entries, selectedFilter) {
        when (selectedFilter) {
            "WATCHING" -> entries.filter { it.status.equals("WATCHING", ignoreCase = true) }
            "COMPLETED" -> entries.filter { it.status.equals("COMPLETED", ignoreCase = true) }
            "PLAN_TO_WATCH" -> entries.filter { it.status.equals("PLAN_TO_WATCH", ignoreCase = true) }
            "FAVORITES" -> entries.filter { it.isFavorite }
            else -> entries
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        item {
            AnimeStatsCard(
                stats = stats,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        item {
            // Horizontal filter pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "ALL" to androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.all_tab),
                    "WATCHING" to androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.watching_tab),
                    "COMPLETED" to androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.completed_tab),
                    "FAVORITES" to androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.favorites_tab)
                )

                filters.forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { selectedFilter = key }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.empty_section_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredEntries, key = { it.animeId }) { entry ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    LibraryEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun AnimeStatsCard(
    stats: UserAnimeStats,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0E1318)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = accentColor.copy(alpha = 0.25f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimeStatMetric(
                count = stats.totalEntries,
                label = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.total_entries),
                accentColor = accentColor
            )
            VerticalMetricDivider()
            AnimeStatMetric(
                count = stats.watchingCount,
                label = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.watching_tab),
                accentColor = Color(0xFF00E676)
            )
            VerticalMetricDivider()
            AnimeStatMetric(
                count = stats.completedCount,
                label = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.completed_tab),
                accentColor = Color(0xFF00B0FF)
            )
            VerticalMetricDivider()
            AnimeStatMetric(
                count = stats.favoritesCount,
                label = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.favorites_tab),
                accentColor = Color(0xFFFF4081)
            )
        }
    }
}

@Composable
private fun VerticalMetricDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
private fun AnimeStatMetric(
    count: Int,
    label: String,
    accentColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = formatCount(count),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8BA0B2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            shape = RoundedCornerShape(24.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.follow),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        OutlinedButton(
            onClick = onUnfollowClick,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                if (isPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = if (isPressed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        ) {
            Text(
                text = if (isPressed) {
                    androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.unfollow)
                } else {
                    androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.following)
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun LibraryTabContent(
    entries: List<com.imashnake.animite.social.model.FirestoreLibraryEntry>,
    emptySubtitle: String,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.empty_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = emptySubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(entries, key = { it.animeId }) { entry ->
                LibraryEntryCard(entry = entry)
            }
        }
    }
}

@Composable
private fun LibraryEntryCard(
    entry: com.imashnake.animite.social.model.FirestoreLibraryEntry,
) {
    val statusColor = when (entry.status.uppercase()) {
        "WATCHING" -> Color(0xFF00E676)
        "COMPLETED" -> Color(0xFF00B0FF)
        "PLAN_TO_WATCH" -> Color(0xFF7C4DFF)
        "ON_HOLD" -> Color(0xFFFF9100)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141A22)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.06f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Anime #${entry.animeId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (entry.isFavorite) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = Color(0xFFFF4081),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.status,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (entry.currentEpisode > 0) {
                        Text(
                            text = "Ep. ${entry.currentEpisode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8BA0B2)
                        )
                    }

                    entry.score?.let { score ->
                        if (score > 0f) {
                            Text(
                                text = "★ ${String.format(Locale.US, "%.1f", score)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostsTabContent(
    posts: List<Comment>?,
    profile: AnimiteUser,
    emptyText: String,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp),
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
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

            if (!comment.mediaUrl.isNullOrBlank()) {
                AsyncImage(
                    model = comment.mediaUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // Where the post was made: community post, anime discussion, or episode.
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = when {
                        comment.targetType == "community" -> "Community Post"
                        comment.targetType == "episode" && !comment.episodeNumber.isNullOrBlank() ->
                            "Episode ${comment.episodeNumber} · Anime #${comment.animeId}"
                        else -> "Anime #${comment.animeId}"
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
private fun MediaTabContent(
    groups: List<MediaGroup>?,
    emptyText: String,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp),
) {
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
                contentPadding = contentPadding,
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

/**
 * Premium full-screen image viewer with animated zoom & fade transitions,
 * backdrop scrim, tap to dismiss, and close button.
 */
@Composable
private fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            visible = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(280)) + scaleIn(initialScale = 0.82f, animationSpec = tween(280)),
                exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.82f, animationSpec = tween(200))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Expanded view",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* Prevent dismissing when clicking the image itself */ }
                    )
                }
            }

            // Close button in top end
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(42.dp)
                    .align(Alignment.TopEnd)
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

