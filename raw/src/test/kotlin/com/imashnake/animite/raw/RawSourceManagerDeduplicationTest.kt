package com.imashnake.animite.raw

import com.imashnake.animite.raw.model.RawAnimeRelease
import com.imashnake.animite.raw.model.RawDownloadOption
import com.imashnake.animite.raw.model.RawPlaybackAvailability
import com.imashnake.animite.raw.model.RawQuality
import com.imashnake.animite.raw.model.RawSourceId
import com.imashnake.animite.raw.source.RawSourceManager
import org.junit.Assert.assertEquals
import org.junit.Test

class RawSourceManagerDeduplicationTest {

    private val sourceManager = RawSourceManager()

    @Test
    fun testDeduplicationByIdenticalMagnetHash() {
        val rel1 = RawAnimeRelease(
            id = "rel_1",
            sourceId = RawSourceId.SUBSPLEASE,
            rawTitle = "[SubsPlease] One Piece - 1155 (1080p).mkv",
            animeTitle = "One Piece",
            episodeNumber = "1155",
            downloadOptions = listOf(
                RawDownloadOption(
                    quality = RawQuality.Q_1080P,
                    resolutionLabel = "1080p",
                    magnetUrl = "magnet:?xt=urn:btih:JPW5KY4KTEEZQHKOPKXOGFRMOCPWO7IK&dn=OnePiece.mkv"
                )
            ),
            availability = RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY
        )

        val rel2 = RawAnimeRelease(
            id = "rel_2",
            sourceId = RawSourceId.NYAA,
            rawTitle = "[SubsPlease] One Piece - 1155 (1080p) [CRC].mkv",
            animeTitle = "One Piece",
            episodeNumber = "1155",
            downloadOptions = listOf(
                RawDownloadOption(
                    quality = RawQuality.Q_1080P,
                    resolutionLabel = "1080p",
                    magnetUrl = "magnet:?xt=urn:btih:JPW5KY4KTEEZQHKOPKXOGFRMOCPWO7IK&tr=udp://tracker.nyaa.si"
                )
            ),
            availability = RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY
        )

        val deduplicated = sourceManager.deduplicateReleases(listOf(rel1, rel2))
        assertEquals(1, deduplicated.size)
        assertEquals("rel_1", deduplicated.first().id)
    }

    @Test
    fun testKeepDistinctEpisodes() {
        val rel1 = RawAnimeRelease(
            id = "rel_1",
            sourceId = RawSourceId.SUBSPLEASE,
            rawTitle = "[SubsPlease] One Piece - 1154 (1080p).mkv",
            animeTitle = "One Piece",
            episodeNumber = "1154",
            downloadOptions = listOf(RawDownloadOption(quality = RawQuality.Q_1080P, resolutionLabel = "1080p", magnetUrl = "magnet:?xt=urn:btih:HASH1")),
            availability = RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY
        )

        val rel2 = RawAnimeRelease(
            id = "rel_2",
            sourceId = RawSourceId.SUBSPLEASE,
            rawTitle = "[SubsPlease] One Piece - 1155 (1080p).mkv",
            animeTitle = "One Piece",
            episodeNumber = "1155",
            downloadOptions = listOf(RawDownloadOption(quality = RawQuality.Q_1080P, resolutionLabel = "1080p", magnetUrl = "magnet:?xt=urn:btih:HASH2")),
            availability = RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY
        )

        val deduplicated = sourceManager.deduplicateReleases(listOf(rel1, rel2))
        assertEquals(2, deduplicated.size)
    }
}
