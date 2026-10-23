package com.imashnake.animite.social

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.imashnake.animite.social.data.AuthRepository
import com.imashnake.animite.social.data.CloudinaryUploader
import com.imashnake.animite.social.data.CommentRepository
import com.imashnake.animite.social.data.MessagingRepository
import com.imashnake.animite.social.data.NotificationRepository
import com.imashnake.animite.social.data.UserProfileRepository
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuthState
import com.imashnake.animite.social.model.Comment
import com.imashnake.animite.social.model.Conversation
import com.imashnake.animite.social.ui.auth.AuthBottomSheet
import com.imashnake.animite.social.data.PostRepository
import com.imashnake.animite.social.model.Post
import com.imashnake.animite.social.ui.comments.CommentsSection
import com.imashnake.animite.social.util.AvatarValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val messagingRepository: MessagingRepository,
    private val userProfileRepository: UserProfileRepository,
    val postRepository: PostRepository,
    val commentRepository: CommentRepository,
    private val notificationRepository: NotificationRepository,
    private val cloudinaryUploader: CloudinaryUploader,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), AuthState.Loading)

    val currentUid: String? get() = authRepository.currentUid

    val currentUserProfile: StateFlow<AnimiteUser?> = authRepository.authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            userProfileRepository.getUserProfile(state.user.uid)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    val feedPosts: StateFlow<List<Post>?> = postRepository.getFeedPosts(limit = 40)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    val unreadNotificationsCount: StateFlow<Int> = authRepository.authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            notificationRepository.getUnreadCount(state.user.uid)
        } else {
            flowOf(0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), 0)

    val conversations: StateFlow<List<Conversation>> = authRepository.authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            messagingRepository.getConversations(state.user.uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<AnimiteUser>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList())
        else userProfileRepository.searchUsers(query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    fun publishPost(
        content: String,
        imageBytes: ByteArray?,
        mimeType: String?,
        onSuccess: () -> Unit,
    ) {
        val user = currentUserProfile.value ?: (authState.value as? AuthState.Authenticated)?.user ?: return
        if (content.isBlank() && imageBytes == null) return
        if (_isPublishing.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isPublishing.value = true
            try {
                var mediaUrl: String? = null
                if (imageBytes != null && mimeType != null) {
                    val uploadResult = cloudinaryUploader.uploadImage(imageBytes, mimeType)
                    if (uploadResult.isSuccess) {
                        mediaUrl = uploadResult.getOrNull()
                    } else {
                        _uiMessage.value = "Failed to upload image: ${uploadResult.exceptionOrNull()?.message}"
                        _isPublishing.value = false
                        return@launch
                    }
                }

                postRepository.createPost(
                    author = user,
                    content = content,
                    mediaUrl = mediaUrl
                ).onSuccess {
                    _uiMessage.value = "Post published successfully!"
                    onSuccess()
                }.onFailure {
                    _uiMessage.value = it.localizedMessage ?: "Failed to publish post"
                }
            } finally {
                _isPublishing.value = false
            }
        }
    }

    fun editPost(postId: String, newContent: String) {
        val uid = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            postRepository.editPost(postId, newContent, uid)
                .onSuccess { _uiMessage.value = "Post updated!" }
                .onFailure { _uiMessage.value = "Failed to update: ${it.localizedMessage}" }
        }
    }

    fun deletePost(postId: String) {
        val user = currentUserProfile.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            postRepository.deletePost(postId, user.uid, isModerator = user.isModerator || user.isOwner)
                .onSuccess { _uiMessage.value = "Post deleted" }
                .onFailure { _uiMessage.value = "Failed to delete: ${it.localizedMessage}" }
        }
    }

    fun toggleLike(postId: String) {
        val user = currentUserProfile.value ?: (authState.value as? AuthState.Authenticated)?.user ?: return
        viewModelScope.launch(Dispatchers.IO) {
            postRepository.toggleLike(postId, user)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    onNavigateToUserProfile: (userId: String) -> Unit = {},
    onNavigateToChat: (conversationId: String, recipientId: String, recipientName: String, recipientAvatar: String?) -> Unit = { _, _, _, _ -> },
    onNavigateToNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SocialViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val currentUser by viewModel.currentUserProfile.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val feedPosts by viewModel.feedPosts.collectAsState()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsState()
    val isPublishing by viewModel.isPublishing.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Community, 1: Messages
    var showAuthSheet by remember { mutableStateOf(false) }

    // Post Creation State
    var postInputText by remember { mutableStateOf("") }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageMimeType by remember { mutableStateOf<String?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Dialog & Sheet States
    var editingPost by remember { mutableStateOf<Post?>(null) }
    var deletingPostId by remember { mutableStateOf<String?>(null) }
    var viewingCommentsPost by remember { mutableStateOf<Post?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            when (val res = AvatarValidator.validateAndRead(context, uri)) {
                is AvatarValidator.ValidationResult.Valid -> {
                    selectedImageBytes = res.bytes
                    selectedImageMimeType = res.mimeType
                    selectedImageUri = uri
                }
                is AvatarValidator.ValidationResult.Invalid -> {
                    scope.launch { snackbarHostState.showSnackbar(res.reason) }
                }
            }
        }
    }

    val totalUnreadMessages = remember(conversations, viewModel.currentUid) {
        conversations.sumOf { it.getUnreadCount(viewModel.currentUid.orEmpty()) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Animeta Social",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Notification Bell with Badge
                    IconButton(onClick = onNavigateToNotifications) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("$unreadNotifications")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (unreadNotifications > 0) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                                contentDescription = "Notifications",
                                tint = if (unreadNotifications > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (authState is AuthState.Authenticated) {
                        val user = (authState as AuthState.Authenticated).user
                        IconButton(onClick = { onNavigateToUserProfile(user.uid) }) {
                            AsyncImage(
                                model = user.photoUrl,
                                contentDescription = "My Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    } else if (authState is AuthState.Unauthenticated) {
                        Button(
                            onClick = { showAuthSheet = true },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Sign In")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Primary Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Community Feed", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Messages", fontWeight = FontWeight.SemiBold)
                            if (totalUnreadMessages > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge { Text("$totalUnreadMessages") }
                            }
                        }
                    }
                )
            }

            if (selectedTab == 0) {
                // Community Tab
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        // Member Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text("Search members by @username...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Member Search Results
                    if (searchQuery.isNotBlank()) {
                        item {
                            Text(
                                text = "Search Results (${searchResults.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (searchResults.isEmpty()) {
                            item {
                                Text(
                                    text = "No members found matching \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            items(searchResults, key = { it.uid }) { searchUser ->
                                Card(
                                    onClick = { onNavigateToUserProfile(searchUser.uid) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = searchUser.photoUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = searchUser.displayName,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = searchUser.handle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Create Post Card (when authenticated)
                        if (authState is AuthState.Authenticated) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            AsyncImage(
                                                model = currentUser?.photoUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            OutlinedTextField(
                                                value = postInputText,
                                                onValueChange = { if (it.length <= 2000) postInputText = it },
                                                placeholder = { Text("What's on your mind? Share anime reviews or thoughts...") },
                                                minLines = 2,
                                                maxLines = 6,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        // Image Preview if selected
                                        if (selectedImageUri != null) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(160.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            ) {
                                                AsyncImage(
                                                    model = selectedImageUri,
                                                    contentDescription = "Selected Image",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                IconButton(
                                                    onClick = {
                                                        selectedImageBytes = null
                                                        selectedImageMimeType = null
                                                        selectedImageUri = null
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(28.dp)
                                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Action bar: Attach photo + Post button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.AddPhotoAlternate,
                                                    contentDescription = "Attach image",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (postInputText.isNotBlank()) {
                                                    Text(
                                                        text = "${2000 - postInputText.length}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.publishPost(
                                                            content = postInputText,
                                                            imageBytes = selectedImageBytes,
                                                            mimeType = selectedImageMimeType,
                                                            onSuccess = {
                                                                postInputText = ""
                                                                selectedImageBytes = null
                                                                selectedImageMimeType = null
                                                                selectedImageUri = null
                                                            }
                                                        )
                                                    },
                                                    enabled = !isPublishing && (postInputText.isNotBlank() || selectedImageBytes != null),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    if (isPublishing) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    } else {
                                                        Text("Post")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (authState is AuthState.Unauthenticated) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Text(
                                            text = "Join the Animite Social Community",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Sign in to share posts, like and comment on anime discussions, and follow fellow fans.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = { showAuthSheet = true },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Get Started")
                                        }
                                    }
                                }
                            }
                        }

                        // Feed Header
                        item {
                            Text(
                                text = "Recent Posts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Feed Content
                        when {
                            feedPosts == null -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            feedPosts.isNullOrEmpty() -> {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(28.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "No community posts yet!",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Be the first to share your thoughts, reviews, or favorite anime moments.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                items(feedPosts!!, key = { it.id }) { post ->
                                    CommunityPostCard(
                                        post = post,
                                        currentUserId = viewModel.currentUid,
                                        isPostLikedFlow = viewModel.postRepository.isPostLiked(post.id, viewModel.currentUid.orEmpty()),
                                        onLikeClick = { viewModel.toggleLike(post.id) },
                                        onCommentsClick = { viewingCommentsPost = post },
                                        onAuthorClick = { onNavigateToUserProfile(post.authorId) },
                                        onEditClick = { editingPost = post },
                                        onDeleteClick = { deletingPostId = post.id },
                                        onShareClick = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, "${post.authorDisplayName}: ${post.content}")
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share post"))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            } else {
                // Messages Tab
                if (authState is AuthState.Unauthenticated) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Sign in to access Private Messages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Chat directly with friends and discuss anime recommendations.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { showAuthSheet = true }) {
                                Text("Sign In")
                            }
                        }
                    }
                } else if (conversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No conversations yet. Visit a user's profile to start a chat!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val currentUid = viewModel.currentUid.orEmpty()
                    val timeFormatter = remember { SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()) }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(conversations, key = { it.id }) { conv ->
                            val other = conv.getOtherParticipant(currentUid)
                            val unread = conv.getUnreadCount(currentUid)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (other != null) {
                                            onNavigateToChat(conv.id, other.uid, other.displayName, other.photoUrl)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = other?.photoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = other?.displayName ?: "User",
                                            fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = timeFormatter.format(Date(conv.lastMessageTimestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = conv.lastMessageText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (unread > 0) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (unread > 0) {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                Text("$unread")
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
    }

    // Comments Bottom Sheet for Post
    if (viewingCommentsPost != null) {
        val post = viewingCommentsPost!!
        ModalBottomSheet(
            onDismissRequest = { viewingCommentsPost = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Comments (${post.commentCount})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                CommentsSection(
                    targetType = "post",
                    targetId = post.id,
                    animeId = 0,
                    onNavigateToUserProfile = onNavigateToUserProfile
                )
            }
        }
    }

    // Edit Post Dialog
    if (editingPost != null) {
        var editedContent by remember { mutableStateOf(editingPost!!.content) }
        AlertDialog(
            onDismissRequest = { editingPost = null },
            title = { Text("Edit Post") },
            text = {
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { if (it.length <= 2000) editedContent = it },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editPost(editingPost!!.id, editedContent)
                        editingPost = null
                    },
                    enabled = editedContent.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPost = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (deletingPostId != null) {
        AlertDialog(
            onDismissRequest = { deletingPostId = null },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to delete this post? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePost(deletingPostId!!)
                        deletingPostId = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPostId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAuthSheet) {
        AuthBottomSheet(onDismissRequest = { showAuthSheet = false })
    }
}

@Composable
private fun CommunityPostCard(
    post: Post,
    currentUserId: String?,
    isPostLikedFlow: Flow<Boolean>,
    onLikeClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLiked by isPostLikedFlow.collectAsState(initial = false)
    var showMenu by remember { mutableStateOf(false) }
    val isAuthor = currentUserId != null && currentUserId == post.authorId

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Avatar, Names, Badge, Time, Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = post.authorPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onAuthorClick)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorDisplayName.ifBlank { "User" },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable(onClick = onAuthorClick)
                        )

                        if (!post.authorBadgeLabel.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = post.authorBadgeLabel!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${post.authorUsername} · ${formatRelativeTime(post.createdAt)}${if (post.isEdited) " (edited)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isAuthor) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    showMenu = false
                                    onEditClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Post Text
            if (post.content.isNotBlank()) {
                Text(
                    text = post.displayContent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Post Attached Image
            if (!post.mediaUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = post.mediaUrl,
                    contentDescription = "Post Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions Row: Likes, Comments, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onLikeClick)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${post.likesCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLiked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Comments button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCommentsClick)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${post.commentCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Share button
                IconButton(onClick = onShareClick) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
