package com.example.collegenoticeboard.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.collegenoticeboard.data.Notice
import com.example.collegenoticeboard.ui.theme.CollegeNoticeBoardTheme

/**
 * Preview data for MainScreen components.
 */
private val previewNotices = listOf(
    Notice(
        id = "NMID_1",
        title = "College Holiday Notice - Diwali Vacation",
        date = "14 Nov 2026",
        category = "All Notices",
        noticeUrl = "https://ajcbosecollege.ac.in/show-notice.php?NMID=1",
        pdfUrl = "",
        isNew = true,
        isImportant = true,
        isDownloaded = false
    ),
    Notice(
        id = "NMID_2",
        title = "B.Sc Semester 3 Examination Schedule Released",
        date = "12 Nov 2026",
        category = "Exam",
        noticeUrl = "https://ajcbosecollege.ac.in/show-notice.php?NMID=2",
        pdfUrl = "",
        isNew = true,
        isImportant = true,
        isDownloaded = true
    )
)

@Preview(
    name = "NoticeCard - Normal",
    showBackground = true,
    device = Devices.PHONE
)
@Composable
fun NoticeCardPreview_Normal() {
    CollegeNoticeBoardTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                NoticeCard(
                    notice = previewNotices[0],
                    isLoading = false,
                    isDownloaded = false,
                    onOpenDriveOrDocs = {},
                    onDownload = {},
                    onOpenWebView = {},
                    onShare = {},
                    onDeleteDownloaded = {}
                )
            }
        }
    }
}

@Preview(
    name = "NoticeCard - Downloaded",
    showBackground = true,
    device = Devices.PHONE
)
@Composable
fun NoticeCardPreview_Downloaded() {
    CollegeNoticeBoardTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                NoticeCard(
                    notice = previewNotices[1],
                    isLoading = false,
                    isDownloaded = true,
                    onOpenDriveOrDocs = {},
                    onDownload = {},
                    onOpenWebView = {},
                    onShare = {},
                    onDeleteDownloaded = {}
                )
            }
        }
    }
}

@Preview(
    name = "NoticeCard - Loading",
    showBackground = true,
    device = Devices.PHONE
)
@Composable
fun NoticeCardPreview_Loading() {
    CollegeNoticeBoardTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                NoticeCard(
                    notice = previewNotices[0],
                    isLoading = true,
                    isDownloaded = false,
                    onOpenDriveOrDocs = {},
                    onDownload = {},
                    onOpenWebView = {},
                    onShare = {},
                    onDeleteDownloaded = {}
                )
            }
        }
    }
}

@Preview(
    name = "StatsCard",
    showBackground = true,
    device = Devices.PHONE
)
@Composable
fun StatsCardPreview() {
    CollegeNoticeBoardTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                StatsCard(
                    totalNotices = 150,
                    newNotices = 5,
                    savedNotices = 12
                )
            }
        }
    }
}
