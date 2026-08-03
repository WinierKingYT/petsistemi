package com.petsistemi.runtime.task;

import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.Optional;

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
            if (entity == null || !entity.isValid()) {
                continue;
            }

            Optional<PetInstance> petOpt = petRepository.findById(activePet.getPetId());
            if (petOpt.isEmpty()) {
                continue;
            }

            World world = entity.getWorld();
            String defId = petOpt.get().definitionId();
            Particle particle;
            try {
                particle = switch (defId.toLowerCase()) {
                    case "wolf" -> Particle.valueOf("VILLAGER_HAPPY");
                    case "cat" -> Particle.HEART;
                    case "allay" -> Particle.valueOf("SOUL_FIRE_FLAME");
                    default -> Particle.valueOf("VILLAGER_HAPPY");
                };
            } catch (Throwable t) {
                particle = Particle.HEART;
            }

            world.spawnParticle(particle, entity.getLocation().add(0, 0.6, 0), 2, 0.2, 0.2, 0.2, 0.02);
        }
    }
}
