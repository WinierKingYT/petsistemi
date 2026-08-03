package com.petsistemi.listener;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetProgressionListener implements Listener {

    private final ActivePetRegistry activePetRegistry;
    private final PetExperienceService experienceService;
    private final Map<UUID, Double> distanceAccumulator = new ConcurrentHashMap<>();

    public PetProgressionListener(ActivePetRegistry activePetRegistry, PetExperienceService experienceService) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !killer.isOnline()) {
            return;
        }

        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(killer.getUniqueId());
        if (activeOpt.isEmpty()) {
            return;
        }

        ActivePet activePet = activeOpt.get();

        // Calculate XP based on mob's max health
        double maxHealth = 20.0;
        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealth = maxHealthAttr.getValue();
        }

        long xpEarned = Math.max(5L, (long) Math.ceil(maxHealth / 2.0));
        experienceService.addExperience(activePet.getPetId(), xpEarned, ExperienceSource.MOB_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(player.getUniqueId());
        if (activeOpt.isEmpty()) {
            return;
        }

        double distance = from.distance(to);
        if (distance <= 0.0 || distance > 10.0) { // Exclude teleporting
            return;
        }

        UUID uuid = player.getUniqueId();
        double current = distanceAccumulator.getOrDefault(uuid, 0.0) + distance;
        if (current >= 50.0) { // Award 5 XP every 50 blocks
            distanceAccumulator.put(uuid, 0.0);
            experienceService.addExperience(activeOpt.get().getPetId(), 5L, ExperienceSource.WALKING);
        } else {
            distanceAccumulator.put(uuid, current);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        distanceAccumulator.remove(event.getPlayer().getUniqueId());
    }
}
