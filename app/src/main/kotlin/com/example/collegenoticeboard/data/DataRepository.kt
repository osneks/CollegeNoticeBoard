package com.example.collegenoticeboard.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * In-process event bus to notify active UI screens immediately when a push
 * notification or background worker detects new circulars.
 */
object NoticeUpdateBus {
    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val updates: SharedFlow<Unit> = _updates.asSharedFlow()

    fun notifyUpdated() {
        _updates.tryEmit(Unit)
    }
}

interface NoticeRepository {
    suspend fun fetchNotices(category: NoticeCategory = NoticeCategory.ALL): List<Notice>
    suspend fun downloadPdf(notice: Notice, context: Context): File?
    suspend fun resolvePdfUrl(notice: Notice): String
    fun getDownloadedFile(notice: Notice, context: Context): File?
    fun isNoticeNew(noticeId: String): Boolean
    fun markNoticeAsSeen(noticeId: String)
    fun markNoticesAsSeen(noticeIds: Collection<String>)
    fun getSeenNoticeIds(): Set<String>
    fun markNoticeAsNotified(noticeId: String)
    fun markNoticesAsNotified(noticeIds: Collection<String>)
    fun findUnnotifiedNewNotices(notices: List<Notice>): List<Notice>
    fun loadCachedNotices(): List<Notice>
}

class DefaultNoticeRepository(private val context: Context) : NoticeRepository {

    companion object {
        private val prefsLock = Any()
        private const val KEY_SEEN_IDS = "seen_notice_ids"
        private const val KEY_NOTIFIED_IDS = "notified_notice_ids"
        private const val KEY_NOTIFICATIONS_INITIALIZED = "notifications_initialized"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notice_prefs", Context.MODE_PRIVATE)

    private val erpBaseUrl = "https://ajcbosecollegeerp.in"
    private val allNoticesUrl = "https://www.ajcbosecollege.ac.in/all-notice.html"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun fetchNotices(category: NoticeCategory): List<Notice> =
        withContext(Dispatchers.IO) {
            val categoryConfigs = listOf(
                Triple(NoticeCategory.ALL, 1, "All Notices"),
                Triple(NoticeCategory.ADMISSION, 2, "Admission"),
                Triple(NoticeCategory.EXAM, 3, "Exam"),
                Triple(NoticeCategory.TENDER, 4, "Tender")
            )

            val endpoints = mutableListOf<Triple<String, Int, Boolean>>()
            // Specific categories first in live feed
            endpoints.add(Triple("Exam", 3, false))
            endpoints.add(Triple("Admission", 2, false))
            endpoints.add(Triple("Tender", 4, false))
            endpoints.add(Triple("All Notices", 1, false))
            // Archive feeds
            endpoints.add(Triple("Exam", 3, true))
            endpoints.add(Triple("Admission", 2, true))
            endpoints.add(Triple("Tender", 4, true))
            endpoints.add(Triple("All Notices", 1, true))

            val scrapedNotices = coroutineScope {
                endpoints.map { (catName, ntid, isArchive) ->
                    async(Dispatchers.IO) {
                        fetchEndpoint(catName, ntid, isArchive)
                    }
                }.awaitAll().flatten()
            }

            val finalNotices = if (scrapedNotices.isNotEmpty()) {
                val unique = scrapedNotices.distinctBy { it.id }
                val sorted = unique.sortedWith(
                    compareBy<Notice> { it.isArchived }
                        .thenByDescending { parseNoticeDate(it.date) }
                        .thenBy { it.webOrder }
                )
                ensureNotificationsSeeded(sorted)
                saveNoticesToCache(sorted)
                sorted
            } else {
                // Offline fallback
                val cached = loadCachedNotices()
                if (cached.isEmpty()) {
                    fallbackScrapeCollegeSite()
                } else {
                    cached
                }
            }

            finalNotices
        }

    private fun fetchEndpoint(catName: String, ntid: Int, isArchive: Boolean): List<Notice> {
        val list = mutableListOf<Notice>()
        try {
            val portalUrl = if (isArchive) {
                "$erpBaseUrl/NoticePortal/NoticeWebsite_bose.aspx?ntid=$ntid&A=A"
            } else {
                "$erpBaseUrl/NoticePortal/NoticeWebsite_bose.aspx?ntid=$ntid"
            }
            val doc: Document = Jsoup.connect(portalUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .timeout(12000)
                .followRedirects(true)
                .get()

            val rows = doc.select("table tr")
            for ((index, row) in rows.withIndex()) {
                val cells = row.select("td")
                if (cells.size >= 2) {
                    val dateText = cells[0].text().trim()
                    val unescapedTitle = org.jsoup.parser.Parser.unescapeEntities(cells[1].text().trim(), false)
                    val titleText = unescapedTitle.replace(Regex("\\s+"), " ").trim()
                    if (titleText.isBlank() || titleText.equals("Subject", ignoreCase = true)) continue

                    val link = row.select("a[href]").firstOrNull()
                    val href = link?.attr("abs:href")?.ifBlank { link.attr("href") } ?: ""
                    val noticeUrl = if (href.startsWith("http")) href else "$erpBaseUrl/$href"

                    val nmidMatch = Regex("NMID=(\\d+)", RegexOption.IGNORE_CASE).find(href)
                    val id = if (nmidMatch != null) {
                        "NMID_${nmidMatch.groupValues[1]}"
                    } else {
                        val digest = hashString("${dateText}_${titleText}")
                        "HASH_$digest"
                    }

                    val isImportant = checkIsImportant(titleText, catName)
                    val isDownloaded = getDownloadedFileById(id, context) != null

                    list.add(
                        Notice(
                            id = id,
                            title = titleText,
                            date = dateText,
                            category = catName,
                            noticeUrl = noticeUrl,
                            pdfUrl = "",
                            isNew = isNoticeNew(id),
                            isDownloaded = isDownloaded,
                            isImportant = isImportant,
                            isArchived = isArchive,
                            webOrder = index
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Keep going across other endpoints
        }
        return list
    }

    private fun fallbackScrapeCollegeSite(): List<Notice> {
        val notices = mutableListOf<Notice>()
        try {
            val doc = Jsoup.connect(allNoticesUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .timeout(12000)
                .followRedirects(true)
                .get()

            val links = doc.select("a[href]")
            for (link in links) {
                val href = link.attr("abs:href").ifBlank { link.attr("href") }
                val unescapedText = org.jsoup.parser.Parser.unescapeEntities(link.text().trim(), false)
                val text = unescapedText.replace(Regex("\\s+"), " ").trim()
                if (text.length > 8 && (href.contains("pdf", ignoreCase = true) || href.contains("notice", ignoreCase = true))) {
                    val nmidMatch = Regex("NMID=(\\d+)", RegexOption.IGNORE_CASE).find(href)
                    val id = if (nmidMatch != null) {
                        "NMID_${nmidMatch.groupValues[1]}"
                    } else {
                        val digest = hashString(text)
                        "HASH_$digest"
                    }
                    notices.add(
                        Notice(
                            id = id,
                            title = text,
                            date = "",
                            category = "All Notices",
                            noticeUrl = href,
                            pdfUrl = if (href.endsWith(".pdf", ignoreCase = true)) href else "",
                            isNew = isNoticeNew(id),
                            isDownloaded = getDownloadedFileById(id, context) != null,
                            isImportant = false
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return notices.distinctBy { it.id }
    }

    override suspend fun resolvePdfUrl(notice: Notice): String = withContext(Dispatchers.IO) {
        if (notice.pdfUrl.isNotBlank() && notice.pdfUrl.endsWith(".pdf", ignoreCase = true)) {
            return@withContext notice.pdfUrl
        }
        val targetUrl = notice.noticeUrl.ifBlank { notice.pdfUrl }
        if (targetUrl.isBlank()) return@withContext ""

        try {
            val doc: Document = Jsoup.connect(targetUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .timeout(12000)
                .followRedirects(true)
                .get()

            val pdfLink = doc.select("a[href*=.pdf], a#pdf-download-btn").firstOrNull()
            if (pdfLink != null) {
                val href = pdfLink.attr("abs:href").ifBlank { pdfLink.attr("href") }
                if (href.isNotBlank()) return@withContext href
            }

            val innerIframe = doc.select("iframe[src*=NoticeDtls]").firstOrNull()
            if (innerIframe != null) {
                val iframeSrc = innerIframe.attr("abs:src").ifBlank { innerIframe.attr("src") }
                if (iframeSrc.isNotBlank()) {
                    val iframeDoc = Jsoup.connect(iframeSrc)
                        .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .timeout(12000)
                        .followRedirects(true)
                        .get()

                    val iframePdf = iframeDoc.select("a[href*=.pdf], a#pdf-download-btn").firstOrNull()
                    if (iframePdf != null) {
                        val pdfHref = iframePdf.attr("abs:href").ifBlank { iframePdf.attr("href") }
                        if (pdfHref.isNotBlank()) return@withContext pdfHref
                    }

                    val scripts = iframeDoc.select("script").html()
                    val pdfRegex = Regex("https?://[^'\"\\s]+\\.pdf", RegexOption.IGNORE_CASE)
                    val match = pdfRegex.find(scripts)
                    if (match != null) {
                        return@withContext match.value
                    }
                }
            }

            val scripts = doc.select("script").html()
            val pdfRegex = Regex("https?://[^'\"\\s]+\\.pdf", RegexOption.IGNORE_CASE)
            val match = pdfRegex.find(scripts)
            if (match != null) {
                return@withContext match.value
            }

            targetUrl
        } catch (e: Exception) {
            targetUrl
        }
    }

    override suspend fun downloadPdf(notice: Notice, context: Context): File? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "notices")
            if (!dir.exists()) dir.mkdirs()

            val targetFile = File(dir, "${notice.id}.pdf")
            if (isValidPdf(targetFile)) return@withContext targetFile

            try {
                val resolvedUrl = resolvePdfUrl(notice)
                if (resolvedUrl.isBlank() || !isAllowedDownloadUrl(resolvedUrl)) {
                    return@withContext null
                }

                pruneNoticeCache(dir)

                val tempFile = File(dir, "${notice.id}_${System.currentTimeMillis()}_temp.tmp")
                val request = Request.Builder()
                    .url(resolvedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val maxAllowedBytes = 25 * 1024 * 1024L // 25 MB safety cap
                var bytesReadTotal = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            bytesReadTotal += bytes
                            if (bytesReadTotal > maxAllowedBytes) {
                                tempFile.delete()
                                return@withContext null
                            }
                            output.write(buffer, 0, bytes)
                            bytes = input.read(buffer)
                        }
                    }
                }

                if (isValidPdf(tempFile)) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                    targetFile
                } else {
                    tempFile.delete()
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun isAllowedDownloadUrl(urlStr: String): Boolean {
        return try {
            val url = URL(urlStr)
            val scheme = url.protocol?.lowercase() ?: return false
            val host = url.host?.lowercase() ?: return false
            (scheme == "https" || scheme == "http") &&
                (host == "ajcbosecollege.ac.in" || host.endsWith(".ajcbosecollege.ac.in") ||
                 host == "ajcbosecollegeerp.in" || host.endsWith(".ajcbosecollegeerp.in"))
        } catch (e: Exception) {
            false
        }
    }

    private fun pruneNoticeCache(dir: File) {
        try {
            val files = dir.listFiles() ?: return
            val now = System.currentTimeMillis()
            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
            val fifteenMinutesMs = 15L * 60 * 1000
            val maxCacheBytes = 50 * 1024 * 1024L // 50MB max cache size

            var totalSize = 0L
            val fileList = files.toList().sortedBy { it.lastModified() }

            for (file in fileList) {
                if (file.name.endsWith(".tmp")) {
                    // Do not delete in-progress downloads
                    if (now - file.lastModified() > fifteenMinutesMs) {
                        file.delete()
                    }
                } else if (now - file.lastModified() > thirtyDaysMs) {
                    file.delete()
                } else {
                    totalSize += file.length()
                }
            }

            if (totalSize > maxCacheBytes) {
                for (file in fileList) {
                    if (file.exists() && !file.name.endsWith(".tmp")) {
                        totalSize -= file.length()
                        file.delete()
                        if (totalSize <= maxCacheBytes) break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isValidPdf(file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read == 4 && header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() &&
                    header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getFileForNotice(noticeId: String, context: Context): File {
        val dir = File(context.cacheDir, "notices")
        if (!dir.exists()) dir.mkdirs()
        val modernFile = File(dir, "${noticeId}.pdf")
        if (isValidPdf(modernFile)) return modernFile
        val legacyFile = File(dir, "${noticeId.hashCode()}.pdf")
        if (isValidPdf(legacyFile)) return legacyFile
        return modernFile
    }

    override fun getDownloadedFile(notice: Notice, context: Context): File? {
        val file = getFileForNotice(notice.id, context)
        return if (isValidPdf(file)) file else null
    }

    private fun getDownloadedFileById(noticeId: String, context: Context): File? {
        val file = getFileForNotice(noticeId, context)
        return if (isValidPdf(file)) file else null
    }

    override fun isNoticeNew(noticeId: String): Boolean {
        val seenIds = getSeenNoticeIds()
        return !seenIds.contains(noticeId)
    }

    override fun markNoticeAsSeen(noticeId: String) {
        markNoticesAsSeen(listOf(noticeId))
    }

    override fun markNoticesAsSeen(noticeIds: Collection<String>) {
        if (noticeIds.isEmpty()) return
        synchronized(prefsLock) {
            val seenIds = getSeenNoticeIdsLocked().toMutableSet()
            if (seenIds.addAll(noticeIds)) {
                prefs.edit().putStringSet(KEY_SEEN_IDS, seenIds).apply()
            }
        }
    }

    override fun getSeenNoticeIds(): Set<String> {
        synchronized(prefsLock) {
            return getSeenNoticeIdsLocked()
        }
    }

    private fun getSeenNoticeIdsLocked(): Set<String> {
        return prefs.getStringSet(KEY_SEEN_IDS, emptySet())?.toSet() ?: emptySet()
    }

    override fun markNoticeAsNotified(noticeId: String) {
        markNoticesAsNotified(listOf(noticeId))
    }

    override fun markNoticesAsNotified(noticeIds: Collection<String>) {
        if (noticeIds.isEmpty()) return
        synchronized(prefsLock) {
            val notified = getNotifiedNoticeIdsLocked().toMutableSet()
            val changed = notified.addAll(noticeIds)
            val editor = prefs.edit()
            if (changed) {
                editor.putStringSet(KEY_NOTIFIED_IDS, notified)
            }
            if (!prefs.getBoolean(KEY_NOTIFICATIONS_INITIALIZED, false)) {
                editor.putBoolean(KEY_NOTIFICATIONS_INITIALIZED, true)
            }
            editor.commit()
        }
    }

    override fun findUnnotifiedNewNotices(notices: List<Notice>): List<Notice> {
        if (notices.isEmpty()) return emptyList()
        synchronized(prefsLock) {
            if (!prefs.getBoolean(KEY_NOTIFICATIONS_INITIALIZED, false)) {
                return emptyList()
            }
            val notifiedIds = getNotifiedNoticeIdsLocked()
            return notices.filter { !notifiedIds.contains(it.id) }
        }
    }

    /**
     * Seeds notified_notice_ids once before overwriting the notice cache so that:
     * - Existing installs upgrading with a prior cache/seen set still get notified
     *   for any brand-new notice not in their prior cache.
     * - Fresh installs seed with the initial board without spamming historical notices,
     *   allowing the very next new circular to trigger a pop-up immediately.
     */
    private fun ensureNotificationsSeeded(fetchedNotices: List<Notice>) {
        if (fetchedNotices.isEmpty()) return
        synchronized(prefsLock) {
            if (prefs.getBoolean(KEY_NOTIFICATIONS_INITIALIZED, false)) return

            val priorCachedIds = loadCachedNotices().map { it.id }
            val priorSeenIds = getSeenNoticeIdsLocked()
            val seedIds = if (priorCachedIds.isNotEmpty() || priorSeenIds.isNotEmpty()) {
                (priorCachedIds + priorSeenIds).toSet()
            } else {
                fetchedNotices.map { it.id }.toSet()
            }

            prefs.edit()
                .putStringSet(KEY_NOTIFIED_IDS, seedIds)
                .putBoolean(KEY_NOTIFICATIONS_INITIALIZED, true)
                .commit()
        }
    }

    private fun getNotifiedNoticeIdsLocked(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet())?.toSet() ?: emptySet()
    }

    override fun loadCachedNotices(): List<Notice> {
        return try {
            val cacheFile = File(context.cacheDir, "cached_notices.json")
            if (!cacheFile.exists()) return emptyList()
            val array = JSONArray(cacheFile.readText())
            val list = mutableListOf<Notice>()
            val seenIds = getSeenNoticeIds()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                list.add(
                    Notice(
                        id = id,
                        title = obj.getString("title"),
                        date = obj.optString("date", ""),
                        category = obj.optString("category", "All Notices"),
                        noticeUrl = obj.optString("noticeUrl", ""),
                        pdfUrl = obj.optString("pdfUrl", ""),
                        isNew = !seenIds.contains(id),
                        isDownloaded = getDownloadedFileById(id, context) != null,
                        isImportant = obj.optBoolean("isImportant", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        webOrder = obj.optInt("webOrder", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveNoticesToCache(notices: List<Notice>) {
        try {
            val cacheFile = File(context.cacheDir, "cached_notices.json")
            val array = JSONArray()
            for (n in notices) {
                val obj = JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("date", n.date)
                    put("category", n.category)
                    put("noticeUrl", n.noticeUrl)
                    put("pdfUrl", n.pdfUrl)
                    put("isImportant", n.isImportant)
                    put("isArchived", n.isArchived)
                    put("webOrder", n.webOrder)
                }
                array.put(obj)
            }
            cacheFile.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseNoticeDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val formats = listOf(
            "ddMMMyyyy",
            "dd MMM yyyy",
            "dd-MMM-yyyy",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )
        val clean = dateStr.trim().replace(Regex("\\s+"), " ")
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.isLenient = true
                val parsed = sdf.parse(clean)
                if (parsed != null) {
                    val time = parsed.time
                    // Sanitize clerical typo future dates (e.g., 31Dec2026) so they sort by NMID
                    val maxValidTime = System.currentTimeMillis() + 86_400_000L
                    return if (time > maxValidTime) 0L else time
                }
            } catch (_: Exception) {}
        }
        return 0L
    }

    private fun extractNmidNumber(id: String): Int {
        val match = Regex("NMID_(\\d+)", RegexOption.IGNORE_CASE).find(id)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun hashString(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun checkIsImportant(title: String, category: String): Boolean {
        val lower = title.lowercase()
        val importantKeywords = listOf(
            "urgent", "important", "alert", "attention", "mandatory",
            "last date", "deadline", "schedule", "exam", "examination",
            "postponed", "cancelled", "result", "fee", "payment",
            "form fill-up", "form fill up", "admit card", "registration",
            "admission", "merit list", "c.u.", "calcutta university",
            "cancellation", "holiday", "suspension", "emergency"
        )
        return category.equals("Exam", ignoreCase = true) ||
               category.equals("Admission", ignoreCase = true) ||
               importantKeywords.any { lower.contains(it) }
    }
}