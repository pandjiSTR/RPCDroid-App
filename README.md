# RPCDroid-NR (non-root)

Padanan non-root dari [RPCDroid](https://github.com/pandjiSTR/RPCDroid): Discord Rich Presence untuk game Android,
tanpa Magisk/KernelSU/APatch. Kotlin, satu dependency (OkHttp), APK kecil.

## Pemetaan dari versi root

| RPCDroid (root)                               | RPCDroid-NR                                              |
|-----------------------------------------------|----------------------------------------------------------|
| IProcessObserver via libbinder_ndk            | `AccessibilityService` (typeWindowStateChanged saja)     |
| fallback `dumpsys`                            | mode alternatif `UsageStatsManager` (polling saat layar on) |
| `onProcessDied` (butuh akses system)          | mode **Shizuku**: UserService shell men-stream event log   |
| daemon C++ + watchdog `service.sh`            | tidak ada daemon; sistem yang mengikat accessibility service |
| mbedTLS + websocket.c (743 baris)             | OkHttp WebSocket                                         |
| socket Discord selalu hidup                   | **lazy connect**: hidup hanya saat game di layar depan   |
| `onProcessDied` untuk hapus status            | game keluar layar depan + jeda linger (default 40 dtk, bisa diatur)   |
| token di `/data/adb` (root only)              | AES-GCM + Android Keystore                               |
| WebUI Vue + file JSON                         | Jetpack Compose + Material 3 + SharedPreferences         |

Protokol (Hello → Identify → Ready → Presence Update, Resume, backoff) dan resolusi icon `external-assets`
mengikuti `gateway.cpp` / `websocket.c`. Data game (`gamelist.txt`, `games.json`) diambil dari repo asli.

## UI (Jetpack Compose + Material 3)
- **Onboarding 3 langkah** (Token → Izin → Siap). Langkah ditentukan murni dari state, jadi otomatis maju saat user kembali dari pengaturan sistem.
- **Beranda**: kartu status berwarna (terhubung / menghubungkan / perlu perhatian dengan tombol perbaikan), kartu "Sedang dimainkan" dengan ikon dan timer, switch utama.
- **Game**: pencarian, switch per game, ikon app asli, dan "Tambah app" untuk app apa pun yang ber-launcher.
- **Pengaturan**: metode deteksi, slider jeda, kelola token, bantuan baterai/OEM, tentang.
- Warna dinamis Material You (Android 12+), mode gelap mengikuti sistem, edge-to-edge.

## Mode Shizuku (opsional, paling akurat)
Tiga metode deteksi, bisa dipilih kapan saja:

| | Accessibility | Usage Access | Shizuku |
|---|---|---|---|
| Game dibuka | seketika | ≤ 4 dtk | seketika |
| Game ditutup | heuristik (jeda linger) | heuristik | **pasti** (proses mati) |
| Proses tambahan | tidak ada | foreground service | foreground service + 1 proses shell |
| Prasyarat | izin Accessibility | izin Usage Access | Shizuku berjalan (ulang tiap reboot) |

Cara kerja: `ShizukuService` mengikat `WatchService` (UserService, jalan sebagai shell) yang men-stream
`logcat -b events` terfilter pada dua tag: `wm_set_resumed_activity` (app/game di depan) dan `am_proc_died`
(proses mati). Tidak memakai hidden API maupun kode transaksi Binder yang berbeda tiap versi Android.
Status dihapus seketika saat proses utama game mati; jika game hanya ke latar belakang, status dipertahankan
sampai batas waktu (default 15 menit, bisa diatur).

**Verifikasi di ROM kamu** sebelum percaya pada mode ini (jalankan di PC, lalu buka dan tutup sebuah game):
`adb shell logcat -b events -v brief -s wm_set_resumed_activity:I am_proc_died:I`
Jika dua tag itu muncul, mode Shizuku akan bekerja. Jika tidak, pakai Accessibility.

## Build
Buka folder ini di Android Studio (akan membuat Gradle wrapper & menawarkan upgrade versi AGP), lalu
`Build > Build APK`. Atau `gradle assembleRelease` jika Gradle terpasang. Min SDK 29.

## Pakai
1. Isi token (paste atau tombol *Login lewat Discord*).
2. Aktifkan **Accessibility → RPCDroid NR**. Di Android 13+ untuk APK sideload: *Info aplikasi → ⋮ → Izinkan setelan terbatas* dulu.
   (Alternatif tanpa Accessibility: pilih mode Usage Access dan beri izin.)
3. Buka game. Status muncul di Discord.

## Batasan
- Tanpa root tidak ada info process-death: "game ditutup" = game tidak lagi di layar depan lebih dari jeda linger.
- Tidak cocok untuk Play Store (kebijakan Accessibility + user-token). Distribusi via APK.
- Game yang tidak terdaftar di Discord dan tidak punya URL icon tampil tanpa gambar.
- Pakai **user token** (self-bot): melanggar ToS Discord. Gunakan akun sekunder.
- OEM agresif (MIUI, ColorOS, dll.) perlu app di-set "tanpa batasan baterai" / autostart agar accessibility service tidak dimatikan.

## Lisensi
GPL-3.0, karena `gamelist.txt` dan `games.json` diturunkan dari RPCDroid (GPL-3.0).
