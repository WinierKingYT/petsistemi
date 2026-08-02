package com.petsistemi.application;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.*;
import com.petsistemi.api.result.*;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetStorageState;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetEntityController;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DefaultPetService implements PetService {

    private final JavaPlugin plugin;
    private final PetRepository repository;
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activePetRegistry;
    private final PetEntityController entityController;
    private final PetRuntimeCoordinator coordinator;

    public DefaultPetService(JavaPlugin plugin, PetRepository repository,
                             PetDefinitionRegistry definitionRegistry,
                             ActivePetRegistry activePetRegistry,
                             PetEntityController entityController,
                             PetRuntimeCoordinator coordinator) {
        this.plugin = plugin;
        this.repository = repository;
        this.definitionRegistry = definitionRegistry;
        this.activePetRegistry = activePetRegistry;
        this.entityController = entityController;
        this.coordinator = coordinator;
    }

    @Override
    public Optional<PetSnapshot> findPet(UUID petId) {
        return repository.findById(petId).map(this::mapToSnapshot);
    }

    @Override
    public Collection<PetSnapshot> getOwnedPets(UUID ownerId) {
        return repository.findByOwner(ownerId).stream()
                .map(this::mapToSnapshot)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PetSnapshot> getSelectedPet(UUID ownerId) {
        return repository.findActiveByOwner(ownerId).map(this::mapToSnapshot);
    }

    @Override
    public Optional<PetSnapshot> getSpawnedPet(UUID ownerId) {
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(ownerId);
        if (activeOpt.isPresent()) {
            return repository.findById(activeOpt.get().getPetId()).map(this::mapToSnapshot);
        }
        return Optional.empty();
    }

    @Override
    @Deprecated
    public Optional<PetSnapshot> getActivePet(UUID ownerId) {
        return getSpawnedPet(ownerId);
    }

    @Override
    public PetGiveResult givePet(UUID ownerId, String definitionId) {
        Optional<PetDefinition> defOpt = definitionRegistry.find(definitionId);
        if (defOpt.isEmpty()) {
            return new PetGiveResult(false, "Geçersiz pet türü: " + definitionId, null);
        }

        int maxPets = plugin.getConfig().getInt("limits.maximum-owned-pets", 20);
        List<PetInstance> current = repository.findByOwner(ownerId);
        if (current.size() >= maxPets) {
            return new PetGiveResult(false, "Maksimum pet sahiplenme limitine ulaştınız (" + maxPets + ").", null);
        }

        PetInstance pet = new PetInstance(
                UUID.randomUUID(),
                ownerId,
                definitionId.toLowerCase(),
                null,
                1,
                0,
                PetStorageState.AVAILABLE,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        try {
            repository.insert(pet);
        } catch (Exception e) {
            return new PetGiveResult(false, "Pet veritabanına kaydedilirken hata oluştu: " + e.getMessage(), null);
        }

        PetSnapshot snapshot = mapToSnapshot(pet);

        PetGiveEvent event = new PetGiveEvent(ownerId, snapshot);
        Bukkit.getPluginManager().callEvent(event);

        return new PetGiveResult(true, "Pet başarıyla verildi.", snapshot);
    }

    @Override
    public PetSummonResult summon(Player owner, UUID petId) {
        if (!owner.isOnline()) {
            return new PetSummonResult(false, "Oyuncu aktif değil.");
        }

        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new PetSummonResult(false, "Pet bulunamadı.");
        }

        PetInstance pet = petOpt.get();
        if (!pet.ownerId().equals(owner.getUniqueId())) {
            return new PetSummonResult(false, "Bu pet size ait değil.");
        }

        if (pet.storageState() == PetStorageState.DISABLED) {
            return new PetSummonResult(false, "Bu pet geçici olarak devre dışı bırakılmış.");
        }

        PetSnapshot snapshot = mapToSnapshot(pet);

        // PreSummon Event
        PetPreSummonEvent preEvent = new PetPreSummonEvent(owner, snapshot);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            return new PetSummonResult(false, "Çağırma işlemi başka bir eklenti tarafından engellendi.");
        }

        Optional<PetDefinition> defOpt = definitionRegistry.find(pet.definitionId());
        if (defOpt.isEmpty()) {
            repository.update(pet.withStorageState(PetStorageState.DISABLED));
            return new PetSummonResult(false, "Pet tanımı bulunamadı, pet devre dışı bırakıldı.");
        }

        PetDefinition definition = defOpt.get();

        // Atomic spawn & register via coordinator with rollback protection
        try {
            Entity spawned = coordinator.spawnAndRegister(owner, pet, definition);

            PetSummonEvent summonEvent = new PetSummonEvent(owner, snapshot, spawned);
            Bukkit.getPluginManager().callEvent(summonEvent);

            return new PetSummonResult(true, "Pet başarıyla çağırıldı.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Pet çağırılırken hata oluştu!", e);
            return new PetSummonResult(false, "Pet çağırılırken hata oluştu: " + e.getMessage());
        }
    }

    @Override
    public PetDismissResult dismiss(Player owner) {
        Optional<PetSnapshot> selectedOpt = getSelectedPet(owner.getUniqueId());
        if (selectedOpt.isEmpty()) {
            return new PetDismissResult(false, "Çağırılmış aktif bir petiniz bulunmuyor.");
        }

        PetSnapshot pet = selectedOpt.get();

        // PreDismiss Event
        PetPreDismissEvent preEvent = new PetPreDismissEvent(owner, pet);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            return new PetDismissResult(false, "Kaldırma işlemi başka bir eklenti tarafından engellendi.");
        }

        // Complete dismiss and clear selection from DB
        coordinator.dismissAndClear(owner.getUniqueId());

        PetDismissEvent dismissEvent = new PetDismissEvent(owner, pet);
        Bukkit.getPluginManager().callEvent(dismissEvent);

        return new PetDismissResult(true, "Pet kaldırıldı.");
    }

    @Override
    public PetRenameResult rename(Player owner, UUID petId, String newName) {
        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new PetRenameResult(false, "Pet bulunamadı.");
        }

        PetInstance pet = petOpt.get();
        if (!pet.ownerId().equals(owner.getUniqueId())) {
            return new PetRenameResult(false, "Bu pet size ait değil.");
        }

        String validatedName = validateName(newName);
        if (validatedName == null) {
            return new PetRenameResult(false, "Geçersiz isim! (İsim 2-16 karakter olmalı ve izin verilmeyen renk/biçimlendirme içermemeli).");
        }

        PetSnapshot snapshot = mapToSnapshot(pet);

        PetRenameEvent event = new PetRenameEvent(owner, snapshot, pet.customName(), validatedName);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return new PetRenameResult(false, "İsim değiştirme işlemi başka bir eklenti tarafından engellendi.");
        }

        String finalName = event.getNewName();
        PetInstance updated = pet.withCustomName(finalName);
        try {
            repository.update(updated);
        } catch (Exception e) {
            return new PetRenameResult(false, "İsim veritabanına kaydedilemedi: " + e.getMessage());
        }

        // If currently active, update nameplate
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(owner.getUniqueId());
        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            if (activePet.getPetId().equals(petId)) {
                definitionRegistry.find(pet.definitionId()).ifPresent(def -> 
                    entityController.updateName(activePet.getSpawnedEntity(), updated, def)
                );
            }
        }

        return new PetRenameResult(true, "Pet ismi başarıyla değiştirildi.");
    }

    private String validateName(String name) {
        if (name == null) return null;
        String clean = name.trim();
        
        int min = plugin.getConfig().getInt("naming.minimum-length", 2);
        int max = plugin.getConfig().getInt("naming.maximum-length", 16);
        boolean allowColors = plugin.getConfig().getBoolean("naming.allow-colors", false);
        boolean allowFormatting = plugin.getConfig().getBoolean("naming.allow-formatting", false);

        if (clean.length() < min || clean.length() > max) {
            return null;
        }

        if (!allowColors && (clean.contains("&") || clean.contains("§"))) {
            return null;
        }

        if (!allowFormatting && (clean.contains("<") || clean.contains(">"))) {
            return null;
        }

        return clean;
    }

    private PetSnapshot mapToSnapshot(PetInstance p) {
        return new PetSnapshot(
                p.petId(),
                p.ownerId(),
                p.definitionId(),
                p.customName(),
                p.level(),
                p.experience(),
                p.storageState(),
                p.createdAt(),
                p.updatedAt()
        );
    }
}
