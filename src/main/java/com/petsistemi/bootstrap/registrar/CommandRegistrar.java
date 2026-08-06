package com.petsistemi.bootstrap.registrar;

import com.petsistemi.bootstrap.PetPluginContext;
import com.petsistemi.command.PetAdminCommand;
import com.petsistemi.command.PetCommand;

public final class CommandRegistrar {

    public static void register(PetPluginContext context) {
        PetCommand petCommand = new PetCommand(
                context.plugin(),
                context.operationService(),
                context.petService(),
                context.definitionRegistry(),
                context.profileCache(),
                context.activePetRegistry(),
                context.messageService(),
                context.configSnapshot(),
                context.emoteController()
        );
        if (context.plugin().getCommand("pet") != null) {
            context.plugin().getCommand("pet").setExecutor(petCommand);
            context.plugin().getCommand("pet").setTabCompleter(petCommand);
        }

        PetAdminCommand adminCommand = new PetAdminCommand(context);

        if (context.plugin().getCommand("petadmin") != null) {
            context.plugin().getCommand("petadmin").setExecutor(adminCommand);
            context.plugin().getCommand("petadmin").setTabCompleter(adminCommand);
        }
    }
}
