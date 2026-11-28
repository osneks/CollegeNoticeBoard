package com.example.collegenoticeboard.data

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DataRepositoryTest {

    private lateinit var repository: DefaultNoticeRepository
    private lateinit var testContext: Context

    @Before
    fun setup() {
        testContext = RuntimeEnvironment.getApplication()
        testContext.getSharedPreferences("notice_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = DefaultNoticeRepository(testContext)
    }

    @Test
    fun testSeenNoticeIds_persistAndRetrieve() = runTest {
        val noticeId = "NMID_123"

        assertThat(repository.isNoticeNew(noticeId)).isTrue()

        repository.markNoticeAsSeen(noticeId)

        assertThat(repository.isNoticeNew(noticeId)).isFalse()
        assertThat(repository.getSeenNoticeIds()).contains(noticeId)
    }

    @Test
    fun testMarkMultipleNoticesAsSeen() = runTest {
        val ids = listOf("NMID_1", "NMID_2", "NMID_3")

        ids.forEach { assertThat(repository.isNoticeNew(it)).isTrue() }

        repository.markNoticesAsSeen(ids)

        ids.forEach { assertThat(repository.isNoticeNew(it)).isFalse() }
        assertThat(repository.getSeenNoticeIds()).containsAtLeastElementsIn(ids)
    }

    @Test
    fun testNotifiedNoticeIds_separateFromSeenIds() = runTest {
        val oldNotice = Notice("NMID_100", "Old", "01 Jan 2025", "All Notices", "url0", "")
        val newNotice = Notice("NMID_456", "Test", "02 Jan 2025", "All Notices", "url", "")

        // Seed initial notified state (simulates initial fetch)
        repository.markNoticeAsNotified(oldNotice.id)

        // New notice should be reported as unnotified while still remaining NEW for UI badge
        val unnotified = repository.findUnnotifiedNewNotices(listOf(oldNotice, newNotice))
        assertThat(unnotified).hasSize(1)
        assertThat(unnotified[0].id).isEqualTo(newNotice.id)
        assertThat(repository.isNoticeNew(newNotice.id)).isTrue()

        // Mark as notified
        repository.markNoticeAsNotified(newNotice.id)

        // No longer unnotified, yet still marked NEW for UI until user marks seen
        val unnotifiedAfter = repository.findUnnotifiedNewNotices(listOf(oldNotice, newNotice))
        assertThat(unnotifiedAfter).isEmpty()
        assertThat(repository.isNoticeNew(newNotice.id)).isTrue()
    }

    @Test
    fun testFindUnnotifiedNewNotices_returnsEmptyBeforeInitialization() = runTest {
        val notices = listOf(
            Notice("NMID_1", "Old Notice 1", "01 Jan 2020", "All Notices", "url1", ""),
            Notice("NMID_2", "Old Notice 2", "02 Jan 2020", "All Notices", "url2", "")
        )

        val unnotifiedBeforeSeed = repository.findUnnotifiedNewNotices(notices)
        assertThat(unnotifiedBeforeSeed).isEmpty()

        // Seed initial board
        repository.markNoticesAsNotified(notices.map { it.id })

        // Add a genuinely new notice
        val withNew = notices + Notice("NMID_3", "New Notice", "01 Jan 2025", "All Notices", "url3", "")
        val unnotifiedAfterSeed = repository.findUnnotifiedNewNotices(withNew)
        assertThat(unnotifiedAfterSeed).hasSize(1)
        assertThat(unnotifiedAfterSeed[0].id).isEqualTo("NMID_3")
    }

    @Test
    fun testLoadCachedNotices_returnsEmptyInitially() = runTest {
        val cached = repository.loadCachedNotices()
        assertThat(cached).isEmpty()
    }

    @Test
    fun testGetDownloadedFile_returnsNullForMissing() = runTest {
        val notice = Notice("NMID_999", "Test", "01 Jan 2025", "All Notices", "url", "")
        val file = repository.getDownloadedFile(notice, testContext)
        assertThat(file).isNull()
    }
}
