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
import com.example.collegenoticeboard.ui.theme.CollegeNoticeBoardTheme

class MainActivity : ComponentActivity() {

    companion object {
        var pendingNoticeUrl: String? = null
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

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
        if (!url.isNullOrBlank()) {
            pendingNoticeUrl = url
        }
    }

    override fun onStart() {
        super.onStart()
        // Connect only while the app is actively in use
        CloudPushManager.startListening(this)
    }

    override fun onStop() {
        super.onStop()
        // Immediately disconnect all sockets and free connection pools when app is minimized/closed
        CloudPushManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        CloudPushManager.stopListening()
    }
}

