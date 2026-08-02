package com.petsistemi.bootstrap;

import com.petsistemi.application.DefaultPetExperienceService;
import com.petsistemi.application.DefaultPetService;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.definition.YamlPetDefinitionRegistry;
import com.petsistemi.persistence.*;
import com.petsistemi.runtime.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;

public final class PetPluginBootstrap {

    private PetPluginBootstrap() {}

    public static PetPluginContext bootstrap(JavaPlugin plugin) {
        plugin.getLogger().info("PetSistemi önyükleme (bootstrap) başlatılıyor...");

        // 1. Config Setup
        plugin.saveDefaultConfig();

        // 2. Database Connection
        File dbFile = new File(plugin.getDataFolder(), "pets.db");
        DatabaseManager databaseManager = new DatabaseManager(plugin);
        try {
            databaseManager.initialize();
        } catch (Exception e) {
            plugin.getLogger().severe("Veritabanı başlatılırken hata oluştu: " + e.getMessage());
            throw new RuntimeException("Veritabanı başlatılamadı.", e);
        }

        Connection connection = databaseManager.getConnection();

        // 3. Database Backup & Migrations
        try {
            File backupDir = new File(plugin.getDataFolder(), "database-backups");
            boolean backupEnabled = plugin.getConfig().getBoolean("database.migration-backup.enabled", true);
            boolean failOnBackupError = plugin.getConfig().getBoolean("database.migration-backup.fail-startup-on-backup-error", true);
            int maxBackups = plugin.getConfig().getInt("database.migration-backup.maximum-backups", 5);

            SchemaMigrator.migrate(connection, dbFile, backupDir, backupEnabled, failOnBackupError, maxBackups);
        } catch (Exception e) {
            plugin.getLogger().severe("Veritabanı migration adımı başarısız oldu! " + e.getMessage());
            throw new RuntimeException("Veritabanı başlatılamadığı için eklenti durduruldu.", e);
        }

        // 4. Definition Registry
        PetDefinitionRegistry definitionRegistry = new YamlPetDefinitionRegistry(plugin);
        definitionRegistry.reload();

        // 5. Repositories
        PetRepository petRepository = new SqlitePetRepository(databaseManager, plugin.getLogger());
        PetSelectionRepository selectionRepository = new SqlitePetSelectionRepository(databaseManager, plugin.getLogger());

        // 6. Runtime Components
        PetEntityController entityController = new PaperPetEntityController(plugin);
        PetBehaviorController behaviorController = new BasicPetBehaviorController();
        ActivePetRegistry activePetRegistry = new ActivePetRegistry();
        PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(plugin, petRepository, definitionRegistry, activePetRegistry, entityController, behaviorController);

        // 7. Application Services
        DefaultPetService petService = new DefaultPetService(plugin, petRepository, definitionRegistry, activePetRegistry, entityController, coordinator);
        DefaultPetExperienceService experienceService = new DefaultPetExperienceService(plugin, petRepository, definitionRegistry, activePetRegistry, entityController);

        // 8. Task Registry
        TaskRegistry taskRegistry = new TaskRegistry();

        plugin.getLogger().info("PetSistemi önyükleme başarıyla tamamlandı.");

        return new PetPluginContext(
                plugin,
                databaseManager,
                petRepository,
                selectionRepository,
                definitionRegistry,
                activePetRegistry,
                entityController,
                behaviorController,
                coordinator,
                petService,
                experienceService,
                taskRegistry
        );
    }
}
