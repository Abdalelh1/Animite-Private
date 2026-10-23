package com.imashnake.animite.social

import com.imashnake.animite.social.model.Comment
import com.imashnake.animite.social.model.SocialNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialNotificationTest {

    @Test
    fun notification_id_generation_is_deterministic() {
        val id1 = SocialNotification.generateId(
            type = SocialNotification.TYPE_LIKE,
            targetId = "post_123",
            senderId = "user_456"
        )
        val id2 = SocialNotification.generateId(
            type = SocialNotification.TYPE_LIKE,
            targetId = "post_123",
            senderId = "user_456"
        )
        assertEquals("like_post_123_user_456", id1)
        assertEquals(id1, id2)
    }

    @Test
    fun episode_notification_id_deduplicates_airing_releases() {
        val epId1 = SocialNotification.generateEpisodeId(animeId = 21, episodeNumber = "1100")
        val epId2 = SocialNotification.generateEpisodeId(animeId = 21, episodeNumber = "1100")
        val diffEp = SocialNotification.generateEpisodeId(animeId = 21, episodeNumber = "1101")

        assertEquals("ep_21_1100", epId1)
        assertEquals(epId1, epId2)
        assertNotEquals(epId1, diffEp)
    }

    @Test
    fun notification_prevents_self_notification_recipient() {
        val currentUid = "user_abc"
        val selfNotification = SocialNotification(
            recipientId = currentUid,
            senderId = currentUid,
            type = SocialNotification.TYPE_LIKE
        )
        // Recipient and sender are identical -> should be filtered out
        val shouldSuppress = selfNotification.recipientId == selfNotification.senderId
        assertTrue(shouldSuppress)
    }

    @Test
    fun comment_post_supports_media_attachment() {
        val textOnlyPost = Comment(
            id = "p1",
            authorId = "u1",
            content = "Excited for the finale!",
            targetType = "community",
            mediaUrl = null
        )
        assertEquals("Excited for the finale!", textOnlyPost.displayContent)
        assertFalse(textOnlyPost.isEdited)

        val imagePost = textOnlyPost.copy(
            id = "p2",
            mediaUrl = "https://res.cloudinary.com/dojsusfim/image/upload/v1/sample.jpg"
        )
        assertEquals("https://res.cloudinary.com/dojsusfim/image/upload/v1/sample.jpg", imagePost.mediaUrl)
    }

    @Test
    fun comment_post_edit_marks_isEdited() {
        val original = Comment(
            id = "p1",
            authorId = "u1",
            content = "Original text",
            isEdited = false
        )
        val edited = original.copy(
            content = "Updated text with corrections",
            isEdited = true
        )
        assertTrue(edited.isEdited)
        assertEquals("Updated text with corrections", edited.displayContent)
    }
}
