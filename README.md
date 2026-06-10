# NotifAssist

**NotifAssist** adalah asisten notifikasi cerdas untuk Android yang dirancang untuk membantu pengguna mengelola dan merespons notifikasi secara hands-free. Aplikasi ini sangat berguna saat berkendara atau ketika akses ke ponsel terbatas.

## 🚀 Fitur Utama

- **Notification Listener:** Membaca notifikasi masuk dari berbagai aplikasi populer (WhatsApp, Telegram, Gmail, dll).
- **Text-to-Speech (TTS):** Mengubah teks notifikasi menjadi suara secara real-time.
- **Wake Word Recognition:** Fitur aktivasi suara menggunakan Vosk untuk mendengarkan perintah pengguna (Contoh: "Hey Assist").
- **Driving Mode:** Deteksi otomatis saat terhubung ke Bluetooth kendaraan untuk mengaktifkan mode asisten.
- **Privacy Focused:** Semua pemrosesan suara dilakukan secara lokal (on-device) tanpa mengirim data ke cloud.

## 🛠️ Teknologi yang Digunakan

- **Bahasa:** [Kotlin](https://kotlinlang.org/)
- **Arsitektur:** MVVM dengan Jetpack Components (Lifecycle, Room, ViewBinding).
- **Speech Recognition:** 
  - [Vosk Android SDK](https://alphacephei.com/vosk/android)
  - [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) (Local AAR)
- **Database:** Room Persistence Library.
- **Concurrency:** Kotlin Coroutines.

## 📦 Persiapan & Instalasi

1. **Clone repositori ini:**
   ```bash
   git clone https://github.com/USERNAME/NotifAssist.git
   ```
2. **Setup Library Lokal:**
   Pastikan file `sherpa-onnx.aar` sudah ada di dalam folder `app/libs/`. Jika belum, unduh dari [sini](https://huggingface.co/csukuangfj/sherpa-onnx-libs/tree/main/android/aar).
3. **Build Project:**
   Buka project di Android Studio dan lakukan **Gradle Sync**.
4. **Izin Aplikasi:**
   Berikan izin **Notification Access** dan **Microphone** agar fitur asisten berfungsi maksimal.

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
