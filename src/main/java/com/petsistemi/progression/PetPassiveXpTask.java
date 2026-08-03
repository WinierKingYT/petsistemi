package com.petsistemi.progression;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PetPassiveXpTask implements Runnable {

    private final ActivePetRegistry activePetRegistry;
    private final PetExperienceService experienceService;

    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
    }

    @Override
    public void run() {
        for (ActivePet activePet : activePetRegistry.getAllActive()) {
            Player owner = Bukkit.getPlayer(activePet.getOwnerId());
            if (owner != null && owner.isOnline()) {
                experienceService.addExperience(activePet.getPetId(), 10L, ExperienceSource.ONLINE_TIME);
            }
        }
    }
}
