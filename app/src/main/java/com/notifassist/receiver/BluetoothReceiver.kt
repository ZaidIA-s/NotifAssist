package com.notifassist.receiver

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.notifassist.engine.DrivingModeManager
import com.notifassist.service.TtsService
import com.notifassist.service.WakeWordService

/**
 * Mendengarkan event koneksi/disconnect Bluetooth.
 * Mendukung: headset, A2DP (speaker/helm audio), HFP (hands-free).
 *
 * Didaftarkan secara dynamic di DrivingModeService agar bisa
 * unregister saat tidak dibutuhkan (hemat baterai).
 */
class BluetoothReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BluetoothReceiver"

        // Intent actions yang kita pantau
        val ACTIONS = arrayOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!DrivingModeManager.isBluetoothAutoEnabled(context)) return

        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val deviceName = try { device?.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
        val action = intent.action ?: return

        Log.d(TAG, "BT event: $action — device: $deviceName")

        // Cek filter nama device jika ada
        val filter = DrivingModeManager.getBtDeviceFilter(context)
        if (filter.isNotBlank() && !deviceName.contains(filter, ignoreCase = true)) {
            Log.d(TAG, "Device '$deviceName' tidak cocok filter '$filter' — skip")
            return
        }

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED,
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" -> {

                val state = intent.getIntExtra(
                    BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED
                )

                when {
                    // Untuk ACL_CONNECTED tidak ada state extra — langsung aktifkan
                    action == BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        activateDrivingMode(context, deviceName)
                    }
                    // Untuk profile-specific, cek state connected
                    state == BluetoothProfile.STATE_CONNECTED -> {
                        activateDrivingMode(context, deviceName)
                    }
                    state == BluetoothProfile.STATE_DISCONNECTED -> {
                        deactivateDrivingMode(context, deviceName)
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                deactivateDrivingMode(context, deviceName)
            }
        }
    }

    private fun activateDrivingMode(context: Context, deviceName: String) {
        if (DrivingModeManager.isDriving) return // sudah aktif
        Log.d(TAG, "Activating driving mode via: $deviceName")
        DrivingModeManager.setDriving(context, true)

        // Aktifkan Wake Word jika setting mengizinkan
        if (DrivingModeManager.isWakeWordOnDriving(context)) {
            WakeWordService.start(context)
        }

        // Umumkan via TTS
        TtsService.speak(
            context,
            "Mode berkendara aktif. Terhubung ke $deviceName. Ucapkan Hei Alpha untuk perintah.",
            pauseMusic = false
        )
    }

    private fun deactivateDrivingMode(context: Context, deviceName: String) {
        if (!DrivingModeManager.isDriving) return // sudah nonaktif
        Log.d(TAG, "Deactivating driving mode, disconnected: $deviceName")
        DrivingModeManager.setDriving(context, false)

        // Stop Wake Word
        WakeWordService.stop(context)

        TtsService.speak(
            context,
            "Mode berkendara nonaktif.",
            pauseMusic = false
        )
    }
}
