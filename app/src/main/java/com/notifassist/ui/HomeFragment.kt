package com.notifassist.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.notifassist.databinding.FragmentHomeBinding
import com.notifassist.service.NotifListenerService
import com.notifassist.service.TtsService

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private val handler = Handler(Looper.getMainLooper())

    private val statusUpdater = object : Runnable {
        override fun run() { updateStatus(); handler.postDelayed(this, 2000) }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentHomeBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Jalankan service TTS persisten agar proses tetap hidup di background
        // (foreground service "NotifAssist aktif" menjaga listener tidak dibekukan).
        TtsService.start(requireContext())

        b.btnTestTts.setOnClickListener {
            TtsService.speak(requireContext(), "NotifAssist siap. Halo!")
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusUpdater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusUpdater)
    }

    private fun updateStatus() {
        val hasNotifAccess = isNotifListenerEnabled()
        b.tvStatusNotifVal.text = if (hasNotifAccess) "Aktif" else "Nonaktif"
        b.tvStatusNotifVal.setTextColor(
            if (hasNotifAccess) resources.getColor(com.google.android.material.R.color.design_default_color_primary, null)
            else requireContext().getColor(android.R.color.holo_red_dark)
        )
    }

    private fun isNotifListenerEnabled(): Boolean {
        val ctx = context ?: return false
        val cn = ComponentName(ctx, NotifListenerService::class.java)
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(cn.flattenToString())
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
