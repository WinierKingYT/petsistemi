package com.petsistemi.runtime.task;

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

    public PetAbilityTask(ActivePetRegistry activePetRegistry) {
        this.activePetRegistry = activePetRegistry;
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
            int level = activePet.getLevel();

            applyBuffs(owner, defId, level);
        }
    }

    /** Amplifier grows every 5 levels: 1-5 → 0, 6-10 → 1, 11-15 → 2, 16+ → 3 (capped at potion-safe values). */
    static int buffAmplifier(int level) {
        return Math.max(0, Math.min(3, (level - 1) / 5));
    }

    private void applyBuffs(Player owner, String defId, int level) {
        int amp = buffAmplifier(level);
        switch (defId) {
            case "wolf" -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BUFF_DURATION, amp, true, false, true));
            }
            case "cat" -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, true));
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BUFF_DURATION, amp, true, false, true));
            }
            case "allay" -> {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, BUFF_DURATION, amp, true, false, true));
                owner.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, BUFF_DURATION, amp, true, false, true));
            }
            default -> {}
        }
    }
}
