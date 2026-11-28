package com.example.collegenoticeboard

import android.app.Application
import com.example.collegenoticeboard.data.CloudPushManager
import com.example.collegenoticeboard.data.NoticeCheckWorker

/**
 * Application entry point that keeps notification channels, periodic background
 * checks, and the real-time cloud push listener initialized for the lifetime
 * of the app process.
 */
class CollegeNoticeBoardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NoticeCheckWorker.createNotificationChannel(this)
        CloudPushManager.createNotificationChannels(this)
        NoticeCheckWorker.schedule(this)
        CloudPushManager.startListening(this)
    }
}
