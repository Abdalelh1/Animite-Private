package com.imashnake.animite.social.ui.notifications

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.imashnake.animite.social.model.AuthState
import com.imashnake.animite.social.model.SocialNotification
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onNavigateToUserProfile: (userId: String) -> Unit,
    onNavigateToAnime: (animeId: Int) -> Unit = {},
    onNavigateToPost: (postId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsState()
    val notifications by viewModel.filteredNotifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("$unreadCount")
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        IconButton(
                            onClick = { viewModel.markAllAsRead() }
                        ) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Mark all as read",
                                tint = MaterialTheme.colorScheme.primary
                            )
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
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "all",
                    onClick = { viewModel.setFilter("all") },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedFilter == "interactions",
                    onClick = { viewModel.setFilter("interactions") },
                    label = { Text("Social") }
                )
                FilterChip(
                    selected = selectedFilter == "episodes",
                    onClick = { viewModel.setFilter("episodes") },
                    label = { Text("Episodes") }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (authState is AuthState.Unauthenticated) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign in to view your notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No notifications yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "When someone follows you, likes your post, or a new episode releases, you'll see it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(notifications, key = { it.id }) { notif ->
                        NotificationItem(
                            notification = notif,
                            onClick = {
                                viewModel.markAsRead(notif.id)
                                when (notif.type) {
                                    SocialNotification.TYPE_FOLLOW -> {
                                        onNavigateToUserProfile(notif.senderId)
                                    }
                                    SocialNotification.TYPE_EPISODE -> {
                                        val animeId = notif.targetId.toIntOrNull() ?: 0
                                        if (animeId > 0) onNavigateToAnime(animeId)
                                    }
                                    SocialNotification.TYPE_LIKE,
                                    SocialNotification.TYPE_COMMENT,
                                    SocialNotification.TYPE_REPLY -> {
                                        if (notif.targetId.startsWith("anime_")) {
                                            val animeId = notif.targetId.removePrefix("anime_").toIntOrNull() ?: 0
                                            if (animeId > 0) onNavigateToAnime(animeId)
                                        } else if (notif.targetId.isNotBlank()) {
                                            onNavigateToPost(notif.targetId)
                                        } else if (notif.senderId.isNotBlank() && notif.senderId != "system") {
                                            onNavigateToUserProfile(notif.senderId)
                                        }
                                    }
                                    else -> {
                                        if (notif.senderId.isNotBlank() && notif.senderId != "system") {
                                            onNavigateToUserProfile(notif.senderId)
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: SocialNotification,
    onClick: () -> Unit,
) {
    val unreadColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    val readColor = Color.Transparent

    val icon: ImageVector
    val iconColor: Color
    val eventText: String

    when (notification.type) {
        SocialNotification.TYPE_LIKE -> {
            icon = Icons.Default.Favorite
            iconColor = Color(0xFFE91E63)
            eventText = "liked your post"
        }
        SocialNotification.TYPE_COMMENT -> {
            icon = Icons.Default.Forum
            iconColor = Color(0xFF2196F3)
            eventText = "commented on your post"
        }
        SocialNotification.TYPE_REPLY -> {
            icon = Icons.Default.Reply
            iconColor = Color(0xFF00BCD4)
            eventText = "replied to your comment"
        }
        SocialNotification.TYPE_FOLLOW -> {
            icon = Icons.Default.PersonAdd
            iconColor = Color(0xFF4CAF50)
            eventText = "started following you"
        }
        SocialNotification.TYPE_EPISODE -> {
            icon = Icons.Default.PlayCircle
            iconColor = Color(0xFFFF9800)
            eventText = "Episode ${notification.episodeNumber.orEmpty()} released!"
        }
        else -> {
            icon = Icons.Default.Notifications
            iconColor = MaterialTheme.colorScheme.primary
            eventText = "notification"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (!notification.isRead) unreadColor else readColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar with overlaid event icon
        Box(modifier = Modifier.size(48.dp)) {
            if (notification.type == SocialNotification.TYPE_EPISODE) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = iconColor.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
                    }
                }
            } else {
                AsyncImage(
                    model = notification.senderPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                // Small badge
                Surface(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (notification.type == SocialNotification.TYPE_EPISODE) {
                        notification.targetTitle ?: "New Episode"
                    } else {
                        notification.senderDisplayName.ifBlank { "User" }
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Text(
                    text = formatRelativeTime(notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = eventText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!notification.targetTitle.isNullOrBlank() && notification.type != SocialNotification.TYPE_EPISODE) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${notification.targetTitle}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!notification.isRead) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.CenterVertically)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
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
