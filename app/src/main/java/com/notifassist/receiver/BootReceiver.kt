package com.notifassist.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.notifassist.service.TtsService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot complete — starting TtsService")
            context.startForegroundService(Intent(context, TtsService::class.java))
        }
    }
}
