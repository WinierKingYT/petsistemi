package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetEvolutionDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class PetEvolutionControllerTest {

    private final UUID petId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private RecordingRepresentation representation;
    private ActivePet active;
    private PetEvolutionController controller;
    private Entity entity;

    @BeforeEach
    void setUp() {
        PetRepresentationDefinition visual = PetRepresentationDefinition.display(
                RuntimeRepresentationType.ITEM_DISPLAY, "BLAZE_POWDER", null, PetVector3.ONE);
        PetDefinition phoenix = PetDefinition.builder("phoenix", "Young Phoenix")
                .representation(visual)
                .evolutions(List.of(
                        new PetEvolutionDefinition(20, "phoenix", "Ancient Phoenix", new PetVector3(1.8, 1.8, 1.8)),
                        new PetEvolutionDefinition(40, "phoenix", "Eternal Phoenix", new PetVector3(2.2, 2.2, 2.2))))
                .build();
        representation = new RecordingRepresentation();
        PetRepresentationRegistry representations = new PetRepresentationRegistry();
        representations.register(RuntimeRepresentationType.ITEM_DISPLAY, representation);
        controller = new PetEvolutionController(registry(Map.of("phoenix", phoenix)), representations);
        entity = mock(Entity.class);
        PetInstance instance = new PetInstance(petId, ownerId, "phoenix", null, 19, 0,
                PetAvailabilityState.AVAILABLE, 1, 1);
        active = new ActivePet(petId, ownerId, "phoenix", 19, UUID.randomUUID(), entity, PetRuntimeState.ACTIVE);
        active.setPetInstance(instance);
        active.setRepresentationType(RuntimeRepresentationType.ITEM_DISPLAY);
    }

    @Test
    void highestQualifiedStageIsAppliedAndRevertedFromPersistedLevel() {
        controller.tick(active, entity);
        assertEquals("Young Phoenix", representation.last().displayName());

        active.setLevel(20);
        controller.tick(active, entity);
        assertEquals("Ancient Phoenix", representation.last().displayName());
        assertEquals(new PetVector3(1.8, 1.8, 1.8), representation.last().representation().scale());

        active.setLevel(45);
        controller.tick(active, entity);
        assertEquals("Eternal Phoenix", representation.last().displayName());

        active.setLevel(10);
        controller.tick(active, entity);
        assertEquals("Young Phoenix", representation.last().displayName());
    }

    @Test
    void unchangedStageDoesNotRenderEveryTickAndCleanupDropsDerivedState() {
        active.setLevel(20);
        controller.tick(active, entity);
        controller.tick(active, entity);
        assertEquals(1, representation.definitions.size());
        controller.cleanup(petId);
        assertEquals("Young Phoenix", controller.activeDefinition(active).displayName());
    }

    private static PetDefinitionRegistry registry(Map<String, PetDefinition> definitions) {
        return new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.ofNullable(definitions.get(id)); }
            @Override public Collection<PetDefinition> getAll() { return definitions.values(); }
            @Override public void reload() {}
        };
    }

    private static final class RecordingRepresentation implements PetRepresentationController {
        private final List<PetDefinition> definitions = new ArrayList<>();
        PetDefinition last() { return definitions.get(definitions.size() - 1); }
        @Override public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) { return null; }
        @Override public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) { definitions.add(definition); }
        @Override public void remove(Entity primaryEntity) {}
        @Override public boolean isValid(Entity primaryEntity) { return true; }
    }
}
