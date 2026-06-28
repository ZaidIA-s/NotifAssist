package com.notifassist.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.notifassist.service.MediaControlService

enum class MicTarget { PHONE, HEADSET }

/**
 * Mic adaptif — inti solusi masalah Bluetooth lama.
 *
 * Aturan (mode "adaptive"):
 *  - Headset BT tidak ada                        → MIC HP
 *  - Headset BT ada + sedang ada audio keluar    → MIC HP  (SCO off → musik A2DP tetap HD)
 *  - Headset BT ada + hening (lewat debounce)    → MIC HEADSET (SCO on, pickup lebih dekat)
 *
 * Transisi ke HP dilakukan SEGERA (lindungi kualitas musik); transisi ke HEADSET pakai
 * debounce hening agar tidak bolak-balik (thrashing) saat notif beruntun.
 *
 * Memakai API modern setCommunicationDevice()/clearCommunicationDevice() (API 31+),
 * BUKAN startBluetoothSco() yang sudah deprecated & jadi sumber bug lama.
 */
class AdaptiveMicRouter(
    private val context: Context,
    private val onTargetChanged: (MicTarget) -> Unit
) {
    companion object {
        private const val TAG = "AdaptiveMic"
        private const val SILENCE_DEBOUNCE_MS = 4000L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    @Volatile private var currentTarget: MicTarget = MicTarget.PHONE

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            evaluate()
        }
    }

    fun start() {
        if (started) return
        started = true
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        // Mulai dari PHONE secara default lalu evaluasi
        applyTarget(MicTarget.PHONE, forceCallback = true)
        evaluate()
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacksAndMessages(null)
        try { audioManager.unregisterAudioPlaybackCallback(playbackCallback) } catch (_: Exception) {}
        // Kembalikan rute audio ke normal
        try { audioManager.clearCommunicationDevice() } catch (_: Exception) {}
    }

    /** Panggil saat pengaturan mode mic berubah. */
    fun reevaluate() { if (started) evaluate() }

    fun currentTarget(): MicTarget = currentTarget

    // ── Logika ───────────────────────────────────────────────────────────────

    private fun isBtScoAvailable(): Boolean = try {
        audioManager.availableCommunicationDevices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    } catch (e: Exception) { false }

    private fun isAudioPlaying(): Boolean =
        audioManager.isMusicActive || MediaControlService.isMusicPlaying(context)

    private fun desiredTarget(): MicTarget = when (VoicePrefs.micMode(context)) {
        VoicePrefs.MIC_PHONE   -> MicTarget.PHONE
        VoicePrefs.MIC_HEADSET -> if (isBtScoAvailable()) MicTarget.HEADSET else MicTarget.PHONE
        else /* adaptive */    -> when {
            !isBtScoAvailable() -> MicTarget.PHONE
            isAudioPlaying()    -> MicTarget.PHONE
            else                -> MicTarget.HEADSET
        }
    }

    private fun evaluate() {
        if (!started) return
        val desired = desiredTarget()
        handler.removeCallbacksAndMessages(null)
        if (desired == MicTarget.PHONE) {
            // Segera — jangan biarkan SCO merusak musik
            applyTarget(MicTarget.PHONE)
        } else {
            // Tunggu hening stabil sebelum pindah ke headset (anti-thrashing)
            handler.postDelayed({
                if (started && desiredTarget() == MicTarget.HEADSET) {
                    applyTarget(MicTarget.HEADSET)
                }
            }, SILENCE_DEBOUNCE_MS)
        }
    }

    private fun applyTarget(target: MicTarget, forceCallback: Boolean = false) {
        if (target == currentTarget && !forceCallback) return
        currentTarget = target
        try {
            when (target) {
                MicTarget.HEADSET -> {
                    val sco = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                    if (sco != null) {
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        audioManager.setCommunicationDevice(sco)
                        Log.d(TAG, "Mic → HEADSET (SCO)")
                    } else {
                        // Headset hilang di saat akhir → fallback HP
                        audioManager.clearCommunicationDevice()
                        audioManager.mode = AudioManager.MODE_NORMAL
                        currentTarget = MicTarget.PHONE
                        onTargetChanged(MicTarget.PHONE)
                        return
                    }
                }
                MicTarget.PHONE -> {
                    audioManager.clearCommunicationDevice()
                    audioManager.mode = AudioManager.MODE_NORMAL
                    Log.d(TAG, "Mic → HP (built-in)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyTarget error: ${e.message}")
        }
        onTargetChanged(target)
    }
}
