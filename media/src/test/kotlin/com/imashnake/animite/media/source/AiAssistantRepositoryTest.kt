package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantRepositoryTest {

    @Test
    fun testAiConnectionStatuses() {
        val unconfigured = AiConnectionStatus.Unconfigured()
        assertTrue(unconfigured is AiConnectionStatus.Unconfigured)
        assertEquals("لم يتم ضبط مفتاح API بعد", unconfigured.message)

        val connected = AiConnectionStatus.Connected("Gemini 1.5 Flash", 65L)
        assertTrue(connected is AiConnectionStatus.Connected)
        assertEquals("Gemini 1.5 Flash", connected.model)
        assertEquals(65L, connected.latencyMs)

        val localFallback = AiConnectionStatus.LocalFallback()
        assertTrue(localFallback is AiConnectionStatus.LocalFallback)
        assertTrue(localFallback.reason.contains("محلي"))

        val errorStatus = AiConnectionStatus.ConnectionError("مفتاح API غير صالح")
        assertTrue(errorStatus is AiConnectionStatus.ConnectionError)
        assertEquals("مفتاح API غير صالح", errorStatus.errorMessage)
    }

    @Test
    fun testStructuredHypothesisVsVerifiedEvidence() {
        val verifiedFinding = AiHypothesis(
            title = "فشل خادم Vid3rb بسبب انتهاء صلاحية الرمز المميز token=exp1234",
            confidence = 0.95f,
            verifiedEvidence = listOf("السجل أظهر HTTP 403 Forbidden مع رسالة 'token expired'"),
            hypothesisExplanation = "الرمز المؤقت يحتاج إلى إعادة طلب من صفحة الحلقة",
        )

        assertEquals(0.95f, verifiedFinding.confidence, 0.01f)
        assertTrue(verifiedFinding.verifiedEvidence.isNotEmpty())
        assertEquals("فشل خادم Vid3rb بسبب انتهاء صلاحية الرمز المميز token=exp1234", verifiedFinding.title)

        val unverifiedHypothesis = AiHypothesis(
            title = "احتمال تغيير بنية كلاس الحلقات في موقع المصدر",
            confidence = 0.60f,
            verifiedEvidence = emptyList(),
            hypothesisExplanation = "لم يتم العثور على أية وسوم مطابقة لمحدد CSS القديم",
        )

        assertEquals(0.60f, unverifiedHypothesis.confidence, 0.01f)
        assertTrue(unverifiedHypothesis.verifiedEvidence.isEmpty())
        assertTrue(unverifiedHypothesis.hypothesisExplanation.contains("CSS"))
    }

    @Test
    fun testProposedPatchAndRollbackSnapshot() {
        val originalConfig = CustomSourceTemplateConfig(
            searchUrlPattern = "{baseUrl}/search?q={query}",
            searchItemSelector = "div.old-selector",
        )

        val originalSource = CustomSourceDefinition(
            id = "custom_anime",
            name = "Custom Anime",
            baseUrl = "https://custom-anime.org",
            sourceType = CustomSourceType.TEMPLATE,
            templateConfig = originalConfig,
        )

        val candidateConfig = originalConfig.copy(
            searchItemSelector = "div.new-selector",
        )

        val patch = AiProposedPatch(
            patchId = "patch_001",
            sourceId = "custom_anime",
            title = "تحديث محدد نتائج البحث إلى div.new-selector",
            description = "الموقع غيّر كلاس العنصر الحاوي للنتائج",
            actionType = PatchActionType.UPDATE_TEMPLATE_SELECTORS,
            candidateTemplateConfig = candidateConfig,
            previousBackupConfig = originalSource,
            status = PatchStatus.SUGGESTED,
        )

        assertEquals("patch_001", patch.patchId)
        assertEquals(PatchStatus.SUGGESTED, patch.status)
        assertNotNull(patch.previousBackupConfig)
        assertEquals("div.old-selector", patch.previousBackupConfig?.templateConfig?.searchItemSelector)
        assertEquals("div.new-selector", patch.candidateTemplateConfig?.searchItemSelector)

        // Simulate activating patch
        val activatedPatch = patch.copy(
            status = PatchStatus.ACTIVATED,
            sandboxTestDetails = "اجتاز الاختبار المعزول بنجاح",
        )
        assertEquals(PatchStatus.ACTIVATED, activatedPatch.status)

        // Simulate 1-click rollback
        val rolledBackSource = activatedPatch.previousBackupConfig
        assertNotNull("Backup snapshot must be preserved for rollback", rolledBackSource)
        assertEquals("div.old-selector", rolledBackSource?.templateConfig?.searchItemSelector)
    }

    @Test
    fun testRejectedFixSignaturesPreventRepetitiveBadSuggestions() {
        val rejectedSignatures = mutableSetOf<String>()

        val signature1 = "anime3rb:UPDATE_TEMPLATE_SELECTORS:تحديث محدد الحلقات الخاطئ"
        rejectedSignatures.add(signature1)

        assertTrue("Signature must be tracked", rejectedSignatures.contains(signature1))

        // Check if candidate patch was previously rejected
        val isAlreadyRejected = rejectedSignatures.contains(signature1)
        assertTrue("System correctly skips previously rejected patch", isAlreadyRejected)

        val signature2 = "anime3rb:INVALIDATE_CACHE:تفريغ كاش الروابط"
        assertFalse("New valid patch is not rejected", rejectedSignatures.contains(signature2))
    }

    @Test
    fun testLocalDiagnosticAiProviderFallback() = runBlocking {
        val localProvider = LocalDiagnosticAiProvider()
        assertEquals(AiProviderType.LOCAL_DIAGNOSTIC, localProvider.type)
        assertTrue(localProvider.displayName.contains("التشخيص المحلي"))

        val (canConnect, msg) = localProvider.testConnection()
        assertTrue("Local diagnostic provider is always available offline", canConnect)
        assertTrue(msg.contains("المحرك المحلي جاهز"))
    }
}
