package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.regex.Pattern

/**
 * AiSourceAssistant — Intelligent diagnostic & recovery system for anime stream sources.
 *
 * Capabilities:
 *  1. Diagnose why an episode failed to load (timeout, HTTP errors, no direct link, etc.)
 *  2. Suggest concrete fixes based on the latest SmartStream diagnostics
 *  3. Safely test a candidate stream URL (no JS execution, strict URL validation, sandboxed)
 *  4. Propose a patch to mark a server as recovered or deprioritized
 *
 * Security rules (non-negotiable):
 *  - Never executes arbitrary JavaScript
 *  - All URLs must pass strict HTTPS-only validation before any network call
 *  - No data is sent to external AI/cloud services; all analysis is purely local & rule-based
 *  - Does NOT modify the video player interface, controls, or behavior in any way
 */
object AiSourceAssistant {

    private const val TAG = "AI_SOURCE_ASSISTANT"

    // Safe URL pattern: HTTPS only, no JS scheme, no data: URI
    private val SAFE_URL_PATTERN: Pattern = Pattern.compile(
        "^https://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+$"
    )

    // Known blocked/dangerous URL fragments
    private val BLOCKED_FRAGMENTS = listOf(
        "javascript:", "data:", "file://", "localhost", "127.0.0.1",
        "192.168.", "10.0.", "eval(", "<script", "document.", "window.",
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Diagnoses why an episode (by number) failed to stream.
     * Returns a structured [DiagnosticResult] with the root cause and suggested actions.
     */
    fun diagnoseEpisode(episodeNumber: Float): DiagnosticResult {
        val report = SmartStreamDiagnostics.getReport(episodeNumber)
            ?: return DiagnosticResult(
                episodeNumber = episodeNumber,
                rootCause = DiagnosticCause.NO_DATA,
                arabicSummary = "لا توجد بيانات تشخيصية لهذه الحلقة. حاول تشغيلها أولاً.",
                suggestions = listOf("شغّل الحلقة مرة واحدة على الأقل ثم أعد المحاولة."),
            )

        Log.d(TAG, "DIAGNOSE: ep=$episodeNumber, queried=${report.queriedSourcesCount}, valid=${report.validDirectStreamsCount}")

        // Analyze the report to determine root cause
        return when {
            report.queriedSourcesCount == 0 -> DiagnosticResult(
                episodeNumber = episodeNumber,
                rootCause = DiagnosticCause.NO_SOURCES_CONFIGURED,
                arabicSummary = "لم يتم ضبط أي مصادر لهذه الحلقة.",
                suggestions = listOf(
                    "تأكد من أن المصادر مفعّلة في إعدادات التطبيق.",
                    "تحقق من أن اسم الأنمي صحيح ومطابق.",
                ),
                report = report,
            )

            report.respondingSourcesCount == 0 -> DiagnosticResult(
                episodeNumber = episodeNumber,
                rootCause = DiagnosticCause.ALL_SOURCES_TIMED_OUT,
                arabicSummary = "انتهت مهلة جميع المصادر. قد يكون الاتصال بطيئاً أو المصادر معطّلة.",
                suggestions = listOf(
                    "تحقق من اتصالك بالإنترنت.",
                    "انتظر دقيقة ثم أعد المحاولة.",
                    "فعّل مصدراً احتياطياً من الإعدادات.",
                ),
                report = report,
            )

            report.totalDiscoveredServers > 0 && report.validDirectStreamsCount == 0 -> {
                val hasEmbedLinks = report.serverDetails.any { it.status == ServerDiscoveryStatus.FAILED_NOT_DIRECT_VIDEO }
                DiagnosticResult(
                    episodeNumber = episodeNumber,
                    rootCause = DiagnosticCause.NO_DIRECT_VIDEO_LINKS,
                    arabicSummary = if (hasEmbedLinks)
                        "تم العثور على روابط تضمين (Embed) فقط وليس روابط فيديو مباشرة. المصادر قد غيّرت بنيتها."
                    else
                        "لم يتم العثور على روابط فيديو مباشرة قابلة للتشغيل.",
                    suggestions = listOf(
                        "أعد تحميل الحلقة بالضغط على زر الإعادة.",
                        "جرّب تغيير المصدر من قائمة الجودة.",
                        "قد يكون المصدر تغيّر هيكله — تحقق من التحديثات.",
                    ),
                    report = report,
                )
            }

            report.validDirectStreamsCount > 0 && report.winningStreamUrl == null -> DiagnosticResult(
                episodeNumber = episodeNumber,
                rootCause = DiagnosticCause.STREAM_SELECTION_FAILED,
                arabicSummary = "تم العثور على روابط مباشرة لكن فشل اختيار أفضل رابط.",
                suggestions = listOf("أعد تحميل الحلقة. إن استمر، أبلغ عن المشكلة."),
                report = report,
            )

            report.failureReasons.any { it.contains("403") || it.contains("Forbidden") } -> DiagnosticResult(
                episodeNumber = episodeNumber,
                rootCause = DiagnosticCause.EXPIRED_TOKEN_403,
                arabicSummary = "الروابط منتهية الصلاحية (خطأ 403). سيتم تجديدها تلقائياً.",
                suggestions = listOf(
                    "اضغط على زر إعادة التحميل لتجديد الروابط.",
                    "الروابط المؤقتة تنتهي بعد ساعة — أعد التشغيل.",
                ),
                report = report,
            )

            report.validDirectStreamsCount > 0 -> DiagnosticResult(
                episodeNumber = episodeNumber,
                rootCause = DiagnosticCause.OK,
                arabicSummary = "الحلقة تعمل بشكل صحيح. ${report.validDirectStreamsCount} رابط مباشر متوفر.",
                suggestions = emptyList(),
                report = report,
            )

            else -> DiagnosticResult(
                episodeNumber = episodeNumber,
                rootCause = DiagnosticCause.UNKNOWN,
                arabicSummary = "حدث خطأ غير معروف. تفاصيل: ${report.failureReasons.joinToString(" | ")}",
                suggestions = listOf("أعد تحميل التطبيق وحاول مجدداً."),
                report = report,
            )
        }
    }

    /**
     * Proposes actionable patches for the given diagnostic result.
     * Returns a list of [PatchProposal] items, each with a safe, sandboxed action.
     */
    fun proposePatch(diagnostic: DiagnosticResult): List<PatchProposal> {
        return when (diagnostic.rootCause) {
            DiagnosticCause.EXPIRED_TOKEN_403 -> listOf(
                PatchProposal(
                    id = "refresh_token",
                    title = "تجديد روابط التشغيل",
                    description = "إلغاء صلاحية الكاش وإعادة استخراج الروابط الحلقة ${diagnostic.episodeNumber}",
                    action = PatchAction.INVALIDATE_CACHE_AND_REEXTRACT,
                    episodeNumber = diagnostic.episodeNumber,
                )
            )
            DiagnosticCause.ALL_SOURCES_TIMED_OUT -> listOf(
                PatchProposal(
                    id = "force_refresh_all",
                    title = "إعادة المحاولة على جميع المصادر",
                    description = "إجبار إعادة الاستعلام على جميع المصادر بمهلة أعلى",
                    action = PatchAction.FORCE_REFRESH_ALL_SOURCES,
                    episodeNumber = diagnostic.episodeNumber,
                )
            )
            DiagnosticCause.NO_DIRECT_VIDEO_LINKS -> listOf(
                PatchProposal(
                    id = "clear_and_reextract",
                    title = "مسح الكاش وإعادة الاستخراج",
                    description = "مسح نتائج الاستخراج السابقة وإعادة البحث عن روابط مباشرة",
                    action = PatchAction.CLEAR_EPISODE_CACHE,
                    episodeNumber = diagnostic.episodeNumber,
                )
            )
            else -> emptyList()
        }
    }

    /**
     * Safely validates a candidate stream URL before it's used for playback.
     * Returns [UrlValidationResult] with whether it's safe to use.
     *
     * Rules: HTTPS only, no JS, no local IPs, must match video extension or known host patterns.
     */
    fun validateStreamUrl(url: String): UrlValidationResult {
        val trimmed = url.trim()

        if (trimmed.isBlank()) {
            return UrlValidationResult(isValid = false, reason = "الرابط فارغ")
        }

        // Check for blocked fragments (security)
        for (blocked in BLOCKED_FRAGMENTS) {
            if (trimmed.lowercase().contains(blocked)) {
                return UrlValidationResult(
                    isValid = false,
                    reason = "الرابط يحتوي على محتوى محظور: $blocked"
                )
            }
        }

        // Must be HTTPS
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return UrlValidationResult(
                isValid = false,
                reason = "الرابط يجب أن يبدأ بـ https:// فقط"
            )
        }

        // Must match safe URL pattern
        if (!SAFE_URL_PATTERN.matcher(trimmed).matches()) {
            return UrlValidationResult(
                isValid = false,
                reason = "الرابط يحتوي على أحرف غير مسموح بها"
            )
        }

        // Check for video-like content
        val lower = trimmed.lowercase()
        val isVideoUrl = lower.contains(".mp4") || lower.contains(".m3u8") ||
                lower.contains(".mpd") || lower.contains("video.vid3rb.com/video/") ||
                lower.contains("mp4upload.com") && lower.contains("/video.mp4")

        if (!isVideoUrl) {
            return UrlValidationResult(
                isValid = false,
                reason = "الرابط لا يبدو رابط فيديو مباشر (.mp4 / .m3u8 / .mpd)",
                isSuspicious = true,
            )
        }

        return UrlValidationResult(
            isValid = true,
            reason = "الرابط صالح وآمن للتشغيل",
        )
    }

    /**
     * Sandbox-tests a stream URL by performing a lightweight HEAD request (no body download).
     * Returns [SandboxTestResult] with the HTTP status and latency.
     *
     * This does NOT play the video, inject JS, or trigger UI changes.
     */
    suspend fun sandboxTestUrl(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): SandboxTestResult = withContext(Dispatchers.IO) {
        val validation = validateStreamUrl(url)
        if (!validation.isValid) {
            return@withContext SandboxTestResult(
                url = url,
                passed = false,
                httpStatus = -1,
                latencyMs = 0,
                reason = validation.reason,
            )
        }

        val startMs = System.currentTimeMillis()
        try {
            withTimeoutOrNull(8000L) {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.setRequestProperty("User-Agent", HttpHelper.USER_AGENT)
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                connection.connect()
                val code = connection.responseCode
                val latency = System.currentTimeMillis() - startMs
                connection.disconnect()

                Log.d(TAG, "SANDBOX_TEST: url=${url.take(60)}, status=$code, latency=${latency}ms")

                SandboxTestResult(
                    url = url,
                    passed = code in 200..299 || code == 302 || code == 301,
                    httpStatus = code,
                    latencyMs = latency,
                    reason = when (code) {
                        in 200..299 -> "✅ رابط صالح ($code)"
                        301, 302    -> "↩️ إعادة توجيه ($code) — قد يعمل"
                        403         -> "❌ محظور (403) — الرابط منتهي الصلاحية"
                        404         -> "❌ غير موجود (404)"
                        in 500..599 -> "❌ خطأ في الخادم ($code)"
                        else        -> "⚠️ رمز غير متوقع ($code)"
                    },
                )
            } ?: SandboxTestResult(
                url = url,
                passed = false,
                httpStatus = -1,
                latencyMs = System.currentTimeMillis() - startMs,
                reason = "⏱️ انتهت المهلة (8 ثوانٍ)",
            )
        } catch (e: Exception) {
            Log.w(TAG, "SANDBOX_TEST_ERROR: ${e.message}")
            SandboxTestResult(
                url = url,
                passed = false,
                httpStatus = -1,
                latencyMs = System.currentTimeMillis() - startMs,
                reason = "❌ خطأ في الاتصال: ${e.message?.take(80)}",
            )
        }
    }

    /**
     * Applies an approved [PatchProposal] action.
     * Actions are limited to cache operations — no player modification.
     */
    fun applyPatch(
        proposal: PatchProposal,
        animeTitle: String = "",
    ) {
        Log.i(TAG, "APPLY_PATCH: id=${proposal.id}, action=${proposal.action}, ep=${proposal.episodeNumber}")
        when (proposal.action) {
            PatchAction.INVALIDATE_CACHE_AND_REEXTRACT -> {
                SmartStreamCache.invalidateStream(animeTitle, proposal.episodeNumber)
                Log.i(TAG, "PATCH_APPLIED: cache invalidated for ep=${proposal.episodeNumber}")
            }
            PatchAction.FORCE_REFRESH_ALL_SOURCES -> {
                SmartStreamCache.invalidateStream(animeTitle, proposal.episodeNumber)
                Log.i(TAG, "PATCH_APPLIED: forced full refresh for ep=${proposal.episodeNumber}")
            }
            PatchAction.CLEAR_EPISODE_CACHE -> {
                SmartStreamCache.invalidateStream(animeTitle, proposal.episodeNumber)
                Log.i(TAG, "PATCH_APPLIED: episode cache cleared for ep=${proposal.episodeNumber}")
            }
            PatchAction.DEPRIORITIZE_SERVER -> {
                proposal.targetServerUrl?.let { serverUrl ->
                    SmartStreamCache.recordServerFailure(serverUrl, "Manually deprioritized via AI patch")
                    Log.i(TAG, "PATCH_APPLIED: server deprioritized: $serverUrl")
                }
            }
            PatchAction.RESTORE_SERVER -> {
                proposal.targetServerUrl?.let { serverUrl ->
                    SmartStreamCache.recordServerSuccess(serverUrl)
                    Log.i(TAG, "PATCH_APPLIED: server restored: $serverUrl")
                }
            }
        }
    }

    /** Generates a full Arabic-language diagnostic summary for the current session */
    fun generateSessionSummary(): String {
        val reports = SmartStreamDiagnostics.getAllReports()
        val status = AiAssistantRepository.connectionStatus.value
        val statusDesc = when (status) {
            is AiConnectionStatus.Connected -> "🟢 الذكاء الاصطناعي: متصل (${status.model})"
            is AiConnectionStatus.LocalFallback -> "🟡 وضع التشخيص: محلي (قواعد استدلالية)"
            is AiConnectionStatus.Unconfigured -> "⚪ الذكاء الاصطناعي: غير مهيأ (يمكن إدخال مفتاح API في الإعدادات)"
            is AiConnectionStatus.ConnectionError -> "🔴 خطأ اتصال الذكاء الاصطناعي: ${status.errorMessage}"
        }

        if (reports.isEmpty()) {
            return "$statusDesc\n\nلا توجد بيانات تشخيصية للحلقات في هذه الجلسة بعد. شغّل حلقة لبدء تجميع البيانات."
        }

        return buildString {
            appendLine(statusDesc)
            appendLine()
            appendLine("📊 ملخص جلسة التشخيص (${reports.size} حلقة):")
            appendLine()
            val totalServers = reports.sumOf { it.totalDiscoveredServers }
            val totalValid = reports.sumOf { it.validDirectStreamsCount }
            val successCount = reports.count { it.validDirectStreamsCount > 0 }
            val failCount = reports.count { it.validDirectStreamsCount == 0 }
            appendLine("✅ حلقات نجحت: $successCount / ${reports.size}")
            appendLine("❌ حلقات فشلت: $failCount / ${reports.size}")
            appendLine("🔗 مجموع السيرفرات المكتشفة: $totalServers")
            appendLine("🎯 مجموع الروابط المباشرة: $totalValid")
            appendLine()
            if (failCount > 0) {
                appendLine("⚠️ الحلقات الفاشلة:")
                reports.filter { it.validDirectStreamsCount == 0 }.forEach { r ->
                    appendLine("  - حلقة ${r.episodeNumber}: ${r.failureReasons.firstOrNull() ?: "خطأ غير محدد"}")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

enum class DiagnosticCause {
    OK,
    NO_DATA,
    NO_SOURCES_CONFIGURED,
    ALL_SOURCES_TIMED_OUT,
    NO_DIRECT_VIDEO_LINKS,
    STREAM_SELECTION_FAILED,
    EXPIRED_TOKEN_403,
    UNKNOWN,
}

data class DiagnosticResult(
    val episodeNumber: Float,
    val rootCause: DiagnosticCause,
    val arabicSummary: String,
    val suggestions: List<String>,
    val report: EpisodeDiagnosticReport? = null,
)

enum class PatchAction {
    INVALIDATE_CACHE_AND_REEXTRACT,
    FORCE_REFRESH_ALL_SOURCES,
    CLEAR_EPISODE_CACHE,
    DEPRIORITIZE_SERVER,
    RESTORE_SERVER,
}

data class PatchProposal(
    val id: String,
    val title: String,
    val description: String,
    val action: PatchAction,
    val episodeNumber: Float,
    val targetServerUrl: String? = null,
)

data class UrlValidationResult(
    val isValid: Boolean,
    val reason: String,
    val isSuspicious: Boolean = false,
)

data class SandboxTestResult(
    val url: String,
    val passed: Boolean,
    val httpStatus: Int,
    val latencyMs: Long,
    val reason: String,
)
