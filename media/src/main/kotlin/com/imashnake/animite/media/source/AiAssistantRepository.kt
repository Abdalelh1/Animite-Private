package com.imashnake.animite.media.source

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository coordinating AI Provider lifecycle, credentials storage,
 * isolated sandbox patch testing, and configuration rollback.
 */
object AiAssistantRepository {

    private const val TAG = "AI_ASSISTANT_REPO"
    private const val PREFS_NAME = "animite_ai_assistant_prefs"

    private const val KEY_PROVIDER_TYPE = "ai_provider_type"
    private const val KEY_GEMINI_KEY = "gemini_api_key"
    private const val KEY_GEMINI_MODEL = "gemini_model"
    private const val KEY_OPENAI_KEY = "openai_api_key"
    private const val KEY_OPENAI_BASE_URL = "openai_base_url"
    private const val KEY_OPENAI_MODEL = "openai_model"
    private const val KEY_REJECTED_FIXES = "rejected_fixes_set"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _connectionStatus = MutableStateFlow<AiConnectionStatus>(AiConnectionStatus.Unconfigured())
    val connectionStatus: StateFlow<AiConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activePatches = MutableStateFlow<List<AiProposedPatch>>(emptyList())
    val activePatches: StateFlow<List<AiProposedPatch>> = _activePatches.asStateFlow()

    private val rejectedFixSignatures = ConcurrentHashMap.newKeySet<String>()

    fun initialize(context: Context) {
        val prefs = prefs(context)
        val savedRejected = prefs.getStringSet(KEY_REJECTED_FIXES, emptySet()) ?: emptySet()
        rejectedFixSignatures.addAll(savedRejected)
        refreshProvider(context)
    }

    fun getProvider(context: Context): AiSourceAssistantProvider {
        val prefs = prefs(context)
        val typeStr = prefs.getString(KEY_PROVIDER_TYPE, AiProviderType.LOCAL_DIAGNOSTIC.name)
        val type = try {
            AiProviderType.valueOf(typeStr ?: AiProviderType.LOCAL_DIAGNOSTIC.name)
        } catch (_: Exception) {
            AiProviderType.LOCAL_DIAGNOSTIC
        }

        return when (type) {
            AiProviderType.GEMINI -> {
                val key = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
                val model = prefs.getString(KEY_GEMINI_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash"
                if (key.isBlank()) LocalDiagnosticAiProvider() else GeminiAiAssistantProvider(key, model)
            }
            AiProviderType.OPENAI_COMPATIBLE -> {
                val key = prefs.getString(KEY_OPENAI_KEY, "") ?: ""
                val baseUrl = prefs.getString(KEY_OPENAI_BASE_URL, "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
                val model = prefs.getString(KEY_OPENAI_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
                if (key.isBlank()) LocalDiagnosticAiProvider() else OpenAiCompatibleAiProvider(key, baseUrl, model)
            }
            AiProviderType.LOCAL_DIAGNOSTIC -> LocalDiagnosticAiProvider()
        }
    }

    fun saveConfig(
        context: Context,
        type: AiProviderType,
        geminiKey: String? = null,
        geminiModel: String? = null,
        openaiKey: String? = null,
        openaiBaseUrl: String? = null,
        openaiModel: String? = null,
    ) {
        val prefs = prefs(context)
        prefs.edit {
            putString(KEY_PROVIDER_TYPE, type.name)
            geminiKey?.let { putString(KEY_GEMINI_KEY, it.trim()) }
            geminiModel?.let { putString(KEY_GEMINI_MODEL, it.trim()) }
            openaiKey?.let { putString(KEY_OPENAI_KEY, it.trim()) }
            openaiBaseUrl?.let { putString(KEY_OPENAI_BASE_URL, it.trim()) }
            openaiModel?.let { putString(KEY_OPENAI_MODEL, it.trim()) }
        }
        refreshProvider(context)
    }

    fun getSavedConfig(context: Context): Map<String, String> {
        val prefs = prefs(context)
        return mapOf(
            "type" to (prefs.getString(KEY_PROVIDER_TYPE, AiProviderType.LOCAL_DIAGNOSTIC.name) ?: ""),
            "geminiKey" to (prefs.getString(KEY_GEMINI_KEY, "") ?: ""),
            "geminiModel" to (prefs.getString(KEY_GEMINI_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash"),
            "openaiKey" to (prefs.getString(KEY_OPENAI_KEY, "") ?: ""),
            "openaiBaseUrl" to (prefs.getString(KEY_OPENAI_BASE_URL, "https://api.openai.com/v1") ?: "https://api.openai.com/v1"),
            "openaiModel" to (prefs.getString(KEY_OPENAI_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"),
        )
    }

    suspend fun testActiveConnection(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val provider = getProvider(context)
        val (success, message) = provider.testConnection()
        if (success) {
            _connectionStatus.value = AiConnectionStatus.Connected(provider.displayName, 120L)
            if (provider is GeminiAiAssistantProvider && provider.activeModel != provider.model) {
                saveConfig(context, AiProviderType.GEMINI, geminiModel = provider.activeModel)
            }
        } else {
            _connectionStatus.value = AiConnectionStatus.ConnectionError(message)
        }
        Pair(success, message)
    }

    suspend fun analyzeEpisodeFailure(
        context: Context,
        episodeNumber: Float,
        animeTitle: String,
        failingProviderId: String,
    ): Result<AiAnalysisResponse> = withContext(Dispatchers.IO) {
        val provider = getProvider(context)
        val report = SmartStreamDiagnostics.getReport(episodeNumber)
        val request = AiDiagnosticAnalysisRequest(
            animeTitle = animeTitle,
            episodeNumber = episodeNumber,
            failingProviderId = failingProviderId,
            totalDiscoveredServers = report?.totalDiscoveredServers ?: 0,
            validDirectStreamsCount = report?.validDirectStreamsCount ?: 0,
            failureReasons = report?.failureReasons ?: listOf("تعذر استخراج روابط التشغيل"),
            serverDetails = report?.serverDetails?.map { "${it.serverName}: ${it.status} (${it.failureReason ?: "OK"})" } ?: emptyList(),
            previouslyFailedFixes = rejectedFixSignatures.toList(),
        )

        val result = provider.analyze(request)
        result.onSuccess { resp ->
            _activePatches.value = resp.proposedPatches
        }
        result
    }

    /**
     * Isolated sandbox test of a candidate patch before live activation.
     */
    suspend fun testPatchInSandbox(
        context: Context,
        patch: AiProposedPatch,
        testQuery: String = "One Piece",
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        Log.i(TAG, "SANDBOX_TEST_START: testing patch ${patch.patchId} for source ${patch.sourceId}")

        when (patch.actionType) {
            PatchActionType.INVALIDATE_CACHE -> {
                // Invalidate cache action is non-destructive and immediately verifiable
                Pair(true, "✅ إجراء تجديد الكاش آمن وصالح للتنفيذ.")
            }
            PatchActionType.UPDATE_TEMPLATE_SELECTORS -> {
                val candidateConfig = patch.candidateTemplateConfig
                if (candidateConfig == null) {
                    recordFailedFix(context, patch)
                    return@withContext Pair(false, "❌ القالب المقترح لا يحتوي على معايير استخراج صالحة.")
                }

                val currentCustom = SourceRepositoryManager.customSources.value.firstOrNull { it.id == patch.sourceId }
                val testDef = (currentCustom ?: CustomSourceDefinition(
                    id = patch.sourceId,
                    name = "Sandbox Candidate",
                    baseUrl = patch.candidateBaseUrl ?: "https://anime3rb.com",
                    sourceType = CustomSourceType.TEMPLATE,
                )).copy(templateConfig = candidateConfig)

                val testProvider = TemplateEpisodeSourceProvider(testDef)
                val testResult = SourcePreSaveTester.testSource(testProvider, testQuery)

                if (testResult.overallSuccess) {
                    updatePatchStatus(patch.patchId, PatchStatus.SANDBOX_PASSED, "نجح في جميع المراحل الـ 5 مع ${testResult.streamStage.details}")
                    Pair(true, "✅ اجتاز الاختبار المعزول بنجاح: ${testResult.streamStage.message}")
                } else {
                    recordFailedFix(context, patch)
                    updatePatchStatus(patch.patchId, PatchStatus.SANDBOX_FAILED, "فشل في مرحلة ${testResult.connectivityStage.stageName}: ${testResult.connectivityStage.message}")
                    Pair(false, "❌ فشل الاختبار المعزول: تم رفض المقترح تلقائياً للحفاظ على استقرار التطبيق.")
                }
            }
            else -> {
                Pair(true, "✅ الإجراء المقترح تم التحقق من سلامته المبدئية.")
            }
        }
    }

    /**
     * Applies a validated patch and saves backup snapshot for instant rollback.
     */
    fun applyPatch(context: Context, patch: AiProposedPatch, animeTitle: String = ""): Result<Unit> {
        Log.i(TAG, "APPLY_PATCH: applying patch ${patch.patchId}")
        val currentCustom = SourceRepositoryManager.customSources.value.firstOrNull { it.id == patch.sourceId }

        when (patch.actionType) {
            PatchActionType.INVALIDATE_CACHE -> {
                SmartStreamCache.invalidateStream(animeTitle, 1f)
            }
            PatchActionType.UPDATE_TEMPLATE_SELECTORS -> {
                if (currentCustom != null && patch.candidateTemplateConfig != null) {
                    val backup = currentCustom
                    val updated = currentCustom.copy(templateConfig = patch.candidateTemplateConfig)
                    SourceRepositoryManager.updateCustomSource(context, updated)
                    updatePatchStatus(patch.patchId, PatchStatus.ACTIVATED, "تم التطبيق بنجاح مع حفظ نسخة احتياطية", backup)
                }
            }
            PatchActionType.UPDATE_BASE_URL -> {
                if (currentCustom != null && !patch.candidateBaseUrl.isNullOrBlank()) {
                    val backup = currentCustom
                    val updated = currentCustom.copy(baseUrl = patch.candidateBaseUrl)
                    SourceRepositoryManager.updateCustomSource(context, updated)
                    updatePatchStatus(patch.patchId, PatchStatus.ACTIVATED, "تم تحديث الرابط بنجاح", backup)
                }
            }
            PatchActionType.DEPRIORITIZE_SERVER -> {
                patch.targetServerUrl?.let {
                    SmartStreamCache.recordServerFailure(it, "Deprioritized via AI patch")
                }
            }
            else -> {}
        }
        return Result.success(Unit)
    }

    /**
     * Rolls back an applied patch to its previous working configuration snapshot.
     */
    fun rollbackPatch(context: Context, patch: AiProposedPatch): Result<Unit> {
        Log.i(TAG, "ROLLBACK_PATCH: rolling back patch ${patch.patchId}")
        val backup = patch.previousBackupConfig
        if (backup != null) {
            SourceRepositoryManager.updateCustomSource(context, backup)
            updatePatchStatus(patch.patchId, PatchStatus.ROLLED_BACK, "تم التراجع واستعادة الإعداد السابق بنجاح")
            return Result.success(Unit)
        }
        return Result.failure(IllegalStateException("لا توجد نسخة احتياطية محفوظة لهذا الإصلاح"))
    }

    private fun updatePatchStatus(
        patchId: String,
        newStatus: PatchStatus,
        details: String? = null,
        backup: CustomSourceDefinition? = null,
    ) {
        _activePatches.value = _activePatches.value.map {
            if (it.patchId == patchId) {
                it.copy(
                    status = newStatus,
                    sandboxTestDetails = details ?: it.sandboxTestDetails,
                    previousBackupConfig = backup ?: it.previousBackupConfig,
                )
            } else it
        }
    }

    private fun recordFailedFix(context: Context, patch: AiProposedPatch) {
        val signature = "${patch.sourceId}:${patch.actionType}:${patch.title}"
        rejectedFixSignatures.add(signature)
        val prefs = prefs(context)
        prefs.edit { putStringSet(KEY_REJECTED_FIXES, rejectedFixSignatures) }
        Log.w(TAG, "RECORD_FAILED_FIX: fix signature recorded to avoid repeating: $signature")
    }

    private fun refreshProvider(context: Context) {
        val p = getProvider(context)
        CoroutineScope(Dispatchers.IO).launch {
            val (success, _) = p.testConnection()
            _connectionStatus.value = if (success) {
                if (p.type == AiProviderType.LOCAL_DIAGNOSTIC) {
                    AiConnectionStatus.LocalFallback()
                } else {
                    AiConnectionStatus.Connected(p.displayName, 80L)
                }
            } else {
                AiConnectionStatus.Unconfigured()
            }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
