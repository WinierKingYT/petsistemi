package com.petsistemi.persistence;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.domain.PetAvailabilityState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerPetProfileCacheConcurrencyTest {

    private PlayerPetProfileCache cache;
    private UUID ownerId;
    private UUID petId;

    @BeforeEach
    void setUp() {
        cache = new PlayerPetProfileCache(null, null);
        ownerId = UUID.randomUUID();
        petId = UUID.randomUUID();
    }

    @Test
    void testStaleLoadRejectedAfterQuitOrInvalidate() {
        long gen1 = cache.beginLoad(ownerId);
        cache.invalidate(ownerId); // Invalidate increments generation counter

        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, false, false);
        PlayerPetProfile staleProfile = new PlayerPetProfile(ownerId, Map.of(petId, snapshot), null, System.currentTimeMillis(), 1L);

        boolean accepted = cache.completeLoad(ownerId, gen1, staleProfile);
        assertFalse(accepted);
        assertTrue(cache.getProfile(ownerId).isEmpty());
    }

    @Test
    void testVersionIncrementsOnMutation() {
        PetSnapshot pet = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, false, false);
        PlayerPetProfile initialProfile = new PlayerPetProfile(ownerId, Map.of(petId, pet), null, System.currentTimeMillis(), 1L);
        cache.putLoadedProfile(initialProfile);

        assertEquals(1L, cache.getProfile(ownerId).orElseThrow().version());

        cache.updateName(ownerId, petId, "Yeni Kurt");
        PlayerPetProfile updated1 = cache.getProfile(ownerId).orElseThrow();
        assertEquals(2L, updated1.version());
        assertEquals("Yeni Kurt", updated1.pets().get(petId).customName());

        cache.updateExperience(ownerId, petId, 5, 500L);
        PlayerPetProfile updated2 = cache.getProfile(ownerId).orElseThrow();
        assertEquals(3L, updated2.version());
        assertEquals(5, updated2.pets().get(petId).level());
        assertEquals(500L, updated2.pets().get(petId).experience());
    }

    @Test
    void testSelectionUpdateAndClear() {
        PetSnapshot pet = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, false, false);
        PlayerPetProfile initialProfile = new PlayerPetProfile(ownerId, Map.of(petId, pet), null, System.currentTimeMillis(), 1L);
        cache.putLoadedProfile(initialProfile);

        cache.updateSelection(ownerId, petId);
        PlayerPetProfile profileSelected = cache.getProfile(ownerId).orElseThrow();
        assertEquals(petId, profileSelected.selectedPetId());
        assertTrue(profileSelected.pets().get(petId).selected());

        cache.clearSelection(ownerId);
        PlayerPetProfile profileCleared = cache.getProfile(ownerId).orElseThrow();
        assertNull(profileCleared.selectedPetId());
        assertFalse(profileCleared.pets().get(petId).selected());
    }

    @Test
    void testUnmodifiableMapEnforced() {
        PetSnapshot pet = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, false, false);
        PlayerPetProfile initialProfile = new PlayerPetProfile(ownerId, Collections.unmodifiableMap(Map.of(petId, pet)), null, System.currentTimeMillis(), 1L);
        cache.putLoadedProfile(initialProfile);

        PlayerPetProfile profile = cache.getProfile(ownerId).orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> profile.pets().clear());
    }
}
