# DATABASE.md - PetSistemi Veritabanı Dokümanı

## 1. Veritabanı Motorları

`PetSistemi`, tek sunucuda varsayılan SQLite/WAL veya paylaşımlı kurulumlarda MySQL 8.x
kullanabilir. Seçim `database.backend` ile yapılır. Network modu MySQL gerektirir.

### SQLite WAL yapılandırması

### Pragma Yapılandırması
```sql
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
PRAGMA busy_timeout = 5000;
PRAGMA synchronous = NORMAL;
```

## 2. Şema Yapısı

### `pets` Tablosu
```sql
CREATE TABLE pets (
    pet_id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    definition_id TEXT NOT NULL,
    custom_name TEXT,
    level INTEGER NOT NULL DEFAULT 1,
    experience INTEGER NOT NULL DEFAULT 0,
    availability_state TEXT NOT NULL DEFAULT 'AVAILABLE',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX uq_pets_pet_owner ON pets(pet_id, owner_id);
```

## 3. MF8 tabloları ve MySQL

V9, SQLite ve MySQL şemalarına `pet_network_events` ile `pet_pack_installations`
tablolarını ekler. MySQL şeması aynı mantıksal kolonları InnoDB, uygun `VARCHAR` tipleri,
foreign key/index'ler ve `AUTO_INCREMENT` kimliklerle oluşturur. `schema_migrations` 1–9
sürümlerini kaydeder; repository adaptörleri MySQL `ON DUPLICATE KEY UPDATE` sözdizimini
kullanır.

`pet_network_events`, sunucu kimliği ve artan event cursor'ı üzerinden cache/runtime
invalidasyonu taşır. Eski event'ler `ecosystem.network.retention-hours` süresine göre
temizlenir. Aynı sahip veya pet üzerindeki network yazımları MySQL named lock ile
serialize edilir.

SQLite dosya yedekleri MySQL'e uygulanmaz. MySQL için sağlayıcı snapshot'ı veya
`mysqldump` kullanın; ayrıntılar [ECOSYSTEM.md](ECOSYSTEM.md) belgesindedir.

### `player_selected_pets` Tablosu
```sql
CREATE TABLE player_selected_pets (
    owner_id TEXT PRIMARY KEY,
    pet_id TEXT NOT NULL UNIQUE,
    selected_at INTEGER NOT NULL,
    FOREIGN KEY (pet_id, owner_id) REFERENCES pets(pet_id, owner_id) ON DELETE CASCADE
);
```

### `schema_migrations` Tablosu
```sql
CREATE TABLE schema_migrations (
    version INTEGER PRIMARY KEY,
    applied_at INTEGER NOT NULL
);
```

### `pet_audit_log` Tablosu
```sql
CREATE TABLE pet_audit_log (
    audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    actor_type TEXT NOT NULL,
    actor_id TEXT,
    action TEXT NOT NULL,
    owner_id TEXT,
    pet_id TEXT,
    details_json TEXT,
    success INTEGER NOT NULL
);
```
