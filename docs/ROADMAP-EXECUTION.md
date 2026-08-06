# PETSİSTEMİ 0.1.0-ALPHA.1 ROADMAP EXECUTION TRACKER

- **Başlangıç Commit:** `71c915c`
- **Mevcut Branch:** `main`
- **Hedef Sürüm:** `0.1.0-alpha.1`

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
- **Faz A**: Per-pet `states:` şeması, dönüşümler + çevre varyantları, per-pet reaction/emote,
  ECHO/SHADOW_TRAIL/ROAM_NEAR_OWNER/MIRROR hareketleri, SWARM/COMPOSITE ayrımı, interaction
  hitbox, kişilik, stage'li visual progression.
- **Faz B**: Orta seviye sistemler (bkz. VISION.md §4).
- **Faz C**: Yüksek riskli sistemler (binek, pet evi, packet görünürlük, evrim) — core stabil olunca.
