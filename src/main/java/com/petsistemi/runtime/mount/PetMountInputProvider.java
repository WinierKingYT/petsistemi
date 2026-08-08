package com.petsistemi.runtime.mount;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface PetMountInputProvider {
    PetMountInput read(Player player);
}
