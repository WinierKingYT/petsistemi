# PETSİSTEMİ ROADMAP EXECUTION TRACKER

- **Başlangıç Commit:** `71c915c`
- **Mevcut Sürüm:** `0.2.0-alpha.1`
- **Milestone 0-13:** `COMPLETED` (`0.1.0-alpha.1` ile)

---

## 📌 Milestone Durum Tablosu

| Milestone | Açıklama | Durum |
| :--- | :--- | :--- |
| **Milestone 0** | Build ve Kalite Zemini (Shadow Plugin, Versioning, Quality Docs) | `COMPLETED` |
| **Milestone 1** | Immutable Migration Framework (`DatabaseMigration`, `MigrationRunner`, Backup) | `COMPLETED` |
| **Milestone 2** | State Modelini Ayır (`PetAvailabilityState`, `player_selected_pets`, V5/V6) | `COMPLETED` |
| **Milestone 3** | Bootstrap ve Plugin Lifecycle (`PetPluginContext`, `TaskRegistry`, Registrars) | `COMPLETED` |
| **Milestone 4** | Typed Config, Messages ve Reload (Validation, MiniMessage i18n, Atomic Reload) | `COMPLETED` |
| **Milestone 5** | Application Use-Case Katmanı (`PetQueryService`, `PetLifecycleService`, `PetAdminService`) | `COMPLETED` |
| **Milestone 6** | Player Command ve GUI (Menu Framework, Pagination, Rename Session, Exploit Guard) | `COMPLETED` |
| **Milestone 7** | Pet Definition Sistemi (Schema v1, Validator, Atomic Registry) | `COMPLETED` |
| **Milestone 8** | Runtime State Machine ve Recovery (State Engine, Tick Buckets, Recovery Queue, PDC v1) | `COMPLETED` |
| **Milestone 9** | Progression (Experience Curves, Transactional XP/Level, Progression Events) | `COMPLETED` |
| **Milestone 10** | Admin, Audit ve Health (Fine-grained Perms, Audit Log, Health Report, Backup Tool) | `COMPLETED` |
| **Milestone 11** | Database Executor ve Cache (Single-thread Executor, WAL, Profile Cache, Tab-Complete Guard) | `COMPLETED` |
| **Milestone 12** | Public API (Multi-module/API contracts, Thread Contracts, Events, Example Plugin) | `COMPLETED` |
| **Milestone 13** | CI, Release ve Dokümantasyon (GitHub Actions, Manual Test Matrix, Shaded Release JAR) | `COMPLETED` |

---

## 📋 Değişiklik Günlüğü ve Notlar

- **[Başlangıç]**: `71c915c` commitinden başlandı.

---

## 🧭 Sonraki Aşamalar (Companion Runtime Vizyonu)

Milestone 0-13 (temel ürün) tamamlandı. Ürünün devamı `VISION.md`'de tanımlanan companion
runtime vizyonuna göre ilerler: 4 ürün ailesi (Canlı/Familiar/Attached/Special), 33 sistem
kataloğu ve Faz A/B/C yol haritası.

- **Faz 0 (tamamlandı)**: İlk genişleme paketi — ENTITY+GROUND_FOLLOW, ITEM_DISPLAY+FLYING_FOLLOW,
  ITEM_DISPLAY+ORBIT, DISPLAY+SHOULDER/ANCHORED, DISPLAY+TRAIL, SWARM+FORMATION; idle/sleep,
  reaction çekirdeği, level-scale, mode persistence (V8).
- **Faz A (kısmen tamamlandı — `0.2.0-alpha.1`)**:
  - ✅ Per-pet `states:` şeması (`sleepy_cat`)
  - ✅ Dönüşümler + çevre varyantları (`wisplight`)
  - ✅ Per-pet reaction/emote şeması + `/pet emote`
  - ✅ ECHO / SHADOW_TRAIL / ROAM_NEAR_OWNER / MIRROR hareketleri
  - ⬜ SWARM (RANDOM_CLOUD) ve COMPOSITE (role) ayrımı — #9/#10
  - ⬜ Interaction hitbox (PARTICLE petler) — #31
  - ⬜ Kişilik profilleri — #25
  - ⬜ Stage'li visual progression — #26
- **Faz B**: Orta seviye sistemler (bkz. VISION.md §4).
- **Faz C**: Yüksek riskli sistemler (binek, pet evi, packet görünürlük, evrim) — core stabil olunca.

---

## 🏗️ Motor Fazları (MF1-MF8)

Yukarıdaki Faz A/B/C **hangi özelliklerin** geleceğini söyler. Bu özelliklerin üzerine
oturacağı motor katmanları ayrı bir eksende ilerler ve `ENGINE-ROADMAP.md`'de tanımlıdır:

| Faz | Konu | Durum |
| :--- | :--- | :--- |
| **MF1** | Namespaced Registry — açık movement/representation kaydı | `COMPLETED` |
| **MF2** | Behavior Engine — Trigger / Condition / Action | `COMPLETED` |
| **MF3** | Ability / Skill | `COMPLETED` |
| **MF4** | Animation Abstraction | `COMPLETED` |
| **MF5** | Model Provider adaptörleri (ModelEngine vb.) | `COMPLETED` |
| **MF6** | Collection GUI + oyun içi editör | `COMPLETED` |
| **MF7** | Gameplay (item action, evolution, orders, mount) | `COMPLETED` — MF7a + MF7b + MF7c + MF7d + MF7e |
| **MF8** | Ekosistem (MySQL/network, Pet Packs, marketplace) | `COMPLETED` |

Kilitlenen kararlar, çıkış kriterleri, kapsam dışı maddeler ve borç kaydı için bkz.
[ENGINE-ROADMAP.md](ENGINE-ROADMAP.md).

- **[2026-08-08 · MF1]**: Movement/representation registry'leri namespaced anahtarlara
  taşındı; özel YAML anahtarları runtime'a kadar korunuyor, yerleşik enum API'si ve
  `FAST_DIGGING` → `HASTE` 1.20.4 uyumluluk çözümlemesi geriye uyumlu kaldı.
- **[2026-08-08 · MF2 başlangıcı]**: Açık trigger/condition/action registry'leri ve
  behavior executor eklendi. Eski `reactions:`/`emotes:` akışları uyumluluk adaptörüyle
  yeni motor üzerinden çalışıyor; native YAML ve buff action sonraki dilimde.
- **[2026-08-08 · MF2 tamamlandı]**: Native `behaviors:` YAML, buff action adaptörü ve
  Bukkit `BehaviorService` eklendi. Legacy reaction/emote/buff davranışı korunarak
  üçüncü taraf trigger/condition/action kaydı açıldı; 512 test yeşil.
- **[2026-08-08 · MF3 başlangıcı]**: Behavior tabanlı ability YAML modeli, cooldown,
  hedef seçimi ve `/pet ability` komutu eklendi. Projectile/AoE action'ları ve tuş
  bağlama sonraki dilimde.
- **[2026-08-08 · MF3 tamamlandı]**: Yerleşik projectile, alan potion ve alan hasarı
  action'ları ile oturumluk çömelme + el değiştirme tuş bağı eklendi. Başarısız
  hedefleme cooldown tüketmiyor; 522 test yeşil.
- **[2026-08-08 · MF4 tamamlandı]**: Provider-bağımsız animasyon state machine,
  namespaced klip/öncelik/blend metadata'sı ve ortak vanilla/display animasyon arayüzü
  eklendi. Eski idle animation YAML'ı korunurken IDLE/MOVING/SPRINTING/SLEEPING ile
  öncelikli ATTACKING geçişleri açıldı; 526 test yeşil.
- **[2026-08-08 · MF5 tamamlandı]**: ModelEngine, ItemsAdder ve Oraxen için isteğe bağlı
  model adaptörleri; `representation.model-id`; Bukkit `ModelProviderService` ve güvenli
  eksik-provider davranışı eklendi. Harici API'ler core linkage'ından ayrıldı; 535 test
  yeşil. Kullanım: [MODEL-PROVIDERS.md](MODEL-PROVIDERS.md).
- **[2026-08-08 · MF6 tamamlandı]**: Tüm pet tanımlarını kilitli/açık durumuyla gösteren,
  filtreli ve sayfalı `/pet collection`; chat girdili `/petadmin editor`; üretim doğrulama
  zinciri, iyimser dosya kilidi, atomik yazma ve başarısız yayında rollback eklendi.
  Tam paket 539/539 yeşil. Kullanım: [COLLECTION-EDITOR.md](COLLECTION-EDITOR.md).
- **[2026-08-08 · MF7a tamamlandı]**: Açık `PetItemActionService`, namespaced action
  registry, materyal/custom-model-data eşleştirmesi, başarıya bağlı tüketim/cooldown ve
  yerleşik XP/unlock aksiyonları eklendi. `wolf` kemik yemiyle örneklendi. MF7 bütünü
  orders/evolution/mount-control tamamlanana kadar sürüyor; tam paket 547/547 yeşil. Kullanım:
  [ITEM-ACTIONS.md](ITEM-ACTIONS.md).
- **[2026-08-08 · MF7b tamamlandı]**: `evolutions:` şeması en yüksek uygun seviye
  aşamasını seçen runtime controller'a bağlandı; hedef tanım + ad/ölçek override'ı transforms
  öncesinde uygulanıyor. Eksik hedef ve canlı değiştirilemeyen provider geçişleri klasör
  genelinde doğrulanıyor; tam paket 551/551 yeşil. Kullanım: [EVOLUTIONS.md](EVOLUTIONS.md).
- **[2026-08-08 · MF7c tamamlandı]**: Bukkit `PetOrderService`, namespaced order registry,
  oyuncu başına devam eden işlem koruması ve yerleşik `follow/stay/wander/come` emirleri
  eklendi. Eski `/pet mode` aynı motor üzerinden geriye uyumlu çalışıyor; `come` multi-entity
  petlerin bütün parçalarını taşırken kalıcı modu değiştirmiyor. Tam paket 557/557 yeşil.
  Kullanım: [ORDERS.md](ORDERS.md).
- **[2026-08-08 · MF7d tamamlandı]**: Merkezî `PetMountController`, Bukkit
  `PetMountService`, WASD/zıplama giriş adaptörü, kara/uçuş velocity kontrolü ve gravity
  geri yüklemeli lifecycle temizliği eklendi. Binek tick'i normal follow movement'ı bastırırken
  görsel zincir çalışmayı sürdürüyor; tam paket 568/568 yeşil. Kullanım: [MOUNTS.md](MOUNTS.md).
- **[2026-08-08 · MF7e / MF7 tamamlandı]**: `petsistemi:evolve_pet` kalıcı tanım
  değişimini pet kimliği ve progression verisini koruyarak uygular; aktif runtime spawn
  başarısızlığında DB ve görünüm geri alınır. Aktif pet gerektirmeyen PDC işaretli unlock
  itemi, `/petadmin unlockitem`, `PetUnlockItemService` ve asenkron item iadesi eklendi.
  Tam paket 575/575 yeşil. Kullanım: [ITEM-ACTIONS.md](ITEM-ACTIONS.md),
  [EVOLUTIONS.md](EVOLUTIONS.md).
- **[2026-08-08 · MF8 tamamlandı]**: MySQL V9 backend ve CI servisi, paylaşımlı event
  cursor'ı + dağıtık kilitli network senkronizasyonu, atomik namespaced Pet Pack yönetimi
  ve HTTPS/SHA-256 korumalı marketplace tamamlandı. Bukkit network/pack/marketplace
  servisleri üçüncü taraflara açıldı; tam regresyon paketi 587/587 yeşil. Kullanım:
  [ECOSYSTEM.md](ECOSYSTEM.md).
