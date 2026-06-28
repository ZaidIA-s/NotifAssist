package com.notifassist.voice

import org.json.JSONArray

/**
 * Grammar terbatas untuk KaldiRecognizer Vosk.
 *
 * Memakai grammar (bukan ASR bebas) membuat pengenalan jauh lebih akurat & ringan —
 * recognizer hanya boleh menebak dari daftar frasa ini + "[unk]" (kata di luar daftar).
 * Ini krusial karena model ID komunitas akurasinya terbatas.
 */
object CommandGrammar {

    /**
     * Frasa perintah yang dikenali saat mode COMMAND.
     * Semua kata sudah diverifikasi ADA di vocabulary model bookbot (graph/words.txt).
     * Catatan: "ulangi"/"stop" TIDAK ada di vocab → pakai "ulang"/"jeda".
     */
    val COMMAND_PHRASES = listOf(
        "putar",
        "jeda",
        "lanjut",
        "sebelumnya",
        "volume naik",
        "volume turun",
        "ulang",
        "batal"
    )

    /** Grammar wake word — hanya frasa pemicu + unknown. */
    fun wakeGrammar(wakePhrase: String): String =
        toJsonArray(listOf(wakePhrase, "[unk]"))

    /** Grammar perintah — semua frasa perintah + unknown. */
    fun commandGrammar(): String =
        toJsonArray(COMMAND_PHRASES + "[unk]")

    private fun toJsonArray(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }
}
