package com.petsistemi.config;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.message.MessageService;

public record RuntimeConfigurationSnapshot(
        PluginConfiguration configuration,
        MessageService messageService,
        PetDefinitionRegistry definitionRegistry,
        long loadedAtTimestamp
) {
}
