package com.petsistemi.runtime.behavior;

import com.petsistemi.domain.PetReactionType;
import org.bukkit.NamespacedKey;

import java.util.Locale;

public final class BuiltInBehaviorKeys {
    public static final String NAMESPACE = "petsistemi";
    public static final NamespacedKey EMOTE = key("emote");
    public static final NamespacedKey PLAY_EFFECT = key("play_effect");
    public static final NamespacedKey TICK = key("tick");
    public static final NamespacedKey MIN_LEVEL = key("min_level");
    public static final NamespacedKey APPLY_POTION_EFFECT = key("apply_potion_effect");
    public static final NamespacedKey ABILITY = key("ability");
    public static final NamespacedKey LAUNCH_PROJECTILE = key("launch_projectile");
    public static final NamespacedKey AREA_POTION_EFFECT = key("area_potion_effect");
    public static final NamespacedKey DAMAGE_TARGETS = key("damage_targets");

    private BuiltInBehaviorKeys() {}

    public static NamespacedKey reaction(PetReactionType type) {
        return key(type.name().toLowerCase(Locale.ROOT));
    }

    private static NamespacedKey key(String value) { return new NamespacedKey(NAMESPACE, value); }
}
