# INSTALLATION.md - PetSistemi Kurulum Rehberi

## Gereksinimler
- **Minecraft Sunucu Sürümü:** Paper 1.20.4 (veya uyumlu Paper türevleri Purpur/Folia)
- **Java Sürümü:** Java 17 veya üzeri

## Kurulum Adımları
1. Releases sayfasından `PetSistemi-0.2.0-alpha.1.jar` dosyasını indirin.
2. İndirdiğiniz `.jar` dosyasını sunucunuzun `plugins/` klasörüne yapıştırın.
3. Sunucuyu başlatın veya `/reload confirm` komutunu çalıştırın.
4. Eklenti `plugins/PetSistemi/` klasörünü, varsayılan `config.yml`, `messages/` ve `pets/` klasörlerini otomatik olarak oluşturacaktır.

## Eski Veritabanından Yükseltme (Upgrade from V1/V2/V3)
- Eski veritabanı `pets.db` otomatik olarak algılanır.
- Migration V4–V9 adımları otomatik çalışarak şemayı ve veri bütünlüğünü bozmadan `0.2.0-alpha.1` sürümüne yükseltir (V8 kalıcı takip modunu, V9 MF8 event/pack tablolarını ekler).
- Otomatik veritabanı yedeği `plugins/PetSistemi/database-backups/` klasörüne kaydedilir.

## MySQL / Network Kurulumu

1. Boş bir MySQL 8.x veritabanı ve sınırlı yetkili kullanıcı oluşturun.
2. `database.backend: MYSQL` seçip `database.mysql` bağlantı bilgilerini doldurun.
3. Network kullanılacaksa her sunucuda `ecosystem.network.enabled: true`, ortak DB ve
   benzersiz `server-id` ayarlayın.
4. Sunucuları başlatın; V9 MySQL şeması otomatik oluşturulur.

SQLite verisi MySQL'e otomatik taşınmaz. Geçiş ve sağlayıcı yedeği ayrıntıları için
[ECOSYSTEM.md](ECOSYSTEM.md).
