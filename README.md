# Emotexware v1.0.1

Remote support MVP dengan persetujuan pengguna target. Fitur akses screen/control hanya aktif setelah pengguna target menyetujui sesi dan izin Android yang relevan.

## Komponen
- `android-controller`: aplikasi pengendali
- `android-target`: aplikasi perangkat target
- `server`: WebSocket signaling/relay

## Catatan keamanan
Device ID adalah identifier, bukan rahasia. Gunakan autentikasi/session token untuk deployment internet. Jangan mengandalkan nomor model/serial sebagai kredensial.

## Build
Project Android dibuild di CI 64-bit (contoh GitHub Actions) karena Termux 32-bit tidak dapat menjalankan AAPT2.
