-- v3-aborted-temp-table.sql
CREATE TABLE player_active_pets_v3 (
    owner_id TEXT PRIMARY KEY,
    pet_id TEXT NOT NULL UNIQUE,
    updated_at INTEGER NOT NULL
);
INSERT INTO player_active_pets_v3 VALUES ('owner-old-aborted', 'pet-old', 10);
