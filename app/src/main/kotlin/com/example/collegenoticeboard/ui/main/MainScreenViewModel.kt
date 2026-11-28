package com.example.collegenoticeboard.ui.main

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.collegenoticeboard.data.DefaultNoticeRepository
import com.example.collegenoticeboard.data.Notice
import com.example.collegenoticeboard.data.NoticeCategory
import com.example.collegenoticeboard.data.NoticeCheckWorker
import com.example.collegenoticeboard.data.NoticeRepository
import com.example.collegenoticeboard.data.NoticeUpdateBus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MainScreenUiState(
    val isLoading: Boolean = true,
    val notices: List<Notice> = emptyList(),
    val filteredNotices: List<Notice> = emptyList(),
    val selectedCategory: NoticeCategory = NoticeCategory.ALL,
    val searchQuery: String = "",
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val loadingNoticeIds: Set<String> = emptySet(),
    val downloadedFiles: Map<String, File> = emptyMap()
)

class MainScreenViewModel private constructor(
    application: Application,
    private val repository: NoticeRepository,
    private val ioDispatcher: CoroutineDispatcher,
    autoStart: Boolean
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = DefaultNoticeRepository(application),
        ioDispatcher = Dispatchers.IO,
        autoStart = true
    )

    internal constructor(
        application: Application,
        repository: NoticeRepository,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        application = application,
        repository = repository,
        ioDispatcher = ioDispatcher,
        autoStart = false
    )

    private var allNotices: List<Notice> = emptyList()
    private var lastSyncTimeMs: Long = 0L
    private var foregroundSyncJob: Job? = null
    private var inFlightSyncJob: Job? = null

    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    init {
        if (autoStart) {
            // Instant startup: load cached notices immediately if available
            loadCachedNotices()
            // Then perform network sync
            loadNotices()
        }
        // Auto-refresh UI whenever CloudPushManager or NoticeCheckWorker receives new circulars
        viewModelScope.launch {
            NoticeUpdateBus.updates.collect {
                syncNoticesInBackground(notifyIfUnseen = true)
            }
        }
    }

    fun onForegroundChanged(isForeground: Boolean) {
        if (isForeground) {
            val now = System.currentTimeMillis()
            if (now - lastSyncTimeMs >= 30_000L) {
                syncNoticesInBackground(notifyIfUnseen = true)
            }
            if (foregroundSyncJob?.isActive != true) {
                foregroundSyncJob = viewModelScope.launch {
                    while (isActive) {
                        delay(120_000L)
                        syncNoticesInBackground(notifyIfUnseen = true)
                    }
                }
            }
        } else {
            foregroundSyncJob?.cancel()
            foregroundSyncJob = null
        }
    }

    private fun loadCachedNotices() {
        val cached = repository.loadCachedNotices()
        if (cached.isNotEmpty()) {
            allNotices = cached
            val ctx = getApplication<Application>()
            val downloaded = collectDownloadedFiles(cached, ctx)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    notices = cached,
                    filteredNotices = filterNotices(cached, it.searchQuery, it.selectedCategory),
                    downloadedFiles = downloaded
                )
            }
        }
    }

    fun loadNotices() {
        inFlightSyncJob?.cancel()
        inFlightSyncJob = viewModelScope.launch {
            lastSyncTimeMs = System.currentTimeMillis()
            if (_uiState.value.notices.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            try {
                val notices = repository.fetchNotices(NoticeCategory.ALL)
                applyFetchedNotices(notices, notifyIfUnseen = true, clearLoading = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = if (it.notices.isEmpty()) e.message ?: "Failed to load notices" else null
                    )
                }
            }
        }
    }

    fun refresh() {
        inFlightSyncJob?.cancel()
        inFlightSyncJob = viewModelScope.launch {
            lastSyncTimeMs = System.currentTimeMillis()
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val notices = repository.fetchNotices(NoticeCategory.ALL)
                applyFetchedNotices(notices, notifyIfUnseen = false, clearRefreshing = true)
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

    private fun syncNoticesInBackground(notifyIfUnseen: Boolean = true) {
        if (inFlightSyncJob?.isActive == true) return
        inFlightSyncJob = viewModelScope.launch {
            lastSyncTimeMs = System.currentTimeMillis()
            try {
                val notices = repository.fetchNotices(NoticeCategory.ALL)
                applyFetchedNotices(notices, notifyIfUnseen = notifyIfUnseen, clearLoading = true)
            } catch (_: Exception) {
                // Silent background sync: keep existing cached UI state on transient errors
            }
        }
    }

    private suspend fun applyFetchedNotices(
        notices: List<Notice>,
        notifyIfUnseen: Boolean,
        clearLoading: Boolean = false,
        clearRefreshing: Boolean = false
    ) {
        val ctx = getApplication<Application>()
        val (downloadedFiles, newUnnotified) = withContext(ioDispatcher) {
            val unnotified = repository.findUnnotifiedNewNotices(notices)
            if (unnotified.isNotEmpty()) {
                if (notifyIfUnseen) {
                    NoticeCheckWorker.notifyNewNotices(ctx, unnotified)
                }
                repository.markNoticesAsNotified(unnotified.map { it.id })
            }
            collectDownloadedFiles(notices, ctx) to unnotified
        }

        allNotices = notices
        lastSyncTimeMs = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isLoading = if (clearLoading) false else it.isLoading,
                isRefreshing = if (clearRefreshing) false else it.isRefreshing,
                notices = notices,
                filteredNotices = filterNotices(notices, it.searchQuery, it.selectedCategory),
                downloadedFiles = downloadedFiles,
                error = null
            )
        }
    }

    private fun collectDownloadedFiles(notices: List<Notice>, ctx: Application): Map<String, File> {
        val downloaded = mutableMapOf<String, File>()
        notices.forEach { notice ->
            val file = repository.getDownloadedFile(notice, ctx)
            if (file != null) {
                downloaded[notice.id] = file
            }
        }
        return downloaded
    }

    fun selectCategory(category: NoticeCategory) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                filteredNotices = filterNotices(allNotices, it.searchQuery, category)
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredNotices = filterNotices(allNotices, query, it.selectedCategory)
            )
        }
    }

    fun openPdf(notice: Notice, onFileReady: (File) -> Unit) {
        val existingFile = _uiState.value.downloadedFiles[notice.id]
        if (existingFile != null && existingFile.exists()) {
            onFileReady(existingFile)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loadingNoticeIds = it.loadingNoticeIds + notice.id) }
            val ctx = getApplication<Application>()
            val file = repository.downloadPdf(notice, ctx)
            _uiState.update { state ->
                val newFiles = if (file != null) state.downloadedFiles + (notice.id to file) else state.downloadedFiles
                state.copy(
                    loadingNoticeIds = state.loadingNoticeIds - notice.id,
                    downloadedFiles = newFiles,
                    error = if (file == null) "Could not load notice document" else null
                )
            }
            if (file != null) {
                onFileReady(file)
            }
        }
    }

    fun downloadPdfOnly(notice: Notice, onComplete: ((File) -> Unit)? = null) {
        val existingFile = _uiState.value.downloadedFiles[notice.id]
        if (existingFile != null && existingFile.exists()) {
            onComplete?.invoke(existingFile)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loadingNoticeIds = it.loadingNoticeIds + notice.id) }
            val ctx = getApplication<Application>()
            val file = repository.downloadPdf(notice, ctx)
            _uiState.update { state ->
                val newFiles = if (file != null) state.downloadedFiles + (notice.id to file) else state.downloadedFiles
                state.copy(
                    loadingNoticeIds = state.loadingNoticeIds - notice.id,
                    downloadedFiles = newFiles,
                    error = if (file == null) "Could not download notice document" else null
                )
            }
            if (file != null) {
                onComplete?.invoke(file)
            }
        }
    }

    fun downloadPdfToPublicFolder(notice: Notice, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingNoticeIds = it.loadingNoticeIds + notice.id) }
            val ctx = getApplication<Application>()
            val cacheFile = repository.downloadPdf(notice, ctx)
            if (cacheFile == null) {
                _uiState.update { state ->
                    state.copy(
                        loadingNoticeIds = state.loadingNoticeIds - notice.id,
                        error = "Failed to download notice"
                    )
                }
                onResult(false, "Failed to download notice")
                return@launch
            }

            // Save to public Downloads directory
            val cleanTitle = notice.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50)
            val fileName = "AJCBose_${cleanTitle}.pdf"
            val success = withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val resolver = ctx.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { out ->
                                cacheFile.inputStream().use { input -> input.copyTo(out) }
                            }
                            true
                        } else false
                    } else {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!downloadsDir.exists()) downloadsDir.mkdirs()
                        val destFile = File(downloadsDir, fileName)
                        cacheFile.copyTo(destFile, overwrite = true)
                        true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            _uiState.update { state ->
                state.copy(
                    loadingNoticeIds = state.loadingNoticeIds - notice.id,
                    downloadedFiles = state.downloadedFiles + (notice.id to cacheFile)
                )
            }

            if (success) {
                onResult(true, "Saved to Downloads: $fileName")
            } else {
                onResult(true, "Notice PDF downloaded")
            }
        }
    }

    fun sharePdf(notice: Notice, onReadyToShare: (File) -> Unit) {
        val existing = _uiState.value.downloadedFiles[notice.id]
        if (existing != null && existing.exists()) {
            onReadyToShare(existing)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loadingNoticeIds = it.loadingNoticeIds + notice.id) }
            val ctx = getApplication<Application>()
            val file = repository.downloadPdf(notice, ctx)
            _uiState.update { state ->
                val newFiles = if (file != null) state.downloadedFiles + (notice.id to file) else state.downloadedFiles
                state.copy(
                    loadingNoticeIds = state.loadingNoticeIds - notice.id,
                    downloadedFiles = newFiles,
                    error = if (file == null) "Failed to prepare PDF for sharing" else null
                )
            }
            if (file != null) {
                onReadyToShare(file)
            }
        }
    }

    fun deleteDownloadedPdf(notice: Notice) {
        val existingFile = _uiState.value.downloadedFiles[notice.id]
        if (existingFile != null && existingFile.exists()) {
            existingFile.delete()
        }
        _uiState.update { state ->
            state.copy(downloadedFiles = state.downloadedFiles - notice.id)
        }
    }

    fun findNoticeForUrl(url: String): Notice {
        val nmidMatch = Regex("NMID=(\\d+)", RegexOption.IGNORE_CASE).find(url)
        val nmid = nmidMatch?.groupValues?.get(1)

        val match = allNotices.find {
            (nmid != null && it.id == "NMID_$nmid") || it.noticeUrl == url || it.pdfUrl == url
        }

        return match ?: Notice(
            id = if (nmid != null) "NMID_$nmid" else "HASH_${url.hashCode()}",
            title = "College Notice",
            date = "",
            category = "All Notices",
            noticeUrl = url,
            pdfUrl = if (url.endsWith(".pdf", ignoreCase = true)) url else ""
        )
    }

    fun openOnlineInDriveOrDocs(notice: Notice, onUrlResolved: (String) -> Unit) {
        if (notice.pdfUrl.isNotBlank() && notice.pdfUrl.endsWith(".pdf", ignoreCase = true)) {
            onUrlResolved(notice.pdfUrl)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loadingNoticeIds = it.loadingNoticeIds + notice.id) }
            val resolvedUrl = repository.resolvePdfUrl(notice)
            _uiState.update { it.copy(loadingNoticeIds = it.loadingNoticeIds - notice.id) }
            val finalUrl = if (resolvedUrl.isNotBlank()) resolvedUrl else notice.noticeUrl
            if (finalUrl.isNotBlank()) {
                onUrlResolved(finalUrl)
            } else {
                _uiState.update { it.copy(error = "Could not find notice document link") }
            }
        }
    }

    fun handlePendingNotice(url: String, onUrlResolved: (String) -> Unit) {
        val notice = findNoticeForUrl(url)
        openOnlineInDriveOrDocs(notice, onUrlResolved)
    }

    fun markAllAsSeen() {
        viewModelScope.launch(ioDispatcher) {
            val allIds = allNotices.map { it.id }
            repository.markNoticesAsSeen(allIds)
            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        notices = state.notices.map { it.copy(isNew = false) },
                        filteredNotices = state.filteredNotices.map { it.copy(isNew = false) }
                    )
                }
            }
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
                NoticeCategory.HIDDEN_ARCHIVE -> notice.isArchived
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