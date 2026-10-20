package com.imashnake.animite.social.data

import java.util.Locale

/**
 * Username rules are enforced in three places that must agree with each other:
 * the sign-up form (`^[a-z0-9_.]+$`, 3..30 chars), [AuthRepository.isUsernameAvailable]
 * (which returns `false` for any invalid candidate) and the Firestore rules for
 * `users/{uid}` (username must be 3..30 chars).
 *
 * Google accounts frequently have display names that break those rules
 * (e.g. Arabic names, names with spaces, or very short names). Sanitising has
 * to happen *before* a candidate is checked for availability: feeding an
 * invalid candidate into an availability loop makes the check return `false`
 * forever and the loop never terminates, leaving sign-in stuck on Loading.
 */
object UsernameUtils {

    const val MIN_USERNAME_LENGTH = 3
    const val MAX_USERNAME_LENGTH = 30

    private val VALID_USERNAME = Regex("^[a-z0-9_.]+$")

    fun isValidUsername(username: String): Boolean =
        username.length in MIN_USERNAME_LENGTH..MAX_USERNAME_LENGTH &&
            username.matches(VALID_USERNAME)

    /** Keeps only characters a username may contain; everything else is dropped. */
    fun sanitizeUsername(raw: String?): String =
        raw.orEmpty()
            .trim()
            .lowercase(Locale.ROOT)
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' }
            .trim('.', '_')
            .take(MAX_USERNAME_LENGTH)

    /**
     * Picks a valid base username for an account that did not choose one
     * (Google sign-in). Falls back from the display name to the email prefix
     * and finally to a uid-derived value, so the result is always a valid
     * username even when the display name is entirely non-Latin.
     */
    fun baseUsernameFor(displayName: String?, email: String?, uid: String): String {
        val fromDisplayName = sanitizeUsername(displayName)
        if (fromDisplayName.length >= MIN_USERNAME_LENGTH) return fromDisplayName

        val fromEmail = sanitizeUsername(email?.substringBefore('@'))
        if (fromEmail.length >= MIN_USERNAME_LENGTH) return fromEmail

        val uidSuffix = sanitizeUsername(uid).take(6)
        return if (uidSuffix.isNotBlank()) "user$uidSuffix" else "user"
    }

    /**
     * The candidate to try after [base]: [base] itself for attempt 0, then
     * `base1`, `base2`, ... truncated so the result never exceeds the maximum
     * length (an over-long suffix would make the candidate invalid again).
     */
    fun usernameCandidate(base: String, attempt: Int): String {
        if (attempt <= 0) return base.take(MAX_USERNAME_LENGTH)
        val suffix = attempt.toString()
        return base.take(MAX_USERNAME_LENGTH - suffix.length) + suffix
    }

    /** Last-resort candidate used when the numbered attempts are exhausted. */
    fun fallbackUsername(base: String, uid: String): String {
        val uidSuffix = sanitizeUsername(uid).take(6).ifBlank { "1" }
        val separator = "_"
        val keep = MAX_USERNAME_LENGTH - separator.length - uidSuffix.length
        return base.take(keep) + separator + uidSuffix
    }
}
