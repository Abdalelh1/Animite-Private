package com.imashnake.animite.social.data

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuditLog
import com.imashnake.animite.social.model.SystemSettings
import com.imashnake.animite.social.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OwnerRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : IOwnerRepository {

    override fun getDashboardStats(): Flow<OwnerDashboardStats> = callbackFlow {
        suspend fun fetchStats(): OwnerDashboardStats = runCatching {
            val usersCol = firestore.collection("users")
            val commentsCol = firestore.collection("comments")
            val reportsCol = firestore.collection("reports")

            val totalUsersTask = usersCol.count().get(AggregateSource.SERVER)
            val ownersTask = usersCol.whereEqualTo("role", UserRole.OWNER.id).count().get(AggregateSource.SERVER)
            val modsTask = usersCol.whereIn("role", listOf("moderator", "admin")).count().get(AggregateSource.SERVER)
            val premiumTask = usersCol.whereEqualTo("role", UserRole.PREMIUM.id).count().get(AggregateSource.SERVER)
            val verifiedTask = usersCol.whereEqualTo("isVerified", true).count().get(AggregateSource.SERVER)
            val suspendedTask = usersCol.whereEqualTo("isSuspended", true).count().get(AggregateSource.SERVER)
            val commentsTask = commentsCol.count().get(AggregateSource.SERVER)
            val reportsTask = reportsCol.count().get(AggregateSource.SERVER)

            val totalUsers = totalUsersTask.await().count
            val owners = ownersTask.await().count
            val mods = modsTask.await().count
            val premium = premiumTask.await().count
            val verified = verifiedTask.await().count
            val suspended = suspendedTask.await().count
            val comments = commentsTask.await().count
            val reports = reportsTask.await().count
            val members = maxOf(0L, totalUsers - owners - mods - premium)

            OwnerDashboardStats(
                totalUsers = totalUsers,
                ownersCount = owners,
                moderatorsCount = mods,
                premiumCount = premium,
                verifiedCount = verified,
                membersCount = members,
                suspendedCount = suspended,
                totalComments = comments,
                totalReports = reports
            )
        }.getOrElse { OwnerDashboardStats() }

        trySend(fetchStats())

        // Periodically refresh or listen to collection snapshot
        val registration = firestore.collection("users").limit(1).addSnapshotListener { _, _ ->
            launch(Dispatchers.IO) {
                trySend(fetchStats())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun getRecentUsers(limit: Long): Flow<List<AnimiteUser>> = callbackFlow {
        val query = firestore.collection("users")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)

        val reg = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val users = snapshot?.documents?.mapNotNull { it.toObject(AnimiteUser::class.java) }.orEmpty()
            trySend(users)
        }
        awaitClose { reg.remove() }
    }

    override fun searchUsers(query: String, limit: Long): Flow<List<AnimiteUser>> = callbackFlow {
        val clean = query.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Search by username range
        val usernameQuery = firestore.collection("users")
            .orderBy("username")
            .startAt(clean)
            .endAt(clean + "\uf8ff")
            .limit(limit)

        val reg = usernameQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val users = snapshot?.documents?.mapNotNull { it.toObject(AnimiteUser::class.java) }.orEmpty()
            trySend(users)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun updateUserRole(
        actorUser: AnimiteUser,
        targetUser: AnimiteUser,
        newRole: UserRole,
        customBadgeTitle: String?,
        reason: String,
    ): Result<Unit> = runCatching {
        require(actorUser.isOwner) { "Access denied: Only an application OWNER can change user roles." }
        require(actorUser.uid != targetUser.uid) { "Security violation: You cannot modify your own role." }

        val data = hashMapOf(
            "targetUid" to targetUser.uid,
            "newRole" to newRole.id,
            "customBadgeTitle" to customBadgeTitle?.trim()?.ifBlank { null },
            "reason" to reason
        )

        functions.getHttpsCallable("changeUserRole").call(data).await()
    }

    override suspend fun toggleUserVerification(
        actorUser: AnimiteUser,
        targetUser: AnimiteUser,
        isVerified: Boolean,
        reason: String,
    ): Result<Unit> = runCatching {
        require(actorUser.isOwner) { "Access denied: Only an application OWNER can manage verification." }

        val data = hashMapOf(
            "targetUid" to targetUser.uid,
            "isVerified" to isVerified,
            "reason" to reason
        )

        functions.getHttpsCallable("toggleUserVerification").call(data).await()
    }

    override suspend fun updatePremiumMembership(
        actorUser: AnimiteUser,
        targetUser: AnimiteUser,
        durationDays: Int?,
        reason: String,
    ): Result<Unit> = runCatching {
        require(actorUser.isOwner) { "Access denied: Only an application OWNER can grant or revoke premium membership." }

        val data = hashMapOf(
            "targetUid" to targetUser.uid,
            "durationDays" to durationDays,
            "reason" to reason
        )

        functions.getHttpsCallable("updatePremiumMembership").call(data).await()
    }

    override suspend fun setUserSuspended(
        actorUser: AnimiteUser,
        targetUser: AnimiteUser,
        isSuspended: Boolean,
        reason: String,
    ): Result<Unit> = runCatching {
        require(actorUser.isOwner || (actorUser.isModerator && !targetUser.isOwner)) {
            "Access denied: You do not have permission to suspend this user."
        }
        require(actorUser.uid != targetUser.uid) { "Security violation: You cannot suspend your own account." }
        require(!targetUser.isOwner) { "Security violation: The application owner cannot be suspended." }

        val data = hashMapOf(
            "targetUid" to targetUser.uid,
            "isSuspended" to isSuspended,
            "reason" to reason
        )

        functions.getHttpsCallable("setUserSuspended").call(data).await()
    }

    override fun getAuditLogs(limit: Long): Flow<List<AuditLog>> = callbackFlow {
        val query = firestore.collection("audit_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)

        val reg = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val logs = snapshot?.documents?.mapNotNull { it.toObject(AuditLog::class.java) }.orEmpty()
            trySend(logs)
        }
        awaitClose { reg.remove() }
    }

    override fun getSystemSettings(): Flow<SystemSettings> = callbackFlow {
        val docRef = firestore.collection("system_settings").document("config")
        val reg = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(SystemSettings.DEFAULT)
                return@addSnapshotListener
            }
            val settings = snapshot.toObject(SystemSettings::class.java) ?: SystemSettings.DEFAULT
            trySend(settings)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun updateSystemSettings(
        actorUser: AnimiteUser,
        newSettings: SystemSettings,
        reason: String,
    ): Result<Unit> = runCatching {
        require(actorUser.isOwner) { "Access denied: Only an application OWNER can modify system settings." }

        val data = hashMapOf(
            "maintenanceMode" to newSettings.maintenanceMode,
            "allowRegistrations" to newSettings.allowRegistrations,
            "commentsEnabled" to newSettings.commentsEnabled,
            "directMessagesEnabled" to newSettings.directMessagesEnabled,
            "announcementBanner" to newSettings.announcementBanner,
            "reason" to reason
        )

        functions.getHttpsCallable("updateSystemSettings").call(data).await()
    }
}
