package com.imashnake.animite.social.ui.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.imashnake.animite.social.R
import com.imashnake.animite.social.validation.ProfileValidator

/**
 * Dedicated, full-screen profile editor with rich fields, image picking, validation,
 * unsaved-changes confirmation dialog, and full Arabic RTL support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val user by viewModel.userProfile.collectAsState()
    val avatarUploadState by viewModel.avatarUploadState.collectAsState()
    val isUploadingAvatar = avatarUploadState is AvatarUploadState.Loading
    val snackbarHostState = remember { SnackbarHostState() }

    var username by remember(user) { mutableStateOf(user?.username.orEmpty()) }
    var displayName by remember(user) { mutableStateOf(user?.displayName.orEmpty()) }
    var bio by remember(user) { mutableStateOf(user?.bio.orEmpty()) }
    var location by remember(user) { mutableStateOf(user?.location.orEmpty()) }
    var websiteUrl by remember(user) { mutableStateOf(user?.websiteUrl.orEmpty()) }
    var isPrivate by remember(user) { mutableStateOf(user?.isPrivate ?: false) }
    var allowMessagesFrom by remember(user) { mutableStateOf(user?.allowMessagesFrom ?: "everyone") }
    var selectedAccentColor by remember(user) { mutableStateOf(user?.customization?.accentColor ?: "#00D1FF") }
    var selectedThemePreset by remember(user) { mutableStateOf(user?.customization?.themePreset ?: "cyan_glow") }
    var isMessageMenuExpanded by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

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
    var showCoverOptionsDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(liveCoverUploadState) {
        if (liveCoverUploadState is LiveCoverUploadState.Error) {
            val errorState = liveCoverUploadState as LiveCoverUploadState.Error
            val result = snackbarHostState.showSnackbar(
                message = errorState.message,
                actionLabel = if (errorState.canRetry) "Retry" else null,
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.retryLiveCoverUpload()
            }
        }
    }

    // Validation using production ProfileValidator
    val usernameValidation = ProfileValidator.validateUsername(username)
    val displayNameValidation = ProfileValidator.validateDisplayName(displayName)
    val bioValidation = ProfileValidator.validateBio(bio)
    val locationValidation = ProfileValidator.validateLocation(location)
    val websiteValidation = ProfileValidator.validateWebsite(websiteUrl)

    val canSave = usernameValidation.isValid &&
        displayNameValidation.isValid &&
        bioValidation.isValid &&
        locationValidation.isValid &&
        websiteValidation.isValid &&
        user != null

    // Track unsaved changes
    val hasUnsavedChanges = remember(username, displayName, bio, location, websiteUrl, isPrivate, allowMessagesFrom, selectedAccentColor, selectedThemePreset, user) {
        user != null && (
            username.trim().lowercase() != user?.username.orEmpty().trim().lowercase() ||
            displayName.trim() != user?.displayName.orEmpty().trim() ||
            bio.trim() != user?.bio.orEmpty().trim() ||
            location.trim() != user?.location.orEmpty().trim() ||
            websiteUrl.trim() != user?.websiteUrl.orEmpty().trim() ||
            isPrivate != user?.isPrivate ||
            allowMessagesFrom != user?.allowMessagesFrom ||
            selectedAccentColor != user?.customization?.accentColor ||
            selectedThemePreset != user?.customization?.themePreset
        )
    }

    val handleBack = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showUnsavedDialog = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_profile),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (canSave) {
                                viewModel.updateProfile(
                                    displayName = displayName.trim(),
                                    bio = bio.trim(),
                                    location = location.trim().ifEmpty { null },
                                    websiteUrl = websiteUrl.trim().ifEmpty { null },
                                    isPrivate = isPrivate,
                                    allowMessagesFrom = allowMessagesFrom,
                                    username = username.trim().lowercase(),
                                )
                                val currentCustom = user?.customization ?: com.imashnake.animite.social.model.ProfileCustomization.DEFAULT
                                if (selectedAccentColor != currentCustom.accentColor || selectedThemePreset != currentCustom.themePreset) {
                                    viewModel.updateCustomization(
                                        currentCustom.copy(
                                            accentColor = selectedAccentColor,
                                            themePreset = selectedThemePreset
                                        )
                                    )
                                }
                                onBack()
                            }
                        },
                        enabled = canSave
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            fontWeight = FontWeight.Bold,
                            color = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Cover and Avatar preview with tap to edit
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    // Cover
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clickable {
                                showCoverOptionsDialog = true
                            }
                    ) {
                        LiveCoverPlayer(
                            liveCoverUrl = user!!.liveCoverUrl,
                            posterUrl = user!!.liveCoverPosterUrl,
                            mediaType = user!!.liveCoverMediaType,
                            fallbackBannerUrl = user!!.bannerUrl,
                            isOwner = true,
                            onCoverClick = { showCoverOptionsDialog = true }
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isUploadingLiveCover) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = stringResource(R.string.change_cover),
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Avatar overlapping bottom of cover
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp)
                            .size(90.dp)
                    ) {
                        AsyncImage(
                            model = user!!.photoUrl,
                            contentDescription = stringResource(R.string.change_avatar),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = !isUploadingAvatar) {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        )

                        if (isUploadingAvatar) {
                            Surface(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
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
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = stringResource(R.string.change_avatar),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Editable Fields Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = { if (it.length <= 30) username = it.lowercase().trim() },
                        label = { Text(stringResource(R.string.username)) },
                        prefix = { Text("@") },
                        supportingText = {
                            if (!usernameValidation.isValid) {
                                Text((usernameValidation as com.imashnake.animite.social.validation.ValidationResult.Invalid).reason, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("${username.length}/30")
                            }
                        },
                        isError = !usernameValidation.isValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Display Name
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= 40) displayName = it },
                        label = { Text(stringResource(R.string.display_name)) },
                        supportingText = { Text("${displayName.length}/40") },
                        isError = !displayNameValidation.isValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bio
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { if (it.length <= 300) bio = it },
                        label = { Text(stringResource(R.string.bio)) },
                        placeholder = { Text(stringResource(R.string.bio_placeholder)) },
                        supportingText = { Text("${bio.length}/300") },
                        isError = !bioValidation.isValid,
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Location
                    OutlinedTextField(
                        value = location,
                        onValueChange = { if (it.length <= 100) location = it },
                        label = { Text(stringResource(R.string.location)) },
                        placeholder = { Text(stringResource(R.string.location_placeholder)) },
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        isError = !locationValidation.isValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Website URL
                    OutlinedTextField(
                        value = websiteUrl,
                        onValueChange = { websiteUrl = it },
                        label = { Text(stringResource(R.string.website)) },
                        placeholder = { Text(stringResource(R.string.website_placeholder)) },
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        isError = !websiteValidation.isValid,
                        supportingText = {
                            if (!websiteValidation.isValid) {
                                Text(stringResource(R.string.website_error), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Direct Messages Permission
                    ExposedDropdownMenuBox(
                        expanded = isMessageMenuExpanded,
                        onExpandedChange = { isMessageMenuExpanded = !isMessageMenuExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = when (allowMessagesFrom) {
                                "followers" -> stringResource(R.string.messages_followers)
                                "none" -> stringResource(R.string.messages_none)
                                else -> stringResource(R.string.messages_everyone)
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.allow_messages_from)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMessageMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = isMessageMenuExpanded,
                            onDismissRequest = { isMessageMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.messages_everyone)) },
                                onClick = {
                                    allowMessagesFrom = "everyone"
                                    isMessageMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.messages_followers)) },
                                onClick = {
                                    allowMessagesFrom = "followers"
                                    isMessageMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.messages_none)) },
                                onClick = {
                                    allowMessagesFrom = "none"
                                    isMessageMenuExpanded = false
                                }
                            )
                        }
                    }

                    // Private Account Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.private_account),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.private_account_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isPrivate,
                                onCheckedChange = { isPrivate = it }
                            )
                        }
                    }

                    // Theme & Accent Color Customization Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.theme_customization),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.accent_color),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Accent Color Swatches
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.imashnake.animite.social.model.ProfileCustomization.ALLOWED_ACCENT_COLORS.forEach { hex ->
                                    val isSelected = selectedAccentColor.equals(hex, ignoreCase = true)
                                    val color = remember(hex) {
                                        try {
                                            Color(android.graphics.Color.parseColor(hex))
                                        } catch (e: Exception) {
                                            Color(0xFF00D1FF)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                } else {
                                                    Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                                }
                                            )
                                            .clickable { selectedAccentColor = hex }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Cover Options Dialog
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

    // Unsaved Changes Confirmation Dialog
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.unsaved_changes_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.unsaved_changes_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        onBack()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.discard),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text(text = stringResource(R.string.stay_in_editor))
                }
            }
        )
    }
}
