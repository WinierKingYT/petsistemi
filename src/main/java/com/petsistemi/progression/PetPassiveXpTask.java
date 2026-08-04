package com.petsistemi.progression;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicReference;

public class PetPassiveXpTask implements Runnable {

    private final ActivePetRegistry activePetRegistry;
    private final PetExperienceService experienceService;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final long fallbackXp;

    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
        this.configSnapshot = configSnapshot;
        this.fallbackXp = 10L;
    }

    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService, FileConfiguration config) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
        this.configSnapshot = null;
        this.fallbackXp = config != null ? config.getLong("progression.passive-xp-per-minute", 10L) : 10L;
    }

    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService) {
        this(activePetRegistry, experienceService, (FileConfiguration) null);
    }

    @Override
    public void run() {
        long xp = fallbackXp;
        for (ActivePet activePet : activePetRegistry.getAllActive()) {
            Player owner = Bukkit.getPlayer(activePet.getOwnerId());
            if (owner != null && owner.isOnline()) {
                experienceService.addExperience(activePet.getPetId(), xp, ExperienceSource.ONLINE_TIME);
            }
        }
    }
}
