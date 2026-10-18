package com.imashnake.animite.media

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class LibraryStatus(val key: String) {
    WATCHING("watching"),
    PLAN_TO_WATCH("plan_to_watch"),
    COMPLETED("completed"),
    ON_HOLD("on_hold"),
    DROPPED("dropped")
}

@Serializable
data class LocalLibraryEntry(
    val animeId: Int,
    val title: String,
    val coverImage: String? = null,
    val bannerImage: String? = null,
    val totalEpisodes: Int? = null,
    val currentEpisode: Int = 0,
    val status: LibraryStatus = LibraryStatus.WATCHING,
    val score: Float? = null,
    val isFavorite: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val notes: String = "",
)

object LocalLibraryManager {
    private const val PREFS_NAME = "animite_local_library"
    private const val KEY_SAVED_IDS = "saved_media_ids"
    private const val KEY_LIBRARY_ENTRIES = "library_entries_v2"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val _entriesFlow = MutableStateFlow<List<LocalLibraryEntry>>(emptyList())
    val entriesFlow: StateFlow<List<LocalLibraryEntry>> = _entriesFlow.asStateFlow()

    private var isInitialized = false

    @Synchronized
    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            _entriesFlow.value = loadEntriesFromDisk(context)
            isInitialized = true
        }
    }

    private fun loadEntriesFromDisk(context: Context): List<LocalLibraryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString(KEY_LIBRARY_ENTRIES, null)
        val loaded = if (!str.isNullOrBlank()) {
            try {
                json.decodeFromString<List<LocalLibraryEntry>>(str)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val legacyIds = prefs.getStringSet(KEY_SAVED_IDS, null)
        if (!legacyIds.isNullOrEmpty()) {
            val existingIds = loaded.map { it.animeId }.toSet()
            val migrated = loaded.toMutableList()
            var hasNewMigration = false
            for (idStr in legacyIds) {
                val id = idStr.toIntOrNull() ?: continue
                if (!existingIds.contains(id)) {
                    migrated.add(
                        LocalLibraryEntry(
                            animeId = id,
                            title = "Anime #$id",
                            status = LibraryStatus.PLAN_TO_WATCH
                        )
                    )
                    hasNewMigration = true
                }
            }
            if (hasNewMigration) {
                saveEntriesToDisk(context, migrated)
                return migrated
            }
        }
        return loaded
    }

    @Synchronized
    private fun saveEntriesToDisk(context: Context, entries: List<LocalLibraryEntry>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val encoded = json.encodeToString(entries)
            val idSet = entries.map { it.animeId.toString() }.toSet()
            prefs.edit {
                putString(KEY_LIBRARY_ENTRIES, encoded)
                putStringSet(KEY_SAVED_IDS, idSet)
            }
            _entriesFlow.value = entries
        } catch (_: Exception) {}
    }

    fun getEntries(context: Context): List<LocalLibraryEntry> {
        ensureInitialized(context)
        return _entriesFlow.value
    }

    fun getEntry(context: Context, animeId: Int): LocalLibraryEntry? {
        if (animeId <= 0) return null
        ensureInitialized(context)
        return _entriesFlow.value.firstOrNull { it.animeId == animeId }
    }

    fun isInLibrary(context: Context, animeId: Int): Boolean {
        if (animeId <= 0) return false
        ensureInitialized(context)
        return _entriesFlow.value.any { it.animeId == animeId }
    }

    fun isFavorite(context: Context, animeId: Int): Boolean {
        if (animeId <= 0) return false
        ensureInitialized(context)
        return _entriesFlow.value.any { it.animeId == animeId && it.isFavorite }
    }

    fun saveEntry(context: Context, entry: LocalLibraryEntry) {
        if (entry.animeId <= 0) return
        ensureInitialized(context)
        val current = _entriesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.animeId == entry.animeId }
        val updatedEntry = entry.copy(lastUpdated = System.currentTimeMillis())
        if (index >= 0) {
            current[index] = updatedEntry
        } else {
            current.add(0, updatedEntry)
        }
        saveEntriesToDisk(context, current)
    }

    fun updateStatus(context: Context, animeId: Int, status: LibraryStatus) {
        val entry = getEntry(context, animeId) ?: return
        saveEntry(context, entry.copy(status = status))
    }

    fun updateProgress(context: Context, animeId: Int, episode: Int) {
        val entry = getEntry(context, animeId) ?: return
        val clamped = episode.coerceAtLeast(0)
        val isCompleted = entry.totalEpisodes != null && clamped >= entry.totalEpisodes && entry.totalEpisodes > 0
        val newStatus = if (isCompleted) LibraryStatus.COMPLETED else entry.status
        saveEntry(context, entry.copy(currentEpisode = clamped, status = newStatus))
    }

    fun updateScore(context: Context, animeId: Int, score: Float?) {
        val entry = getEntry(context, animeId) ?: return
        saveEntry(context, entry.copy(score = score))
    }

    fun toggleFavorite(context: Context, animeId: Int): Boolean {
        val entry = getEntry(context, animeId) ?: return false
        val newFav = !entry.isFavorite
        saveEntry(context, entry.copy(isFavorite = newFav))
        return newFav
    }

    fun removeEntry(context: Context, animeId: Int): Boolean {
        if (animeId <= 0) return false
        ensureInitialized(context)
        val current = _entriesFlow.value.toMutableList()
        val removed = current.removeAll { it.animeId == animeId }
        if (removed) {
            saveEntriesToDisk(context, current)
        }
        return removed
    }

    fun toggleLibrary(
        context: Context,
        animeId: Int,
        title: String = "",
        coverImage: String? = null,
        bannerImage: String? = null,
        totalEpisodes: Int? = null,
    ): Boolean {
        if (animeId <= 0) return false
        ensureInitialized(context)
        if (isInLibrary(context, animeId)) {
            removeEntry(context, animeId)
            return false
        } else {
            val newEntry = LocalLibraryEntry(
                animeId = animeId,
                title = title.ifBlank { "Anime #$animeId" },
                coverImage = coverImage,
                bannerImage = bannerImage,
                totalEpisodes = totalEpisodes,
                currentEpisode = 0,
                status = LibraryStatus.WATCHING,
            )
            saveEntry(context, newEntry)
            return true
        }
    }
}
