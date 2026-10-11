# Environment & Position Sensor: Intensitas Cahaya dan Kompas

Aplikasi Android (Kotlin + Jetpack Compose) yang memakai dua sensor dari kategori berbeda dan langsung menampilkan efeknya di UI.

- **Bagian atas:** jam realtime dan langit yang berubah mengikuti cahaya sekitar (**Light sensor**, kategori Environment).
- **Bagian bawah:** kompas yang berputar mengikuti medan magnet bumi (**Magnetic Field sensor**, kategori Position).

## Memenuhi syarat tugas

| Syarat | Implementasi |
|---|---|
| Sensor dari kategori **Environment** | Light sensor (`Sensor.TYPE_LIGHT`) |
| Sensor dari kategori **Position** | Magnetic Field sensor (`Sensor.TYPE_MAGNETIC_FIELD`) |
| Sensor memberi efek langsung ke UI | Light mengubah tema langit, Magnetic Field memutar kompas |
| Lifecycle sensor | `registerListener` di `onResume()`, `unregisterListener` di `onPause()` |

---

## Apa itu Environment Sensor?

Environment sensor mengukur **kondisi lingkungan sekitar** perangkat.

| Sensor | Fungsi |
|---|---|
| **Light** | Intensitas cahaya, satuan lux |
| Ambient Temperature | Suhu ruangan, satuan °C (tidak tersedia di banyak HP) |
| Relative Humidity | Kelembapan udara, satuan % |
| Pressure | Tekanan udara, satuan hPa |

**Yang dipakai di aplikasi ini: Light sensor.**

## Apa itu Position Sensor?

Position sensor mengukur **posisi fisik dan orientasi** perangkat terhadap lingkungan.

| Sensor | Fungsi |
|---|---|
| **Magnetic Field** | Kekuatan medan magnet pada tiga sumbu, satuan µT |
| Proximity | Jarak benda dekat (misalnya telinga atau tangan) ke layar |
| Game Rotation Vector | Orientasi tanpa pengaruh medan magnet |
| Geomagnetic Rotation Vector | Orientasi berbasis medan magnet |

**Yang dipakai di aplikasi ini: Magnetic Field sensor.**

---

## 1. Intensitas Cahaya (Light Sensor)

Light sensor membaca intensitas cahaya sekitar dalam lux, lalu aplikasi mengubah tampilan langit di bagian atas.

| Cahaya | Kondisi | Tampilan |
|---|---|---|
| < 20 lux | Malam | Langit biru tua, bulan, bintang berkedip |
| 20 sampai 150 lux | Sore / Senja | Gradasi ungu ke jingga, matahari jingga turun mendekati horizon |
| 150 sampai 500 lux | Mendung / Redup | Langit abu-biru, awan bergeser, matahari samar |
| > 500 lux | Cerah | Langit biru cerah, matahari dengan sinar berputar |

Efek lain:
- Warna kartu kompas di bagian bawah ikut berubah mengikuti kondisi cahaya.
- Chip di langit menampilkan nama kondisi dan nilai lux terbaru.
- Perpindahan warna dan benda langit dibuat halus dengan animasi (`animateColorAsState`, `animateFloatAsState`).

Kode penentu kondisi:

```kotlin
val level = when {
    lux < 20f -> 0    // Malam
    lux < 150f -> 1   // Sore / Senja
    lux < 500f -> 2   // Mendung / Redup
    else -> 3         // Cerah
}
```

Laju pembacaan: `SENSOR_DELAY_NORMAL`, karena cahaya berubah tidak secepat gerakan.

### Fitur UI pendukung
- **Jam realtime** (HH:mm:ss), tanggal dalam bahasa Indonesia, dan sapaan sesuai jam (pagi, siang, sore, malam).
- **Animasi langit:** sinar matahari berputar, awan bergeser, bintang berkedip.

---

## 2. Kompas (Magnetic Field Sensor)

Magnetic Field sensor membaca medan magnet bumi pada sumbu X, Y, Z. Dari nilai X dan Y dihitung arah hadap perangkat (heading), lalu piringan kompas diputar berlawanan arah supaya huruf **N** selalu menunjuk ke utara.

```kotlin
// Low-pass filter supaya jarum tidak bergetar
mx = mx * 0.9f + event.values[0] * 0.1f
my = my * 0.9f + event.values[1] * 0.1f
mz = mz * 0.9f + event.values[2] * 0.1f

// Sudut arah hadap (0 sampai 360 derajat)
val heading = ((Math.toDegrees(atan2(-mx, my).toDouble()) + 360.0) % 360.0).toFloat()
```

Yang ditampilkan:
- Piringan kompas berskala dengan huruf N, E, S, W.
- Jarum merah (utara) dan jarum abu-abu (selatan).
- Sudut dan nama arah, misalnya "135° Tenggara".
- Kekuatan medan magnet dalam µT.

Laju pembacaan: `SENSOR_DELAY_UI`.

**Batasan:**
- Perhitungan ini hanya akurat saat HP diletakkan **datar**.
- Jauhkan HP dari magnet, speaker, atau laptop karena bisa mengganggu pembacaan.
- Jika arah melenceng, gerakkan HP membentuk angka 8 untuk kalibrasi.

---

## Lifecycle sensor

Sensor didaftarkan saat aplikasi tampil dan dilepas saat aplikasi tidak aktif, supaya tidak menguras baterai.

```kotlin
override fun onResume() {
    super.onResume()
    lightSensor?.also {
        sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
    }
    magnetSensor?.also {
        sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
    }
}

override fun onPause() {
    super.onPause()
    sensorManager.unregisterListener(this)
}
```

Aplikasi juga memeriksa ketersediaan sensor di `onCreate()`. Jika sebuah sensor tidak ada di perangkat, pesan ditampilkan di layar dan aplikasi tidak crash.

---

## Cara menjalankan

1. Buka project di Android Studio, lalu **Sync Project with Gradle Files**.
2. Hubungkan HP asli (USB debugging atau Wireless debugging) atau gunakan emulator.
3. Klik tombol **Run**.

### Cara menguji

| Sensor | Cara |
|---|---|
| Light | Tutup sensor depan HP dengan tangan untuk mode Malam, arahkan ke senter untuk mode Cerah |
| Magnetic Field | Letakkan HP datar di meja lalu putar badannya |
| Lifecycle | Tekan tombol Home lalu buka lagi aplikasinya, sensor tetap berjalan |

Di emulator, atur nilai sensor lewat **Extended Controls → Virtual sensors**.

## Struktur kode

Seluruh kode ada di satu file, `MainActivity.kt`.

| Bagian | Fungsi |
|---|---|
| `MainActivity` | Mengelola sensor, lifecycle, dan menyimpan nilai sensor sebagai state |
| `EnvironmentSensorScreen` | Tampilan utama: langit dan jam di atas, kompas di bawah |
| `CompassDial` | Piringan kompas yang berputar |
| `drawCloud` | Fungsi gambar awan |

Video Demo Singkat
https://drive.google.com/drive/folders/1rMHsGhw_pCWniSnY7b4loQOOSzyu4jm4?usp=sharing
