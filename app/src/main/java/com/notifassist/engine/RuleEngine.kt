package com.notifassist.engine

import android.content.Context
import com.notifassist.data.AppRule
import com.notifassist.data.RuleDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Menentukan apakah notifikasi harus dibacakan dan menghasilkan teks TTS-nya.
 */
class RuleEngine(context: Context) {

    private val dao = RuleDatabase.getInstance(context).appRuleDao()

    // Simpan timestamp terakhir TTS per package untuk hindari spam
    private val lastSpokenTime = mutableMapOf<String, Long>()

    data class Decision(
        val shouldSpeak: Boolean,
        val ttsText: String = "",
        val pauseMusic: Boolean = false
    )

    suspend fun evaluate(notif: ParsedNotification, skipInterval: Boolean = false): Decision = withContext(Dispatchers.IO) {
        val rule = dao.getRuleForPackage(notif.packageName)
            ?: return@withContext Decision(false) // App tidak terdaftar = diam

        if (!rule.isEnabled) return@withContext Decision(false)

        // Cek jam aktif
        if (!isWithinActiveHours(rule)) return@withContext Decision(false)

        // Cek interval minimal (anti-spam) — dilewati untuk pesan beruntun
        // karena dedup per-pesan sudah menjamin tiap pesan dibaca sekali
        if (!skipInterval && !isIntervalOk(notif.packageName, rule.minIntervalSeconds)) {
            return@withContext Decision(false)
        }

        // Semua cek lolos — buat teks TTS
        val ttsText = buildTtsText(notif, rule)
        lastSpokenTime[notif.packageName] = System.currentTimeMillis()

        Decision(
            shouldSpeak = true,
            ttsText     = ttsText,
            pauseMusic  = rule.pauseMusic
        )
    }

    private fun buildTtsText(notif: ParsedNotification, rule: AppRule): String {
        // Kalau user matikan readContent, pakai template pendek
        val template = if (!rule.readContent) {
            "{app}: pesan dari {sender}"
        } else {
            rule.messageTemplate
        }
        return MessageParser.renderTemplate(template, notif)
    }

    private fun isWithinActiveHours(rule: AppRule): Boolean {
        if (rule.priority >= 1) return true
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in rule.activeHourStart..rule.activeHourEnd
    }

    private fun isIntervalOk(pkg: String, minSeconds: Int): Boolean {
        val last = lastSpokenTime[pkg] ?: return true
        return (System.currentTimeMillis() - last) >= minSeconds * 1000L
    }
}
