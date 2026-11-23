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
        assertEquals(3, NoticeCategory.EXAM.ntid)
        assertEquals(2, NoticeCategory.ADMISSION.ntid)
        assertEquals(4, NoticeCategory.TENDER.ntid)
    }
}
