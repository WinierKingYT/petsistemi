package com.petsistemi.listener;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
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
import java.util.concurrent.atomic.AtomicReference;

public class PetProgressionListener implements Listener {

    private static final long KILL_XP_MIN  = 1L;
    private static final long BLOCK_XP_MIN = 1L;

    private final ActivePetRegistry activePetRegistry;
    private final PetExperienceService experienceService;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    private final double fallbackWalkXpThreshold;
    private final long fallbackWalkXpAmount;
    private final long fallbackBlockBreakXpBase;
    private final double fallbackKillXpMultiplier;

    private final Map<UUID, Double> distanceAccumulator = new ConcurrentHashMap<>();

    /** Production constructor using atomic configuration snapshot. */
    public PetProgressionListener(ActivePetRegistry activePetRegistry,
                                  PetExperienceService experienceService,
                                  AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
        this.configSnapshot = configSnapshot;
        this.fallbackWalkXpThreshold = 50.0;
        this.fallbackWalkXpAmount = 5L;
        this.fallbackBlockBreakXpBase = 2L;
        this.fallbackKillXpMultiplier = 0.5;
    }

    /** Config-aware legacy constructor using Bukkit FileConfiguration. */
    public PetProgressionListener(ActivePetRegistry activePetRegistry,
                                  PetExperienceService experienceService,
                                  FileConfiguration config) {
        this.activePetRegistry = activePetRegistry;
        this.experienceService = experienceService;
        this.configSnapshot = null;
        if (config != null) {
            this.fallbackWalkXpThreshold  = config.getDouble("progression.walk-xp-threshold",  50.0);
            this.fallbackWalkXpAmount     = config.getLong("progression.walk-xp-amount",        5L);
            this.fallbackBlockBreakXpBase = config.getLong("progression.block-break-xp",        2L);
            this.fallbackKillXpMultiplier = config.getDouble("progression.kill-xp-multiplier",  0.5);
        } else {
            this.fallbackWalkXpThreshold  = 50.0;
            this.fallbackWalkXpAmount     = 5L;
            this.fallbackBlockBreakXpBase = 2L;
            this.fallbackKillXpMultiplier = 0.5;
        }
    }

    /** Backward-compatible constructor. */
    public PetProgressionListener(ActivePetRegistry activePetRegistry, PetExperienceService experienceService) {
        this(activePetRegistry, experienceService, (FileConfiguration) null);
    }

    private PluginConfiguration.ProgressionConfiguration getProgressionConfig() {
        RuntimeConfigurationSnapshot snapshot = (configSnapshot != null) ? configSnapshot.get() : null;
        return (snapshot != null && snapshot.configuration() != null) ? snapshot.configuration().progression() : null;
    }

    // ── Mob Kill ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !killer.isOnline()) return;

        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(killer.getUniqueId());
        if (activeOpt.isEmpty()) return;

        PluginConfiguration.ProgressionConfiguration prog = getProgressionConfig();
        double killXpMultiplier = (prog != null) ? prog.killXpMultiplier() : fallbackKillXpMultiplier;

        double maxHealth = 20.0;
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) maxHealth = attr.getValue();

        long xp = Math.max(KILL_XP_MIN, (long) Math.ceil(maxHealth * killXpMultiplier));
        experienceService.addExperience(activeOpt.get().getPetId(), xp, ExperienceSource.MOB_KILL);
    }

    // ── Block Break ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        PluginConfiguration.ProgressionConfiguration prog = getProgressionConfig();
        long blockBreakXp = (prog != null) ? prog.blockBreakXp() : fallbackBlockBreakXpBase;

        if (blockBreakXp <= 0) return; // disabled by config

        Player player = event.getPlayer();
        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(player.getUniqueId());
        if (activeOpt.isEmpty()) return;

        experienceService.addExperience(activeOpt.get().getPetId(),
                Math.max(BLOCK_XP_MIN, blockBreakXp),
                ExperienceSource.BLOCK_BREAK);
    }

    // ── Walking ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();
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
            return;
        }

        if (distance <= 0.0 || distance > 10.0) return;

        PluginConfiguration.ProgressionConfiguration prog = getProgressionConfig();
        double walkXpThreshold = (prog != null) ? prog.walkXpThreshold() : fallbackWalkXpThreshold;
        long walkXpAmount      = (prog != null) ? prog.walkXpAmount() : fallbackWalkXpAmount;

        UUID uuid = player.getUniqueId();
        double accumulated = distanceAccumulator.getOrDefault(uuid, 0.0) + distance;
        if (accumulated >= walkXpThreshold) {
            distanceAccumulator.put(uuid, accumulated - walkXpThreshold);
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
