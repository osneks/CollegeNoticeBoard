package com.example.collegenoticeboard.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.collegenoticeboard.MainActivity
import com.example.collegenoticeboard.R
import java.util.concurrent.TimeUnit

class NoticeCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "notice_updates"
        const val IMPORTANT_CHANNEL_ID = "important_notice_updates"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "notice_check_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 15 minutes is the minimum interval allowed by Android WorkManager
            val workRequest = PeriodicWorkRequestBuilder<NoticeCheckWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)

                // Regular notices channel
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "General Notice Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for new AJC Bose College circulars"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)

                // High priority channel for urgent circulars
                val importantChannel = NotificationChannel(
                    IMPORTANT_CHANNEL_ID,
                    "Urgent College Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent alerts for exam schedules, deadlines, and form fill-ups"
                    enableVibration(true)
                }
                manager.createNotificationChannel(importantChannel)
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val repository = DefaultNoticeRepository(applicationContext)
            val notices = repository.fetchNotices()
            val seenIds = repository.getSeenNoticeIds()

            val newNotices = notices.filter { !seenIds.contains(it.id) }

            if (newNotices.isNotEmpty()) {
                createNotificationChannel(applicationContext)
                showNotification(newNotices)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(newNotices: List<Notice>) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val latestNotice = newNotices.first()
        val targetChannel = CHANNEL_ID

        val title = if (newNotices.size == 1) {
            "New Notice: ${latestNotice.category}"
        } else {
            "🔔 ${newNotices.size} New College Notices"
        }

        val body = if (newNotices.size == 1) {
            latestNotice.title
        } else {
            "${latestNotice.title} and ${newNotices.size - 1} other circular(s)"
        }

        val bigText = StringBuilder().apply {
            newNotices.take(5).forEach { notice ->
                append("• ")
                append(notice.title)
                append("\n")
            }
            if (newNotices.size > 5) {
                append("+${newNotices.size - 5} more notices...")
            }
        }.toString().trimEnd()

        val notification = NotificationCompat.Builder(applicationContext, targetChannel)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted
        }
    }
}
