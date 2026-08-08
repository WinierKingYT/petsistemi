package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetEmoteDefinition;
import com.petsistemi.domain.PetEvolutionDefinition;
import com.petsistemi.domain.PetIdleAnimation;
import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.PetReactionDefinition;
import com.petsistemi.domain.PetReactionType;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetStatesDefinition;
import com.petsistemi.domain.PetTransformDefinition;
import com.petsistemi.domain.PetVisualOverride;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.behavior.BehaviorActionDefinition;
import com.petsistemi.domain.behavior.BehaviorConditionDefinition;
import com.petsistemi.domain.behavior.PetBehaviorDefinition;
import com.petsistemi.runtime.behavior.BuiltInBehaviorKeys;
import com.petsistemi.domain.ability.PetAbilityDefinition;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class PetDefinitionValidator {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,63}(?::[a-z0-9][a-z0-9._-]{1,63})?$");
    private static final Pattern EMOTE_NAME_PATTERN = Pattern.compile("^([a-z0-9_-]+)(,[a-z0-9_-]+)*$");

    private PetDefinitionValidator() {}

    public static List<String> validate(PetDefinition def, int schemaVersion) {
        List<String> errors = new ArrayList<>();

        if (def == null) {
            errors.add("PetDefinition null olamaz.");
            return errors;
        }

        if (schemaVersion != 1) {
            errors.add("Bilinmeyen schema-version: " + schemaVersion + " (Beklenen: 1)");
        }

        if (def.id() == null || !ID_PATTERN.matcher(def.id()).matches()) {
            errors.add("Geçersiz pet ID formatı: '" + def.id() + "'. Küçük harfli yerel-id veya namespace:yerel-id bekleniyor.");
        }

        if (def.displayName() == null || def.displayName().trim().isEmpty()) {
            errors.add("Görünen isim (displayName) boş olamaz.");
        }

        if (def.guiMaterial() != null && Material.matchMaterial(def.guiMaterial()) == null) {
            errors.add("Geçersiz gui-material: " + def.guiMaterial() + ". Geçerli bir Minecraft item adı olmalıdır.");
        }

        validateRepresentation(def, errors);
        validateMovement(def, errors);
        validateEvolutions(def, errors);
        validateStates(def, errors);
        validateTransforms(def, errors);
        validateReactionsAndEmotes(def, errors);
        validateBehaviors(def, errors);
        validateAbilities(def, errors);
        validateItemActions(def, errors);
        validateMount(def, errors);

        if (def.maxLevel() <= 0) {
            errors.add("Maksimum seviye (maxLevel) 0'dan büyük olmalıdır.");
        }

        return errors;
    }

    private static void validateMount(PetDefinition def, List<String> errors) {
        com.petsistemi.domain.PetMountDefinition mount = def.mount();
        if (mount == null) return;
        if (!Double.isFinite(mount.speedMultiplier())
                || mount.speedMultiplier() < 0.1D || mount.speedMultiplier() > 3.0D) {
            errors.add("mount.speed-multiplier 0.1 ile 3.0 arasında olmalıdır.");
        }
        if (mount.permission() != null && mount.permission().isBlank()) {
            errors.add("mount.permission boş olamaz.");
        }
    }

    private static void validateRepresentation(PetDefinition def, List<String> errors) {
        PetRepresentationDefinition rep = def.representation();
        if (rep == null || rep.type() == null) {
            rep = PetRepresentationDefinition.legacyEntity(def.entityType(), def.baby(), def.glowing(),
                    def.invulnerable(), def.silent(), def.gravity());
        }

        switch (rep.type()) {
            case ENTITY -> validateEntityType(rep.entityType(), errors);
            case ITEM_DISPLAY -> validateItemMaterial(rep.itemMaterial(), errors);
            case BLOCK_DISPLAY -> {
                if (rep.itemMaterial() == null) {
                    errors.add("BLOCK_DISPLAY için representation.item-material zorunludur.");
                } else {
                    Material block = Material.matchMaterial(rep.itemMaterial());
                    if (block == null || !block.isBlock()) {
                        errors.add("Geçersiz BLOCK_DISPLAY material: " + rep.itemMaterial() + ". Geçerli bir blok adı olmalıdır.");
                    }
                }
            }
            case TEXT_DISPLAY -> {
                // No material required; the text is rendered from the pet name.
            }
            case PARTICLE -> {
                if (rep.particleType() == null || parseParticle(rep.particleType()) == null) {
                    errors.add("PARTICLE için geçerli bir representation.particle-type gerekli: " + rep.particleType());
                }
                if (rep.particleCount() <= 0 || rep.particleCount() > 500) {
                    errors.add("representation.particle-count 1-500 aralığında olmalıdır (mevcut: " + rep.particleCount() + ").");
                }
            }
            case INVISIBLE -> {
                // Deliberately no visible representation; fine.
            }
            case MULTI_ENTITY -> {
                if (rep.childCount() <= 0 || rep.childCount() > 8) {
                    errors.add("MULTI_ENTITY için representation.child-count 1-8 aralığında olmalıdır (mevcut: " + rep.childCount() + ").");
                }
                if (rep.childMaterial() != null && Material.matchMaterial(rep.childMaterial()) == null) {
                    errors.add("Geçersiz MULTI_ENTITY child-material: " + rep.childMaterial());
                }
                validateItemMaterial(rep.itemMaterial(), errors);
            }
            default -> errors.add("Bu sürümde desteklenmeyen representation türü: " + rep.type());
        }

        if (rep.scale() != null && !rep.scale().isValidScale()) {
            errors.add("Görsel ölçek (representation.scale) değerleri 0'dan büyük olmalıdır.");
        }
        if (isBuiltInModelProvider(rep.key()) && (rep.modelId() == null || rep.modelId().isBlank())) {
            errors.add("Harici model representation için representation.model-id zorunludur (type: "
                    + rep.key() + ").");
        }
    }

    private static boolean isBuiltInModelProvider(org.bukkit.NamespacedKey key) {
        if (key == null) return false;
        return ("modelengine".equals(key.getNamespace())
                || "itemsadder".equals(key.getNamespace())
                || "oraxen".equals(key.getNamespace()))
                && "model".equals(key.getKey());
    }

    private static void validateEntityType(String entityType, List<String> errors) {
        EntityType type = null;
        if (entityType != null) {
            try {
                type = EntityType.valueOf(entityType.toUpperCase(java.util.Locale.ROOT));
            } catch (Exception ignored) {}
        }

        if (type == null || !type.isSpawnable() || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
            errors.add("Geçersiz EntityType: " + entityType + ". Canlı (LivingEntity) ve çağırılabilir varlık olmalıdır.");
        }
    }

    private static void validateItemMaterial(String material, List<String> errors) {
        if (material == null || Material.matchMaterial(material) == null) {
            errors.add("Geçersiz ITEM_DISPLAY material: " + material + ". Geçerli bir Minecraft item adı olmalıdır.");
        }
    }

    private static Particle parseParticle(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Particle.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void validateMovement(PetDefinition def, List<String> errors) {
        PetMovementDefinition mov = def.movement();
        if (mov == null) {
            return;
        }

        PetMovementType type = mov.type();
        if (type == null) {
            errors.add("Geçersiz movement türü.");
            return;
        }

        switch (type) {
            case GROUND_FOLLOW, FLYING_FOLLOW, HOVER, SHOULDER, ANCHORED, TRAIL, FORMATION,
                    TELEPORT_ONLY, STATIC_NEAR_OWNER, ECHO, SHADOW_TRAIL, ROAM_NEAR_OWNER, MIRROR, SWARM_CLOUD -> {}
            case ORBIT -> {
                if (mov.orbit() == null || mov.orbit().radius() <= 0.0) {
                    errors.add("ORBIT hareketi için movement.orbit.radius 0'dan büyük olmalıdır.");
                }
            }
            default -> errors.add("Bu sürümde desteklenmeyen movement türü: " + type);
        }

        if (mov.updateIntervalTicks() < 0) {
            errors.add("movement.update-interval-ticks negatif olamaz.");
        }
        if (mov.delayTicks() < 0 || mov.delayTicks() > PetMovementDefinition.MAX_DELAY_TICKS) {
            errors.add("movement.delay-ticks 0-" + PetMovementDefinition.MAX_DELAY_TICKS + " aralığında olmalıdır (mevcut: " + mov.delayTicks() + ").");
        }
    }

    private static void validateEvolutions(PetDefinition def, List<String> errors) {
        if (def.evolutions() == null) return;
        java.util.Set<Integer> levels = new java.util.HashSet<>();
        for (int i = 0; i < def.evolutions().size(); i++) {
            PetEvolutionDefinition evolution = def.evolutions().get(i);
            String path = "evolutions[" + i + "]";
            if (evolution == null) {
                errors.add(path + " null olamaz.");
                continue;
            }
            if (evolution.minLevel() < 1) errors.add(path + ".min-level en az 1 olmalıdır.");
            if (!levels.add(evolution.minLevel())) errors.add(path + ".min-level tekrarlı olamaz: " + evolution.minLevel());
            if (evolution.targetDefinitionId() == null || evolution.targetDefinitionId().isBlank()) {
                errors.add(path + ".target-id zorunludur.");
            }
            if (evolution.scaleOverride() != null && !evolution.scaleOverride().isValidScale()) {
                errors.add(path + ".scale değerleri 0'dan büyük olmalıdır.");
            }
        }
    }

    private static void validateStates(PetDefinition def, List<String> errors) {
        PetStatesDefinition states = def.states();
        if (states == null) {
            return;
        }
        if (!states.defined()) {
            errors.add("states bölümü en az bir animasyon durumu içermelidir.");
            return;
        }
        if (states.moving() != null) {
            PetIdleAnimation animation = states.moving().animation();
            if (animation != null && animation != PetIdleAnimation.NONE && animation != PetIdleAnimation.WALK) {
                errors.add("states.MOVING.animation yalnızca WALK veya NONE olabilir (mevcut: " + animation + ").");
            }
        }
        if (states.idle() != null) {
            PetIdleAnimation animation = states.idle().animation();
            if (animation != null
                    && animation != PetIdleAnimation.SIT
                    && animation != PetIdleAnimation.SLEEP
                    && animation != PetIdleAnimation.LOOK_AROUND
                    && animation != PetIdleAnimation.NONE) {
                errors.add("states.IDLE.animation yalnızca SIT, SLEEP, LOOK_AROUND veya NONE olabilir (mevcut: " + animation + ").");
            }
            if (states.idle().afterTicks() < 0) {
                errors.add("states.IDLE.after-ticks negatif olamaz.");
            }
        }
    }

    private static void validateTransforms(PetDefinition def, List<String> errors) {
        List<PetTransformDefinition> transforms = def.transforms();
        if (transforms == null || transforms.isEmpty()) {
            return;
        }
        for (int i = 0; i < transforms.size(); i++) {
            PetTransformDefinition transform = transforms.get(i);
            String path = "transforms[" + i + "]";
            if (transform.condition() == null || !transform.condition().hasAny()) {
                errors.add(path + " en az bir when koşulu içermelidir (owner-state, biome, world, time-of-day, weather).");
            }
            if (transform.apply() == null || !transform.apply().hasAny()) {
                errors.add(path + " en az bir apply alanı içermelidir (item-material, block-material, custom-model-data, scale, particle-type, particle-count, glowing, baby).");
            }
            if (transform.apply() == null || !transform.apply().hasAny()) {
                continue;
            }
            PetVisualOverride apply = transform.apply();
            if (apply.scale() != null && !apply.scale().isValidScale()) {
                errors.add(path + " apply.representation.scale değerleri 0'dan büyük olmalıdır.");
            }
            if (apply.itemMaterial() != null && Material.matchMaterial(apply.itemMaterial()) == null) {
                errors.add(path + " geçersiz apply.representation.item-material: " + apply.itemMaterial());
            }
            if (apply.blockMaterial() != null && Material.matchMaterial(apply.blockMaterial()) == null) {
                errors.add(path + " geçersiz apply.representation.block-material: " + apply.blockMaterial());
            }
            if (apply.particleType() != null && parseParticle(apply.particleType()) == null) {
                errors.add(path + " geçersiz apply.representation.particle-type: " + apply.particleType());
            }
            if (apply.particleCount() != null && (apply.particleCount() < 0 || apply.particleCount() > 500)) {
                errors.add(path + " apply.representation.particle-count 0-500 aralığında olmalıdır (mevcut: " + apply.particleCount() + ").");
            }
            if (transform.condition() != null && transform.condition().biome() != null) {
                try {
                    org.bukkit.block.Biome.valueOf(transform.condition().biome().trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    errors.add(path + " bilinmeyen when.biome: " + transform.condition().biome());
                }
            }
        }
    }

    private static void validateReactionsAndEmotes(PetDefinition def, List<String> errors) {
        Map<PetReactionType, PetReactionDefinition> reactions = def.reactions();
        if (reactions != null) {
            for (Map.Entry<PetReactionType, PetReactionDefinition> entry : reactions.entrySet()) {
                PetReactionDefinition reaction = entry.getValue();
                if (reaction == null) continue;
                if (reaction.sound() != null && parseSound(reaction.sound()) == null) {
                    errors.add("reactions." + entry.getKey() + " geçersiz sound: " + reaction.sound());
                }
                if (reaction.particle() != null && parseParticle(reaction.particle()) == null) {
                    errors.add("reactions." + entry.getKey() + " geçersiz particle: " + reaction.particle());
                }
                if (reaction.particleCount() < 0 || reaction.particleCount() > 500) {
                    errors.add("reactions." + entry.getKey() + " particle-count 0-500 aralığında olmalıdır (mevcut: " + reaction.particleCount() + ").");
                }
                if (reaction.volume() < 0.0 || reaction.volume() > 2.0) {
                    errors.add("reactions." + entry.getKey() + " volume 0-2 aralığında olmalıdır (mevcut: " + reaction.volume() + ").");
                }
            }
        }

        Map<String, PetEmoteDefinition> emotes = def.emotes();
        if (emotes != null) {
            if (!EMOTE_NAME_PATTERN.matcher(String.join(",", emotes.keySet())).matches()) {
                errors.add("emote isimleri yalnızca küçük harf, rakam, alt çizgi ve tire içerebilir.");
            }
            for (Map.Entry<String, PetEmoteDefinition> entry : emotes.entrySet()) {
                PetEmoteDefinition emote = entry.getValue();
                if (emote == null) continue;
                if (emote.sound() != null && parseSound(emote.sound()) == null) {
                    errors.add("emotes." + entry.getKey() + " geçersiz sound: " + emote.sound());
                }
                if (emote.particle() != null && parseParticle(emote.particle()) == null) {
                    errors.add("emotes." + entry.getKey() + " geçersiz particle: " + emote.particle());
                }
                if (emote.particleCount() < 0 || emote.particleCount() > 500) {
                    errors.add("emotes." + entry.getKey() + " particle-count 0-500 aralığında olmalıdır (mevcut: " + emote.particleCount() + ").");
                }
            }
        }
    }

    private static org.bukkit.Sound parseSound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return org.bukkit.Sound.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void validateBehaviors(PetDefinition def, List<String> errors) {
        if (def.behaviors() == null) return;
        for (int i = 0; i < def.behaviors().size(); i++) {
            PetBehaviorDefinition behavior = def.behaviors().get(i);
            String path = "behaviors[" + i + "]";
            if (behavior == null) {
                errors.add(path + " null olamaz.");
                continue;
            }
            if (behavior.actions().isEmpty()) errors.add(path + ".actions en az bir action içermelidir.");
            for (int j = 0; j < behavior.conditions().size(); j++) {
                BehaviorConditionDefinition condition = behavior.conditions().get(j);
                if (condition == null) {
                    errors.add(path + ".conditions[" + j + "] null olamaz.");
                    continue;
                }
                if (BuiltInBehaviorKeys.MIN_LEVEL.equals(condition.key())) {
                    Object level = condition.parameters().get("level");
                    if (!(level instanceof Number number) || number.intValue() < 1) {
                        errors.add(path + ".conditions[" + j + "] min_level için pozitif 'level' gerektirir.");
                    }
                }
            }
            for (int j = 0; j < behavior.actions().size(); j++) {
                BehaviorActionDefinition action = behavior.actions().get(j);
                if (action == null) {
                    errors.add(path + ".actions[" + j + "] null olamaz.");
                    continue;
                }
                if (BuiltInBehaviorKeys.APPLY_POTION_EFFECT.equals(action.key())) {
                    Object effect = action.parameters().get("effect");
                    if (effect == null || effect.toString().isBlank()) {
                        errors.add(path + ".actions[" + j + "] apply_potion_effect için 'effect' gerektirir.");
                    }
                    Object duration = action.parameters().get("duration-ticks");
                    if (duration != null && (!(duration instanceof Number number) || number.intValue() <= 0)) {
                        errors.add(path + ".actions[" + j + "] duration-ticks pozitif olmalıdır.");
                    }
                }
            }
        }
    }

    private static void validateAbilities(PetDefinition def, List<String> errors) {
        if (def.abilities() == null) return;
        for (Map.Entry<org.bukkit.NamespacedKey, PetAbilityDefinition> entry : def.abilities().entrySet()) {
            PetAbilityDefinition ability = entry.getValue();
            String path = "abilities." + entry.getKey();
            if (ability == null) {
                errors.add(path + " null olamaz.");
                continue;
            }
            if (!entry.getKey().equals(ability.key())) errors.add(path + " map anahtarı ile ability.key eşleşmelidir.");
            if (ability.cooldownSeconds() < 0) errors.add(path + ".cooldown-seconds negatif olamaz.");
            if (!Double.isFinite(ability.range()) || ability.range() <= 0.0 || ability.range() > 64.0) {
                errors.add(path + ".range 0-64 aralığında olmalıdır.");
            }
            if (ability.behavior() == null || ability.behavior().actions().isEmpty()) {
                errors.add(path + ".actions en az bir action içermelidir.");
            } else {
                for (int i = 0; i < ability.behavior().actions().size(); i++) {
                    BehaviorActionDefinition action = ability.behavior().actions().get(i);
                    String actionPath = path + ".actions[" + i + "]";
                    if (BuiltInBehaviorKeys.LAUNCH_PROJECTILE.equals(action.key())) {
                        String projectile = String.valueOf(action.parameters().getOrDefault("projectile", "SNOWBALL"));
                        if (!java.util.Set.of("ARROW", "SMALL_FIREBALL", "SNOWBALL")
                                .contains(projectile.toUpperCase(java.util.Locale.ROOT))) {
                            errors.add(actionPath + " projectile ARROW, SMALL_FIREBALL veya SNOWBALL olmalıdır.");
                        }
                    }
                    if (BuiltInBehaviorKeys.AREA_POTION_EFFECT.equals(action.key())) {
                        Object effect = action.parameters().get("effect");
                        if (effect == null || effect.toString().isBlank()) {
                            errors.add(actionPath + " area_potion_effect için 'effect' gerektirir.");
                        }
                    }
                    if (BuiltInBehaviorKeys.DAMAGE_TARGETS.equals(action.key())) {
                        Object amount = action.parameters().get("amount");
                        if (amount != null && (!(amount instanceof Number number) || number.doubleValue() <= 0.0)) {
                            errors.add(actionPath + " amount pozitif olmalıdır.");
                        }
                    }
                }
            }
        }
    }

    private static void validateItemActions(PetDefinition def, List<String> errors) {
        if (def.itemActions() == null) return;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < def.itemActions().size(); i++) {
            com.petsistemi.domain.item.PetItemActionDefinition action = def.itemActions().get(i);
            String path = "item-actions[" + i + "]";
            if (action == null) {
                errors.add(path + " null olamaz.");
                continue;
            }
            if (action.id() == null || action.id().isBlank() || !ids.add(action.id())) {
                errors.add(path + ".id boş veya tekrarlı olamaz: " + action.id());
            }
            if (action.material() == null || Material.matchMaterial(action.material()) == null) {
                errors.add(path + ".item.material geçerli bir materyal olmalıdır: " + action.material());
            }
            if (action.customModelData() != null && action.customModelData() < 0) {
                errors.add(path + ".item.custom-model-data negatif olamaz.");
            }
            if (action.consumeAmount() < 0) errors.add(path + ".consume negatif olamaz.");
            if (action.cooldownSeconds() < 0) errors.add(path + ".cooldown-seconds negatif olamaz.");
            if (action.minimumLevel() < 1) errors.add(path + ".min-level en az 1 olmalıdır.");
            if (action.maximumLevel() > 0 && action.maximumLevel() < action.minimumLevel()) {
                errors.add(path + ".max-level, min-level değerinden küçük olamaz.");
            }
            if (action.action() == null) errors.add(path + ".action geçerli bir namespaced key olmalıdır.");
            if (com.petsistemi.runtime.item.BuiltInPetItemActions.GAIN_EXPERIENCE.equals(action.action())) {
                Object amount = action.parameters().get("amount");
                if (!(amount instanceof Number number) || number.longValue() <= 0) {
                    errors.add(path + ".parameters.amount pozitif bir sayı olmalıdır.");
                }
            }
            if (com.petsistemi.runtime.item.BuiltInPetItemActions.UNLOCK_PET.equals(action.action())) {
                Object target = action.parameters().get("definition-id");
                if (target == null || target.toString().isBlank()) {
                    errors.add(path + ".parameters.definition-id zorunludur.");
                }
            }
            if (com.petsistemi.runtime.item.BuiltInPetItemActions.EVOLVE_PET.equals(action.action())) {
                Object target = action.parameters().get("target-id");
                if (target == null || target.toString().isBlank()) {
                    errors.add(path + ".parameters.target-id zorunludur.");
                }
            }
        }
    }
}
