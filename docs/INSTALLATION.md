# INSTALLATION.md - PetSistemi Kurulum Rehberi

## Gereksinimler
- **Minecraft Sunucu Sürümü:** Paper 1.20.4 (veya uyumlu Paper türevleri Purpur/Folia)
- **Java Sürümü:** Java 17 veya üzeri

## Kurulum Adımları
1. Releases sayfasından `PetSistemi-0.1.0-alpha.1.jar` dosyasını indirin.
2. İndirdiğiniz `.jar` dosyasını sunucunuzun `plugins/` klasörüne yapıştırın.
3. Sunucuyu başlatın veya `/reload confirm` komutunu çalıştırın.
4. Eklenti `plugins/PetSistemi/` klasörünü, varsayılan `config.yml`, `messages/` ve `pets/` klasörlerini otomatik olarak oluşturacaktır.

## Eski Veritabanından Yükseltme (Upgrade from V1/V2/V3)
- Eski veritabanı `pets.db` otomatik olarak algılanır.
- Migration V4, V5, V6 ve V7 adımları otomatik çalışarak şemayı ve veri bütünlüğünü bozmadan `0.1.0-alpha.1` sürümüne yükseltir.
- Otomatik veritabanı yedeği `plugins/PetSistemi/database-backups/` klasörüne kaydedilir.
