package com.petsistemi.api.mount;

import org.bukkit.entity.Player;

import java.util.UUID;

/** Main-thread Bukkit service for mounting and dismounting the owner's active pet. */
public interface PetMountService {
    PetMountResult toggleMount(Player player);
    PetMountResult dismount(Player player);
    boolean isMounted(UUID playerId);
}
