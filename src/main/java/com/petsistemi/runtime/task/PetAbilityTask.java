package com.petsistemi.runtime.task;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;

public class PetAbilityTask implements Runnable {

    private final ActivePetRegistry activePetRegistry;
    private final PetRepository petRepository;
    private final PetDefinitionRegistry definitionRegistry;

    public PetAbilityTask(ActivePetRegistry activePetRegistry, PetRepository petRepository, PetDefinitionRegistry definitionRegistry) {
        this.activePetRegistry = activePetRegistry;
        this.petRepository = petRepository;
        this.definitionRegistry = definitionRegistry;
    }

    @Override
    public void run() {
        for (ActivePet activePet : activePetRegistry.getAllActive()) {
            Player owner = Bukkit.getPlayer(activePet.getOwnerId());
            if (owner == null || !owner.isOnline()) {
                continue;
            }

            Entity entity = activePet.getSpawnedEntity();
            if (entity == null || !entity.isValid() || !entity.getWorld().equals(owner.getWorld())) {
                continue;
            }

            if (entity.getLocation().distanceSquared(owner.getLocation()) > 15.0 * 15.0) {
                continue;
            }

            Optional<PetInstance> petOpt = petRepository.findById(activePet.getPetId());
            if (petOpt.isEmpty()) {
                continue;
            }

            String defId = petOpt.get().definitionId();
            applyBuffsForPet(owner, defId);
        }
    }

    private void applyBuffsForPet(Player owner, String defId) {
        switch (defId.toLowerCase()) {
            case "wolf" -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false, true));
            }
            case "cat" -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, true));
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false, true));
            }
            case "allay" -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, true));
                owner.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 0, true, false, true));
            }
            default -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false, true));
            }
        }
    }
}
