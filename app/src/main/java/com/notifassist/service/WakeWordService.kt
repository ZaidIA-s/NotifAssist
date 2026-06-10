package com.notifassist.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.notifassist.R
import com.notifassist.engine.DrivingModeManager
import com.notifassist.ui.MainActivity
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Foreground service untuk wake word + perintah suara.
 *
 * State machine satu-thread (menghindari race condition pada Vosk recognizer):
 * - WAKE              → Vosk dengar "Hei Alpha"
 * - PENDING_WHISPER   → tunggu TTS "Ya?" selesai
 * - COLLECTING_WHISPER→ kumpulkan 4.5s audio untuk Whisper (Indonesia)
 * - COMMAND_VOSK      → fallback: Vosk dengar perintah English (jika Whisper tak ada)
 *
 * Semua operasi recognizer (create/close/acceptWaveForm) HANYA di loop thread.
 */
class WakeWordService : Service() {

    companion object {
        const val ACTION_START = "com.notifassist.WAKE_START"
        const val ACTION_STOP  = "com.notifassist.WAKE_STOP"
        const val CHANNEL_ID   = "notifassist_wake"
        const val NOTIF_ID     = 3
        private const val TAG  = "WakeWord"

        private val WAKE_WORDS = listOf(
            "hey alpha", "hei alpha", "hey alfa", "a alpha",
            "hey alpa", "hi alpha", "he alpha", "hai alfa", "hai alpha"
        )

        var lastNotifText: String? = null

        const val ACTION_HEARD = "com.notifassist.WAKE_HEARD"
        const val EXTRA_HEARD  = "heard_text"
        const val EXTRA_TYPE   = "heard_type"
        var lastHeard: String = ""
            private set

        fun start(context: Context) = context.startForegroundService(
            Intent(context, WakeWordService::class.java).apply { action = ACTION_START }
        )
        fun stop(context: Context) = context.startService(
            Intent(context, WakeWordService::class.java).apply { action = ACTION_STOP }
        )
    }

    private enum class ListenState { WAKE, PENDING_WHISPER, COLLECTING_WHISPER, COMMAND_VOSK }

    // VOSK
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRunning = false

    // WHISPER
    private var whisperEngine: WhisperCommandEngine? = null
    private val whisperBuffer = ArrayList<Short>(16000 * 5)

    // State machine
    @Volatile private var state = ListenState.WAKE
    private var stateDeadline = 0L
    private val PENDING_MS  = 900L
    private val WHISPER_MS  = 4500L
    private val COMMAND_MS  = 4000L

    // Resilience & routing
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var scoStarted = false
    private var restartCount = 0

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val wakeVocab = """["hey alpha", "hey alfa", "hei alpha", "hi alpha",
        "hai alfa", "hai alpha", "hey alpa", "a alpha", "he alpha", "[unk]"]"""
    private val cmdVocab = """["pause", "stop", "play", "resume", "next", "skip",
        "previous", "back", "repeat", "info", "song", "[unk]"]"""

    override fun onCreate() {
        super.onCreate()
        setupNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Memuat model..."))
        acquireWakeLock()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        initWhisper()
        loadVoskModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!isRunning) startListening()
            ACTION_STOP  -> stopListening()
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NotifAssist::WakeWord").apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 60 * 1000L)
        }
        Log.d(TAG, "Wake lock acquired")
    }

    private fun startBluetoothScoIfNeeded() {
        val am = audioManager ?: return
        if (!DrivingModeManager.isBluetoothMicEnabled(this)) return
        if (!am.isBluetoothScoAvailableOffCall) { Log.d(TAG, "BT SCO tak tersedia"); return }
        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION") am.startBluetoothSco()
            @Suppress("DEPRECATION") am.isBluetoothScoOn = true
            scoStarted = true
            Log.d(TAG, "Bluetooth SCO started — pakai mic headset")
        } catch (e: Exception) { Log.e(TAG, "SCO gagal: ${e.message}") }
    }

    private fun stopBluetoothSco() {
        val am = audioManager ?: return
        if (scoStarted) {
            try {
                @Suppress("DEPRECATION") am.stopBluetoothSco()
                @Suppress("DEPRECATION") am.isBluetoothScoOn = false
                am.mode = AudioManager.MODE_NORMAL
            } catch (_: Exception) {}
            scoStarted = false
        }
    }

    private fun initWhisper() {
        scope.launch {
            val engine = WhisperCommandEngine(this@WakeWordService)
            if (engine.isModelBundled() && engine.init()) {
                whisperEngine = engine
                Log.d(TAG, "Whisper ready (bundled)")
            } else {
                Log.d(TAG, "Whisper tak tersedia — fallback Vosk English")
            }
        }
    }

    private fun loadVoskModel() {
        scope.launch {
            try {
                updateNotification("Memuat model suara...")
                val path = getModelPath() ?: run {
                    updateNotification("ERROR: Model Vosk tak ditemukan"); return@launch
                }
                model = Model(path)
                startListening()
            } catch (e: Exception) {
                Log.e(TAG, "Model load gagal: ${e.message}")
                updateNotification("ERROR: Gagal muat model")
            }
        }
    }

    private fun getModelPath(): String? {
        val destDir = File(filesDir, "model-en")
        if (destDir.exists() && File(destDir, "am/final.mdl").exists()) return destDir.absolutePath
        return try { copyAssetFolder("model-en", destDir); destDir.absolutePath }
        catch (e: Exception) { null }
    }

    private fun copyAssetFolder(assetPath: String, destDir: File) {
        destDir.mkdirs()
        val am = applicationContext.assets
        val entries = am.list(assetPath) ?: return
        for (entry in entries) {
            val srcPath = "$assetPath/$entry"
            val dstFile = File(destDir, entry)
            if (!am.list(srcPath).isNullOrEmpty()) copyAssetFolder(srcPath, dstFile)
            else am.open(srcPath).use { i -> dstFile.outputStream().use { o -> i.copyTo(o) } }
        }
    }

    private fun startListening() {
        if (model == null || isRunning) return
        isRunning = true
        startBluetoothScoIfNeeded()
        state = ListenState.WAKE
        updateNotification("Mendengarkan... ucapkan 'Hei Alpha'")

        scope.launch {
            try {
                recognizer  = Recognizer(model, 16000.0f, wakeVocab)
                audioRecord = createAudioRecord()
                audioRecord?.startRecording()
                Log.d(TAG, "Listening for wake word...")

                val buffer = ShortArray(4096)
                var errorStreak = 0

                while (isRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read < 0) {
                        errorStreak++
                        Log.w(TAG, "AudioRecord error: $read (streak $errorStreak)")
                        if (errorStreak >= 5) { restartAudioRecord(); errorStreak = 0 }
                        delay(100); continue
                    }
                    errorStreak = 0
                    if (read == 0) continue

                    when (state) {
                        ListenState.WAKE               -> handleWake(buffer, read)
                        ListenState.PENDING_WHISPER    -> handlePendingWhisper()
                        ListenState.COLLECTING_WHISPER -> handleCollectingWhisper(buffer, read)
                        ListenState.COMMAND_VOSK       -> handleCommandVosk(buffer, read)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Listen error: ${e.message}")
                isRunning = false
            }
        }
    }

    // ── State: WAKE ───────────────────────────────────────────────────────
    private fun handleWake(buffer: ShortArray, read: Int) {
        val bytes = shortArrayToByteArray(buffer, read)
        val rec = recognizer ?: return
        if (rec.acceptWaveForm(bytes, bytes.size)) {
            val text = parseVoskText(rec.result ?: "")
            if (text.isNotEmpty()) {
                val isWake = WAKE_WORDS.any { text.contains(it) }
                broadcastHeard(text, if (isWake) "wake" else "noise")
                if (isWake) {
                    Log.d(TAG, "WAKE WORD DETECTED: '$text'")
                    onWakeWordDetected()
                }
            }
        } else {
            val partial = parseVoskPartial(rec.partialResult ?: "")
            if (partial.isNotEmpty() && partial != lastHeard) broadcastHeard(partial, "partial")
        }
    }

    private fun onWakeWordDetected() {
        TtsService.speak(this, "Ya?", pauseMusic = false)
        val whisper = whisperEngine
        if (whisper != null && whisper.isReady) {
            // Tunggu TTS selesai dulu (PENDING), lalu kumpulkan audio
            state = ListenState.PENDING_WHISPER
            stateDeadline = System.currentTimeMillis() + PENDING_MS
            updateNotification("Dengarkan perintah...")
        } else {
            // Fallback: ganti recognizer ke command vocab (DI THREAD LOOP — aman)
            recognizer?.close()
            recognizer = Recognizer(model, 16000.0f, cmdVocab)
            state = ListenState.COMMAND_VOSK
            stateDeadline = System.currentTimeMillis() + COMMAND_MS
            updateNotification("Dengarkan perintah (English)...")
        }
    }

    // ── State: PENDING_WHISPER ──────────────────────────────────────────────
    private fun handlePendingWhisper() {
        if (System.currentTimeMillis() >= stateDeadline) {
            whisperBuffer.clear()
            state = ListenState.COLLECTING_WHISPER
            stateDeadline = System.currentTimeMillis() + WHISPER_MS
        }
        // selama pending, audio dibuang (jangan rekam TTS "Ya?")
    }

    // ── State: COLLECTING_WHISPER ──────────────────────────────────────────
    private fun handleCollectingWhisper(buffer: ShortArray, read: Int) {
        for (i in 0 until read) whisperBuffer.add(buffer[i])
        if (System.currentTimeMillis() >= stateDeadline) {
            val captured = whisperBuffer.toShortArray()
            whisperBuffer.clear()
            state = ListenState.WAKE
            updateNotification("Mendengarkan... ucapkan 'Hei Alpha'")
            processWithWhisper(captured)
        }
    }

    private fun processWithWhisper(audioShorts: ShortArray) {
        scope.launch {
            try {
                val engine = whisperEngine ?: return@launch
                val floats = FloatArray(audioShorts.size) { audioShorts[it] / 32768f }
                val text = engine.transcribeFloats(floats)
                Log.d(TAG, "Whisper result: '$text'")
                if (!text.isNullOrBlank()) {
                    broadcastHeard(text, "command")
                    processIndonesianCommand(text)
                } else {
                    TtsService.speak(this@WakeWordService, "Maaf, tidak terdengar.", pauseMusic = false)
                }
            } catch (e: Exception) { Log.e(TAG, "Whisper proc: ${e.message}") }
        }
    }

    // ── State: COMMAND_VOSK (fallback English) ─────────────────────────────
    private fun handleCommandVosk(buffer: ShortArray, read: Int) {
        val bytes = shortArrayToByteArray(buffer, read)
        val rec = recognizer ?: return
        val done = rec.acceptWaveForm(bytes, bytes.size)
        if (done) {
            val text = parseVoskText(rec.result ?: "")
            if (text.isNotEmpty()) {
                broadcastHeard(text, "command")
                processEnglishCommand(text)
            }
            exitToWake()
        } else if (System.currentTimeMillis() >= stateDeadline) {
            // Timeout — pakai result biasa (BUKAN finalResult, agar tak crash)
            val text = parseVoskText(rec.result ?: "")
            if (text.isNotEmpty()) { broadcastHeard(text, "command"); processEnglishCommand(text) }
            exitToWake()
        }
    }

    private fun exitToWake() {
        recognizer?.close()
        recognizer = Recognizer(model, 16000.0f, wakeVocab)
        state = ListenState.WAKE
        updateNotification("Mendengarkan... ucapkan 'Hei Alpha'")
    }

    // ── Pemrosesan perintah ─────────────────────────────────────────────────
    private fun processIndonesianCommand(text: String) {
        Log.d(TAG, "Perintah ID: '$text'")
        when {
            Regex("\\b(pause|berhenti|stop|diam|jeda|tahan|senyap)\\b").containsMatchIn(text) -> {
                MediaControlService.pauseMusic(this); reply("Musik dijeda.")
            }
            Regex("\\b(play|lanjut|putar|resume|nyalakan|teruskan|mainkan)\\b").containsMatchIn(text) -> {
                MediaControlService.resumeMusic(this); reply("Musik dilanjutkan.")
            }
            Regex("\\b(next|skip|berikutnya|selanjutnya|ganti lagu|lagu lain|lagu baru)\\b").containsMatchIn(text) -> {
                MediaControlService.nextTrack(this); reply("Lagu berikutnya.")
            }
            Regex("\\b(previous|back|sebelumnya|kembali|mundur|balik)\\b").containsMatchIn(text) -> {
                MediaControlService.previousTrack(this); reply("Lagu sebelumnya.")
            }
            Regex("\\b(lagu apa|apa lagu|lagi apa|judul|info)\\b").containsMatchIn(text) -> {
                val np = MediaControlService.getNowPlaying(this)
                reply(if (np != null) "Sedang diputar: $np" else "Tidak ada musik.")
            }
            Regex("\\b(baca ulang|ulangi|ulang|tadi|apa tadi|repeat|sekali lagi|notif apa)\\b").containsMatchIn(text) -> {
                lastNotifText?.let { TtsService.speak(this, it) } ?: reply("Tidak ada notifikasi terakhir.")
            }
            else -> reply("Maaf, tidak mengerti. Coba lagi.")
        }
    }

    private fun processEnglishCommand(text: String) {
        when {
            text.contains("pause") || text.contains("stop") -> { MediaControlService.pauseMusic(this); reply("Musik dijeda.") }
            text.contains("play")  || text.contains("resume") -> { MediaControlService.resumeMusic(this); reply("Musik dilanjutkan.") }
            text.contains("next")  || text.contains("skip") -> { MediaControlService.nextTrack(this); reply("Lagu berikutnya.") }
            text.contains("previous") || text.contains("back") -> { MediaControlService.previousTrack(this); reply("Lagu sebelumnya.") }
            text.contains("info") || text.contains("song") -> {
                val np = MediaControlService.getNowPlaying(this)
                reply(if (np != null) "Sedang diputar: $np" else "Tidak ada musik.")
            }
            text.contains("repeat") -> { lastNotifText?.let { TtsService.speak(this, it) } ?: reply("Tidak ada notifikasi terakhir.") }
        }
    }

    private fun reply(msg: String) = TtsService.speak(this, msg, pauseMusic = false)

    // ── Util ────────────────────────────────────────────────────────────────
    private fun broadcastHeard(text: String, type: String) {
        lastHeard = text
        sendBroadcast(Intent(ACTION_HEARD).apply {
            putExtra(EXTRA_HEARD, text); putExtra(EXTRA_TYPE, type); setPackage(packageName)
        })
        if (type == "wake" || type == "command") updateNotification("Terdengar: \"$text\"")
    }

    private fun restartAudioRecord() {
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        restartCount++
        stopBluetoothSco(); startBluetoothScoIfNeeded()
        Thread.sleep(200)
        try {
            audioRecord = createAudioRecord(); audioRecord?.startRecording()
            Log.d(TAG, "AudioRecord restarted ($restartCount)")
        } catch (e: Exception) { Log.e(TAG, "Restart gagal: ${e.message}") }
    }

    private fun parseVoskText(json: String): String =
        json.substringAfter("\"text\" : \"", "").substringBefore("\"").trim().lowercase()

    private fun parseVoskPartial(json: String): String =
        json.substringAfter("\"partial\" : \"", "").substringBefore("\"").trim().lowercase()

    private fun stopListening() {
        isRunning = false
        stopBluetoothSco()
        audioRecord?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
        audioRecord = null
        recognizer?.close(); recognizer = null
        updateNotification("Nonaktif")
        stopSelf()
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private fun createAudioRecord(): AudioRecord {
        val rate = 16000
        val bufSize = AudioRecord.getMinBufferSize(rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(8192)
        return AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
    }

    private fun shortArrayToByteArray(shorts: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            bytes[i * 2]     = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8).toByte()
        }
        return bytes
    }

    private fun setupNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Wake Word", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "NotifAssist mendengarkan wake word" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(status: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hei Alpha")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notif_assist)
            .setContentIntent(pi).setOngoing(true).build()
    }

    private fun updateNotification(status: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(status))

    override fun onDestroy() {
        stopListening()
        stopBluetoothSco()
        wakeLock?.let { if (it.isHeld) it.release() }
        model?.close()
        whisperEngine?.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
