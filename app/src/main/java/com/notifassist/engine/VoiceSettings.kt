package com.notifassist.engine

import android.content.Context

/** Pengaturan terkait suara & mikrofon (wake word). */
object VoiceSettings {
    private const val PREFS    = "voice_settings"
    private const val KEY_BT_MIC = "use_bluetooth_mic"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Gunakan mikrofon headset Bluetooth (SCO) untuk wake word & perintah */
    fun isBluetoothMicEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_BT_MIC, true)

    fun setBluetoothMicEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_BT_MIC, enabled).apply()
}
