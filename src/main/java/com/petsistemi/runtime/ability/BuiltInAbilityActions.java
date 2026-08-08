package com.petsistemi.runtime.ability;

import com.petsistemi.api.behavior.BehaviorService;
import com.petsistemi.domain.RuntimeKeyResolver;
import com.petsistemi.runtime.behavior.BehaviorContext;
import com.petsistemi.runtime.behavior.BuiltInBehaviorKeys;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Built-in ability actions. All parameters are YAML-safe primitives. */
final class BuiltInAbilityActions {
    private BuiltInAbilityActions() {}

    static void register(BehaviorService service) {
        service.registerAction(BuiltInBehaviorKeys.LAUNCH_PROJECTILE, BuiltInAbilityActions::launchProjectile);
        service.registerAction(BuiltInBehaviorKeys.AREA_POTION_EFFECT, BuiltInAbilityActions::applyAreaPotionEffect);
        service.registerAction(BuiltInBehaviorKeys.DAMAGE_TARGETS, BuiltInAbilityActions::damageTargets);
    }

    private static void launchProjectile(BehaviorContext context, Map<String, Object> parameters) {
        Object ownerValue = context.attributes().get("owner");
        Entity pet = context.petEntity();
        ProjectileSource source = pet instanceof ProjectileSource projectileSource
                ? projectileSource : ownerValue instanceof ProjectileSource projectileSource ? projectileSource : null;
        if (source == null) return;
        Class<? extends Projectile> projectileType = projectileType(string(parameters, "projectile", "SNOWBALL"));
        if (projectileType == null) return;
        Projectile projectile = source.launchProjectile(projectileType);
        double speed = decimal(parameters, "speed", 1.5);
        Object targetValue = context.attributes().get("target");
        if (targetValue instanceof Entity target && source instanceof Entity sourceEntity) {
            Location from = sourceEntity instanceof LivingEntity living ? living.getEyeLocation() : sourceEntity.getLocation();
            Location to = target instanceof LivingEntity living ? living.getEyeLocation() : target.getLocation();
            Vector direction = to.toVector().subtract(from.toVector());
            if (direction.lengthSquared() > 0.0) projectile.setVelocity(direction.normalize().multiply(speed));
        }
        if (projectile instanceof Fireball fireball) {
            fireball.setYield((float) decimal(parameters, "yield", 0.0));
            fireball.setIsIncendiary(bool(parameters, "incendiary", false));
        }
    }

    private static void applyAreaPotionEffect(BehaviorContext context, Map<String, Object> parameters) {
        PotionEffectType type = RuntimeKeyResolver.potionEffect(string(parameters, "effect", null));
        if (type == null) return;
        PotionEffect effect = new PotionEffect(type, integer(parameters, "duration-ticks", 60),
                integer(parameters, "amplifier", 0), true,
                bool(parameters, "particles", true), true);
        for (Entity target : targets(context)) {
            if (target instanceof LivingEntity living) living.addPotionEffect(effect);
        }
    }

    private static void damageTargets(BehaviorContext context, Map<String, Object> parameters) {
        double amount = Math.max(0.0, decimal(parameters, "amount", 1.0));
        Object ownerValue = context.attributes().get("owner");
        for (Entity target : targets(context)) {
            if (target instanceof LivingEntity living) {
                if (ownerValue instanceof Player owner) living.damage(amount, owner);
                else living.damage(amount);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Entity> targets(BehaviorContext context) {
        Object value = context.attributes().get("targets");
        return value instanceof List<?> list ? list.stream().filter(Entity.class::isInstance)
                .map(Entity.class::cast).toList() : List.of();
    }

    private static Class<? extends Projectile> projectileType(String raw) {
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "ARROW" -> Arrow.class;
            case "SMALL_FIREBALL" -> SmallFireball.class;
            case "SNOWBALL" -> Snowball.class;
            default -> null;
        };
    }

    private static String string(Map<String, Object> parameters, String key, String fallback) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : fallback;
    }

    private static int integer(Map<String, Object> parameters, String key, int fallback) {
        Object value = parameters.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double decimal(Map<String, Object> parameters, String key, double fallback) {
        Object value = parameters.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static boolean bool(Map<String, Object> parameters, String key, boolean fallback) {
        Object value = parameters.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }
}
