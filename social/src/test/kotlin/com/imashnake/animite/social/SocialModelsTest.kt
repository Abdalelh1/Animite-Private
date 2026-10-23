package com.imashnake.animite.social

import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.Comment
import com.imashnake.animite.social.model.Conversation
import com.imashnake.animite.social.model.ParticipantInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialModelsTest {

    @Test
    fun animiteUser_handle_formatsCorrectly() {
        val user = AnimiteUser(uid = "123", username = "kenkaneki", displayName = "Ken")
        assertEquals("@kenkaneki", user.handle)
        assertFalse(user.isAnilistLinked)

        val linkedUser = user.copy(anilistUserId = 999, anilistUsername = "KanekiAL")
        assertTrue(linkedUser.isAnilistLinked)
    }

    @Test
    fun comment_displayContent_handlesSoftDelete() {
        val normalComment = Comment(
            id = "c1",
            authorId = "u1",
            content = "This anime is incredible!",
            isDeleted = false
        )
        assertEquals("This anime is incredible!", normalComment.displayContent)

        val deletedComment = normalComment.copy(isDeleted = true, content = "")
        assertEquals("[Comment deleted by user]", deletedComment.displayContent)
    }

    @Test
    fun conversation_deterministic_participants_and_unread() {
        val uid1 = "alice"
        val uid2 = "bob"
        val convId1 = if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
        val convId2 = if (uid2 < uid1) "${uid2}_${uid1}" else "${uid1}_${uid2}"
        assertEquals(convId1, convId2)

        val conversation = Conversation(
            id = convId1,
            participants = listOf(uid1, uid2),
            participantDetails = mapOf(
                uid1 to ParticipantInfo(uid = uid1, displayName = "Alice", username = "alice"),
                uid2 to ParticipantInfo(uid = uid2, displayName = "Bob", username = "bob"),
            ),
            unreadCounts = mapOf(uid1 to 0, uid2 to 3)
        )

        assertEquals("Bob", conversation.getOtherParticipant(uid1)?.displayName)
        assertEquals("Alice", conversation.getOtherParticipant(uid2)?.displayName)
        assertEquals(0, conversation.getUnreadCount(uid1))
        assertEquals(3, conversation.getUnreadCount(uid2))
    }

    @Test
    fun animiteUser_roleAndBadge_helpersWorkAsExpected() {
        val normalUser = AnimiteUser(uid = "1", role = "user")
        assertFalse(normalUser.isOwner)
        assertFalse(normalUser.isAdmin)
        assertFalse(normalUser.isModerator)
        assertEquals(null, normalUser.badgeLabel)

        val modUser = AnimiteUser(uid = "2", role = "moderator")
        assertFalse(modUser.isOwner)
        assertFalse(modUser.isAdmin)
        assertTrue(modUser.isModerator)
        assertEquals("مشرف", modUser.badgeLabel)

        val adminUser = AnimiteUser(uid = "3", role = "admin")
        assertFalse(adminUser.isOwner)
        assertTrue(adminUser.isAdmin)
        assertTrue(adminUser.isModerator)
        assertEquals("مدير النظام", adminUser.badgeLabel)

        val ownerUser = AnimiteUser(uid = "4", role = "owner")
        assertTrue(ownerUser.isOwner)
        assertTrue(ownerUser.isAdmin)
        assertTrue(ownerUser.isModerator)
        assertEquals("مالك التطبيق", ownerUser.badgeLabel)

        val customBadgeUser = AnimiteUser(uid = "5", role = "user", customBadgeTitle = "مشرف الفعاليات")
        assertEquals("مشرف الفعاليات", customBadgeUser.badgeLabel)
        assertTrue(customBadgeUser.hasBadge)
    }

    @Test
    fun comment_authorRole_helpersWorkAsExpected() {
        val ownerComment = Comment(id = "c1", authorRole = "owner")
        assertTrue(ownerComment.isAuthorOwner)
        assertTrue(ownerComment.isAuthorAdmin)
        assertEquals("مالك التطبيق", ownerComment.authorBadgeLabel)

        val customComment = Comment(id = "c2", authorRole = "moderator", authorCustomBadge = "المطور")
        assertEquals("المطور", customComment.authorBadgeLabel)
    }

    @Test
    fun animiteUser_liveCoverHelpersWorkAsExpected() {
        val userNoCover = AnimiteUser(uid = "u1")
        assertFalse(userNoCover.hasLiveCover)
        assertFalse(userNoCover.isLiveCoverVideo)
        assertFalse(userNoCover.isLiveCoverGif)

        val userMp4 = AnimiteUser(
            uid = "u2",
            liveCoverUrl = "https://res.cloudinary.com/demo/video/upload/cover.mp4",
            liveCoverMediaType = "video/mp4"
        )
        assertTrue(userMp4.hasLiveCover)
        assertTrue(userMp4.isLiveCoverVideo)
        assertFalse(userMp4.isLiveCoverGif)

        val userGif = AnimiteUser(
            uid = "u3",
            liveCoverUrl = "https://res.cloudinary.com/demo/image/upload/cover.gif",
            liveCoverMediaType = "image/gif"
        )
        assertTrue(userGif.hasLiveCover)
        assertFalse(userGif.isLiveCoverVideo)
        assertTrue(userGif.isLiveCoverGif)
    }
}
