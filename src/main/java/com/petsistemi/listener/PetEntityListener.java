package com.petsistemi.listener;

import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetStorageState;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
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
    private final PetRepository repository;

    public PetEntityListener(ActivePetRegistry activeRegistry, PetRepository repository) {
        this.activeRegistry = activeRegistry;
        this.repository = repository;
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

            // Clean up registry
            activeRegistry.unregister(activePet.getOwnerId());
            repository.clearActivePet(activePet.getOwnerId());
            
            // Set state to AVAILABLE
            repository.findById(activePet.getPetId()).ifPresent(pet -> 
                repository.update(pet.withStorageState(PetStorageState.AVAILABLE))
            );
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        // If our pet is in this chunk, dismiss it to prevent being saved inside chunk file
        for (Entity entity : event.getChunk().getEntities()) {
            Optional<ActivePet> activeOpt = activeRegistry.getByEntity(entity.getUniqueId());
            if (activeOpt.isPresent()) {
                ActivePet active = activeOpt.get();
                entity.remove();
                activeRegistry.unregister(active.getOwnerId());
                repository.clearActivePet(active.getOwnerId());
                repository.findById(active.getPetId()).ifPresent(pet -> 
                    repository.update(pet.withStorageState(PetStorageState.AVAILABLE))
                );
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
