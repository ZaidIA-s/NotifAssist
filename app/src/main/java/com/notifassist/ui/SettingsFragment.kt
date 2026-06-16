package com.notifassist.ui

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.notifassist.databinding.FragmentSettingsBinding
import com.notifassist.service.TtsService

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSettingsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val prefs = TtsService.getPrefs(ctx)

        // ── Suara Google TTS ──────────────────────────────────────────────
        loadGoogleVoices()

        // Speed
        val savedSpeed = prefs.getFloat(TtsService.KEY_SPEED, 0.95f)
        b.sliderSpeed.value = savedSpeed
        updateSpeedLabel(savedSpeed)
        b.sliderSpeed.addOnChangeListener { _, value, fromUser ->
            prefs.edit().putFloat(TtsService.KEY_SPEED, value).apply()
            updateSpeedLabel(value)
            if (fromUser) TtsService.reload(ctx)
        }

        // Pitch
        val savedPitch = prefs.getFloat(TtsService.KEY_PITCH, 1.0f)
        b.sliderPitch.value = savedPitch
        updatePitchLabel(savedPitch)
        b.sliderPitch.addOnChangeListener { _, value, fromUser ->
            prefs.edit().putFloat(TtsService.KEY_PITCH, value).apply()
            updatePitchLabel(value)
            if (fromUser) TtsService.reload(ctx)
        }

        // Preview
        b.btnPreviewVoice.setOnClickListener {
            TtsService.reload(ctx)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                TtsService.speak(ctx, "Halo! Ini suara NotifAssist. Pesan dari WhatsApp telah tiba.", pauseMusic = false)
            }, 300)
        }

        // ── Battery optimization ──────────────────────────────────────────
        b.btnBatteryOpt.setOnClickListener {
            try {
                startActivity(android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:${ctx.packageName}")
                ))
            } catch (e: Exception) {
                startActivity(android.content.Intent(
                    android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun loadGoogleVoices() {
        val ctx = context ?: return
        ctx.startForegroundService(android.content.Intent(ctx, TtsService::class.java))

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (_b == null) return@postDelayed
            try {
                android.speech.tts.TextToSpeech(ctx) { status ->
                    if (status != android.speech.tts.TextToSpeech.SUCCESS) return@TextToSpeech
                    val ttsTmp = android.speech.tts.TextToSpeech(ctx) {}
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val voices = ttsTmp.voices
                            ?.filter { !it.isNetworkConnectionRequired }
                            ?.sortedWith(compareBy({ it.locale.language != "id" }, { it.name }))
                            ?: emptyList()
                        val labels = voices.map { "${it.locale.displayLanguage} — ${it.name}" }
                        val keys   = voices.map { it.name }

                        activity?.runOnUiThread {
                            if (_b == null) return@runOnUiThread
                            val adapter = ArrayAdapter(ctx,
                                android.R.layout.simple_dropdown_item_1line,
                                listOf("Default (Bahasa Indonesia)") + labels)
                            b.spinnerGoogleVoice.setAdapter(adapter)

                            val saved = TtsService.getPrefs(ctx).getString(TtsService.KEY_VOICE_NAME, "") ?: ""
                            val idx = (keys.indexOf(saved) + 1).coerceAtLeast(0)
                            b.spinnerGoogleVoice.setText(adapter.getItem(idx) ?: "Default", false)

                            b.spinnerGoogleVoice.setOnItemClickListener { _, _, pos, _ ->
                                val key = if (pos == 0) "" else keys.getOrElse(pos - 1) { "" }
                                TtsService.getPrefs(ctx).edit()
                                    .putString(TtsService.KEY_VOICE_NAME, key).apply()
                                TtsService.reload(ctx)
                            }
                        }
                    }, 600)
                }
            } catch (_: Exception) {}
        }, 400)
    }

    private fun updateSpeedLabel(v: Float) {
        b.tvSpeedLabel.text = when {
            v < 0.8f -> "Kecepatan: Lambat (${String.format("%.2f", v)}x)"
            v > 1.2f -> "Kecepatan: Cepat (${String.format("%.2f", v)}x)"
            else     -> "Kecepatan: Normal (${String.format("%.2f", v)}x)"
        }
    }

    private fun updatePitchLabel(v: Float) {
        b.tvPitchLabel.text = when {
            v < 0.9f -> "Nada: Rendah (${String.format("%.2f", v)}x)"
            v > 1.1f -> "Nada: Tinggi (${String.format("%.2f", v)}x)"
            else     -> "Nada: Normal (${String.format("%.2f", v)}x)"
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
