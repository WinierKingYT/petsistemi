# DEFINITION-OF-DONE.md - Kabul Kriterleri

## `0.2.0-alpha.1` — Companion Runtime

- [x] `./gradlew clean build` hatasız tamamlanmalı (346 test, 0 hata).
- [x] Paketlenmiş her pet tanımı, sunucunun açılışta çalıştırdığı hattan (parse → validate) hatasız geçmeli — `BundledPetDefinitionsTest`.
- [x] Temiz kurulumda paketlenmiş tüm pet şablonları `pets/` klasörüne kopyalanmalı.
- [x] Bozuk tek bir pet dosyası sunucuyu petsiz bırakmamalı; `/petadmin reload` ise atomik kalmalı — [ADR 0005](adr/0005-definition-load-failure-modes.md).
- [x] Bir petin tick hatası diğer petlerin tick'ini durdurmamalı.
- [x] Dokümante edilen her pet tanımı alanı (`gui-material`, `permission`, `block-material`) gerçekten okunmalı ve uygulanmalı.
- [ ] **`MANUAL-TEST-MATRIX.md` gerçek bir Paper 1.20.4 sunucusunda çalıştırılmalı.** Henüz yapılmadı — bu sürümün alpha kalmasının başlıca nedeni budur. Tüm doğrulama şu an unit test seviyesindedir; hiçbir hareket/görünüm sınıfının gerçek Bukkit davranışı gözlenmemiştir.

## `0.1.0-alpha.1` — Çekirdek Ürün

Aşağıdaki şartlar `0.1.0-alpha.1` sürümü için tanımlanmış ve karşılanmıştır:

- [x] `./gradlew clean test` hatasız tamamlanmalı.
- [x] `./gradlew clean build` hatasız tamamlanmalı.
- [x] `./gradlew clean test shadowJar` hatasız tamamlanmalı ve `PetSistemi-0.1.0-alpha.1.jar` üretilmeli.
- [x] Ana plugin sınıfı (`PetSistemiPlugin`) iş mantığından arındırılmış olmalı.
- [x] Command ve GUI sınıfları `PetRepository` veya raw SQL bağlantılarına erişmemeli.
- [x] Domain katmanı Bukkit tiplerinden (`Player`, `Entity`, `Inventory`) bağımsız olmalı.
- [x] Availability, Selection ve Runtime State birbirinden ayrılmış olmalı.
- [x] Uygulanmış migration sınıfları (`V1`..`V4`) değiştirilemez (immutable) tutulmalı.
- [x] Migration öncesi veritabanı yedeği alınmalı.
- [x] SQLite okuma/yazma operasyonları Paper main thread yerine Single-Thread Database Executor üzerinden yürütülmeli.
- [x] Kapsamlı kalite ve mimari dokümantasyonu tamamlanmış olmalı.
