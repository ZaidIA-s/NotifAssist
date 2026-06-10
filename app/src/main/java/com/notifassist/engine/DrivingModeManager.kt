package com.notifassist.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Singleton yang menyimpan state mode berkendara.
 * Diakses oleh RuleEngine untuk override jam aktif dan setting lainnya.
 */
object DrivingModeManager {

    private const val TAG   = "DrivingMode"
    private const val PREFS = "notifassist_prefs"
    private const val KEY_DRIVING       = "driving_mode_active"
    private const val KEY_BT_ENABLED    = "bt_auto_driving"
    private const val KEY_BT_NAME       = "bt_device_name_filter"
    private const val KEY_WAKE_ON_DRIVE = "wake_word_on_driving"
    private const val KEY_BT_MIC        = "use_bluetooth_mic"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── State ─────────────────────────────────────────────────────────────

    var isDriving = false
        private set

    fun setDriving(ctx: Context, active: Boolean) {
        isDriving = active
        prefs(ctx).edit().putBoolean(KEY_DRIVING, active).apply()
        Log.d(TAG, "Driving mode: $active")
    }

    // ── Settings ──────────────────────────────────────────────────────────

    fun isBluetoothAutoEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_BT_ENABLED, true)

    fun setBluetoothAutoEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_BT_ENABLED, enabled).apply()

    /** Filter nama device BT — kosong = semua device BT trigger mode berkendara */
    fun getBtDeviceFilter(ctx: Context): String =
        prefs(ctx).getString(KEY_BT_NAME, "") ?: ""

    fun setBtDeviceFilter(ctx: Context, name: String) =
        prefs(ctx).edit().putString(KEY_BT_NAME, name).apply()

    /** Apakah Wake Word otomatis aktif saat mode berkendara */
    fun isWakeWordOnDriving(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WAKE_ON_DRIVE, true)

    fun setWakeWordOnDriving(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_WAKE_ON_DRIVE, enabled).apply()

    /** Gunakan mikrofon headset Bluetooth (SCO) untuk wake word & perintah */
    fun isBluetoothMicEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_BT_MIC, true)

    fun setBluetoothMicEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_BT_MIC, enabled).apply()

    /**
     * Cek apakah notifikasi dari packageName harus dibacakan.
     * Saat mode berkendara aktif: override jam aktif, baca semua app yang terdaftar.
     */
    fun shouldOverrideActiveHours(): Boolean = isDriving
}
