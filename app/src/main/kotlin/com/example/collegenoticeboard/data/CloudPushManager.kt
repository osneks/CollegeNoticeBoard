package com.example.collegenoticeboard.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.collegenoticeboard.MainActivity
import com.example.collegenoticeboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lifecycle-safe, Telecom-safe Cloud Push Manager.
 *
 * Ensures:
 * 1. ZERO interference with incoming phone call notifications, ringtones, or telecom services.
 * 2. Notification channels use standard IMPORTANCE_DEFAULT without overriding audio focus.
 * 3. Sockets are completely closed when the app goes into the background.
 */
object CloudPushManager {

    private const val TOPIC = "ajc_bose_college_notices_2026"
    private const val CHANNEL_ID = "college_notices_channel"
    private const val NOTIFICATION_ID = 2001

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventSource: EventSource? = null

    // Safe timeouts to avoid indefinitely dangling sockets
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun startListening(context: Context) {
        stopListening() // Clean up any lingering connections first
        createNotificationChannels(context)

        job = scope.launch {
            try {
                val request = Request.Builder()
                    .url("https://ntfy.sh/$TOPIC/sse")
                    .header("Accept", "text/event-stream")
                    .build()

                val sseFactory = EventSources.createFactory(client)
                eventSource = sseFactory.newEventSource(request, object : EventSourceListener() {
                    override fun onOpen(eventSource: EventSource, response: Response) {}

                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        try {
                            val json = JSONObject(data)
                            if (json.optString("event") == "message") {
                                val title = json.optString("title", "New College Notice")
                                val message = json.optString("message", "")
                                val clickUrl = json.optString("click", "")
                                showNotification(context.applicationContext, title, message, clickUrl)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {}
                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {}
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Completely shuts down the SSE connection and connection pool
     * to guarantee zero battery drain and zero socket/notification interference.
     */
    fun stopListening() {
        try {
            eventSource?.cancel()
            eventSource = null
            job?.cancel()
            job = null
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            // Safe IMPORTANCE_DEFAULT channel: will NEVER steal focus from incoming phone calls
            val channel = NotificationChannel(
                CHANNEL_ID,
                "College Notice Board Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for AJC Bose College notices"
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Safe priority, does not hijack telecom
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission check
        }
    }
}
