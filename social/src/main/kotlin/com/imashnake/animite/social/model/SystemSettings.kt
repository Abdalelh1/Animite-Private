package com.imashnake.animite.social.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Global community and application runtime settings managed by OWNER.
 * Stored in Firestore at `/system_settings/config`.
 */
@Immutable
@Serializable
data class SystemSettings(
    val maintenanceMode: Boolean = false,
    val allowRegistrations: Boolean = true,
    val commentsEnabled: Boolean = true,
    val directMessagesEnabled: Boolean = true,
    val announcementBanner: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedByUid: String = "",
) {
    companion object {
        val DEFAULT = SystemSettings()
    }
}
