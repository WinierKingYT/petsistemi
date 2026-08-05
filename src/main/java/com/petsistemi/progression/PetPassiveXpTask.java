package com.petsistemi.progression;

import com.petsistemi.api.AsyncPetExperienceService;
import com.petsistemi.api.PetExperienceService;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PetPassiveXpTask implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(PetPassiveXpTask.class.getName());

    private final ActivePetRegistry activePetRegistry;
    private final AsyncPetExperienceService asyncExperienceService;
    private final Set<UUID> inFlightXpPets = ConcurrentHashMap.newKeySet();

    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService) {
        this.activePetRegistry = Objects.requireNonNull(activePetRegistry, "activePetRegistry null olamaz.");
        this.asyncExperienceService = experienceService instanceof AsyncPetExperienceService async ? async : null;
    }

    public PetPassiveXpTask(ActivePetRegistry activePetRegistry, PetExperienceService experienceService, java.util.concurrent.atomic.AtomicReference<com.petsistemi.config.RuntimeConfigurationSnapshot> configSnapshot) {
        this(activePetRegistry, experienceService);
    }

    @Override
    public void run() {
        if (asyncExperienceService == null) return;

        Collection<ActivePet> activePets = activePetRegistry.getAllActive();
        for (ActivePet active : activePets) {
            UUID ownerId = active.getOwnerId();

            if (Bukkit.getServer() != null) {
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null && !owner.isOnline()) {
                    continue;
                }
            }

            UUID petId = active.getPetId();
            if (!inFlightXpPets.add(petId)) {
                continue;
            }

            try {
                CompletableFuture<?> future = asyncExperienceService.addExperienceAsync(petId, 1L, ExperienceSource.ONLINE_TIME);
                if (future == null) {
                    inFlightXpPets.remove(petId);
                    LOGGER.warning("Pasif XP görevi null future döndürdü: " + petId);
                    continue;
                }

                future.whenComplete((res, ex) -> {
                    inFlightXpPets.remove(petId);
                    if (ex != null) {
                        LOGGER.log(Level.WARNING, "Pasif XP verilirken hata oluştu [PetId: " + petId + "]: " + ex.getMessage(), ex);
                    }
                });
            } catch (Throwable t) {
                inFlightXpPets.remove(petId);
                LOGGER.log(Level.WARNING, "Pasif XP senkron istisnai hata [PetId: " + petId + "]: " + t.getMessage(), t);
            }
        }
    }
}
