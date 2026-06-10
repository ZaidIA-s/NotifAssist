package com.notifassist.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.notifassist.R
import com.notifassist.ui.MainActivity
import kotlinx.coroutines.*

/**
 * Voice Command via push-to-talk.
 * Mendengarkan hanya saat user aktif tap tombol di notifikasi atau di UI.
 * Ini menghindari ERROR_INSUFFICIENT_PERMISSIONS dari Android 12+ background restriction.
 */
class VoiceCommandService : Service() {

    companion object {
        const val ACTION_LISTEN_ONCE = "com.notifassist.VOICE_LISTEN_ONCE"
        const val ACTION_STOP        = "com.notifassist.VOICE_STOP"
        const val CHANNEL_ID         = "notifassist_voice"
        const val NOTIF_ID           = 2
        private const val TAG        = "VoiceCommand"

        var lastNotifText: String? = null

        fun listenOnce(context: Context) {
            context.startForegroundService(
                Intent(context, VoiceCommandService::class.java)
                    .apply { action = ACTION_LISTEN_ONCE }
            )
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        setupNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Siap..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN_ONCE -> startOnce()
            ACTION_STOP        -> { stopSelf(); return START_NOT_STICKY }
        }
        return START_NOT_STICKY
    }

    private fun startOnce() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            TtsService.speak(this, "Perangkat tidak mendukung pengenalan suara.", pauseMusic = false)
            stopSelf()
            return
        }

        updateNotification("Mendengarkan...")
        TtsService.speak(this, "Silakan berbicara.", pauseMusic = false)

        // Beri jeda kecil agar TTS selesai sebelum mikrofon aktif
        scope.launch {
            delay(1200)
            doListen()
        }
    }

    private fun doListen() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d(TAG, "Heard: $matches")
                val text = matches?.firstOrNull()?.lowercase()?.trim()
                if (text != null) processCommand(text)
                else TtsService.speak(this@VoiceCommandService, "Tidak terdengar.", pauseMusic = false)
                cleanup()
            }

            override fun onError(error: Int) {
                Log.w(TAG, "STT error: $error")
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Tidak terdengar. Coba lagi."
                    SpeechRecognizer.ERROR_NETWORK ->
                        "Butuh koneksi internet untuk pengenalan suara."
                    else -> null
                }
                if (msg != null) TtsService.speak(this@VoiceCommandService, msg, pauseMusic = false)
                cleanup()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processCommand(text: String) {
        Log.d(TAG, "Command: '$text'")
        when {
            text.containsAny("pause", "berhenti", "stop", "diam") -> {
                MediaControlService.pauseMusic(this)
                TtsService.speak(this, "Musik dijeda.", pauseMusic = false)
            }
            text.containsAny("play", "lanjut", "putar", "resume") -> {
                MediaControlService.resumeMusic(this)
                TtsService.speak(this, "Musik dilanjutkan.", pauseMusic = false)
            }
            text.containsAny("berikutnya", "next", "skip", "ganti") -> {
                MediaControlService.nextTrack(this)
                TtsService.speak(this, "Lagu berikutnya.", pauseMusic = false)
            }
            text.containsAny("sebelumnya", "previous", "kembali") -> {
                MediaControlService.previousTrack(this)
                TtsService.speak(this, "Lagu sebelumnya.", pauseMusic = false)
            }
            text.containsAny("lagu apa", "sedang diputar", "judul") -> {
                val info = MediaControlService.getNowPlaying(this)
                TtsService.speak(this,
                    if (info != null) "Sedang diputar: $info"
                    else "Tidak ada musik.", pauseMusic = false)
            }
            text.containsAny("baca ulang", "ulangi", "apa tadi") -> {
                val last = lastNotifText
                if (last != null) TtsService.speak(this, last)
                else TtsService.speak(this, "Tidak ada notifikasi terakhir.", pauseMusic = false)
            }
            else -> TtsService.speak(this,
                "Perintah tidak dikenal: $text", pauseMusic = false)
        }
    }

    private fun String.containsAny(vararg kw: String) = kw.any { this.contains(it) }

    private fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        updateNotification("Siap...")
        scope.launch { delay(2000); stopSelf() }
    }

    private fun setupNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Voice Command", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(status: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Command")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notif_assist)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(status))
    }

    override fun onDestroy() { scope.cancel(); speechRecognizer?.destroy(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
