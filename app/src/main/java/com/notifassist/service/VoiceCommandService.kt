package com.notifassist.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.notifassist.R
import com.notifassist.ui.MainActivity
import com.notifassist.voice.AdaptiveMicRouter
import com.notifassist.voice.CommandGrammar
import com.notifassist.voice.CommandRouter
import com.notifassist.voice.MicTarget
import com.notifassist.voice.SpeakingState
import com.notifassist.voice.VoicePrefs
import com.notifassist.voice.VoskModelProvider
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.concurrent.thread

/**
 * Foreground service (tipe microphone) untuk perintah suara.
 *
 * State machine:
 *   WAKE    → mic mendengar wake word (grammar = frasa pemicu) ; musik tetap utuh
 *   COMMAND → setelah wake terdeteksi: earcon, grammar = daftar perintah, eksekusi
 *
 * Mic dipilih oleh AdaptiveMicRouter (HP saat ada audio, headset saat hening).
 * Saat TtsService bicara, pengenalan dijeda (SpeakingState) agar tidak self-trigger.
 */
class VoiceCommandService : Service() {

    companion object {
        private const val TAG = "VoiceCommand"
        private const val CHANNEL_ID = "notifassist_voice"
        private const val NOTIF_ID = 2
        private const val SAMPLE_RATE = 16000.0f
        private const val COMMAND_TIMEOUT_MS = 6000L

        fun start(context: Context) {
            if (!isEligible(context)) return
            context.startForegroundService(Intent(context, VoiceCommandService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceCommandService::class.java))
        }

        /** Aktif hanya bila fitur ON, izin mic diberikan, dan model terpasang. */
        fun isEligible(context: Context): Boolean =
            VoicePrefs.isEnabled(context) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED &&
                VoskModelProvider.isModelPresent(context)
    }

    private enum class Mode { WAKE, COMMAND }

    @Volatile private var running = false
    @Volatile private var requestedTarget = MicTarget.PHONE
    private var engineThread: Thread? = null
    private lateinit var micRouter: AdaptiveMicRouter
    private var tone: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()

        // Cek kelayakan SEBELUM startForeground: memulai FGS tipe microphone tanpa izin
        // RECORD_AUDIO akan crash di Android 14. stopSelf cepat tanpa startForeground aman.
        if (!isEligible(this)) {
            Log.w(TAG, "Tidak memenuhi syarat (izin/model/toggle) — berhenti")
            stopSelf()
            return
        }

        setupChannel()
        startForeground(NOTIF_ID, buildNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        try { tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) } catch (_: Exception) {}

        micRouter = AdaptiveMicRouter(this) { target -> requestedTarget = target }

        VoskModelProvider.loadAsync(
            this,
            onReady = { model -> startEngine(model) },
            onError = { e ->
                Log.e(TAG, "Model gagal dimuat: ${e.message}")
                stopSelf()
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startEngine(model: Model) {
        if (running) return
        running = true
        micRouter.start()
        engineThread = thread(name = "vosk-engine", start = true) { runEngine(model) }
    }

    private fun runEngine(model: Model) {
        val wakePhrase = VoicePrefs.wakePhrase(this).lowercase()
        var recognizer = Recognizer(model, SAMPLE_RATE, CommandGrammar.wakeGrammar(wakePhrase))
        var mode = Mode.WAKE
        var commandDeadline = 0L

        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val recordBufBytes = maxOf(minBytes, 2 * SAMPLE_RATE.toInt())
        val frame = ShortArray(2048)

        var activeTarget = requestedTarget
        var record = buildAudioRecord(activeTarget, recordBufBytes) ?: run {
            Log.e(TAG, "AudioRecord gagal dibuat — berhenti")
            recognizer.close(); stopSelf(); return
        }
        try { record.startRecording() } catch (e: Exception) {
            Log.e(TAG, "startRecording gagal: ${e.message}"); record.release(); recognizer.close(); stopSelf(); return
        }

        try {
            while (running) {
                // Rebuild bila mic adaptif berpindah HP <-> headset
                if (requestedTarget != activeTarget) {
                    runCatching { record.stop(); record.release() }
                    activeTarget = requestedTarget
                    val rebuilt = buildAudioRecord(activeTarget, recordBufBytes)
                    if (rebuilt == null) { Thread.sleep(200); continue }
                    record = rebuilt
                    runCatching { record.startRecording() }
                    recognizer.reset()
                    continue
                }

                // Jeda saat TTS bicara (cegah self-trigger) — tetap baca & buang
                val n = record.read(frame, 0, frame.size)
                if (SpeakingState.isSpeaking.get()) continue
                if (n <= 0) continue

                val done = recognizer.acceptWaveForm(frame, n)
                when (mode) {
                    Mode.WAKE -> {
                        val text = if (done) jsonField(recognizer.result, "text")
                                   else jsonField(recognizer.partialResult, "partial")
                        if (text.isNotBlank() && text.contains(wakePhrase)) {
                            recognizer.close()
                            recognizer = Recognizer(model, SAMPLE_RATE, CommandGrammar.commandGrammar())
                            mode = Mode.COMMAND
                            commandDeadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
                            enterCommandMode()
                        }
                    }
                    Mode.COMMAND -> {
                        if (done) {
                            val text = jsonField(recognizer.result, "text")
                            if (text.isNotBlank()) {
                                handleCommand(text)
                                recognizer.close()
                                recognizer = Recognizer(model, SAMPLE_RATE, CommandGrammar.wakeGrammar(wakePhrase))
                                mode = Mode.WAKE
                            }
                        }
                        if (mode == Mode.COMMAND && System.currentTimeMillis() > commandDeadline) {
                            recognizer.close()
                            recognizer = Recognizer(model, SAMPLE_RATE, CommandGrammar.wakeGrammar(wakePhrase))
                            mode = Mode.WAKE
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Engine loop error: ${e.message}")
        } finally {
            runCatching { record.stop(); record.release() }
            runCatching { recognizer.close() }
        }
    }

    private fun buildAudioRecord(target: MicTarget, bufBytes: Int): AudioRecord? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val source = if (target == MicTarget.HEADSET)
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            else
                MediaRecorder.AudioSource.VOICE_RECOGNITION

            val ar = AudioRecord(
                source,
                SAMPLE_RATE.toInt(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufBytes
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) { ar.release(); return null }

            // Mic HP: pin ke built-in mic agar sistem tidak iseng pindah ke headset
            if (target == MicTarget.PHONE) {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC }
                    ?.let { ar.setPreferredDevice(it) }
            }
            ar
        } catch (e: Exception) {
            Log.e(TAG, "buildAudioRecord error: ${e.message}")
            null
        }
    }

    private fun enterCommandMode() {
        try { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (_: Exception) {}
    }

    private fun handleCommand(text: String) {
        val cmd = CommandRouter.parse(text)
        Log.d(TAG, "Perintah: '$text' → $cmd")
        val feedback = CommandRouter.execute(this, cmd)
        if (feedback != null) TtsService.speak(this, feedback, pauseMusic = false)
    }

    private fun jsonField(json: String?, field: String): String = try {
        JSONObject(json ?: "{}").optString(field, "").lowercase().trim()
    } catch (e: Exception) { "" }

    private fun setupChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "NotifAssist Perintah Suara", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Perintah suara aktif")
            .setContentText("Mendengarkan wake word")
            .setSmallIcon(R.drawable.ic_notif_assist)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        if (::micRouter.isInitialized) micRouter.stop()
        runCatching { engineThread?.join(800) }
        tone?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
