package com.notifassist.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.notifassist.databinding.FragmentSettingsBinding
import com.notifassist.service.TtsService
import com.notifassist.service.VoiceCommandService
import com.notifassist.voice.VoicePrefs
import com.notifassist.voice.VoskModelProvider

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    private val micPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableVoice(true)
        else {
            b.switchVoiceEnable.isChecked = false
            Toast.makeText(requireContext(), getString(com.notifassist.R.string.voice_need_mic), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSettingsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val prefs = TtsService.getPrefs(ctx)

        // ── Suara Google TTS ──────────────────────────────────────────────
        loadGoogleVoices()

        // ── Perintah Suara ────────────────────────────────────────────────
        setupVoiceCommands()

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

    }

    // ── Perintah Suara ────────────────────────────────────────────────────
    private fun setupVoiceCommands() {
        val ctx = requireContext()

        b.switchVoiceEnable.isChecked = VoicePrefs.isEnabled(ctx)
        updateVoiceOptionsVisibility(VoicePrefs.isEnabled(ctx))

        b.switchVoiceEnable.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!VoskModelProvider.isModelPresent(ctx)) {
                    b.switchVoiceEnable.isChecked = false
                    Toast.makeText(ctx, com.notifassist.R.string.voice_model_missing, Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                    enableVoice(true)
                } else {
                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                enableVoice(false)
            }
        }

        // Wake phrase dropdown
        val phrases = VoicePrefs.WAKE_PHRASES
        b.spinnerWakePhrase.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, phrases)
        )
        b.spinnerWakePhrase.setText(VoicePrefs.wakePhrase(ctx), false)
        b.spinnerWakePhrase.setOnItemClickListener { _, _, pos, _ ->
            VoicePrefs.get(ctx).edit().putString(VoicePrefs.KEY_WAKE_PHRASE, phrases[pos]).apply()
            restartVoiceIfEnabled()
        }

        // Mic mode dropdown
        val micLabels = listOf("Adaptif (otomatis)", "Selalu mic HP", "Utamakan mic headset")
        val micValues = listOf(VoicePrefs.MIC_ADAPTIVE, VoicePrefs.MIC_PHONE, VoicePrefs.MIC_HEADSET)
        b.spinnerMicMode.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, micLabels)
        )
        val curMic = micValues.indexOf(VoicePrefs.micMode(ctx)).coerceAtLeast(0)
        b.spinnerMicMode.setText(micLabels[curMic], false)
        b.spinnerMicMode.setOnItemClickListener { _, _, pos, _ ->
            VoicePrefs.get(ctx).edit().putString(VoicePrefs.KEY_MIC_MODE, micValues[pos]).apply()
            restartVoiceIfEnabled()
        }

        // Sensitivity
        b.sliderSensitivity.value = VoicePrefs.sensitivity(ctx)
        b.sliderSensitivity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) VoicePrefs.get(ctx).edit().putFloat(VoicePrefs.KEY_SENSITIVITY, value).apply()
        }
    }

    private fun enableVoice(enable: Boolean) {
        val ctx = requireContext()
        VoicePrefs.get(ctx).edit().putBoolean(VoicePrefs.KEY_ENABLED, enable).apply()
        updateVoiceOptionsVisibility(enable)
        if (enable) VoiceCommandService.start(ctx) else VoiceCommandService.stop(ctx)
    }

    /** Terapkan ulang setting (restart service) bila fitur sedang aktif. */
    private fun restartVoiceIfEnabled() {
        val ctx = requireContext()
        if (VoicePrefs.isEnabled(ctx)) {
            VoiceCommandService.stop(ctx)
            VoiceCommandService.start(ctx)
        }
    }

    private fun updateVoiceOptionsVisibility(visible: Boolean) {
        b.voiceOptions.visibility = if (visible) View.VISIBLE else View.GONE
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
