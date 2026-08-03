package com.petsistemi.runtime.task;

import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

public class PetMagnetTask implements Runnable {

    private final ActivePetRegistry activePetRegistry;

    public PetMagnetTask(ActivePetRegistry activePetRegistry) {
        this.activePetRegistry = activePetRegistry;
    }

    @Override
    public void run() {
        for (ActivePet activePet : activePetRegistry.getAllActive()) {
            Player owner = Bukkit.getPlayer(activePet.getOwnerId());
            if (owner == null || !owner.isOnline()) {
                continue;
            }

            Entity entity = activePet.getSpawnedEntity();
            if (entity == null || !entity.isValid() || !entity.getWorld().equals(owner.getWorld())) {
                continue;
            }

            Collection<Entity> nearby = entity.getWorld().getNearbyEntities(entity.getLocation(), 5.0, 3.0, 5.0, e -> e instanceof Item);
            for (Entity e : nearby) {
                if (e instanceof Item item && item.isValid() && !item.isDead() && item.getPickupDelay() <= 0) {
                    Vector dir = owner.getLocation().toVector().subtract(item.getLocation().toVector()).normalize().multiply(0.35);
                    item.setVelocity(dir);
                }
            }
        }
    }
}
