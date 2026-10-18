package com.imashnake.animite.media.source

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * SourceRepositoryManager — Persistent, runtime-configurable provider registry.
 *
 * Responsibilities:
 * - Manages both built-in dedicated adapters and user-created template sources.
 * - Saves enabled/disabled states, priorities, and custom sources in SharedPreferences.
 * - Prevents duplicate registrations and validates URLs and configuration.
 * - Exposes a reactive ordered list of enabled providers to EpisodeSourcesManager.
 * - Preserves security: untrusted code or arbitrary scripts are strictly forbidden.
 */
object SourceRepositoryManager {

    private const val TAG = "SOURCE_REPO_MGR"
    private const val PREFS_NAME = "animite_source_prefs"
    private const val KEY_ENABLED_PREFIX = "provider_enabled_"
    private const val KEY_ORDER = "provider_order"
    private const val KEY_CUSTOM_SOURCES = "custom_sources_json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /** All built-in providers in default priority order */
    val allBuiltInProviders: List<EpisodeSourceProvider> = listOf(
        Anime4upProvider(),
        Anime3rbProvider(),
        WitAnimeProvider(),
        AnimeLuxeProvider(),
        AnimeSlayerProvider(),
    )

    private val _customSources = MutableStateFlow<List<CustomSourceDefinition>>(emptyList())
    val customSources: StateFlow<List<CustomSourceDefinition>> = _customSources.asStateFlow()

    private val _orderedEnabledProviders = MutableStateFlow(allBuiltInProviders)
    /** Reactive list of currently enabled providers in user-defined order */
    val orderedEnabledProviders: StateFlow<List<EpisodeSourceProvider>> =
        _orderedEnabledProviders.asStateFlow()

    /** Human-readable display labels (Arabic) for built-in providers */
    val builtInDisplayNames: Map<String, String> = mapOf(
        "anime4up"    to "Anime4Up (المصدر 1)",
        "anime3rb"    to "Anime3Rb (المصدر 2)",
        "witanime"    to "WitAnime (المصدر 3)",
        "animeluxe"   to "AnimeLuxe (المصدر 4)",
        "animeslayer" to "AnimeSlayer (المصدر 5)",
    )
    val providerDisplayNames: Map<String, String> get() = builtInDisplayNames

    /** Initialize from SharedPreferences — call once at Application start */
    fun initialize(context: Context) {
        val prefs = prefs(context)
        loadCustomSources(prefs)
        _orderedEnabledProviders.value = buildOrderedEnabledList(prefs)
        Log.i(TAG, "INIT: active providers=${_orderedEnabledProviders.value.map { it.id }}")
    }

    fun isEnabled(context: Context, providerId: String): Boolean {
        return prefs(context).getBoolean("$KEY_ENABLED_PREFIX$providerId", true)
    }

    /**
     * Enables or disables a provider by ID, persists the change, and refreshes the reactive list.
     * At least one provider must remain enabled at all times.
     */
    fun setEnabled(context: Context, providerId: String, enabled: Boolean) {
        val prefs = prefs(context)

        if (!enabled) {
            val allIds = getAllProviderIds(prefs)
            val wouldBeEnabled = allIds.filter { id ->
                id != providerId && prefs.getBoolean("$KEY_ENABLED_PREFIX$id", true)
            }
            if (wouldBeEnabled.isEmpty()) {
                Log.w(TAG, "SET_ENABLED: refused to disable last provider=$providerId")
                return
            }
        }

        prefs.edit { putBoolean("$KEY_ENABLED_PREFIX$providerId", enabled) }

        // Also update enabled flag in custom source definition if applicable
        val currentCustom = _customSources.value
        if (currentCustom.any { it.id == providerId }) {
            val updated = currentCustom.map {
                if (it.id == providerId) it.copy(isEnabled = enabled) else it
            }
            saveCustomSources(prefs, updated)
        }

        _orderedEnabledProviders.value = buildOrderedEnabledList(prefs)
        Log.i(TAG, "SET_ENABLED: providerId=$providerId, enabled=$enabled")
    }

    /**
     * Persists a new provider ordering (list of IDs in desired priority order).
     */
    fun setProviderOrder(context: Context, orderedIds: List<String>) {
        val prefs = prefs(context)
        val allValid = getAllProviderIds(prefs)
        val validIds = orderedIds.filter { it in allValid }
        prefs.edit { putString(KEY_ORDER, validIds.joinToString(",")) }
        _orderedEnabledProviders.value = buildOrderedEnabledList(prefs)
        Log.i(TAG, "SET_ORDER: newOrder=$validIds")
    }

    fun getProviderOrder(context: Context): List<String> {
        return getOrderedIds(prefs(context))
    }

    /**
     * Adds a new custom source with validation to prevent duplicates or malformed configurations.
     */
    fun addCustomSource(context: Context, definition: CustomSourceDefinition): Result<Unit> {
        val prefs = prefs(context)

        // 1. Validation
        val nameTrimmed = definition.name.trim()
        val urlTrimmed = definition.baseUrl.trim()

        if (nameTrimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("اسم المصدر لا يمكن أن يكون فارغاً"))
        }

        if (!urlTrimmed.startsWith("https://", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("يجب أن يبدأ عنوان الموقع بـ https:// حصراً لأسباب الأمان"))
        }

        try {
            val uri = URI(urlTrimmed)
            if (uri.host.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("عنوان الموقع غير صالح أو لا يحتوي على اسم نطاق"))
            }
        } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("صيغة عنوان الموقع غير صالحة"))
        }

        // 2. Duplicate check
        val currentCustom = _customSources.value
        if (allBuiltInProviders.any { it.id == definition.id } || currentCustom.any { it.id == definition.id }) {
            return Result.failure(IllegalArgumentException("معرّف المصدر مسجّل مسبقاً"))
        }

        val normalizedUrl = urlTrimmed.trimEnd('/')
        if (currentCustom.any { it.baseUrl.trimEnd('/') == normalizedUrl }) {
            return Result.failure(IllegalArgumentException("يوجد مصدر مضاف مسبقاً بنفس العنوان: $normalizedUrl"))
        }

        // 3. Persist
        val updated = currentCustom + definition.copy(name = nameTrimmed, baseUrl = normalizedUrl)
        saveCustomSources(prefs, updated)
        prefs.edit { putBoolean("$KEY_ENABLED_PREFIX${definition.id}", definition.isEnabled) }
        _orderedEnabledProviders.value = buildOrderedEnabledList(prefs)
        Log.i(TAG, "ADD_CUSTOM_SOURCE: successfully registered custom source id=${definition.id}")
        return Result.success(Unit)
    }

    /**
     * Updates an existing custom source configuration.
     */
    fun updateCustomSource(context: Context, definition: CustomSourceDefinition): Result<Unit> {
        val prefs = prefs(context)
        val currentCustom = _customSources.value
        val index = currentCustom.indexOfFirst { it.id == definition.id }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("المصدر غير موجود للتعديل"))
        }

        val updated = currentCustom.toMutableList()
        updated[index] = definition
        saveCustomSources(prefs, updated)
        _orderedEnabledProviders.value = buildOrderedEnabledList(prefs)
        Log.i(TAG, "UPDATE_CUSTOM_SOURCE: updated source id=${definition.id}")
        return Result.success(Unit)
    }

    /**
     * Deletes a custom source and updates provider order.
     */
    fun deleteCustomSource(context: Context, sourceId: String): Result<Unit> {
        val prefs = prefs(context)
        val currentCustom = _customSources.value
        if (currentCustom.none { it.id == sourceId }) {
            return Result.failure(IllegalArgumentException("المصدر غير موجود أو أنه مصدر أساسي لا يمكن حذفه"))
        }

        val updated = currentCustom.filter { it.id != sourceId }
        saveCustomSources(prefs, updated)
        prefs.edit { remove("$KEY_ENABLED_PREFIX$sourceId") }

        // Remove from order string
        val currentOrder = getOrderedIds(prefs).filter { it != sourceId }
        prefs.edit { putString(KEY_ORDER, currentOrder.joinToString(",")) }

        _orderedEnabledProviders.value = buildOrderedEnabledList(prefs)
        Log.i(TAG, "DELETE_CUSTOM_SOURCE: deleted custom source id=$sourceId")
        return Result.success(Unit)
    }

    /**
     * Returns all registered providers (both built-in adapters and custom template sources).
     */
    fun getAllProviders(context: Context): List<EpisodeSourceProvider> {
        val customProviders = _customSources.value.map { TemplateEpisodeSourceProvider(it) }
        return allBuiltInProviders + customProviders
    }

    /**
     * Returns display names for all providers (built-in and custom).
     */
    fun getAllDisplayNames(context: Context): Map<String, String> {
        val map = builtInDisplayNames.toMutableMap()
        _customSources.value.forEach {
            map[it.id] = "${it.name} (قالب مخصص)"
        }
        return map
    }

    /** Resets all provider settings to defaults */
    fun resetToDefaults(context: Context) {
        val prefs = prefs(context)
        prefs.edit {
            allBuiltInProviders.forEach { p ->
                putBoolean("$KEY_ENABLED_PREFIX${p.id}", true)
            }
            remove(KEY_ORDER)
        }
        _orderedEnabledProviders.value = buildOrderedEnabledList(prefs)
        Log.i(TAG, "RESET_TO_DEFAULTS: all providers re-enabled in default order")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadCustomSources(prefs: SharedPreferences) {
        val rawJson = prefs.getString(KEY_CUSTOM_SOURCES, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val list = json.decodeFromString<List<CustomSourceDefinition>>(rawJson)
                _customSources.value = list
            } catch (e: Exception) {
                Log.e(TAG, "LOAD_CUSTOM_SOURCES_ERROR: ${e.message}")
            }
        }
    }

    private fun saveCustomSources(prefs: SharedPreferences, sources: List<CustomSourceDefinition>) {
        _customSources.value = sources
        try {
            val rawJson = json.encodeToString(sources)
            prefs.edit { putString(KEY_CUSTOM_SOURCES, rawJson) }
        } catch (e: Exception) {
            Log.e(TAG, "SAVE_CUSTOM_SOURCES_ERROR: ${e.message}")
        }
    }

    private fun getAllProviderIds(prefs: SharedPreferences): List<String> {
        val builtInIds = allBuiltInProviders.map { it.id }
        val customIds = _customSources.value.map { it.id }
        return builtInIds + customIds
    }

    private fun getOrderedIds(prefs: SharedPreferences): List<String> {
        val allIds = getAllProviderIds(prefs)
        val saved = prefs.getString(KEY_ORDER, null)
        return if (saved.isNullOrBlank()) {
            allIds
        } else {
            val savedIds = saved.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val ordered = savedIds.filter { it in allIds }
            val missing = allIds.filter { it !in ordered }
            ordered + missing
        }
    }

    private fun buildOrderedEnabledList(prefs: SharedPreferences): List<EpisodeSourceProvider> {
        val orderedIds = getOrderedIds(prefs)
        val builtInMap = allBuiltInProviders.associateBy { it.id }
        val customMap = _customSources.value.map { TemplateEpisodeSourceProvider(it) }.associateBy { it.id }
        val allMap = builtInMap + customMap

        return orderedIds.mapNotNull { id ->
            val isEnabled = prefs.getBoolean("$KEY_ENABLED_PREFIX$id", true)
            if (isEnabled) allMap[id] else null
        }
    }
}
