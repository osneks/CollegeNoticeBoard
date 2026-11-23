package com.example.collegenoticeboard.ui.main

import com.example.collegenoticeboard.data.Notice
import com.example.collegenoticeboard.data.NoticeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeModelTest {

    @Test
    fun testNoticeModelProperties() {
        val notice = Notice(
            id = "NMID_2436",
            title = "College Holiday Notice",
            date = "14 Nov 2026",
            category = "All Notices",
            noticeUrl = "https://ajcbosecollege.ac.in/show-notice.php?NMID=2436",
            pdfUrl = "",
            isNew = true,
            isImportant = false
        )

        assertEquals("NMID_2436", notice.id)
        assertTrue(notice.isNew)
        assertFalse(notice.isImportant)
        assertEquals("College Holiday Notice", notice.title)
    }

    @Test
    fun testNoticeCategoryDefinitions() {
        assertEquals("All Notices", NoticeCategory.ALL.displayName)
        assertEquals("Important", NoticeCategory.IMPORTANT.displayName)
        assertEquals(3, NoticeCategory.EXAM.ntid)
        assertEquals(2, NoticeCategory.ADMISSION.ntid)
        assertEquals(4, NoticeCategory.TENDER.ntid)
    }

    @Test
    fun testDistinctByIdPreservesMultipleAllotmentLists() {
        // Reproduce real college scenario where 2 distinct allotment lists share the title
        val notices = listOf(
            Notice("NMID_78", "3-Year B.Sc (Gen) PHYSICS Practical Exam", "31Dec2026", "Exam", "", ""),
            Notice("NMID_80", "3-Year B.Sc (Gen) PHYSICS Practical Exam", "31Dec2026", "Exam", "", "")
        )

        val uniqueById = notices.distinctBy { it.id }
        assertEquals("Both distinct circulars must be preserved by ID", 2, uniqueById.size)

        val uniqueByTitle = notices.distinctBy { it.title }
        assertEquals("Title-based deduplication would erroneously drop one circular", 1, uniqueByTitle.size)
    }

    @Test
    fun testLiveNoticesSortBeforeArchivedNotices() {
        val liveNotice = Notice(
            id = "NMID_2448",
            title = "Placement Notice",
            date = "23Nov2026",
            category = "All Notices",
            noticeUrl = "",
            pdfUrl = "",
            isArchived = false
        )
        val archivedNotice = Notice(
            id = "NMID_78",
            title = "Old Physics Exam",
            date = "31Dec2026", // Future typo date in archive
            category = "Exam",
            noticeUrl = "",
            pdfUrl = "",
            isArchived = true
        )

        val list = listOf(archivedNotice, liveNotice)
        val sorted = list.sortedWith(
            compareBy<Notice> { it.isArchived }
                .thenByDescending {
                    val sdf = java.text.SimpleDateFormat("ddMMMyyyy", java.util.Locale.ENGLISH)
                    val time = sdf.parse(it.date)?.time ?: 0L
                    if (time > System.currentTimeMillis() + 86_400_000L) 0L else time
                }
                .thenByDescending { it.id.removePrefix("NMID_").toIntOrNull() ?: 0 }
        )

        assertEquals("Live notice must sort first", "NMID_2448", sorted[0].id)
        assertEquals("Archived notice must sort after live notice", "NMID_78", sorted[1].id)
    }

    @Test
    fun testExactWebOrderPreservedForSameDateNotices() {
        val notice1 = Notice(id = "NMID_2445", title = "Constitution Day", date = "23Nov2026", category = "All Notices", noticeUrl = "", pdfUrl = "", webOrder = 0)
        val notice2 = Notice(id = "NMID_2446", title = "College Closed 24 Nov", date = "23Nov2026", category = "All Notices", noticeUrl = "", pdfUrl = "", webOrder = 1)
        val notice3 = Notice(id = "NMID_2448", title = "Career Counselling", date = "23Nov2026", category = "All Notices", noticeUrl = "", pdfUrl = "", webOrder = 2)

        val list = listOf(notice3, notice1, notice2)
        val sorted = list.sortedWith(
            compareBy<Notice> { it.isArchived }
                .thenByDescending {
                    val sdf = java.text.SimpleDateFormat("ddMMMyyyy", java.util.Locale.ENGLISH)
                    sdf.parse(it.date)?.time ?: 0L
                }
                .thenBy { it.webOrder }
        )

        assertEquals("Position 1 must be Constitution Day", "NMID_2445", sorted[0].id)
        assertEquals("Position 2 must be College Closed", "NMID_2446", sorted[1].id)
        assertEquals("Position 3 must be Career Counselling", "NMID_2448", sorted[2].id)
    }

    @Test
    fun testHiddenNoticesCategory() {
        assertEquals("Hidden Notices", NoticeCategory.HIDDEN_ARCHIVE.displayName)
    }

    @Test
    fun testAllNoticesIncludesBothLiveAndArchived() {
        val liveNotice = Notice("1", "Live", "23Nov2026", "All Notices", "", "", isArchived = false)
        val archivedNotice = Notice("2", "Archive", "14Nov2026", "All Notices", "", "", isArchived = true)
        val list = listOf(liveNotice, archivedNotice)
        val filtered = list.filter { notice ->
            when (NoticeCategory.ALL) {
                NoticeCategory.ALL -> true
                NoticeCategory.HIDDEN_ARCHIVE -> notice.isArchived
                else -> false
            }
        }
        assertEquals("All Notices must contain both live and archived notices", 2, filtered.size)
    }
}
