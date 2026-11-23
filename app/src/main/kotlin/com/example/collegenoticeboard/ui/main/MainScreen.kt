@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.collegenoticeboard.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.collegenoticeboard.data.Notice
import com.example.collegenoticeboard.data.NoticeCategory
import java.io.File

@Composable
fun MainScreen(
    onItemClick: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var webViewNotice by remember { mutableStateOf<Notice?>(null) }

    // Auto-open deep-linked notification in Google Drive / Docs without downloading to disk
    val pendingNoticeUrl by com.example.collegenoticeboard.MainActivity.pendingNoticeUrl.collectAsStateWithLifecycle()
    LaunchedEffect(pendingNoticeUrl) {
        pendingNoticeUrl?.let { url ->
            com.example.collegenoticeboard.MainActivity.pendingNoticeUrl.value = null
            val notice = viewModel.findNoticeForUrl(url)
            val downloaded = uiState.downloadedFiles[notice.id]
            if (downloaded != null && downloaded.exists()) {
                openDownloadedPdfInViewer(context, downloaded)
            } else {
                viewModel.openOnlineInDriveOrDocs(notice) { onlinePdfUrl ->
                    openOnlineInDriveOrDocs(context, onlinePdfUrl)
                }
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.School,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "College Notice Board",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    val newCount = uiState.notices.count { it.isNew }
                    if (newCount > 0) {
                        IconButton(onClick = { viewModel.markAllAsSeen() }) {
                            Box {
                                Icon(Icons.Filled.DoneAll, "Mark all read")
                                Badge(
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    Text("$newCount")
                                }
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Modern Search Bar with Dropdown Filter Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (uiState.selectedCategory == NoticeCategory.ALL) "Search all notices..." else "Search ${uiState.selectedCategory.displayName} notices...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Filled.Close, "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                // Dropdown Filter Menu Trigger Button
                Box {
                    Surface(
                        onClick = { menuExpanded = true },
                        shape = RoundedCornerShape(16.dp),
                        color = if (uiState.selectedCategory != NoticeCategory.ALL) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Filter Menu",
                                modifier = Modifier.size(20.dp),
                                tint = if (uiState.selectedCategory != NoticeCategory.ALL) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Dropdown Menu displaying All Notices, Important, Admission, Exam, Tender
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Notices", fontWeight = if (uiState.selectedCategory == NoticeCategory.ALL) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = if (uiState.selectedCategory == NoticeCategory.ALL) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = {
                                viewModel.selectCategory(NoticeCategory.ALL)
                                menuExpanded = false
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Urgent & Important", fontWeight = if (uiState.selectedCategory == NoticeCategory.IMPORTANT) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(Icons.Filled.PriorityHigh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            trailingIcon = if (uiState.selectedCategory == NoticeCategory.IMPORTANT) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = {
                                viewModel.selectCategory(NoticeCategory.IMPORTANT)
                                menuExpanded = false
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Admission", fontWeight = if (uiState.selectedCategory == NoticeCategory.ADMISSION) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(Icons.Filled.School, contentDescription = null)
                            },
                            trailingIcon = if (uiState.selectedCategory == NoticeCategory.ADMISSION) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = {
                                viewModel.selectCategory(NoticeCategory.ADMISSION)
                                menuExpanded = false
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Exam", fontWeight = if (uiState.selectedCategory == NoticeCategory.EXAM) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(Icons.Filled.Star, contentDescription = null)
                            },
                            trailingIcon = if (uiState.selectedCategory == NoticeCategory.EXAM) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = {
                                viewModel.selectCategory(NoticeCategory.EXAM)
                                menuExpanded = false
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Tender", fontWeight = if (uiState.selectedCategory == NoticeCategory.TENDER) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                            },
                            trailingIcon = if (uiState.selectedCategory == NoticeCategory.TENDER) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = {
                                viewModel.selectCategory(NoticeCategory.TENDER)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }

            // Active Filter Chip Badge
            if (uiState.selectedCategory != NoticeCategory.ALL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Showing: ${uiState.selectedCategory.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    TextButton(onClick = { viewModel.selectCategory(NoticeCategory.ALL) }) {
                        Text("Reset to All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            val displayNotices = uiState.filteredNotices

            // Loading indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Fetching notices from AJC Bose College...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (displayNotices.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No notices found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { viewModel.loadNotices() }) {
                            Icon(Icons.Filled.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            } else {
                // Notice List with Pull to Refresh
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        state = rememberLazyListState()
                    ) {
                        item {
                            StatsCard(
                                totalNotices = displayNotices.size,
                                newNotices = uiState.notices.count { it.isNew },
                                savedNotices = uiState.downloadedFiles.size
                            )
                        }

                        itemsIndexed(
                            displayNotices,
                            key = { _, notice -> notice.id }
                        ) { _, notice ->
                            val isDownloaded = uiState.downloadedFiles.containsKey(notice.id)
                            NoticeCard(
                                notice = notice,
                                isLoading = uiState.loadingNoticeIds.contains(notice.id),
                                isDownloaded = isDownloaded,
                                onOpenDriveOrDocs = {
                                    // Primary classic action: Open in Google Drive / Docs without downloading!
                                    val downloaded = uiState.downloadedFiles[notice.id]
                                    if (downloaded != null && downloaded.exists()) {
                                        openDownloadedPdfInViewer(context, downloaded)
                                    } else {
                                        viewModel.openOnlineInDriveOrDocs(notice) { onlinePdfUrl ->
                                            openOnlineInDriveOrDocs(context, onlinePdfUrl)
                                        }
                                    }
                                },
                                onDownload = {
                                    // Option 1: Download directly to phone's public Downloads folder
                                    viewModel.downloadPdfToPublicFolder(notice) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenWebView = {
                                    // Option 2: Open in-app WebView
                                    webViewNotice = notice
                                },
                                onShare = {
                                    // Option 3: Share PDF
                                    viewModel.sharePdf(notice) { file ->
                                        sharePdf(context, file, notice.title)
                                    }
                                },
                                onDeleteDownloaded = {
                                    viewModel.deleteDownloadedPdf(notice)
                                    Toast.makeText(context, "Removed from offline cache", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // Optional In-App WebView Dialog (only when user explicitly taps the WebView option)
    if (webViewNotice != null) {
        val notice = webViewNotice!!
        val isDownloaded = uiState.downloadedFiles.containsKey(notice.id)
        NoticeViewDialog(
            notice = notice,
            isDownloaded = isDownloaded,
            isDownloading = uiState.loadingNoticeIds.contains(notice.id),
            onDownload = {
                viewModel.downloadPdfToPublicFolder(notice) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                viewModel.sharePdf(notice) { file ->
                    sharePdf(context, file, notice.title)
                }
            },
            onOpenBrowser = {
                val targetUrl = notice.noticeUrl.ifBlank { notice.pdfUrl }
                if (targetUrl.isNotBlank() && isValidCollegeUrl(targetUrl)) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { webViewNotice = null }
        )
    }
}

/**
 * Optional In-App WebView Dialog for users who explicitly choose the WebView option.
 */
@Composable
fun NoticeViewDialog(
    notice: Notice,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onOpenBrowser: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onDismiss)

        var webProgress by remember { mutableStateOf(true) }
        val targetUrl = remember(notice) {
            val nmidMatch = Regex("NMID=(\\d+)", RegexOption.IGNORE_CASE).find(notice.noticeUrl)
            if (nmidMatch != null) {
                "https://ajcbosecollegeerp.in/NoticePortal/NoticeDtls.aspx?NMID=${nmidMatch.groupValues[1]}"
            } else {
                notice.noticeUrl.ifBlank { notice.pdfUrl }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                notice.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${notice.category}${if (notice.date.isNotBlank()) " • ${notice.date}" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = onDownload, enabled = !isDownloading) {
                            Icon(Icons.Filled.Download, contentDescription = "Download")
                        }

                        IconButton(onClick = onShare) {
                            Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                        }

                        IconButton(onClick = onOpenBrowser) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )

                if (webProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round
                    )
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    webProgress = true
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    webProgress = false
                                }
                            }
                            loadUrl(targetUrl)
                        }
                    }
                )
            }
        }
    }
}

fun openOnlineInDriveOrDocs(context: Context, pdfUrl: String) {
    // 1. Google Drive PDF Viewer (streaming directly from cloud, 0 bytes saved to device files)
    try {
        val driveIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(pdfUrl), "application/pdf")
            setPackage("com.google.android.apps.docs")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(driveIntent)
        return
    } catch (_: Exception) {}

    // 2. Generic PDF Viewer supporting HTTP/HTTPS streaming
    try {
        val generalIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(pdfUrl), "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(generalIntent)
        return
    } catch (_: Exception) {}

    // 3. Google Docs Online Cloud Viewer
    try {
        val encoded = java.net.URLEncoder.encode(pdfUrl, "UTF-8")
        val docsViewerUrl = "https://docs.google.com/viewer?url=$encoded"
        val docsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(docsViewerUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(docsIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot open in Google Docs or Drive", Toast.LENGTH_SHORT).show()
    }
}

fun openDownloadedPdfInViewer(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No PDF viewer found on device", Toast.LENGTH_SHORT).show()
    }
}

fun sharePdf(context: Context, file: File, title: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "AJC Bose College Notice: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Notice PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot share PDF", Toast.LENGTH_SHORT).show()
    }
}

private fun isValidCollegeUrl(url: String): Boolean {
    if (url.isBlank()) return false
    return try {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        (scheme == "https" || scheme == "http") &&
            (host == "ajcbosecollege.ac.in" || host.endsWith(".ajcbosecollege.ac.in") ||
             host == "ajcbosecollegeerp.in" || host.endsWith(".ajcbosecollegeerp.in"))
    } catch (_: Exception) {
        false
    }
}

@Composable
fun StatsCard(
    totalNotices: Int,
    newNotices: Int,
    savedNotices: Int
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem(value = "$totalNotices", label = "All Notices", icon = Icons.Filled.Description)
            StatItem(value = "$savedNotices", label = "Saved PDFs", icon = Icons.Filled.PictureAsPdf)
            StatItem(value = "$newNotices", label = "New Updates", icon = Icons.Filled.Star)
        }
    }
}

@Composable
fun StatItem(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun NoticeCard(
    notice: Notice,
    isLoading: Boolean,
    isDownloaded: Boolean,
    onOpenDriveOrDocs: () -> Unit,
    onDownload: () -> Unit,
    onOpenWebView: () -> Unit,
    onShare: () -> Unit,
    onDeleteDownloaded: () -> Unit
) {
    OutlinedCard(
        onClick = onOpenDriveOrDocs, // Card tap opens directly in Google Drive / Docs!
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Category badge + Saved badge + New badge + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            notice.category,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (isDownloaded) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Saved offline",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    "SAVED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (notice.isNew) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "NEW",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (notice.date.isNotBlank()) {
                    Text(
                        notice.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Title
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFFE53935)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    notice.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Opening in Google Drive / Docs progress indicator
            if (isLoading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    strokeCap = StrokeCap.Round
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Opening in Google Drive / Docs...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action row: Options (Download, WebView, Share, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Option 1: Download to phone
                IconButton(
                    onClick = onDownload,
                    enabled = !isLoading,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download PDF",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Option 2: WebView
                IconButton(
                    onClick = onOpenWebView,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = "Open in WebView",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Option 3: Share PDF
                IconButton(
                    onClick = onShare,
                    enabled = !isLoading,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Share PDF",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Option 4: Delete from offline cache if saved
                if (isDownloaded) {
                    IconButton(
                        onClick = onDeleteDownloaded,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete cached PDF",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
