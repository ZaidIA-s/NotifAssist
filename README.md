# NotifAssist

**NotifAssist** adalah asisten notifikasi untuk Android yang membacakan pesan masuk dari aplikasi pilihan Anda melalui suara (TTS). Berguna saat berkendara atau ketika akses ke ponsel terbatas — suara otomatis keluar lewat speaker HP atau speaker/headset Bluetooth bila tersambung.

## 🚀 Fitur Utama

- **Notification Listener:** Membaca notifikasi masuk dari berbagai aplikasi populer (WhatsApp, Telegram, Gmail, dll).
- **Text-to-Speech (TTS):** Mengubah teks notifikasi menjadi suara secara real-time, otomatis ke speaker HP atau Bluetooth (A2DP).
- **Aturan per Aplikasi:** Pilih aplikasi mana yang notifikasinya dibacakan.
- **Auto-pause Musik:** Musik dijeda sejenak saat notifikasi dibacakan, lalu dilanjutkan.
- **Privacy Focused:** Pemrosesan dilakukan secara lokal (on-device) tanpa mengirim data ke cloud.

## 🛠️ Teknologi yang Digunakan

- **Bahasa:** [Kotlin](https://kotlinlang.org/)
- **Arsitektur:** Jetpack Components (Lifecycle, Room, ViewBinding).
- **Text-to-Speech:** Google TextToSpeech bawaan Android.
- **Database:** Room Persistence Library.
- **Concurrency:** Kotlin Coroutines.

## 📦 Persiapan & Instalasi

1. **Clone repositori ini:**
   ```bash
   git clone https://github.com/USERNAME/NotifAssist.git
   ```
2. **Build Project:**
   Buka project di Android Studio dan lakukan **Gradle Sync**.
3. **Izin Aplikasi:**
   Berikan izin **Notification Access** agar aplikasi dapat membaca notifikasi, lalu matikan optimasi baterai & izinkan auto-start agar tetap berjalan di background.

## 📝 Konfigurasi Git (Langkah Push ke GitHub)

Jika Anda ingin mengunggah project ini ke repositori baru:

1. Buat repositori kosong di GitHub.
2. Jalankan perintah berikut di terminal:
   ```bash
   # Tambahkan semua file yang tidak diabaikan oleh .gitignore
   git add .

   # Commit perubahan pertama
   git commit -m "Initial commit: NotifAssist Core Implementation"

   # Hubungkan ke repositori GitHub (ganti URL-nya)
   git remote add origin https://github.com/USERNAME/NotifAssist.git

   # Push ke branch utama
   git branch -M main
   git push -u origin main
   ```

## 📄 Lisensi

Proyek ini dibuat untuk tujuan pengembangan asisten suara lokal pada platform Android.

---
*Dikembangkan dengan ❤️ untuk kemudahan aksesibilitas.*
