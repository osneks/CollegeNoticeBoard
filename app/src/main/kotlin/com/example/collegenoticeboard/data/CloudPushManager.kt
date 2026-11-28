package com.example.collegenoticeboard.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.collegenoticeboard.MainActivity
import com.example.collegenoticeboard.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resilient, lifecycle-safe, Telecom-safe Cloud Push Manager.
 *
 * Ensures:
 * 1. ZERO interference with incoming phone call notifications, ringtones, or telecom services.
 * 2. Automatic reconnect with `?since=` replay on network drop or Wi-Fi <-> mobile data transition.
 * 3. Deduplication by ntfy event ID and against WorkManager periodic background checks.
 * 4. Immediate in-app UI refresh via [NoticeUpdateBus] when a circular arrives.
 */
object CloudPushManager {

    private const val TOPIC = "ajcbose_circulars_sec_9f7a2d81e"
    private const val CHANNEL_ID = "college_alerts_popup_v1"
    private const val PREFS_NAME = "cloud_push_prefs"
    private const val KEY_LAST_NTFY_TIME = "last_ntfy_time"
    private const val KEY_NOTIFIED_EVENT_IDS = "notified_ntfy_event_ids"
    private const val MAX_STORED_EVENT_IDS = 200
    private const val MAX_REPLAY_WINDOW_SECONDS = 86_400L

    private val startLock = Any()
    private val prefsLock = Any()
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventSource: EventSource? = null
    private val isListening = AtomicBoolean(false)
    private val networkCallbackRegistered = AtomicBoolean(false)
    private val reconnectTrigger = Channel<Unit>(Channel.CONFLATED)

    // Long-lived SSE client: readTimeout=0 avoids killing idle streams every 60s;
    // pingInterval keeps NAT/mobile connections alive and detects silent drops.
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Short-lived client for one-shot WorkManager catch-up polling
    private val pollClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun startListening(context: Context) {
        val appContext = context.applicationContext
        createNotificationChannels(appContext)
        registerNetworkCallback(appContext)

        synchronized(startLock) {
            if (isListening.get() && job?.isActive == true) {
                return
            }
            isListening.set(true)
            job?.cancel()
            job = scope.launch {
                connectSseLoop(appContext)
            }
        }
    }

    private fun registerNetworkCallback(context: Context) {
        if (networkCallbackRegistered.getAndSet(true)) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    reconnectTrigger.trySend(Unit)
                }
            })
        } catch (_: Exception) {
            networkCallbackRegistered.set(false)
        }
    }

    private suspend fun connectSseLoop(context: Context) {
        while (isListening.get() && scope.isActive) {
            // Drain any stale trigger before opening a fresh SSE stream
            reconnectTrigger.tryReceive()

            try {
                val since = getSinceParam(context)
                val request = Request.Builder()
                    .url("https://ntfy.sh/$TOPIC/sse?since=$since")
                    .header("Accept", "text/event-stream")
                    .build()

                val sseFactory = EventSources.createFactory(sseClient)
                val disconnectSignal = CompletableDeferred<Unit>()

                eventSource?.cancel()
                eventSource = sseFactory.newEventSource(request, object : EventSourceListener() {
                    override fun onOpen(eventSource: EventSource, response: Response) {}

                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        try {
                            val json = JSONObject(data)
                            handleIncomingNtfyJson(context, json)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        disconnectSignal.complete(Unit)
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        disconnectSignal.complete(Unit)
                    }
                })

                disconnectSignal.await()
            } catch (_: Exception) {}

            if (isListening.get() && scope.isActive) {
                // Wait up to 3s, or wake immediately when network connectivity returns
                withTimeoutOrNull(3000L) {
                    reconnectTrigger.receive()
                }
            }
        }
    }

    /**
     * One-shot HTTP poll of missed ntfy messages since [getSinceParam].
     * Used by WorkManager so alerts sent while the app process was terminated
     * still pop up in the background.
     */
    fun catchUpMissedMessages(context: Context) {
        val appContext = context.applicationContext
        createNotificationChannels(appContext)
        try {
            val since = getSinceParam(appContext)
            val request = Request.Builder()
                .url("https://ntfy.sh/$TOPIC/json?poll=1&since=$since")
                .build()

            pollClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                response.body?.charStream()?.buffered()?.use { reader ->
                    reader.forEachLine { line ->
                        if (line.isNotBlank()) {
                            try {
                                val json = JSONObject(line)
                                handleIncomingNtfyJson(appContext, json)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleIncomingNtfyJson(context: Context, json: JSONObject) {
        val eventTime = json.optLong("time", 0L)
        if (eventTime > 0L) {
            updateLastNtfyTime(context, eventTime)
        }

        if (json.optString("event") != "message") return

        val eventId = json.optString("id", "")
        if (eventId.isNotBlank() && !markEventIdIfNew(context, eventId)) {
            return
        }

        val title = json.optString("title", "New College Notice").take(150)
        val message = json.optString("message", "").take(500)
        val rawClick = json.optString("click", "")
        val clickUrl = if (isValidCollegeUrl(rawClick)) rawClick else ""

        // Deduplicate against WorkManager periodic checks without clearing the UI NEW badge
        val nmidMatch = Regex("NMID=(\\d+)", RegexOption.IGNORE_CASE).find(clickUrl)
        if (nmidMatch != null) {
            val noticeId = "NMID_${nmidMatch.groupValues[1]}"
            DefaultNoticeRepository(context).markNoticeAsNotified(noticeId)
        }

        showNotification(context, title, message, clickUrl)
        NoticeUpdateBus.notifyUpdated()
    }

    private fun getSinceParam(context: Context): Long {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val minAllowed = nowSeconds - MAX_REPLAY_WINDOW_SECONDS
        synchronized(prefsLock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getLong(KEY_LAST_NTFY_TIME, 0L)
            if (stored <= 0L) {
                // First install: start from 60s ago to avoid replaying old historical alerts
                val initial = nowSeconds - 60L
                prefs.edit().putLong(KEY_LAST_NTFY_TIME, initial).commit()
                return initial
            }
            return maxOf(stored, minAllowed)
        }
    }

    private fun updateLastNtfyTime(context: Context, eventTime: Long) {
        synchronized(prefsLock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getLong(KEY_LAST_NTFY_TIME, 0L)
            if (eventTime > current) {
                prefs.edit().putLong(KEY_LAST_NTFY_TIME, eventTime).apply()
            }
        }
    }

    private fun markEventIdIfNew(context: Context, eventId: String): Boolean {
        synchronized(prefsLock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getStringSet(KEY_NOTIFIED_EVENT_IDS, emptySet())?.toMutableList()
                ?: mutableListOf()
            if (existing.contains(eventId)) return false
            existing.add(eventId)
            val trimmed = if (existing.size > MAX_STORED_EVENT_IDS) {
                existing.takeLast(MAX_STORED_EVENT_IDS).toSet()
            } else {
                existing.toSet()
            }
            prefs.edit().putStringSet(KEY_NOTIFIED_EVENT_IDS, trimmed).commit()
            return true
        }
    }

    fun stopListening() {
        synchronized(startLock) {
            isListening.set(false)
            try {
                eventSource?.cancel()
                eventSource = null
                job?.cancel()
                job = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "College Notice Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant pop-up alerts for new AJC Bose College circulars"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(
        context: Context,
        title: String,
        message: String,
        url: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (url.isNotBlank()) {
                putExtra("NOTICE_URL", url)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationId = if (url.isNotBlank()) {
                (url.hashCode() and 0x7FFFFFFF)
            } else {
                (System.currentTimeMillis() % 10000).toInt() + 2000
            }
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    private fun isValidCollegeUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val uri = android.net.Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: return false
            (scheme == "https" || scheme == "http") &&
                (host == "ajcbosecollege.ac.in" || host.endsWith(".ajcbosecollege.ac.in") ||
                 host == "ajcbosecollegeerp.in" || host.endsWith(".ajcbosecollegeerp.in"))
        } catch (e: Exception) {
            false
        }
    }
}
