package com.notifassist.engine

import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * Mengekstrak informasi dari notifikasi mentah menjadi data terstruktur.
 */
data class ParsedNotification(
    val packageName: String,
    val appLabel: String,
    val sender: String,
    val content: String,
    val isGroupMessage: Boolean = false,
    val groupName: String = "",
    val timestamp: Long = 0L,          // waktu pesan (untuk dedup)
    val fromMessagingStyle: Boolean = false  // true jika dari MessagingStyle (identitas akurat)
)

object MessageParser {

    /**
     * Ekstrak SEMUA pesan individual dari notifikasi.
     * WhatsApp/Telegram pakai MessagingStyle yang menyimpan tiap pesan beserta timestamp,
     * sehingga pesan beruntun bisa dibaca satu per satu tanpa duplikasi.
     */
    fun extractMessages(sbn: StatusBarNotification, appLabel: String): List<ParsedNotification> {
        val notification = sbn.notification
        val extras = notification.extras ?: return emptyList()
        val title = extras.getCharSequence("android.title")?.toString()?.trim() ?: ""

        // 1. Coba MessagingStyle (paling akurat — ada timestamp per pesan)
        val style = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        } catch (e: Exception) { null }

        if (style != null && style.messages.isNotEmpty()) {
            val isGroup   = style.isGroupConversation
            val groupName = if (isGroup) (style.conversationTitle?.toString() ?: title) else ""
            return style.messages.mapNotNull { msg ->
                val content = msg.text?.toString()?.trim()
                if (content.isNullOrBlank()) return@mapNotNull null
                if (isSystemNoise(title, content)) return@mapNotNull null
                val sender = msg.person?.name?.toString()?.ifBlank { title } ?: title
                ParsedNotification(
                    packageName    = sbn.packageName,
                    appLabel       = appLabel,
                    sender         = sender.ifBlank { appLabel },
                    content        = content,
                    isGroupMessage = isGroup,
                    groupName      = groupName,
                    timestamp      = msg.timestamp,
                    fromMessagingStyle = true
                )
            }
        }

        // 2. Fallback: parse tunggal (notif non-messaging)
        val single = parse(sbn, appLabel) ?: return emptyList()
        return listOf(single.copy(timestamp = sbn.postTime, fromMessagingStyle = false))
    }


    fun parse(sbn: StatusBarNotification, appLabel: String): ParsedNotification? {
        val extras = sbn.notification.extras ?: return null

        val title = extras.getCharSequence("android.title")?.toString()?.trim() ?: ""
        val text  = extras.getCharSequence("android.text")?.toString()?.trim()  ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString()?.trim() ?: ""

        // Abaikan notif kosong atau notif sistem
        if (title.isBlank() && text.isBlank()) return null
        if (isSystemNoise(title, text)) return null

        val content = bigText.ifBlank { text }

        // WhatsApp group: title biasanya "Nama Grup" dan text "Pengirim: isi"
        val isGroup = text.contains(": ") && sbn.packageName == "com.whatsapp"
        val (sender, actualContent) = if (isGroup) {
            val colonIdx = text.indexOf(": ")
            text.substring(0, colonIdx) to text.substring(colonIdx + 2)
        } else {
            title to content
        }

        return ParsedNotification(
            packageName  = sbn.packageName,
            appLabel     = appLabel,
            sender       = sender.ifBlank { appLabel },
            content      = actualContent.ifBlank { "" },
            isGroupMessage = isGroup,
            groupName    = if (isGroup) title else ""
        )
    }

    /** Teks yang sering muncul sebagai notif sistem/noise, tidak perlu dibacakan */
    private fun isSystemNoise(title: String, text: String): Boolean {
        val combined = "$title $text".lowercase()
        val noiseKeywords = listOf(
            "charging", "low battery", "screenshot", "connected", "disconnected",
            "sedang mengisi", "baterai lemah", "terhubung", "terputus", "sync"
        )
        return noiseKeywords.any { combined.contains(it) }
    }

    /**
     * Render template pesan menjadi string siap TTS.
     * Placeholder: {app}, {sender}, {content}, {group}
     */
    fun renderTemplate(template: String, notif: ParsedNotification): String {
        var result = template
            .replace("{app}", notif.appLabel)
            .replace("{sender}", notif.sender)
            .replace("{content}", notif.content)
            .replace("{group}", notif.groupName)

        // Bersihkan karakter yang bikin TTS aneh
        result = result
            .replace(Regex("[*_~`]"), "")
            .replace(Regex("https?://\\S+"), "link")
            .replace(Regex("\\s+"), " ")
            .trim()

        return result
    }
}
