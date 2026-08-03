package com.petsistemi.bootstrap;

import com.petsistemi.application.DefaultPetExperienceService;
import com.petsistemi.application.DefaultPetService;
import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.PluginConfigurationLoader;
import com.petsistemi.definition.AtomicPetDefinitionRegistry;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.gui.PlayerInputSessionManager;
import com.petsistemi.message.MessageService;
import com.petsistemi.persistence.*;
import com.petsistemi.progression.LinearExperienceCurve;
import com.petsistemi.runtime.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;

public final class PetPluginBootstrap {

    private PetPluginBootstrap() {}

    public static PetPluginContext bootstrap(JavaPlugin plugin) {
        plugin.getLogger().info("PetSistemi önyükleme (bootstrap) başlatılıyor...");

        // 1. Config & Messages Setup
        plugin.saveDefaultConfig();
        PluginConfiguration config = PluginConfigurationLoader.load(plugin.getConfig());
        MessageService messageService = new MessageService(plugin);

        DatabaseManager databaseManager = null;
        DatabaseExecutor dbExecutor = null;

        try {
            // 2. Database Connection & Executor
            databaseManager = new DatabaseManager(plugin);
            databaseManager.initialize();

            Connection connection = databaseManager.getConnection();
            File dbFile = databaseManager.getDbFile();
            dbExecutor = new DatabaseExecutor(plugin.getLogger());

            // 3. Database Backup & Migrations
            File backupDir = new File(plugin.getDataFolder(), "database-backups");
            boolean backupEnabled = config.database().backupEnabled();
            boolean failOnBackupError = config.database().failOnBackupError();
            int maxBackups = config.database().maxBackups();

            SchemaMigrator.migrate(connection, dbFile, backupDir, backupEnabled, failOnBackupError, maxBackups);

            // 4. Definition Registry
            PetDefinitionRegistry definitionRegistry = new AtomicPetDefinitionRegistry(plugin);
            definitionRegistry.reload();

            // 5. Repositories, Cache & Audit
            PetRepository petRepository = new SqlitePetRepository(databaseManager, plugin.getLogger());
            PetSelectionRepository selectionRepository = new SqlitePetSelectionRepository(databaseManager, plugin.getLogger());
            PlayerPetProfileCache profileCache = new PlayerPetProfileCache(petRepository, selectionRepository);
            AuditLogger auditLogger = new AuditLogger(databaseManager, plugin.getLogger());

            // 6. Runtime Components & Sessions
            PetEntityController entityController = new PaperPetEntityController(plugin);
            PetBehaviorController behaviorController = new BasicPetBehaviorController();
            ActivePetRegistry activePetRegistry = new ActivePetRegistry();
            PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(plugin, petRepository, definitionRegistry, activePetRegistry, entityController, behaviorController);
            PlayerInputSessionManager sessionManager = new PlayerInputSessionManager();

            // 7. Application Services
            DefaultPetService petService = new DefaultPetService(plugin, petRepository, selectionRepository, definitionRegistry, activePetRegistry, entityController, coordinator, profileCache);
            DefaultPetExperienceService experienceService = new DefaultPetExperienceService(plugin, petRepository, definitionRegistry, activePetRegistry, entityController, new LinearExperienceCurve(100));

            // 8. Task Registry
            TaskRegistry taskRegistry = new TaskRegistry();

            plugin.getLogger().info("PetSistemi önyükleme başarıyla tamamlandı.");

            return new PetPluginContext(
                    plugin,
                    config,
                    messageService,
                    databaseManager,
                    dbExecutor,
                    petRepository,
                    selectionRepository,
                    profileCache,
                    auditLogger,
                    definitionRegistry,
                    activePetRegistry,
                    entityController,
                    behaviorController,
                    coordinator,
                    petService,
                    experienceService,
                    sessionManager,
                    taskRegistry
            );
        } catch (Throwable t) {
            plugin.getLogger().severe("PetSistemi önyüklemesi sırasında kritik hata oluştu! Kaynaklar temizleniyor: " + t.getMessage());
            if (dbExecutor != null) {
                try { dbExecutor.close(); } catch (Exception ignored) {}
            }
            if (databaseManager != null) {
                try { databaseManager.close(); } catch (Exception ignored) {}
            }
            throw (t instanceof RuntimeException re) ? re : new RuntimeException("Önyükleme başarısız.", t);
        }
    }
}
