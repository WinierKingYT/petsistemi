package com.petsistemi;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetService;
import com.petsistemi.application.DefaultPetExperienceService;
import com.petsistemi.application.DefaultPetService;
import com.petsistemi.command.PetAdminCommand;
import com.petsistemi.command.PetCommand;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.definition.YamlPetDefinitionRegistry;
import com.petsistemi.listener.PlayerConnectionListener;
import com.petsistemi.listener.PetEntityListener;
import com.petsistemi.listener.PetProtectionListener;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PetSistemiPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private PetDefinitionRegistry definitionRegistry;
    private ActivePetRegistry activeRegistry;
    private PetEntityController entityController;
    private PetBehaviorController behaviorController;
    
    private PetService petService;
    private PetExperienceService experienceService;

    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // 1. Database Manager & Repository Setup
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        PetRepository repository = new SqlitePetRepository(databaseManager, getLogger());

        // 2. Definition Registry Setup
        definitionRegistry = new YamlPetDefinitionRegistry(this);
        definitionRegistry.reload();

        // 3. Runtime Registries
        activeRegistry = new ActivePetRegistry();
        entityController = new PaperPetEntityController(this);
        behaviorController = new BasicPetBehaviorController();

        // 4. Core Services
        petService = new DefaultPetService(this, repository, definitionRegistry, activeRegistry, entityController);
        experienceService = new DefaultPetExperienceService(this, repository, definitionRegistry, activeRegistry, entityController);

        // 5. Register API Services globally
        getServer().getServicesManager().register(PetService.class, petService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PetExperienceService.class, experienceService, this, ServicePriority.Normal);

        // 6. Listeners Registration
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this, petService), this);
        getServer().getPluginManager().registerEvents(new PetEntityListener(activeRegistry, repository), this);
        getServer().getPluginManager().registerEvents(new PetProtectionListener(activeRegistry), this);
        getServer().getPluginManager().registerEvents(new WorldChangeListener(activeRegistry), this);

        // 7. Commands Registration
        Objects.requireNonNull(getCommand("pet")).setExecutor(new PetCommand(petService));
        Objects.requireNonNull(getCommand("pet")).setTabCompleter(new PetCommand(petService));

        PetAdminCommand adminCmd = new PetAdminCommand(this, petService, experienceService, definitionRegistry, activeRegistry, repository);
        Objects.requireNonNull(getCommand("petadmin")).setExecutor(adminCmd);
        Objects.requireNonNull(getCommand("petadmin")).setTabCompleter(adminCmd);

        // 8. Start Behavior Ticking Task (runs every 2 ticks / 100ms)
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
        }, 20L, 2L);

        getLogger().info("PetSistemiPlugin başarıyla aktif edildi!");
    }

    @Override
    public void onDisable() {
        // Cancel behavior tick task
        if (tickTask != null) {
            tickTask.cancel();
        }

        // Clean up spawned pet entities safely (avoids orphaned entities on shutdown/reload)
        if (activeRegistry != null) {
            List<ActivePet> activePetsCopy = new ArrayList<>(activeRegistry.getAllActive());
            for (ActivePet active : activePetsCopy) {
                Player owner = Bukkit.getPlayer(active.getOwnerId());
                if (owner != null) {
                    petService.dismiss(owner);
                } else {
                    entityController.remove(active.getSpawnedEntity());
                }
            }
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("PetSistemiPlugin başarıyla devredışı bırakıldı!");
    }
}
