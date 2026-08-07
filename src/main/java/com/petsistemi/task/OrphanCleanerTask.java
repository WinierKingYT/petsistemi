package com.petsistemi.task;

import com.petsistemi.runtime.ActivePetRegistry;
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
 * Periodically scans loaded worlds for orphaned pet entities (entities tagged with pet_id
 * PDC key whose pet_id is no longer registered in ActivePetRegistry) and removes them.
 */
public class OrphanCleanerTask implements Runnable {

    private final JavaPlugin plugin;
    private final ActivePetRegistry activePetRegistry;
    private final NamespacedKey petIdKey;
    private final Logger logger;

    public OrphanCleanerTask(JavaPlugin plugin, ActivePetRegistry activePetRegistry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin null olamaz.");
        this.activePetRegistry = Objects.requireNonNull(activePetRegistry, "activePetRegistry null olamaz.");
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.logger = plugin.getLogger();
    }

    @Override
    public void run() {
        if (activePetRegistry == null) return;
        int cleanedCount = 0;

        for (World world : Bukkit.getWorlds()) {
            if (world == null) continue;
            for (Entity entity : world.getEntities()) {
                if (entity == null || !entity.isValid()) continue;
                if (!entity.getPersistentDataContainer().has(petIdKey, PersistentDataType.STRING)) continue;

                String petIdStr = entity.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
                if (petIdStr == null) continue;

                try {
                    UUID petId = UUID.fromString(petIdStr);
                    if (activePetRegistry.getAllActive().stream().noneMatch(p -> p.getPetId().equals(petId))) {
                        entity.remove();
                        cleanedCount++;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (cleanedCount > 0 && logger != null) {
            logger.info("[OrphanCleanerTask] Toplam " + cleanedCount + " adet yetim pet varlığı temizlendi.");
        }
    }
}
