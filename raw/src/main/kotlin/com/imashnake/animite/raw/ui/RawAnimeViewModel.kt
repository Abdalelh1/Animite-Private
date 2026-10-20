package com.imashnake.animite.raw.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imashnake.animite.raw.model.RawAnimeRelease
import com.imashnake.animite.raw.model.RawQuality
import com.imashnake.animite.raw.model.RawSourceDiagnostic
import com.imashnake.animite.raw.model.RawSourceId
import com.imashnake.animite.raw.source.RawSourceManager
import com.imashnake.animite.raw.subtitle.ImportedSubtitle
import com.imashnake.animite.raw.subtitle.SubtitleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RawSectionTab {
    RELEASES,
    SUBTITLES,
    DIAGNOSTICS,
}

@HiltViewModel
class RawAnimeViewModel @Inject constructor(
    private val sourceManager: RawSourceManager,
    val subtitleManager: SubtitleManager,
) : ViewModel() {

    private val _currentTab = MutableStateFlow(RawSectionTab.RELEASES)
    val currentTab = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedSource = MutableStateFlow<RawSourceId?>(null)
    val selectedSource = _selectedSource.asStateFlow()

    private val _selectedQuality = MutableStateFlow<RawQuality?>(null)
    val selectedQuality = _selectedQuality.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _releases = MutableStateFlow<List<RawAnimeRelease>>(emptyList())
    val releases = _releases.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<RawSourceDiagnostic>>(emptyList())
    val diagnostics = _diagnostics.asStateFlow()

    private val _importedSubtitles = MutableStateFlow<List<ImportedSubtitle>>(emptyList())
    val importedSubtitles = _importedSubtitles.asStateFlow()

    private val _onlineSubtitles = MutableStateFlow<List<com.imashnake.animite.raw.subtitle.OnlineSubtitle>>(emptyList())
    val onlineSubtitles = _onlineSubtitles.asStateFlow()

    private val _isSearchingOnlineSubtitles = MutableStateFlow(false)
    val isSearchingOnlineSubtitles = _isSearchingOnlineSubtitles.asStateFlow()

    private val _onlineSubtitleError = MutableStateFlow<String?>(null)
    val onlineSubtitleError = _onlineSubtitleError.asStateFlow()

    private val _onlineSubtitleLanguage = MutableStateFlow("ar,en")
    val onlineSubtitleLanguage = _onlineSubtitleLanguage.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadLatest()
        loadSubtitles()
        fetchDiagnostics()
    }

    fun setTab(tab: RawSectionTab) {
        _currentTab.value = tab
    }

    fun setSourceFilter(sourceId: RawSourceId?) {
        _selectedSource.value = sourceId
        if (_searchQuery.value.isNotBlank()) {
            performSearch(_searchQuery.value)
        } else {
            loadLatest()
        }
    }

    fun setQualityFilter(quality: RawQuality?) {
        _selectedQuality.value = quality
    }

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            loadLatest()
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500) // debounce
            performSearch(newQuery)
        }
    }

    fun loadLatest() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val list = sourceManager.getLatestReleases(_selectedSource.value)
                _releases.value = list
                if (list.isEmpty()) {
                    val src = _selectedSource.value
                    if (src != null && !src.name.equals("SUBSPLEASE", ignoreCase = true)) {
                        _errorMessage.value = "المصدر ${src.displayName} غير متاح حالياً أو يتطلب مصادقة."
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "حدث خطأ أثناء تحميل أحدث الإصدارات"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun performSearch(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val list = sourceManager.searchReleases(query, _selectedSource.value)
                _releases.value = list
                if (list.isEmpty()) {
                    _errorMessage.value = "لم يتم العثور على نتائج للبحث: \"$query\""
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "حدث خطأ أثناء البحث"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = sourceManager.fetchAllDiagnostics()
            _diagnostics.value = list
        }
    }

    fun loadSubtitles() {
        viewModelScope.launch(Dispatchers.IO) {
            _importedSubtitles.value = subtitleManager.getImportedSubtitles()
        }
    }

    fun importSubtitle(uri: Uri, preferredLang: String = "ar", onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = subtitleManager.importSubtitleFromUri(uri, preferredLang)
            res.fold(
                onSuccess = { sub ->
                    loadSubtitles()
                    launch(Dispatchers.Main) {
                        onResult(true, "تم استيراد الترجمة بنجاح: ${sub.name}")
                    }
                },
                onFailure = { err ->
                    launch(Dispatchers.Main) {
                        onResult(false, err.localizedMessage ?: "فشل استيراد ملف الترجمة")
                    }
                }
            )
        }
    }

    fun adjustSubtitleDelay(sub: ImportedSubtitle, deltaMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            subtitleManager.applySubtitleDelay(sub, deltaMs)
            loadSubtitles()
        }
    }

    fun deleteSubtitle(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            subtitleManager.deleteSubtitle(id)
            loadSubtitles()
        }
    }

    fun setOnlineSubtitleLanguage(lang: String) {
        _onlineSubtitleLanguage.value = lang
    }

    fun searchOnlineSubtitles(query: String, episodeNumber: Int? = null) {
        if (query.isBlank()) {
            _onlineSubtitles.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isSearchingOnlineSubtitles.value = true
            _onlineSubtitleError.value = null
            try {
                val res = subtitleManager.searchOnlineSubtitles(
                    query = query,
                    episodeNumber = episodeNumber,
                    languages = _onlineSubtitleLanguage.value
                )
                res.fold(
                    onSuccess = { list ->
                        _onlineSubtitles.value = list
                        if (list.isEmpty()) {
                            _onlineSubtitleError.value = "لم يتم العثور على ملفات ترجمة أونلاين لـ \"$query\""
                        }
                    },
                    onFailure = { err ->
                        _onlineSubtitleError.value = err.localizedMessage ?: "حدث خطأ أثناء البحث في OpenSubtitles"
                    }
                )
            } finally {
                _isSearchingOnlineSubtitles.value = false
            }
        }
    }

    fun downloadOnlineSubtitle(
        onlineSub: com.imashnake.animite.raw.subtitle.OnlineSubtitle,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = subtitleManager.downloadAndImportOnlineSubtitle(onlineSub)
            res.fold(
                onSuccess = { imported ->
                    loadSubtitles()
                    launch(Dispatchers.Main) {
                        onResult(true, "تم تنزيل وترميز الترجمة بنجاح: ${imported.name}")
                    }
                },
                onFailure = { err ->
                    launch(Dispatchers.Main) {
                        onResult(false, err.localizedMessage ?: "فشل تنزيل ملف الترجمة")
                    }
                }
            )
        }
    }
}
