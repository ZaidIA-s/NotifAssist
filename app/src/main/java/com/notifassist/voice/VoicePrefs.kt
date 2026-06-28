package com.notifassist.voice

import android.content.Context
import android.content.SharedPreferences

/**
 * Pengaturan fitur perintah suara (wake word). Disimpan terpisah dari tts_prefs.
 * Default fitur OFF — pengguna mengaktifkan secara sadar (mic always-on = pertimbangan privasi).
 */
object VoicePrefs {

    const val NAME = "voice_prefs"

    const val KEY_ENABLED     = "vc_enabled"
    const val KEY_WAKE_PHRASE = "vc_wake_phrase"
    const val KEY_MIC_MODE    = "vc_mic_mode"
    const val KEY_SENSITIVITY = "vc_sensitivity"

    // Mode mic
    const val MIC_ADAPTIVE = "adaptive"
    const val MIC_PHONE    = "phone"
    const val MIC_HEADSET  = "headset"

    // PENTING: hanya pakai kata yang ADA di vocabulary model (graph/words.txt).
    // Model bookbot TIDAK punya: "notif", "oke", "asisten", "beta", "bravo", "delta" → dihindari.
    // Semua kata di bawah sudah diverifikasi ada di vocab & khas (false-positive rendah).
    const val DEFAULT_WAKE_PHRASE = "hai alfa"
    const val DEFAULT_SENSITIVITY = 0.5f

    val WAKE_PHRASES = listOf("hai alfa", "galaksi", "kosmos", "komandan")

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        get(context).getBoolean(KEY_ENABLED, false)

    fun wakePhrase(context: Context): String =
        get(context).getString(KEY_WAKE_PHRASE, DEFAULT_WAKE_PHRASE) ?: DEFAULT_WAKE_PHRASE

    fun micMode(context: Context): String =
        get(context).getString(KEY_MIC_MODE, MIC_ADAPTIVE) ?: MIC_ADAPTIVE

    fun sensitivity(context: Context): Float =
        get(context).getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
}
