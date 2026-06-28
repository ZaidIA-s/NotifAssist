package com.notifassist.voice

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Jembatan ringan antara TtsService dan VoiceCommandService.
 * Saat TTS sedang bicara, VoiceCommandService berhenti mengenali audio agar suara
 * TTS sendiri tidak memicu wake word / perintah palsu (self-trigger).
 */
object SpeakingState {
    val isSpeaking = AtomicBoolean(false)
}
