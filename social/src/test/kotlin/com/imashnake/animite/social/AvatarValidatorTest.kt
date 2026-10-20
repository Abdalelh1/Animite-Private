package com.imashnake.animite.social

import com.imashnake.animite.social.util.AvatarValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarValidatorTest {

    @Test
    fun maxFileSize_is5MB() {
        assertEquals(5 * 1024 * 1024L, AvatarValidator.MAX_FILE_SIZE_BYTES)
    }

    @Test
    fun allowedMimeTypes_containsSupportedImageFormats() {
        assertTrue(AvatarValidator.ALLOWED_MIME_TYPES.contains("image/jpeg"))
        assertTrue(AvatarValidator.ALLOWED_MIME_TYPES.contains("image/png"))
        assertTrue(AvatarValidator.ALLOWED_MIME_TYPES.contains("image/webp"))
        assertTrue(AvatarValidator.ALLOWED_MIME_TYPES.contains("image/gif"))
    }
}
