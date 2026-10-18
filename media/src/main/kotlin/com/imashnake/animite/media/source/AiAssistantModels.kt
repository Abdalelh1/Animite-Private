package com.imashnake.animite.media.source

import kotlinx.serialization.Serializable

enum class AiProviderType {
    GEMINI,
    OPENAI_COMPATIBLE,
    LOCAL_DIAGNOSTIC,
}

@Serializable
sealed class AiConnectionStatus {
    @Serializable
    data class Connected(val model: String, val latencyMs: Long) : AiConnectionStatus()

    @Serializable
    data class Unconfigured(val message: String = "لم يتم ضبط مفتاح API بعد") : AiConnectionStatus()

    @Serializable
    data class ConnectionError(val errorMessage: String) : AiConnectionStatus()

    @Serializable
    data class LocalFallback(val reason: String = "يعمل بالتشخيص المحلي الافتراضي") : AiConnectionStatus()
}

@Serializable
data class AiDiagnosticAnalysisRequest(
    val animeTitle: String,
    val episodeNumber: Float,
    val failingProviderId: String,
    val totalDiscoveredServers: Int,
    val validDirectStreamsCount: Int,
    val failureReasons: List<String>,
    val serverDetails: List<String>,
    val previouslyFailedFixes: List<String> = emptyList(),
)

@Serializable
data class AiHypothesis(
    val title: String,
    val confidence: Float,
    val verifiedEvidence: List<String>,
    val hypothesisExplanation: String,
)

enum class PatchActionType {
    UPDATE_TEMPLATE_SELECTORS,
    UPDATE_BASE_URL,
    UPDATE_HEADERS,
    INVALIDATE_CACHE,
    CHANGE_SOURCE_PRIORITY,
    DEPRIORITIZE_SERVER,
}

enum class PatchStatus {
    SUGGESTED,
    SANDBOX_TESTING,
    SANDBOX_PASSED,
    SANDBOX_FAILED,
    ACTIVATED,
    ROLLED_BACK,
}

@Serializable
data class AiProposedPatch(
    val patchId: String,
    val sourceId: String,
    val title: String,
    val description: String,
    val actionType: PatchActionType,
    val candidateTemplateConfig: CustomSourceTemplateConfig? = null,
    val candidateBaseUrl: String? = null,
    val candidateHeaders: Map<String, String>? = null,
    val targetServerUrl: String? = null,
    val status: PatchStatus = PatchStatus.SUGGESTED,
    val sandboxTestDetails: String? = null,
    val previousBackupConfig: CustomSourceDefinition? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

@Serializable
data class AiAnalysisResponse(
    val providerUsed: String,
    val isRealAiModel: Boolean,
    val findings: List<AiHypothesis>,
    val proposedPatches: List<AiProposedPatch>,
    val summary: String,
)
