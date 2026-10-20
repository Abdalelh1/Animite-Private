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
}
