# NotifAssist

**NotifAssist** adalah pembaca notifikasi Android berbasis TTS (Text-to-Speech). Aplikasi memantau notifikasi masuk dari aplikasi pilihan dan membacakannya dengan suara secara otomatis — cocok untuk kondisi berkendara saat melihat layar tidak memungkinkan.

Semua pemrosesan dilakukan **on-device**, tanpa koneksi internet dan tanpa mengirim data ke luar.

---

## Fitur

- **Pembaca Notifikasi** — menangkap notifikasi dari WhatsApp, Telegram, Gmail, Instagram, dan lainnya
- **Text-to-Speech** — menggunakan Google TTS bawaan Android; mendukung suara Indonesia berkualitas tinggi
- **Aturan per Aplikasi** — tiap app bisa dikonfigurasi sendiri: aktif/nonaktif, template pesan, jam aktif, jeda minimal
- **Auto-pause Musik** — musik dijeda sebelum TTS berbicara, dilanjutkan sesudahnya
- **Deduplication** — pesan yang sama tidak dibaca dua kali (TTL 3 menit)
- **MessagingStyle Support** — pesan beruntun dari WhatsApp/Telegram dibaca secara kronologis
- **Foreground Service Persisten** — berjalan terus di background dengan notifikasi status permanen
- **Auto-start saat Boot** — aktif kembali otomatis setelah HP restart

---

## Tampilan Aplikasi

### Beranda
Satu halaman dengan dua section yang terpisah jelas:

**Section 1 — Status Izin**
Menampilkan status tiga izin yang dibutuhkan secara ringkas dalam satu baris chip berwarna. Tombol "Perbaiki Izin" muncul otomatis saat ada izin yang belum diberikan dan memandu pengguna satu per satu.

**Section 2 — Aplikasi Dipantau**
Daftar aplikasi yang sudah ditambahkan, masing-masing dengan toggle aktif/nonaktif dan tombol ⚙ untuk konfigurasi detail. Tombol "+ Tambah App" dan "🔊 Test Suara" ada di bagian bawah.

### Pengaturan
Konfigurasi global suara TTS: pilihan suara Google, kecepatan, dan nada.

---

## Izin yang Dibutuhkan

| Izin | Keterangan |
|---|---|
| Akses Notifikasi | Wajib — untuk membaca notifikasi masuk |
| Izin Notifikasi Sistem | Wajib di Android 13+ — agar foreground service bisa tampil |
| Bebas Optimasi Baterai | Sangat direkomendasikan — mencegah sistem mematikan service saat layar mati |

Semua izin dapat diaktifkan langsung dari halaman Beranda.

---

## Instalasi

1. Clone repositori:
   ```bash
   git clone https://github.com/ZaidIA-s/NotifAssist.git
   ```
2. Buka di **Android Studio** dan lakukan Gradle Sync.
3. Build dan install ke perangkat (minSdk 31 / Android 12).
4. Buka app → ikuti panduan izin di halaman Beranda.
5. Tambahkan aplikasi yang ingin dipantau → tap "+ Tambah App".

---

## Teknologi

| Komponen | Detail |
|---|---|
| Bahasa | Kotlin 1.9.25 |
| Min SDK | 31 (Android 12) |
| Target SDK | 35 (Android 15) |
| UI | Material3, ViewBinding |
| Database | Room 2.6.1 |
| Async | Kotlin Coroutines + Flow |
| TTS | Android `TextToSpeech` (Google TTS) |
| Background | `NotificationListenerService` + `ForegroundService` |

---

## Konfigurasi per Aplikasi

Setiap aplikasi yang ditambahkan dapat dikonfigurasi via tombol ⚙:

- **Aktif/Nonaktif** — master switch
- **Baca Pengirim** — sertakan nama pengirim dalam ucapan
- **Baca Isi** — sertakan isi pesan
- **Pause Musik** — jeda musik otomatis saat membaca
- **Template Pesan** — kustomisasi format ucapan (`{app}`, `{sender}`, `{content}`)
- **Jam Aktif** — rentang jam di mana notifikasi dibacakan
- **Jeda Minimal** — anti-spam antar notifikasi dari app yang sama

---

## Lisensi

Proyek ini dikembangkan untuk keperluan aksesibilitas dan produktivitas personal pada platform Android.
