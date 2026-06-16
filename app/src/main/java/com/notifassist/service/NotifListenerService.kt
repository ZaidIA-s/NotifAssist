package com.notifassist.service

import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.notifassist.engine.MessageParser
import com.notifassist.engine.RuleEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class NotifListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"
        // Longgar agar pesan yang datang ter-batch saat HP standby/Doze tidak terbuang.
        private const val MAX_AGE_MS    = 60_000L
        private const val DEDUP_TTL_MS  = 180_000L  // ingat pesan yang sudah dibaca selama 3 menit
    }

    private lateinit var ruleEngine: RuleEngine
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Dedup: identitas pesan unik -> waktu dibaca
    private val spokenMessages = ConcurrentHashMap<String, Long>()
    // Mutex agar burst notifikasi diproses berurutan (tidak balapan antar coroutine)
    private val processMutex = kotlinx.coroutines.sync.Mutex()

    override fun onCreate() {
        super.onCreate()
        ruleEngine = RuleEngine(this)
        Log.d(TAG, "NotifListenerService started")
    }

    // Saat binding terbentuk — listener siap menerima notifikasi.
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
    }

    // ROM OEM (itelOS/Transsion dsb.) sering memutus binding listener saat proses
    // dibekukan / tekanan memori. Tanpa rebind, onNotificationPosted berhenti dipanggil
    // secara diam-diam meski app masih terlihat di recents. Minta sistem bind ulang.
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected — requesting rebind")
        try {
            requestRebind(ComponentName(this, NotifListenerService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind gagal: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return

        val notifAge = System.currentTimeMillis() - sbn.postTime
        if (notifAge > MAX_AGE_MS) return

        scope.launch {
            // Mutex menjamin urutan: notifikasi diproses satu per satu sesuai datang
            processMutex.withLock {
                try {
                    cleanupOldEntries()
                    val appLabel = getAppLabel(sbn.packageName)
                    val messages = MessageParser.extractMessages(sbn, appLabel)
                    if (messages.isEmpty()) return@withLock

                    val multiMessage = messages.size > 1

                    for (msg in messages) {  // urutan kronologis dari MessagingStyle
                        // Identitas unik per pesan — re-fire notif menghasilkan id sama → di-skip
                        val id = buildString {
                            append(sbn.key); append('|')
                            append(msg.sender); append('|')
                            append(msg.content); append('|')
                            append(msg.timestamp)
                        }
                        if (spokenMessages.containsKey(id)) continue  // sudah dibaca

                        // Pesan beruntun (MessagingStyle) → skip anti-spam interval
                        val skipInterval = msg.fromMessagingStyle || multiMessage
                        val decision = ruleEngine.evaluate(msg, skipInterval = skipInterval)

                        if (decision.shouldSpeak) {
                            spokenMessages[id] = System.currentTimeMillis()
                            Log.d(TAG, "Speak: ${decision.ttsText}")
                            TtsService.speak(
                                context    = this@NotifListenerService,
                                text       = decision.ttsText,
                                pauseMusic = decision.pauseMusic
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification", e)
                }
            }
        }
    }

    private fun cleanupOldEntries() {
        val now = System.currentTimeMillis()
        val it = spokenMessages.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value > DEDUP_TTL_MS) it.remove()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
