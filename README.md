# PetSistemi 🐾 (v0.2.0-alpha.1)

Advanced, modular Paper 1.20.4 Minecraft Pet System plugin written in Java 17.

## 🚀 Özellikler
- **Modüler Pet Runtime**: Petler `representation` (görünüm) + `movement` (hareket) olarak bağımsız parçalardan oluşur; tek tick task ile yönetilir.
- **Display Petler**: `ITEM_DISPLAY` tabanlı familiar/orbit petler (ör. `arcane_crystal`, `floating_book`) — collision-free, custom-model-data destekli.
- **İmmutable Migration Framework**: V1'den V7'ye geriye dönük güvenli ve korumalı veritabanı yükseltmeleri.
- **State Model Ayrımı**: Availability (`AVAILABLE`, `DISABLED`), Selection (`player_selected_pets`) ve Runtime state ayrımı.
- **Performans & İzolasyon**: SQLite WAL modu ve Single-Thread Database Executor (`PetSistemi-Database-1`).
- **Oyuncu Pet Menüsü (GUI)**: Sayfalamalı (pagination) ve korumalı oyuncu GUI arayüzü.
- **Admin ve Denetim (Audit)**: Kapsamlı sağlık raporu (`/petadmin health`), uzlaştırma (`/petadmin reconcile`) ve audit günlüğü (`pet_audit_log`).
- **MiniMessage & Localization**: `tr_TR` ve `en_US` dil paketleri ile safe placeholder tag kaçırma koruması.
- **Public API & Eventler**: Bukkit ServicesManager entegrasyonu ve kapsamlı yaşam döngüsü etkinlikleri.

## 🛠️ Derleme & Test
```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17"; ./gradlew clean test shadowJar
```

## 📖 Dokümantasyon
- [Kurulum Rehberi](docs/INSTALLATION.md)
- [Yapılandırma](docs/CONFIGURATION.md)
- [Komutlar](docs/COMMANDS.md)
- [Yetkiler](docs/PERMISSIONS.md)
- [Pet Tanımları](docs/PET-DEFINITIONS.md)
- [Mimari Doküman](docs/ARCHITECTURE.md)
- [Veritabanı](docs/DATABASE.md)
- [Public API](docs/API.md)
- [ADR 0001: SQLite Depolama](docs/adr/0001-sqlite-storage.md)
- [ADR 0002: State Ayrımı](docs/adr/0002-selection-runtime-separation.md)
- [ADR 0003: Single DB Executor](docs/adr/0003-single-database-executor.md)
- [ADR 0004: Immutable Migrations](docs/adr/0004-immutable-migrations.md)
- [ADR 0005: Tanım Yükleme Hata Modları](docs/adr/0005-definition-load-failure-modes.md)
