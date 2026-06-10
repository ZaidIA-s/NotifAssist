package com.notifassist.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.notifassist.databinding.FragmentHomeBinding
import com.notifassist.service.*

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private var wakeEnabled = false
    private val handler = Handler(Looper.getMainLooper())

    private val heardReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
            if (_b == null) return
            val text = intent?.getStringExtra(WakeWordService.EXTRA_HEARD) ?: return
            val type = intent.getStringExtra(WakeWordService.EXTRA_TYPE) ?: "noise"
            val ctxx = context ?: return
            val label = when (type) {
                "wake"    -> "✓ WAKE: "
                "command" -> "▶ CMD: "
                "partial" -> "… "
                else      -> "✗ "
            }
            b.tvHeard.text = "$label$text"
            b.tvHeard.setTextColor(
                when (type) {
                    "wake", "command" -> resources.getColor(com.google.android.material.R.color.design_default_color_primary, null)
                    "partial"         -> ctxx.getColor(android.R.color.darker_gray)
                    else              -> ctxx.getColor(android.R.color.holo_orange_dark)
                }
            )
        }
    }
    private val statusUpdater = object : Runnable {
        override fun run() { updateStatus(); handler.postDelayed(this, 2000) }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentHomeBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnTestTts.setOnClickListener {
            TtsService.speak(requireContext(), "NotifAssist siap. Halo!")
        }

        b.btnWakeToggle.setOnClickListener {
            val ctx = requireContext()
            if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                return@setOnClickListener
            }
            if (!wakeEnabled) {
                WakeWordService.start(ctx)
                wakeEnabled = true
                b.btnWakeToggle.text = "Hei Alpha: Aktif"
            } else {
                WakeWordService.stop(ctx)
                wakeEnabled = false
                b.btnWakeToggle.text = "Aktifkan Hei Alpha"
            }
            updateStatus()
        }

        b.btnMusicPause.setOnClickListener { MediaControlService.pauseMusic(requireContext()) }
        b.btnMusicPlay.setOnClickListener  { MediaControlService.resumeMusic(requireContext()) }
        b.btnMusicNext.setOnClickListener  { MediaControlService.nextTrack(requireContext()) }
        b.btnMusicPrev.setOnClickListener  { MediaControlService.previousTrack(requireContext()) }
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusUpdater)
        val filter = IntentFilter(WakeWordService.ACTION_HEARD)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requireContext().registerReceiver(heardReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireContext().registerReceiver(heardReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusUpdater)
        try { requireContext().unregisterReceiver(heardReceiver) } catch (_: Exception) {}
    }

    private fun updateStatus() {
        val ctx = context ?: return
        val hasNotifAccess = isNotifListenerEnabled()

        // Status notifikasi
        b.tvStatusNotifVal.text = if (hasNotifAccess) "Aktif" else "Nonaktif"
        b.tvStatusNotifVal.setTextColor(
            if (hasNotifAccess) resources.getColor(com.google.android.material.R.color.design_default_color_primary, null)
            else requireContext().getColor(android.R.color.holo_red_dark)
        )

        // Status wake word
        b.tvStatusWake.text = if (wakeEnabled) "Aktif" else "Nonaktif"
        b.tvStatusWake.setTextColor(
            if (wakeEnabled) resources.getColor(com.google.android.material.R.color.design_default_color_primary, null)
            else requireContext().getColor(android.R.color.holo_red_dark)
        )

        // Now playing
        val nowPlaying = MediaControlService.getNowPlaying(ctx)
        b.tvNowPlaying.text = nowPlaying ?: "Tidak ada musik yang diputar"
    }

    private fun isNotifListenerEnabled(): Boolean {
        val ctx = context ?: return false
        val cn = ComponentName(ctx, NotifListenerService::class.java)
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(cn.flattenToString())
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
