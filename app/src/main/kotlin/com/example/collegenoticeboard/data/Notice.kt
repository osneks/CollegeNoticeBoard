package com.example.collegenoticeboard.data

import kotlinx.serialization.Serializable

@Serializable
data class Notice(
    val id: String,
    val title: String,
    val date: String,
    val category: String,
    val noticeUrl: String,
    val pdfUrl: String,
    val isNew: Boolean = false,
    val isDownloaded: Boolean = false,
    val isImportant: Boolean = false,
    val isArchived: Boolean = false,
    val webOrder: Int = 0
)

enum class NoticeCategory(val displayName: String, val ntid: Int?) {
    ALL("All Notices", null),
    IMPORTANT("Important", null),
    ADMISSION("Admission", 2),
    EXAM("Exam", 3),
    TENDER("Tender", 4),
    HIDDEN_ARCHIVE("Hidden Notices", null)
}

