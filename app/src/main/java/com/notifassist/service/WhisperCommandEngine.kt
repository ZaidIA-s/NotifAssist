package com.notifassist.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whisper Tiny untuk transkripsi perintah bahasa Indonesia.
 * Menggunakan sherpa-onnx (OfflineRecognizer) dengan model yang sudah
 * di-bundle di app/src/main/assets/whisper-tiny/.
 *
 * Model di-copy dari assets ke internal storage sekali saat pertama jalan,
 * lalu dimuat via path filesystem (AssetManager=null).
 *
 * File model yang harus ada di assets/whisper-tiny/:
 * - encoder.int8.onnx
 * - decoder.int8.onnx
 * - tokens.txt
 */
class WhisperCommandEngine(private val context: Context) {

    companion object {
        private const val TAG       = "WhisperCmd"
        private const val ASSET_DIR = "whisper-tiny"
        private const val MODEL_DIR = "whisper-tiny"
        private const val MIN_SIZE  = 1_000_000L

        private val REQUIRED = listOf("encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt")
    }

    private var recognizer: Any? = null
    var isReady = false
        private set

    private val modelDir: File get() = File(context.filesDir, MODEL_DIR)

    /** Cek apakah model tersedia di assets (bundled) */
    fun isModelBundled(): Boolean = try {
        val list = context.assets.list(ASSET_DIR) ?: emptyArray()
        REQUIRED.all { it in list }
    } catch (e: Exception) { false }

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isModelBundled()) {
                Log.w(TAG, "Model Whisper tidak ada di assets — perintah pakai Vosk English fallback")
                return@withContext false
            }
            copyAssetsIfNeeded()
            loadRecognizer()
        } catch (e: Exception) {
            Log.e(TAG, "Init gagal: ${e.message}")
            false
        }
    }

    /** Copy model dari assets ke internal storage (hanya sekali) */
    private fun copyAssetsIfNeeded() {
        val dir = modelDir.apply { mkdirs() }
        val encoder = File(dir, "encoder.int8.onnx")
        if (encoder.exists() && encoder.length() >= MIN_SIZE) {
            Log.d(TAG, "Model sudah di internal storage")
            return
        }
        Log.d(TAG, "Copy model Whisper dari assets...")
        for (name in REQUIRED) {
            context.assets.open("$ASSET_DIR/$name").use { input ->
                File(dir, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
        Log.d(TAG, "Copy selesai — encoder: ${encoder.length() / 1_000_000}MB")
    }

    /** Cari setter yang menerima parameter dengan tipe tertentu (unik) */
    private fun findSetterByType(clazz: Class<*>, paramType: Class<*>): java.lang.reflect.Method? =
        clazz.methods.firstOrNull {
            it.name.startsWith("set") && it.parameterTypes.size == 1 &&
            it.parameterTypes[0] == paramType
        }

    /** Panggil setter berdasarkan nama field (untuk tipe yang tidak unik seperti String) */
    private fun invokeSetterByName(clazz: Class<*>, target: Any, field: String,
                                    paramType: Class<*>, value: Any) {
        try {
            clazz.getMethod("set$field", paramType).invoke(target, value)
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "Setter set$field tidak ditemukan, dilewati")
        }
    }

    private fun loadRecognizer(): Boolean {
        return try {
            val dir = modelDir
            val whisperCfgClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig")
            val modelCfgClass   = Class.forName("com.k2fsa.sherpa.onnx.OfflineModelConfig")
            val recCfgClass     = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig")
            val recognizerClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")

            // OfflineWhisperModelConfig(encoder, decoder, language, task, tailPaddings)
            val whisperCfg = whisperCfgClass.getConstructor(
                String::class.java, String::class.java,
                String::class.java, String::class.java, Int::class.java
            ).newInstance(
                File(dir, "encoder.int8.onnx").absolutePath,
                File(dir, "decoder.int8.onnx").absolutePath,
                "id", "transcribe", 0
            )

            val modelCfg = modelCfgClass.getDeclaredConstructor().newInstance()
            // setWhisper(OfflineWhisperModelConfig) — tipe unik, cari by type
            findSetterByType(modelCfgClass, whisperCfgClass)?.invoke(modelCfg, whisperCfg)
                ?: throw NoSuchMethodException("setter whisper")
            // setTokens(String) & setModelType(String) — keduanya String, cari by name
            invokeSetterByName(modelCfgClass, modelCfg, "Tokens", String::class.java,
                File(dir, "tokens.txt").absolutePath)
            invokeSetterByName(modelCfgClass, modelCfg, "ModelType", String::class.java, "whisper")
            invokeSetterByName(modelCfgClass, modelCfg, "NumThreads", Int::class.java, 2)

            val recCfg = recCfgClass.getDeclaredConstructor().newInstance()
            // setModelConfig / setModel (OfflineModelConfig) — tipe unik, cari by type
            findSetterByType(recCfgClass, modelCfgClass)?.invoke(recCfg, modelCfg)
                ?: throw NoSuchMethodException("setter modelConfig")

            // AssetManager=null karena pakai path filesystem absolut
            recognizer = recognizerClass
                .getConstructor(android.content.res.AssetManager::class.java, recCfgClass)
                .newInstance(null, recCfg)

            isReady = true
            Log.d(TAG, "Whisper ready (bundled)")
            true
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Sherpa-ONNX AAR tidak mendukung OfflineRecognizer")
            false
        } catch (e: Exception) {
            Log.e(TAG, "loadRecognizer: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** Transkripsi dari FloatArray yang sudah direkam (16kHz mono) */
    suspend fun transcribeFloats(samples: FloatArray, sampleRate: Int = 16000): String? =
        withContext(Dispatchers.IO) {
            val rec = recognizer ?: return@withContext null
            try {
                val streamClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineStream")
                val stream = rec.javaClass.getMethod("createStream").invoke(rec)
                    ?: return@withContext null
                stream.javaClass.getMethod("acceptWaveform",
                    FloatArray::class.java, Int::class.java
                ).invoke(stream, samples, sampleRate)
                rec.javaClass.getMethod("decode", streamClass).invoke(rec, stream)
                val result = rec.javaClass.getMethod("getResult", streamClass).invoke(rec, stream)
                val text = result?.javaClass?.getMethod("getText")?.invoke(result) as? String ?: ""
                Log.d(TAG, "Transkripsi: '$text'")
                text.trim().lowercase().ifBlank { null }
            } catch (e: Exception) {
                Log.e(TAG, "transcribe error: ${e.message}")
                null
            }
        }

    fun release() {
        try { recognizer?.javaClass?.getMethod("release")?.invoke(recognizer) } catch (_: Exception) {}
        recognizer = null
        isReady = false
    }
}
