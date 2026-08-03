package com.petsistemi.application;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.*;
import com.petsistemi.api.result.*;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfileCache;
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
    private final PetSelectionRepository selectionRepository;
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activePetRegistry;
    private final PetEntityController entityController;
    private final PetRuntimeCoordinator coordinator;
    private final PlayerPetProfileCache profileCache;

    public DefaultPetService(JavaPlugin plugin, PetRepository repository,
                             PetSelectionRepository selectionRepository,
                             PetDefinitionRegistry definitionRegistry,
                             ActivePetRegistry activePetRegistry,
                             PetEntityController entityController,
                             PetRuntimeCoordinator coordinator) {
        this(plugin, repository, selectionRepository, definitionRegistry, activePetRegistry, entityController, coordinator, null);
    }

    public DefaultPetService(JavaPlugin plugin, PetRepository repository,
                             PetSelectionRepository selectionRepository,
                             PetDefinitionRegistry definitionRegistry,
                             ActivePetRegistry activePetRegistry,
                             PetEntityController entityController,
                             PetRuntimeCoordinator coordinator,
                             PlayerPetProfileCache profileCache) {
        this.plugin = plugin;
        this.repository = repository;
        this.selectionRepository = selectionRepository;
        this.definitionRegistry = definitionRegistry;
        this.activePetRegistry = activePetRegistry;
        this.entityController = entityController;
        this.coordinator = coordinator;
        this.profileCache = profileCache;
    }

    @Override
    public Optional<PetSnapshot> findPet(UUID petId) {
        return repository.findById(petId).map(this::mapToSnapshot);
    }

    @Override
    public Collection<PetSnapshot> getOwnedPets(UUID ownerId) {
        Optional<PetSelection> selectionOpt = selectionRepository.findByOwner(ownerId);
        UUID selectedPetId = selectionOpt.map(PetSelection::petId).orElse(null);

        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(ownerId);
        UUID spawnedPetId = activeOpt.map(ActivePet::getPetId).orElse(null);

        return repository.findByOwner(ownerId).stream()
                .map(p -> new PetSnapshot(
                        p.petId(),
                        p.ownerId(),
                        p.definitionId(),
                        p.customName(),
                        p.level(),
                        p.experience(),
                        p.availabilityState(),
                        p.petId().equals(selectedPetId),
                        p.petId().equals(spawnedPetId)
                ))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PetSnapshot> getSelectedPet(UUID ownerId) {
        return selectionRepository.findByOwner(ownerId)
                .flatMap(selection -> repository.findById(selection.petId()))
                .map(this::mapToSnapshot);
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
        return getSelectedPet(ownerId);
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
                PetAvailabilityState.AVAILABLE,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        try {
            repository.insert(pet);
            if (profileCache != null) {
                profileCache.invalidate(ownerId);
            }
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

        if (pet.availabilityState() == PetAvailabilityState.DISABLED) {
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
            repository.update(pet.withAvailabilityState(PetAvailabilityState.DISABLED));
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
    public PetRenameResult rename(UUID ownerId, UUID petId, String newName) {
        Player player = Bukkit.getPlayer(ownerId);
        if (player != null && player.isOnline()) {
            return rename(player, petId, newName);
        }

        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new PetRenameResult(false, "Pet bulunamadı.");
        }

        PetInstance pet = petOpt.get();
        if (!pet.ownerId().equals(ownerId)) {
            return new PetRenameResult(false, "Bu pet bu oyuncuya ait değil.");
        }

        String validatedName = validateName(newName);
        if (validatedName == null) {
            return new PetRenameResult(false, "Geçersiz isim!");
        }

        PetInstance updated = pet.withCustomName(validatedName);
        try {
            repository.update(updated);
            return new PetRenameResult(true, "Pet ismi güncellendi.");
        } catch (Exception e) {
            return new PetRenameResult(false, "İsim veritabanına kaydedilemedi: " + e.getMessage());
        }
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

    @Override
    public PetDisableResult disablePet(UUID petId) {
        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new PetDisableResult(false, "Pet bulunamadı.");
        }

        PetInstance pet = petOpt.get();
        UUID ownerId = pet.ownerId();

        // Only despawn if currently spawned runtime pet matches target petId
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(ownerId);
        if (activeOpt.isPresent() && activeOpt.get().getPetId().equals(petId)) {
            coordinator.dismissAndClear(ownerId);
        }

        // Only clear selection if active selection matches target petId
        Optional<PetSelection> selectionOpt = selectionRepository.findByOwner(ownerId);
        if (selectionOpt.isPresent() && selectionOpt.get().petId().equals(petId)) {
            selectionRepository.clear(ownerId);
        }

        PetInstance updated = new PetInstance(pet.petId(), pet.ownerId(), pet.definitionId(), pet.customName(), pet.level(), pet.experience(), PetAvailabilityState.DISABLED, pet.createdAt(), System.currentTimeMillis());
        try {
            repository.update(updated);
            if (profileCache != null) {
                profileCache.invalidate(ownerId);
            }
            return new PetDisableResult(true, "Pet başarıyla devre dışı bırakıldı (DISABLED).");
        } catch (Exception e) {
            return new PetDisableResult(false, "Pet güncellenemedi: " + e.getMessage());
        }
    }

    @Override
    public PetDisableResult enablePet(UUID petId) {
        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new PetDisableResult(false, "Pet bulunamadı.");
        }

        PetInstance pet = petOpt.get();
        PetInstance updated = new PetInstance(pet.petId(), pet.ownerId(), pet.definitionId(), pet.customName(), pet.level(), pet.experience(), PetAvailabilityState.AVAILABLE, pet.createdAt(), System.currentTimeMillis());
        try {
            repository.update(updated);
            if (profileCache != null) {
                profileCache.invalidate(pet.ownerId());
            }
            return new PetDisableResult(true, "Pet başarıyla etkinleştirildi (AVAILABLE).");
        } catch (Exception e) {
            return new PetDisableResult(false, "Pet güncellenemedi: " + e.getMessage());
        }
    }

    @Override
    public PetRemoveResult removePet(UUID petId) {
        Optional<PetInstance> petOpt = repository.findById(petId);
        if (petOpt.isEmpty()) {
            return new PetRemoveResult(false, "Pet bulunamadı.");
        }

        PetInstance pet = petOpt.get();
        UUID ownerId = pet.ownerId();

        // Only despawn if currently spawned runtime pet matches target petId
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(ownerId);
        if (activeOpt.isPresent() && activeOpt.get().getPetId().equals(petId)) {
            coordinator.dismissAndClear(ownerId);
        }

        // Only clear selection if active selection matches target petId
        Optional<PetSelection> selectionOpt = selectionRepository.findByOwner(ownerId);
        if (selectionOpt.isPresent() && selectionOpt.get().petId().equals(petId)) {
            selectionRepository.clear(ownerId);
        }

        try {
            repository.delete(petId);
            if (profileCache != null) {
                profileCache.invalidate(ownerId);
            }
            return new PetRemoveResult(true, "Pet başarıyla silindi.");
        } catch (Exception e) {
            return new PetRemoveResult(false, "Pet silinemedi: " + e.getMessage());
        }
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
        boolean selected = selectionRepository.findByOwner(p.ownerId())
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
