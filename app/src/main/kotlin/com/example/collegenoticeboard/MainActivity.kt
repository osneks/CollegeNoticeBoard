package com.example.collegenoticeboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.collegenoticeboard.data.CloudPushManager
import com.example.collegenoticeboard.data.NoticeCheckWorker
import com.example.collegenoticeboard.ui.theme.CollegeNoticeBoardTheme

import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        val pendingNoticeUrl = MutableStateFlow<String?>(null)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        // Schedule periodic background notice check worker
        NoticeCheckWorker.schedule(applicationContext)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            CollegeNoticeBoardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.getStringExtra("NOTICE_URL")
        if (!url.isNullOrBlank() && isValidCollegeUrl(url)) {
            pendingNoticeUrl.value = url
        }
    }

    private fun isValidCollegeUrl(url: String): Boolean {
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

    override fun onStart() {
        super.onStart()
        CloudPushManager.startListening(this)
    }

    override fun onStop() {
        super.onStop()
        // Keep push listener active while app process is alive so alerts pop up like WhatsApp
    }

    override fun onDestroy() {
        super.onDestroy()
        CloudPushManager.stopListening()
    }
}

