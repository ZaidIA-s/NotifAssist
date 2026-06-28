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
- **Perintah Suara (Wake Word)** — sebut kata pemicu lalu beri perintah suara untuk kendali musik & mengulang notifikasi — sepenuhnya offline via Vosk *(opsional, perlu setup model)*

---

## Perintah Suara (Wake Word)

Fitur opsional untuk mengendalikan app tanpa menyentuh layar. Ucapkan **wake word** (default **"hai alfa"**), tunggu nada *beep*, lalu ucapkan perintah:

| Perintah | Aksi |
|---|---|
| `putar` | Lanjutkan musik |
| `jeda` | Jeda musik |
| `lanjut` | Lagu berikutnya |
| `sebelumnya` | Lagu sebelumnya |
| `volume naik` / `volume turun` | Atur volume |
| `ulang` | Bacakan ulang notifikasi terakhir |
| `batal` | Batalkan |

**Mic Adaptif (solusi headset Bluetooth):** saat ada musik diputar lewat headset BT, app memakai **mic HP** agar kualitas musik (A2DP) tetap tinggi; saat hening, app beralih ke **mic headset** untuk pickup lebih baik. Tidak pernah memaksa SCO saat musik berjalan, sehingga kualitas audio headset tidak turun.

> Semua pengenalan suara berjalan **100% di perangkat** (offline). Mikrofon hanya aktif saat fitur ini dinyalakan di Pengaturan.

### Setup Model Vosk (wajib untuk fitur ini)

Model Bahasa Indonesia (~55 MB) **tidak disertakan di repo** dan harus dipasang manual sekali. Lihat petunjuk lengkap di [`app/src/main/assets/model-id/SETUP.md`](app/src/main/assets/model-id/SETUP.md).

Singkatnya:
```bash
git clone https://github.com/bookbot-kids/speech-recognizer-bahasa-indonesian
git lfs pull   # bila file model berupa pointer LFS
# salin isi .../assets/model-id-id/  →  app/src/main/assets/model-id/
```

Tanpa model, app tetap berjalan normal sebagai pembaca notifikasi — fitur perintah suara hanya akan nonaktif otomatis.

---

## Tampilan Aplikasi

### Beranda
Satu halaman dengan dua section yang terpisah jelas:

**Section 1 — Status Izin**
Menampilkan status tiga izin yang dibutuhkan secara ringkas dalam satu baris chip berwarna. Tombol "Perbaiki Izin" muncul otomatis saat ada izin yang belum diberikan dan memandu pengguna satu per satu.

**Section 2 — Aplikasi Dipantau**
Daftar aplikasi yang sudah ditambahkan, masing-masing dengan toggle aktif/nonaktif dan tombol ⚙ untuk konfigurasi detail. Tombol "+ Tambah App" dan "🔊 Test Suara" ada di bagian bawah.

### Pengaturan
Konfigurasi global suara TTS: pilihan suara Google, kecepatan, dan nada. Juga berisi card **Perintah Suara**: toggle aktif/nonaktif, pilihan wake word, mode mikrofon (adaptif / HP / headset), dan sensitivitas.

---

## Izin yang Dibutuhkan

| Izin | Keterangan |
|---|---|
| Akses Notifikasi | Wajib — untuk membaca notifikasi masuk |
| Izin Notifikasi Sistem | Wajib di Android 13+ — agar foreground service bisa tampil |
| Bebas Optimasi Baterai | Sangat direkomendasikan — mencegah sistem mematikan service saat layar mati |
| Mikrofon | Hanya untuk fitur Perintah Suara — diminta saat fitur diaktifkan |

Semua izin dapat diaktifkan langsung dari halaman Beranda.

---

## Instalasi

1. Clone repositori:
   ```bash
   git clone https://github.com/ZaidIA-s/NotifAssist.git
   ```
2. *(Opsional, untuk Perintah Suara)* Pasang model Vosk sesuai [`app/src/main/assets/model-id/SETUP.md`](app/src/main/assets/model-id/SETUP.md).
3. Buka di **Android Studio** dan lakukan Gradle Sync.
4. Build dan install ke perangkat (minSdk 31 / Android 12).
5. Buka app → ikuti panduan izin di halaman Beranda.
6. Tambahkan aplikasi yang ingin dipantau → tap "+ Tambah App".

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
| Voice Recognition | Vosk (Kaldi) — offline, grammar terbatas; model ID komunitas bookbot |
| Background | `NotificationListenerService` + `ForegroundService` (TTS & Mikrofon) |

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
