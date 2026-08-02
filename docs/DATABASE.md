# DATABASE.md - PetSistemi Veritabanı Dokümanı

## 1. Veritabanı Motoru: SQLite (WAL Modu)

`PetSistemi`, yerel veri depolaması için SQLite veritabanı motorunu kullanır.

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
    state TEXT NOT NULL DEFAULT 'AVAILABLE',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX uq_pets_pet_owner ON pets(pet_id, owner_id);
```

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
