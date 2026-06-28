package com.notifassist.voice

import android.content.Context
import com.notifassist.service.MediaControlService
import com.notifassist.service.TtsService

enum class VoiceCommand { PLAY, PAUSE, NEXT, PREV, VOLUME_UP, VOLUME_DOWN, REPEAT, CANCEL, UNKNOWN }

/**
 * Memetakan teks hasil pengenalan suara ke aksi konkret.
 * Pemisahan parse() vs execute() memudahkan pengujian & penambahan perintah.
 */
object CommandRouter {

    fun parse(text: String): VoiceCommand {
        val t = text.lowercase().trim()
        return when {
            t.isBlank() -> VoiceCommand.UNKNOWN
            t.contains("volume naik") || t.contains("keras")     -> VoiceCommand.VOLUME_UP
            t.contains("volume turun") || t.contains("pelan")    -> VoiceCommand.VOLUME_DOWN
            t.contains("putar") || t.contains("main")            -> VoiceCommand.PLAY
            t.contains("jeda") || t.contains("stop") ||
                t.contains("berhenti")                           -> VoiceCommand.PAUSE
            t.contains("sebelum")                                -> VoiceCommand.PREV
            t.contains("lanjut") || t.contains("berikut")        -> VoiceCommand.NEXT
            t.contains("ulangi") || t.contains("ulang") ||
                t.contains("baca lagi")                          -> VoiceCommand.REPEAT
            t.contains("batal")                                  -> VoiceCommand.CANCEL
            else                                                 -> VoiceCommand.UNKNOWN
        }
    }

    /**
     * Jalankan perintah. Mengembalikan kalimat feedback untuk diucapkan TTS,
     * atau null bila aksi tidak perlu konfirmasi suara.
     */
    fun execute(context: Context, command: VoiceCommand): String? {
        return when (command) {
            VoiceCommand.PLAY        -> { MediaControlService.resumeMusic(context); null }
            VoiceCommand.PAUSE       -> { MediaControlService.pauseMusic(context); null }
            VoiceCommand.NEXT        -> { MediaControlService.nextTrack(context); null }
            VoiceCommand.PREV        -> { MediaControlService.previousTrack(context); null }
            VoiceCommand.VOLUME_UP   -> { MediaControlService.volumeUp(context); null }
            VoiceCommand.VOLUME_DOWN -> { MediaControlService.volumeDown(context); null }
            VoiceCommand.REPEAT      -> {
                val last = LastSpokenStore.last()
                if (last != null) { TtsService.speak(context, last, pauseMusic = true); null }
                else "Tidak ada notifikasi untuk diulang"
            }
            VoiceCommand.CANCEL      -> null
            VoiceCommand.UNKNOWN     -> "Perintah tidak dikenali"
        }
    }
}
