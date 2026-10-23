import * as admin from 'firebase-admin';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';

if (!admin.apps.length) {
    admin.initializeApp();
}

const db = admin.firestore();
const auth = admin.auth();

/**
 * Helper to assert caller has owner authority via cryptographic custom claims
 */
function assertCallerIsOwner(contextAuth: any): string {
    if (!contextAuth || !contextAuth.uid) {
        throw new HttpsError('unauthenticated', 'User must be authenticated.');
    }
    const token = contextAuth.token;
    if (token.role !== 'owner') {
        throw new HttpsError('permission-denied', 'Access denied: Requires application OWNER privileges.');
    }
    return contextAuth.uid;
}

/**
 * Helper to assert caller has admin/moderator authority via custom claims
 */
function assertCallerIsModeratorOrOwner(contextAuth: any): { uid: string; role: string } {
    if (!contextAuth || !contextAuth.uid) {
        throw new HttpsError('unauthenticated', 'User must be authenticated.');
    }
    const role = contextAuth.token.role;
    if (role !== 'owner' && role !== 'moderator' && role !== 'admin') {
        throw new HttpsError('permission-denied', 'Access denied: Requires MODERATOR or OWNER privileges.');
    }
    return { uid: contextAuth.uid, role };
}

/**
 * Ensures the ownership registry document exists in system_settings/ownership_registry.
 * Automatically bootstraps/recovers by scanning for existing owners if not yet created.
 */
async function ensureOwnershipRegistryInitialized(): Promise<void> {
    const registryRef = db.collection('system_settings').doc('ownership_registry');
    const snap = await registryRef.get();
    if (!snap.exists) {
        const ownersSnap = await db.collection('users').where('role', '==', 'owner').get();
        const initialOwnerUids = ownersSnap.docs.map(doc => doc.id);
        await registryRef.set({
            ownerUids: initialOwnerUids,
            updatedAt: Date.now(),
            updatedBy: 'RECOVERY_INITIALIZER'
        }, { merge: true });
    }
}

/**
 * Trusted backend callable function to change a user's role.
 * Enforces race-safe transactional final-owner protection, updates the ownership registry,
 * synchronizes custom claims, and generates server-side immutable audit logs.
 */
export const changeUserRole = onCall(async (request) => {
    const callerUid = assertCallerIsOwner(request.auth);
    const { targetUid, newRole, customBadgeTitle, reason } = request.data || {};

    if (!targetUid || typeof targetUid !== 'string' || targetUid.trim().length === 0) {
        throw new HttpsError('invalid-argument', 'Valid targetUid must be provided.');
    }
    const allowedRoles = ['owner', 'moderator', 'premium', 'verified', 'member', 'user'];
    if (!newRole || !allowedRoles.includes(String(newRole).toLowerCase())) {
        throw new HttpsError('invalid-argument', `newRole must be one of: ${allowedRoles.join(', ')}`);
    }

    const cleanNewRole = String(newRole).toLowerCase();

    if (callerUid === targetUid && cleanNewRole !== 'owner') {
        throw new HttpsError('failed-precondition', 'Security violation: Cannot demote your own account.');
    }

    await ensureOwnershipRegistryInitialized();

    const callerRecord = await auth.getUser(callerUid);
    const callerName = callerRecord.displayName || callerRecord.email || callerUid;

    let targetName = targetUid;
    let currentRole = 'user';

    // Atomic transaction for final-owner protection and state mutation
    await db.runTransaction(async (transaction) => {
        const registryRef = db.collection('system_settings').doc('ownership_registry');
        const registrySnap = await transaction.get(registryRef);
        let ownerUids: string[] = registrySnap.exists ? (registrySnap.data()?.ownerUids || []) : [];

        const targetUserRef = db.collection('users').doc(targetUid);
        const targetUserSnap = await transaction.get(targetUserRef);
        if (!targetUserSnap.exists) {
            throw new HttpsError('not-found', 'Target user document does not exist.');
        }

        const currentData = targetUserSnap.data() || {};
        currentRole = String(currentData.role || 'user').toLowerCase();
        targetName = currentData.displayName || currentData.username || targetUid;

        const isCurrentlyOwner = currentRole === 'owner' || ownerUids.includes(targetUid);

        // Final Owner Protection: verify atomically inside transaction
        if (isCurrentlyOwner && cleanNewRole !== 'owner') {
            const remainingOwners = ownerUids.filter(uid => uid !== targetUid);
            if (remainingOwners.length < 1) {
                throw new HttpsError('failed-precondition', 'Security violation: Cannot demote the final application owner.');
            }
            ownerUids = remainingOwners;
        } else if (cleanNewRole === 'owner') {
            if (!ownerUids.includes(targetUid)) {
                ownerUids.push(targetUid);
            }
        }

        // 1. Update transactional ownership registry
        transaction.set(registryRef, {
            ownerUids: ownerUids,
            updatedAt: Date.now(),
            updatedBy: callerUid
        }, { merge: true });

        // 2. Update Firestore user document
        const updates: Record<string, any> = {
            role: cleanNewRole,
            customBadgeTitle: customBadgeTitle ? String(customBadgeTitle).trim() : null,
            updatedAt: Date.now()
        };
        transaction.update(targetUserRef, updates);

        // 3. Write immutable audit log
        const auditLogId = `role_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
        const auditRef = db.collection('audit_logs').doc(auditLogId);
        transaction.set(auditRef, {
            id: auditLogId,
            actorUid: callerUid,
            actorName: callerName,
            actorRole: 'owner',
            targetUid: targetUid,
            targetName: targetName,
            action: 'ROLE_CHANGED',
            details: reason ? String(reason) : `Role changed from ${currentRole} to ${cleanNewRole}`,
            previousValue: currentRole,
            newValue: cleanNewRole,
            timestamp: Date.now()
        });
    });

    // 4. Synchronize Firebase Auth Custom Claims (after transaction commit)
    const customClaims: Record<string, any> = {
        role: cleanNewRole,
        admin: cleanNewRole === 'owner' || cleanNewRole === 'moderator' || cleanNewRole === 'admin',
        updatedAt: Date.now()
    };
    await auth.setCustomUserClaims(targetUid, customClaims);

    return { success: true, targetUid, newRole: cleanNewRole };
});

/**
 * Trusted backend callable function to grant or revoke premium membership.
 * Preserves administrative and verification roles.
 */
export const updatePremiumMembership = onCall(async (request) => {
    const callerUid = assertCallerIsOwner(request.auth);
    const { targetUid, durationDays, reason } = request.data || {};

    if (!targetUid || typeof targetUid !== 'string' || targetUid.trim().length === 0) {
        throw new HttpsError('invalid-argument', 'Valid targetUid must be provided.');
    }

    const targetUserRef = db.collection('users').doc(targetUid);
    const targetUserSnap = await targetUserRef.get();
    if (!targetUserSnap.exists) {
        throw new HttpsError('not-found', 'Target user document does not exist.');
    }

    const currentData = targetUserSnap.data() || {};
    const currentRole = String(currentData.role || 'user').toLowerCase();
    const isPrivilegedAdmin = currentRole === 'owner' || currentRole === 'moderator' || currentRole === 'admin';

    const updates: Record<string, any> = {
        updatedAt: Date.now()
    };

    let action = 'PREMIUM_GRANTED';
    let newValueDesc = '';

    if (durationDays === 0) {
        // Revocation
        if (!isPrivilegedAdmin && currentRole === 'premium') {
            updates.role = currentData.isVerified ? 'verified' : 'member';
        }
        updates.premiumUntil = null;
        action = 'PREMIUM_REVOKED';
        newValueDesc = 'Revoked';
    } else if (durationDays === null || durationDays === undefined) {
        // Lifetime
        if (!isPrivilegedAdmin) {
            updates.role = 'premium';
        }
        updates.premiumUntil = null;
        newValueDesc = 'Lifetime';
    } else {
        // Temporary days
        const days = Number(durationDays);
        if (isNaN(days) || days <= 0) {
            throw new HttpsError('invalid-argument', 'durationDays must be positive integer or null or 0.');
        }
        const currentExpiry = currentData.premiumUntil ? Number(currentData.premiumUntil) : Date.now();
        const startMillis = Math.max(currentExpiry, Date.now());
        const expiryMillis = startMillis + (days * 24 * 60 * 60 * 1000);

        if (!isPrivilegedAdmin) {
            updates.role = 'premium';
        }
        updates.premiumUntil = expiryMillis;
        newValueDesc = `${days} days (until ${expiryMillis})`;
    }

    await targetUserRef.update(updates);

    // Write server-side audit log
    const auditLogId = `prem_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    const callerRecord = await auth.getUser(callerUid);
    await db.collection('audit_logs').doc(auditLogId).set({
        id: auditLogId,
        actorUid: callerUid,
        actorName: callerRecord.displayName || callerRecord.email || callerUid,
        actorRole: 'owner',
        targetUid: targetUid,
        targetName: currentData.displayName || currentData.username || targetUid,
        action: action,
        details: reason ? String(reason) : `Premium updated: ${newValueDesc}`,
        previousValue: currentData.premiumUntil ? String(currentData.premiumUntil) : 'None',
        newValue: newValueDesc,
        timestamp: Date.now()
    });

    return { success: true, targetUid, status: newValueDesc };
});

/**
 * Trusted backend callable function to toggle user verification status.
 */
export const toggleUserVerification = onCall(async (request) => {
    const callerUid = assertCallerIsOwner(request.auth);
    const { targetUid, isVerified, reason } = request.data || {};

    if (!targetUid || typeof targetUid !== 'string' || targetUid.trim().length === 0) {
        throw new HttpsError('invalid-argument', 'Valid targetUid must be provided.');
    }

    const targetUserRef = db.collection('users').doc(targetUid);
    const targetUserSnap = await targetUserRef.get();
    if (!targetUserSnap.exists) {
        throw new HttpsError('not-found', 'Target user document does not exist.');
    }

    const currentData = targetUserSnap.data() || {};
    const currentVerified = Boolean(currentData.isVerified);
    const newVerified = Boolean(isVerified);

    await targetUserRef.update({
        isVerified: newVerified,
        updatedAt: Date.now()
    });

    const auditLogId = `verif_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    const callerRecord = await auth.getUser(callerUid);
    await db.collection('audit_logs').doc(auditLogId).set({
        id: auditLogId,
        actorUid: callerUid,
        actorName: callerRecord.displayName || callerRecord.email || callerUid,
        actorRole: 'owner',
        targetUid: targetUid,
        targetName: currentData.displayName || currentData.username || targetUid,
        action: newVerified ? 'VERIFICATION_GRANTED' : 'VERIFICATION_REVOKED',
        details: reason ? String(reason) : `Verification set to ${newVerified}`,
        previousValue: String(currentVerified),
        newValue: String(newVerified),
        timestamp: Date.now()
    });

    return { success: true, targetUid, isVerified: newVerified };
});

/**
 * Trusted backend callable function to suspend or restore user accounts.
 * Prevents suspending an owner by consulting the transactional registry.
 */
export const setUserSuspended = onCall(async (request) => {
    const { uid: callerUid, role: callerRole } = assertCallerIsModeratorOrOwner(request.auth);
    const { targetUid, isSuspended, reason } = request.data || {};

    if (!targetUid || typeof targetUid !== 'string' || targetUid.trim().length === 0) {
        throw new HttpsError('invalid-argument', 'Valid targetUid must be provided.');
    }

    if (callerUid === targetUid) {
        throw new HttpsError('failed-precondition', 'Security violation: Cannot suspend your own account.');
    }

    await ensureOwnershipRegistryInitialized();

    const callerRecord = await auth.getUser(callerUid);
    const callerName = callerRecord.displayName || callerRecord.email || callerUid;

    let targetName = targetUid;

    await db.runTransaction(async (transaction) => {
        const registryRef = db.collection('system_settings').doc('ownership_registry');
        const registrySnap = await transaction.get(registryRef);
        const ownerUids: string[] = registrySnap.exists ? (registrySnap.data()?.ownerUids || []) : [];

        const targetUserRef = db.collection('users').doc(targetUid);
        const targetUserSnap = await transaction.get(targetUserRef);
        if (!targetUserSnap.exists) {
            throw new HttpsError('not-found', 'Target user document does not exist.');
        }

        const currentData = targetUserSnap.data() || {};
        const currentRole = String(currentData.role || 'user').toLowerCase();
        targetName = currentData.displayName || currentData.username || targetUid;

        if (currentRole === 'owner' || ownerUids.includes(targetUid)) {
            throw new HttpsError('permission-denied', 'Security violation: The application owner cannot be suspended.');
        }

        transaction.update(targetUserRef, {
            isSuspended: Boolean(isSuspended),
            updatedAt: Date.now()
        });

        const auditLogId = `susp_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
        const auditRef = db.collection('audit_logs').doc(auditLogId);
        transaction.set(auditRef, {
            id: auditLogId,
            actorUid: callerUid,
            actorName: callerName,
            actorRole: callerRole,
            targetUid: targetUid,
            targetName: targetName,
            action: isSuspended ? 'ACCOUNT_SUSPENDED' : 'ACCOUNT_RESTORED',
            details: reason ? String(reason) : (isSuspended ? 'Account suspended' : 'Account restored'),
            previousValue: String(currentData.isSuspended || false),
            newValue: String(Boolean(isSuspended)),
            timestamp: Date.now()
        });
    });

    // Disable in Firebase Auth
    await auth.updateUser(targetUid, {
        disabled: Boolean(isSuspended)
    });

    return { success: true, targetUid, isSuspended: Boolean(isSuspended) };
});

/**
 * Trusted backend callable function to update system settings.
 * Strictly verifies owner authority and validates all field names and data types.
 */
export const updateSystemSettings = onCall(async (request) => {
    const callerUid = assertCallerIsOwner(request.auth);
    const data = request.data;
    if (!data || typeof data !== 'object') {
        throw new HttpsError('invalid-argument', 'Settings data payload must be an object.');
    }

    const allowedKeys = [
        'maintenanceMode',
        'maintenanceMessage',
        'allowRegistrations',
        'commentsEnabled',
        'directMessagesEnabled',
        'announcementsEnabled',
        'announcementText',
        'announcementBanner',
        'minSupportedAppVersion',
        'reason'
    ];

    for (const key of Object.keys(data)) {
        if (!allowedKeys.includes(key)) {
            throw new HttpsError('invalid-argument', `Unrecognized setting field: ${key}`);
        }
    }

    if (data.maintenanceMode !== undefined && typeof data.maintenanceMode !== 'boolean') {
        throw new HttpsError('invalid-argument', 'maintenanceMode must be a boolean.');
    }
    if (data.maintenanceMessage !== undefined && typeof data.maintenanceMessage !== 'string') {
        throw new HttpsError('invalid-argument', 'maintenanceMessage must be a string.');
    }
    if (data.allowRegistrations !== undefined && typeof data.allowRegistrations !== 'boolean') {
        throw new HttpsError('invalid-argument', 'allowRegistrations must be a boolean.');
    }
    if (data.commentsEnabled !== undefined && typeof data.commentsEnabled !== 'boolean') {
        throw new HttpsError('invalid-argument', 'commentsEnabled must be a boolean.');
    }
    if (data.directMessagesEnabled !== undefined && typeof data.directMessagesEnabled !== 'boolean') {
        throw new HttpsError('invalid-argument', 'directMessagesEnabled must be a boolean.');
    }
    if (data.announcementsEnabled !== undefined && typeof data.announcementsEnabled !== 'boolean') {
        throw new HttpsError('invalid-argument', 'announcementsEnabled must be a boolean.');
    }
    if (data.announcementText !== undefined && typeof data.announcementText !== 'string') {
        throw new HttpsError('invalid-argument', 'announcementText must be a string.');
    }
    if (data.announcementBanner !== undefined && typeof data.announcementBanner !== 'string') {
        throw new HttpsError('invalid-argument', 'announcementBanner must be a string.');
    }
    if (data.minSupportedAppVersion !== undefined && (!Number.isInteger(data.minSupportedAppVersion) || data.minSupportedAppVersion < 1)) {
        throw new HttpsError('invalid-argument', 'minSupportedAppVersion must be an integer >= 1.');
    }

    const settingsRef = db.collection('system_settings').doc('config');
    const existingSnap = await settingsRef.get();
    const existingData = existingSnap.exists ? existingSnap.data() || {} : {};

    const cleanSettings: Record<string, any> = {
        maintenanceMode: data.maintenanceMode !== undefined ? Boolean(data.maintenanceMode) : (existingData.maintenanceMode ?? false),
        maintenanceMessage: typeof data.maintenanceMessage === 'string' ? data.maintenanceMessage.substring(0, 500) : (existingData.maintenanceMessage ?? ''),
        allowRegistrations: data.allowRegistrations !== undefined ? Boolean(data.allowRegistrations) : (existingData.allowRegistrations ?? true),
        commentsEnabled: data.commentsEnabled !== undefined ? Boolean(data.commentsEnabled) : (existingData.commentsEnabled ?? true),
        directMessagesEnabled: data.directMessagesEnabled !== undefined ? Boolean(data.directMessagesEnabled) : (existingData.directMessagesEnabled ?? true),
        announcementsEnabled: data.announcementsEnabled !== undefined ? Boolean(data.announcementsEnabled) : (existingData.announcementsEnabled ?? false),
        announcementText: typeof data.announcementText === 'string' ? data.announcementText.substring(0, 1000) : (existingData.announcementText ?? ''),
        announcementBanner: typeof data.announcementBanner === 'string' ? data.announcementBanner.substring(0, 1000) : (existingData.announcementBanner ?? ''),
        minSupportedAppVersion: data.minSupportedAppVersion !== undefined ? data.minSupportedAppVersion : (existingData.minSupportedAppVersion ?? 1),
        updatedAt: Date.now(),
        updatedByUid: callerUid
    };

    await settingsRef.set(cleanSettings, { merge: true });

    const auditLogId = `settings_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    const callerRecord = await auth.getUser(callerUid);
    await db.collection('audit_logs').doc(auditLogId).set({
        id: auditLogId,
        actorUid: callerUid,
        actorName: callerRecord.displayName || callerRecord.email || callerUid,
        actorRole: 'owner',
        targetUid: 'SYSTEM',
        targetName: 'Global Config',
        action: 'SYSTEM_SETTINGS_UPDATED',
        details: data.reason ? String(data.reason) : `Maintenance: ${cleanSettings.maintenanceMode}, Regs: ${cleanSettings.allowRegistrations}`,
        previousValue: JSON.stringify(existingData),
        newValue: JSON.stringify(cleanSettings),
        timestamp: Date.now()
    });

    return { success: true, settings: cleanSettings };
});

/**
 * Scheduled Cloud Function running every 24 hours to prune expired premium memberships.
 * Uses bounded pagination (limit 500 per batch) to prevent OOM/timeouts.
 * Preserves OWNER, MODERATOR, and VERIFIED status independently of premium expiration.
 */
export const cleanupExpiredPremium = onSchedule('every 24 hours', async () => {
    const now = Date.now();
    console.log(`Starting automated cleanup of expired premium memberships at ${new Date(now).toISOString()}...`);

    const BATCH_SIZE = 500;
    let totalProcessed = 0;
    let hasMore = true;

    while (hasMore) {
        const snapshot = await db.collection('users')
            .where('role', '==', 'premium')
            .where('premiumUntil', '<=', now)
            .limit(BATCH_SIZE)
            .get();

        if (snapshot.empty) {
            hasMore = false;
            break;
        }

        console.log(`Processing batch of ${snapshot.docs.length} expired premium users...`);
        const batch = db.batch();

        for (const doc of snapshot.docs) {
            const userData = doc.data();
            const currentRole = String(userData.role || '').toLowerCase();

            // Safety guard: Never demote OWNER, MODERATOR, or ADMIN
            if (currentRole === 'owner' || currentRole === 'moderator' || currentRole === 'admin') {
                batch.update(doc.ref, {
                    premiumUntil: null,
                    updatedAt: now
                });
            } else {
                // Fall back to 'verified' if they hold a verified badge, else 'member'
                const fallbackRole = userData.isVerified ? 'verified' : 'member';
                batch.update(doc.ref, {
                    role: fallbackRole,
                    premiumUntil: null,
                    updatedAt: now
                });
            }
            totalProcessed++;
        }

        await batch.commit();

        if (snapshot.docs.length < BATCH_SIZE) {
            hasMore = false;
        }
    }

    if (totalProcessed > 0) {
        const auditLogId = `cleanup_${now}`;
        await db.collection('audit_logs').doc(auditLogId).set({
            id: auditLogId,
            actorUid: 'SYSTEM_SCHEDULER',
            actorName: 'Cloud Scheduler',
            actorRole: 'system',
            targetUid: 'ALL_EXPIRED_USERS',
            targetName: `${totalProcessed} users`,
            action: 'PREMIUM_REVOKED',
            details: `Automated cleanup expired premium for ${totalProcessed} accounts with bounded pagination.`,
            previousValue: 'premium',
            newValue: 'expired_cleanup',
            timestamp: now
        });
    }

    console.log(`Successfully completed cleanup for ${totalProcessed} expired premium accounts.`);
});
