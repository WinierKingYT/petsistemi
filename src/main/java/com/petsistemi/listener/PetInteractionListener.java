package com.petsistemi.listener;

import com.petsistemi.api.PetService;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.gui.PetInspectMenu;
import com.petsistemi.message.MessageService;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.InteractionHitboxController;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Listens for player right-clicks on pet entities or interaction hitboxes.
 */
public class PetInteractionListener implements Listener {

    private final JavaPlugin plugin;
    private final PetService petService;
    private final ActivePetRegistry activePetRegistry;
    private final InteractionHitboxController hitboxController;
    private final PetDefinitionRegistry definitionRegistry;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final MessageService messageService;

    public PetInteractionListener(
            JavaPlugin plugin,
            PetService petService,
            ActivePetRegistry activePetRegistry,
            InteractionHitboxController hitboxController,
            PetDefinitionRegistry definitionRegistry,
            AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
            MessageService messageService
    ) {
        this.plugin = plugin;
        this.petService = petService;
        this.activePetRegistry = activePetRegistry;
        this.hitboxController = hitboxController;
        this.definitionRegistry = definitionRegistry;
        this.configSnapshot = configSnapshot;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player clicker = event.getPlayer();
        UUID entityId = event.getRightClicked().getUniqueId();

        UUID petId = null;

        // Check if entity is an interaction hitbox
        if (hitboxController != null) {
            petId = hitboxController.getPetIdFromHitbox(entityId);
        }

        // Check if entity is a active pet entity directly
        if (petId == null && activePetRegistry != null) {
            for (ActivePet pet : activePetRegistry.getAllActive()) {
                if (pet.getSpawnedEntity() != null && pet.getSpawnedEntity().getUniqueId().equals(entityId)) {
                    petId = pet.getPetId();
                    break;
                }
            }
        }

        if (petId != null) {
            event.setCancelled(true);
            ActivePet activePet = activePetRegistry != null ? activePetRegistry.getByOwner(clicker.getUniqueId()).orElse(null) : null;
            if (activePet != null && clicker.getUniqueId().equals(activePet.getOwnerId()) && clicker.isSneaking()) {
                PetDefinition def = definitionRegistry != null ? definitionRegistry.find(activePet.getDefinitionId()).orElse(null) : null;
                if (def != null && def.mount() != null && def.mount().enabled()) {
                    if (def.mount().permission() != null && !clicker.hasPermission(def.mount().permission())) {
                        clicker.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>Bu pete binme yetkiniz yok.</red>"));
                        return;
                    }
                    if (activePet.getSpawnedEntity() != null && activePet.getSpawnedEntity().isValid()) {
                        activePet.getSpawnedEntity().addPassenger(clicker);
                        clicker.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<green>Petinizin üzerine bindiniz! (İnmek için Shift'e basın)</green>"));
                    }
                    return;
                }
            }
            UUID ownerId = clicker.getUniqueId();
            PetInspectMenu.open(clicker, petService, petId, ownerId, plugin, definitionRegistry, configSnapshot, messageService);
        }
    }
}
