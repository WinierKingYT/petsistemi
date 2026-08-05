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
        java.util.concurrent.atomic.AtomicReference<com.petsistemi.config.RuntimeConfigurationSnapshot> configSnapshot
) {}
