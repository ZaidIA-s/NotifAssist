package com.notifassist.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.notifassist.service.TtsService
import com.notifassist.service.VoiceCommandService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot complete — starting services")
            context.startForegroundService(Intent(context, TtsService::class.java))
            // Hidupkan perintah suara bila diaktifkan (start() sudah cek kelayakan)
            VoiceCommandService.start(context)
        }
    }
}
