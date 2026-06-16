package com.notifassist.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.*
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.notifassist.R
import com.notifassist.ui.MainActivity
import kotlinx.coroutines.*
import java.util.LinkedList
import java.util.Locale
import java.util.UUID

/**
 * Service TTS menggunakan Google Text-to-Speech (bawaan Android).
 * Mengelola antrian ucapan + pause/resume musik otomatis.
 *
 * Untuk suara lebih natural, user upgrade Google TTS di:
 * Pengaturan HP → Bahasa → Text-to-Speech → Google TTS → download voice Indonesia
 */
class TtsService : Service() {

    companion object {
        const val ACTION_SPEAK      = "com.notifassist.SPEAK"
        const val ACTION_RELOAD     = "com.notifassist.TTS_RELOAD"
        const val EXTRA_TEXT        = "extra_text"
        const val EXTRA_PAUSE_MUSIC = "extra_pause_music"
        const val CHANNEL_ID        = "notifassist_fg"
        const val NOTIF_ID          = 1
        private const val TAG       = "TtsService"

        const val PREFS_NAME     = "tts_prefs"
        const val KEY_VOICE_NAME = "voice_name"
        const val KEY_SPEED      = "speed"
        const val KEY_PITCH      = "pitch"

        /** Jalankan service sebagai foreground persisten (tanpa bicara) agar
         *  proses tetap hidup & listener notifikasi tidak dibekukan sistem. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, TtsService::class.java))
        }

        fun speak(context: Context, text: String, pauseMusic: Boolean = true) {
            context.startForegroundService(
                Intent(context, TtsService::class.java).apply {
                    action = ACTION_SPEAK
                    putExtra(EXTRA_TEXT, text)
                    putExtra(EXTRA_PAUSE_MUSIC, pauseMusic)
                }
            )
        }

        fun reload(context: Context) {
            context.startForegroundService(
                Intent(context, TtsService::class.java).apply { action = ACTION_RELOAD }
            )
        }

        fun getPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private lateinit var tts: TextToSpeech
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private var ttsReady = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val queue = LinkedList<Triple<String, Boolean, String>>()
    private var isSpeaking = false
    private var musicWasPlaying = false

    override fun onCreate() {
        super.onCreate()
        setupNotificationChannel()
        startForeground(NOTIF_ID, buildNotif())
        initGoogleTts()
        initAudioManager()
    }

    private fun initGoogleTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applySettings()
                tts.setOnUtteranceProgressListener(utteranceListener)
                ttsReady = true
                processQueue()
            }
        }
    }

    fun applySettings() {
        val prefs = getPrefs(this)
        val voiceName = prefs.getString(KEY_VOICE_NAME, "") ?: ""
        val speed = prefs.getFloat(KEY_SPEED, 0.95f)
        val pitch = prefs.getFloat(KEY_PITCH, 1.0f)

        if (voiceName.isNotBlank()) {
            val target = tts.voices?.find { it.name == voiceName }
            if (target != null) tts.voice = target
            else setIndonesianOrEnglish()
        } else {
            setIndonesianOrEnglish()
        }
        tts.setSpeechRate(speed)
        tts.setPitch(pitch)
    }

    private fun setIndonesianOrEnglish() {
        val result = tts.setLanguage(Locale("id", "ID"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
    }

    private fun initAudioManager() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RELOAD -> { if (ttsReady) applySettings(); return START_NOT_STICKY }
            ACTION_SPEAK  -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return START_NOT_STICKY
                val pauseMusic = intent.getBooleanExtra(EXTRA_PAUSE_MUSIC, true)
                queue.add(Triple(text, pauseMusic, UUID.randomUUID().toString()))
                if (ttsReady) processQueue()
            }
        }
        // START_STICKY: sistem menjalankan ulang service ini bila sempat dimatikan,
        // sehingga pemantauan notifikasi tetap berjalan.
        return START_STICKY
    }

    private fun processQueue() {
        if (isSpeaking || queue.isEmpty()) return
        val (text, pauseMusic, uttId) = queue.poll() ?: return
        isSpeaking = true

        if (pauseMusic) {
            musicWasPlaying = MediaControlService.isMusicPlaying(this)
            if (musicWasPlaying) {
                MediaControlService.pauseMusic(this)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    doSpeak(text, uttId)
                }, 350)
                return
            }
        }
        doSpeak(text, uttId)
    }

    private fun doSpeak(text: String, uttId: String) {
        audioManager.requestAudioFocus(focusRequest)
        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uttId)
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, params, uttId)
    }

    private fun onTtsDone() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        if (musicWasPlaying && queue.isEmpty()) {
            scope.launch {
                delay(400)
                MediaControlService.resumeMusic(this@TtsService)
                musicWasPlaying = false
            }
        }
        isSpeaking = false
        scope.launch { processQueue() }
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {}
        override fun onDone(id: String?)  { onTtsDone() }
        override fun onError(id: String?) { onTtsDone() }
    }

    private fun setupNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "NotifAssist Active", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotifAssist aktif")
            .setContentText("Memantau notifikasi")
            .setSmallIcon(R.drawable.ic_notif_assist)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
