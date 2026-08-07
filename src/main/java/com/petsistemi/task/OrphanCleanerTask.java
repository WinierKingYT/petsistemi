package com.petsistemi.task;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Periodically scans loaded worlds for orphaned pet entities — entities tagged with the
 * {@code pet_id} PDC key whose pet the runtime no longer knows about — and removes them.
 */
public class OrphanCleanerTask implements Runnable {

    /**
     * Answers whether a pet id is still owned by the runtime. Implemented by
     * {@code PetRuntimeCoordinator#isKnownPet}, which covers both live pets and pets
     * that are mid-summon.
     */
    @FunctionalInterface
    public interface KnownPetLookup {
        boolean isKnown(UUID petId);
    }

    private final JavaPlugin plugin;
    private final KnownPetLookup knownPets;
    private final NamespacedKey petIdKey;
    private final Logger logger;

    public OrphanCleanerTask(JavaPlugin plugin, KnownPetLookup knownPets) {
        this.plugin = Objects.requireNonNull(plugin, "plugin null olamaz.");
        this.knownPets = Objects.requireNonNull(knownPets, "knownPets null olamaz.");
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.logger = plugin.getLogger();
    }

    @Override
    public void run() {
        int cleanedCount = 0;

        for (World world : Bukkit.getWorlds()) {
            if (world == null) continue;
            for (Entity entity : world.getEntities()) {
                try {
                    if (shouldRemove(entity)) {
                        entity.remove();
                        cleanedCount++;
                    }
                } catch (Exception ignored) {
                    // One stubborn entity must not abort the sweep.
                }
            }
        }

        if (cleanedCount > 0 && logger != null) {
            logger.info("[OrphanCleanerTask] Toplam " + cleanedCount + " adet yetim pet varlığı temizlendi.");
        }
    }

    /**
     * An entity is an orphan only when it is definitely ours (carries a well-formed
     * {@code pet_id}) and the runtime no longer knows that pet. Anything unreadable is
     * left alone — deleting a live pet is far worse than leaving a stray behind.
     */
    boolean shouldRemove(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        if (!entity.getPersistentDataContainer().has(petIdKey, PersistentDataType.STRING)) {
            return false;
        }
        String petIdStr = entity.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
        if (petIdStr == null) {
            return false;
        }
        UUID petId;
        try {
            petId = UUID.fromString(petIdStr);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return !knownPets.isKnown(petId);
    }
}
