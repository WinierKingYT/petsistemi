package com.petsistemi.runtime.mount;

import com.petsistemi.api.mount.PetMountStatus;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetMountDefinition;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PetMountControllerTest {

    private ActivePetRegistry activeRegistry;
    private PetDefinitionRegistry definitions;
    private Player player;
    private Entity entity;
    private ActivePet active;
    private PetDefinition definition;

    @BeforeEach
    void setUp() {
        UUID ownerId = UUID.randomUUID();
        player = mock(Player.class);
        entity = mock(Entity.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(entity.isValid()).thenReturn(true);
        when(entity.isDead()).thenReturn(false);
        when(entity.hasGravity()).thenReturn(true);
        when(entity.addPassenger(player)).thenReturn(true);
        when(entity.removePassenger(player)).thenReturn(true);
        when(entity.getVelocity()).thenReturn(new Vector());
        active = new ActivePet(UUID.randomUUID(), ownerId, "wolf", 1,
                UUID.randomUUID(), entity, PetRuntimeState.ACTIVE);
        activeRegistry = new ActivePetRegistry();
        activeRegistry.register(active);
        definition = PetDefinition.builder("wolf", "Wolf")
                .mount(new PetMountDefinition(true, null, 1.25D, false)).build();
        definitions = mock(PetDefinitionRegistry.class);
        when(definitions.find("wolf")).thenReturn(Optional.of(definition));
    }

    @Test
    void mountsAndDismountsWhileRestoringOriginalGravity() {
        PetMountController controller = controller(p -> PetMountInput.NONE, true);

        assertEquals(PetMountStatus.MOUNTED, controller.toggleMount(player).status());
        assertTrue(controller.isMounted(player.getUniqueId()));
        when(player.getVehicle()).thenReturn(entity);
        assertEquals(PetMountStatus.DISMOUNTED, controller.toggleMount(player).status());

        assertFalse(controller.isMounted(player.getUniqueId()));
        verify(entity).removePassenger(player);
        verify(entity).setGravity(true);
    }

    @Test
    void globalSwitchAndDefinitionPermissionGateMounting() {
        assertEquals(PetMountStatus.DISABLED,
                controller(p -> PetMountInput.NONE, false).toggleMount(player).status());

        definition = definition.toBuilder()
                .mount(new PetMountDefinition(true, "pets.mount.wolf", 1.0D, false)).build();
        when(definitions.find("wolf")).thenReturn(Optional.of(definition));
        when(player.hasPermission("pets.mount.wolf")).thenReturn(false);
        assertEquals(PetMountStatus.NO_PERMISSION,
                controller(p -> PetMountInput.NONE, true).toggleMount(player).status());
    }

    @Test
    void forwardInputSteersGroundMountAndJumpIsEdgeTriggered() {
        PetMountController controller = controller(p -> new PetMountInput(0, 1, true), true);
        assertEquals(PetMountStatus.MOUNTED, controller.toggleMount(player).status());
        World world = mock(World.class);
        when(player.getVehicle()).thenReturn(entity);
        when(player.getWorld()).thenReturn(world);
        when(entity.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0, 0, 0));
        when(entity.isOnGround()).thenReturn(true);

        assertTrue(controller.tick(active, player));

        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(entity).setVelocity(velocity.capture());
        assertEquals(PetMountController.BASE_SPEED * 1.25D, velocity.getValue().getZ(), 0.0001D);
        assertEquals(PetMountController.JUMP_VELOCITY, velocity.getValue().getY(), 0.0001D);
    }

    @Test
    void flyingMountDisablesGravityAndUsesLookPitch() {
        definition = definition.toBuilder()
                .mount(new PetMountDefinition(true, null, 1.0D, true)).build();
        when(definitions.find("wolf")).thenReturn(Optional.of(definition));
        PetMountController controller = controller(p -> new PetMountInput(0, 1, false), true);
        World world = mock(World.class);

        assertEquals(PetMountStatus.MOUNTED, controller.toggleMount(player).status());
        when(player.getVehicle()).thenReturn(entity);
        when(player.getWorld()).thenReturn(world);
        when(entity.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0, 0, -30));
        assertTrue(controller.tick(active, player));

        verify(entity).setGravity(false);
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(entity).setVelocity(velocity.capture());
        assertTrue(velocity.getValue().getY() > 0.0D);
    }

    @Test
    void lostVehicleEndsSessionAndRestoresGravity() {
        PetMountController controller = controller(p -> PetMountInput.NONE, true);
        assertEquals(PetMountStatus.MOUNTED, controller.toggleMount(player).status());
        when(player.getVehicle()).thenReturn(null);

        assertFalse(controller.tick(active, player));
        assertFalse(controller.isMounted(player.getUniqueId()));
        verify(entity).setGravity(true);
    }

    private PetMountController controller(PetMountInputProvider provider, boolean enabled) {
        return new PetMountController(activeRegistry, definitions, () -> enabled, provider);
    }
}
