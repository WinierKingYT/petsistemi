package com.petsistemi.progression;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class PetPassiveXpTask implements Runnable {

    private final ActivePetRegistry activePetRegistry;
    private final PetExperienceService experienceService;
    private final long xpPerTick;

    /** Config-aware constructor — reads progression.passive-xp-per-minute from config. */
    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService, FileConfiguration config) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
        this.xpPerTick = config != null ? config.getLong("progression.passive-xp-per-minute", 10L) : 10L;
    }

    /** Backward-compatible constructor with hardcoded 10 XP per tick (1 per minute cycle). */
    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService) {
        this(activePetRegistry, experienceService, null);
    }

    @Override
    public void run() {
        for (ActivePet activePet : activePetRegistry.getAllActive()) {
            Player owner = Bukkit.getPlayer(activePet.getOwnerId());
            if (owner != null && owner.isOnline()) {
                experienceService.addExperience(activePet.getPetId(), xpPerTick, ExperienceSource.ONLINE_TIME);
            }
        }
    }
}
