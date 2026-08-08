package com.petsistemi.api.order;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Immutable snapshot supplied to a registered pet order handler. */
public record PetOrderContext(
        Player player,
        UUID petId,
        PetDefinition petDefinition,
        Entity petEntity,
        List<Entity> petEntities
) {
    public PetOrderContext {
        petEntities = petEntities == null ? List.of() : List.copyOf(petEntities);
    }
}
