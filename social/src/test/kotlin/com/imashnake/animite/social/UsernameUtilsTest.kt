package com.imashnake.animite.social

import com.imashnake.animite.social.data.UsernameUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameUtilsTest {

    @Test
    fun baseUsername_arabicDisplayName_fallsBackToEmail() {
        // An Arabic Google display name sanitises to nothing usable; the old
        // code fed that into the availability loop, which returned false for
        // the invalid candidate forever and sign-in never finished loading.
        val base = UsernameUtils.baseUsernameFor(
            displayName = "رفيق عبد الإله",
            email = "rafiq.animeta@gmail.com",
            uid = "abc123xyz",
        )
        assertEquals("rafiq.animeta", base)
        assertTrue(UsernameUtils.isValidUsername(base))
    }

    @Test
    fun baseUsername_noLatinNameOrEmail_fallsBackToUid() {
        val base = UsernameUtils.baseUsernameFor(
            displayName = "رفيق",
            email = null,
            uid = "xY9abcDEF",
        )
        assertEquals("userxy9abc", base)
        assertTrue(UsernameUtils.isValidUsername(base))
    }

    @Test
    fun baseUsername_englishName_isSanitised() {
        val base = UsernameUtils.baseUsernameFor(
            displayName = "Ken Kaneki!",
            email = null,
            uid = "uid123",
        )
        assertEquals("kenkaneki", base)
    }

    @Test
    fun baseUsername_shortName_fallsBack() {
        val base = UsernameUtils.baseUsernameFor(
            displayName = "Al",
            email = "al99@example.com",
            uid = "uid123",
        )
        assertEquals("al99", base)
    }

    @Test
    fun usernameCandidate_appendsSuffixWithinMaxLength() {
        val base = "a".repeat(30)
        val candidate = UsernameUtils.usernameCandidate(base, 12)
        assertEquals(30, candidate.length)
        assertTrue(candidate.endsWith("12"))
        assertTrue(UsernameUtils.isValidUsername(candidate))
    }

    @Test
    fun fallbackUsername_isValid() {
        val fallback = UsernameUtils.fallbackUsername("kenkaneki", "AbC-123_xYz")
        assertTrue(UsernameUtils.isValidUsername(fallback))
    }
}
