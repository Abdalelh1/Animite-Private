package com.imashnake.animite.media.source

import kotlinx.serialization.Serializable

/**
 * Custom Source Type categorization to distinguish configurable templates,
 * built-in dedicated adapters, and unsupported targets.
 */
enum class CustomSourceType {
    TEMPLATE,
    ADAPTER,
    UNSUPPORTED,
}

enum class ExtractionMethod {
    CSS_SELECTOR,
    REGEX,
    JSON_PATH,
    DIRECT_HTML,
}

@Serializable
data class StageExtractionRule(
    val method: ExtractionMethod = ExtractionMethod.CSS_SELECTOR,
    val selectorOrPattern: String = "",
    val attribute: String? = null,
    val regexGroup: Int = 1,
)

/**
 * Safe, declarative template configuration for extracting anime, episodes, and servers.
 * Purely uses CSS selectors and regular expressions — NO arbitrary script execution.
 */
@Serializable
data class CustomSourceTemplateConfig(
    val searchUrlPattern: String = "{baseUrl}/search?q={query}",
    val searchItemSelector: String = "div.anime-card, .anime-item, a.anime-link",
    val searchTitleSelector: String = "h2, h3, .title",
    val searchLinkSelector: String = "a",
    val searchPosterSelector: String = "img",

    val episodeListSelector: String = "a[href*='/episode/'], a[href*='/watch/'], li.episode-item a",
    val episodeNumberRegex: String = """(?:episode|ep|حلقة|الحلقة|-)\s*(\d+(?:\.\d+)?)""",
    val episodeTitleSelector: String = ".episode-title, span.title",

    val serverItemSelector: String = "ul#servers li a, .server-item, iframe",
    val serverUrlAttribute: String = "data-url",
    val serverNameSelector: String = "",
)

@Serializable
data class CustomSourceDefinition(
    val id: String,
    val name: String,
    val baseUrl: String,
    val sourceType: CustomSourceType = CustomSourceType.TEMPLATE,
    val templateConfig: CustomSourceTemplateConfig? = null,
    val adapterId: String? = null,
    val isEnabled: Boolean = true,
    val priority: Int = 50,
    val customHeaders: Map<String, String> = emptyMap(),
    val notes: String? = null,
    val createdTimestampMs: Long = System.currentTimeMillis(),
)

@Serializable
data class SourceStageTestResult(
    val stageName: String,
    val isSuccess: Boolean,
    val message: String,
    val details: String? = null,
    val latencyMs: Long = 0,
)

@Serializable
data class FullSourceTestResult(
    val sourceId: String,
    val sourceName: String,
    val overallSuccess: Boolean,
    val connectivityStage: SourceStageTestResult,
    val searchStage: SourceStageTestResult,
    val episodeStage: SourceStageTestResult,
    val serverStage: SourceStageTestResult,
    val streamStage: SourceStageTestResult,
    val testTimestampMs: Long = System.currentTimeMillis(),
)
