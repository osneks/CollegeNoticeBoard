package com.example.collegenoticeboard

import com.example.collegenoticeboard.data.Notice
import com.example.collegenoticeboard.data.NoticeCategory
import com.example.collegenoticeboard.data.NoticeRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Test utilities for creating fake data and repositories.
 */
object TestUtils {

    fun createFakeNotices(count: Int = 5): List<Notice> {
        return (1..count).map { i ->
            Notice(
                id = "NMID_$i",
                title = "Test Notice $i",
                date = "$i Jan 2025",
                category = when (i % 4) {
                    0 -> "Exam"
                    1 -> "Admission"
                    2 -> "Tender"
                    else -> "All Notices"
                },
                noticeUrl = "https://ajcbosecollege.ac.in/notice/$i",
                pdfUrl = "",
                isNew = i <= 2,
                isImportant = i == 1,
                isArchived = i > 3,
                webOrder = i - 1
            )
        }
    }

    fun createFakeRepository(notices: List<Notice> = createFakeNotices()): NoticeRepository {
        return object : NoticeRepository {
            private val _notices = MutableStateFlow(notices)
            private val _seenIds = mutableSetOf<String>()
            private val _notifiedIds = mutableSetOf<String>()
            private var _notificationsInitialized = false

            override suspend fun fetchNotices(category: NoticeCategory): List<Notice> {
                return _notices.value.filter { notice ->
                    when (category) {
                        NoticeCategory.ALL -> true
                        NoticeCategory.HIDDEN_ARCHIVE -> notice.isArchived
                        NoticeCategory.IMPORTANT -> notice.isImportant
                        else -> notice.category.equals(category.displayName, ignoreCase = true)
                    }
                }
            }

            override suspend fun downloadPdf(notice: Notice, context: android.content.Context): java.io.File? = null

            override suspend fun resolvePdfUrl(notice: Notice): String = notice.pdfUrl.ifBlank { notice.noticeUrl }

            override fun getDownloadedFile(notice: Notice, context: android.content.Context): java.io.File? = null

            override fun isNoticeNew(noticeId: String): Boolean = !_seenIds.contains(noticeId)

            override fun markNoticeAsSeen(noticeId: String) = markNoticesAsSeen(listOf(noticeId))

            override fun markNoticesAsSeen(noticeIds: Collection<String>) {
                _seenIds.addAll(noticeIds)
            }

            override fun getSeenNoticeIds(): Set<String> = _seenIds.toSet()

            override fun markNoticeAsNotified(noticeId: String) = markNoticesAsNotified(listOf(noticeId))

            override fun markNoticesAsNotified(noticeIds: Collection<String>) {
                _notifiedIds.addAll(noticeIds)
                _notificationsInitialized = true
            }

            override fun findUnnotifiedNewNotices(notices: List<Notice>): List<Notice> {
                if (!_notificationsInitialized) return emptyList()
                return notices.filter { !_notifiedIds.contains(it.id) }
            }

            override fun loadCachedNotices(): List<Notice> = _notices.value
        }
    }
}
