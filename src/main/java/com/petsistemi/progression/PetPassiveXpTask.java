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

    /** Production constructor using atomic config snapshot. */
    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
        this.configSnapshot = configSnapshot;
        this.fallbackXp = 10L;
    }

    /** Backward-compatible / test constructor using Bukkit FileConfiguration. */
    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService, FileConfiguration config) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
        this.configSnapshot = null;
        this.fallbackXp = config != null ? config.getLong("progression.passive-xp-per-minute", 10L) : 10L;
    }

    /** Backward-compatible constructor with default fallback XP. */
    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService) {
        this(activePetRegistry, experienceService, (FileConfiguration) null);
    }

    @Override
    public void run() {
        RuntimeConfigurationSnapshot snapshot = (configSnapshot != null) ? configSnapshot.get() : null;
        long xp = (snapshot != null && snapshot.configuration() != null && snapshot.configuration().progression() != null)
                ? snapshot.configuration().progression().passiveXpPerMinute()
                : fallbackXp;

        if (xp <= 0) return;

        try {
            for (ActivePet activePet : activePetRegistry.getAllActive()) {
                if (Bukkit.getServer() == null) break;
                Player owner = Bukkit.getPlayer(activePet.getOwnerId());
                if (owner != null && owner.isOnline()) {
                    experienceService.addExperience(activePet.getPetId(), xp, ExperienceSource.ONLINE_TIME);
                }
            }
        } catch (Exception ignored) {}
    }
}
