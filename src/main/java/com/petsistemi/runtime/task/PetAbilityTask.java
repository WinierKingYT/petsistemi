package com.petsistemi.runtime.task;

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

    private static final int BUFF_DURATION = 70; // slightly longer than period (40t) to avoid gaps
    private static final double MAX_DISTANCE_SQUARED = 15.0 * 15.0;

    private final ActivePetRegistry activePetRegistry;
    private final PetRepository petRepository;

    public PetAbilityTask(ActivePetRegistry activePetRegistry, PetRepository petRepository, Object ignored) {
        this.activePetRegistry = activePetRegistry;
        this.petRepository = petRepository;
    }

    @Override
    public void run() {
        for (ActivePet activePet : activePetRegistry.getAllActive()) {
            Player owner = Bukkit.getPlayer(activePet.getOwnerId());
            if (owner == null || !owner.isOnline()) continue;

            Entity entity = activePet.getSpawnedEntity();
            if (entity == null || !entity.isValid() || !entity.getWorld().equals(owner.getWorld())) continue;
            if (entity.getLocation().distanceSquared(owner.getLocation()) > MAX_DISTANCE_SQUARED) continue;

            String defId = activePet.getDefinitionId() != null ? activePet.getDefinitionId().toLowerCase() : "";

            applyBuffs(owner, defId);
        }
    }

    private void applyBuffs(Player owner, String defId) {
        switch (defId) {
            case "wolf" -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BUFF_DURATION, 0, true, false, true));
            }
            case "cat" -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, true));
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BUFF_DURATION, 0, true, false, true));
            }
            case "allay" -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, BUFF_DURATION, 0, true, false, true));
                owner.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, BUFF_DURATION, 0, true, false, true));
            }
            default -> {}
        }
    }
}
