# DEFINITION-OF-DONE.md - Kabul Kriterleri

Projenin `0.1.0-alpha.1` sürümü olarak yayınlanabilmesi için aşağıdaki şartların eksiksiz karşılanması zorunludur:

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
