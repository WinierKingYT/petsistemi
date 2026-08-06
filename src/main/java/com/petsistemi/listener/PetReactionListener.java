package com.petsistemi.listener;

import com.petsistemi.api.event.PetLevelUpEvent;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetReactionEngine;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Optional;

/**
 * Triggers pet reactions (sounds/particles at the pet) for owner damage and
 * level-ups. The engine is feature-gated via config; per-pet {@code reactions:}
 * definitions override the global defaults.
 */
public class PetReactionListener implements Listener {

    private static final double REACTION_RANGE_SQUARED = 8.0 * 8.0;

    private final ActivePetRegistry activeRegistry;
    private final PetReactionEngine reactionEngine;
    private final PetDefinitionRegistry definitionRegistry;

    public PetReactionListener(ActivePetRegistry activeRegistry, PetReactionEngine reactionEngine) {
        this(activeRegistry, reactionEngine, null);
    }

    public PetReactionListener(ActivePetRegistry activeRegistry, PetReactionEngine reactionEngine,
                               PetDefinitionRegistry definitionRegistry) {
        this.activeRegistry = activeRegistry;
        this.reactionEngine = reactionEngine;
        this.definitionRegistry = definitionRegistry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || reactionEngine == null) return;

        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(player.getUniqueId());
        if (activeOpt.isEmpty()) return;

        ActivePet active = activeOpt.get();
        Entity pet = active.getSpawnedEntity();
        if (pet == null || !pet.isValid() || pet.isDead()) return;
        if (pet.getWorld() == null || !pet.getWorld().equals(player.getWorld())) return;
        if (pet.getLocation().distanceSquared(player.getLocation()) > REACTION_RANGE_SQUARED) return;

        reactionEngine.playOwnerDamage(pet, resolveDefinition(active));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(PetLevelUpEvent event) {
        if (reactionEngine == null || event.getPetSnapshot() == null || event.getPetSnapshot().ownerId() == null) return;

        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(event.getPetSnapshot().ownerId());
        if (activeOpt.isEmpty()) return;

        ActivePet active = activeOpt.get();
        Entity pet = active.getSpawnedEntity();
        if (pet != null && pet.isValid() && !pet.isDead()) {
            reactionEngine.playLevelUp(pet, resolveDefinition(active));
        }
    }

    private PetDefinition resolveDefinition(ActivePet active) {
        if (definitionRegistry == null || active == null || active.getDefinitionId() == null) {
            return null;
        }
        return definitionRegistry.find(active.getDefinitionId()).orElse(null);
    }
}
