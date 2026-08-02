package com.petsistemi.bootstrap;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetService;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.persistence.ConnectionProvider;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetBehaviorController;
import com.petsistemi.runtime.PetEntityController;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.bukkit.plugin.java.JavaPlugin;

public record PetPluginContext(
        JavaPlugin plugin,
        ConnectionProvider connectionProvider,
        PetRepository petRepository,
        PetSelectionRepository selectionRepository,
        PetDefinitionRegistry definitionRegistry,
        ActivePetRegistry activePetRegistry,
        PetEntityController entityController,
        PetBehaviorController behaviorController,
        PetRuntimeCoordinator coordinator,
        PetService petService,
        PetExperienceService experienceService,
        TaskRegistry taskRegistry
) {}
