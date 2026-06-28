package com.notifassist.voice

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.android.StorageService

/**
 * Memuat model Vosk Bahasa Indonesia yang dibundel di assets/model-id.
 *
 * Vosk perlu path file nyata, jadi StorageService.unpack menyalin model dari assets
 * ke filesDir sekali, lalu membuat objek Model. Hasilnya di-cache (model native mahal).
 *
 * Bila model belum dipasang (lihat assets/model-id/PLACEHOLDER_README.txt), isModelPresent()
 * mengembalikan false sehingga fitur perintah suara dinonaktifkan dengan aman tanpa crash.
 */
object VoskModelProvider {

    private const val TAG = "VoskModel"
    private const val ASSET_DIR = "model-id"     // folder di app/src/main/assets
    private const val TARGET_DIR = "vosk-model"  // sub-folder di filesDir

    @Volatile private var model: Model? = null

    /** True bila folder model di assets berisi file model (bukan sekadar placeholder). */
    fun isModelPresent(context: Context): Boolean {
        return try {
            val files = context.assets.list(ASSET_DIR) ?: return false
            // Folder valid harus punya struktur model (am/, conf/, graph/, dst),
            // bukan cuma PLACEHOLDER_README.txt.
            files.any { it != "PLACEHOLDER_README.txt" }
        } catch (e: Exception) {
            false
        }
    }

    fun cached(): Model? = model

    /**
     * Muat model secara async. onReady dipanggil di thread Vosk (bukan main thread).
     */
    fun loadAsync(context: Context, onReady: (Model) -> Unit, onError: (Exception) -> Unit) {
        model?.let { onReady(it); return }
        if (!isModelPresent(context)) {
            onError(IllegalStateException("Model Vosk tidak ditemukan di assets/$ASSET_DIR"))
            return
        }
        try {
            StorageService.unpack(
                context, ASSET_DIR, TARGET_DIR,
                { m ->
                    model = m
                    Log.d(TAG, "Model Vosk siap")
                    onReady(m)
                },
                { e ->
                    Log.e(TAG, "Gagal unpack model: ${e.message}")
                    onError(e)
                }
            )
        } catch (e: Exception) {
            onError(e)
        }
    }
}
