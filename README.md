# Ren-ESP

Aplikasi Android buat deteksi objek real-time pake gaya overlay ESP (Extra Sensory Perception) kayak di game.

> Wajib Android 8.0+. Gak perlu root, santai aja.

## Fitur

- **Deteksi Real-time** — Pake model YOLOv8n INT8, support 80 kelas COCO, ngebut sampe ~18 FPS di hape kelas mid-range
- **Overlay ESP** — Bounding box ala game, ada efek glow, corner bracket, sama crosshair
- **Snapline** — Garis tracking dari atas layar ke tiap target
- **Estimasi Jarak** — Hitung jarak real-time pake FOV kamera sama ukuran objek
- **Radar** — Minimap setengah lingkaran nunjukin posisi sama jarak target
- **Tracking Trajectory** — Jejak gerakan sama panah prediksi buat target yang lagi gerak
- **Target Lock** — Indikator "LOCKED" otomatis nempel ke target paling deket sama tengah layar
- **Render Halus 30fps** — Animasi overlay smooth pake Choreographer
- **Zoom Ala iPhone** — Pinch-to-zoom + arc dial sama tombol preset (1x sampe 1000x)
- **HUD GPS** — Koordinat real-time, kecepatan, jumlah satelit
- **Monitor Satelit** — Sky plot semua satelit GNSS yang keliatan
- **Filter Kategori** — Toggle deteksi Person / Vehicle / Animal / Object sesuka lo
- **Bilingual** — Bisa ganti bahasa Mandarin / Inggris / Indonesia
- **Boot Animation** — Splash screen ala cyberpunk lengkap sama info hardware asli

## Arsitektur

```
Camera (preview 1080p + analysis 640x480)
  |
  v
CpuPreprocessor (YUV->RGB, rotate, letterbox, 320x320)
  |
  v
YoloDetector (TFLite INT8, GPU Delegate)
  |
  v
DetectionStabilizer (IOU tracking, EMA smoothing, trajectory)
  |
  v
OverlayView (Choreographer 30fps, interpolation, render ESP)
```

## Tech Stack

- **Bahasa**: Java
- **Camera**: Camera2 API
- **ML**: TensorFlow Lite + GPU Delegate
- **Model**: YOLOv8n INT8 quantized (3.2MB)
- **Render**: Android Canvas + Hardware Acceleration
- **Lokasi**: GNSS API + Fused Location

## Syarat Pemakaian

- Android 8.0+ (API 26)
- Izin kamera
- Izin lokasi (opsional, kalo mau pake fitur GPS)

## Cara Build

```bash
git clone https://github.com/RenzMc/Ren-Esp.git
cd Ren-Esp

./gradlew assembleDebug

adb install app/build/outputs/apk/debug/app-debug.apk
```

## Kompatibilitas Device

Aplikasinya baca FOV kamera, orientasi sensor, sama dimensi layar otomatis pas runtime. Udah dites di Samsung Galaxy A36, Itel p55 5g, sama Infinix smart 10 — tapi harusnya jalan di hape Android manapun yang punya kamera belakang.

## Lisensi

MIT License — cek file [LICENSE](LICENSE) buat detailnya.
