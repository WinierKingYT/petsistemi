package com.petsistemi.bootstrap;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetService;
import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.gui.PlayerInputSessionManager;
import com.petsistemi.message.MessageService;
import com.petsistemi.persistence.*;
import com.petsistemi.runtime.*;
import org.bukkit.plugin.java.JavaPlugin;

public record PetPluginContext(
        JavaPlugin plugin,
        PluginConfiguration config,
        MessageService messageService,
        ConnectionProvider connectionProvider,
        DatabaseExecutor dbExecutor,
        MainThreadDispatcher mainThreadDispatcher,
        PetRepository petRepository,
        PetSelectionRepository selectionRepository,
        PlayerPetProfileCache profileCache,
        AuditLogger auditLogger,
        PetDefinitionRegistry definitionRegistry,
        ActivePetRegistry activePetRegistry,
        PetEntityController entityController,
        PetBehaviorController behaviorController,
        PetRuntimeCoordinator coordinator,
        PetRuntimeOperationService operationService,
        PetService petService,
        PetExperienceService experienceService,
        PlayerInputSessionManager sessionManager,
        TaskRegistry taskRegistry,
        java.util.concurrent.atomic.AtomicReference<com.petsistemi.config.RuntimeConfigurationSnapshot> configSnapshot,
        PetReactionEngine reactionEngine,
        PetEmoteController emoteController,
        InteractionHitboxController hitboxController,
        PetBuffController buffController,
        AdminPersistenceService adminPersistenceService,
        com.petsistemi.runtime.ability.PetAbilityEngine abilityEngine,
        com.petsistemi.runtime.ability.PetAbilityBindingController abilityBindingController,
        com.petsistemi.runtime.model.ModelProviderRegistry modelProviderRegistry,
        com.petsistemi.definition.editor.PetEditorSessionManager editorSessionManager,
        com.petsistemi.runtime.item.PetItemActionEngine itemActionEngine,
        com.petsistemi.runtime.order.PetOrderEngine orderEngine,
        com.petsistemi.runtime.mount.PetMountController mountController,
        com.petsistemi.runtime.item.PetUnlockItemController unlockItemController,
        com.petsistemi.network.DefaultPetNetworkSyncService networkSyncService,
        com.petsistemi.pack.DefaultPetPackService petPackService,
        com.petsistemi.marketplace.DefaultPetMarketplaceService marketplaceService
) {
    /** Backward-compatible overload retaining the former full context signature. */
    public PetPluginContext(
            JavaPlugin plugin, PluginConfiguration config, MessageService messageService,
            ConnectionProvider connectionProvider, DatabaseExecutor dbExecutor,
            MainThreadDispatcher mainThreadDispatcher, PetRepository petRepository,
            PetSelectionRepository selectionRepository, PlayerPetProfileCache profileCache,
            AuditLogger auditLogger, PetDefinitionRegistry definitionRegistry,
            ActivePetRegistry activePetRegistry, PetEntityController entityController,
            PetBehaviorController behaviorController, PetRuntimeCoordinator coordinator,
            PetRuntimeOperationService operationService, PetService petService,
            PetExperienceService experienceService, PlayerInputSessionManager sessionManager,
            TaskRegistry taskRegistry,
            java.util.concurrent.atomic.AtomicReference<com.petsistemi.config.RuntimeConfigurationSnapshot> configSnapshot,
            PetReactionEngine reactionEngine, PetEmoteController emoteController,
            InteractionHitboxController hitboxController, PetBuffController buffController,
            AdminPersistenceService adminPersistenceService
    ) {
        this(plugin, config, messageService, connectionProvider, dbExecutor, mainThreadDispatcher,
                petRepository, selectionRepository, profileCache, auditLogger, definitionRegistry,
                activePetRegistry, entityController, behaviorController, coordinator, operationService,
                petService, experienceService, sessionManager, taskRegistry, configSnapshot, reactionEngine,
                emoteController, hitboxController, buffController, adminPersistenceService, null, null, null, null, null, null, null, null, null, null, null);
    }
    public PetPluginContext(
            JavaPlugin plugin,
            PluginConfiguration config,
            MessageService messageService,
            ConnectionProvider connectionProvider,
            DatabaseExecutor dbExecutor,
            MainThreadDispatcher mainThreadDispatcher,
            PetRepository petRepository,
            PetSelectionRepository selectionRepository,
            PlayerPetProfileCache profileCache,
            AuditLogger auditLogger,
            PetDefinitionRegistry definitionRegistry,
            ActivePetRegistry activePetRegistry,
            PetEntityController entityController,
            PetBehaviorController behaviorController,
            PetRuntimeCoordinator coordinator,
            PetRuntimeOperationService operationService,
            PetService petService,
            PetExperienceService experienceService,
            PlayerInputSessionManager sessionManager,
            TaskRegistry taskRegistry,
            java.util.concurrent.atomic.AtomicReference<com.petsistemi.config.RuntimeConfigurationSnapshot> configSnapshot,
            PetReactionEngine reactionEngine,
            PetEmoteController emoteController
    ) {
        this(plugin, config, messageService, connectionProvider, dbExecutor, mainThreadDispatcher,
                petRepository, selectionRepository, profileCache, auditLogger, definitionRegistry,
                activePetRegistry, entityController, behaviorController, coordinator, operationService,
                petService, experienceService, sessionManager, taskRegistry, configSnapshot, reactionEngine,
                emoteController, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
