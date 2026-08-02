package com.petsistemi.application;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.event.PetGainExperienceEvent;
import com.petsistemi.api.event.PetLevelUpEvent;
import com.petsistemi.api.result.ExperienceResult;
import com.petsistemi.api.result.LevelResult;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetEntityController;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

public class DefaultPetExperienceService implements PetExperienceService {

    private final JavaPlugin plugin;
    private final PetRepository repository;
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activePetRegistry;
    private final PetEntityController entityController;

    public DefaultPetExperienceService(JavaPlugin plugin, PetRepository repository,
                                      PetDefinitionRegistry definitionRegistry,
                                      ActivePetRegistry activePetRegistry,
                                      PetEntityController entityController) {
        this.plugin = plugin;
        this.repository = repository;
        this.definitionRegistry = definitionRegistry;
        this.activePetRegistry = activePetRegistry;
        this.entityController = entityController;
    }

    @Override
    public ExperienceResult addExperience(UUID petId, long amount, ExperienceSource source) {
        if (amount < 0) {
            return new ExperienceResult(false, "Deneyim miktarı negatif olamaz.", 0, false);
        }

        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new ExperienceResult(false, "Pet bulunamadı.", 0, false);
        }

        PetInstance pet = petOpt.get();
        PetDefinition definition = definitionRegistry.find(pet.definitionId()).orElse(null);
        int maxLevel = definition != null ? definition.maxLevel() : 100;

        // Trigger Event
        PetGainExperienceEvent event = new PetGainExperienceEvent(pet, amount, source);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return new ExperienceResult(false, "Deneyim kazanma işlemi iptal edildi.", pet.experience(), false);
        }

        long actualAmount = event.getAmount();
        long newXp = pet.experience() + actualAmount;
        int newLevel = calculateLevelFromXp(newXp);

        if (newLevel > maxLevel) {
            newLevel = maxLevel;
            newXp = requiredExperienceForLevel(maxLevel);
        }

        boolean leveledUp = newLevel > pet.level();
        int oldLevel = pet.level();

        PetInstance updatedPet = pet.withLevelAndExperience(newLevel, newXp);
        repository.update(updatedPet);

        // If active, update nameplate
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(pet.ownerId());
        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            if (activePet.getPetId().equals(pet.petId()) && definition != null) {
                entityController.updateName(activePet.getSpawnedEntity(), updatedPet, definition);
            }
        }

        if (leveledUp) {
            PetLevelUpEvent levelUpEvent = new PetLevelUpEvent(updatedPet, oldLevel, newLevel);
            Bukkit.getPluginManager().callEvent(levelUpEvent);
        }

        return new ExperienceResult(true, "Deneyim başarıyla eklendi.", newXp, leveledUp);
    }

    @Override
    public ExperienceResult removeExperience(UUID petId, long amount) {
        if (amount < 0) {
            return new ExperienceResult(false, "Deneyim miktarı negatif olamaz.", 0, false);
        }

        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new ExperienceResult(false, "Pet bulunamadı.", 0, false);
        }

        PetInstance pet = petOpt.get();
        long newXp = Math.max(0, pet.experience() - amount);
        int newLevel = calculateLevelFromXp(newXp);

        PetInstance updatedPet = pet.withLevelAndExperience(newLevel, newXp);
        repository.update(updatedPet);

        // If active, update nameplate
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(pet.ownerId());
        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            if (activePet.getPetId().equals(pet.petId())) {
                definitionRegistry.find(pet.definitionId()).ifPresent(def -> 
                    entityController.updateName(activePet.getSpawnedEntity(), updatedPet, def)
                );
            }
        }

        return new ExperienceResult(true, "Deneyim başarıyla silindi.", newXp, false);
    }

    @Override
    public LevelResult setLevel(UUID petId, int level) {
        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new LevelResult(false, "Pet bulunamadı.", 0);
        }

        PetInstance pet = petOpt.get();
        PetDefinition definition = definitionRegistry.find(pet.definitionId()).orElse(null);
        int maxLevel = definition != null ? definition.maxLevel() : 100;

        if (level < 1 || level > maxLevel) {
            return new LevelResult(false, "Geçersiz seviye değeri (1 ile " + maxLevel + " arasında olmalı).", pet.level());
        }

        long newXp = requiredExperienceForLevel(level);
        PetInstance updatedPet = pet.withLevelAndExperience(level, newXp);
        repository.update(updatedPet);

        // If active, update nameplate
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(pet.ownerId());
        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            if (activePet.getPetId().equals(pet.petId()) && definition != null) {
                entityController.updateName(activePet.getSpawnedEntity(), updatedPet, definition);
            }
        }

        return new LevelResult(true, "Seviye başarıyla ayarlandı.", level);
    }

    @Override
    public long requiredExperienceForLevel(int level) {
        if (level <= 1) return 0;
        return 100L * (level - 1) * (level - 1);
    }

    public int calculateLevelFromXp(long totalXp) {
        if (totalXp <= 0) return 1;
        return (int) (Math.sqrt(totalXp / 100.0) + 1);
    }
}
