package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * 5-Stage Pre-Save Source Tester.
 * Tests any built-in or custom template source across all five independent stages:
 *  1. Connectivity: Basic HTTP reachability (not blocked, not 403 Cloudflare, not 404).
 *  2. Search: Executes search for test query and extracts results.
 *  3. Episode Extraction: Opens anime page and extracts episode list + numbering.
 *  4. Server Discovery: Opens episode watch page and discovers server links or embeds.
 *  5. Stream Resolution: Validates that at least one playable direct stream or valid embed is resolved.
 */
object SourcePreSaveTester {

    private const val TAG = "SOURCE_TESTER"

    suspend fun testSource(
        provider: EpisodeSourceProvider,
        testQuery: String = "One Piece",
    ): FullSourceTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // ── Stage 1: Connectivity ───────────────────────────────────────────
        val connStart = System.currentTimeMillis()
        val connectivity = try {
            val probeUrl = when (provider) {
                is TemplateEpisodeSourceProvider -> provider.definition.baseUrl
                is Anime4upProvider -> "https://w1.anime4up.rest"
                is Anime3rbProvider -> "https://anime3rb.com"
                is WitAnimeProvider -> "https://witanime.site"
                is AnimeLuxeProvider -> "https://ww1.animeluxe.com"
                is AnimeSlayerProvider -> "https://animeslayer.to"
                else -> "https://google.com"
            }

            withTimeoutOrNull(7000L) {
                val conn = (URL(probeUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", HttpHelper.USER_AGENT)
                }
                val code = conn.responseCode
                val latency = System.currentTimeMillis() - connStart
                conn.disconnect()

                if (code == 403) {
                    SourceStageTestResult(
                        stageName = "الاتصال الأولي (Connectivity)",
                        isSuccess = false,
                        message = "محظور بنظام الحماية Cloudflare 403 Bot Protection",
                        details = "الموقع يطلب متصفحاً حقيقياً أو حلاً لكابتشا Turnstile",
                        latencyMs = latency,
                    )
                } else if (code in 200..399) {
                    SourceStageTestResult(
                        stageName = "الاتصال الأولي (Connectivity)",
                        isSuccess = true,
                        message = "تم الاتصال بنجاح (HTTP $code)",
                        details = "الموقع متاح ومستجيب خلال ${latency}ms",
                        latencyMs = latency,
                    )
                } else {
                    SourceStageTestResult(
                        stageName = "الاتصال الأولي (Connectivity)",
                        isSuccess = false,
                        message = "استجابة غير متوقعة (HTTP $code)",
                        latencyMs = latency,
                    )
                }
            } ?: SourceStageTestResult(
                stageName = "الاتصال الأولي (Connectivity)",
                isSuccess = false,
                message = "انتهت المهلة الزمنية (Timeout 7s)",
                latencyMs = 7000L,
            )
        } catch (e: Exception) {
            SourceStageTestResult(
                stageName = "الاتصال الأولي (Connectivity)",
                isSuccess = false,
                message = "فشل الاتصال: ${e.message}",
                latencyMs = System.currentTimeMillis() - connStart,
            )
        }

        if (!connectivity.isSuccess) {
            return@withContext FullSourceTestResult(
                sourceId = provider.id,
                sourceName = provider.name,
                overallSuccess = false,
                connectivityStage = connectivity,
                searchStage = SourceStageTestResult("البحث عن الأنمي (Search)", false, "تم التخطي بسبب فشل الاتصال"),
                episodeStage = SourceStageTestResult("استخراج الحلقات (Episodes)", false, "تم التخطي"),
                serverStage = SourceStageTestResult("اكتشاف السيرفرات (Servers)", false, "تم التخطي"),
                streamStage = SourceStageTestResult("التحقق من رابط التشغيل (Playback)", false, "تم التخطي"),
            )
        }

        // ── Stage 2: Search ─────────────────────────────────────────────────
        val searchStart = System.currentTimeMillis()
        var searchResults: List<AnimeSearchResult> = emptyList()
        val searchStage = try {
            withTimeoutOrNull(9000L) {
                searchResults = provider.search(testQuery)
                val latency = System.currentTimeMillis() - searchStart
                if (searchResults.isNotEmpty()) {
                    SourceStageTestResult(
                        stageName = "البحث عن الأنمي (Search)",
                        isSuccess = true,
                        message = "تم العثور على ${searchResults.size} نتيجة للبحث عن '$testQuery'",
                        details = "أول نتيجة: ${searchResults.first().title}",
                        latencyMs = latency,
                    )
                } else {
                    SourceStageTestResult(
                        stageName = "البحث عن الأنمي (Search)",
                        isSuccess = false,
                        message = "لم يتم العثور على أي نتائج للبحث عن '$testQuery'",
                        details = "تحقق من محددات البحث (Search Selectors) أو بنية الموقع",
                        latencyMs = latency,
                    )
                }
            } ?: SourceStageTestResult(
                stageName = "البحث عن الأنمي (Search)",
                isSuccess = false,
                message = "انتهت مهلة البحث (Timeout 9s)",
                latencyMs = 9000L,
            )
        } catch (e: Exception) {
            SourceStageTestResult(
                stageName = "البحث عن الأنمي (Search)",
                isSuccess = false,
                message = "خطأ أثناء البحث: ${e.message}",
                latencyMs = System.currentTimeMillis() - searchStart,
            )
        }

        if (!searchStage.isSuccess || searchResults.isEmpty()) {
            return@withContext FullSourceTestResult(
                sourceId = provider.id,
                sourceName = provider.name,
                overallSuccess = false,
                connectivityStage = connectivity,
                searchStage = searchStage,
                episodeStage = SourceStageTestResult("استخراج الحلقات (Episodes)", false, "تم التخطي لعدم وجود نتائج بحث"),
                serverStage = SourceStageTestResult("اكتشاف السيرفرات (Servers)", false, "تم التخطي"),
                streamStage = SourceStageTestResult("التحقق من رابط التشغيل (Playback)", false, "تم التخطي"),
            )
        }

        // ── Stage 3: Episode Extraction ─────────────────────────────────────
        val epStart = System.currentTimeMillis()
        var episodes: List<AnimeEpisode> = emptyList()
        val targetAnime = searchResults.first()
        val episodeStage = try {
            withTimeoutOrNull(9000L) {
                episodes = provider.getEpisodes(targetAnime.url)
                val latency = System.currentTimeMillis() - epStart
                if (episodes.isNotEmpty()) {
                    SourceStageTestResult(
                        stageName = "استخراج الحلقات (Episodes)",
                        isSuccess = true,
                        message = "تم استخراج ${episodes.size} حلقة بنجاح",
                        details = "أول حلقة: ${episodes.first().number} (${episodes.first().title})",
                        latencyMs = latency,
                    )
                } else {
                    SourceStageTestResult(
                        stageName = "استخراج الحلقات (Episodes)",
                        isSuccess = false,
                        message = "تم فتح صفحة الأنمي لكن قائمة الحلقات فارغة",
                        details = "تحقق من محدد الحلقات (Episode Selector): ${targetAnime.url}",
                        latencyMs = latency,
                    )
                }
            } ?: SourceStageTestResult(
                stageName = "استخراج الحلقات (Episodes)",
                isSuccess = false,
                message = "انتهت مهلة استخراج الحلقات (Timeout 9s)",
                latencyMs = 9000L,
            )
        } catch (e: Exception) {
            SourceStageTestResult(
                stageName = "استخراج الحلقات (Episodes)",
                isSuccess = false,
                message = "خطأ استخراج الحلقات: ${e.message}",
                latencyMs = System.currentTimeMillis() - epStart,
            )
        }

        if (!episodeStage.isSuccess || episodes.isEmpty()) {
            return@withContext FullSourceTestResult(
                sourceId = provider.id,
                sourceName = provider.name,
                overallSuccess = false,
                connectivityStage = connectivity,
                searchStage = searchStage,
                episodeStage = episodeStage,
                serverStage = SourceStageTestResult("اكتشاف السيرفرات (Servers)", false, "تم التخطي لعدم توفر حلقات"),
                streamStage = SourceStageTestResult("التحقق من رابط التشغيل (Playback)", false, "تم التخطي"),
            )
        }

        // ── Stage 4: Server Discovery ───────────────────────────────────────
        val serverStart = System.currentTimeMillis()
        var servers: List<AnimeStreamServer> = emptyList()
        val targetEp = episodes.first()
        val serverStage = try {
            withTimeoutOrNull(9000L) {
                servers = provider.getStreamServers(targetEp.url)
                val latency = System.currentTimeMillis() - serverStart
                if (servers.isNotEmpty()) {
                    SourceStageTestResult(
                        stageName = "اكتشاف السيرفرات (Servers)",
                        isSuccess = true,
                        message = "تم اكتشاف ${servers.size} سيرفر أو مشغل مدمج",
                        details = servers.take(3).joinToString(" | ") { "${it.name} (${it.quality})" },
                        latencyMs = latency,
                    )
                } else {
                    SourceStageTestResult(
                        stageName = "اكتشاف السيرفرات (Servers)",
                        isSuccess = false,
                        message = "لم يتم اكتشاف أي سيرفر تشغيل في صفحة الحلقة",
                        details = "تحقق من محدد السيرفرات (Server Selector): ${targetEp.url}",
                        latencyMs = latency,
                    )
                }
            } ?: SourceStageTestResult(
                stageName = "اكتشاف السيرفرات (Servers)",
                isSuccess = false,
                message = "انتهت مهلة اكتشاف السيرفرات (Timeout 9s)",
                latencyMs = 9000L,
            )
        } catch (e: Exception) {
            SourceStageTestResult(
                stageName = "اكتشاف السيرفرات (Servers)",
                isSuccess = false,
                message = "خطأ أثناء اكتشاف السيرفرات: ${e.message}",
                latencyMs = System.currentTimeMillis() - serverStart,
            )
        }

        if (!serverStage.isSuccess || servers.isEmpty()) {
            return@withContext FullSourceTestResult(
                sourceId = provider.id,
                sourceName = provider.name,
                overallSuccess = false,
                connectivityStage = connectivity,
                searchStage = searchStage,
                episodeStage = episodeStage,
                serverStage = serverStage,
                streamStage = SourceStageTestResult("التحقق من رابط التشغيل (Playback)", false, "تم التخطي لعدم وجود سيرفرات"),
            )
        }

        // ── Stage 5: Playable Stream Resolution ─────────────────────────────
        val streamStart = System.currentTimeMillis()
        val streamStage = try {
            val directServer = servers.firstOrNull { it.isDirectVideo || SourceLinkExtractor.isPlayableDirectStream(it) }
            val embedServer = servers.firstOrNull { it.url.startsWith("http") }

            if (directServer != null) {
                val validation = AiSourceAssistant.validateStreamUrl(directServer.url)
                val testRes = AiSourceAssistant.sandboxTestUrl(directServer.url, directServer.headers)
                val latency = System.currentTimeMillis() - streamStart

                if (testRes.passed) {
                    SourceStageTestResult(
                        stageName = "التحقق من رابط التشغيل (Playback)",
                        isSuccess = true,
                        message = "رابط فيديو مباشر تم التحقق منه وجاهز للمشغّل (${directServer.quality})",
                        details = "${directServer.name} -> HTTP ${testRes.httpStatus}",
                        latencyMs = latency,
                    )
                } else {
                    SourceStageTestResult(
                        stageName = "التحقق من رابط التشغيل (Playback)",
                        isSuccess = false,
                        message = "فشل التحقق من الرابط المباشر: ${testRes.reason}",
                        details = directServer.url.take(60),
                        latencyMs = latency,
                    )
                }
            } else if (embedServer != null) {
                val latency = System.currentTimeMillis() - streamStart
                SourceStageTestResult(
                    stageName = "التحقق من رابط التشغيل (Playback)",
                    isSuccess = true,
                    message = "تم توفير مشغل ويب/مضمن بديل جاهز للعرض (${embedServer.quality})",
                    details = embedServer.url.take(60),
                    latencyMs = latency,
                )
            } else {
                SourceStageTestResult(
                    stageName = "التحقق من رابط التشغيل (Playback)",
                    isSuccess = false,
                    message = "تعذر استخراج أي رابط تشغيل أو تضمين صالح",
                    latencyMs = System.currentTimeMillis() - streamStart,
                )
            }
        } catch (e: Exception) {
            SourceStageTestResult(
                stageName = "التحقق من رابط التشغيل (Playback)",
                isSuccess = false,
                message = "خطأ فحص الرابط: ${e.message}",
                latencyMs = System.currentTimeMillis() - streamStart,
            )
        }

        val allPassed = connectivity.isSuccess && searchStage.isSuccess &&
                episodeStage.isSuccess && serverStage.isSuccess && streamStage.isSuccess

        FullSourceTestResult(
            sourceId = provider.id,
            sourceName = provider.name,
            overallSuccess = allPassed,
            connectivityStage = connectivity,
            searchStage = searchStage,
            episodeStage = episodeStage,
            serverStage = serverStage,
            streamStage = streamStage,
        )
    }
}
