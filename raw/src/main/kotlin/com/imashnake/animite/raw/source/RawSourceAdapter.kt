package com.imashnake.animite.raw.source

import com.imashnake.animite.raw.model.RawAnimeRelease
import com.imashnake.animite.raw.model.RawSourceDiagnostic
import com.imashnake.animite.raw.model.RawSourceId

interface RawSourceAdapter {
    val sourceId: RawSourceId
    val isSupported: Boolean

    suspend fun getLatestReleases(limit: Int = 30): List<RawAnimeRelease>
    suspend fun searchReleases(query: String, limit: Int = 30): List<RawAnimeRelease>
    suspend fun getDiagnostics(): RawSourceDiagnostic
}
