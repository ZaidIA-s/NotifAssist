package com.notifassist.service

import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.notifassist.R
import com.notifassist.engine.DrivingModeManager
import com.notifassist.receiver.BluetoothReceiver
import com.notifassist.ui.MainActivity

class DrivingModeService : Service() {

    companion object {
        const val CHANNEL_ID = "notifassist_driving"
        const val NOTIF_ID   = 4

        fun start(context: android.content.Context) =
            context.startForegroundService(Intent(context, DrivingModeService::class.java))

        fun stop(context: android.content.Context) =
            context.stopService(Intent(context, DrivingModeService::class.java))
    }

    private val btReceiver = BluetoothReceiver()

    override fun onCreate() {
        super.onCreate()
        setupChannel()
        startForeground(NOTIF_ID, buildNotif("Menunggu koneksi Bluetooth..."))

        // Daftar BT receiver secara dynamic
        val filter = IntentFilter().apply {
            BluetoothReceiver.ACTIONS.forEach { addAction(it) }
        }
        registerReceiver(btReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(btReceiver)
        DrivingModeManager.setDriving(this, false)
        super.onDestroy()
    }

    private fun setupChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Mode Berkendara", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(status: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mode Berkendara")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notif_assist)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    fun updateStatus(status: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif(status))

    override fun onBind(intent: Intent?): IBinder? = null
}
