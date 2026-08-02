package com.petsistemi.application;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.*;
import com.petsistemi.api.result.*;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.PetStorageState;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetEntityController;
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

    public DefaultPetService(JavaPlugin plugin, PetRepository repository,
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
    public Optional<PetSnapshot> getActivePet(UUID ownerId) {
        return repository.findActiveByOwner(ownerId).map(this::mapToSnapshot);
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

        repository.insert(pet);

        // Call event
        PetGiveEvent event = new PetGiveEvent(ownerId, pet);
        Bukkit.getPluginManager().callEvent(event);

        return new PetGiveResult(true, "Pet başarıyla verildi.", pet);
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

        // PreSummon Event
        PetPreSummonEvent preEvent = new PetPreSummonEvent(owner, pet);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            return new PetSummonResult(false, "Çağırma işlemi başka bir eklenti tarafından engellendi.");
        }

        // Check and dismiss existing active pet
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(owner.getUniqueId());
        if (activeOpt.isPresent()) {
            dismiss(owner);
        }

        Optional<PetDefinition> defOpt = definitionRegistry.find(pet.definitionId());
        if (defOpt.isEmpty()) {
            // Disable if definition deleted
            repository.update(pet.withStorageState(PetStorageState.DISABLED));
            return new PetSummonResult(false, "Pet tanımı bulunamadı, pet devre dışı bırakıldı.");
        }

        PetDefinition definition = defOpt.get();

        // Spawn
        try {
            Entity spawned = entityController.spawn(pet, definition, owner);
            ActivePet activePet = new ActivePet(petId, owner.getUniqueId(), spawned.getUniqueId(), spawned, PetRuntimeState.ACTIVE);
            
            activePetRegistry.register(activePet);
            repository.setActivePet(owner.getUniqueId(), petId);
            repository.update(pet.withStorageState(PetStorageState.ACTIVE));

            PetSummonEvent summonEvent = new PetSummonEvent(owner, pet, spawned);
            Bukkit.getPluginManager().callEvent(summonEvent);

            return new PetSummonResult(true, "Pet başarıyla çağırıldı.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Pet çağırılırken entity oluşturulamadı!", e);
            return new PetSummonResult(false, "Pet çağırılırken bir sunucu hatası oluştu.");
        }
    }

    @Override
    public PetDismissResult dismiss(Player owner) {
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(owner.getUniqueId());
        if (activeOpt.isEmpty()) {
            return new PetDismissResult(false, "Çağırılmış aktif bir petiniz bulunmuyor.");
        }

        ActivePet activePet = activeOpt.get();
        Optional<PetInstance> petOpt = repository.findById(activePet.getPetId());
        if (petOpt.isEmpty()) {
            return new PetDismissResult(false, "Pet kaydı bulunamadı.");
        }

        PetInstance pet = petOpt.get();

        // PreDismiss Event
        PetPreDismissEvent preEvent = new PetPreDismissEvent(owner, pet);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            return new PetDismissResult(false, "Kaldırma işlemi başka bir eklenti tarafından engellendi.");
        }

        // Remove Entity
        entityController.remove(activePet.getSpawnedEntity());
        activePetRegistry.unregister(owner.getUniqueId());
        repository.clearActivePet(owner.getUniqueId());
        repository.update(pet.withStorageState(PetStorageState.AVAILABLE));

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

        // Validate name
        String validatedName = validateName(newName);
        if (validatedName == null) {
            return new PetRenameResult(false, "Geçersiz isim! (İsim 2-16 karakter olmalı ve renk kodu içermemeli).");
        }

        // Event
        PetRenameEvent event = new PetRenameEvent(owner, pet, pet.customName(), validatedName);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return new PetRenameResult(false, "İsim değiştirme işlemi başka bir eklenti tarafından engellendi.");
        }

        String finalName = event.getNewName();
        PetInstance updated = pet.withCustomName(finalName);
        repository.update(updated);

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

        if (clean.length() < min || clean.length() > max) {
            return null;
        }

        // Check for formatting codes if not allowed
        if (!allowColors && (clean.contains("&") || clean.contains("§") || clean.contains("<"))) {
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
