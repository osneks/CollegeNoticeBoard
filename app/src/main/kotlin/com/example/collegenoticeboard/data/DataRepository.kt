package com.example.collegenoticeboard.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.FileOutputStream
import java.net.URL

interface NoticeRepository {
    suspend fun fetchNotices(category: NoticeCategory = NoticeCategory.ALL): List<Notice>
    suspend fun downloadPdf(notice: Notice, context: Context): File?
    suspend fun resolvePdfUrl(notice: Notice): String
    fun getDownloadedFile(notice: Notice, context: Context): File?
    fun isNoticeNew(noticeId: String): Boolean
    fun markNoticeAsSeen(noticeId: String)
    fun getSeenNoticeIds(): Set<String>
}

class DefaultNoticeRepository(private val context: Context) : NoticeRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notice_prefs", Context.MODE_PRIVATE)

    private val allNoticesUrl = "https://www.ajcbosecollege.ac.in/all-notice.html"
    private val erpBaseUrl = "https://ajcbosecollegeerp.in"

    override suspend fun fetchNotices(category: NoticeCategory): List<Notice> =
        withContext(Dispatchers.IO) {
            val notices = mutableListOf<Notice>()

            // Categories mapping directly to the tabs on https://www.ajcbosecollege.ac.in/all-notice.html
            // Note: General notices (ntid=1) are the main "All Notices" feed on the college portal
            val categoryConfigs = listOf(
                Triple(NoticeCategory.ALL, 1, "All Notices"),
                Triple(NoticeCategory.ADMISSION, 2, "Admission"),
                Triple(NoticeCategory.EXAM, 3, "Exam"),
                Triple(NoticeCategory.TENDER, 4, "Tender")
            )

            val targets = when (category) {
                NoticeCategory.ALL, NoticeCategory.IMPORTANT -> categoryConfigs
                else -> categoryConfigs.filter { it.first == category }
            }

            for ((catEnum, ntid, catName) in targets) {
                try {
                    val portalUrl = "$erpBaseUrl/NoticePortal/NoticeWebsite_bose.aspx?ntid=$ntid&A=A"
                    val doc: Document = Jsoup.connect(portalUrl)
                        .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .timeout(15000)
                        .followRedirects(true)
                        .get()

                    val rows = doc.select("table tr")
                    for (row in rows) {
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
                                "HASH_${titleText.hashCode()}"
                            }

                            notices.add(
                                Notice(
                                    id = id,
                                    title = titleText,
                                    date = dateText,
                                    category = catName,
                                    noticeUrl = noticeUrl,
                                    pdfUrl = "", // resolved on-demand or during download
                                    isNew = isNoticeNew(id),
                                    isImportant = false
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback scraping if ERP direct connection is blocked
            if (notices.isEmpty()) {
                try {
                    val doc = Jsoup.connect(allNoticesUrl)
                        .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .timeout(15000)
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
                                "HASH_${text.hashCode()}"
                            }
                            notices.add(
                                Notice(
                                    id = id,
                                    title = text,
                                    date = "",
                                    category = "General",
                                    noticeUrl = href,
                                    pdfUrl = if (href.endsWith(".pdf", ignoreCase = true)) href else "",
                                    isNew = isNoticeNew(id),
                                    isImportant = false
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            notices.distinctBy { it.title }
        }

    override suspend fun resolvePdfUrl(notice: Notice): String = withContext(Dispatchers.IO) {
        if (notice.pdfUrl.isNotBlank() && notice.pdfUrl.endsWith(".pdf", ignoreCase = true)) {
            return@withContext notice.pdfUrl
        }
        val targetUrl = notice.noticeUrl.ifBlank { notice.pdfUrl }
        if (targetUrl.isBlank()) return@withContext ""

        try {
            // If targetUrl is show-notice.php or NoticeWebsite or NoticeDtls
            val doc: Document = Jsoup.connect(targetUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .timeout(12000)
                .followRedirects(true)
                .get()

            // Look for direct PDF download button/link
            val pdfLink = doc.select("a[href*=.pdf], a#pdf-download-btn").firstOrNull()
            if (pdfLink != null) {
                val href = pdfLink.attr("abs:href").ifBlank { pdfLink.attr("href") }
                if (href.isNotBlank()) return@withContext href
            }

            // Look for iframe inside show-notice.php (e.g. NoticeDtls.aspx?NMID=...)
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

                    // Check script text for PDF path
                    val scripts = iframeDoc.select("script").html()
                    val pdfRegex = Regex("https?://[^'\"\\s]+\\.pdf", RegexOption.IGNORE_CASE)
                    val match = pdfRegex.find(scripts)
                    if (match != null) {
                        return@withContext match.value
                    }
                }
            }

            // Check if any script mentions a PDF file
            val scripts = doc.select("script").html()
            val pdfRegex = Regex("https?://[^'\"\\s]+\\.pdf", RegexOption.IGNORE_CASE)
            val match = pdfRegex.find(scripts)
            if (match != null) {
                return@withContext match.value
            }

            targetUrl
        } catch (e: Exception) {
            e.printStackTrace()
            targetUrl
        }
    }

    override suspend fun downloadPdf(notice: Notice, context: Context): File? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "notices")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "${notice.id.hashCode()}.pdf"
            val file = File(dir, fileName)

            // Validate existing file is not corrupted (must be > 200 bytes and start with %PDF)
            if (isValidPdf(file)) return@withContext file

            try {
                val resolvedUrl = resolvePdfUrl(notice)
                if (resolvedUrl.isBlank()) return@withContext null

                val url = URL(resolvedUrl)
                val connection = (url.openConnection() as? java.net.HttpURLConnection) ?: url.openConnection()
                if (connection is java.net.HttpURLConnection) {
                    connection.instanceFollowRedirects = true
                    connection.requestMethod = "GET"
                }

                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
                )
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                val tempFile = File(dir, "${notice.id.hashCode()}_temp.tmp")
                connection.getInputStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (isValidPdf(tempFile)) {
                    if (file.exists()) file.delete()
                    tempFile.renameTo(file)
                    file
                } else {
                    tempFile.delete()
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
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

    override fun getDownloadedFile(notice: Notice, context: Context): File? {
        val dir = File(context.cacheDir, "notices")
        val fileName = "${notice.id.hashCode()}.pdf"
        val file = File(dir, fileName)
        return if (isValidPdf(file)) file else null
    }

    override fun isNoticeNew(noticeId: String): Boolean {
        val seenIds = getSeenNoticeIds()
        return !seenIds.contains(noticeId)
    }

    override fun markNoticeAsSeen(noticeId: String) {
        val seenIds = getSeenNoticeIds().toMutableSet()
        seenIds.add(noticeId)
        prefs.edit().putStringSet("seen_notice_ids", seenIds).apply()
    }

    override fun getSeenNoticeIds(): Set<String> {
        return prefs.getStringSet("seen_notice_ids", emptySet()) ?: emptySet()
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
