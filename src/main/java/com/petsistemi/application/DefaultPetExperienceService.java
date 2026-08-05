package com.petsistemi.application;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetGainExperienceEvent;
import com.petsistemi.api.event.PetLevelUpEvent;
import com.petsistemi.api.result.ExperienceResult;
import com.petsistemi.api.result.LevelResult;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.progression.ExperienceCurve;
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

    private final ExperienceCurve experienceCurve;
    private final com.petsistemi.persistence.DatabaseExecutor dbExecutor;

    public DefaultPetExperienceService(JavaPlugin plugin, PetRepository repository,
                                       PetDefinitionRegistry definitionRegistry,
                                       ActivePetRegistry activePetRegistry,
                                       PetEntityController entityController) {
        this(plugin, repository, definitionRegistry, activePetRegistry, entityController, new com.petsistemi.progression.LinearExperienceCurve(100), null);
    }

    public DefaultPetExperienceService(JavaPlugin plugin, PetRepository repository,
                                      PetDefinitionRegistry definitionRegistry,
                                      ActivePetRegistry activePetRegistry,
                                      PetEntityController entityController,
                                      ExperienceCurve experienceCurve) {
        this(plugin, repository, definitionRegistry, activePetRegistry, entityController, experienceCurve, null);
    }

    public DefaultPetExperienceService(JavaPlugin plugin, PetRepository repository,
                                      PetDefinitionRegistry definitionRegistry,
                                      ActivePetRegistry activePetRegistry,
                                      PetEntityController entityController,
                                      ExperienceCurve experienceCurve,
                                      com.petsistemi.persistence.DatabaseExecutor dbExecutor) {
        this.plugin = plugin;
        this.repository = repository;
        this.definitionRegistry = definitionRegistry;
        this.activePetRegistry = activePetRegistry;
        this.entityController = entityController;
        this.experienceCurve = experienceCurve != null ? experienceCurve : new com.petsistemi.progression.LinearExperienceCurve(100);
        this.dbExecutor = dbExecutor;
    }

    @Override
    public ExperienceResult addExperience(UUID petId, long amount, ExperienceSource source) {
        if (!plugin.getConfig().getBoolean("progression.enabled", true)) {
            return new ExperienceResult(false, "Sistem genelinde tecrübe sistemi devre dışı.", 0, false);
        }

        if (amount <= 0) {
            return new ExperienceResult(false, "Deneyim miktarı pozitif olmalıdır.", 0, false);
        }

        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new ExperienceResult(false, "Pet bulunamadı.", 0, false);
        }

        PetInstance pet = petOpt.get();
        PetDefinition definition = definitionRegistry.find(pet.definitionId()).orElse(null);
        if (definition != null && !definition.progressionEnabled()) {
            return new ExperienceResult(false, "Bu pet türü için gelişim sistemi kapalı.", pet.experience(), false);
        }

        int maxLevel = definition != null ? definition.maxLevel() : plugin.getConfig().getInt("progression.maximum-level", 100);

        PetSnapshot snapshot = mapToSnapshot(pet);

        // Trigger Event
        PetGainExperienceEvent event = new PetGainExperienceEvent(snapshot, amount, source);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return new ExperienceResult(false, "Deneyim kazanma işlemi iptal edildi.", pet.experience(), false);
        }

        long actualAmount = event.getAmount();
        if (actualAmount <= 0) {
            return new ExperienceResult(false, "Etkinlik sonrası geçerli tecrübe miktarı sıfır veya negatif kaldı.", pet.experience(), false);
        }

        long newXp;
        try {
            newXp = Math.addExact(pet.experience(), actualAmount);
        } catch (ArithmeticException e) {
            newXp = Long.MAX_VALUE;
        }

        int newLevel = calculateLevelFromXp(newXp);

        if (newLevel > maxLevel) {
            newLevel = maxLevel;
            newXp = requiredExperienceForLevel(maxLevel);
        }

        boolean leveledUp = newLevel > pet.level();
        int oldLevel = pet.level();

        PetInstance updatedPet = pet.withLevelAndExperience(newLevel, newXp);
        try {
            repository.update(updatedPet);
        } catch (Exception e) {
            return new ExperienceResult(false, "Deneyim veritabanına kaydedilemedi: " + e.getMessage(), pet.experience(), false);
        }

        // If active, update nameplate
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(pet.ownerId());
        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            if (activePet.getPetId().equals(pet.petId()) && definition != null) {
                entityController.updateName(activePet.getSpawnedEntity(), updatedPet, definition);
            }
        }

        if (leveledUp) {
            PetLevelUpEvent levelUpEvent = new PetLevelUpEvent(mapToSnapshot(updatedPet), oldLevel, newLevel);
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
        try {
            repository.update(updatedPet);
        } catch (Exception e) {
            return new ExperienceResult(false, "Deneyim güncellenemedi: " + e.getMessage(), pet.experience(), false);
        }

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
    public ExperienceResult setExperience(UUID petId, long amount, ExperienceSource source) {
        if (!plugin.getConfig().getBoolean("progression.enabled", true)) {
            return new ExperienceResult(false, "Sistem genelinde tecrübe sistemi devre dışı.", 0, false);
        }

        if (amount < 0) {
            return new ExperienceResult(false, "Deneyim miktarı negatif olamaz.", 0, false);
        }

        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new ExperienceResult(false, "Pet bulunamadı.", 0, false);
        }

        PetInstance pet = petOpt.get();
        PetDefinition definition = definitionRegistry.find(pet.definitionId()).orElse(null);
        if (definition != null && !definition.progressionEnabled() && source != ExperienceSource.ADMIN) {
            return new ExperienceResult(false, "Bu pet türü için gelişim sistemi kapalı.", pet.experience(), false);
        }

        int maxLevel = definition != null ? definition.maxLevel() : plugin.getConfig().getInt("progression.maximum-level", 100);

        long newXp = amount;
        int newLevel = calculateLevelFromXp(newXp);

        if (newLevel > maxLevel) {
            newLevel = maxLevel;
            newXp = requiredExperienceForLevel(maxLevel);
        }

        boolean leveledUp = newLevel > pet.level();
        int oldLevel = pet.level();

        PetInstance updatedPet = pet.withLevelAndExperience(newLevel, newXp);
        try {
            repository.update(updatedPet);
        } catch (Exception e) {
            return new ExperienceResult(false, "Deneyim veritabanında güncellenemedi: " + e.getMessage(), pet.experience(), false);
        }

        // If active, update nameplate
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(pet.ownerId());
        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            if (activePet.getPetId().equals(pet.petId()) && definition != null) {
                entityController.updateName(activePet.getSpawnedEntity(), updatedPet, definition);
            }
        }

        if (leveledUp) {
            PetLevelUpEvent levelUpEvent = new PetLevelUpEvent(mapToSnapshot(updatedPet), oldLevel, newLevel);
            Bukkit.getPluginManager().callEvent(levelUpEvent);
        }

        return new ExperienceResult(true, "Deneyim başarıyla ayarlandı.", newXp, leveledUp);
    }

    @Override
    public LevelResult setLevel(UUID petId, int level) {
        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new LevelResult(false, "Pet bulunamadı.", 0);
        }

        PetInstance pet = petOpt.get();
        PetDefinition definition = definitionRegistry.find(pet.definitionId()).orElse(null);
        int maxLevel = definition != null ? definition.maxLevel() : plugin.getConfig().getInt("progression.maximum-level", 100);

        if (level < 1 || level > maxLevel) {
            return new LevelResult(false, "Geçersiz seviye değeri (1 ile " + maxLevel + " arasında olmalı).", pet.level());
        }

        long newXp = requiredExperienceForLevel(level);
        PetInstance updatedPet = pet.withLevelAndExperience(level, newXp);
        try {
            repository.update(updatedPet);
        } catch (Exception e) {
            return new LevelResult(false, "Seviye veritabanına kaydedilemedi: " + e.getMessage(), pet.level());
        }

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
        return experienceCurve.getRequiredExperience(level);
    }

    public int calculateLevelFromXp(long totalXp) {
        return experienceCurve.getLevelForExperience(totalXp);
    }

    private PetSnapshot mapToSnapshot(PetInstance p) {
        boolean selected = repository.findActiveByOwner(p.ownerId())
                .map(s -> s.petId().equals(p.petId()))
                .orElse(false);
        boolean spawned = activePetRegistry.getByOwner(p.ownerId())
                .map(a -> a.getPetId().equals(p.petId()))
                .orElse(false);

        return new PetSnapshot(
                p.petId(),
                p.ownerId(),
                p.definitionId(),
                p.customName(),
                p.level(),
                p.experience(),
                p.availabilityState(),
                selected,
                spawned
        );
    }
}
