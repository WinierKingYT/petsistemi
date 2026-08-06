package com.petsistemi.listener;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetLevelUpEvent;
import com.petsistemi.api.event.PetRenameEvent;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PetRuntimeSyncListenerTest {

    private ActivePetRegistry registry;
    private PetRuntimeCoordinator coordinator;
    private PetRuntimeSyncListener listener;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID petId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registry = new ActivePetRegistry();
        // Real coordinator with null registries: refreshVisual updates the active
        // pet state but skips representation rendering (no registry wired).
        coordinator = new PetRuntimeCoordinator(null, null, registry, null, null);
        listener = new PetRuntimeSyncListener(registry, coordinator);
    }

    private ActivePet registerActivePet(int level) {
        ActivePet active = new ActivePet(petId, ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        active.setLevel(level);
        registry.register(active);
        return active;
    }

    @Test
    void onLevelUpRefreshesActivePetLevel() {
        ActivePet active = registerActivePet(7);
        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 12, 500,
                PetAvailabilityState.AVAILABLE, true, true);

        listener.onLevelUp(new PetLevelUpEvent(snapshot, 7, 12));

        assertEquals(12, active.getLevel());
        assertNotNull(active.getPetInstance());
        assertEquals(12, active.getPetInstance().level());
        assertEquals("Kurt", active.getPetInstance().customName());
    }

    @Test
    void onRenameRefreshesActivePetName() {
        ActivePet active = registerActivePet(5);
        PetSnapshot oldSnapshot = new PetSnapshot(petId, ownerId, "wolf", "Eski Isim", 5, 100,
                PetAvailabilityState.AVAILABLE, true, true);

        PetRenameEvent event = new PetRenameEvent(null, ownerId, oldSnapshot, "Eski Isim", "Yeni Isim");
        listener.onRename(event);

        assertNotNull(active.getPetInstance());
        assertEquals("Yeni Isim", active.getPetInstance().customName());
    }

    @Test
    void noChangeWhenPetNotActive() {
        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 12, 500,
                PetAvailabilityState.AVAILABLE, true, true);
        listener.onLevelUp(new PetLevelUpEvent(snapshot, 7, 12));

        assertTrue(registry.getByOwner(ownerId).isEmpty());
    }

    @Test
    void noChangeWhenActivePetDoesNotMatch() {
        ActivePet active = new ActivePet(UUID.randomUUID(), ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        active.setLevel(3);
        registry.register(active);

        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 12, 500,
                PetAvailabilityState.AVAILABLE, true, true);
        listener.onLevelUp(new PetLevelUpEvent(snapshot, 7, 12));

        assertEquals(3, active.getLevel(), "mismatched pet must not be refreshed");
        assertNull(active.getPetInstance());
    }

    @Test
    void cancelledRenameDoesNotRefresh() {
        ActivePet active = registerActivePet(5);
        PetRenameEvent event = new PetRenameEvent(null, ownerId,
                new PetSnapshot(petId, ownerId, "wolf", "Eski", 5, 100, PetAvailabilityState.AVAILABLE, true, true),
                "Eski", "Yeni");
        event.setCancelled(true);
        listener.onRename(event);

        assertNull(active.getPetInstance(), "cancelled rename must not refresh visuals");
    }
}
