-- v3-imposter-selection.sql
INSERT INTO pets VALUES ('pet-imposter-1', 'owner-actual', 'wolf', 'SharedDog', 1, 0, 'AVAILABLE', 100, 100);
INSERT INTO player_active_pets VALUES ('owner-actual', 'pet-imposter-1', 100);
INSERT INTO player_active_pets VALUES ('owner-imposter', 'pet-imposter-1', 500);
