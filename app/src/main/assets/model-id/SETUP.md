# Setup Model Vosk Bahasa Indonesia

Fitur **Perintah Suara** memerlukan model Vosk Bahasa Indonesia (~55 MB) di folder ini
(`app/src/main/assets/model-id/`). Model **tidak di-commit ke git** (lihat `.gitignore`) —
pasang manual sekali.

> Tanpa model, app tetap berjalan normal sebagai pembaca notifikasi. Fitur Perintah Suara
> akan nonaktif otomatis dengan aman (`VoskModelProvider.isModelPresent()` = false).

## Sumber model (terverifikasi)

- Repo: <https://github.com/bookbot-kids/speech-recognizer-bahasa-indonesian>
- Path: `speech_recognizer/android/app/src/main/assets/model-id-id/`
- Lisensi: lihat repo (model Kaldi/Vosk, dilatih suara anak)

## Cara pasang

```bash
git clone https://github.com/bookbot-kids/speech-recognizer-bahasa-indonesian
# Jika file model berupa pointer Git LFS:
git lfs pull
```

Salin **seluruh isi** folder:

```
speech-recognizer-bahasa-indonesian/speech_recognizer/android/app/src/main/assets/model-id-id/
```

ke folder ini (`app/src/main/assets/model-id/`), sehingga strukturnya:

```
app/src/main/assets/model-id/
├── am/final.mdl
├── conf/mfcc.conf
├── conf/model.conf
├── graph/HCLr.fst
├── graph/words.txt
├── ivector/final.ie
└── ... (am/ conf/ graph/ ivector/ uuid)
```

Lalu build ulang di Android Studio.

## Catatan vocabulary

Kata di wake word & perintah **harus ada di** `graph/words.txt` (28.160 kata). Sudah diverifikasi:

- **Wake word**: default `hai alfa`; alternatif `galaksi`, `kosmos`, `komandan`.
- **Perintah**: `putar`, `jeda`, `lanjut`, `sebelumnya`, `volume naik`, `volume turun`, `ulang`, `batal`.

Kata yang **tidak ada** di vocab (hindari): `notif`, `oke`, `asisten`, `ulangi`, `stop`,
`beta`, `bravo`, `delta`. Jika mengganti wake word/perintah, cek dulu ada di `words.txt`.
