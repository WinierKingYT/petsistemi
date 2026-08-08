package com.petsistemi.runtime;

import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A movement controller that throws must not stop the pets queued behind it —
 * without isolation, one broken pet silently freezes every other player's pet.
 */
class PetTickIsolationTest {

    private ActivePetRegistry registry;
    private PetMovementRegistry movementRegistry;
    private final List<Player> owners = new ArrayList<>();

    /** Records every pet it ticks; optionally blows up for one specific pet. */
    private static final class RecordingMovement implements PetMovementController {
        final List<UUID> ticked = new ArrayList<>();
        UUID explodeOn;

        @Override public void initialize(ActivePet pet, Entity entity, Player owner) {}

        @Override
        public void tick(ActivePet pet, Entity entity, Player owner) {
            if (pet.getPetId().equals(explodeOn)) {
                throw new IllegalStateException("bilerek patlatıldı");
            }
            ticked.add(pet.getPetId());
        }

        @Override public void remove(ActivePet pet, Entity entity) {}
    }

    private RecordingMovement movement;

    @BeforeEach
    void setUp() {
        registry = new ActivePetRegistry();
        movement = new RecordingMovement();
        movementRegistry = new PetMovementRegistry();
        movementRegistry.register(PetMovementType.GROUND_FOLLOW, movement);
    }

    /** Registers a spawned pet with a mocked online owner and a valid entity. */
    private ActivePet addPet() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        Entity entity = mock(Entity.class);
        when(entity.isValid()).thenReturn(true);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());

        Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.isOnline()).thenReturn(true);
        // The tick loop skips owners whose chunk is unloaded, so give them a loaded one.
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(world.isChunkLoaded(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(true);
        when(owner.getLocation()).thenReturn(new org.bukkit.Location(world, 0.0, 64.0, 0.0));
        owners.add(owner);

        ActivePet active = new ActivePet(petId, ownerId, "wolf", 1,
                entity.getUniqueId(), entity, PetRuntimeState.ACTIVE);
        active.setRepresentationType(RuntimeRepresentationType.ENTITY);
        active.setMovementType(PetMovementType.GROUND_FOLLOW);
        registry.register(active);
        return active;
    }

    private PetRuntimeCoordinator coordinator() {
        return new PetRuntimeCoordinator(null, null, registry, null, null, null, movementRegistry);
    }

    private PetRuntimeCoordinator.OwnerLookup lookup() {
        return ownerId -> owners.stream()
                .filter(p -> p.getUniqueId().equals(ownerId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Buffs used to run in their own sweep outside the isolation, so a single failing
     * buff aborted the whole tick — every pet's movement included.
     */
    @Test
    void throwingBuffControllerDoesNotStopMovementForOtherPets() {
        ActivePet first = addPet();
        ActivePet boom = addPet();
        ActivePet last = addPet();

        com.petsistemi.domain.PetDefinition definition = new com.petsistemi.domain.PetDefinition(
                "wolf", "Kurt", List.of(), "WOLF", false, false, true, false, true,
                true, 100, false, List.of("{pet_name}"));
        com.petsistemi.definition.PetDefinitionRegistry definitions =
                new com.petsistemi.definition.PetDefinitionRegistry() {
                    @Override public java.util.Optional<com.petsistemi.domain.PetDefinition> find(String id) {
                        return java.util.Optional.of(definition);
                    }
                    @Override public java.util.Collection<com.petsistemi.domain.PetDefinition> getAll() {
                        return List.of(definition);
                    }
                    @Override public void reload() {}
                };

        PetBuffController explodingBuffs = new PetBuffController() {
            @Override
            public void apply(ActivePet pet, Player owner, com.petsistemi.domain.PetDefinition def) {
                if (pet.getPetId().equals(boom.getPetId())) {
                    throw new IllegalStateException("buff bilerek patlatıldı");
                }
            }
        };

        PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(
                null, definitions, registry, null, null, null, movementRegistry);
        coordinator.setBuffController(explodingBuffs);

        coordinator.tickEach(List.of(first, boom, last), lookup());

        assertTrue(movement.ticked.contains(first.getPetId()));
        assertTrue(movement.ticked.contains(last.getPetId()), "buff hatası sonraki petleri durdurmamalı");
        assertEquals(2, movement.ticked.size(), "yalnızca buff'ı patlayan pet atlanmalı");
    }

    @Test
    void throwingPetDoesNotStopTheOthers() {
        ActivePet first = addPet();
        ActivePet boom = addPet();
        ActivePet last = addPet();
        movement.explodeOn = boom.getPetId();

        // Order the failing pet in the middle so a broken loop would drop `last`.
        List<ActivePet> pets = List.of(first, boom, last);

        coordinator().tickEach(pets, lookup());

        assertTrue(movement.ticked.contains(first.getPetId()), "patlayandan önceki pet tick almalı");
        assertTrue(movement.ticked.contains(last.getPetId()), "patlayandan sonraki pet de tick almalı");
        assertEquals(2, movement.ticked.size(), "yalnızca patlayan pet atlanmalı");
    }

    @Test
    void allPetsTickWhenNothingThrows() {
        List<ActivePet> pets = List.of(addPet(), addPet(), addPet());

        coordinator().tickEach(pets, lookup());

        assertEquals(3, movement.ticked.size());
    }

    @Test
    void repeatedFailuresKeepSkippingWithoutAffectingHealthyPets() {
        ActivePet healthy = addPet();
        ActivePet boom = addPet();
        movement.explodeOn = boom.getPetId();
        List<ActivePet> pets = List.of(boom, healthy);

        PetRuntimeCoordinator coordinator = coordinator();
        for (int i = 0; i < 5; i++) {
            coordinator.tickEach(pets, lookup());
        }

        assertEquals(5, movement.ticked.size(), "sağlıklı pet her turda tick almalı");
        assertTrue(movement.ticked.stream().allMatch(id -> id.equals(healthy.getPetId())));
    }

    @Test
    void offlineOwnersAreSkippedWithoutTouchingMovement() {
        ActivePet offline = addPet();
        AtomicInteger lookups = new AtomicInteger();

        coordinator().tickEach(List.of(offline), ownerId -> {
            lookups.incrementAndGet();
            return null;
        });

        assertEquals(1, lookups.get());
        assertTrue(movement.ticked.isEmpty(), "çevrimdışı sahibin peti tick almamalı");
    }
}
