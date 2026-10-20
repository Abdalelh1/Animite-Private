package com.imashnake.animite.raw

import com.imashnake.animite.raw.model.RawPlaybackAvailability
import com.imashnake.animite.raw.model.RawQuality
import com.imashnake.animite.raw.source.NyaaAdapter
import com.imashnake.animite.raw.source.SubsPleaseAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubsPleaseAndNyaaParsingTest {

    @Test
    fun testSubsPleaseRealJsonResponseParsing() {
        val jsonSample = """
        {
            "Ao Ashi S2 - 03": {
                "time": "Oct 18th",
                "release_date": "Sun, 18 Oct 2026 09:30:00 +0000",
                "show": "Ao Ashi S2",
                "episode": "03",
                "downloads": [
                    {
                        "res": "480",
                        "magnet": "magnet:?xt=urn:btih:RPYHV34YYFGYNPE5E4MQGVX7C5FN3XEX&dn=AoAshi480.mkv&xl=398221925"
                    },
                    {
                        "res": "720",
                        "magnet": "magnet:?xt=urn:btih:T5UQTQ6XXT7PIVPNH3F7IK5HXT7OUYHD&dn=AoAshi720.mkv&xl=764371767"
                    },
                    {
                        "res": "1080",
                        "magnet": "magnet:?xt=urn:btih:EZOBXAEKAD2EMEU5ET3CTC27VQBWLQQA&dn=AoAshi1080.mkv&xl=1498138198"
                    }
                ],
                "xdcc": "%22%5BSubsPlease%5D+Ao+Ashi+S2+-+03%22",
                "image_url": "/wp-content/uploads/2026/10/158726.jpg",
                "page": "ao-ashi-s2"
            }
        }
        """.trimIndent()

        val adapter = SubsPleaseAdapter()
        val releases = adapter.parseSubsPleaseJson(jsonSample)

        assertEquals(1, releases.size)
        val rel = releases.first()
        assertEquals("Ao Ashi S2", rel.animeTitle)
        assertEquals("03", rel.episodeNumber)
        assertEquals("Ao Ashi S2 - 03", rel.rawTitle)
        assertEquals(3, rel.downloadOptions.size)
        assertEquals(RawQuality.Q_1080P, rel.bestQuality)
        assertNotNull(rel.primaryMagnet)
        // Critical requirement: Never treating torrent or magnet as direct video URL
        assertNull(rel.directPlayableUrl)
        assertEquals(RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY, rel.availability)
    }

    @Test
    fun testNyaaRssXmlParsing() {
        val rssXmlSample = """
        <?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0" xmlns:nyaa="https://nyaa.si/xmlns/nyaa">
            <channel>
                <title>Nyaa - Dandadan</title>
                <item>
                    <title>[SubsPlease] DanDaDan - 03 (1080p) [0E61C6CD].mkv</title>
                    <link>magnet:?xt=urn:btih:37ZBCNKEK3WHDITXB3CAORYK3M3SQAYQ</link>
                    <guid isPermaLink="true">https://nyaa.land/view/1885432</guid>
                    <pubDate>Thu, 17 Oct 2026 16:30:00 +0000</pubDate>
                    <nyaa:seeders>245</nyaa:seeders>
                    <nyaa:leechers>12</nyaa:leechers>
                    <nyaa:size>1.4 GiB</nyaa:size>
                    <enclosure url="https://nyaa.land/download/1885432.torrent" length="0" type="application/x-bittorrent" />
                </item>
            </channel>
        </rss>
        """.trimIndent()

        val adapter = NyaaAdapter()
        val releases = adapter.parseNyaaRss(rssXmlSample)

        assertEquals(1, releases.size)
        val rel = releases.first()
        assertEquals("DanDaDan", rel.animeTitle)
        assertEquals("03", rel.episodeNumber)
        assertEquals(245, rel.seeders)
        assertEquals(12, rel.leechers)
        assertEquals(RawQuality.Q_1080P, rel.bestQuality)
        assertEquals(RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY, rel.availability)
        assertNull(rel.directPlayableUrl)
    }
}
