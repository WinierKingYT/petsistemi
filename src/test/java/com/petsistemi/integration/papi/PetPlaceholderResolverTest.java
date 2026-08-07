package com.petsistemi.integration.papi;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.persistence.PlayerPetProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PlaceholderAPI resolves on the main server thread, repeatedly, for every player with a
 * scoreboard or tab entry. These placeholders must therefore be pure reads over an
 * already-cached profile — never a database round trip.
 */
class PetPlaceholderResolverTest {

    private static final UUID OWNER = UUID.randomUUID();

    private static PetSnapshot pet(UUID petId, String definitionId, String customName,
                                   int level, long xp, boolean selected, boolean spawned) {
        return new PetSnapshot(petId, OWNER, definitionId, customName, level, xp,
                PetAvailabilityState.AVAILABLE, selected, spawned);
    }

    private static Optional<PlayerPetProfile> profile(UUID selectedPetId, PetSnapshot... pets) {
        Map<UUID, PetSnapshot> map = new LinkedHashMap<>();
        for (PetSnapshot p : pets) {
            map.put(p.petId(), p);
        }
        return Optional.of(new PlayerPetProfile(OWNER, map, selectedPetId, System.currentTimeMillis(), 1L));
    }

    @Test
    void activePetPlaceholdersReadTheSelectedPet() {
        UUID petId = UUID.randomUUID();
        var p = profile(petId, pet(petId, "wolf", "Karabaş", 7, 250L, true, true));

        assertEquals("wolf", PetPlaceholderResolver.resolve(p, "active_pet"));
        assertEquals("Karabaş", PetPlaceholderResolver.resolve(p, "active_pet_custom_name"));
        assertEquals("7", PetPlaceholderResolver.resolve(p, "active_pet_level"));
        assertEquals("250", PetPlaceholderResolver.resolve(p, "active_pet_xp"));
        assertEquals("Çağrıldı", PetPlaceholderResolver.resolve(p, "active_pet_status"));
    }

    @Test
    void customNameFallsBackToTheDefinitionId() {
        UUID petId = UUID.randomUUID();
        var p = profile(petId, pet(petId, "wolf", null, 1, 0L, true, false));

        assertEquals("wolf", PetPlaceholderResolver.resolve(p, "active_pet_custom_name"));
    }

    @Test
    void selectedButNotSpawnedReportsSelected() {
        UUID petId = UUID.randomUUID();
        var p = profile(petId, pet(petId, "wolf", null, 1, 0L, true, false));

        assertEquals("Seçili", PetPlaceholderResolver.resolve(p, "active_pet_status"));
    }

    @Test
    void withoutASelectedPetTheActivePlaceholdersReportNone() {
        UUID petId = UUID.randomUUID();
        var p = profile(null, pet(petId, "wolf", null, 3, 40L, false, false));

        assertEquals(PetPlaceholderResolver.NONE, PetPlaceholderResolver.resolve(p, "active_pet"));
        assertEquals(PetPlaceholderResolver.NONE, PetPlaceholderResolver.resolve(p, "active_pet_custom_name"));
        assertEquals("0", PetPlaceholderResolver.resolve(p, "active_pet_level"));
        assertEquals("0", PetPlaceholderResolver.resolve(p, "active_pet_xp"));
    }

    @Test
    void totalsCoverEveryOwnedPetNotJustTheActiveOne() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var p = profile(first,
                pet(first, "wolf", null, 5, 120L, true, true),
                pet(second, "cat", null, 2, 30L, false, false));

        assertEquals("150", PetPlaceholderResolver.resolve(p, "total_xp"));
        assertEquals("2", PetPlaceholderResolver.resolve(p, "total_pets"));
    }

    @Test
    void emptyProfileReportsZeroes() {
        var p = profile(null);

        assertEquals("0", PetPlaceholderResolver.resolve(p, "total_xp"));
        assertEquals("0", PetPlaceholderResolver.resolve(p, "total_pets"));
        assertEquals(PetPlaceholderResolver.NONE, PetPlaceholderResolver.resolve(p, "active_pet"));
    }

    /** A cache miss must render a filler, never block the main thread to load the profile. */
    @Test
    void anUncachedProfileRendersFillerInsteadOfLoading() {
        assertEquals(PetPlaceholderResolver.UNKNOWN, PetPlaceholderResolver.resolve(Optional.empty(), "active_pet"));
        assertEquals(PetPlaceholderResolver.UNKNOWN, PetPlaceholderResolver.resolve(Optional.empty(), "total_pets"));
        assertEquals(PetPlaceholderResolver.UNKNOWN, PetPlaceholderResolver.resolve(null, "active_pet_level"));
    }

    /** Unknown keys stay null so PlaceholderAPI can offer them to another expansion. */
    @Test
    void unknownPlaceholdersReturnNull() {
        UUID petId = UUID.randomUUID();

        assertNull(PetPlaceholderResolver.resolve(profile(petId, pet(petId, "wolf", null, 1, 0L, true, true)), "nope"));
        assertNull(PetPlaceholderResolver.resolve(Optional.empty(), "nope"));
        assertNull(PetPlaceholderResolver.resolve(Optional.empty(), ""));
    }

    @Test
    void placeholderKeysAreCaseInsensitive() {
        UUID petId = UUID.randomUUID();
        var p = profile(petId, pet(petId, "wolf", null, 4, 0L, true, true));

        assertEquals("4", PetPlaceholderResolver.resolve(p, "ACTIVE_PET_LEVEL"));
    }
}
