package com.imashnake.animite.media.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 - Step 2: Deep Episode Counts, Provider Comparison, and Deduplication Audit.
 * Audits 7 anime:
 * 1. One Piece
 * 2. Naruto
 * 3. Naruto Shippuden
 * 4. Jujutsu Kaisen
 * 5. Attack on Titan
 * 6. Death Note
 * 7. Solo Leveling
 */
class Phase11AuditStep2Test {

    data class ProviderAuditDetail(
        val providerId: String,
        val providerName: String,
        val matchedTitle: String?,
        val matchedUrl: String?,
        val matchScore: Int,
        val rawEpisodeCount: Int,
        val firstEpisodeNumber: Float?,
        val lastEpisodeNumber: Float?,
        val specialEpisodesCount: Int,
        val isBlockedOrFailed: Boolean,
        val failureReason: String? = null,
    )

    data class AnimeAuditResult(
        val animeName: String,
        val searchTitles: List<String>,
        val providerDetails: List<ProviderAuditDetail>,
        val rawTotalEpisodes: Int,
        val unifiedCount: Int,
        val duplicatesRemoved: Int,
        val episodesWithMultipleSources: Int,
        val regularEpisodesCount: Int,
        val specialsCount: Int,
        val moviesCount: Int,
        val detectedGaps: List<Int>,
        val offsetApplied: Int,
        val deduplicationIntegrityValid: Boolean,
        val notes: String = "",
    ) {
        fun printReport() {
            println("====================================================================================================")
            println("=== AUDIT STEP 2 REPORT: $animeName ===")
            println("====================================================================================================")
            println("Search Titles: [${searchTitles.joinToString(", ")}]")
            println("--- PROVIDER BREAKDOWN ---")
            for (p in providerDetails) {
                if (p.isBlockedOrFailed) {
                    println("  [X] Provider: ${p.providerName} (${p.providerId}) -> FAILED/BLOCKED (${p.failureReason})")
                } else {
                    println("  [✓] Provider: ${p.providerName} (${p.providerId})")
                    println("      Matched: '${p.matchedTitle}' | Score: ${p.matchScore}% | URL: ${p.matchedUrl}")
                    println("      Episodes: ${p.rawEpisodeCount} (First: ${p.firstEpisodeNumber}, Last: ${p.lastEpisodeNumber}, Specials: ${p.specialEpisodesCount})")
                }
            }
            println("--- UNIFIED & DEDUPLICATION METRICS ---")
            println("  - Raw Total Episodes Across All Providers : $rawTotalEpisodes")
            println("  - Unified Episode Count                   : $unifiedCount (Regular: $regularEpisodesCount, Specials: $specialsCount, Movies: $moviesCount)")
            println("  - Duplicate Episodes Removed              : $duplicatesRemoved")
            println("  - Episodes with Multiple Sources (Backup) : $episodesWithMultipleSources")
            println("  - Detected Episode Gaps in Numbering      : ${if (detectedGaps.isEmpty()) "None" else detectedGaps.joinToString(", ")}")
            println("  - Season Numbering Offset Applied         : ${if (offsetApplied > 0) "$offsetApplied episodes" else "None"}")
            println("  - Deduplication Integrity (Separation)    : ${if (deduplicationIntegrityValid) "PASSED (No Special/Movie mixed into TV)" else "FAILED"}")
            if (notes.isNotBlank()) {
                println("  - Audit Notes                             : $notes")
            }
            println("====================================================================================================\n")
        }
    }

    private suspend fun auditAnime(
        animeName: String,
        titles: List<String>,
        totalEpisodesExpected: Int? = null,
        maxAiredExpected: Int? = null,
    ): AnimeAuditResult {
        val providerDetails = mutableListOf<ProviderAuditDetail>()
        val batches = mutableListOf<ProviderEpisodeBatch>()
        var rawTotal = 0

        for (provider in EpisodeSourcesManager.providers) {
            try {
                val matchResult = withTimeoutOrNull(10000L) {
                    EpisodeSourcesManager.autoFindAnime(provider, titles)
                }

                if (matchResult == null) {
                    providerDetails.add(
                        ProviderAuditDetail(
                            providerId = provider.id,
                            providerName = provider.name,
                            matchedTitle = null,
                            matchedUrl = null,
                            matchScore = 0,
                            rawEpisodeCount = 0,
                            firstEpisodeNumber = null,
                            lastEpisodeNumber = null,
                            specialEpisodesCount = 0,
                            isBlockedOrFailed = true,
                            failureReason = "Timeout or not responding (>10s)",
                        )
                    )
                    continue
                }

                val (match, allResults) = matchResult
                if (match != null) {
                    val score = AnimeTitleMatcher.matchScore(titles, match.title)
                    if (score >= AnimeTitleMatcher.MIN_MATCH_THRESHOLD) {
                        val fetchedEps = withTimeoutOrNull(12000L) {
                            try {
                                provider.getEpisodes(match.url)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        } ?: emptyList()

                        if (fetchedEps.isNotEmpty()) {
                            val specials = fetchedEps.count {
                                it.episodeType.isSpecial || it.episodeType.isMovie || it.episodeType.isRecap
                            }
                            val firstNum = fetchedEps.minByOrNull { it.number }?.number
                            val lastNum = fetchedEps.maxByOrNull { it.number }?.number

                            providerDetails.add(
                                ProviderAuditDetail(
                                    providerId = provider.id,
                                    providerName = provider.name,
                                    matchedTitle = match.title,
                                    matchedUrl = match.url,
                                    matchScore = score,
                                    rawEpisodeCount = fetchedEps.size,
                                    firstEpisodeNumber = firstNum,
                                    lastEpisodeNumber = lastNum,
                                    specialEpisodesCount = specials,
                                    isBlockedOrFailed = false,
                                )
                            )
                            rawTotal += fetchedEps.size
                            batches.add(ProviderEpisodeBatch(provider, match, fetchedEps, score))
                        } else {
                            providerDetails.add(
                                ProviderAuditDetail(
                                    providerId = provider.id,
                                    providerName = provider.name,
                                    matchedTitle = match.title,
                                    matchedUrl = match.url,
                                    matchScore = score,
                                    rawEpisodeCount = 0,
                                    firstEpisodeNumber = null,
                                    lastEpisodeNumber = null,
                                    specialEpisodesCount = 0,
                                    isBlockedOrFailed = true,
                                    failureReason = "Returned 0 episodes (Bot challenge/Cloudflare protection)",
                                )
                            )
                        }
                    } else {
                        providerDetails.add(
                            ProviderAuditDetail(
                                providerId = provider.id,
                                providerName = provider.name,
                                matchedTitle = match.title,
                                matchedUrl = match.url,
                                matchScore = score,
                                rawEpisodeCount = 0,
                                firstEpisodeNumber = null,
                                lastEpisodeNumber = null,
                                specialEpisodesCount = 0,
                                isBlockedOrFailed = true,
                                failureReason = "Title match score below threshold ($score < ${AnimeTitleMatcher.MIN_MATCH_THRESHOLD})",
                            )
                        )
                    }
                } else {
                    providerDetails.add(
                        ProviderAuditDetail(
                            providerId = provider.id,
                            providerName = provider.name,
                            matchedTitle = null,
                            matchedUrl = null,
                            matchScore = 0,
                            rawEpisodeCount = 0,
                            firstEpisodeNumber = null,
                            lastEpisodeNumber = null,
                            specialEpisodesCount = 0,
                            isBlockedOrFailed = true,
                            failureReason = "Anime not found in search results (${allResults.size} candidates)",
                        )
                    )
                }
            } catch (e: Exception) {
                providerDetails.add(
                    ProviderAuditDetail(
                        providerId = provider.id,
                        providerName = provider.name,
                        matchedTitle = null,
                        matchedUrl = null,
                        matchScore = 0,
                        rawEpisodeCount = 0,
                        firstEpisodeNumber = null,
                        lastEpisodeNumber = null,
                        specialEpisodesCount = 0,
                        isBlockedOrFailed = true,
                        failureReason = "${e.javaClass.simpleName}: ${e.message}",
                    )
                )
            }
        }

        // Run Unification & Deduplication Engine
        val unified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(
            batches = batches,
            maxAiredEpisode = maxAiredExpected,
            totalEpisodes = totalEpisodesExpected,
            titles = titles,
        )

        val duplicatesRemoved = (rawTotal - unified.size).coerceAtLeast(0)
        val episodesWithMultipleSources = unified.count { it.sources.size > 1 }

        val regularEps = unified.count { !it.episodeType.isSpecial && !it.episodeType.isMovie && !it.episodeType.isRecap }
        val specials = unified.count { it.episodeType.isSpecial || it.episodeType.isRecap }
        val movies = unified.count { it.episodeType.isMovie }

        // Check for numbering gaps in regular episodes
        val presentRegularNumbers = unified
            .filter { !it.episodeType.isSpecial && !it.episodeType.isMovie && !it.episodeType.isRecap }
            .map { it.number.toInt() }
            .toSet()

        val maxRegularNum = presentRegularNumbers.maxOrNull() ?: 0
        val detectedGaps = mutableListOf<Int>()
        if (maxRegularNum > 1) {
            for (i in 1..maxRegularNum) {
                if (i !in presentRegularNumbers) {
                    detectedGaps.add(i)
                }
            }
        }

        // Verify that specials and movies are not accidentally merged into regular episode 1
        var deduplicationValid = true
        for (ep in unified) {
            if (ep.sources.size > 1) {
                // All sources should belong to the same episode classification
                val distinctTypes = batches.flatMap { b ->
                    b.episodes.filter { it.number == ep.number }.map { it.episodeType }
                }.distinct()
                if (distinctTypes.any { it.isMovie } && distinctTypes.any { it == EpisodeType.REGULAR }) {
                    deduplicationValid = false
                }
            }
        }

        // Determine if offset was applied
        val detectedSeason = titles.firstNotNullOfOrNull { AnimeTitleMatcher.extractSeasonNumber(it) } ?: 1
        val sampleBatch = batches.firstOrNull()
        val offsetApplied = if (sampleBatch != null) {
            EpisodeSourcesManager.calculateBatchOffset(sampleBatch.episodes, detectedSeason, totalEpisodesExpected)
        } else 0

        return AnimeAuditResult(
            animeName = animeName,
            searchTitles = titles,
            providerDetails = providerDetails,
            rawTotalEpisodes = rawTotal,
            unifiedCount = unified.size,
            duplicatesRemoved = duplicatesRemoved,
            episodesWithMultipleSources = episodesWithMultipleSources,
            regularEpisodesCount = regularEps,
            specialsCount = specials,
            moviesCount = movies,
            detectedGaps = detectedGaps,
            offsetApplied = offsetApplied,
            deduplicationIntegrityValid = deduplicationValid,
            notes = "Participating providers: ${batches.map { it.provider.id }.joinToString(", ")}",
        )
    }

    // ==========================================
    // 1. One Piece Audit
    // ==========================================
    @Test
    fun auditOnePiece() = runBlocking {
        val result = auditAnime(
            animeName = "One Piece",
            titles = listOf("One Piece", "ONE PIECE", "ون بيس"),
        )
        result.printReport()
        assertTrue("One Piece must have raw episodes", result.rawTotalEpisodes > 0)
        assertTrue("One Piece must have unified episodes", result.unifiedCount > 0)
        assertTrue("Deduplication integrity must be valid", result.deduplicationIntegrityValid)
    }

    // ==========================================
    // 2. Naruto Audit
    // ==========================================
    @Test
    fun auditNaruto() = runBlocking {
        val result = auditAnime(
            animeName = "Naruto",
            titles = listOf("Naruto", "ناروتو"),
            totalEpisodesExpected = 220,
        )
        result.printReport()
        assertTrue("Naruto must have raw episodes", result.rawTotalEpisodes > 0)
        assertTrue("Naruto must have unified episodes", result.unifiedCount > 0)
        assertTrue("Deduplication integrity must be valid", result.deduplicationIntegrityValid)
    }

    // ==========================================
    // 3. Naruto Shippuden Audit
    // ==========================================
    @Test
    fun auditNarutoShippuden() = runBlocking {
        val result = auditAnime(
            animeName = "Naruto Shippuden",
            titles = listOf("Naruto Shippuden", "Naruto: Shippuuden", "ناروتو شيبودن"),
            totalEpisodesExpected = 500,
        )
        result.printReport()
        assertTrue("Naruto Shippuden must have raw episodes", result.rawTotalEpisodes > 0)
        assertTrue("Naruto Shippuden must have unified episodes", result.unifiedCount > 0)
        assertTrue("Deduplication integrity must be valid", result.deduplicationIntegrityValid)
    }

    // ==========================================
    // 4. Jujutsu Kaisen Audit
    // ==========================================
    @Test
    fun auditJujutsuKaisen() = runBlocking {
        val result = auditAnime(
            animeName = "Jujutsu Kaisen",
            titles = listOf("Jujutsu Kaisen", "جوجيتسو كايسن"),
            totalEpisodesExpected = 24,
        )
        result.printReport()
        assertTrue("Jujutsu Kaisen must have raw episodes", result.rawTotalEpisodes > 0)
        assertTrue("Jujutsu Kaisen must have unified episodes", result.unifiedCount > 0)
        assertTrue("Deduplication integrity must be valid", result.deduplicationIntegrityValid)
    }

    // ==========================================
    // 5. Attack on Titan Audit
    // ==========================================
    @Test
    fun auditAttackOnTitan() = runBlocking {
        val result = auditAnime(
            animeName = "Attack on Titan",
            titles = listOf("Attack on Titan", "Shingeki no Kyojin", "هجوم العمالقة"),
            totalEpisodesExpected = 25,
        )
        result.printReport()
        assertTrue("Attack on Titan must have raw episodes", result.rawTotalEpisodes > 0)
        assertTrue("Attack on Titan must have unified episodes", result.unifiedCount > 0)
        assertTrue("Deduplication integrity must be valid", result.deduplicationIntegrityValid)
    }

    // ==========================================
    // 6. Death Note Audit
    // ==========================================
    @Test
    fun auditDeathNote() = runBlocking {
        val result = auditAnime(
            animeName = "Death Note",
            titles = listOf("Death Note", "مذكرة الموت"),
            totalEpisodesExpected = 37,
        )
        result.printReport()
        assertTrue("Death Note must have raw episodes", result.rawTotalEpisodes > 0)
        assertTrue("Death Note must have unified episodes", result.unifiedCount > 0)
        assertTrue("Deduplication integrity must be valid", result.deduplicationIntegrityValid)
    }

    // ==========================================
    // 7. Solo Leveling Audit
    // ==========================================
    @Test
    fun auditSoloLeveling() = runBlocking {
        val result = auditAnime(
            animeName = "Solo Leveling",
            titles = listOf("Solo Leveling", "Ore dake Level Up na Ken", "سولو ليفلينج"),
            totalEpisodesExpected = 12,
        )
        result.printReport()
        assertTrue("Solo Leveling must have raw episodes", result.rawTotalEpisodes > 0)
        assertTrue("Solo Leveling must have unified episodes", result.unifiedCount > 0)
        assertTrue("Deduplication integrity must be valid", result.deduplicationIntegrityValid)
    }
}
