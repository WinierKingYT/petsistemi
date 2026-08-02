# ADR 0001: SQLite Depolama Motoru ve WAL Modu

## Durum
Kabul Edildi

## Bağlam
Minecraft Paper eklentilerinde harici veritabanı sürücüleri (MySQL/PostgreSQL) ekstra kurulum karmaşıklığı yaratmaktadır. Eklentinin bağımsız, performanslı ve güvenilir çalışması gerekmektedir.

## Karar
SQLite JDBC veritabanı motoru kullanılacaktır. Veritabanı bağlantısı `PRAGMA journal_mode = WAL;`, `PRAGMA foreign_keys = ON;`, `PRAGMA busy_timeout = 5000;` pragma ayarları ile yapılandırılacaktır.

## Sonuçlar
- Harici veritabanı sunucusu gereksinimi ortadan kalkar.
- WAL modu sayesinde okuma operasyonları yazma operasyonlarını engellemez.
- Single-thread executor ile paralel yazma kilitlenmeleri ve veritabanı bozulmaları önlenir.
