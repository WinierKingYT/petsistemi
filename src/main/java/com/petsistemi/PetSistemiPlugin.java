package com.petsistemi;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetService;
import com.petsistemi.application.DefaultPetExperienceService;
import com.petsistemi.application.DefaultPetService;
import com.petsistemi.command.PetAdminCommand;
import com.petsistemi.command.PetCommand;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.definition.YamlPetDefinitionRegistry;
import com.petsistemi.listener.PetEntityListener;
import com.petsistemi.listener.PetProtectionListener;
import com.petsistemi.listener.PlayerConnectionListener;
import com.petsistemi.listener.WorldChangeListener;
import com.petsistemi.persistence.DatabaseManager;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.SqlitePetRepository;
import com.petsistemi.runtime.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

public class PetSistemiPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private PetDefinitionRegistry definitionRegistry;
    private ActivePetRegistry activeRegistry;
    private PetEntityController entityController;
    private PetBehaviorController behaviorController;
    private PetRuntimeCoordinator coordinator;
    
    private PetService petService;
    private PetExperienceService experienceService;

    private BukkitTask tickTask;
    private BukkitTask watchdogTask;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // 1. Database Manager & Fail-Fast Initialization
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "PET VERİTABANI BAŞLATILAMADI! Eklenti devre dışı bırakılıyor.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PetRepository repository = new SqlitePetRepository(databaseManager, getLogger());

        // 2. Definition Registry Setup
        definitionRegistry = new YamlPetDefinitionRegistry(this);
        definitionRegistry.reload();

        // 3. Runtime Registries & Controllers
        activeRegistry = new ActivePetRegistry();
        entityController = new PaperPetEntityController(this);
        behaviorController = new BasicPetBehaviorController();

        // 4. Pet Runtime Coordinator
        coordinator = new PetRuntimeCoordinator(this, repository, definitionRegistry, activeRegistry, entityController, behaviorController);

        // 5. Core Application Services
        petService = new DefaultPetService(this, repository, definitionRegistry, activeRegistry, entityController, coordinator);
        experienceService = new DefaultPetExperienceService(this, repository, definitionRegistry, activeRegistry, entityController);

        // 6. Register API Services globally in Bukkit
        getServer().getServicesManager().register(PetService.class, petService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PetExperienceService.class, experienceService, this, ServicePriority.Normal);

        // 7. Listeners Registration
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this, petService, coordinator), this);
        getServer().getPluginManager().registerEvents(new PetEntityListener(activeRegistry, coordinator), this);
        getServer().getPluginManager().registerEvents(new PetProtectionListener(activeRegistry), this);
        getServer().getPluginManager().registerEvents(new WorldChangeListener(this, activeRegistry), this);

        // 8. Commands Registration
        Objects.requireNonNull(getCommand("pet")).setExecutor(new PetCommand(petService));
        Objects.requireNonNull(getCommand("pet")).setTabCompleter(new PetCommand(petService));

        PetAdminCommand adminCmd = new PetAdminCommand(this, petService, experienceService, definitionRegistry, activeRegistry, repository);
        Objects.requireNonNull(getCommand("petadmin")).setExecutor(adminCmd);
        Objects.requireNonNull(getCommand("petadmin")).setTabCompleter(adminCmd);

        // 9. Start Behavior Ticking Task (runs every 5 ticks / 250ms)
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (ActivePet activePet : activeRegistry.getAllActive()) {
                Player owner = Bukkit.getPlayer(activePet.getOwnerId());
                if (owner != null && owner.isOnline()) {
                    Entity entity = activePet.getSpawnedEntity();
                    if (entity instanceof LivingEntity living) {
                        behaviorController.tick(activePet, living, owner);
                    }
                }
            }
        }, 20L, 5L);

        // 10. Start Periodic Entity Loss Watchdog (runs every 100 ticks / 5 seconds)
        watchdogTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (coordinator != null) {
                coordinator.runWatchdogCheck();
            }
        }, 100L, 100L);

        // 11. Restore pets for online players (e.g. reload or late plugin load)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                petService.getSelectedPet(player.getUniqueId())
                        .ifPresent(snapshot -> petService.summon(player, snapshot.petId()));
            }
        }, 20L);

        getLogger().info("PetSistemiPlugin (Paper 1.20.4, Java 17) başarıyla aktif edildi!");
    }

    @Override
    public void onDisable() {
        // Cancel tasks
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (watchdogTask != null) {
            watchdogTask.cancel();
        }

        // Guaranteed non-cancellable force cleanup for shutdown
        if (coordinator != null) {
            coordinator.forceCleanupAll();
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("PetSistemiPlugin başarıyla devredışı bırakıldı!");
    }
}
