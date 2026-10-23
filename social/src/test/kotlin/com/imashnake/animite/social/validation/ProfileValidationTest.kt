package com.imashnake.animite.social.validation

import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.FieldVisibility
import com.imashnake.animite.social.model.LayoutPreset
import com.imashnake.animite.social.model.ProfileCustomization
import com.imashnake.animite.social.model.ProfilePrivacy
import com.imashnake.animite.social.model.ProfileVisibility
import com.imashnake.animite.social.model.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidationTest {

    @Test
    fun displayNameValidation_enforcesBoundsAndHtmlProtection() {
        assertTrue(ProfileValidator.validateDisplayName("Ken").isValid)
        assertTrue(ProfileValidator.validateDisplayName("عمر أحمد").isValid)

        // Too short (< 2)
        val shortResult = ProfileValidator.validateDisplayName("A")
        assertFalse(shortResult.isValid)
        assertTrue((shortResult as ValidationResult.Invalid).reason.contains("at least 2"))

        // Too long (> 40)
        val longResult = ProfileValidator.validateDisplayName("A".repeat(45))
        assertFalse(longResult.isValid)
        assertTrue((longResult as ValidationResult.Invalid).reason.contains("not exceed 40"))

        // HTML script injection
        val scriptResult = ProfileValidator.validateDisplayName("<script>alert(1)</script>")
        assertFalse(scriptResult.isValid)
        assertTrue((scriptResult as ValidationResult.Invalid).reason.contains("HTML"))

        // Null
        assertFalse(ProfileValidator.validateDisplayName(null).isValid)
    }

    @Test
    fun usernameValidation_enforcesRegexAndBounds() {
        assertTrue(ProfileValidator.validateUsername("kaneki_99").isValid)
        assertTrue(ProfileValidator.validateUsername("user.name").isValid)

        // Too short (< 3)
        assertFalse(ProfileValidator.validateUsername("ab").isValid)

        // Invalid chars (spaces, uppercase, symbols)
        assertFalse(ProfileValidator.validateUsername("User Name").isValid)
        assertFalse(ProfileValidator.validateUsername("user@name").isValid)
        assertFalse(ProfileValidator.validateUsername("user!").isValid)
    }

    @Test
    fun bioValidation_enforcesLengthAndHtmlProtection() {
        assertTrue(ProfileValidator.validateBio(null).isValid)
        assertTrue(ProfileValidator.validateBio("").isValid)
        assertTrue(ProfileValidator.validateBio("مطور ومحب للأنمي").isValid)

        // > 300 chars
        val longBio = "A".repeat(301)
        assertFalse(ProfileValidator.validateBio(longBio).isValid)

        // HTML tags
        assertFalse(ProfileValidator.validateBio("<b>Bold text</b>").isValid)
    }

    @Test
    fun locationValidation_enforcesLengthAndHtmlProtection() {
        assertTrue(ProfileValidator.validateLocation("Tokyo, Japan").isValid)
        assertTrue(ProfileValidator.validateLocation(null).isValid)

        val longLocation = "L".repeat(101)
        assertFalse(ProfileValidator.validateLocation(longLocation).isValid)
        assertFalse(ProfileValidator.validateLocation("<div/>").isValid)
    }

    @Test
    fun websiteValidation_enforcesHttpsProtocol() {
        assertTrue(ProfileValidator.validateWebsite(null).isValid)
        assertTrue(ProfileValidator.validateWebsite("").isValid)
        assertTrue(ProfileValidator.validateWebsite("https://animeta.app").isValid)

        // Insecure HTTP is rejected
        assertFalse(ProfileValidator.validateWebsite("http://animeta.app").isValid)
        // Missing protocol is rejected
        assertFalse(ProfileValidator.validateWebsite("animeta.app").isValid)
        // Javascript pseudo-protocol is rejected
        assertFalse(ProfileValidator.validateWebsite("javascript:void(0)").isValid)
    }

    @Test
    fun accentColorValidation_enforcesApprovedPalette() {
        assertTrue(ProfileValidator.validateAccentColor(null).isValid)
        assertTrue(ProfileValidator.validateAccentColor("#00D1FF").isValid)
        assertTrue(ProfileValidator.validateAccentColor("#818CF8").isValid)

        // Unapproved colors (e.g. unreadable bright yellow, pure red, arbitrary hex)
        assertFalse(ProfileValidator.validateAccentColor("#FF0000").isValid)
        assertFalse(ProfileValidator.validateAccentColor("#123456").isValid)
        assertFalse(ProfileValidator.validateAccentColor("invalid").isValid)
    }

    @Test
    fun legacySchemaParsing_defaultsEnsureFullCompatibility() {
        // Simulates an older user document created before schema v2 (no customization or privacy fields)
        val legacyUser = AnimiteUser(
            uid = "user_123",
            username = "legacy_user",
            displayName = "Legacy",
        )

        assertEquals(2, legacyUser.schemaVersion)
        assertNotNull(legacyUser.customization)
        assertNotNull(legacyUser.privacy)

        assertEquals("#00D1FF", legacyUser.customization.accentColor)
        assertEquals(ThemePreset.CYAN_GLOW.id, legacyUser.customization.themePreset)
        assertEquals(LayoutPreset.STANDARD.id, legacyUser.customization.layoutPreset)

        assertEquals(ProfileVisibility.PUBLIC.name, legacyUser.privacy.profileVisibility)
        assertEquals(FieldVisibility.PUBLIC.name, legacyUser.privacy.bioVisibility)
        assertEquals(FieldVisibility.PRIVATE.name, legacyUser.privacy.emailVisibility)
        assertEquals(FieldVisibility.PRIVATE.name, legacyUser.privacy.phoneVisibility)

        // AvatarUrl compatibility alias
        assertEquals(legacyUser.photoUrl, legacyUser.avatarUrl)
    }
}
