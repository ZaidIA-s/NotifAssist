package com.notifassist.voice

/**
 * Menyimpan teks notifikasi terakhir yang dibacakan, untuk perintah "ulangi".
 * Cukup in-memory (relevan hanya selama sesi berkendara).
 */
object LastSpokenStore {
    @Volatile private var lastText: String? = null

    fun record(text: String) { if (text.isNotBlank()) lastText = text }
    fun last(): String? = lastText
}
