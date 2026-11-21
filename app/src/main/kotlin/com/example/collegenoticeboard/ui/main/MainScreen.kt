@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.collegenoticeboard.ui.main

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
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

    // Intercept back button to return from PDF viewer to notice list
    BackHandler(enabled = uiState.showPdfViewer) {
        viewModel.closePdfViewer()
    }

    // Auto-focus or highlight notification deep-linked circular if opened from notification
    LaunchedEffect(Unit) {
        val pendingUrl = com.example.collegenoticeboard.MainActivity.pendingNoticeUrl
        if (!pendingUrl.isNullOrBlank()) {
            com.example.collegenoticeboard.MainActivity.pendingNoticeUrl = null
            // Check if matches any notice
            val match = uiState.notices.find { it.noticeUrl == pendingUrl || it.pdfUrl == pendingUrl }
            if (match != null) {
                viewModel.downloadNotice(match)
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    if (uiState.showPdfViewer && uiState.pdfFile != null) {
        PdfViewerScreen(
            file = uiState.pdfFile!!,
            title = uiState.selectedNotice?.title ?: "PDF Viewer",
            onBack = { viewModel.closePdfViewer() },
            onOpenExternal = {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    uiState.pdfFile!!
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    val importantCount = remember(uiState.notices) {
        uiState.notices.count { it.isImportant }
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
            // Modern Search Bar with Dropdown Filter Menu integrated on the right
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
                            when (uiState.selectedCategory) {
                                NoticeCategory.ALL -> "Search all notices..."
                                NoticeCategory.IMPORTANT -> "Search important notices..."
                                else -> "Search ${uiState.selectedCategory.displayName} notices..."
                            },
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
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                // Dropdown Filter Menu Trigger Button
                Box {
                    Surface(
                        onClick = { menuExpanded = true },
                        shape = RoundedCornerShape(16.dp),
                        color = if (uiState.selectedCategory == NoticeCategory.IMPORTANT) {
                            MaterialTheme.colorScheme.errorContainer
                        } else if (uiState.selectedCategory != NoticeCategory.ALL) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (uiState.selectedCategory == NoticeCategory.IMPORTANT) {
                                    Icons.Filled.PriorityHigh
                                } else {
                                    Icons.Filled.FilterList
                                },
                                contentDescription = "Filter Menu",
                                modifier = Modifier.size(20.dp),
                                tint = if (uiState.selectedCategory == NoticeCategory.IMPORTANT) {
                                    MaterialTheme.colorScheme.error
                                } else if (uiState.selectedCategory != NoticeCategory.ALL) {
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
                        // 1. All Notices (Unified with General)
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

                        // 2. Important Notices
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Important Notices", fontWeight = if (uiState.selectedCategory == NoticeCategory.IMPORTANT) FontWeight.Bold else FontWeight.Normal)
                                    if (importantCount > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                "$importantCount",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.PriorityHigh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            trailingIcon = if (uiState.selectedCategory == NoticeCategory.IMPORTANT) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            } else null,
                            onClick = {
                                viewModel.selectCategory(NoticeCategory.IMPORTANT)
                                menuExpanded = false
                            }
                        )

                        // 3. Admission Notices
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

                        // 4. Examination Notices
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

                        // 5. Tender Notices
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

            // Active Filter Chip Badge (When a specific filter like Important/Exam/Admission/Tender is selected)
            if (uiState.selectedCategory != NoticeCategory.ALL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = if (uiState.selectedCategory == NoticeCategory.IMPORTANT) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Showing: ${uiState.selectedCategory.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.selectedCategory == NoticeCategory.IMPORTANT) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
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
                            if (uiState.selectedCategory == NoticeCategory.IMPORTANT) Icons.Filled.Star else Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (uiState.selectedCategory == NoticeCategory.IMPORTANT) "No important notices currently" else "No notices found",
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
                        // Stats card at the top
                        item {
                            StatsCard(
                                totalNotices = displayNotices.size,
                                importantNotices = importantCount,
                                downloadedNotices = uiState.downloadedFiles.size,
                                onImportantClick = { viewModel.selectCategory(NoticeCategory.IMPORTANT) }
                            )
                        }

                        itemsIndexed(
                            displayNotices,
                            key = { _, notice -> notice.id }
                        ) { _, notice ->
                            NoticeCard(
                                notice = notice,
                                isDownloading = uiState.downloadingNoticeIds.contains(notice.id),
                                isDownloaded = uiState.downloadedFiles.containsKey(notice.id),
                                onDownload = { viewModel.downloadNotice(notice, autoOpen = true) },
                                onView = { viewModel.openPdf(notice) },
                                onOpenUrl = {
                                    val urlToOpen = notice.noticeUrl.ifBlank { notice.pdfUrl }
                                    if (urlToOpen.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    totalNotices: Int,
    importantNotices: Int,
    downloadedNotices: Int,
    onImportantClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem(value = "$totalNotices", label = "All Notices", icon = Icons.Filled.Description)
            StatItem(
                value = "$importantNotices",
                label = "Important",
                icon = Icons.Filled.PriorityHigh,
                modifier = Modifier.clickable { onImportantClick() }
            )
            StatItem(value = "$downloadedNotices", label = "Downloaded", icon = Icons.Filled.CloudDownload)
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
    isDownloading: Boolean,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onView: () -> Unit,
    onOpenUrl: () -> Unit
) {
    ElevatedCard(
        onClick = {
            if (isDownloaded) {
                onView()
            } else if (!isDownloading) {
                onDownload()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (notice.isImportant) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (notice.isImportant) 4.dp else 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Category badge + Important badge + New badge + Date
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
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            notice.category,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (notice.isImportant) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PriorityHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    "IMPORTANT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (notice.isNew) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
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

            // Downloading progress
            if (isDownloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    strokeCap = StrokeCap.Round
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Downloading PDF...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val targetUrl = notice.noticeUrl.ifBlank { notice.pdfUrl }
                if (targetUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = onOpenUrl,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Open Link", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(Modifier.width(8.dp))

                    if (isDownloaded) {
                        FilledTonalButton(
                            onClick = onView,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("View PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (!isDownloading) {
                        FilledTonalButton(
                            onClick = onDownload,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Download PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewerScreen(
    file: File,
    title: String,
    onBack: () -> Unit,
    onOpenExternal: () -> Unit
) {
    // Intercept back button in PDF viewer screen
    BackHandler(onBack = onBack)

    var pages by remember(file) { mutableStateOf<List<Bitmap>?>(null) }
    var isRendering by remember(file) { mutableStateOf(true) }

    LaunchedEffect(file) {
        isRendering = true
        withContext(Dispatchers.IO) {
            val bitmaps = mutableListOf<Bitmap>()
            var fd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(fd)
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Downscale appropriately (clamp max dimension to 1080px) to prevent OOM
                    val scale = if (page.width > 0) (1080f / page.width).coerceIn(1.0f, 1.8f) else 1.0f
                    val destWidth = (page.width * scale).toInt()
                    val destHeight = (page.height * scale).toInt()

                    val bitmap = Bitmap.createBitmap(
                        destWidth,
                        destHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { renderer?.close() } catch (ignored: Exception) {}
                try { fd?.close() } catch (ignored: Exception) {}
            }
            pages = bitmaps
            isRendering = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open externally")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (isRendering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Rendering PDF preview...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (pages.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Cannot render PDF preview")
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onOpenExternal) {
                        Text("Open in external viewer")
                    }
                }
            }
        } else {
            val pageList = pages!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "${pageList.size} page(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                itemsIndexed(pageList) { index, bitmap ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                            if (pageList.size > 1) {
                                Text(
                                    "Page ${index + 1} of ${pageList.size}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
