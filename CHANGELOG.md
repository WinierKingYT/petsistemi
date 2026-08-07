# CHANGELOG - PetSistemi

## [0.2.0-alpha.1] - 2026-08-07

Companion runtime genişlemesi: pet artık "oyuncunun peşinden gelen mob" değil,
görünüm (`representation`) ve hareket (`movement`) olarak iki bağımsız parçadan
kurulan bir çalışma zamanı. Ayrıntılar için bkz. [VISION.md](docs/VISION.md).

### Eklenenler
- **7 görünüm türü**: `ENTITY`, `ITEM_DISPLAY`, `BLOCK_DISPLAY`, `TEXT_DISPLAY`, `PARTICLE`, `MULTI_ENTITY`, `INVISIBLE`.
- **14 hareket türü**: `GROUND_FOLLOW`, `FLYING_FOLLOW`, `HOVER`, `ORBIT`, `TRAIL`, `FORMATION`, `SHOULDER`, `ANCHORED`, `STATIC_NEAR_OWNER`, `TELEPORT_ONLY`, `ECHO`, `SHADOW_TRAIL`, `ROAM_NEAR_OWNER`, `MIRROR`.
- Per-pet `states:` şeması (MOVING/IDLE, `after-ticks` global eşiği ezer) — `sleepy_cat`.
- `transforms:` şeması: `when` (owner-state / biome / world / time-of-day / weather) + `apply` görsel override — `wisplight`.
- Per-pet `reactions:` ve `emotes:` şemaları; `/pet emote <ad>` komutu, tab completion ve per-owner cooldown.
- `/pet mode <follow|stay|wander>` ile kalıcı takip modu (V8 migration).
- Level-scale görsel büyüme (`features.level-scaling.*`), idle/sleep rest ölçeği.
- 13 yeni örnek pet tanımı (`arcane_crystal`, `floating_book`, `shoulder_orb`, `ghost_scribe`, `familiar_swarm`, `void_cube`, `spirit_flame`, `sleepy_cat`, `wisplight`, `shadow_wisp`, `mirror_doll`, `echo_phantom`, `roam_fox`).
- Pet tanımlarında `gui-material` (GUI ikonu) ve `permission` (pet bazlı yetki) alanları artık okunuyor ve uygulanıyor.
- [ADR 0005](docs/adr/0005-definition-load-failure-modes.md): tanım yükleme hata modları.

### Düzeltmeler
- **Açılışta tüm pet sistemi çökebiliyordu**: `shadow_wisp.yml` parser'ın okumadığı `block-material` anahtarını kullanıyordu ve `sleepy_cat.yml` 1.20.4'te bulunmayan `SMOKE` parçacığını istiyordu. Tek hatalı dosya tüm kaydı reddettiği için sunucu hiç petsiz açılıyordu.
- **Tanım yükleme hata modları ayrıldı**: açılış hoşgörülü (geçerli petler yüklenir, bozuklar SEVERE loglanır), `/petadmin reload` katı kalır (tek hata adayı reddeder, çalışan tanımlar korunur).
- **Temiz kurulumda 16 petin yalnızca 3'ü kopyalanıyordu**; artık paketlenmiş tüm petler kuruluma dahil.
- **GUI ikonu** `wolf`/`cat`/`allay` üçlüsüne sabitlenmişti; diğer tüm petler `BONE` görünüyordu.
- **PARTICLE petleri kendi konumlarını yok sayıyordu**: aura sabit oyuncu konumunda çiziliyordu, bu yüzden `movement.type` ve `movement.height` bu petlerde tamamen etkisizdi.
- **Tick döngüsünde hata izolasyonu yoktu**: tek bir petin istisnası o turda sırada bekleyen tüm petleri atlıyordu. Artık her pet ayrı ele alınıyor, hata pet başına bir kez loglanıyor.
- Validator artık `gui-material` ve `transforms[].apply.particle-type` doğruluyor (geçersiz particle sessizce efekti kapatıyordu).
- Ölü `YamlPetDefinitionRegistry` kaldırıldı (hiç örneklenmiyordu, eski şemayı okuyordu).

### Dokümantasyon
- `COMMANDS.md`: `/pet mode`, `/pet emote` ve 6 admin komutu eklendi.
- `PERMISSIONS.md`: eksik 10 admin düğümü ve pet bazlı yetki bölümü eklendi.
- `MANUAL-TEST-MATRIX.md`: bozuk YAML davranışını çelişkili biçimde tarif eden iki satır ayrıştırıldı.
- `PET-DEFINITIONS.md` / `wolf.yml`: hiç okunmayan `behavior-profile`, `follow-speed`, `follow-distances` anahtarları temizlendi.

### Testler
- 307 → 346 test, 0 hata. `BundledPetDefinitionsTest` paketlenmiş her peti sunucunun çalıştırdığı hattan (parse → validate) geçiriyor; `PetDefinitionLoadFailureModeTest` iki yükleme modunu kilitliyor; `PetTickIsolationTest`, `ParticlePetRepresentationTest`, `GroundFollowMovementTest` eklendi.

> ⚠️ `MANUAL-TEST-MATRIX.md` henüz gerçek sunucuda çalıştırılmadı; bu sürüm alpha'dır.

## [0.1.0-alpha.1] - 2026-08-03

### Eklenenler
- Gradle Shadow Plugin entegrasyonu ve `PetSistemi-0.1.0-alpha.1.jar` release artifact üretimi.
- Baştan sona yenilenmiş `DatabaseMigration` ve `MigrationRunner` mimarisi (V1, V2, V3, V4, V5, V6, V7).
- Otomatik migration öncesi veritabanı yedeği alma sistemi (`database-backups/`).
- `PetAvailabilityState` (`AVAILABLE`, `DISABLED`), `PetSelection` (`player_selected_pets`) ve `PetRuntimeState` modellerine geçiş.
- Bağımsız `PetPluginBootstrap` ve modüler `Registrar` sınıfları.
- Typed `PluginConfiguration` ve `ConfigurationValidator` fail-fast mekanizması.
- `MessageService`, MiniMessage tag kaçırma koruması ve `tr_TR` / `en_US` dil paketleri.
- `PetListMenu` GUI (sayfalama, status göstergeleri ve koruma).
- `PetIdResolver` kısa UUID çözümleme mantığı.
- `ExperienceCurve` eğrileri (Linear, Exponential, Table).
- `AuditLogger` ile `pet_audit_log` veritabanı denetim kaydı.
- `DatabaseExecutor` (`PetSistemi-Database-1` single-thread asenkron veritabanı yürütücüsü).
- Public API paketindeki `PetSelectionChangedEvent` ve `PetRecoveryFailedEvent` etkinlikleri.
- GitHub Actions CI build workflow (`.github/workflows/build.yml`).
