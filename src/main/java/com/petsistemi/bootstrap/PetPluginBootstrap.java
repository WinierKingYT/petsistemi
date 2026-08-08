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

        ConnectionProvider connectionProvider = null;
        DatabaseExecutor dbExecutor = null;

        try {
            // 2. Database Connection, Executor & Main Thread Dispatcher
            DatabaseBackend backend = DatabaseBackend.from(config.database().backend());
            File dbFile = null;
            if (backend == DatabaseBackend.MYSQL) {
                MysqlDatabaseManager mysql = new MysqlDatabaseManager(plugin, config.database().mysql());
                mysql.initialize();
                connectionProvider = mysql;
            } else {
                DatabaseManager sqlite = new DatabaseManager(plugin);
                sqlite.initialize();
                connectionProvider = sqlite;
                dbFile = sqlite.getDbFile();
            }

            Connection connection = connectionProvider.getConnection();
            dbExecutor = new DatabaseExecutor(plugin.getLogger());
            MainThreadDispatcher mainThreadDispatcher = new BukkitMainThreadDispatcher(plugin);

            // 3. Database Backup & Migrations
            File backupDir = com.petsistemi.persistence.DatabaseManager.backupDirectory(plugin);
            boolean backupEnabled = config.database().backupEnabled();
            boolean failOnBackupError = config.database().failOnBackupError();
            int maxBackups = config.database().maxBackups();

            if (backend == DatabaseBackend.MYSQL) {
                MysqlSchemaMigrator.migrate(connection);
            } else {
                SchemaMigrator.migrate(connection, dbFile, backupDir, backupEnabled, failOnBackupError, maxBackups);
            }

            // 4. Definition Registry & Atomic Config Snapshot
            AtomicPetDefinitionRegistry definitionRegistry = new AtomicPetDefinitionRegistry(plugin);
            definitionRegistry.reload();
            com.petsistemi.pack.DefaultPetPackService petPackService = new com.petsistemi.pack.DefaultPetPackService(
                    plugin, definitionRegistry, config.ecosystem().petPacks().maximumFiles(),
                    config.ecosystem().petPacks().maximumArchiveBytes(),
                    config.ecosystem().petPacks().maximumExpandedBytes());
            com.petsistemi.marketplace.DefaultPetMarketplaceService marketplaceService =
                    config.ecosystem().marketplace().enabled()
                            ? new com.petsistemi.marketplace.DefaultPetMarketplaceService(
                                    plugin, java.net.URI.create(config.ecosystem().marketplace().catalogUrl()),
                                    config.ecosystem().marketplace().requireSha256(),
                                    config.ecosystem().marketplace().maximumDownloadBytes(),
                                    config.ecosystem().marketplace().requestTimeoutMs(), petPackService)
                            : null;
            com.petsistemi.definition.editor.PetEditorSessionManager editorSessionManager =
                    new com.petsistemi.definition.editor.PetEditorSessionManager(
                            new com.petsistemi.definition.editor.PetDefinitionEditorService(plugin, definitionRegistry));

            com.petsistemi.config.RuntimeConfigurationSnapshot initialSnapshot = new com.petsistemi.config.RuntimeConfigurationSnapshot(
                    config,
                    messageService,
                    definitionRegistry,
                    System.currentTimeMillis()
            );
            java.util.concurrent.atomic.AtomicReference<com.petsistemi.config.RuntimeConfigurationSnapshot> configSnapshot = new java.util.concurrent.atomic.AtomicReference<>(initialSnapshot);

            // 5. Repositories, Cache & Audit
            PetRepository petRepository = backend == DatabaseBackend.MYSQL
                    ? new MysqlPetRepository(connectionProvider, plugin.getLogger())
                    : new SqlitePetRepository(connectionProvider, plugin.getLogger());
            PetSelectionRepository selectionRepository = backend == DatabaseBackend.MYSQL
                    ? new MysqlPetSelectionRepository(connectionProvider, plugin.getLogger())
                    : new SqlitePetSelectionRepository(connectionProvider, plugin.getLogger());
            com.petsistemi.network.JdbcPetNetworkEventStore networkEventStore =
                    config.ecosystem().network().enabled()
                            ? new com.petsistemi.network.JdbcPetNetworkEventStore(connectionProvider) : null;
            if (networkEventStore != null) {
                String serverId = config.ecosystem().network().serverId();
                com.petsistemi.network.MysqlNetworkLockManager networkLocks =
                        new com.petsistemi.network.MysqlNetworkLockManager(connectionProvider);
                petRepository = new com.petsistemi.network.NetworkAwarePetRepository(
                        petRepository, networkEventStore, serverId, networkLocks);
                selectionRepository = new com.petsistemi.network.NetworkAwarePetSelectionRepository(
                        selectionRepository, networkEventStore, serverId, networkLocks);
            }
            PlayerPetProfileCache profileCache = new PlayerPetProfileCache(petRepository, selectionRepository);
            AuditLogger auditLogger = new AuditLogger(connectionProvider, plugin.getLogger(), plugin.getDataFolder());

            // 6. Runtime Components & Operations
            PaperPetEntityController paperEntityController = new PaperPetEntityController(plugin);
            PetEntityController entityController = paperEntityController;
            PetBehaviorController behaviorController = new BasicPetBehaviorController(configSnapshot);
            ActivePetRegistry activePetRegistry = new ActivePetRegistry();

            // Modular representation / movement registries
            PetRepresentationRegistry representationRegistry = new PetRepresentationRegistry();
            PetMovementRegistry movementRegistry = new PetMovementRegistry();
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.ENTITY, paperEntityController);
            ItemDisplayPetRepresentation itemDisplayRepresentation = new ItemDisplayPetRepresentation(plugin, configSnapshot);
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.ITEM_DISPLAY, itemDisplayRepresentation);
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.BLOCK_DISPLAY, new BlockDisplayPetRepresentation(plugin, configSnapshot));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.TEXT_DISPLAY, new TextDisplayPetRepresentation(plugin, configSnapshot));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.PARTICLE, new ParticlePetRepresentation(plugin));
            InvisiblePetRepresentation invisibleRepresentation = new InvisiblePetRepresentation(plugin);
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.INVISIBLE, invisibleRepresentation);
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.MULTI_ENTITY, new MultiEntityPetRepresentation(plugin, configSnapshot));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.SPRITE,
                    new SpritePetRepresentation(itemDisplayRepresentation));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.PARTICLE_MODEL,
                    new ParticleModelPetRepresentation(invisibleRepresentation));
            com.petsistemi.runtime.model.ModelProviderRegistry modelProviderRegistry =
                    com.petsistemi.integration.model.ModelProviderBootstrap.registerAvailable(
                            plugin, representationRegistry, configSnapshot);
            CompositePetRepresentation compositeRepresentation = new CompositePetRepresentation(representationRegistry);
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.COMPOSITE,
                    compositeRepresentation);
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.DISPLAY_MODEL,
                    new DisplayModelPetRepresentation(compositeRepresentation));
            representationRegistry.register(com.petsistemi.domain.RuntimeRepresentationType.PROCEDURAL,
                    new ProceduralPetRepresentation(compositeRepresentation));
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
            movementRegistry.register(com.petsistemi.domain.PetMovementType.SWARM_CLOUD, new SwarmCloudMovement());

            PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(
                    plugin, definitionRegistry, activePetRegistry, entityController, behaviorController,
                    representationRegistry, movementRegistry
            );
            PetReactionEngine reactionEngine = new PetReactionEngine(configSnapshot);
            PetEmoteController emoteController = new PetEmoteController(reactionEngine);
            InteractionHitboxController hitboxController = new InteractionHitboxController(plugin);
            PetBuffController buffController = new PetBuffController(
                    configSnapshot.get().configuration().features().buffsEnabled(), reactionEngine.behaviorEngine());
            com.petsistemi.runtime.ability.PetAbilityEngine abilityEngine =
                    new com.petsistemi.runtime.ability.PetAbilityEngine(reactionEngine.behaviorEngine());
            com.petsistemi.runtime.ability.PetAbilityBindingController abilityBindings =
                    new com.petsistemi.runtime.ability.PetAbilityBindingController(
                            abilityEngine, activePetRegistry, definitionRegistry);

            PetIdleSleepController idleSleepController = new PetIdleSleepController(
                    configSnapshot, definitionRegistry, representationRegistry, reactionEngine);
            com.petsistemi.runtime.animation.PetAnimationStateMachine animationStateMachine =
                    new com.petsistemi.runtime.animation.PetAnimationStateMachine(representationRegistry);
            PetEvolutionController evolutionController = new PetEvolutionController(
                    definitionRegistry, representationRegistry);
            PetTransformController transformController = new PetTransformController(
                    definitionRegistry, representationRegistry, evolutionController);
            com.petsistemi.runtime.mount.PetMountController mountController =
                    new com.petsistemi.runtime.mount.PetMountController(
                            activePetRegistry, definitionRegistry, configSnapshot,
                            new com.petsistemi.runtime.mount.ReflectivePlayerMountInputProvider(plugin.getLogger()));
            coordinator.setEvolutionController(evolutionController);
            coordinator.setTransformController(transformController);
            idleSleepController.setTransformController(transformController);
            idleSleepController.setAnimationStateMachine(animationStateMachine);
            coordinator.setIdleSleepController(idleSleepController);
            coordinator.setAnimationStateMachine(animationStateMachine);
            coordinator.setEmoteController(emoteController);
            coordinator.setHitboxController(hitboxController);
            coordinator.setBuffController(buffController);
            coordinator.setMountController(mountController);

            PetRuntimeOperationService operationService = new PetRuntimeOperationService(
                    plugin, petRepository, selectionRepository, definitionRegistry, coordinator, profileCache, dbExecutor, mainThreadDispatcher
            );
            com.petsistemi.network.DefaultPetNetworkSyncService networkSyncService = networkEventStore != null
                    ? new com.petsistemi.network.DefaultPetNetworkSyncService(
                            config.ecosystem().network().serverId(), config.ecosystem().network().batchSize(),
                            config.ecosystem().network().retentionMillis(), networkEventStore, dbExecutor,
                            mainThreadDispatcher, profileCache, coordinator, operationService, plugin.getLogger())
                    : null;
            com.petsistemi.runtime.order.PetOrderEngine orderEngine =
                    new com.petsistemi.runtime.order.PetOrderEngine(activePetRegistry, definitionRegistry);
            com.petsistemi.runtime.order.BuiltInPetOrders.register(
                    orderEngine, activePetRegistry, operationService);

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
            com.petsistemi.runtime.item.PetItemActionEngine itemActionEngine =
                    new com.petsistemi.runtime.item.PetItemActionEngine();
            com.petsistemi.runtime.item.BuiltInPetItemActions.register(
                    itemActionEngine, experienceService, petService, operationService);
            com.petsistemi.runtime.item.PetUnlockItemController unlockItemController =
                    new com.petsistemi.runtime.item.PetUnlockItemController(
                            plugin, definitionRegistry, petService);

            // 8. PlaceholderAPI Integration
            if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    // Cache-backed on purpose: PAPI resolves on the main thread.
                    new com.petsistemi.integration.papi.PetPapiExpansion(plugin, profileCache).register();
                    plugin.getLogger().info("PlaceholderAPI entegrasyonu (PetPapiExpansion) başarıyla yüklendi.");
                } catch (Throwable t) {
                    plugin.getLogger().warning("PlaceholderAPI yüklenirken hata oluştu: " + t.getMessage());
                }
            }

            // 9. Task Registry
            TaskRegistry taskRegistry = new TaskRegistry();

            plugin.getLogger().info("PetSistemi önyükleme başarıyla tamamlandı.");

            AdminPersistenceService adminPersistenceService = new AdminPersistenceService(
                    dbExecutor, connectionProvider, plugin.getLogger(), backend);

            return new PetPluginContext(
                    plugin,
                    config,
                    messageService,
                    connectionProvider,
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
                    emoteController,
                    hitboxController,
                    buffController,
                    adminPersistenceService,
                    abilityEngine,
                    abilityBindings,
                    modelProviderRegistry,
                    editorSessionManager,
                    itemActionEngine,
                    orderEngine,
                    mountController,
                    unlockItemController,
                    networkSyncService,
                    petPackService,
                    marketplaceService
            );
        } catch (Throwable t) {
            plugin.getLogger().severe("PetSistemi önyüklemesi sırasında kritik hata oluştu! Kaynaklar temizleniyor: " + t.getMessage());
            if (dbExecutor != null) {
                try { dbExecutor.close(); } catch (Exception ignored) {}
            }
            if (connectionProvider != null) {
                try { connectionProvider.close(); } catch (Exception ignored) {}
            }
            throw (t instanceof RuntimeException re) ? re : new RuntimeException("Önyükleme başarısız.", t);
        }
    }
}
