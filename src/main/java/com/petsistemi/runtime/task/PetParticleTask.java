package com.petsistemi.runtime.task;

import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class PetParticleTask implements Runnable {

    private final ActivePetRegistry activePetRegistry;
    private final PetRepository petRepository;

    public PetParticleTask(ActivePetRegistry activePetRegistry, PetRepository petRepository) {
        this.activePetRegistry = activePetRegistry;
        this.petRepository = petRepository;
    }

    @Override
    public void run() {
        for (ActivePet activePet : activePetRegistry.getAllActive()) {
            Entity entity = activePet.getSpawnedEntity();
            if (entity == null || !entity.isValid() || entity.isDead()) continue;

            // Guard: owner must be online and in the same world
            Player owner = Bukkit.getPlayer(activePet.getOwnerId());
            if (owner == null || !owner.isOnline()) continue;

            World world = entity.getWorld();
            if (!world.equals(owner.getWorld())) continue;

            String defId = activePet.getDefinitionId() != null ? activePet.getDefinitionId().toLowerCase() : "";

            Particle particle;
            try {
                particle = switch (defId) {
                    case "wolf" -> Particle.valueOf("VILLAGER_HAPPY");
                    case "cat" -> Particle.HEART;
                    case "allay" -> Particle.valueOf("SOUL_FIRE_FLAME");
                    default -> Particle.HEART;
                };
            } catch (Throwable t) {
                particle = Particle.HEART;
            }

            try {
                world.spawnParticle(particle, entity.getLocation().add(0, 0.6, 0), 2, 0.2, 0.2, 0.2, 0.02);
            } catch (Throwable ignored) {}
        }
    }
}
