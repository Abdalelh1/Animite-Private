package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Test

class OnePieceDiagnosticTest {

    @Test
    fun diagnoseOnePiece() = runBlocking {
        val titles = listOf("One Piece", "ONE PIECE", "ون بيس")
        println("==================================================")
        println("=== DIAGNOSING ONE PIECE SEARCH ACROSS ALL PROVIDERS ===")
        println("==================================================")

        for (provider in EpisodeSourcesManager.providers) {
            println("\n>>> Testing Provider: ${provider.id} (${provider.name}) <<<")
            try {
                val (match, allResults) = EpisodeSourcesManager.autoFindAnime(provider, titles)
                println("  Matched Anime: ${match?.title} -> ${match?.url}")
                println("  Total Search Results: ${allResults.size}")
                for (r in allResults.take(3)) {
                    val score = AnimeTitleMatcher.matchScore(titles, r.title)
                    println("    - [Score: $score] ${r.title} (${r.url})")
                }

                if (match != null) {
                    println("  Fetching episodes from ${match.url}...")
                    val eps = provider.getEpisodes(match.url)
                    println("  Total Episodes Returned: ${eps.size}")
                    if (eps.isNotEmpty()) {
                        println("    First ep: ${eps.first().number} -> ${eps.first().url} (title: ${eps.first().title})")
                        println("    Last ep: ${eps.last().number} -> ${eps.last().url} (title: ${eps.last().title})")

                        val testEp = eps.first()
                        println("  Testing getStreamServers for Ep ${testEp.number} (${testEp.url})...")
                        val servers = provider.getStreamServers(testEp.url)
                        println("  Total Servers Found: ${servers.size}")
                        for (s in servers) {
                            println("    Server: ${s.name} | isDirect: ${s.isDirectVideo} | quality: ${s.quality} | linkType: ${s.linkType} | url: ${s.url}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("  FAILED with exception: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }

        println("\n==================================================")
        println("=== TESTING FULL AGGREGATION FOR ONE PIECE ===")
        println("==================================================")
        try {
            val aggregated = EpisodeSourcesManager.findBestAggregatedEpisodes(titles)
            if (aggregated != null) {
                println("Aggregated Match: ${aggregated.matchedAnime.title} via ${aggregated.provider.name}")
                println("Total Unified Episodes: ${aggregated.episodes.size}")
                val ep1 = aggregated.episodes.firstOrNull()
                if (ep1 != null) {
                    println("Resolving streams for Episode ${ep1.number} (sources: ${ep1.sources.size})...")
                    val playback = EpisodeSourcesManager.resolveAllEpisodeQualities(ep1, preferredProviderId = aggregated.provider.id)
                    println("Playback Success: ${playback.success}")
                    println("Best Stream: ${playback.bestStream?.name} -> ${playback.bestStream?.url}")
                    println("All Direct Servers: ${playback.allDirectServers.size}")
                    for (s in playback.allDirectServers) {
                        println("  Direct: ${s.name} [${s.quality}] -> ${s.url}")
                    }
                    println("Quality Options: ${playback.qualityOptions.size}")
                    for (q in playback.qualityOptions) {
                        println("  Option: ${q.quality.canonicalName} -> primary: ${q.primaryServer.url} (fallbacks: ${q.fallbackServers.size})")
                    }
                    println("Error Message: ${playback.errorMessage}")
                }
            } else {
                println("findBestAggregatedEpisodes returned NULL for One Piece!")
            }
        } catch (e: Exception) {
            println("Aggregation failed: ${e.message}")
            e.printStackTrace()
        }

        println("\n==================================================")
        println("=== TESTING DIRECT STREAM EXTRACTION FOR EMBEDS ===")
        println("==================================================")
        val client = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // 1. Test Share4max Inertia partial request
        try {
            val share4maxUrl = "https://share4max.com/iframe/FbqAiHnJ7eFAa"
            val inertiaReq = okhttp3.Request.Builder()
                .url(share4maxUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Referer", "https://w1.anime4up.rest/")
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "7a7eafc95b15a2110f8f43bd7706b997")
                .header("X-Inertia-Partial-Component", "files/mirror/video")
                .header("X-Inertia-Partial-Data", "streams")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val inertiaResp = client.newCall(inertiaReq).execute()
            val inertiaBody = inertiaResp.body?.string() ?: ""
            println("Share4max Inertia partial status: ${inertiaResp.code}, len: ${inertiaBody.length}")
            if (inertiaBody.length < 1500) {
                println("  Inertia Body: $inertiaBody")
            } else {
                println("  Inertia snippet: ${inertiaBody.take(1000)}...")
            }
        } catch (e: Exception) {
            println("Share4max Inertia error: ${e.message}")
        }

        // 1. Inspect 4f.7grcy6t.shop (Anime4up-S1)
        try {
            val s1Url = "https://4f.7grcy6t.shop/Anime4up-S1/mal/21/1/sub/"
            val reqS1 = okhttp3.Request.Builder()
                .url(s1Url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://w1.anime4up.rest/")
                .build()
            val respS1 = client.newCall(reqS1).execute()
            val bodyS1 = respS1.body?.string() ?: ""
            println("Anime4up-S1 status: ${respS1.code}, len: ${bodyS1.length}")
            println("Anime4up-S1 snippet: ${bodyS1.take(1000)}")
            val m3u8 = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").findAll(bodyS1).map { it.value }.toList()
            println("Anime4up-S1 m3u8 found: $m3u8")
            val mp4 = Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""").findAll(bodyS1).map { it.value }.toList()
            println("Anime4up-S1 mp4 found: $mp4")
            val iframes = Regex("""<iframe[^>]+src=["']([^"']+)["']""").findAll(bodyS1).map { it.groupValues[1] }.toList()
            println("Anime4up-S1 iframes: $iframes")
        } catch (e: Exception) {
            println("Anime4up-S1 error: ${e.message}")
        }

        // 2. Inspect WitAnime watch page
        try {
            val witWatchUrl = "https://witanime.site/watch/one-piece/1"
            val reqWit = okhttp3.Request.Builder()
                .url(witWatchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://witanime.site/")
                .build()
            val respWit = client.newCall(reqWit).execute()
            val bodyWit = respWit.body?.string() ?: ""
            println("WitAnime watch page status: ${respWit.code}, len: ${bodyWit.length}")
            val serverTabs = Regex("""<li[^>]*>\s*<a[^>]+data-url=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(bodyWit).map { "${it.groupValues[2].trim()} -> ${it.groupValues[1]}" }.toList()
            println("WitAnime serverTabs with data-url: $serverTabs")
            val docWit = org.jsoup.Jsoup.parse(bodyWit, witWatchUrl)
            val serverLinks = docWit.select("a:contains(سيرفر), button:contains(سيرفر), li:contains(سيرفر), .server-item, .servers-list, #servers")
            println("WitAnime server elements count: ${serverLinks.size}")
            for (el in serverLinks.take(10)) {
                println("  el: tag=${el.tagName()}, class=${el.className()}, attrs=${el.attributes().asList().map { "${it.key}=${it.value}" }}")
            }
            val iframesWit = docWit.select("iframe")
            println("WitAnime jsoup iframes count: ${iframesWit.size}")
            for (ifm in iframesWit) {
                println("  iframe src=${ifm.attr("abs:src")}, data-src=${ifm.attr("abs:data-src")}")
            }
            val dataUrls = docWit.select("[data-url], [data-src], [data-embed], [data-server], [data-link]")
            println("WitAnime data-* elements count: ${dataUrls.size}")
            for (du in dataUrls.take(10)) {
                println("  data item: tag=${du.tagName()}, text=${du.text().take(30)}, attrs=${du.attributes().asList().filter { it.key.startsWith("data-") }.map { "${it.key}=${it.value}" }}")
            }
        } catch (e: Exception) {
            println("WitAnime inspect error: ${e.message}")
        }

        // 3. Inspect Vid3rb player html & HttpHelper
        try {
            val doc3rb = HttpHelper.getDocument("https://anime3rb.com/episode/one-piece/1")
            val playerUrl = Anime3rbHtmlParser.extractPlayerUrl(doc3rb.html())
            println("Anime3rb playerUrl from ep 1: $playerUrl")
            if (playerUrl != null) {
                try {
                    val htmlViaHttpHelper = HttpHelper.getHtml(playerUrl, referer = "https://anime3rb.com/episode/one-piece/1")
                    println("Vid3rb via HttpHelper SUCCESS! Length: ${htmlViaHttpHelper.length}")
                    val hasVideoSources = htmlViaHttpHelper.contains("video_sources")
                    println("Vid3rb contains 'video_sources': $hasVideoSources")
                    if (hasVideoSources) {
                        val vsIdx = htmlViaHttpHelper.indexOf("video_sources")
                        println("Vid3rb around video_sources: ${htmlViaHttpHelper.substring(maxOf(0, vsIdx - 30), minOf(htmlViaHttpHelper.length, vsIdx + 300))}")
                    } else {
                        val scripts = Regex("""<script[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).findAll(htmlViaHttpHelper).map { it.groupValues[1].take(200) }.toList()
                        println("Vid3rb scripts count: ${scripts.size}, first: ${scripts.take(2)}")
                    }
                    val parsed = Anime3rbHtmlParser.parsePlayerSources(htmlViaHttpHelper, playerUrl)
                    println("Vid3rb parsed sources count: ${parsed.size}")
                    for (p in parsed) {
                        println("  Direct source: ${p.name} [${p.quality}] isDirect=${p.isDirectVideo} -> ${p.url}")
                    }
                } catch (e: Exception) {
                    println("Vid3rb via HttpHelper FAILED with: ${e.javaClass.name}: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            println("Vid3rb player inspect error: ${e.message}")
        }

        // 4. Inspect VOE redirect target
        try {
            val voeTarget = "https://megankiddifference.com/e/kxxurqbklmyb"
            val reqVoeTarget = okhttp3.Request.Builder()
                .url(voeTarget)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://voe.sx/")
                .build()
            val respVoeTarget = client.newCall(reqVoeTarget).execute()
            val bodyVoeTarget = respVoeTarget.body?.string() ?: ""
            println("VOE Target status: ${respVoeTarget.code}, len: ${bodyVoeTarget.length}")
            val m3u8Voe = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").findAll(bodyVoeTarget).map { it.value }.toList()
            println("VOE Target m3u8: $m3u8Voe")
            val mp4Voe = Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""").findAll(bodyVoeTarget).map { it.value }.toList()
            println("VOE Target mp4: $mp4Voe")
            val sourcesVoe = Regex("""(?:sources|hls)\s*[:=]\s*["']?([^"'}\s]+)""").findAll(bodyVoeTarget).map { it.value }.toList()
            println("VOE Target sources regex: $sourcesVoe")
            val b64Voe = Regex("""(?:prompt|sources)\s*=\s*['"]([A-Za-z0-9+/=]{40,})['"]""").findAll(bodyVoeTarget).map { it.groupValues[1] }.toList()
            println("VOE Target base64 tokens: ${b64Voe.size}")
        } catch (e: Exception) {
            println("VOE target error: ${e.message}")
        }

        // 5. Inspect Mp4Upload embed
        try {
            val mp4Url = "https://www.mp4upload.com/embed-17xkrx6x68xb.html"
            val reqMp4 = okhttp3.Request.Builder()
                .url(mp4Url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.mp4upload.com/")
                .build()
            val respMp4 = client.newCall(reqMp4).execute()
            val bodyMp4 = respMp4.body?.string() ?: ""
            println("Mp4Upload embed status: ${respMp4.code}, len: ${bodyMp4.length}")
            val mp4Matches = Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""").findAll(bodyMp4).map { it.value }.toList()
            println("Mp4Upload direct mp4: $mp4Matches")
            val evalMatches = bodyMp4.contains("eval(function(p,a,c,k,e,d)")
            println("Mp4Upload contains packed eval: $evalMatches")
            val playerSrc = Regex("""player\.src\([^)]+\)""").find(bodyMp4)?.value
            println("Mp4Upload player.src: $playerSrc")
        } catch (e: Exception) {
            println("Mp4Upload error: ${e.message}")
        }

        // Test VOE redirect target

        // 1. Inspect Uqload POST /dl
        try {
            val uqUrl = "https://uqload.is/dl"
            val formBody = okhttp3.FormBody.Builder()
                .add("op", "embed")
                .add("file_code", "xxhtvm7dq6ct")
                .add("auto", "1")
                .add("referer", "https://w1.anime4up.rest/")
                .build()
            val reqUq = okhttp3.Request.Builder()
                .url(uqUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://uqload.is/e/xxhtvm7dq6ct")
                .post(formBody)
                .build()
            val respUq = client.newCall(reqUq).execute()
            val bodyUq = respUq.body?.string() ?: ""
            println("Uqload POST /dl status: ${respUq.code}, body len: ${bodyUq.length}")
            val videoTags = Regex("""<video[^>]*>(.*?)</video>""", RegexOption.DOT_MATCHES_ALL).findAll(bodyUq).map { it.groupValues[0] }.toList()
            println("Uqload <video> tags: $videoTags")
            val sources = Regex("""sources:\s*(\[[^\]]+\])""").find(bodyUq)?.groupValues?.get(1)
            println("Uqload sources: $sources")
            val mp4Matches = Regex("""['"](https?://[^'"]+\.mp4[^'"]*)['"]""").findAll(bodyUq).map { it.groupValues[1] }.toList()
            println("Uqload mp4 matches: $mp4Matches")
            val m3u8Matches = Regex("""['"](https?://[^'"]+\.m3u8[^'"]*)['"]""").findAll(bodyUq).map { it.groupValues[1] }.toList()
            println("Uqload m3u8 matches: $m3u8Matches")
            if (mp4Matches.isEmpty() && m3u8Matches.isEmpty()) {
                val scripts = Regex("""<script[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).findAll(bodyUq).map { it.groupValues[1] }.filter { it.contains("player") || it.contains("eval") || it.contains("Clappr") }.toList()
                for (s in scripts) println("  Uqload script: ${s.take(400)}")
            }
        } catch (e: Exception) {
            println("Uqload POST inspect error: ${e.message}")
        }

        // 1. Inspect AnimeSlayer episodes for One Piece
        try {
            val titleUrl = "https://animeslayer.to/title/One-Piece"
            val reqTitle = okhttp3.Request.Builder()
                .url(titleUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://animeslayer.to/ios")
                .build()
            val respTitle = client.newCall(reqTitle).execute()
            val bodyTitle = respTitle.body?.string() ?: ""
            println("AnimeSlayer title page status: ${respTitle.code}, body len: ${bodyTitle.length}")
            val episodesMatch = Regex("""const\s+episodes\s*=\s*\[(.*?)\];""", RegexOption.DOT_MATCHES_ALL).find(bodyTitle)
            if (episodesMatch != null) {
                println("AnimeSlayer found episodes block! Length: ${episodesMatch.groupValues[1].length}")
                val epItemRegex = Regex("""\{\s*n:\s*(\d+(?:\.\d+)?)\s*,\s*title:\s*["']([^"']*)["']\s*,\s*href:\s*["']([^"']*)["']""")
                val found = epItemRegex.findAll(episodesMatch.groupValues[1]).take(3).toList()
                for (m in found) {
                    val epNum = m.groupValues[1]
                    val encHref = m.groupValues[3]
                    // decrypt XOR with asxwqa147
                    val decoded = java.util.Base64.getDecoder().decode(encHref)
                    val key = "asxwqa147".toByteArray()
                    val decrypted = ByteArray(decoded.size) { i -> (decoded[i].toInt() xor key[i % key.size].toInt()).toByte() }
                    val link = String(decrypted)
                    println("  Episode $epNum -> $link")

                    // Inspect servers of first episode!
                    val epReq = okhttp3.Request.Builder()
                        .url("https://animeslayer.to$link")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", "https://animeslayer.to/title/One-Piece")
                        .build()
                    val epResp = client.newCall(epReq).execute()
                    val epBody = epResp.body?.string() ?: ""
                    println("  Episode 1 page status: ${epResp.code}, len: ${epBody.length}")
                    val serversMatch = Regex("""const\s+servers\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL).find(epBody)
                    println("  Episode 1 servers found: ${serversMatch != null}")
                    if (serversMatch != null) {
                        println("    Servers snippet: ${serversMatch.groupValues[1].take(400)}")
                    }
                    break
                }
            } else {
                println("AnimeSlayer 'const episodes' not found. Snippet: ${bodyTitle.take(400)}")
            }
        } catch (e: Exception) {
            println("AnimeSlayer inspect error: ${e.message}")
        }
    }
}
