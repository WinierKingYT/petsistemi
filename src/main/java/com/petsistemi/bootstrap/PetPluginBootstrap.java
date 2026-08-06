package com.petsistemi.bootstrap;

import com.petsistemi.application.DefaultPetExperienceService;
import com.petsistemi.application.DefaultPetService;
import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.PluginConfigurationLoader;
import com.petsistemi.definition.AtomicPetDefinitionRegistry;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.gui.PlayerInputSessionManager;
import com.petsistemi.message.MessageService;
import com.petsistemi.persistence.*;
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
            // 2. Database Connection, Executor & Main Thread Dispatcher
            databaseManager = new DatabaseManager(plugin);
            databaseManager.initialize();

            Connection connection = databaseManager.getConnection();
            File dbFile = databaseManager.getDbFile();
            dbExecutor = new DatabaseExecutor(plugin.getLogger());
            MainThreadDispatcher mainThreadDispatcher = new BukkitMainThreadDispatcher(plugin);

            // 3. Database Backup & Migrations
            File backupDir = new File(plugin.getDataFolder(), "database-backups");
            boolean backupEnabled = config.database().backupEnabled();
            boolean failOnBackupError = config.database().failOnBackupError();
            int maxBackups = config.database().maxBackups();

            SchemaMigrator.migrate(connection, dbFile, backupDir, backupEnabled, failOnBackupError, maxBackups);

            // 4. Definition Registry & Atomic Config Snapshot
            PetDefinitionRegistry definitionRegistry = new AtomicPetDefinitionRegistry(plugin);
            definitionRegistry.reload();

            com.petsistemi.config.RuntimeConfigurationSnapshot initialSnapshot = new com.petsistemi.config.RuntimeConfigurationSnapshot(
                    config,
                    messageService,
                    definitionRegistry,
                    System.currentTimeMillis()
            );
            java.util.concurrent.atomic.AtomicReference<com.petsistemi.config.RuntimeConfigurationSnapshot> configSnapshot = new java.util.concurrent.atomic.AtomicReference<>(initialSnapshot);

            // 5. Repositories, Cache & Audit
            PetRepository petRepository = new SqlitePetRepository(databaseManager, plugin.getLogger());
            PetSelectionRepository selectionRepository = new SqlitePetSelectionRepository(databaseManager, plugin.getLogger());
            PlayerPetProfileCache profileCache = new PlayerPetProfileCache(petRepository, selectionRepository);
            AuditLogger auditLogger = new AuditLogger(databaseManager, plugin.getLogger());

            // 6. Runtime Components & Operations
            PaperPetEntityController paperEntityController = new PaperPetEntityController(plugin);
            PetEntityController entityController = paperEntityController;
            PetBehaviorController behaviorController = new BasicPetBehaviorController(configSnapshot);
            ActivePetRegistry activePetRegistry = new ActivePetRegistry();

            // Modular representation / movement registries
            PetRepresentationRegistry representationRegistry = new PetRepresentationRegistry();
            PetMovementRegistry movementRegistry = new PetMovementRegistry();
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.ENTITY, paperEntityController);
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.ITEM_DISPLAY, new ItemDisplayPetRepresentation(plugin, configSnapshot));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.BLOCK_DISPLAY, new BlockDisplayPetRepresentation(plugin, configSnapshot));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.TEXT_DISPLAY, new TextDisplayPetRepresentation(plugin, configSnapshot));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.PARTICLE, new ParticlePetRepresentation(plugin));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.INVISIBLE, new InvisiblePetRepresentation(plugin));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.MULTI_ENTITY, new MultiEntityPetRepresentation(plugin, configSnapshot));
            movementRegistry.register(com.petsistemi.domain.PetMovementType.GROUND_FOLLOW, new GroundFollowMovement(configSnapshot));
            movementRegistry.register(com.petsistemi.domain.PetMovementType.FLYING_FOLLOW, new FlyingFollowMovement(configSnapshot));
            movementRegistry.register(com.petsistemi.domain.PetMovementType.ORBIT, new OrbitMovement(configSnapshot));
            movementRegistry.register(com.petsistemi.domain.PetMovementType.HOVER, new HoverMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.SHOULDER, new ShoulderMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.ANCHORED, new AnchoredMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.TRAIL, new TrailMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.FORMATION, new FormationMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.TELEPORT_ONLY, new TeleportOnlyMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.STATIC_NEAR_OWNER, new TeleportOnlyMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.ECHO, new EchoMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.SHADOW_TRAIL, new ShadowTrailMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.ROAM_NEAR_OWNER, new RoamNearOwnerMovement());
            movementRegistry.register(com.petsistemi.domain.PetMovementType.MIRROR, new MirrorMovement());

            PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(
                    plugin, definitionRegistry, activePetRegistry, entityController, behaviorController,
                    representationRegistry, movementRegistry
            );

            PetReactionEngine reactionEngine = new PetReactionEngine(configSnapshot);
            PetEmoteController emoteController = new PetEmoteController(reactionEngine);
            PetIdleSleepController idleSleepController = new PetIdleSleepController(
                    configSnapshot, definitionRegistry, representationRegistry, reactionEngine);
            PetTransformController transformController = new PetTransformController(
                    definitionRegistry, representationRegistry);
            coordinator.setTransformController(transformController);
            idleSleepController.setTransformController(transformController);
            coordinator.setIdleSleepController(idleSleepController);
            coordinator.setEmoteController(emoteController);

            PetRuntimeOperationService operationService = new PetRuntimeOperationService(
                    plugin, petRepository, selectionRepository, definitionRegistry, coordinator, profileCache, dbExecutor, mainThreadDispatcher
            );

            // Stage 7: watchdog recovery callback (wired after construction to avoid circularity)
            coordinator.setRecoveryHandler(operationService::recoverPetAsync);
            PlayerInputSessionManager sessionManager = new PlayerInputSessionManager();

            // 7. Application Services
            DefaultPetService petService = new DefaultPetService(
                    plugin, petRepository, selectionRepository, definitionRegistry, activePetRegistry, entityController, dbExecutor, mainThreadDispatcher, profileCache, configSnapshot
            );

            DefaultPetExperienceService experienceService = new DefaultPetExperienceService(
                    plugin, petRepository, definitionRegistry, activePetRegistry, entityController, new com.petsistemi.progression.ConfigBackedLinearExperienceCurve(configSnapshot), dbExecutor, mainThreadDispatcher, profileCache, configSnapshot
            );

            // 8. Task Registry
            TaskRegistry taskRegistry = new TaskRegistry();

            plugin.getLogger().info("PetSistemi önyükleme başarıyla tamamlandı.");

            return new PetPluginContext(
                    plugin,
                    config,
                    messageService,
                    databaseManager,
                    dbExecutor,
                    mainThreadDispatcher,
                    petRepository,
                    selectionRepository,
                    profileCache,
                    auditLogger,
                    definitionRegistry,
                    activePetRegistry,
                    entityController,
                    behaviorController,
                    coordinator,
                    operationService,
                    petService,
                    experienceService,
                    sessionManager,
                    taskRegistry,
                    configSnapshot,
                    reactionEngine,
                    emoteController
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
