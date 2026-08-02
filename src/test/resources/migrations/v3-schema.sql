-- v3-schema.sql
CREATE TABLE schema_migrations (
    version INTEGER PRIMARY KEY,
    applied_at INTEGER NOT NULL
);
INSERT INTO schema_migrations VALUES (1, 1000);
INSERT INTO schema_migrations VALUES (2, 2000);
INSERT INTO schema_migrations VALUES (3, 3000);

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

CREATE TABLE player_active_pets (
    owner_id TEXT PRIMARY KEY,
    pet_id TEXT NOT NULL UNIQUE,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE
);
