package com.imashnake.animite.social.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.imashnake.animite.social.model.AnimiteUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    user: AnimiteUser,
    onDismiss: () -> Unit,
    onSave: (displayName: String, bio: String, location: String?, websiteUrl: String?, isPrivate: Boolean, allowMessagesFrom: String, username: String?) -> Unit,
    onChangeAvatar: (() -> Unit)? = null,
    isUploadingAvatar: Boolean = false,
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var username by remember { mutableStateOf(user.username) }
    var bio by remember { mutableStateOf(user.bio) }
    var location by remember { mutableStateOf(user.location.orEmpty()) }
    var websiteUrl by remember { mutableStateOf(user.websiteUrl.orEmpty()) }
    var isPrivate by remember { mutableStateOf(user.isPrivate) }
    var allowMessagesFrom by remember { mutableStateOf(user.allowMessagesFrom) }
    var isMessageMenuExpanded by remember { mutableStateOf(false) }

    // Validation
    val isDisplayNameValid = displayName.trim().isNotEmpty() && displayName.length <= 50
    val usernameValidation = com.imashnake.animite.social.validation.ProfileValidator.validateUsername(username)
    val isUsernameValid = usernameValidation.isValid
    val isBioValid = bio.length <= 250
    val isLocationValid = location.length <= 35
    val isWebsiteValid = websiteUrl.isBlank() || websiteUrl.startsWith("http://") || websiteUrl.startsWith("https://")

    val canSave = isDisplayNameValid && isUsernameValid && isBioValid && isLocationValid && isWebsiteValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Avatar preview and change button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isUploadingAvatar && onChangeAvatar != null) {
                            onChangeAvatar?.invoke()
                        }
                ) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )

                    if (isUploadingAvatar) {
                        Surface(
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                            modifier = Modifier.size(76.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                if (onChangeAvatar != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onChangeAvatar,
                        enabled = !isUploadingAvatar,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isUploadingAvatar) "Uploading..." else "Change Avatar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Display Name
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 50) displayName = it },
                    label = { Text("Display Name") },
                    supportingText = {
                        Text("${displayName.length}/50")
                    },
                    isError = !isDisplayNameValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Username (@handle)
                OutlinedTextField(
                    value = username,
                    onValueChange = { input ->
                        val sanitized = input.trim().removePrefix("@")
                        if (sanitized.length <= 30) username = sanitized
                    },
                    label = { Text(androidx.compose.ui.res.stringResource(com.imashnake.animite.social.R.string.username)) },
                    prefix = { Text("@") },
                    supportingText = {
                        if (usernameValidation is com.imashnake.animite.social.validation.ValidationResult.Invalid) {
                            Text(usernameValidation.reason, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("${username.length}/30")
                        }
                    },
                    isError = !isUsernameValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bio
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 250) bio = it },
                    label = { Text("Bio") },
                    placeholder = { Text("Tell other fans about yourself...") },
                    supportingText = {
                        Text("${bio.length}/250")
                    },
                    isError = !isBioValid,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Location
                OutlinedTextField(
                    value = location,
                    onValueChange = { if (it.length <= 35) location = it },
                    label = { Text("Location") },
                    placeholder = { Text("e.g. Tokyo, Japan") },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Website URL
                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = { websiteUrl = it },
                    label = { Text("Website") },
                    placeholder = { Text("https://example.com") },
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    isError = !isWebsiteValid,
                    supportingText = {
                        if (!isWebsiteValid) {
                            Text("Must begin with https:// or http://", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Direct Messages permission selector
                ExposedDropdownMenuBox(
                    expanded = isMessageMenuExpanded,
                    onExpandedChange = { isMessageMenuExpanded = !isMessageMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (allowMessagesFrom) {
                            "followers" -> "Followers Only"
                            "none" -> "No One"
                            else -> "Everyone"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Allow Direct Messages From") },
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
                            text = { Text("Everyone") },
                            onClick = {
                                allowMessagesFrom = "everyone"
                                isMessageMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Followers Only") },
                            onClick = {
                                allowMessagesFrom = "followers"
                                isMessageMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("No One") },
                            onClick = {
                                allowMessagesFrom = "none"
                                isMessageMenuExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Private Account switch
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Private Account", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Only approved followers can see full activity",
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) {
                        onSave(
                            displayName.trim(),
                            bio.trim(),
                            location.trim().ifEmpty { null },
                            websiteUrl.trim().ifEmpty { null },
                            isPrivate,
                            allowMessagesFrom,
                            username.trim().removePrefix("@")
                        )
                    }
                },
                enabled = canSave
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
