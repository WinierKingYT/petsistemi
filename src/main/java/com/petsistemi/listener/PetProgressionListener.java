package com.petsistemi.listener;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetProgressionListener implements Listener {

    // Sensible floor values
    private static final long KILL_XP_MIN  = 1L;
    private static final long BLOCK_XP_MIN = 1L;

    private final ActivePetRegistry activePetRegistry;
    private final PetExperienceService experienceService;

    // Config-driven values
    private final double walkXpThreshold;   // blocks walked per XP award
    private final long   walkXpAmount;      // XP per threshold
    private final long   blockBreakXpBase;  // flat XP per block break
    // Kill XP is dynamic (based on max health), but multiplier is configurable
    private final double killXpMultiplier;

    private final Map<UUID, Double> distanceAccumulator = new ConcurrentHashMap<>();

    /** Config-aware constructor. */
    public PetProgressionListener(ActivePetRegistry activePetRegistry,
                                   PetExperienceService experienceService,
                                   FileConfiguration config) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
        if (config != null) {
            this.walkXpThreshold  = config.getDouble("progression.walk-xp-threshold",  50.0);
            this.walkXpAmount     = config.getLong("progression.walk-xp-amount",        5L);
            this.blockBreakXpBase = config.getLong("progression.block-break-xp",        2L);
            this.killXpMultiplier = config.getDouble("progression.kill-xp-multiplier",  0.5);
        } else {
            this.walkXpThreshold  = 50.0;
            this.walkXpAmount     = 5L;
            this.blockBreakXpBase = 2L;
            this.killXpMultiplier = 0.5;
        }
    }

    /** Backward-compatible constructor (used by existing registrars). */
    public PetProgressionListener(ActivePetRegistry activePetRegistry, PetExperienceService experienceService) {
        this(activePetRegistry, experienceService, null);
    }

    // ── Mob Kill ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !killer.isOnline()) return;

        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(killer.getUniqueId());
        if (activeOpt.isEmpty()) return;

        // XP scales with mob max-health × configurable multiplier
        double maxHealth = 20.0;
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) maxHealth = attr.getValue();

        long xp = Math.max(KILL_XP_MIN, (long) Math.ceil(maxHealth * killXpMultiplier));
        experienceService.addExperience(activeOpt.get().getPetId(), xp, ExperienceSource.MOB_KILL);
    }

    // ── Block Break ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (blockBreakXpBase <= 0) return; // disabled by config

        Player player = event.getPlayer();
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(player.getUniqueId());
        if (activeOpt.isEmpty()) return;

        experienceService.addExperience(activeOpt.get().getPetId(),
                Math.max(BLOCK_XP_MIN, blockBreakXpBase),
                ExperienceSource.WALKING); // No dedicated BLOCK_BREAK source yet — reuse WALKING
    }

    // ── Walking ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();
        // Skip head-only rotation events (no block change)
        if (to == null
                || (from.getBlockX() == to.getBlockX()
                &&  from.getBlockY() == to.getBlockY()
                &&  from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(player.getUniqueId());
        if (activeOpt.isEmpty()) return;

        double distance;
        try {
            distance = from.distance(to);
        } catch (IllegalArgumentException ignored) {
            return; // different worlds — skip
        }

        if (distance <= 0.0 || distance > 10.0) return; // >10 = teleport noise

        UUID uuid = player.getUniqueId();
        double accumulated = distanceAccumulator.getOrDefault(uuid, 0.0) + distance;
        if (accumulated >= walkXpThreshold) {
            distanceAccumulator.put(uuid, accumulated - walkXpThreshold); // carry remainder
            experienceService.addExperience(activeOpt.get().getPetId(), walkXpAmount, ExperienceSource.WALKING);
        } else {
            distanceAccumulator.put(uuid, accumulated);
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        distanceAccumulator.remove(event.getPlayer().getUniqueId());
    }
}
