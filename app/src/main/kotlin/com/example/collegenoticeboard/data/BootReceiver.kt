package com.example.collegenoticeboard.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Kept clean with zero background sockets to protect system telecom and phone services.
    }
}
