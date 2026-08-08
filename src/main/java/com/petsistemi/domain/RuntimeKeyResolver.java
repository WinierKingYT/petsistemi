package com.petsistemi.domain;

import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * The single compatibility seam for Bukkit names and MCPets' built-in runtime keys.
 * Keeping these mappings here makes a Minecraft version upgrade a small, auditable diff.
 */
public final class RuntimeKeyResolver {
    public static final String NAMESPACE = "petsistemi";

    private RuntimeKeyResolver() {}

    public static NamespacedKey movementKey(PetMovementType type) {
        PetMovementType resolved = type != null ? type : PetMovementType.GROUND_FOLLOW;
        return new NamespacedKey(NAMESPACE, resolved.name().toLowerCase(Locale.ROOT));
    }

    public static NamespacedKey representationKey(RuntimeRepresentationType type) {
        RuntimeRepresentationType resolved = type != null ? type : RuntimeRepresentationType.ENTITY;
        return new NamespacedKey(NAMESPACE, resolved.name().toLowerCase(Locale.ROOT));
    }

    public static PetMovementType builtInMovement(NamespacedKey key) {
        if (key == null || !NAMESPACE.equals(key.getNamespace())) return null;
        try { return PetMovementType.valueOf(key.getKey().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static RuntimeRepresentationType builtInRepresentation(NamespacedKey key) {
        if (key == null || !NAMESPACE.equals(key.getNamespace())) return null;
        try { return RuntimeRepresentationType.valueOf(key.getKey().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static PotionEffectType potionEffect(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // 1.20.4 registers FAST_DIGGING as HASTE; accept both spellings.
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("FAST_DIGGING".equals(normalized)) normalized = "HASTE";
        return PotionEffectType.getByName(normalized);
    }
}
