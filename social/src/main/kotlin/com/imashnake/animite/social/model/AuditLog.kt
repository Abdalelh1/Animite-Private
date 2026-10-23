package com.imashnake.animite.social.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class AuditAction(val labelAr: String, val labelEn: String) {
    ROLE_CHANGED("تغيير الرتبة", "Role Changed"),
    VERIFICATION_GRANTED("منح شارة التوثيق", "Verification Granted"),
    VERIFICATION_REVOKED("إلغاء شارة التوثيق", "Verification Revoked"),
    PREMIUM_GRANTED("منح عضوية النخبة", "Premium Granted"),
    PREMIUM_REVOKED("إلغاء عضوية النخبة", "Premium Revoked"),
    ACCOUNT_SUSPENDED("حظر الحساب", "Account Suspended"),
    ACCOUNT_RESTORED("إلغاء حظر الحساب", "Account Restored"),
    SYSTEM_SETTINGS_UPDATED("تحديث إعدادات النظام", "System Settings Updated");
}

/**
 * Immutable administrative audit record stored in Firestore `/audit_logs`.
 * Append-only; once written, it cannot be modified or deleted.
 */
@Immutable
@Serializable
data class AuditLog(
    val id: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val targetUid: String = "",
    val targetName: String = "",
    val action: String = AuditAction.ROLE_CHANGED.name,
    val details: String = "",
    val previousValue: String? = null,
    val newValue: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val auditAction: AuditAction
        get() = try {
            AuditAction.valueOf(action)
        } catch (_: Exception) {
            AuditAction.ROLE_CHANGED
        }
}
