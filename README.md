# 📱 Viper Bot Sprayer - Aplikasi Android (Frontend)

Repository ini berisi kode sumber (*source code*) aplikasi Android berdesain futuristik untuk mengontrol robot Viper Bot Sprayer. Aplikasi ini dirancang agar pengguna dapat menyetir robot seperti bermain *game* sambil memonitor data lingkungan robot secara *real-time*.

## ✨ Fitur Utama
1. **UI Futuristik & Responsif:** Desain modern dengan warna gelap elegan, palet biru cerah, dan umpan balik visual (*visual feedback*) seketika. Ikon-ikon tombol teranimasi sesuai dengan *state* (misal: tombol semprot berubah menjadi tombol merah saat aktif).
2. **Kontrol D-Pad Tanpa Jeda:** Menggunakan pendeteksi sentuhan layar (OnTouchListener). Robot akan bergerak saat tombol ditekan (ditahan) dan otomatis berhenti sesaat setelah jari dilepaskan dari layar.
3. **Sinkronisasi Data Real-Time:** Menampilkan Kecepatan Pergerakan (m/s), Status Pompa (ON/OFF), dan Waktu Durasi Semprotan tanpa *lag* menggunakan **WebSockets**.
4. **Auto-Connect Pintar:** Tidak perlu mengetik IP address robot! Aplikasi menggunakan *UDP Broadcast* (Port 8888) untuk meraba jaringan lokal. Saat robot ditemukan, aplikasi otomatis melakukan *binding* koneksi.

## 🛠️ Teknologi & Tools
- Bahasa: **Kotlin**
- UI System: **Android XML Layouts (Material Components)**
- Jaringan: **OkHttp** (WebSocket client)
- Lingkungan Pengembangan: **Android Studio** (Gradle)

## 🚀 Cara Build & Instalasi
### Cara 1: Menggunakan Terminal (Cepat)
1. Buka PowerShell di dalam folder yang sama dengan *file* README ini, lalu masuk ke direktori utama aplikasi:
   ```powershell
   cd Sprayer-Bot-Application
   ```
2. Jalankan perintah Gradle untuk melakukan kompilasi versi Debug:
   ```powershell
   .\gradlew assembleDebug
   ```
3. Setelah proses selesai (`BUILD SUCCESSFUL`), temukan file APK siap pakai di lokasi:
   `Sprayer-Bot-Application\app\build\outputs\apk\debug\app-debug.apk`
4. Pindahkan *file* APK tersebut ke ponsel Android Anda dan klik untuk Menginstal.

### Cara 2: Menggunakan Android Studio
1. Buka **Android Studio**.
2. Pilih opsi *Open an existing Android Studio project*, lalu pilih folder `Sprayer-Bot-Application` yang ada di dalam *repository* ini.
3. Tunggu hingga proses sinkronisasi Gradle (Index) selesai.
4. Hubungkan ponsel Anda ke komputer (aktifkan mode *USB Debugging* di HP).
5. Klik ikon **Play (Run 'app')** di bilah atas Android Studio. Aplikasi akan langsung dikompilasi dan otomatis terbuka di layar HP Anda.

## 📁 Struktur Folder Utama
- `Sprayer-Bot-Application/app/src/main/java/com/yan/sbapp/MainActivity.kt`: Kode utama Kotlin pengontrol logika UI dan WebSockets.
- `Sprayer-Bot-Application/app/src/main/res/layout/activity_main.xml`: Desain susunan tata letak antarmuka (UI).
- `Sprayer-Bot-Application/app/build.gradle.kts`: Konfigurasi *build* Gradle dan dependensi (*library*).
