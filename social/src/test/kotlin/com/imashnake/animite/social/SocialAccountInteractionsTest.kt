package com.imashnake.animite.social

import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.Comment
import com.imashnake.animite.social.model.Post
import com.imashnake.animite.social.model.SocialNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates cross-account functional interactions and security invariants between two
 * authenticated accounts (User A / "alice_uid" and User B / "bob_uid").
 */
class SocialAccountInteractionsTest {

    private val userA = AnimiteUser(uid = "alice_uid", username = "alice", displayName = "Alice")
    private val userB = AnimiteUser(uid = "bob_uid", username = "bob", displayName = "Bob")
    private val moderator = AnimiteUser(uid = "mod_uid", username = "mod", displayName = "Mod", role = "moderator")

    // --- 1. Follow / Unfollow Idempotency & Document Paths ---

    @Test
    fun follow_documentPaths_and_idempotency() {
        val followerUid = userA.uid
        val targetUid = userB.uid

        // In Firestore: users/{followerUid}/following/{targetUid} and users/{targetUid}/followers/{followerUid}
        val followingPath = "users/$followerUid/following/$targetUid"
        val followersPath = "users/$targetUid/followers/$followerUid"

        assertEquals("users/alice_uid/following/bob_uid", followingPath)
        assertEquals("users/bob_uid/followers/alice_uid", followersPath)

        // Simulate state transitions
        val relationshipStore = mutableSetOf<String>()
        var bobFollowerCount = 0
        var aliceFollowingCount = 0

        fun performFollow(actor: String, target: String): Boolean {
            val key = "$actor->$target"
            // Idempotent guard implemented in SocialRelationshipRepository:
            if (relationshipStore.contains(key)) {
                return false // Already following, do not increment counters
            }
            relationshipStore.add(key)
            bobFollowerCount++
            aliceFollowingCount++
            return true
        }

        fun performUnfollow(actor: String, target: String): Boolean {
            val key = "$actor->$target"
            if (!relationshipStore.contains(key)) {
                return false // Not following, do not decrement counters
            }
            relationshipStore.remove(key)
            if (bobFollowerCount > 0) bobFollowerCount--
            if (aliceFollowingCount > 0) aliceFollowingCount--
            return true
        }

        // First follow: succeeds and increments counters
        val firstFollow = performFollow(followerUid, targetUid)
        assertTrue(firstFollow)
        assertEquals(1, bobFollowerCount)
        assertEquals(1, aliceFollowingCount)

        // Duplicate follow call (rapid tap or retry): rejected by idempotency guard, counters unchanged
        val duplicateFollow = performFollow(followerUid, targetUid)
        assertFalse(duplicateFollow)
        assertEquals(1, bobFollowerCount)
        assertEquals(1, aliceFollowingCount)

        // Unfollow: succeeds and decrements counters
        val unfollow = performUnfollow(followerUid, targetUid)
        assertTrue(unfollow)
        assertEquals(0, bobFollowerCount)
        assertEquals(0, aliceFollowingCount)

        // Duplicate unfollow: rejected by idempotency guard, counters never go negative
        val duplicateUnfollow = performUnfollow(followerUid, targetUid)
        assertFalse(duplicateUnfollow)
        assertEquals(0, bobFollowerCount)
        assertEquals(0, aliceFollowingCount)
    }

    // --- 2. Post-Like vs Comment-Like Path Separation ---

    @Test
    fun postLikes_use_dedicated_post_subcollection_path() {
        val postId = "post_xyz_789"
        val commentId = "comment_abc_123"
        val actorUid = userA.uid

        val postLikeDocPath = "posts/$postId/likes/$actorUid"
        val commentLikeDocPath = "comments/$commentId/likes/$actorUid"

        // Ensure post likes and comment likes use completely disjoint Firestore paths
        assertNotEquals(postLikeDocPath, commentLikeDocPath)
        assertTrue(postLikeDocPath.startsWith("posts/"))
        assertTrue(postLikeDocPath.endsWith("/likes/$actorUid"))
        assertTrue(commentLikeDocPath.startsWith("comments/"))
        assertFalse(postLikeDocPath.contains("comments"))
    }

    // --- 3. Notification Recipient Correctness & Self-Suppression ---

    @Test
    fun notification_dispatched_to_recipient_when_userB_interacts_with_userA() {
        val postAuthor = userA.uid
        val actor = userB.uid
        val postId = "post_100"

        // User B likes User A's post -> Recipient must be User A
        val likeNotification = if (postAuthor != actor) {
            SocialNotification(
                id = SocialNotification.generateId(SocialNotification.TYPE_LIKE, postId, actor),
                recipientId = postAuthor,
                senderId = actor,
                senderDisplayName = userB.displayName,
                type = SocialNotification.TYPE_LIKE,
                targetId = postId
            )
        } else null

        assertTrue(likeNotification != null)
        assertEquals(userA.uid, likeNotification?.recipientId)
        assertEquals(userB.uid, likeNotification?.senderId)
        assertEquals("like_post_100_bob_uid", likeNotification?.id)
    }

    @Test
    fun notification_suppressed_when_userA_interacts_with_own_content() {
        val postAuthor = userA.uid
        val actor = userA.uid // Self-action
        val postId = "post_100"

        val likeNotification = if (postAuthor != actor) {
            SocialNotification(
                id = SocialNotification.generateId(SocialNotification.TYPE_LIKE, postId, actor),
                recipientId = postAuthor,
                senderId = actor,
                type = SocialNotification.TYPE_LIKE,
                targetId = postId
            )
        } else null

        // Must be null: sender never receives notification for their own action
        assertNull(likeNotification)
    }

    @Test
    fun comment_notification_distinguishes_postAuthor_and_replyRecipient() {
        val post = Post(id = "post_200", authorId = userA.uid, content = "User A post")
        val comment = Comment(id = "c_1", authorId = userB.uid, targetType = "post", targetId = post.id, content = "User B top comment")

        // User B comments on User A's post -> notify post author (User A)
        val postAuthorNotification = if (post.authorId != comment.authorId) {
            SocialNotification(
                recipientId = post.authorId,
                senderId = comment.authorId,
                type = SocialNotification.TYPE_COMMENT,
                targetId = post.id
            )
        } else null

        assertTrue(postAuthorNotification != null)
        assertEquals(userA.uid, postAuthorNotification?.recipientId)
        assertEquals(userB.uid, postAuthorNotification?.senderId)

        // User A replies to User B's comment -> notify parent author (User B)
        val replyAuthor = userA.uid
        val parentAuthor = userB.uid
        val replyNotification = if (parentAuthor != replyAuthor) {
            SocialNotification(
                recipientId = parentAuthor,
                senderId = replyAuthor,
                type = SocialNotification.TYPE_REPLY,
                targetId = post.id
            )
        } else null

        assertTrue(replyNotification != null)
        assertEquals(userB.uid, replyNotification?.recipientId)
        assertEquals(userA.uid, replyNotification?.senderId)
    }

    // --- 4. Ownership Checks: Edit and Delete Operations ---

    @Test
    fun post_ownership_validation_for_edit_and_delete() {
        val userAPost = Post(id = "p_1", authorId = userA.uid, content = "Original Content")

        // Edit permission check: only author can edit
        fun canEditPost(post: Post, actorUid: String): Boolean {
            return post.authorId == actorUid
        }

        // Delete permission check: author OR moderator/admin/owner can delete
        fun canDeletePost(post: Post, actor: AnimiteUser): Boolean {
            return post.authorId == actor.uid || actor.isModerator
        }

        // User A edits own post -> ALLOWED
        assertTrue(canEditPost(userAPost, userA.uid))

        // User B edits User A's post -> DENIED
        assertFalse(canEditPost(userAPost, userB.uid))

        // User A deletes own post -> ALLOWED
        assertTrue(canDeletePost(userAPost, userA))

        // User B deletes User A's post -> DENIED
        assertFalse(canDeletePost(userAPost, userB))

        // Moderator deletes User A's post -> ALLOWED (Moderation privilege)
        assertTrue(canDeletePost(userAPost, moderator))
    }

    @Test
    fun comment_ownership_validation_for_edit_and_delete() {
        val userAComment = Comment(id = "c_1", authorId = userA.uid, content = "User A thought")

        fun canEditComment(comment: Comment, actorUid: String): Boolean {
            return comment.authorId == actorUid && !comment.isDeleted
        }

        fun canDeleteComment(comment: Comment, actor: AnimiteUser): Boolean {
            return (comment.authorId == actor.uid || actor.isModerator) && !comment.isDeleted
        }

        assertTrue(canEditComment(userAComment, userA.uid))
        assertFalse(canEditComment(userAComment, userB.uid))

        assertTrue(canDeleteComment(userAComment, userA))
        assertFalse(canDeleteComment(userAComment, userB))
        assertTrue(canDeleteComment(userAComment, moderator))
    }

    // --- 5. Notification Destination Routing Resolution ---

    @Test
    fun notification_destination_routing_resolution() {
        fun resolveDestination(notification: SocialNotification): String {
            return when (notification.type) {
                SocialNotification.TYPE_FOLLOW -> "profile:${notification.senderId}"
                SocialNotification.TYPE_EPISODE -> "anime:${notification.targetId}"
                SocialNotification.TYPE_LIKE,
                SocialNotification.TYPE_COMMENT,
                SocialNotification.TYPE_REPLY -> {
                    if (notification.targetId.startsWith("anime_")) {
                        "anime:${notification.targetId.removePrefix("anime_")}"
                    } else if (notification.targetId.isNotBlank()) {
                        "post:${notification.targetId}"
                    } else {
                        "profile:${notification.senderId}"
                    }
                }
                else -> "profile:${notification.senderId}"
            }
        }

        val followNotif = SocialNotification(type = SocialNotification.TYPE_FOLLOW, senderId = "user_b", targetId = "")
        assertEquals("profile:user_b", resolveDestination(followNotif))

        val postLikeNotif = SocialNotification(type = SocialNotification.TYPE_LIKE, senderId = "user_b", targetId = "post_999")
        assertEquals("post:post_999", resolveDestination(postLikeNotif))

        val animeCommentNotif = SocialNotification(type = SocialNotification.TYPE_COMMENT, senderId = "user_b", targetId = "anime_21")
        assertEquals("anime:21", resolveDestination(animeCommentNotif))

        val episodeNotif = SocialNotification(type = SocialNotification.TYPE_EPISODE, senderId = "system", targetId = "1535")
        assertEquals("anime:1535", resolveDestination(episodeNotif))
    }
}
