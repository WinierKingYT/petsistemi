package com.petsistemi.listener;

import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
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
        Optional<ActivePet> activeOpt = activeRegistry.getByAnyEntity(entity.getUniqueId());
        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            event.getDrops().clear();
            event.setDroppedExp(0);
            coordinator.despawnRuntime(activePet.getOwnerId());
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            Optional<ActivePet> activeOpt = activeRegistry.getByAnyEntity(entity.getUniqueId());
            if (activeOpt.isPresent()) {
                ActivePet active = activeOpt.get();
                coordinator.despawnRuntime(active.getOwnerId());
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (activeRegistry.getByAnyEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (activeRegistry.getByAnyEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && activeRegistry.getByAnyEntity(event.getTarget().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
