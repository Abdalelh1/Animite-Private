/**
 * Animeta Initial Owner Bootstrap Script
 *
 * This script runs in a secure, trusted backend or administrative CLI environment.
 * It NEVER runs on the Android client and NEVER embeds credentials in the APK.
 *
 * Usage:
 *   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
 *   node scripts/bootstrap-owner.js <TARGET_FIREBASE_AUTH_UID> [--force]
 */

const admin = require('firebase-admin');

async function bootstrapOwner() {
    const args = process.argv.slice(2);
    const forceFlag = args.includes('--force');
    const ownerUid = args.find(arg => !arg.startsWith('--')) || process.env.INITIAL_OWNER_UID;

    if (!ownerUid || typeof ownerUid !== 'string' || ownerUid.trim().length === 0) {
        console.error('ERROR: You must supply a valid target Firebase Auth UID as an argument or INITIAL_OWNER_UID env var.');
        console.error('Usage: node scripts/bootstrap-owner.js <TARGET_FIREBASE_AUTH_UID> [--force]');
        process.exit(1);
    }

    // UID Validation: standard Firebase Auth UID pattern (5-128 alphanumeric, dash, underscore)
    const uidPattern = /^[a-zA-Z0-9_\-\.]{5,128}$/;
    if (!uidPattern.test(ownerUid.trim())) {
        console.error(`ERROR: Target UID "${ownerUid}" does not match the valid Firebase Auth UID format.`);
        process.exit(1);
    }

    const cleanOwnerUid = ownerUid.trim();

    if (!process.env.GOOGLE_APPLICATION_CREDENTIALS && !admin.apps.length) {
        console.log('Initializing Firebase Admin SDK using application default credentials...');
        admin.initializeApp({
            projectId: 'anime-d39b9'
        });
    } else if (!admin.apps.length) {
        admin.initializeApp();
    }

    const auth = admin.auth();
    const firestore = admin.firestore();

    try {
        console.log(`Checking system ownership registry and existing owners...`);

        // Check 1: Query existing users with role == 'owner'
        const existingOwnersSnap = await firestore.collection('users')
            .where('role', '==', 'owner')
            .get();

        const activeOwnerUids = existingOwnersSnap.docs.map(doc => doc.id);

        // Check 2: Query bootstrap state lock document
        const bootstrapLockRef = firestore.collection('system_settings').doc('bootstrap_state');
        const bootstrapLockSnap = await bootstrapLockRef.get();

        if (activeOwnerUids.length > 0 && !forceFlag) {
            console.error('\n==================== [SECURITY ABORT] ====================');
            console.error(`Bootstrap aborted: Active owner(s) already exist in the system:`);
            activeOwnerUids.forEach(uid => console.error(`  - UID: ${uid}`));
            console.error('\nAccidental or unauthorized re-bootstrap is blocked.');
            console.error('To grant ownership to additional users, sign in as an existing owner');
            console.error('and manage roles securely via the Animeta Owner Control Center.');
            console.error('If performing emergency disaster recovery, re-run with explicit "--force".');
            console.error('==========================================================\n');
            process.exit(1);
        }

        if (bootstrapLockSnap.exists && !forceFlag) {
            const lockData = bootstrapLockSnap.data() || {};
            console.error('\n==================== [SECURITY ABORT] ====================');
            console.error(`Bootstrap aborted: System bootstrap was already performed at ${new Date(lockData.bootstrappedAt).toISOString()}`);
            console.error(`Initial Owner UID: ${lockData.initialOwnerUid}`);
            console.error('Re-run with "--force" only for authorized emergency owner re-assignment.');
            console.error('==========================================================\n');
            process.exit(1);
        }

        console.log(`Verifying target UID in Firebase Authentication: ${cleanOwnerUid}...`);
        const userRecord = await auth.getUser(cleanOwnerUid);
        console.log(`User found: ${userRecord.email || userRecord.displayName || cleanOwnerUid}`);

        console.log('Assigning cryptographically signed custom claims (role: owner, admin: true)...');
        await auth.setCustomUserClaims(cleanOwnerUid, {
            role: 'owner',
            admin: true,
            promotedAt: Date.now()
        });

        console.log('Updating Firestore user document...');
        const userRef = firestore.collection('users').doc(cleanOwnerUid);
        const userSnap = await userRef.get();

        const updates = {
            role: 'owner',
            customBadgeTitle: 'مالك التطبيق',
            isVerified: true,
            updatedAt: Date.now()
        };

        if (userSnap.exists) {
            await userRef.update(updates);
        } else {
            updates.uid = cleanOwnerUid;
            updates.username = userRecord.email ? userRecord.email.split('@')[0] : `owner_${cleanOwnerUid.substring(0, 6)}`;
            updates.displayName = userRecord.displayName || 'Application Owner';
            updates.createdAt = Date.now();
            await userRef.set(updates, { merge: true });
        }

        console.log('Synchronizing transactional ownership registry...');
        const registryRef = firestore.collection('system_settings').doc('ownership_registry');
        const registrySnap = await registryRef.get();
        let currentOwners = registrySnap.exists ? (registrySnap.data().ownerUids || []) : [];
        if (!currentOwners.includes(cleanOwnerUid)) {
            currentOwners.push(cleanOwnerUid);
        }
        await registryRef.set({
            ownerUids: currentOwners,
            updatedAt: Date.now(),
            updatedBy: 'SYSTEM_BOOTSTRAP'
        }, { merge: true });

        console.log('Writing persistent bootstrap lock...');
        await bootstrapLockRef.set({
            bootstrappedAt: Date.now(),
            initialOwnerUid: cleanOwnerUid,
            bootstrappedBy: process.env.USER || process.env.USERNAME || 'CLI_ADMIN',
            forced: forceFlag
        });

        console.log('Recording immutable audit log...');
        const auditLogId = `bootstrap_${Date.now()}`;
        await firestore.collection('audit_logs').doc(auditLogId).set({
            id: auditLogId,
            actorUid: 'SYSTEM_BOOTSTRAP',
            actorName: 'Firebase Admin SDK (CLI)',
            actorRole: 'system',
            targetUid: cleanOwnerUid,
            targetName: userRecord.displayName || userRecord.email || cleanOwnerUid,
            action: 'ROLE_CHANGED',
            details: `Initial owner bootstrap executed securely via Firebase Admin SDK (force=${forceFlag})`,
            previousValue: userSnap.exists ? (userSnap.data().role || 'user') : 'none',
            newValue: 'owner',
            timestamp: Date.now()
        });

        console.log('\n SUCCESS: Initial owner established securely!');
        console.log(`UID: ${cleanOwnerUid}`);
        console.log(`Claims: { role: 'owner', admin: true }`);
        console.log('The owner can now sign in to Animeta and access the Owner Control Center.');
        process.exit(0);
    } catch (error) {
        console.error(' FAILED to bootstrap initial owner:', error);
        process.exit(1);
    }
}

bootstrapOwner();
