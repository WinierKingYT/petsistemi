package com.petsistemi.listener;

import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import com.petsistemi.runtime.PetRuntimeCoordinator.PetRemovalCause;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Optional;

public class PetEntityListener implements Listener {

    private final ActivePetRegistry activeRegistry;
    private final PetRuntimeCoordinator coordinator;

    public PetEntityListener(ActivePetRegistry activeRegistry, PetRuntimeCoordinator coordinator) {
        this.activeRegistry = activeRegistry;
        this.coordinator = coordinator;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        Optional<ActivePet> activeOpt = activeRegistry.getByEntity(entity.getUniqueId());
        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            // Clear drops and XP
            event.getDrops().clear();
            event.setDroppedExp(0);

            // Delegate centralized loss handling to coordinator
            coordinator.handleRemoval(activePet.getOwnerId(), PetRemovalCause.ENTITY_DEATH);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        // If our pet is in this chunk, handle unload via coordinator to prevent chunk save pollution
        for (Entity entity : event.getChunk().getEntities()) {
            Optional<ActivePet> activeOpt = activeRegistry.getByEntity(entity.getUniqueId());
            if (activeOpt.isPresent()) {
                ActivePet active = activeOpt.get();
                coordinator.handleRemoval(active.getOwnerId(), PetRemovalCause.CHUNK_UNLOAD);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (activeRegistry.getByEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (activeRegistry.getByEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && activeRegistry.getByEntity(event.getTarget().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
