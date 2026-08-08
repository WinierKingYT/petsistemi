package com.petsistemi.api.item;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record PetItemActionContext(
        Player player,
        UUID petId,
        PetDefinition petDefinition,
        Entity petEntity,
        ItemStack item
) {}
