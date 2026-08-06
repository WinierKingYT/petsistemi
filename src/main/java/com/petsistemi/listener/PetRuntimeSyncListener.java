package com.petsistemi.listener;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetLevelUpEvent;
import com.petsistemi.api.event.PetRenameEvent;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the runtime visuals of an active pet in sync with persisted state changes:
 * level-up and rename re-render the representation (e.g. TEXT_DISPLAY body text)
 * and update the level used by ability tasks.
 */
public class PetRuntimeSyncListener implements Listener {

    private final ActivePetRegistry activeRegistry;
    private final PetRuntimeCoordinator coordinator;

    public PetRuntimeSyncListener(ActivePetRegistry activeRegistry, PetRuntimeCoordinator coordinator) {
        this.activeRegistry = activeRegistry;
        this.coordinator = coordinator;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(PetLevelUpEvent event) {
        PetSnapshot snapshot = event.getPetSnapshot();
        if (snapshot == null || snapshot.ownerId() == null || snapshot.petId() == null) return;
        refresh(snapshot.ownerId(), snapshot, snapshot.customName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRename(PetRenameEvent event) {
        if (event.isCancelled()) return;
        UUID ownerId = event.getOwnerId();
        PetSnapshot snapshot = event.getPet();
        if (ownerId == null || snapshot == null || snapshot.petId() == null) return;
        refresh(ownerId, snapshot, event.getNewName());
    }

    private void refresh(UUID ownerId, PetSnapshot snapshot, String customName) {
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(ownerId);
        if (activeOpt.isEmpty()) return;

        PetInstance instance = new PetInstance(
                snapshot.petId(), snapshot.ownerId(), snapshot.definitionId(),
                customName != null ? customName : snapshot.customName(),
                snapshot.level(), snapshot.experience(), snapshot.availabilityState(),
                System.currentTimeMillis(), System.currentTimeMillis());

        coordinator.refreshVisual(ownerId, instance);
    }
}
