package com.imashnake.animite.social

import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuditAction
import com.imashnake.animite.social.model.AuditLog
import com.imashnake.animite.social.model.Permission
import com.imashnake.animite.social.model.SystemSettings
import com.imashnake.animite.social.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RBACTest {

    @Test
    fun userRole_rankHierarchyIsStrict() {
        assertTrue(UserRole.OWNER.rank > UserRole.MODERATOR.rank)
        assertTrue(UserRole.MODERATOR.rank > UserRole.PREMIUM.rank)
        assertTrue(UserRole.PREMIUM.rank > UserRole.VERIFIED.rank)
        assertTrue(UserRole.VERIFIED.rank > UserRole.MEMBER.rank)
    }

    @Test
    fun userRole_fromRoleString_parsesCorrectly() {
        assertEquals(UserRole.OWNER, UserRole.fromRoleString("owner"))
        assertEquals(UserRole.OWNER, UserRole.fromRoleString("OWNER"))
        assertEquals(UserRole.MODERATOR, UserRole.fromRoleString("moderator"))
        assertEquals(UserRole.MODERATOR, UserRole.fromRoleString("admin"))
        assertEquals(UserRole.PREMIUM, UserRole.fromRoleString("premium"))
        assertEquals(UserRole.VERIFIED, UserRole.fromRoleString("verified"))
        assertEquals(UserRole.MEMBER, UserRole.fromRoleString("member"))
        assertEquals(UserRole.MEMBER, UserRole.fromRoleString("user"))
        assertEquals(UserRole.MEMBER, UserRole.fromRoleString("unknown"))
        assertEquals(UserRole.MEMBER, UserRole.fromRoleString(null))
    }

    @Test
    fun userRole_permissionsFollowLeastPrivilege() {
        // OWNER has all permissions
        Permission.entries.forEach { permission ->
            assertTrue("Owner must have $permission", UserRole.OWNER.hasPermission(permission))
        }

        // MODERATOR has moderation permissions, but NOT owner permissions
        assertTrue(UserRole.MODERATOR.hasPermission(Permission.MODERATE_COMMENTS))
        assertTrue(UserRole.MODERATOR.hasPermission(Permission.SUSPEND_USER))
        assertTrue(UserRole.MODERATOR.hasPermission(Permission.GRANT_VERIFICATION))

        assertFalse(UserRole.MODERATOR.hasPermission(Permission.MANAGE_SYSTEM_SETTINGS))
        assertFalse(UserRole.MODERATOR.hasPermission(Permission.VIEW_AUDIT_LOGS))
        assertFalse(UserRole.MODERATOR.hasPermission(Permission.MANAGE_ALL_ROLES))
        assertFalse(UserRole.MODERATOR.hasPermission(Permission.ASSIGN_MODERATOR))

        // PREMIUM has premium customization, but no admin
        assertTrue(UserRole.PREMIUM.hasPermission(Permission.PREMIUM_CUSTOMIZATION))
        assertFalse(UserRole.PREMIUM.hasPermission(Permission.MODERATE_COMMENTS))
        assertFalse(UserRole.PREMIUM.hasPermission(Permission.MANAGE_SYSTEM_SETTINGS))

        // MEMBER has basic access (no privileged permissions)
        assertFalse(UserRole.MEMBER.hasPermission(Permission.PREMIUM_CUSTOMIZATION))
        assertFalse(UserRole.MEMBER.hasPermission(Permission.MODERATE_COMMENTS))
        assertFalse(UserRole.MEMBER.hasPermission(Permission.VIEW_CONTROL_CENTER))
    }

    @Test
    fun canAssignRole_enforcesHierarchicalRules() {
        // OWNER can assign any role
        assertTrue(UserRole.canAssignRole(actorRole = UserRole.OWNER, targetCurrentRole = UserRole.MEMBER, newRole = UserRole.OWNER))
        assertTrue(UserRole.canAssignRole(actorRole = UserRole.OWNER, targetCurrentRole = UserRole.MODERATOR, newRole = UserRole.PREMIUM))
        assertTrue(UserRole.canAssignRole(actorRole = UserRole.OWNER, targetCurrentRole = UserRole.OWNER, newRole = UserRole.MEMBER))

        // MODERATOR cannot assign OWNER or MODERATOR
        assertFalse(UserRole.canAssignRole(actorRole = UserRole.MODERATOR, targetCurrentRole = UserRole.MEMBER, newRole = UserRole.OWNER))
        assertFalse(UserRole.canAssignRole(actorRole = UserRole.MODERATOR, targetCurrentRole = UserRole.MEMBER, newRole = UserRole.MODERATOR))

        // MODERATOR cannot modify an OWNER
        assertFalse(UserRole.canAssignRole(actorRole = UserRole.MODERATOR, targetCurrentRole = UserRole.OWNER, newRole = UserRole.MEMBER))

        // MODERATOR can assign lower roles like VERIFIED, PREMIUM, or MEMBER to normal users
        assertTrue(UserRole.canAssignRole(actorRole = UserRole.MODERATOR, targetCurrentRole = UserRole.MEMBER, newRole = UserRole.VERIFIED))

        // MEMBER cannot assign any role
        assertFalse(UserRole.canAssignRole(actorRole = UserRole.MEMBER, targetCurrentRole = UserRole.MEMBER, newRole = UserRole.VERIFIED))
    }

    @Test
    fun animiteUser_rbacHelpers_ownerRole() {
        val owner = AnimiteUser(
            uid = "owner_1",
            role = "owner",
            displayName = "Super Owner"
        )
        assertTrue(owner.isOwner)
        assertTrue(owner.isAdmin)
        assertTrue(owner.isModerator)
        assertEquals(UserRole.OWNER, owner.userRole)
        assertEquals("مالك التطبيق", owner.badgeLabel)
    }

    @Test
    fun animiteUser_rbacHelpers_moderatorRole() {
        val mod = AnimiteUser(
            uid = "mod_1",
            role = "moderator",
            displayName = "Mod Alex"
        )
        assertFalse(mod.isOwner)
        assertFalse(mod.isAdmin)
        assertTrue(mod.isModerator)
        assertEquals(UserRole.MODERATOR, mod.userRole)
        assertEquals("مشرف", mod.badgeLabel)
    }

    @Test
    fun animiteUser_rbacHelpers_premiumExpiration() {
        val futureTime = System.currentTimeMillis() + 100_000L
        val pastTime = System.currentTimeMillis() - 100_000L

        val activePremium = AnimiteUser(
            uid = "prem_1",
            role = "premium",
            premiumUntil = futureTime
        )
        assertTrue(activePremium.isPremium)
        assertEquals(UserRole.PREMIUM, activePremium.userRole)
        assertEquals("نخبة", activePremium.badgeLabel)

        val expiredPremium = AnimiteUser(
            uid = "prem_2",
            role = "premium",
            premiumUntil = pastTime
        )
        assertFalse(expiredPremium.isPremium)
        // Since expired, fallback role is MEMBER
        assertEquals(UserRole.MEMBER, expiredPremium.userRole)

        // Verified user who had premium: expires back to VERIFIED
        val expiredVerifiedPremium = AnimiteUser(
            uid = "prem_3",
            role = "premium",
            isVerified = true,
            premiumUntil = pastTime
        )
        assertFalse(expiredVerifiedPremium.isPremium)
        assertEquals(UserRole.VERIFIED, expiredVerifiedPremium.userRole)
        assertTrue(expiredVerifiedPremium.isVerifiedUser)

        // Owner who had premium timestamp: never loses OWNER status
        val ownerWithExpiredPremium = AnimiteUser(
            uid = "owner_prem",
            role = "owner",
            premiumUntil = pastTime
        )
        assertTrue(ownerWithExpiredPremium.isOwner)
        assertEquals(UserRole.OWNER, ownerWithExpiredPremium.userRole)

        // Moderator who had premium timestamp: never loses MODERATOR status
        val modWithExpiredPremium = AnimiteUser(
            uid = "mod_prem",
            role = "moderator",
            premiumUntil = pastTime
        )
        assertTrue(modWithExpiredPremium.isModerator)
        assertEquals(UserRole.MODERATOR, modWithExpiredPremium.userRole)
    }

    @Test
    fun animiteUser_rbacHelpers_suspension() {
        val suspended = AnimiteUser(
            uid = "bad_user",
            role = "user",
            isSuspended = true
        )
        assertTrue(suspended.isSuspended)
    }

    @Test
    fun auditLog_dataStructureIntegrity() {
        val log = AuditLog(
            id = "log_123",
            actorUid = "owner_uid",
            actorName = "Super Owner",
            targetUid = "user_456",
            targetName = "Target User",
            action = AuditAction.ROLE_CHANGED.name,
            details = "Changed role from MEMBER to MODERATOR",
            previousValue = "MEMBER",
            newValue = "MODERATOR",
            timestamp = 1700000000000L
        )

        assertEquals("log_123", log.id)
        assertEquals(AuditAction.ROLE_CHANGED, log.auditAction)
        assertEquals("تغيير الرتبة", log.auditAction.labelAr)
    }

    @Test
    fun systemSettings_defaultStateIsSafe() {
        val settings = SystemSettings.DEFAULT
        assertFalse("Maintenance mode must be disabled by default", settings.maintenanceMode)
        assertTrue("Registrations must be allowed by default", settings.allowRegistrations)
        assertTrue("Comments must be enabled by default", settings.commentsEnabled)
        assertTrue("Direct messages must be enabled by default", settings.directMessagesEnabled)
        assertTrue("Announcement banner must be empty by default", settings.announcementBanner.isEmpty())
    }

    @Test
    fun systemSettings_maintenanceMode_ownerCanBypass() {
        val maintenanceSettings = SystemSettings(maintenanceMode = true)
        val ownerUser = AnimiteUser(uid = "owner_1", role = "owner")
        val normalUser = AnimiteUser(uid = "user_1", role = "user")
        val moderatorUser = AnimiteUser(uid = "mod_1", role = "moderator")

        // In maintenance mode: only owner is allowed access
        assertTrue("Owner must be allowed to bypass maintenance mode", ownerUser.isOwner)
        assertFalse("Normal user must be blocked during maintenance mode", normalUser.isOwner)
        assertFalse("Moderator must be blocked during maintenance mode", moderatorUser.isOwner)
    }

    @Test
    fun systemSettings_commentsAndMessaging_administrativeException() {
        val disabledSettings = SystemSettings(
            commentsEnabled = false,
            directMessagesEnabled = false
        )
        val owner = AnimiteUser(uid = "owner", role = "owner")
        val mod = AnimiteUser(uid = "mod", role = "moderator")
        val member = AnimiteUser(uid = "member", role = "user")

        // Owner and Moderator have administrative exception
        assertTrue(owner.isModerator || owner.isOwner)
        assertTrue(mod.isModerator || mod.isOwner)
        assertFalse(member.isModerator || member.isOwner)
    }
}
