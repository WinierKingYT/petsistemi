package com.petsistemi.runtime;

import com.petsistemi.domain.*;
import com.petsistemi.runtime.visual.PetVisualHandle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetRepresentationVisualAdapterTest {
    @Test void legacyControllerBecomesNamedVisualGraphAndRemovesChildren() {
        Entity root = entity();
        Entity child = entity();
        PetDefinition definition = PetDefinition.builder("orb", "Orb")
                .representation(PetRepresentationDefinition.display(RuntimeRepresentationType.ITEM_DISPLAY,
                        "AMETHYST_SHARD", null, PetVector3.ONE)).build();
        PetInstance pet = new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "orb", null, 1, 0,
                PetAvailabilityState.AVAILABLE, 1, 1);
        Player owner = mock(Player.class);
        int[] ticks = {0};
        int[] updates = {0};
        int[] rootRemovals = {0};
        PetRepresentationController controller = new PetRepresentationController() {
            @Override public Entity spawn(PetInstance ignored, PetDefinition ignoredDef, Player ignoredOwner) { return root; }
            @Override public List<Entity> spawnChildren(Entity ignored, PetInstance ignoredPet,
                                                        PetDefinition ignoredDef, Player ignoredOwner) { return List.of(child); }
            @Override public void tickVisual(Entity ignored, PetInstance ignoredPet,
                                             PetDefinition ignoredDef, Player ignoredOwner) { ticks[0]++; }
            @Override public void updateVisual(Entity ignored, PetInstance ignoredPet, PetDefinition ignoredDef) { updates[0]++; }
            @Override public void remove(Entity ignored) { rootRemovals[0]++; }
            @Override public boolean isValid(Entity entity) { return entity != null && entity.isValid(); }
        };

        PetVisualHandle visual = controller.spawnVisual(pet, definition, owner);
        controller.tickVisualHandle(visual, pet, definition, owner);
        controller.updateVisualHandle(visual, pet, definition);
        controller.removeVisualHandle(visual);

        assertEquals(List.of("root", "child-1"), visual.components().stream()
                .map(component -> component.id()).toList());
        assertEquals(1, ticks[0]);
        assertEquals(1, updates[0]);
        assertEquals(1, rootRemovals[0]);
        verify(child).remove();
    }

    private static Entity entity() {
        Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        when(entity.isDead()).thenReturn(false);
        return entity;
    }
}
