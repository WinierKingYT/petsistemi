package com.petsistemi.runtime.visual;

import com.petsistemi.domain.RuntimeKeyResolver;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.visual.PetVisualTransform;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetVisualHandleTest {
    private static final NamespacedKey KEY = RuntimeKeyResolver.representationKey(RuntimeRepresentationType.ITEM_DISPLAY);

    @Test void legacyHandleNamesAndIndexesServerComponents() {
        Entity root = entity(true);
        Entity child = entity(true);

        PetVisualHandle handle = PetVisualHandle.legacy(KEY, root, List.of(child));

        assertEquals(root, handle.primaryEntity().orElseThrow());
        assertEquals(List.of(root, child), handle.serverEntities());
        assertEquals("child-1", handle.componentForEntity(child.getUniqueId()).orElseThrow().id());
        assertTrue(handle.isValid());
    }

    @Test void validatesParentGraphAndSupportsEntitylessVirtualHandles() {
        PetVisualComponent virtualRoot = new PetVisualComponent("root", null, KEY,
                PetVisualTransform.IDENTITY, null);
        PetVisualHandle virtual = PetVisualHandle.builder("root", PetRenderBackend.VIRTUAL)
                .component(virtualRoot).build();
        assertTrue(virtual.isValid());
        assertTrue(virtual.serverEntities().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> PetVisualHandle.builder("root", PetRenderBackend.SERVER)
                .component(new PetVisualComponent("root", null, KEY, PetVisualTransform.IDENTITY, entity(true)))
                .component(new PetVisualComponent("wing", "missing", KEY, PetVisualTransform.IDENTITY, entity(true)))
                .build());
    }

    private static Entity entity(boolean valid) {
        Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(valid);
        when(entity.isDead()).thenReturn(false);
        return entity;
    }
}
