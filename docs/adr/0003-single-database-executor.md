# ADR 0003: Single-Thread Database Executor

## Durum
Kabul Edildi

## Bağlam
SQLite kütüphanesi eşzamanlı çoklu yazma işlemlerinde kilitlenmeler (`SQLITE_BUSY`) yaşayabilir. Ana sunucu thread'i üzerinde I/O işlemleri yapmak sunucu lagına neden olur.

## Karar
Tüm veritabanı OKU, YAZ ve MIGRATION operasyonları özel `PetSistemi-Database-1` adlı tek thread'li bir `DatabaseExecutor` servisi üzerinden asenkron yönetilecektir.

## Sonuçlar
- Paper main thread veritabanı I/O beklemelerinden korunur.
- Eşzamanlı yazma çakışmaları tamamen engellenir.
- Sunucu kapanışında `DatabaseExecutor` bekleyen tüm I/O kuyruğunu güvenle tamamlayarak kapanır.
