package com.imashnake.animite.media.source

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

enum class ServerDiscoveryStatus {
    SUCCESS,
    FAILED_TIMEOUT,
    FAILED_NETWORK_ERROR,
    FAILED_NOT_DIRECT_VIDEO,
    EXPIRED_LINK,
}

data class ServerDiagnostic(
    val providerId: String,
    val serverName: String,
    val url: String,
    val quality: String,
    val status: ServerDiscoveryStatus,
    val failureReason: String? = null,
)

data class EpisodeDiagnosticReport(
    val episodeNumber: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val queriedSourcesCount: Int,
    val respondingSourcesCount: Int,
    val totalDiscoveredServers: Int,
    val validDirectStreamsCount: Int,
    val discoveredQualities: List<String>,
    val winningStreamUrl: String?,
    val winningQuality: String?,
    val serverDetails: List<ServerDiagnostic> = emptyList(),
    val failureReasons: List<String> = emptyList(),
    val failoverHistory: MutableList<String> = mutableListOf(),
) {
    fun toFormattedLog(): String {
        return buildString {
            appendLine("=== SMARTSTREAM DIAGNOSTIC REPORT (Ep $episodeNumber) ===")
            appendLine("Queried Sources: $queriedSourcesCount, Responding Sources: $respondingSourcesCount")
            appendLine("Discovered Servers: $totalDiscoveredServers, Valid Direct Streams: $validDirectStreamsCount")
            appendLine("Discovered Qualities: [${discoveredQualities.joinToString(", ")}]")
            appendLine("Winning Quality: $winningQuality")
            appendLine("Winning URL: ${winningStreamUrl ?: "None"}")
            if (failureReasons.isNotEmpty()) {
                appendLine("Failures/Warnings: ${failureReasons.joinToString(" | ")}")
            }
            if (failoverHistory.isNotEmpty()) {
                appendLine("Failovers: ${failoverHistory.joinToString(" -> ")}")
            }
            appendLine("=========================================================")
        }
    }
}

data class ProviderDiagnosticInfo(
    val providerId: String,
    val providerName: String,
    val episodesDiscovered: Int,
    val isFailed: Boolean = false,
    val failureReason: String? = null,
)

data class AnimeDiagnosticSummary(
    val animeTitle: String,
    val timestamp: Long = System.currentTimeMillis(),
    val episodesDiscoveredPerProvider: Map<String, Int> = emptyMap(),
    val providerDiagnostics: List<ProviderDiagnosticInfo> = emptyList(),
    val unifiedEpisodeCount: Int,
    val duplicateEpisodesRemoved: Int,
    val episodesWithoutPlayableCandidates: Int,
    val validatedStreamCount: Int,
    val failedProviders: Map<String, String> = emptyMap(),
    val lastSuccessfulCheck: Long = System.currentTimeMillis(),
    val isFromCache: Boolean = false,
    val cacheStatus: String = "LIVE",
)

/**
 * Diagnostic tracking system for SmartStream.
 * Records detailed metrics for each episode resolution, health stats, and failover occurrences.
 */
object SmartStreamDiagnostics {

    private const val TAG = "SMARTSTREAM_DIAG"
    private val reports = ConcurrentHashMap<Float, EpisodeDiagnosticReport>()
    private val animeSummaries = ConcurrentHashMap<String, AnimeDiagnosticSummary>()

    fun recordReport(report: EpisodeDiagnosticReport) {
        reports[report.episodeNumber] = report
        Log.i(TAG, report.toFormattedLog())
    }

    fun addFailoverEvent(episodeNumber: Float, event: String) {
        val report = reports[episodeNumber]
        if (report != null) {
            report.failoverHistory.add(event)
            Log.w(TAG, "Ep $episodeNumber FAILOVER: $event")
        } else {
            Log.w(TAG, "Ep $episodeNumber FAILOVER: $event")
        }
    }

    fun getReport(episodeNumber: Float): EpisodeDiagnosticReport? {
        return reports[episodeNumber]
    }

    fun getAllReports(): List<EpisodeDiagnosticReport> {
        return reports.values.sortedBy { it.episodeNumber }
    }

    fun recordAnimeSummary(summary: AnimeDiagnosticSummary) {
        animeSummaries[summary.animeTitle.lowercase()] = summary
        Log.i(TAG, "Recorded diagnostic summary for '${summary.animeTitle}': unified=${summary.unifiedEpisodeCount}, providers=${summary.episodesDiscoveredPerProvider.keys}")
    }

    fun getAnimeSummary(animeTitle: String): AnimeDiagnosticSummary? {
        return animeSummaries[animeTitle.lowercase()]
            ?: animeSummaries.entries.firstOrNull { it.key.contains(animeTitle.lowercase()) || animeTitle.lowercase().contains(it.key) }?.value
    }

    fun getAllAnimeSummaries(): List<AnimeDiagnosticSummary> {
        return animeSummaries.values.toList()
    }

    fun clear() {
        reports.clear()
        animeSummaries.clear()
    }
}
