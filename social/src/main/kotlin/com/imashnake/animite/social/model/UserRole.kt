package com.imashnake.animite.social.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Explicit permissions for Role-Based Access Control (RBAC).
 * Enforces principle of least privilege.
 */
@Serializable
enum class Permission(val labelAr: String) {
    VIEW_CONTROL_CENTER("الوصول لمركز التحكم"),
    MANAGE_ALL_ROLES("إدارة كافة الرتب"),
    ASSIGN_MODERATOR("تعيين مشرفين"),
    GRANT_PREMIUM("منح عضوية النخبة"),
    GRANT_VERIFICATION("توثيق الحسابات"),
    SUSPEND_USER("حظر المستخدمين"),
    VIEW_AUDIT_LOGS("استعراض سجل العمليات"),
    MANAGE_SYSTEM_SETTINGS("تعديل إعدادات النظام"),
    MODERATE_COMMENTS("إدارة وحذف التعليقات"),
    PREMIUM_CUSTOMIZATION("تخصيصات النخبة المتقدمة"),
    VERIFIED_BADGE("شارة التوثيق الرسمية");
}

/**
 * Stable account types and roles in Animeta.
 *
 * Membership tiers (VERIFIED, PREMIUM) are strictly separated from
 * administrative authorities (MODERATOR, OWNER).
 */
@Serializable
enum class UserRole(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val hierarchyLevel: Int,
    val badgeColorHex: String,
) {
    MEMBER(
        id = "member",
        titleAr = "عضو",
        titleEn = "Member",
        hierarchyLevel = 0,
        badgeColorHex = "#64748B" // Slate
    ),
    VERIFIED(
        id = "verified",
        titleAr = "موثق",
        titleEn = "Verified",
        hierarchyLevel = 1,
        badgeColorHex = "#0EA5E9" // Sky Blue
    ),
    PREMIUM(
        id = "premium",
        titleAr = "نخبة Animeta",
        titleEn = "Animeta Elite",
        hierarchyLevel = 2,
        badgeColorHex = "#F59E0B" // Amber / Gold
    ),
    MODERATOR(
        id = "moderator",
        titleAr = "مشرف",
        titleEn = "Moderator",
        hierarchyLevel = 3,
        badgeColorHex = "#10B981" // Emerald Green
    ),
    OWNER(
        id = "owner",
        titleAr = "مالك التطبيق",
        titleEn = "Owner",
        hierarchyLevel = 4,
        badgeColorHex = "#8B5CF6" // Violet / Purple
    );

    val permissions: Set<Permission>
        get() = when (this) {
            OWNER -> Permission.entries.toSet()
            MODERATOR -> setOf(
                Permission.MODERATE_COMMENTS,
                Permission.SUSPEND_USER,
                Permission.GRANT_VERIFICATION
            )
            PREMIUM -> setOf(
                Permission.PREMIUM_CUSTOMIZATION
            )
            VERIFIED -> setOf(
                Permission.VERIFIED_BADGE
            )
            MEMBER -> emptySet()
        }

    fun hasPermission(permission: Permission): Boolean = permissions.contains(permission)

    val rank: Int get() = hierarchyLevel
    val roleNameEn: String get() = titleEn

    val badgeColor: Color get() = try {
        Color(android.graphics.Color.parseColor(badgeColorHex))
    } catch (_: Exception) {
        Color(0xFF8B5CF6)
    }

    companion object {
        fun fromId(id: String?): UserRole = when (id?.trim()?.lowercase()) {
            "owner" -> OWNER
            "admin", "moderator" -> MODERATOR
            "premium", "elite", "vip" -> PREMIUM
            "verified" -> VERIFIED
            else -> MEMBER
        }

        fun fromRoleString(id: String?): UserRole = fromId(id)

        fun canAssignRole(actorRole: UserRole, targetRole: UserRole): Boolean {
            if (actorRole == OWNER) return true
            if (targetRole == OWNER || targetRole == MODERATOR) return false
            return actorRole.hierarchyLevel > targetRole.hierarchyLevel
        }

        fun canAssignRole(actorRole: UserRole, targetCurrentRole: UserRole, newRole: UserRole): Boolean {
            if (actorRole == OWNER) return true
            if (newRole == OWNER || newRole == MODERATOR) return false
            if (targetCurrentRole == OWNER) return false
            return actorRole.hierarchyLevel > newRole.hierarchyLevel
        }
    }
}
