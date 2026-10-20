package com.imashnake.animite.media

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ContinueWatchingItem(
    val animeId: Int,
    val animeTitle: String,
    val coverImage: String? = null,
    val episodeNumber: Float,
    val progress: Float = 0f,
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
)

object EpisodeWatchManager {
    private const val PREFS_NAME = "animite_episode_watch_prefs"
    private const val KEY_LAST_EPISODE_PREFIX = "last_ep_"
    private const val KEY_PROGRESS_PREFIX = "prog_"
    private const val KEY_WATCHED_PREFIX = "watched_"
    private const val KEY_CONTINUE_WATCHING_JSON = "continue_watching_list_json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    var onWatchProgressChanged: ((animeId: Int, episodeNumber: Float, progress: Float, lastPosMs: Long, durationMs: Long) -> Unit)? = null

    fun getProgress(context: Context, animeId: Int, episodeNumber: Float): Float {
        if (animeId <= 0) return 0f
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(progressKey(animeId, episodeNumber), 0f)
    }

    fun isWatched(context: Context, animeId: Int, episodeNumber: Float): Boolean {
        if (animeId <= 0) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(watchedKey(animeId, episodeNumber), false)) return true
        return prefs.getFloat(progressKey(animeId, episodeNumber), 0f) >= 0.9f
    }

    fun isInProgress(context: Context, animeId: Int, episodeNumber: Float): Boolean {
        val p = getProgress(context, animeId, episodeNumber)
        return p in 0.05f..0.89f
    }

    fun getLastWatchedEpisode(context: Context, animeId: Int): Float? {
        if (animeId <= 0) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$KEY_LAST_EPISODE_PREFIX$animeId"
        if (!prefs.contains(key)) return null
        return prefs.getFloat(key, -1f).takeIf { it >= 0 }
    }

    fun saveProgress(context: Context, animeId: Int, episodeNumber: Float, progress: Float) {
        if (animeId <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val clamped = progress.coerceIn(0f, 1f)
        prefs.edit {
            putFloat(progressKey(animeId, episodeNumber), clamped)
            putFloat("$KEY_LAST_EPISODE_PREFIX$animeId", episodeNumber)
            if (clamped >= 0.9f) {
                putBoolean(watchedKey(animeId, episodeNumber), true)
            }
        }
        onWatchProgressChanged?.invoke(animeId, episodeNumber, clamped, 0L, 0L)
    }

    fun markWatched(context: Context, animeId: Int, episodeNumber: Float, watched: Boolean) {
        if (animeId <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(watchedKey(animeId, episodeNumber), watched)
            if (watched) {
                putFloat(progressKey(animeId, episodeNumber), 1f)
                putFloat("$KEY_LAST_EPISODE_PREFIX$animeId", episodeNumber)
            } else {
                putFloat(progressKey(animeId, episodeNumber), 0f)
            }
        }
        onWatchProgressChanged?.invoke(animeId, episodeNumber, if (watched) 1f else 0f, 0L, 0L)
    }

    fun hasAnyWatchHistory(context: Context, animeId: Int): Boolean {
        if (animeId <= 0) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains("$KEY_LAST_EPISODE_PREFIX$animeId")
    }

    private const val KEY_POSITION_MS_PREFIX = "pos_ms_"

    fun savePositionMs(
        context: Context,
        animeIdentifier: String,
        episodeNumber: Float,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (animeIdentifier.isBlank() || episodeNumber <= 0f) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val safePos = positionMs.coerceAtLeast(0L)
        val safeDur = durationMs.coerceAtLeast(0L)
        val ratio = if (safeDur > 0L) (safePos.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f) else 0f
        val key = "$KEY_POSITION_MS_PREFIX${animeIdentifier.hashCode()}_${canonicalKey(episodeNumber)}"

        prefs.edit {
            putLong(key, safePos)
            val progKey = "$KEY_PROGRESS_PREFIX${animeIdentifier.hashCode()}_${canonicalKey(episodeNumber)}"
            putFloat(progKey, ratio)
            val watchKey = "$KEY_WATCHED_PREFIX${animeIdentifier.hashCode()}_${canonicalKey(episodeNumber)}"
            if (ratio >= 0.90f) {
                putBoolean(watchKey, true)
            } else {
                putBoolean(watchKey, false)
            }
        }

        recordContinueWatching(
            context = context,
            animeId = -1,
            animeTitle = animeIdentifier,
            coverImage = null,
            episodeNumber = episodeNumber,
            progress = ratio,
            lastPositionMs = safePos,
            durationMs = safeDur,
        )
    }

    fun getLastPositionMs(
        context: Context,
        animeIdentifier: String,
        episodeNumber: Float,
    ): Long {
        if (animeIdentifier.isBlank() || episodeNumber <= 0f) return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$KEY_POSITION_MS_PREFIX${animeIdentifier.hashCode()}_${canonicalKey(episodeNumber)}"
        val watchKey = "$KEY_WATCHED_PREFIX${animeIdentifier.hashCode()}_${canonicalKey(episodeNumber)}"
        // If already completed (>= 90%), do not auto-resume at the end
        if (prefs.getBoolean(watchKey, false)) return 0L
        return prefs.getLong(key, 0L)
    }

    private fun progressKey(animeId: Int, epNum: Float): String =
        "$KEY_PROGRESS_PREFIX${animeId}_${canonicalKey(epNum)}"

    private fun watchedKey(animeId: Int, epNum: Float): String =
        "$KEY_WATCHED_PREFIX${animeId}_${canonicalKey(epNum)}"

    private fun canonicalKey(epNum: Float): String {
        return if (kotlin.math.abs(epNum - epNum.toInt()) < 0.05f) {
            epNum.toInt().toString()
        } else {
            (Math.round(epNum * 10f) / 10f).toString()
        }
    }

    private const val KEY_CUSTOM_INTRO_START_PREFIX = "custom_intro_start_"
    private const val KEY_CUSTOM_INTRO_END_PREFIX = "custom_intro_end_"

    fun saveCustomIntro(
        context: Context,
        animeIdentifier: String,
        startMs: Long,
        endMs: Long,
    ) {
        if (animeIdentifier.isBlank() || endMs <= startMs) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hash = animeIdentifier.hashCode()
        prefs.edit {
            putLong("$KEY_CUSTOM_INTRO_START_PREFIX$hash", startMs)
            putLong("$KEY_CUSTOM_INTRO_END_PREFIX$hash", endMs)
        }
    }

    fun getCustomIntro(
        context: Context,
        animeIdentifier: String,
    ): Pair<Long, Long>? {
        if (animeIdentifier.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hash = animeIdentifier.hashCode()
        val start = prefs.getLong("$KEY_CUSTOM_INTRO_START_PREFIX$hash", -1L)
        val end = prefs.getLong("$KEY_CUSTOM_INTRO_END_PREFIX$hash", -1L)
        return if (start >= 0L && end > start) Pair(start, end) else null
    }

    fun clearCustomIntro(
        context: Context,
        animeIdentifier: String,
    ) {
        if (animeIdentifier.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hash = animeIdentifier.hashCode()
        prefs.edit {
            remove("$KEY_CUSTOM_INTRO_START_PREFIX$hash")
            remove("$KEY_CUSTOM_INTRO_END_PREFIX$hash")
        }
    }

    fun recordContinueWatching(
        context: Context,
        animeId: Int,
        animeTitle: String,
        coverImage: String?,
        episodeNumber: Float,
        progress: Float,
        lastPositionMs: Long = 0L,
        durationMs: Long = 0L,
    ) {
        if (animeId <= 0 && animeTitle.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingList = getContinueWatchingItems(context).toMutableList()

        val previous = existingList.firstOrNull {
            (animeId > 0 && it.animeId == animeId) ||
            (animeTitle.isNotBlank() && it.animeTitle.equals(animeTitle, ignoreCase = true))
        }
        val effectiveAnimeId = if (animeId > 0) animeId else (previous?.animeId ?: -1)
        val effectiveCover = coverImage ?: previous?.coverImage

        existingList.removeAll {
            (effectiveAnimeId > 0 && it.animeId == effectiveAnimeId) ||
            (animeTitle.isNotBlank() && it.animeTitle.equals(animeTitle, ignoreCase = true))
        }

        val clamped = progress.coerceIn(0f, 1f)
        if (clamped < 0.95f) {
            val newItem = ContinueWatchingItem(
                animeId = effectiveAnimeId,
                animeTitle = animeTitle.ifBlank { previous?.animeTitle ?: "Anime" },
                coverImage = effectiveCover,
                episodeNumber = episodeNumber,
                progress = clamped,
                lastPositionMs = lastPositionMs,
                durationMs = durationMs,
                timestamp = System.currentTimeMillis(),
            )
            existingList.add(0, newItem)
        }

        val capped = existingList.take(20)
        try {
            val str = json.encodeToString(capped)
            prefs.edit().putString(KEY_CONTINUE_WATCHING_JSON, str).apply()
        } catch (_: Exception) {}

        if (effectiveAnimeId > 0) {
            onWatchProgressChanged?.invoke(effectiveAnimeId, episodeNumber, clamped, lastPositionMs, durationMs)
        }
    }

    fun getContinueWatchingItems(context: Context): List<ContinueWatchingItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString(KEY_CONTINUE_WATCHING_JSON, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ContinueWatchingItem>>(str)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun removeContinueWatchingItem(context: Context, animeId: Int, animeTitle: String = "") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingList = getContinueWatchingItems(context).toMutableList()
        val modified = existingList.removeAll {
            (animeId > 0 && it.animeId == animeId) ||
            (animeTitle.isNotBlank() && it.animeTitle.equals(animeTitle, ignoreCase = true))
        }
        if (modified) {
            try {
                val str = json.encodeToString(existingList)
                prefs.edit().putString(KEY_CONTINUE_WATCHING_JSON, str).apply()
            } catch (_: Exception) {}
        }
    }
}
