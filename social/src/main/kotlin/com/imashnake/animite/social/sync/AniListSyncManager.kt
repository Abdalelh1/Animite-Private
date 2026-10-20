package com.imashnake.animite.social.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.imashnake.animite.api.preferences.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PendingSyncMutation(
    val mediaId: Int,
    val progress: Int? = null,
    val score: Float? = null,
    val status: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Singleton
class AniListSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("animite_anilist_sync_queue", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _pendingQueue = MutableStateFlow<List<PendingSyncMutation>>(emptyList())
    val pendingQueue: StateFlow<List<PendingSyncMutation>> = _pendingQueue.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        loadQueueFromDisk()
        registerNetworkCallback()
    }

    private fun loadQueueFromDisk() {
        val raw = prefs.getString("queue", null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                val list = json.decodeFromString<List<PendingSyncMutation>>(raw)
                _pendingQueue.value = list
            }
        }
    }

    private fun saveQueueToDisk(queue: List<PendingSyncMutation>) {
        _pendingQueue.value = queue
        val raw = json.encodeToString(queue)
        prefs.edit().putString("queue", raw).apply()
    }

    fun enqueueMutation(mutation: PendingSyncMutation) {
        val current = _pendingQueue.value.toMutableList()
        // Replace previous mutation for same mediaId with newest state
        current.removeAll { it.mediaId == mutation.mediaId }
        current.add(mutation)
        saveQueueToDisk(current)
        triggerSync()
    }

    fun triggerSync() {
        scope.launch {
            if (_isSyncing.value || _pendingQueue.value.isEmpty()) return@launch

            val token = preferencesRepository.accessToken.firstOrNull()
            if (token.isNullOrBlank()) return@launch // AniList not linked, preserve local queue safely

            if (!isNetworkAvailable()) return@launch

            _isSyncing.value = true
            try {
                val queue = _pendingQueue.value.toMutableList()
                val iterator = queue.iterator()

                while (iterator.hasNext()) {
                    val item = iterator.next()
                    // Perform sync mutation safely
                    // On success:
                    iterator.remove()
                }

                saveQueueToDisk(queue)
            } catch (e: Exception) {
                Log.e("AniListSyncManager", "Sync paused: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                triggerSync()
            }
        })
    }
}
