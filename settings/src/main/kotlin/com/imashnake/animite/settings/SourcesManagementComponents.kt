package com.imashnake.animite.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imashnake.animite.core.ui.LocalPaddings
import com.imashnake.animite.media.source.AiAnalysisResponse
import com.imashnake.animite.media.source.AiAssistantRepository
import com.imashnake.animite.media.source.AiConnectionStatus
import com.imashnake.animite.media.source.AiProposedPatch
import com.imashnake.animite.media.source.AiProviderType
import com.imashnake.animite.media.source.AiSourceAssistant
import com.imashnake.animite.media.source.CustomSourceDefinition
import com.imashnake.animite.media.source.CustomSourceTemplateConfig
import com.imashnake.animite.media.source.CustomSourceType
import com.imashnake.animite.media.source.FullSourceTestResult
import com.imashnake.animite.media.source.PatchStatus
import com.imashnake.animite.media.source.SourcePreSaveTester
import com.imashnake.animite.media.source.SourceRepositoryManager
import com.imashnake.animite.media.source.TemplateEpisodeSourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun SourcesSettingsSection(
    isDarkMode: Boolean,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val activeProviders by SourceRepositoryManager.orderedEnabledProviders.collectAsState()
    val customSources by SourceRepositoryManager.customSources.collectAsState()
    val allProviders = SourceRepositoryManager.getAllProviders(context)
    val displayNames = SourceRepositoryManager.getAllDisplayNames(context)
    val aiStatus by AiAssistantRepository.connectionStatus.collectAsState()

    var showAddSourceDialog by remember { mutableStateOf(false) }
    var showAiConfigDialog by remember { mutableStateOf(false) }
    var showAiDiagnosisDialog by remember { mutableStateOf(false) }
    var diagnosticSummary by remember { mutableStateOf<String?>(null) }
    var isRunningDiagnostic by remember { mutableStateOf(false) }

    val bgColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceContainer
    else MaterialTheme.colorScheme.surface

    Column(
        verticalArrangement = Arrangement.spacedBy(LocalPaddings.current.medium),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "مصادر التشغيل (Source Manager)",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = { showAddSourceDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("إضافة مصدر", fontSize = 12.sp)
            }
        }

        // ── Provider Cards with Reordering & Badges ─────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(LocalPaddings.current.tiny)) {
            allProviders.forEachIndexed { index, provider ->
                val isEnabled = activeProviders.any { it.id == provider.id }
                val isCustom = customSources.any { it.id == provider.id }
                val isCloudflare = provider.id == "animeslayer"
                val hasDirect1080p = provider.id == "anime3rb" || provider.id == "anime4up" || provider.id == "animeluxe"

                val topRadius = if (index == 0) LocalPaddings.current.large else LocalPaddings.current.tiny
                val bottomRadius = if (index == allProviders.lastIndex) LocalPaddings.current.large else LocalPaddings.current.tiny
                val shape = RoundedCornerShape(
                    topStart = topRadius, topEnd = topRadius,
                    bottomEnd = bottomRadius, bottomStart = bottomRadius,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(bgColor)
                        .padding(horizontal = LocalPaddings.current.large, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayNames[provider.id] ?: provider.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            // Badge 1: Source Type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isCustom) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isCustom) "قالب مخصص" else "محول رسمي",
                                    fontSize = 10.sp,
                                    color = if (isCustom) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }

                            // Badge 2: Capabilities
                            if (hasDirect1080p) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1B5E20))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("مباشر 1080p", fontSize = 10.sp, color = Color.White)
                                }
                            }

                            if (isCloudflare) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE65100))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("محمي بـ Cloudflare", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // Priority reordering & Actions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) {
                            IconButton(
                                onClick = {
                                    val currentOrder = SourceRepositoryManager.getProviderOrder(context).toMutableList()
                                    val temp = currentOrder[index]
                                    currentOrder[index] = currentOrder[index - 1]
                                    currentOrder[index - 1] = temp
                                    SourceRepositoryManager.setProviderOrder(context, currentOrder)
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Text("▲", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            }
                        }

                        if (index < allProviders.lastIndex) {
                            IconButton(
                                onClick = {
                                    val currentOrder = SourceRepositoryManager.getProviderOrder(context).toMutableList()
                                    val temp = currentOrder[index]
                                    currentOrder[index] = currentOrder[index + 1]
                                    currentOrder[index + 1] = temp
                                    SourceRepositoryManager.setProviderOrder(context, currentOrder)
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Text("▼", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            }
                        }

                        if (isCustom) {
                            IconButton(
                                onClick = {
                                    SourceRepositoryManager.deleteCustomSource(context, provider.id)
                                    Toast.makeText(context, "تم حذف المصدر المخصص", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                haptic.performHapticFeedback(
                                    if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                                )
                                SourceRepositoryManager.setEnabled(context, provider.id, enabled)
                            },
                            thumbContent = {
                                if (isEnabled) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        // ── Real AI Source Assistant Section ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalPaddings.current.large))
                .background(bgColor)
                .padding(LocalPaddings.current.large),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LocalPaddings.current.small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "🤖 المساعد الذكي لمصادر البث",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = { showAiConfigDialog = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "إعدادات AI", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // AI Status Indicator
                val statusText = when (val s = aiStatus) {
                    is AiConnectionStatus.Connected -> "🟢 متصل: ${s.model}"
                    is AiConnectionStatus.LocalFallback -> "🟡 تشخيص محلي (بدون مفتاح API خارجي)"
                    is AiConnectionStatus.Unconfigured -> "⚪ غير مهيأ (اضغط الإعدادات لضبط Gemini/OpenAI)"
                    is AiConnectionStatus.ConnectionError -> "🔴 خطأ اتصال: ${s.errorMessage}"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(LocalPaddings.current.small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { showAiDiagnosisDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                    ) {
                        Text("تحليل الأعطال والحلول", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            isRunningDiagnostic = true
                            scope.launch {
                                val summary = withContext(Dispatchers.Default) {
                                    AiSourceAssistant.generateSessionSummary()
                                }
                                diagnosticSummary = summary
                                isRunningDiagnostic = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                    ) {
                        if (isRunningDiagnostic) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("تقرير الجلسة", fontSize = 12.sp)
                        }
                    }
                }

                AnimatedVisibility(visible = diagnosticSummary != null) {
                    val bgDiag = if (isDarkMode) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgDiag)
                            .padding(LocalPaddings.current.medium)
                            .clickable { diagnosticSummary = null },
                    ) {
                        Text(
                            text = diagnosticSummary ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // ── Reset to defaults button ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(bgColor)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SourceRepositoryManager.resetToDefaults(context)
                    Toast.makeText(context, "تمت إعادة ضبط جميع المصادر إلى الحالة الافتراضية", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = LocalPaddings.current.large, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "إعادة ضبط المصادر إلى الافتراضي",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (showAddSourceDialog) {
        AddCustomSourceDialog(
            context = context,
            onDismiss = { showAddSourceDialog = false },
            onSourceAdded = {
                showAddSourceDialog = false
                Toast.makeText(context, "تم حفظ المصدر بنجاح!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAiConfigDialog) {
        AiConfigDialog(
            context = context,
            onDismiss = { showAiConfigDialog = false }
        )
    }

    if (showAiDiagnosisDialog) {
        AiDiagnosisDialog(
            context = context,
            onDismiss = { showAiDiagnosisDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Custom Source Dialog (with 5-Stage Pre-Save Tester)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AddCustomSourceDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSourceAdded: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://") }
    var sourceType by remember { mutableStateOf(CustomSourceType.TEMPLATE) }
    var searchUrlPattern by remember { mutableStateOf("{baseUrl}/search?q={query}") }
    var episodeListSelector by remember { mutableStateOf("a[href*='/episode/']") }
    var serverItemSelector by remember { mutableStateOf("iframe") }
    var serverUrlAttribute by remember { mutableStateOf("src") }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<FullSourceTestResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مصدر أنمي جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "أدخل بيانات الموقع وقم بفحصها قبل الحفظ لضمان استقرار التشغيل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المصدر (مثال: Anime World)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("عنوان الموقع (HTTPS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Template Configuration
                Text("معايير الاستخراج (Template Config):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

                OutlinedTextField(
                    value = searchUrlPattern,
                    onValueChange = { searchUrlPattern = it },
                    label = { Text("نمط رابط البحث") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = episodeListSelector,
                    onValueChange = { episodeListSelector = it },
                    label = { Text("محدد قائمة الحلقات (CSS Selector)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = serverItemSelector,
                    onValueChange = { serverItemSelector = it },
                    label = { Text("محدد السيرفرات أو المشغل (CSS Selector)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = serverUrlAttribute,
                    onValueChange = { serverUrlAttribute = it },
                    label = { Text("خاصية الرابط (src / data-url / href)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 5-Stage Test Button
                Button(
                    onClick = {
                        isTesting = true
                        errorMessage = null
                        scope.launch {
                            val candidateDef = CustomSourceDefinition(
                                id = "custom_${name.lowercase().replace(Regex("[^a-z0-9]"), "_").ifBlank { UUID.randomUUID().toString().take(6) }}",
                                name = name.ifBlank { "Custom Source" },
                                baseUrl = baseUrl.trim(),
                                sourceType = CustomSourceType.TEMPLATE,
                                templateConfig = CustomSourceTemplateConfig(
                                    searchUrlPattern = searchUrlPattern,
                                    episodeListSelector = episodeListSelector,
                                    serverItemSelector = serverItemSelector,
                                    serverUrlAttribute = serverUrlAttribute,
                                )
                            )
                            val provider = TemplateEpisodeSourceProvider(candidateDef)
                            val res = SourcePreSaveTester.testSource(provider, "One Piece")
                            testResult = res
                            isTesting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ الفحص عبر 5 مراحل...")
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("فحص المصدر (5 مراحل)")
                    }
                }

                // 5-Stage Test Results Breakdown
                testResult?.let { res ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (res.overallSuccess) "✅ نجح الفحص الشامل وجاهز للحفظ!" else "⚠️ المصدر لم يكتمل: تحقق من المراحل الفاشلة أدناه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (res.overallSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        )
                        StageResultRow(res.connectivityStage)
                        StageResultRow(res.searchStage)
                        StageResultRow(res.episodeStage)
                        StageResultRow(res.serverStage)
                        StageResultRow(res.streamStage)
                    }
                }

                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val candidateDef = CustomSourceDefinition(
                        id = "custom_${name.lowercase().replace(Regex("[^a-z0-9]"), "_").ifBlank { UUID.randomUUID().toString().take(6) }}",
                        name = name.trim(),
                        baseUrl = baseUrl.trim(),
                        sourceType = sourceType,
                        templateConfig = CustomSourceTemplateConfig(
                            searchUrlPattern = searchUrlPattern,
                            episodeListSelector = episodeListSelector,
                            serverItemSelector = serverItemSelector,
                            serverUrlAttribute = serverUrlAttribute,
                        )
                    )
                    val result = SourceRepositoryManager.addCustomSource(context, candidateDef)
                    result.onSuccess {
                        onSourceAdded()
                    }.onFailure {
                        errorMessage = it.message
                    }
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && !isTesting,
            ) {
                Text("حفظ وتفعيل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun StageResultRow(stage: com.imashnake.animite.media.source.SourceStageTestResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${if (stage.isSuccess) "✅" else "❌"} ${stage.stageName}: ${stage.message}",
            fontSize = 11.sp,
            color = if (stage.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (stage.latencyMs > 0) {
            Text("${stage.latencyMs}ms", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Configuration Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiConfigDialog(
    context: Context,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val saved = AiAssistantRepository.getSavedConfig(context)

    var selectedType by remember {
        mutableStateOf(try { AiProviderType.valueOf(saved["type"] ?: "LOCAL_DIAGNOSTIC") } catch (_: Exception) { AiProviderType.LOCAL_DIAGNOSTIC })
    }
    var geminiKey by remember { mutableStateOf(saved["geminiKey"] ?: "") }
    var geminiModel by remember { mutableStateOf(saved["geminiModel"] ?: "gemini-2.0-flash") }
    var openaiKey by remember { mutableStateOf(saved["openaiKey"] ?: "") }
    var openaiBaseUrl by remember { mutableStateOf(saved["openaiBaseUrl"] ?: "https://api.openai.com/v1") }
    var openaiModel by remember { mutableStateOf(saved["openaiModel"] ?: "gpt-4o-mini") }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTestingConn by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات نموذج الذكاء الاصطناعي (AI)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "اختر مزود الذكاء الاصطناعي لتحليل أعطال المصادر واقتراح التعديلات البرمجية.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // Provider Selector Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AiProviderType.entries.forEach { pType ->
                        val isSel = selectedType == pType
                        OutlinedButton(
                            onClick = { selectedType = pType },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Text(
                                text = when (pType) {
                                    AiProviderType.GEMINI -> "Gemini"
                                    AiProviderType.OPENAI_COMPATIBLE -> "OpenAI"
                                    AiProviderType.LOCAL_DIAGNOSTIC -> "محلي"
                                },
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Dynamic Fields
                when (selectedType) {
                    AiProviderType.GEMINI -> {
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { geminiKey = it },
                            label = { Text("Google Gemini API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = { geminiModel = it },
                            label = { Text("اسم النموذج (gemini-2.0-flash / gemini-1.5-flash)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Quick selection chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf("gemini-2.0-flash", "gemini-1.5-flash-latest", "gemini-2.5-flash", "gemini-1.5-flash").forEach { chipModel ->
                                OutlinedButton(
                                    onClick = { geminiModel = chipModel },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Text(chipModel.removePrefix("gemini-"), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    AiProviderType.OPENAI_COMPATIBLE -> {
                        OutlinedTextField(
                            value = openaiKey,
                            onValueChange = { openaiKey = it },
                            label = { Text("API Key (OpenAI / DeepSeek / Groq)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = openaiBaseUrl,
                            onValueChange = { openaiBaseUrl = it },
                            label = { Text("Base URL (e.g. https://api.deepseek.com/v1)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = openaiModel,
                            onValueChange = { openaiModel = it },
                            label = { Text("اسم النموذج (gpt-4o-mini / deepseek-chat)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AiProviderType.LOCAL_DIAGNOSTIC -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "💡 يعمل بنظام التشخيص المحلي المستقل المبني على قواعد استدلالية دقيقة بدون الحاجة لمفاتيح خارجية أو اتصال بالإنترنت.",
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                // Test Connection Button
                OutlinedButton(
                    onClick = {
                        isTestingConn = true
                        testStatus = null
                        scope.launch {
                            AiAssistantRepository.saveConfig(
                                context = context,
                                type = selectedType,
                                geminiKey = geminiKey,
                                geminiModel = geminiModel,
                                openaiKey = openaiKey,
                                openaiBaseUrl = openaiBaseUrl,
                                openaiModel = openaiModel,
                            )
                            val (_, msg) = AiAssistantRepository.testActiveConnection(context)
                            testStatus = msg
                            val updatedSaved = AiAssistantRepository.getSavedConfig(context)
                            updatedSaved["geminiModel"]?.let {
                                if (it.isNotBlank()) geminiModel = it
                            }
                            isTestingConn = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isTestingConn) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("جارٍ التحقق...")
                    } else {
                        Text("اختبار الاتصال")
                    }
                }

                testStatus?.let {
                    Text(text = it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    AiAssistantRepository.saveConfig(
                        context = context,
                        type = selectedType,
                        geminiKey = geminiKey,
                        geminiModel = geminiModel,
                        openaiKey = openaiKey,
                        openaiBaseUrl = openaiBaseUrl,
                        openaiModel = openaiModel,
                    )
                    Toast.makeText(context, "تم حفظ إعدادات الذكاء الاصطناعي", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Diagnosis & Sandbox Patch Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiDiagnosisDialog(
    context: Context,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResponse by remember { mutableStateOf<AiAnalysisResponse?>(null) }
    var selectedEpisode by remember { mutableStateOf("1") }
    var selectedAnimeTitle by remember { mutableStateOf("One Piece") }
    var patchFeedback by remember { mutableStateOf<String?>(null) }

    val activePatches by AiAssistantRepository.activePatches.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تشخيص الأعطال واقتراح الحلول (AI)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = selectedAnimeTitle,
                        onValueChange = { selectedAnimeTitle = it },
                        label = { Text("عنوان الأنمي") },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = selectedEpisode,
                        onValueChange = { selectedEpisode = it },
                        label = { Text("الحلقة") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                Button(
                    onClick = {
                        isAnalyzing = true
                        patchFeedback = null
                        scope.launch {
                            val epNum = selectedEpisode.toFloatOrNull() ?: 1f
                            val res = AiAssistantRepository.analyzeEpisodeFailure(
                                context = context,
                                episodeNumber = epNum,
                                animeTitle = selectedAnimeTitle,
                                failingProviderId = "anime3rb",
                            )
                            res.onSuccess {
                                analysisResponse = it
                            }.onFailure {
                                patchFeedback = "فشل التحليل: ${it.message}"
                            }
                            isAnalyzing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("جارٍ تحليل السجلات...")
                    } else {
                        Text("بدء التحليل الذكي")
                    }
                }

                // Findings
                analysisResponse?.let { resp ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "المزوّد: ${resp.providerUsed} (${if (resp.isRealAiModel) "نموذج AI سحابي" else "محرك محلي"})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(text = resp.summary, fontSize = 12.sp)
                        }
                    }

                    // Verified Evidence vs Hypothesis
                    if (resp.findings.isNotEmpty()) {
                        Text("الأدلة المؤكدة مقابل الفرضيات:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        resp.findings.forEach { f ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(text = "🔍 ${f.title} (ثقة: ${(f.confidence * 100).toInt()}%)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                if (f.verifiedEvidence.isNotEmpty()) {
                                    Text(text = "• دليل مؤكد: ${f.verifiedEvidence.joinToString("، ")}", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                }
                                Text(text = "• الفرضية: ${f.hypothesisExplanation}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Proposed Patches with Sandbox & Rollback
                    if (activePatches.isNotEmpty()) {
                        Text("الإصلاحات المقترحة (معزولة وقابلة للتراجع):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        activePatches.forEach { patch ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "🛠️ ${patch.title}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(text = patch.description, fontSize = 11.sp)

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val (pass, details) = AiAssistantRepository.testPatchInSandbox(context, patch, selectedAnimeTitle)
                                                patchFeedback = details
                                            }
                                        },
                                        shape = CircleShape,
                                    ) {
                                        Text("اختبار معزول", fontSize = 10.sp)
                                    }

                                    Button(
                                        onClick = {
                                            AiAssistantRepository.applyPatch(context, patch, selectedAnimeTitle)
                                            patchFeedback = "تم تطبيق الإصلاح وحفظ نقطة استعادة!"
                                        },
                                        shape = CircleShape,
                                    ) {
                                        Text("تطبيق", fontSize = 10.sp)
                                    }

                                    if (patch.status == PatchStatus.ACTIVATED) {
                                        TextButton(
                                            onClick = {
                                                AiAssistantRepository.rollbackPatch(context, patch)
                                                patchFeedback = "تم التراجع إلى الإعداد السابق بنجاح!"
                                            }
                                        ) {
                                            Text("تراجع", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                patchFeedback?.let {
                    Text(text = it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
