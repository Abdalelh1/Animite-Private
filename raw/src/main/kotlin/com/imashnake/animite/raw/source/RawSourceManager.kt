package com.imashnake.animite.raw.source

import com.imashnake.animite.raw.model.RawAnimeRelease
import com.imashnake.animite.raw.model.RawSourceDiagnostic
import com.imashnake.animite.raw.model.RawSourceId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RawSourceManager @Inject constructor() {

    private val adapters: Map<RawSourceId, RawSourceAdapter> = listOf(
        SubsPleaseAdapter(),
        AniRenaAdapter(),
        NyaaAdapter(),
    ).associateBy { it.sourceId }

    fun getAdapter(sourceId: RawSourceId): RawSourceAdapter? = adapters[sourceId]

    fun getAllAdapters(): List<RawSourceAdapter> = adapters.values.toList()

    suspend fun getLatestReleases(
        sourceId: RawSourceId? = null,
        timeoutMs: Long = 15000L,
    ): List<RawAnimeRelease> = coroutineScope {
        if (sourceId != null) {
            val adapter = adapters[sourceId] ?: return@coroutineScope emptyList()
            return@coroutineScope withTimeoutOrNull(timeoutMs) {
                adapter.getLatestReleases()
            } ?: emptyList()
        }

        val deferredList = adapters.values.map { adapter ->
            async {
                try {
                    withTimeoutOrNull(timeoutMs) {
                        adapter.getLatestReleases()
                    } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        val combined = deferredList.awaitAll().flatten()
        deduplicateReleases(combined)
    }

    suspend fun searchReleases(
        query: String,
        sourceId: RawSourceId? = null,
        timeoutMs: Long = 15000L,
    ): List<RawAnimeRelease> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()

        if (sourceId != null) {
            val adapter = adapters[sourceId] ?: return@coroutineScope emptyList()
            return@coroutineScope withTimeoutOrNull(timeoutMs) {
                adapter.searchReleases(query)
            } ?: emptyList()
        }

        val deferredList = adapters.values.map { adapter ->
            async {
                try {
                    withTimeoutOrNull(timeoutMs) {
                        adapter.searchReleases(query)
                    } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        val combined = deferredList.awaitAll().flatten()
        deduplicateReleases(combined)
    }

    suspend fun fetchAllDiagnostics(): List<RawSourceDiagnostic> = coroutineScope {
        adapters.values.map { adapter ->
            async {
                try {
                    adapter.getDiagnostics()
                } catch (e: Exception) {
                    RawSourceDiagnostic(
                        sourceId = adapter.sourceId,
                        isReachable = false,
                        statusMessage = "Diagnostics error: ${e.message ?: "Unknown"}"
                    )
                }
            }
        }.awaitAll()
    }

    internal fun deduplicateReleases(releases: List<RawAnimeRelease>): List<RawAnimeRelease> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<RawAnimeRelease>()

        for (rel in releases) {
            // Deduplicate key: title + episode + sourceId (or global match for identical magnet hash)
            val magnetHash = rel.primaryMagnet?.substringBefore("&")?.substringAfter("urn:btih:")?.lowercase()
            val dedupeKey = if (!magnetHash.isNullOrEmpty()) {
                "hash_$magnetHash"
            } else {
                "${rel.sourceId.name}_${rel.animeTitle.lowercase()}_${rel.episodeNumber.orEmpty()}_${rel.rawTitle.lowercase()}"
            }

            if (seen.add(dedupeKey)) {
                result.add(rel)
            }
        }
        return result
    }
}
