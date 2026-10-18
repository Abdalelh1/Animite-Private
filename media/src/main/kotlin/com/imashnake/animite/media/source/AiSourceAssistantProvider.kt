package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

interface AiSourceAssistantProvider {
    val type: AiProviderType
    val displayName: String

    suspend fun testConnection(): Pair<Boolean, String>
    suspend fun analyze(request: AiDiagnosticAnalysisRequest): Result<AiAnalysisResponse>
}

/**
 * Real Google Gemini AI Provider.
 * Connects directly to the Gemini REST API using user-configured API key and model.
 */
class GeminiAiAssistantProvider(
    private val apiKey: String,
    val model: String = "gemini-2.0-flash",
) : AiSourceAssistantProvider {

    override val type: AiProviderType = AiProviderType.GEMINI

    var activeModel: String = model.trim().removePrefix("models/").ifBlank { "gemini-2.0-flash" }
        private set

    override val displayName: String get() = "Google Gemini ($activeModel)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Pair(false, "يرجى إدخال مفتاح Google Gemini API أولاً.")
        }

        val pingBody = """{"contents":[{"parts":[{"text":"ping"}]}]}"""
        val initialModel = activeModel

        // 1. Try active model with v1beta then v1
        val endpointsToTry = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models/$initialModel:generateContent?key=$trimmedKey",
            "https://generativelanguage.googleapis.com/v1/models/$initialModel:generateContent?key=$trimmedKey"
        )

        for (endpoint in endpointsToTry) {
            try {
                val resp = HttpHelper.postJson(endpoint, pingBody)
                if (resp.contains("candidates") || resp.contains("text")) {
                    return@withContext Pair(true, "تم الاتصال بنموذج $initialModel بنجاح!")
                } else if (resp.contains("API_KEY_INVALID") || resp.contains("PERMISSION_DENIED")) {
                    return@withContext Pair(false, "مفتاح API غير صالح أو ليس لديه الصلاحيات المطلوبة.")
                }
            } catch (_: Exception) {}
        }

        // 2. Initial model failed (404/unsupported). Query ListModels to find models available for this specific key
        Log.w("GEMINI_AI", "Initial model $initialModel returned error. Discovering available models for key...")
        val availableModels = fetchAvailableModels(trimmedKey)

        if (availableModels.isNotEmpty()) {
            val preferredOrder = listOf(
                "gemini-2.5-flash",
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-latest",
                "gemini-1.5-flash-002",
                "gemini-1.5-flash-001",
                "gemini-1.5-flash-8b",
                "gemini-2.0-flash-exp",
                "gemini-2.5-pro",
                "gemini-1.5-pro",
            )
            val candidate = preferredOrder.firstOrNull { it in availableModels }
                ?: availableModels.firstOrNull { it.contains("flash") }
                ?: availableModels.first()

            val candidateEndpoints = listOf(
                "https://generativelanguage.googleapis.com/v1beta/models/$candidate:generateContent?key=$trimmedKey",
                "https://generativelanguage.googleapis.com/v1/models/$candidate:generateContent?key=$trimmedKey"
            )

            for (candEndpoint in candidateEndpoints) {
                try {
                    val candResp = HttpHelper.postJson(candEndpoint, pingBody)
                    if (candResp.contains("candidates") || candResp.contains("text")) {
                        activeModel = candidate
                        return@withContext Pair(
                            true,
                            "تم الاتصال بنجاح بالنموذج المتاح: $candidate (تم التبديل التلقائي من $initialModel غير المدعوم لمفتاحك)"
                        )
                    }
                } catch (_: Exception) {}
            }

            return@withContext Pair(
                false,
                "النموذج '$initialModel' غير متاح. النماذج المتاحة لمفتاحك: ${availableModels.take(4).joinToString(", ")}"
            )
        }

        return@withContext Pair(false, "تعذر العثور على نماذج تدعم توليد النصوص لهذا المفتاح. يرجى مراجعة الصلاحيات في Google AI Studio.")
    }

    private suspend fun fetchAvailableModels(key: String): List<String> = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models?key=$key",
            "https://generativelanguage.googleapis.com/v1/models?key=$key"
        )
        for (url in urls) {
            try {
                val jsonStr = HttpHelper.getHtml(url)
                val element = json.parseToJsonElement(jsonStr).jsonObject
                val modelsArray = element["models"]?.jsonArray
                if (modelsArray != null && modelsArray.isNotEmpty()) {
                    val supported = mutableListOf<String>()
                    for (item in modelsArray) {
                        val obj = item.jsonObject
                        val rawName = obj["name"]?.jsonPrimitive?.content ?: continue
                        val methods = obj["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                        if (methods.contains("generateContent")) {
                            supported.add(rawName.removePrefix("models/"))
                        }
                    }
                    if (supported.isNotEmpty()) return@withContext supported
                }
            } catch (e: Exception) {
                Log.d("GEMINI_AI", "fetchAvailableModels error for $url: ${e.message}")
            }
        }
        emptyList()
    }

    override suspend fun analyze(request: AiDiagnosticAnalysisRequest): Result<AiAnalysisResponse> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("مفتاح API غير مهيأ"))
        }

        val prompt = buildAnalysisPrompt(request)
        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", prompt) })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            }
        }.toString()

        val endpoints = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models/$activeModel:generateContent?key=$trimmedKey",
            "https://generativelanguage.googleapis.com/v1/models/$activeModel:generateContent?key=$trimmedKey"
        )

        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                val responseText = HttpHelper.postJson(endpoint, requestBody)
                val parsed = parseAiJsonResponse(responseText, displayName, isRealAi = true, request)
                return@withContext Result.success(parsed)
            } catch (e: Exception) {
                lastError = e
            }
        }

        Log.e("GEMINI_AI", "Analysis call failed: ${lastError?.message}")
        Result.failure(lastError ?: Exception("Unknown error"))
    }
}

/**
 * OpenAI-Compatible AI Provider (OpenAI, DeepSeek, Groq, Ollama, Cloudflare AI Gateway).
 */
class OpenAiCompatibleAiProvider(
    private val apiKey: String,
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
) : AiSourceAssistantProvider {

    override val type: AiProviderType = AiProviderType.OPENAI_COMPATIBLE
    override val displayName: String = "OpenAI Compatible ($model)"

    override suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Pair(false, "يرجى إدخال مفتاح API أولاً.")
        }
        val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"
        val testBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "ping")
                })
            }
            put("max_tokens", 5)
        }.toString()

        try {
            val resp = HttpHelper.postJson(
                urlString = endpoint,
                jsonBody = testBody,
                headers = mapOf("Authorization" to "Bearer $apiKey"),
            )
            if (resp.contains("choices") || resp.contains("content")) {
                Pair(true, "تم الاتصال بنجاح بـ $endpoint!")
            } else if (resp.contains("invalid_api_key") || resp.contains("Unauthorized")) {
                Pair(false, "مفتاح API غير صالح أو غير معتمد.")
            } else {
                Pair(false, "استجابة غير متوقعة: ${resp.take(120)}")
            }
        } catch (e: Exception) {
            Pair(false, "فشل الاتصال: ${e.message}")
        }
    }

    override suspend fun analyze(request: AiDiagnosticAnalysisRequest): Result<AiAnalysisResponse> = withContext(Dispatchers.IO) {
        val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"
        val prompt = buildAnalysisPrompt(request)
        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "You are an expert anime stream extraction engineer. Analyze diagnostics and return JSON.")
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            putJsonObject("response_format") {
                put("type", "json_object")
            }
            put("temperature", 0.2)
        }.toString()

        try {
            val responseText = HttpHelper.postJson(
                urlString = endpoint,
                jsonBody = requestBody,
                headers = mapOf("Authorization" to "Bearer $apiKey"),
            )
            val parsed = parseAiJsonResponse(responseText, displayName, isRealAi = true, request)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e("OPENAI_AI", "Analysis call failed: ${e.message}")
            Result.failure(e)
        }
    }
}

/**
 * Local Deterministic Diagnostic Provider.
 * Always available offline without API keys.
 * Explicitly displays that it operates locally and does NOT claim to be a cloud AI model.
 */
class LocalDiagnosticAiProvider : AiSourceAssistantProvider {

    override val type: AiProviderType = AiProviderType.LOCAL_DIAGNOSTIC
    override val displayName: String = "التشخيص المحلي (قواعد استدلالية)"

    override suspend fun testConnection(): Pair<Boolean, String> {
        return Pair(true, "المحرك المحلي جاهز ويعمل دون اتصال بالإنترنت.")
    }

    override suspend fun analyze(request: AiDiagnosticAnalysisRequest): Result<AiAnalysisResponse> {
        val findings = mutableListOf<AiHypothesis>()
        val proposedPatches = mutableListOf<AiProposedPatch>()

        // 1. Analyze 403 / Token expiration
        if (request.failureReasons.any { it.contains("403") || it.contains("Forbidden") }) {
            findings.add(
                AiHypothesis(
                    title = "انتهاء صلاحية التوكن المؤقت (403 Forbidden)",
                    confidence = 0.95f,
                    verifiedEvidence = listOf("استجابة الخادم تحتوي على رمز الخطأ 403", "الرابط يحتوي على معامل expires أو token"),
                    hypothesisExplanation = "روابط البث المباشرة من خوادم Vid3rb/Share4max تحمل توكنات تنتهي صلاحيتها وتتطلب تجديداً من صفحة الحلقة الأصلية."
                )
            )
            proposedPatches.add(
                AiProposedPatch(
                    patchId = "local_patch_refresh_${UUID.randomUUID().toString().take(6)}",
                    sourceId = request.failingProviderId,
                    title = "تجديد توكنات التشغيل تلقائياً",
                    description = "إلغاء صلاحية الكاش وإعادة استخراج الروابط المباشرة للحلقة ${request.episodeNumber}",
                    actionType = PatchActionType.INVALIDATE_CACHE,
                )
            )
        }

        // 2. Analyze Embed-only / No direct video
        if (request.totalDiscoveredServers > 0 && request.validDirectStreamsCount == 0) {
            findings.add(
                AiHypothesis(
                    title = "روابط مشغلات مضمنة (Embed) دون روابط فيديو مباشرة",
                    confidence = 0.85f,
                    verifiedEvidence = listOf("تم اكتشاف ${request.totalDiscoveredServers} سيرفر", "عدد الروابط المباشرة 0"),
                    hypothesisExplanation = "المصدر يوفر مشغلات iframes خارجية مثل Streamwish أو Voe ولم يتم تحويلها إلى روابط مباشرة بعد."
                )
            )
        }

        // 3. Analyze Timeouts
        if (request.totalDiscoveredServers == 0) {
            findings.add(
                AiHypothesis(
                    title = "انقطاع أو بطء استجابة المصدر",
                    confidence = 0.80f,
                    verifiedEvidence = request.failureReasons,
                    hypothesisExplanation = "لم يستجب المصدر ضمن المهلة المحددة (9 ثوانٍ)، قد يكون الموقع تحت صيانة أو محجوباً في المنطقة."
                )
            )
        }

        val summary = if (findings.isNotEmpty()) {
            "تم إجراء فحص استدلالي محلي للحلقة ${request.episodeNumber}: تم رصد ${findings.size} ملاحظات و ${proposedPatches.size} مقترحات للإصلاح."
        } else {
            "فحص استدلالي محلي: لا توجد أخطاء حرجة مسجلة."
        }

        return Result.success(
            AiAnalysisResponse(
                providerUsed = displayName,
                isRealAiModel = false,
                findings = findings,
                proposedPatches = proposedPatches,
                summary = summary,
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Prompt & Response Parsing Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun buildAnalysisPrompt(request: AiDiagnosticAnalysisRequest): String {
    return """
You are an expert anime stream extraction engineer analyzing a streaming failure.
Context:
- Anime Title: ${request.animeTitle}
- Episode: ${request.episodeNumber}
- Target Source ID: ${request.failingProviderId}
- Discovered Servers: ${request.totalDiscoveredServers}
- Valid Direct Streams: ${request.validDirectStreamsCount}
- Failure Reasons: ${request.failureReasons.joinToString(" | ")}
- Server Details: ${request.serverDetails.take(5).joinToString(" | ")}
- Previously Failed Fixes to AVOID: ${request.previouslyFailedFixes.joinToString(", ")}

Analyze this failure and return a JSON object with this exact schema:
{
  "summary": "Short 1-2 sentence Arabic summary of root cause",
  "findings": [
    {
      "title": "Finding title in Arabic",
      "confidence": 0.95,
      "verifiedEvidence": ["Concrete verified evidence from logs"],
      "hypothesisExplanation": "Hypothesis explaining why this occurred"
    }
  ],
  "proposedPatches": [
    {
      "patchId": "unique_id",
      "sourceId": "${request.failingProviderId}",
      "title": "Patch title in Arabic",
      "description": "Clear Arabic explanation of what this fix will do",
      "actionType": "INVALIDATE_CACHE" // One of: UPDATE_TEMPLATE_SELECTORS, UPDATE_BASE_URL, UPDATE_HEADERS, INVALIDATE_CACHE, CHANGE_SOURCE_PRIORITY, DEPRIORITIZE_SERVER
    }
  ]
}
Return only valid JSON.
""".trimIndent()
}

private fun parseAiJsonResponse(
    rawResponse: String,
    providerName: String,
    isRealAi: Boolean,
    request: AiDiagnosticAnalysisRequest,
): AiAnalysisResponse {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    try {
        val root = json.parseToJsonElement(rawResponse).jsonObject

        // Extract raw JSON string from Gemini or OpenAI structure
        val contentString = when {
            root.containsKey("candidates") -> {
                // Gemini format
                val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
                parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
            }
            root.containsKey("choices") -> {
                // OpenAI format
                val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            }
            root.containsKey("summary") -> rawResponse
            else -> ""
        }

        val parsedObj = if (contentString.isNotBlank()) {
            json.parseToJsonElement(contentString).jsonObject
        } else {
            root
        }

        val summary = parsedObj["summary"]?.jsonPrimitive?.content ?: "تم التحليل بنجاح بواسطة $providerName"
        val findingsList = mutableListOf<AiHypothesis>()
        parsedObj["findings"]?.jsonArray?.forEach { fElem ->
            val fObj = fElem.jsonObject
            val title = fObj["title"]?.jsonPrimitive?.content ?: "ملاحظة تشخيصية"
            val conf = fObj["confidence"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0.8f
            val evidence = fObj["verifiedEvidence"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val hyp = fObj["hypothesisExplanation"]?.jsonPrimitive?.content ?: ""
            findingsList.add(AiHypothesis(title, conf, evidence, hyp))
        }

        val patchesList = mutableListOf<AiProposedPatch>()
        parsedObj["proposedPatches"]?.jsonArray?.forEach { pElem ->
            val pObj = pElem.jsonObject
            val id = pObj["patchId"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString().take(8)
            val title = pObj["title"]?.jsonPrimitive?.content ?: "إصلاح مقترح"
            val desc = pObj["description"]?.jsonPrimitive?.content ?: ""
            val actionTypeStr = pObj["actionType"]?.jsonPrimitive?.content ?: "INVALIDATE_CACHE"
            val actionType = try {
                PatchActionType.valueOf(actionTypeStr)
            } catch (_: Exception) {
                PatchActionType.INVALIDATE_CACHE
            }

            patchesList.add(
                AiProposedPatch(
                    patchId = id,
                    sourceId = request.failingProviderId,
                    title = title,
                    description = desc,
                    actionType = actionType,
                )
            )
        }

        return AiAnalysisResponse(
            providerUsed = providerName,
            isRealAiModel = isRealAi,
            findings = findingsList,
            proposedPatches = patchesList,
            summary = summary,
        )
    } catch (e: Exception) {
        Log.e("AI_PARSE", "Failed to parse AI response: ${e.message}")
        // Fallback to local diagnostic on parse failure
        val fallback = LocalDiagnosticAiProvider()
        return kotlinx.coroutines.runBlocking { fallback.analyze(request).getOrThrow() }
    }
}
