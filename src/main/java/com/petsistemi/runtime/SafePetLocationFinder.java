package com.petsistemi.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

public class SafePetLocationFinder {

    private static final Vector[] OFFSETS = {
            new Vector(-1, 0, -1), // Rear left
            new Vector(1, 0, -1),  // Rear right
            new Vector(0, 0, -2),  // 2 blocks back
            new Vector(-1.5, 0, 0),// Left
            new Vector(1.5, 0, 0), // Right
            new Vector(0, 0, 1)    // Front
    };

    public static Location findSafeLocation(Location center) {
        if (center == null || center.getWorld() == null) {
            return center;
        }

        // Try offsets around player
        for (Vector offset : OFFSETS) {
            Location candidate = center.clone().add(offset);
            if (isSafeLocation(candidate)) {
                return candidate;
            }
        }

        // Try Y-adjustments
        for (int y = -1; y <= 2; y++) {
            Location candidate = center.clone().add(0, y, 0);
            if (isSafeLocation(candidate)) {
                return candidate;
            }
        }

        return center;
    }

    public static boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        // Ground must be solid and not hazardous
        Material groundType = ground.getType();
        if (!groundType.isSolid() || groundType == Material.LAVA || groundType == Material.FIRE || groundType == Material.MAGMA_BLOCK) {
            return false;
        }

        // Feet and head space must be passable (air, water, flowers etc)
        return (feet.isPassable() || feet.getType() == Material.WATER) &&
               (head.isPassable() || head.getType() == Material.WATER);
    }
}
