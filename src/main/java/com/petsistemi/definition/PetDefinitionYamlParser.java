package com.petsistemi.definition;

import com.petsistemi.domain.PetAnchorDefinition;
import com.petsistemi.domain.PetAnchorPosition;
import com.petsistemi.domain.PetBuffDefinition;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetEmoteDefinition;
import com.petsistemi.domain.PetEvolutionDefinition;
import com.petsistemi.domain.PetHitboxDefinition;
import com.petsistemi.domain.PetIdleAnimation;
import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.PetOrbitDefinition;
import com.petsistemi.domain.PetOwnerState;
import com.petsistemi.domain.PetPersonalityType;
import com.petsistemi.domain.PetReactionDefinition;
import com.petsistemi.domain.PetReactionType;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetStateDefinition;
import com.petsistemi.domain.PetStatesDefinition;
import com.petsistemi.domain.PetTimeOfDay;
import com.petsistemi.domain.PetTransformCondition;
import com.petsistemi.domain.PetTransformDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.PetVisualOverride;
import com.petsistemi.domain.PetWeather;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses a single pets/*.yml file into a {@link PetDefinition}.
 *
 * <p>Backward compatible: legacy flat keys ({@code entity-type}, {@code baby}, ...) are still
 * accepted and map to an {@link RuntimeRepresentationType#ENTITY} pet with
 * {@link PetMovementType#GROUND_FOLLOW}. When a nested {@code representation}/{@code movement}
 * section exists, it takes precedence.</p>
 */
public final class PetDefinitionYamlParser {

    private PetDefinitionYamlParser() {}

    public record Parsed(PetDefinition definition, List<String> errors) {}

    public static Parsed parse(String id, YamlConfiguration yaml) {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank() || yaml == null) {
            errors.add("Pet tanımı geçersiz (id veya yaml null).");
            return new Parsed(null, errors);
        }
        try {
            String displayName = yaml.getString("display-name", id);
            List<String> description = yaml.getStringList("description");

            PetRepresentationDefinition representation = parseRepresentation(yaml, errors);
            PetMovementDefinition movement = parseMovement(yaml, representation, errors);
            PetStatesDefinition states = parseStates(yaml, errors);
            List<PetTransformDefinition> transforms = parseTransforms(yaml, errors);
            Map<PetReactionType, PetReactionDefinition> reactions = parseReactions(yaml, errors);
            Map<String, PetEmoteDefinition> emotes = parseEmotes(yaml, errors);
            List<PetBuffDefinition> buffs = parseBuffs(yaml, errors);
            PetPersonalityType personality = parsePersonality(yaml, errors);
            List<PetEvolutionDefinition> evolutions = parseEvolutions(yaml, errors);
            PetHitboxDefinition hitbox = parseHitbox(yaml, errors);
            List<com.petsistemi.domain.PetLevelRewardDefinition> levelRewards = parseLevelRewards(yaml, errors);
            List<com.petsistemi.domain.PetFollowMode> allowedModes = parseAllowedModes(yaml, errors);
            com.petsistemi.domain.PetSpawnStyleDefinition spawnStyle = parseSpawnStyle(yaml, errors);
            com.petsistemi.domain.PetMountDefinition mount = parseMount(yaml, errors);
            com.petsistemi.domain.PetPresenceDefinition presence = parsePresence(yaml, errors);

            PetDefinition def = new PetDefinition(
                    id,
                    displayName,
                    description,
                    representation.entityType() != null ? representation.entityType() : "WOLF",
                    representation.baby(),
                    representation.glowing(),
                    representation.invulnerable(),
                    representation.silent(),
                    representation.gravity(),
                    yaml.getBoolean("progression.enabled", true),
                    yaml.getInt("progression.maximum-level", 100),
                    yaml.getBoolean("nameplate.enabled", true),
                    nameplateFormat(yaml),
                    representation,
                    movement,
                    states,
                    transforms,
                    reactions,
                    emotes,
                    trimToNull(yaml.getString("gui-material")),
                    trimToNull(yaml.getString("permission")),
                    buffs,
                    personality,
                    evolutions,
                    hitbox,
                    levelRewards,
                    allowedModes,
                    spawnStyle,
                    mount,
                    presence
            );
            return new Parsed(def, errors);
        } catch (Exception e) {
            errors.add("Ayrıştırma hatası: " + e.getMessage());
            return new Parsed(null, errors);
        }
    }

    private static List<String> nameplateFormat(YamlConfiguration yaml) {
        List<String> format = yaml.getStringList("nameplate.format");
        if (format == null || format.isEmpty()) {
            return List.of("<gradient:#ffaa00:#ff5500>{pet_name}</gradient> <gray>Lv.{level}</gray>");
        }
        return format;
    }

    private static PetRepresentationDefinition parseRepresentation(YamlConfiguration yaml, List<String> errors) {
        boolean hasRepresentation = yaml.isConfigurationSection("representation");

        String repTypeStr = hasRepresentation
                ? yaml.getString("representation.type", "ENTITY")
                : "ENTITY";
        RuntimeRepresentationType repType = parseEnum(repTypeStr, RuntimeRepresentationType.class, errors, "representation.type");

        String entityType = (hasRepresentation ? yaml.getString("representation.entity-type") : null);
        if (entityType == null || entityType.isBlank()) {
            entityType = yaml.getString("entity-type", "WOLF");
        }

        boolean baby = boolOr(yaml, "representation.baby", "baby", false);
        boolean glowing = boolOr(yaml, "representation.glowing", "glowing", false);
        boolean invulnerable = boolOr(yaml, "representation.invulnerable", "invulnerable", true);
        boolean silent = boolOr(yaml, "representation.silent", "silent", false);
        boolean gravity = boolOr(yaml, "representation.gravity", "gravity", true);

        // BLOCK_DISPLAY pets may spell the material `block-material` (mirroring the
        // transforms[].apply.representation override schema); both feed the same field.
        String itemMaterial = hasRepresentation ? yaml.getString("representation.item-material") : null;
        if (itemMaterial == null && hasRepresentation) {
            itemMaterial = yaml.getString("representation.block-material");
        }
        Integer customModelData = null;
        if (hasRepresentation && yaml.isSet("representation.custom-model-data")) {
            customModelData = yaml.getInt("representation.custom-model-data");
        }
        PetVector3 scale = parseVector(yaml, "representation.scale");

        String particleType = hasRepresentation ? yaml.getString("representation.particle-type") : null;
        int particleCount = Math.max(0, yaml.getInt("representation.particle-count", 0));
        double particleOffset = yaml.getDouble("representation.particle-offset", 0.0);
        double particleSpeed = yaml.getDouble("representation.particle-speed", 0.0);
        int childCount = Math.max(0, yaml.getInt("representation.child-count", 0));
        String childMaterial = hasRepresentation ? yaml.getString("representation.child-material") : null;

        return new PetRepresentationDefinition(
                repType, entityType, baby, glowing, invulnerable, silent, gravity,
                itemMaterial, customModelData, scale != null ? scale : PetVector3.ONE,
                particleType, particleCount, particleOffset, particleSpeed, childCount, childMaterial);
    }

    private static PetMovementDefinition parseMovement(YamlConfiguration yaml,
                                                       PetRepresentationDefinition representation,
                                                       List<String> errors) {
        boolean hasMovement = yaml.isConfigurationSection("movement");
        if (!hasMovement) {
            // Legacy entity pets keep the old config-driven ground follow behavior (null = defaults).
            if (representation.type() == RuntimeRepresentationType.ENTITY) {
                return null;
            }
            // Display pets default to a gentle flying follow so they never block the owner.
            return PetMovementDefinition.flying(0.0, 0, 1.5, 1.1, 0.18);
        }

        String movTypeStr = yaml.getString("movement.type", "GROUND_FOLLOW");
        PetMovementType movType = parseEnum(movTypeStr, PetMovementType.class, errors, "movement.type");

        double followDistance = yaml.getDouble("movement.follow-distance", 0.0);
        double teleportDistance = yaml.getDouble("movement.teleport-distance", 0.0);
        int updateIntervalTicks = yaml.getInt("movement.update-interval-ticks", 0);
        double height = yaml.getDouble("movement.height", 0.0);
        double sideOffset = yaml.getDouble("movement.side-offset", 0.0);
        double followSpeed = yaml.getDouble("movement.follow-speed", 0.0);
        int delayTicks = yaml.getInt("movement.delay-ticks", 0);

        PetOrbitDefinition orbit = null;
        if (yaml.isConfigurationSection("movement.orbit")) {
            double radius = yaml.getDouble("movement.orbit.radius", 1.7);
            double orbitHeight = yaml.getDouble("movement.orbit.height", 1.4);
            double angularSpeed = yaml.getDouble("movement.orbit.angular-speed", 1.2);
            boolean clockwise = !"COUNTER_CLOCKWISE".equalsIgnoreCase(yaml.getString("movement.orbit.direction", "CLOCKWISE"));
            orbit = new PetOrbitDefinition(radius, orbitHeight, angularSpeed, clockwise);
        }

        PetAnchorDefinition anchor = null;
        if (yaml.isConfigurationSection("movement.anchor")) {
            String positionStr = yaml.getString("movement.anchor.position", "BEHIND_RIGHT");
            PetAnchorPosition position = parseEnum(positionStr, PetAnchorPosition.class, errors, "movement.anchor.position");
            double anchorDistance = yaml.getDouble("movement.anchor.distance", 1.8);
            double anchorHeight = yaml.getDouble("movement.anchor.height", 0.4);
            boolean rotateWithOwner = yaml.getBoolean("movement.anchor.rotate-with-owner", true);
            anchor = new PetAnchorDefinition(position != null ? position : PetAnchorPosition.BEHIND_RIGHT,
                    anchorDistance, anchorHeight, rotateWithOwner);
        }

        return new PetMovementDefinition(movType, followDistance, teleportDistance, updateIntervalTicks,
                height, sideOffset, followSpeed, orbit, anchor, delayTicks);
    }

    private static Map<PetReactionType, PetReactionDefinition> parseReactions(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isConfigurationSection("reactions")) {
            return null;
        }
        Map<PetReactionType, PetReactionDefinition> reactions = new java.util.LinkedHashMap<>();
        for (String key : yaml.getConfigurationSection("reactions").getKeys(false)) {
            String base = "reactions." + key;
            if (!yaml.isConfigurationSection(base)) {
                continue;
            }
            PetReactionType type = parseEnum(key, PetReactionType.class, errors, "reactions." + key);
            if (type == null) {
                continue;
            }
            boolean enabled = yaml.getBoolean(base + ".enabled", true);
            String sound = yaml.getString(base + ".sound");
            String particle = yaml.getString(base + ".particle");
            int particleCount = Math.max(0, yaml.getInt(base + ".particle-count", 0));
            double volume = yaml.getDouble(base + ".volume", 0.0);
            reactions.put(type, new PetReactionDefinition(enabled, sound, particle, particleCount, volume));
        }
        return reactions.isEmpty() ? null : reactions;
    }

    private static Map<String, PetEmoteDefinition> parseEmotes(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isConfigurationSection("emotes")) {
            return null;
        }
        Map<String, PetEmoteDefinition> emotes = new java.util.LinkedHashMap<>();
        for (String key : yaml.getConfigurationSection("emotes").getKeys(false)) {
            String base = "emotes." + key;
            if (!yaml.isConfigurationSection(base)) {
                continue;
            }
            boolean enabled = yaml.getBoolean(base + ".enabled", true);
            String sound = yaml.getString(base + ".sound");
            String particle = yaml.getString(base + ".particle");
            int particleCount = Math.max(0, yaml.getInt(base + ".particle-count", 0));
            int cooldownSeconds = Math.max(0, yaml.getInt(base + ".cooldown-seconds", 0));
            emotes.put(key.toLowerCase(java.util.Locale.ROOT), new PetEmoteDefinition(enabled, sound, particle, particleCount, cooldownSeconds));
        }
        return emotes.isEmpty() ? null : emotes;
    }

    private static List<PetTransformDefinition> parseTransforms(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isConfigurationSection("transforms")) {
            return null;
        }
        List<PetTransformDefinition> transforms = new ArrayList<>();
        List<String> keys = new ArrayList<>(yaml.getConfigurationSection("transforms").getKeys(false));
        java.util.Collections.sort(keys);
        for (String key : keys) {
            String base = "transforms." + key;
            if (!yaml.isConfigurationSection(base)) {
                continue;
            }
            PetTransformCondition condition = null;
            if (yaml.isConfigurationSection(base + ".when")) {
                PetOwnerState ownerState = parseEnum(yaml.getString(base + ".when.owner-state"),
                        PetOwnerState.class, errors, "transforms." + key + ".when.owner-state");
                String biome = yaml.getString(base + ".when.biome");
                String world = yaml.getString(base + ".when.world");
                PetTimeOfDay timeOfDay = parseEnum(yaml.getString(base + ".when.time-of-day"),
                        PetTimeOfDay.class, errors, "transforms." + key + ".when.time-of-day");
                PetWeather weather = parseEnum(yaml.getString(base + ".when.weather"),
                        PetWeather.class, errors, "transforms." + key + ".when.weather");
                Integer minY = yaml.isSet(base + ".when.min-y") ? yaml.getInt(base + ".when.min-y") : null;
                Integer maxY = yaml.isSet(base + ".when.max-y") ? yaml.getInt(base + ".when.max-y") : null;
                Integer minLight = yaml.isSet(base + ".when.min-light") ? yaml.getInt(base + ".when.min-light") : null;
                Integer maxLight = yaml.isSet(base + ".when.max-light") ? yaml.getInt(base + ".when.max-light") : null;
                condition = new PetTransformCondition(ownerState, biome, world, timeOfDay, weather, minY, maxY, minLight, maxLight);
            }

            PetVisualOverride apply = null;
            if (yaml.isConfigurationSection(base + ".apply.representation")) {
                String repBase = base + ".apply.representation";
                String itemMaterial = yaml.getString(repBase + ".item-material");
                String blockMaterial = yaml.getString(repBase + ".block-material");
                Integer customModelData = yaml.isSet(repBase + ".custom-model-data") ? yaml.getInt(repBase + ".custom-model-data") : null;
                PetVector3 scale = parseVector(yaml, repBase + ".scale");
                String particleType = yaml.getString(repBase + ".particle-type");
                Integer particleCount = yaml.isSet(repBase + ".particle-count") ? Math.max(0, yaml.getInt(repBase + ".particle-count")) : null;
                Boolean glowing = yaml.isSet(repBase + ".glowing") ? yaml.getBoolean(repBase + ".glowing") : null;
                Boolean baby = yaml.isSet(repBase + ".baby") ? yaml.getBoolean(repBase + ".baby") : null;
                String entityType = yaml.getString(repBase + ".entity-type");
                apply = new PetVisualOverride(itemMaterial, blockMaterial, customModelData, scale,
                        particleType, particleCount, glowing, baby, entityType);
            }
            transforms.add(new PetTransformDefinition(condition, apply));
        }
        return transforms.isEmpty() ? null : transforms;
    }

    private static PetStatesDefinition parseStates(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isConfigurationSection("states")) {
            return null;
        }
        PetStateDefinition moving = null;
        if (yaml.isConfigurationSection("states.MOVING")) {
            PetIdleAnimation animation = parseEnum(yaml.getString("states.MOVING.animation"), PetIdleAnimation.class,
                    errors, "states.MOVING.animation");
            moving = new PetStateDefinition(0, animation);
        }
        PetStateDefinition idle = null;
        if (yaml.isConfigurationSection("states.IDLE")) {
            int afterTicks = Math.max(0, yaml.getInt("states.IDLE.after-ticks", 0));
            PetIdleAnimation animation = parseEnum(yaml.getString("states.IDLE.animation"), PetIdleAnimation.class,
                    errors, "states.IDLE.animation");
            idle = new PetStateDefinition(afterTicks, animation);
        }
        return new PetStatesDefinition(moving, idle);
    }

    private static List<PetBuffDefinition> parseBuffs(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isList("buffs") && !yaml.isConfigurationSection("buffs")) {
            return null;
        }
        List<PetBuffDefinition> buffs = new ArrayList<>();
        List<Map<?, ?>> list = yaml.getMapList("buffs");
        for (Map<?, ?> map : list) {
            String effectStr = map.get("effect") != null ? map.get("effect").toString() : null;
            if (effectStr == null || effectStr.isBlank()) {
                continue;
            }
            PotionEffectType type = PotionEffectType.getByName(effectStr.toUpperCase(java.util.Locale.ROOT).trim());
            if (type == null) {
                errors.add("Geçersiz buff etki türü: " + effectStr);
                continue;
            }
            int amp = map.get("amplifier") instanceof Number n ? n.intValue() : 0;
            int minLvl = map.get("min-level") instanceof Number n ? n.intValue() : 1;
            int duration = map.get("duration-ticks") instanceof Number n ? n.intValue() : 60;
            buffs.add(new PetBuffDefinition(type, amp, minLvl, duration));
        }
        return buffs.isEmpty() ? null : buffs;
    }

    private static PetPersonalityType parsePersonality(YamlConfiguration yaml, List<String> errors) {
        String raw = yaml.getString("personality");
        if (raw == null || raw.isBlank()) {
            return PetPersonalityType.DEFAULT;
        }
        return PetPersonalityType.fromString(raw);
    }

    private static List<PetEvolutionDefinition> parseEvolutions(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isList("evolutions")) {
            return null;
        }
        List<PetEvolutionDefinition> evolutions = new ArrayList<>();
        List<Map<?, ?>> list = yaml.getMapList("evolutions");
        for (Map<?, ?> map : list) {
            int minLvl = map.get("min-level") instanceof Number n ? n.intValue() : 1;
            String targetId = map.get("target-id") != null ? map.get("target-id").toString() : null;
            String nameOverride = map.get("display-name") != null ? map.get("display-name").toString() : null;
            PetVector3 scale = null;
            if (map.get("scale") instanceof Map<?, ?> scaleMap) {
                double x = scaleMap.get("x") instanceof Number n ? n.doubleValue() : 1.0;
                double y = scaleMap.get("y") instanceof Number n ? n.doubleValue() : 1.0;
                double z = scaleMap.get("z") instanceof Number n ? n.doubleValue() : 1.0;
                scale = new PetVector3(x, y, z);
            }
            evolutions.add(new PetEvolutionDefinition(minLvl, targetId, nameOverride, scale));
        }
        return evolutions.isEmpty() ? null : evolutions;
    }

    private static PetHitboxDefinition parseHitbox(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isConfigurationSection("hitbox")) {
            return PetHitboxDefinition.DEFAULT;
        }
        boolean enabled = yaml.getBoolean("hitbox.enabled", true);
        float width = (float) yaml.getDouble("hitbox.width", 0.6);
        float height = (float) yaml.getDouble("hitbox.height", 0.8);
        return new PetHitboxDefinition(enabled, width, height);
    }

    private static List<com.petsistemi.domain.PetLevelRewardDefinition> parseLevelRewards(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isList("progression.level-rewards")) {
            return null;
        }
        List<com.petsistemi.domain.PetLevelRewardDefinition> rewards = new ArrayList<>();
        List<Map<?, ?>> list = yaml.getMapList("progression.level-rewards");
        for (Map<?, ?> map : list) {
            int level = map.get("level") instanceof Number n ? n.intValue() : 1;
            List<String> cmds = new ArrayList<>();
            if (map.get("commands") instanceof List<?> cList) {
                for (Object o : cList) {
                    if (o != null) cmds.add(o.toString());
                }
            }
            String msg = map.get("message") != null ? map.get("message").toString() : null;
            rewards.add(new com.petsistemi.domain.PetLevelRewardDefinition(level, cmds, msg));
        }
        return rewards.isEmpty() ? null : rewards;
    }

    private static List<com.petsistemi.domain.PetFollowMode> parseAllowedModes(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isList("allowed-modes")) {
            return null;
        }
        List<com.petsistemi.domain.PetFollowMode> modes = new ArrayList<>();
        List<String> list = yaml.getStringList("allowed-modes");
        for (String str : list) {
            com.petsistemi.domain.PetFollowMode mode = parseEnum(str, com.petsistemi.domain.PetFollowMode.class, errors, "allowed-modes");
            if (mode != null) modes.add(mode);
        }
        return modes.isEmpty() ? null : modes;
    }

    private static com.petsistemi.domain.PetSpawnStyleDefinition parseSpawnStyle(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isConfigurationSection("spawn-style")) {
            return com.petsistemi.domain.PetSpawnStyleDefinition.DEFAULT;
        }
        String type = yaml.getString("spawn-style.type", "PORTAL");
        String entryParticle = yaml.getString("spawn-style.entry-particle", "PORTAL");
        int entryCount = yaml.getInt("spawn-style.entry-particle-count", 25);
        String entrySound = yaml.getString("spawn-style.entry-sound", "ENTITY_ENDERMAN_TELEPORT");
        String exitParticle = yaml.getString("spawn-style.exit-particle", "SMOKE_LARGE");
        int exitCount = yaml.getInt("spawn-style.exit-particle-count", 20);
        String exitSound = yaml.getString("spawn-style.exit-sound", "ENTITY_ITEM_BREAK");
        return new com.petsistemi.domain.PetSpawnStyleDefinition(type, entryParticle, entryCount, entrySound, exitParticle, exitCount, exitSound);
    }

    private static com.petsistemi.domain.PetMountDefinition parseMount(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isConfigurationSection("mount")) {
            return com.petsistemi.domain.PetMountDefinition.DISABLED;
        }
        boolean enabled = yaml.getBoolean("mount.enabled", false);
        String perm = yaml.getString("mount.permission");
        double speed = yaml.getDouble("mount.speed-multiplier", 1.2);
        boolean allowFly = yaml.getBoolean("mount.allow-fly", false);
        return new com.petsistemi.domain.PetMountDefinition(enabled, perm, speed, allowFly);
    }

    private static com.petsistemi.domain.PetPresenceDefinition parsePresence(YamlConfiguration yaml, List<String> errors) {
        if (!yaml.isConfigurationSection("presence")) {
            return com.petsistemi.domain.PetPresenceDefinition.ALWAYS;
        }
        String mode = yaml.getString("presence.mode", "ALWAYS");
        List<String> triggers = yaml.getStringList("presence.triggers");
        int duration = yaml.getInt("presence.visible-duration-seconds", 10);
        return new com.petsistemi.domain.PetPresenceDefinition(mode, triggers, duration);
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean boolOr(YamlConfiguration yaml, String primary, String legacy, boolean fallback) {
        if (yaml.isSet(primary)) {
            return yaml.getBoolean(primary, fallback);
        }
        return yaml.getBoolean(legacy, fallback);
    }
    private static PetVector3 parseVector(YamlConfiguration yaml, String path) {
        if (!yaml.isConfigurationSection(path)) {
            return null;
        }
        double x = yaml.getDouble(path + ".x", 0.0);
        double y = yaml.getDouble(path + ".y", 0.0);
        double z = yaml.getDouble(path + ".z", 0.0);
        return new PetVector3(x, y, z);
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> enumType, List<String> errors, String path) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add("Geçersiz " + path + ": '" + raw + "'. Desteklenen değerler: " + enumNames(enumType));
            return null;
        }
    }

    private static <E extends Enum<E>> String enumNames(Class<E> enumType) {
        List<String> names = new ArrayList<>();
        for (E constant : enumType.getEnumConstants()) {
            names.add(constant.name());
        }
        return String.join(", ", names);
    }
}
