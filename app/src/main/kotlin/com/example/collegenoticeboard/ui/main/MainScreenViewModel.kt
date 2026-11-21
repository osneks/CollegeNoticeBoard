package com.example.collegenoticeboard.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.collegenoticeboard.data.DefaultNoticeRepository
import com.example.collegenoticeboard.data.Notice
import com.example.collegenoticeboard.data.NoticeCategory
import com.example.collegenoticeboard.data.NoticeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class MainScreenUiState(
    val isLoading: Boolean = true,
    val notices: List<Notice> = emptyList(),
    val filteredNotices: List<Notice> = emptyList(),
    val selectedCategory: NoticeCategory = NoticeCategory.ALL,
    val searchQuery: String = "",
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val downloadingNoticeIds: Set<String> = emptySet(),
    val downloadedFiles: Map<String, File> = emptyMap(),
    val selectedNotice: Notice? = null,
    val showPdfViewer: Boolean = false,
    val pdfFile: File? = null
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoticeRepository = DefaultNoticeRepository(application)

    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    init {
        loadNotices()
    }

    fun loadNotices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val notices = repository.fetchNotices(_uiState.value.selectedCategory)
                val ctx = getApplication<Application>()
                val downloadedFiles = mutableMapOf<String, File>()
                notices.forEach { notice ->
                    val file = repository.getDownloadedFile(notice, ctx)
                    if (file != null) {
                        downloadedFiles[notice.id] = file
                    }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        notices = notices,
                        filteredNotices = filterNotices(notices, it.searchQuery, it.selectedCategory),
                        downloadedFiles = downloadedFiles,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load notices"
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val notices = repository.fetchNotices(_uiState.value.selectedCategory)
                val ctx = getApplication<Application>()
                val downloadedFiles = mutableMapOf<String, File>()
                notices.forEach { notice ->
                    val file = repository.getDownloadedFile(notice, ctx)
                    if (file != null) {
                        downloadedFiles[notice.id] = file
                    }
                }
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        notices = notices,
                        filteredNotices = filterNotices(notices, it.searchQuery, it.selectedCategory),
                        downloadedFiles = downloadedFiles,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = e.message ?: "Failed to refresh notices"
                    )
                }
            }
        }
    }

    fun selectCategory(category: NoticeCategory) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                filteredNotices = filterNotices(it.notices, it.searchQuery, category)
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredNotices = filterNotices(it.notices, query, it.selectedCategory)
            )
        }
    }

    fun downloadNotice(notice: Notice, autoOpen: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(downloadingNoticeIds = it.downloadingNoticeIds + notice.id)
            }
            val ctx = getApplication<Application>()
            val file = repository.downloadPdf(notice, ctx)
            _uiState.update { state ->
                val newDownloading = state.downloadingNoticeIds - notice.id
                val newFiles = if (file != null) {
                    state.downloadedFiles + (notice.id to file)
                } else {
                    state.downloadedFiles
                }
                state.copy(
                    downloadingNoticeIds = newDownloading,
                    downloadedFiles = newFiles,
                    showPdfViewer = if (autoOpen && file != null) true else state.showPdfViewer,
                    selectedNotice = if (autoOpen && file != null) notice else state.selectedNotice,
                    pdfFile = if (autoOpen && file != null) file else state.pdfFile
                )
            }
        }
    }

    fun openPdf(notice: Notice) {
        val file = _uiState.value.downloadedFiles[notice.id]
        if (file != null) {
            _uiState.update {
                it.copy(
                    selectedNotice = notice,
                    showPdfViewer = true,
                    pdfFile = file
                )
            }
        }
    }

    fun closePdfViewer() {
        _uiState.update {
            it.copy(
                showPdfViewer = false,
                selectedNotice = null,
                pdfFile = null
            )
        }
    }

    fun markAllAsSeen() {
        _uiState.value.notices.forEach { notice ->
            repository.markNoticeAsSeen(notice.id)
        }
        _uiState.update { state ->
            state.copy(
                notices = state.notices.map { it.copy(isNew = false) },
                filteredNotices = state.filteredNotices.map { it.copy(isNew = false) }
            )
        }
    }

    private fun filterNotices(
        notices: List<Notice>,
        query: String,
        category: NoticeCategory
    ): List<Notice> {
        return notices.filter { notice ->
            val matchesCategory = when (category) {
                NoticeCategory.ALL -> true
                NoticeCategory.IMPORTANT -> notice.isImportant
                else -> notice.category.equals(category.displayName, ignoreCase = true)
            }
            val matchesQuery = query.isBlank() ||
                notice.title.contains(query, ignoreCase = true) ||
                notice.date.contains(query, ignoreCase = true) ||
                notice.category.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }
}
