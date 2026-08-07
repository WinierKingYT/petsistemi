package com.petsistemi.runtime.task;

import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

public class PetMagnetTask implements Runnable {

    private static final double MAGNET_RADIUS = 5.0;
    private static final double MAGNET_PULL_SPEED = 0.35;

    private final ActivePetRegistry activePetRegistry;

    public PetMagnetTask(ActivePetRegistry activePetRegistry) {
        this.activePetRegistry = activePetRegistry;
    }

    @Override
    public void run() {
        for (ActivePet activePet : activePetRegistry.getAllActive()) {
            Player owner = Bukkit.getPlayer(activePet.getOwnerId());
            if (owner == null || !owner.isOnline()) continue;

            // Full inventory guard: don't pull items if player has no free slots
            if (owner.getInventory().firstEmpty() == -1) continue;

            Entity entity = activePet.getSpawnedEntity();
            if (entity == null || !entity.isValid() || entity.isDead()) continue;

            // Guard: pet and owner must be in the same world
            if (!entity.getWorld().equals(owner.getWorld())) continue;

            Collection<Entity> nearby = entity.getWorld().getNearbyEntities(
                    entity.getLocation(), MAGNET_RADIUS, MAGNET_RADIUS * 0.6, MAGNET_RADIUS,
                    e -> e instanceof Item
            );

            for (Entity e : nearby) {
                if (!(e instanceof Item item)) continue;
                if (!item.isValid() || item.isDead()) continue;
                if (item.getPickupDelay() > 0) continue;

                try {
                    Vector dir = owner.getLocation().toVector()
                            .subtract(item.getLocation().toVector());
                    if (dir.lengthSquared() < 0.001) continue;
                    item.setVelocity(dir.normalize().multiply(MAGNET_PULL_SPEED));
                } catch (Throwable ignored) {}
            }
        }
    }
}
