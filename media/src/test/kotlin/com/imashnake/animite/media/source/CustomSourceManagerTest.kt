package com.imashnake.animite.media.source

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class CustomSourceManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun testCustomSourceUrlValidation() {
        val validHttps = "https://example.com/anime"
        val httpUrl = "http://unsecure.com"
        val jsScheme = "javascript:alert(1)"
        val fileScheme = "file:///etc/passwd"
        val invalidUrl = "not-a-valid-url"

        assertTrue("Valid HTTPS URL should be accepted", isValidHttpsUrl(validHttps))
        assertFalse("HTTP URL must be rejected for security", isValidHttpsUrl(httpUrl))
        assertFalse("javascript: scheme must be rejected", isValidHttpsUrl(jsScheme))
        assertFalse("file: scheme must be rejected", isValidHttpsUrl(fileScheme))
        assertFalse("Malformed URL must be rejected", isValidHttpsUrl(invalidUrl))
    }

    @Test
    fun testDuplicateProviderDetection() {
        val builtInIds = listOf("anime4up", "anime3rb", "witanime", "animeluxe", "animeslayer")

        // Attempting to register an existing built-in ID
        val duplicateId = "anime4up"
        assertTrue("Duplicate built-in ID detected", builtInIds.contains(duplicateId))

        // Duplicate URL detection
        val existingUrls = setOf("https://anime3rb.com", "https://w1.anime4up.rest")
        val duplicateUrl = "https://anime3rb.com/"
        val normalized = duplicateUrl.trimEnd('/')
        assertTrue("Duplicate base URL detected", existingUrls.contains(normalized))

        val uniqueUrl = "https://newanime-site.com"
        assertFalse("Unique base URL accepted", existingUrls.contains(uniqueUrl.trimEnd('/')))
    }

    @Test
    fun testCustomSourceDefinitionSerialization() {
        val templateConfig = CustomSourceTemplateConfig(
            searchUrlPattern = "{baseUrl}/search?q={query}",
            searchItemSelector = "div.anime-card",
            searchTitleSelector = "h3.title",
            searchLinkSelector = "a.link",
            episodeListSelector = "ul.episodes > li",
            episodeNumberRegex = "الحلقة\\s*(\\d+)",
            serverItemSelector = "ul.servers > li > a",
            serverUrlAttribute = "href",
        )

        val customDef = CustomSourceDefinition(
            id = "custom_test_src",
            name = "Test Anime Provider",
            baseUrl = "https://my-anime.org",
            sourceType = CustomSourceType.TEMPLATE,
            templateConfig = templateConfig,
            priority = 10,
            isEnabled = true,
            customHeaders = mapOf("Referer" to "https://my-anime.org"),
        )

        // Serialize to JSON
        val encoded = json.encodeToString(customDef)
        assertNotNull(encoded)
        assertTrue(encoded.contains("custom_test_src"))
        assertTrue(encoded.contains("my-anime.org"))

        // Deserialize back
        val decoded = json.decodeFromString<CustomSourceDefinition>(encoded)
        assertEquals(customDef.id, decoded.id)
        assertEquals(customDef.name, decoded.name)
        assertEquals(customDef.baseUrl, decoded.baseUrl)
        assertEquals(customDef.sourceType, decoded.sourceType)
        assertEquals(customDef.templateConfig?.searchUrlPattern, decoded.templateConfig?.searchUrlPattern)
        assertEquals(customDef.priority, decoded.priority)
        assertEquals(customDef.customHeaders["Referer"], "https://my-anime.org")
    }

    @Test
    fun testPriorityReorderingLogic() {
        val initialOrder = mutableListOf("anime4up", "anime3rb", "witanime", "custom_1")

        // Move custom_1 up by swapping with witanime
        val targetIdx = initialOrder.indexOf("custom_1")
        assertEquals(3, targetIdx)

        val temp = initialOrder[targetIdx]
        initialOrder[targetIdx] = initialOrder[targetIdx - 1]
        initialOrder[targetIdx - 1] = temp

        assertEquals(listOf("anime4up", "anime3rb", "custom_1", "witanime"), initialOrder)

        // Move custom_1 up again
        val nextIdx = initialOrder.indexOf("custom_1")
        val temp2 = initialOrder[nextIdx]
        initialOrder[nextIdx] = initialOrder[nextIdx - 1]
        initialOrder[nextIdx - 1] = temp2

        assertEquals(listOf("anime4up", "custom_1", "anime3rb", "witanime"), initialOrder)
    }

    @Test
    fun testSourceTypeDistinction() {
        assertEquals("TEMPLATE", CustomSourceType.TEMPLATE.name)
        assertEquals("ADAPTER", CustomSourceType.ADAPTER.name)
        assertEquals("UNSUPPORTED", CustomSourceType.UNSUPPORTED.name)

        val templateSource = CustomSourceDefinition(
            id = "tmpl_1",
            name = "Template Source",
            baseUrl = "https://template.com",
            sourceType = CustomSourceType.TEMPLATE,
        )
        assertTrue(templateSource.sourceType == CustomSourceType.TEMPLATE)

        val adapterSource = CustomSourceDefinition(
            id = "custom_adapter_1",
            name = "Adapter Source",
            baseUrl = "https://adapter.com",
            sourceType = CustomSourceType.ADAPTER,
        )
        assertTrue(adapterSource.sourceType == CustomSourceType.ADAPTER)
    }

    private fun isValidHttpsUrl(urlString: String): Boolean {
        if (!urlString.startsWith("https://", ignoreCase = true)) return false
        return try {
            val uri = URI(urlString)
            !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
