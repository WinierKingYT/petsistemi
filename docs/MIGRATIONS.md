# MIGRATIONS.md - PetSistemi Migration Rehberi

## 1. Değişmezlik Kuralı (Immutable Migrations Rule)

Bir sürümde yayınlanmış ve veritabanlarına uygulanmış migration sınıfları (`V1`, `V2`, `V3`...) asla sonradan değiştirilemez. 

Her yeni veritabanı şema güncellemesi, uzlaştırması veya düzeltmesi sıralı yeni bir `DatabaseMigration` sınıfı (`V4`, `V5`, `V6`...) olarak eklenir.

## 2. Migration Geçmişi

- **V1 (Initial Schema)**: Temel `pets`, `player_active_pets` ve `schema_migrations` tabloları.
- **V2 (Initial Unique Constraint)**: `player_active_pets.pet_id` benzersizlik kısıtlaması.
- **V3 (Composite Foreign Key)**: `(pet_id, owner_id)` composite foreign key kısıtlaması.
- **V4 (State Reconciliation)**: `DISABLED` pet koruması, sahte seçimlerin silinmesi, uzlaştırma.
- **V5 (Availability State Separation)**: Persistence `ACTIVE` statüsünün `AVAILABLE` / `DISABLED` availability modeline dönüştürülmesi.
- **V6 (Selection Table Rename)**: `player_active_pets` tablosunun `player_selected_pets` olarak taşınması.

## 3. Otomatik Veritabanı Yedekleme (Backup Before Migration)

Migration çalıştırılmadan önce veritabanı otomatik olarak `plugins/PetSistemi/database-backups/database-before-v<targetVersion>-<timestamp>.db` konumuna yedeklenir.
