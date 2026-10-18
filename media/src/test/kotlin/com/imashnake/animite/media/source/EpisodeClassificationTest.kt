package com.imashnake.animite.media.source

import com.imashnake.animite.media.source.classification.EpisodeClassificationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeClassificationTest {

    @Test
    fun `One Piece canon episodes classified correctly`() {
        val ep1 = EpisodeClassificationRepository.classify(
            animeTitles = listOf("One Piece"),
            episodeNumber = 1.0f,
            rawTitle = "I'm Luffy! The Man Who's Gonna Be King of the Pirates!"
        )
        assertEquals(EpisodeType.CANON, ep1)
        assertTrue(ep1.isCanon)
        assertFalse(ep1.isFiller)

        val ep300 = EpisodeClassificationRepository.classify(
            animeTitles = listOf("One Piece"),
            episodeNumber = 300.0f
        )
        assertEquals(EpisodeType.CANON, ep300)
    }

    @Test
    fun `One Piece filler episodes classified correctly`() {
        // Warship Island Arc (54-61)
        val ep55 = EpisodeClassificationRepository.classify(
            animeTitles = listOf("One Piece"),
            episodeNumber = 55.0f,
            rawTitle = "Warship Island"
        )
        assertEquals(EpisodeType.FILLER, ep55)
        assertTrue(ep55.isFiller)
        assertFalse(ep55.isCanon)

        // G-8 Arc (196-206)
        val ep196 = EpisodeClassificationRepository.classify(
            animeTitles = listOf("One Piece"),
            episodeNumber = 196.0f,
            rawTitle = "Navarone G-8"
        )
        assertEquals(EpisodeType.FILLER, ep196)

        // Foxy Returns (220-226)
        val ep225 = EpisodeClassificationRepository.classify(
            animeTitles = listOf("One Piece"),
            episodeNumber = 225.0f
        )
        assertEquals(EpisodeType.FILLER, ep225)
    }

    @Test
    fun `One Piece mixed canon filler episodes classified correctly`() {
        // Episode 101: Alabasta mixed
        val ep101 = EpisodeClassificationRepository.classify(
            animeTitles = listOf("One Piece"),
            episodeNumber = 101.0f
        )
        assertEquals(EpisodeType.MIXED, ep101)
        assertTrue(ep101.isMixed)
        assertFalse(ep101.isFiller)
    }

    @Test
    fun `One Piece recap episodes classified correctly`() {
        val ep1035_5 = EpisodeClassificationRepository.classify(
            animeTitles = listOf("One Piece"),
            episodeNumber = 1035.5f,
            rawTitle = "A Comprehensive Anatomy! The Legend of Kozuki Oden!"
        )
        assertEquals(EpisodeType.RECAP, ep1035_5)
        assertTrue(ep1035_5.isRecap)
    }

    @Test
    fun `One Piece special episodes classified correctly`() {
        // Toriko x One Piece crossover (492)
        val ep492 = EpisodeClassificationRepository.classify(
            animeTitles = listOf("One Piece"),
            episodeNumber = 492.0f,
            rawTitle = "Toriko x One Piece Crossover"
        )
        assertEquals(EpisodeType.SPECIAL, ep492)
        assertTrue(ep492.isSpecial)
    }

    @Test
    fun `General keyword classification handles various types`() {
        val ova = EpisodeClassificationRepository.classify(
            animeTitles = listOf("Attack on Titan"),
            episodeNumber = 1.0f,
            rawTitle = "No Regrets Part 1 OVA"
        )
        assertEquals(EpisodeType.OVA, ova)
        assertTrue(ova.isSpecial)

        val movie = EpisodeClassificationRepository.classify(
            animeTitles = listOf("Demon Slayer"),
            episodeNumber = 1.0f,
            rawTitle = "Mugen Train The Movie"
        )
        assertEquals(EpisodeType.MOVIE, movie)
        assertTrue(movie.isMovie)

        val recap = EpisodeClassificationRepository.classify(
            animeTitles = listOf("Jujutsu Kaisen"),
            episodeNumber = 5.5f,
            rawTitle = "Season 2 Recap Special"
        )
        assertEquals(EpisodeType.RECAP, recap)

        val special = EpisodeClassificationRepository.classify(
            animeTitles = listOf("Naruto Shippuden"),
            episodeNumber = 1.0f,
            rawTitle = "Bonus Special Episode"
        )
        assertEquals(EpisodeType.SPECIAL, special)
    }

    @Test
    fun `Deduplication preserves both Canon episode 1 and Special episode 1`() {
        val canonEp = AnimeEpisode(
            number = 1.0f,
            title = "Episode 1",
            url = "https://example.com/ep1",
            episodeType = EpisodeType.CANON
        )
        val specialEp = AnimeEpisode(
            number = 1.0f,
            title = "Special 1: In the Territory of Adventure",
            url = "https://example.com/special1",
            episodeType = EpisodeType.SPECIAL
        )

        val provider = EpisodeSourcesManager.providers.first()
        val match = AnimeSearchResult("test-id", "One Piece", "https://example.com/op")
        val batch = ProviderEpisodeBatch(
            provider = provider,
            match = match,
            episodes = listOf(canonEp, specialEp),
            matchScore = 100
        )

        val deduplicated = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(
            batches = listOf(batch),
            titles = listOf("One Piece")
        )

        assertEquals(2, deduplicated.size)
        assertTrue(deduplicated.any { it.episodeType == EpisodeType.CANON })
        assertTrue(deduplicated.any { it.episodeType == EpisodeType.SPECIAL })
    }
}
