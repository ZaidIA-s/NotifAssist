package com.notifassist.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aturan per-aplikasi.
 * Setiap app yang terdaftar punya satu rule yang bisa dikonfigurasi user.
 */
@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey
    val packageName: String,           // e.g. "com.whatsapp"
    val appLabel: String,              // e.g. "WhatsApp"
    val isEnabled: Boolean = true,     // aktifkan TTS untuk app ini?
    val readSender: Boolean = true,    // sebut nama pengirim?
    val readContent: Boolean = true,   // baca isi pesan?
    val pauseMusic: Boolean = true,    // pause musik dulu sebelum TTS?
    val messageTemplate: String = "{app}: pesan dari {sender}. {content}",
    val minIntervalSeconds: Int = 3,   // jeda minimal antar TTS (hindari spam)
    val activeHourStart: Int = 7,      // jam mulai aktif (0-23)
    val activeHourEnd: Int = 22,       // jam selesai aktif (0-23)
    val priority: Int = 0              // 0=normal, 1=tinggi (selalu dibaca)
)
