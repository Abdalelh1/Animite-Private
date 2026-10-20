package com.imashnake.animite.profile

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.imashnake.animite.api.anilist.AnilistUserRepository
import com.imashnake.animite.api.anilist.EntryUpdateParams
import com.imashnake.animite.api.anilist.sanitize.media.Media
import com.imashnake.animite.api.anilist.type.MediaType
import com.imashnake.animite.api.anilist.type.ScoreFormat
import com.imashnake.animite.api.preferences.PreferencesRepository
import com.imashnake.animite.core.resource.Resource
import com.imashnake.animite.core.resource.Resource.Companion.asResource
import com.imashnake.animite.navigation.ProfileRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import android.content.Context
import com.imashnake.animite.media.LibraryStatus
import com.imashnake.animite.media.LocalLibraryEntry
import com.imashnake.animite.media.LocalLibraryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.imashnake.animite.social.data.AuthRepository
import com.imashnake.animite.social.data.FirestoreSyncRepository
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuthState
import com.imashnake.animite.social.model.FirestoreLibraryEntry
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: AnilistUserRepository,
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val firestoreSyncRepository: FirestoreSyncRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val navArgs = savedStateHandle.toRoute<ProfileRoute>()
    private val refreshTrigger = MutableSharedFlow<Unit>()
    private val animeListsRefreshTrigger = MutableSharedFlow<Unit>()
    private val mangaListsRefreshTrigger = MutableSharedFlow<Unit>()

    var useNetwork = false

    val authState = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Loading)

    val isFirebaseLoggedIn = authRepository.authState
        .map { it is AuthState.Authenticated }

    val currentFirebaseUser = authRepository.authState
        .map { (it as? AuthState.Authenticated)?.user }

    val isLoggedIn = isFirebaseLoggedIn

    val useProfileColor = preferencesRepository.useProfileColor.filterNotNull()
    val profileColor = preferencesRepository.profileColor.filterNotNull()
    val scoreFormat = preferencesRepository
        .scoreFormat
        .filterNotNull()
        .map { ScoreFormat.safeValueOf(it) }

    val useExpressiveProgressIndicator = preferencesRepository.useExpressiveProgressIndicator.filterNotNull()

    val viewer = combine(
        flow = refreshTrigger.onStart { emit(Unit) },
        flow2 = preferencesRepository.language.filterNotNull(),
        transform = ::Pair
    ).flatMapLatest {
        userRepository.fetchViewer(useNetwork, Media.Language.valueOf(it.second))
    }.asResource().onEach {
        it.data?.let { user ->
            preferencesRepository.setViewerId(user.id)
            // Don't update with cache
            if (useNetwork) {
                preferencesRepository.setProfileColor(user.color)
                preferencesRepository.setScoreFormat(user.scoreFormat?.name)
                preferencesRepository.setAnimeListOrder(user.animeListOrder)
                preferencesRepository.setMangaListOrder(user.mangaListOrder)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(1000),
        initialValue = Resource.loading(),
    )

    val viewerAnimeLists = combine(
        flow = animeListsRefreshTrigger.onStart { emit(Unit) },
        flow2 = combine(
            preferencesRepository.viewerId.filterNotNull(),
            preferencesRepository.language.filterNotNull(),
            scoreFormat,
            ::Triple
        ),
        flow3 = preferencesRepository.animeListOrder.filterNotNull().map {
            Json.decodeFromString<List<String>>(it)
        },
        transform = ::Triple,
    ).flatMapLatest {
        userRepository.fetchUserMediaList(
            id = it.second.first.toIntOrNull(),
            type = MediaType.ANIME,
            useNetwork = useNetwork,
            language = Media.Language.valueOf(it.second.second),
            scoreFormat = it.second.third,
            mediaListOrder = it.third,
        )
    }.asResource().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(1000),
        initialValue = Resource.loading(),
    )

    val viewerMangaLists = combine(
        flow = mangaListsRefreshTrigger.onStart { emit(Unit) },
        flow2 = combine(
            preferencesRepository.viewerId.filterNotNull(),
            preferencesRepository.language.filterNotNull(),
            scoreFormat,
            ::Triple
        ),
        flow3 = preferencesRepository.mangaListOrder.filterNotNull().map {
            Json.decodeFromString<List<String>>(it)
        },
        transform = ::Triple,
    ).flatMapLatest {
        userRepository.fetchUserMediaList(
            id = it.second.first.toIntOrNull(),
            type = MediaType.MANGA,
            useNetwork = useNetwork,
            language = Media.Language.valueOf(it.second.second),
            scoreFormat = it.second.third,
            mediaListOrder = it.third
        )
    }.asResource().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(1000),
        initialValue = Resource.loading(),
    )

    fun updateAnimeLists(sectionOrder: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        userRepository.updateUser(animeSectionOrder = sectionOrder).collect {
            preferencesRepository.setAnimeListOrder(it.getOrNull()?.mediaListOptions?.animeList?.sectionOrder?.filterNotNull())
        }
        refreshAnimeLists()
    }

    fun updateMangaLists(sectionOrder: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        userRepository.updateUser(mangaSectionOrder = sectionOrder).collect {
            preferencesRepository.setMangaListOrder(it.getOrNull()?.mediaListOptions?.mangaList?.sectionOrder?.filterNotNull())
        }
        refreshMangaLists()
    }

    fun updateMediaListEntry(type: Media.Small.Type, params: EntryUpdateParams) = viewModelScope.launch(Dispatchers.IO) {
        userRepository.updateMediaListEntry(params = params).collect {
            Log.d("UpdatedMediaListEntry", "updateMediaListEntry: id: ${it.getOrNull()?.mediaId}")
        }
        if (type == Media.Small.Type.ANIME) refreshAnimeLists() else refreshMangaLists()
    }

    fun toggleFavourite(type: Media.Small.Type, id: Int) = viewModelScope.launch(Dispatchers.IO) {
        userRepository.toggleFavourite(type = type, id = id).collect {
            Log.d("FavouritedMedia", "toggleFavourite: id: $id")
        }
        if (type == Media.Small.Type.ANIME) refreshAnimeLists() else refreshMangaLists()
    }

    private fun refreshAnimeLists() = viewModelScope.launch(Dispatchers.IO) {
        useNetwork = true
        animeListsRefreshTrigger.emit(Unit)
        delay(250.milliseconds)
        useNetwork = false
    }

    private fun refreshMangaLists() = viewModelScope.launch(Dispatchers.IO) {
        useNetwork = true
        mangaListsRefreshTrigger.emit(Unit)
        delay(250.milliseconds)
        useNetwork = false
    }

    fun refresh(setIsRefreshing: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        setIsRefreshing(true)
        useNetwork = true
        refreshTrigger.emit(Unit)
        animeListsRefreshTrigger.emit(Unit)
        mangaListsRefreshTrigger.emit(Unit)
        delay(1500.milliseconds)
        useNetwork = false
        setIsRefreshing(false)
    }

    fun logOut() = viewModelScope.launch(Dispatchers.IO) {
        authRepository.signOut()
        with(preferencesRepository) {
            setAccessToken(null)
            setViewerId(null)
            setViewerAvatar(null)
        }
    }

    val viewerAvatar = preferencesRepository.viewerAvatar
    fun saveViewerAvatar(avatarUrl: String?) = viewModelScope.launch(Dispatchers.IO) {
        preferencesRepository.setViewerAvatar(avatarUrl)
    }

    val localLibraryEntries = LocalLibraryManager.entriesFlow

    fun loadLocalLibrary() {
        LocalLibraryManager.getEntries(context)
    }

    fun updateLocalEntryStatus(animeId: Int, status: LibraryStatus) {
        LocalLibraryManager.updateStatus(context, animeId, status)
        syncEntryToCloud(animeId)
    }

    fun updateLocalEntryProgress(animeId: Int, episode: Int) {
        LocalLibraryManager.updateProgress(context, animeId, episode)
        syncEntryToCloud(animeId)
    }

    fun updateLocalEntryScore(animeId: Int, score: Float?) {
        LocalLibraryManager.updateScore(context, animeId, score)
        syncEntryToCloud(animeId)
    }

    fun toggleLocalFavorite(animeId: Int) {
        LocalLibraryManager.toggleFavorite(context, animeId)
        syncEntryToCloud(animeId)
    }

    fun removeLocalEntry(animeId: Int) {
        LocalLibraryManager.removeEntry(context, animeId)
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            firestoreSyncRepository.deleteLibraryEntry(uid, animeId)
        }
    }

    fun syncLocalLibraryToCloud() {
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val localList = LocalLibraryManager.getEntries(context)
            if (localList.isEmpty()) return@launch
            val cloudEntries = localList.map { local ->
                FirestoreLibraryEntry(
                    animeId = local.animeId,
                    status = local.status.name,
                    currentEpisode = local.currentEpisode,
                    score = local.score,
                    isFavorite = local.isFavorite,
                    notes = local.notes,
                    lastUpdated = local.lastUpdated,
                )
            }
            firestoreSyncRepository.batchSaveLibraryEntries(uid, cloudEntries)
        }
    }

    private fun syncEntryToCloud(animeId: Int) {
        val uid = authRepository.currentUid ?: return
        val entry = LocalLibraryManager.getEntry(context, animeId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            firestoreSyncRepository.saveLibraryEntry(
                uid = uid,
                entry = FirestoreLibraryEntry(
                    animeId = entry.animeId,
                    status = entry.status.name,
                    currentEpisode = entry.currentEpisode,
                    score = entry.score,
                    isFavorite = entry.isFavorite,
                    notes = entry.notes,
                    lastUpdated = entry.lastUpdated,
                )
            )
        }
    }

    init {
        LocalLibraryManager.getEntries(context)
        if (navArgs.accessToken != null) {
            viewModelScope.launch(Dispatchers.IO) {
                preferencesRepository.setAccessToken(navArgs.accessToken)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.authState.collect { state ->
                if (state is AuthState.Authenticated) {
                    syncLocalLibraryToCloud()
                }
            }
        }
    }
}
