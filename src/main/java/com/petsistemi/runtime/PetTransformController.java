package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetOwnerState;
import com.petsistemi.domain.PetTimeOfDay;
import com.petsistemi.domain.PetTransformCondition;
import com.petsistemi.domain.PetTransformDefinition;
import com.petsistemi.domain.PetWeather;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates the per-pet {@code transforms:} conditions every tick and, when the
 * active transform changes, re-renders the pet's visuals with the transform's
 * overrides (same {@link com.petsistemi.domain.PetInstance} is kept).
 *
 * <p>The derived definition is shared with the rest of the runtime via
 * {@link #activeDefinition(ActivePet)} so idle/rest and per-tick visuals all use
 * the transformed representation.</p>
 */
public class PetTransformController {

    private final PetDefinitionRegistry definitionRegistry;
    private final PetRepresentationRegistry representationRegistry;
    private final PetEvolutionController evolutionController;

    private final Map<UUID, Integer> activeTransformIndex = new HashMap<>();
    private final Map<UUID, PetDefinition> activeDefinitions = new HashMap<>();

    public PetTransformController(PetDefinitionRegistry definitionRegistry,
                                  PetRepresentationRegistry representationRegistry) {
        this(definitionRegistry, representationRegistry, null);
    }

    public PetTransformController(PetDefinitionRegistry definitionRegistry,
                                  PetRepresentationRegistry representationRegistry,
                                  PetEvolutionController evolutionController) {
        this.definitionRegistry = definitionRegistry;
        this.representationRegistry = representationRegistry;
        this.evolutionController = evolutionController;
    }

    /** Called once per pet per tick from the coordinator, before other controllers. */
    public void tick(Player owner, ActivePet active, Entity entity) {
        if (owner == null || active == null || entity == null) {
            return;
        }
        UUID ownerId = owner.getUniqueId();
        PetDefinition base = resolveBase(active);
        if (base == null || base.transforms() == null || base.transforms().isEmpty()) {
            if (activeTransformIndex.remove(ownerId) != null || activeDefinitions.remove(ownerId) != null) {
                reRender(active, entity, base);
            }
            return;
        }

        int index = resolveTransformIndex(owner, base);
        Integer current = activeTransformIndex.get(ownerId);
        if (current != null && current == index) {
            return;
        }
        activeTransformIndex.put(ownerId, index);
        activeDefinitions.put(ownerId, index < 0 ? base : base.withTransformApplied(base.transforms().get(index)));
        reRender(active, entity, activeDefinitions.get(ownerId));
    }

    /**
     * Returns the definition that should be used for visuals of the given pet
     * (base or the currently active transform's derived copy).
     */
    public PetDefinition activeDefinition(ActivePet active) {
        if (active == null || active.getOwnerId() == null) {
            return null;
        }
        PetDefinition derived = activeDefinitions.get(active.getOwnerId());
        if (derived != null) {
            return derived;
        }
        return resolveBase(active);
    }

    /** Frees per-owner state when the pet is despawned or the owner disconnects. */
    public void cleanup(UUID ownerId) {
        if (ownerId == null) return;
        activeTransformIndex.remove(ownerId);
        activeDefinitions.remove(ownerId);
    }

    private void reRender(ActivePet active, Entity entity, PetDefinition definition) {
        PetRepresentationController rep = representationRegistry != null
                ? representationRegistry.get(active.getRepresentationType())
                : null;
        if (rep == null || definition == null || entity == null || !entity.isValid()) {
            return;
        }
        rep.updateVisual(entity, active.getPetInstance(), definition);
        if (active.isResting()) {
            rep.applyRestState(entity, active.getPetInstance(), definition, true);
        }
    }

    private PetDefinition resolveBase(ActivePet active) {
        if (evolutionController != null) {
            PetDefinition evolved = evolutionController.activeDefinition(active);
            if (evolved != null) return evolved;
        }
        if (definitionRegistry == null || active == null || active.getDefinitionId() == null) {
            return null;
        }
        return definitionRegistry.find(active.getDefinitionId()).orElse(null);
    }

    private int resolveTransformIndex(Player owner, PetDefinition base) {
        List<PetTransformDefinition> transforms = base.transforms();
        for (int i = 0; i < transforms.size(); i++) {
            PetTransformDefinition transform = transforms.get(i);
            if (transform.condition() != null && matches(transform.condition(), owner)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matches(PetTransformCondition condition, Player owner) {
        if (condition.ownerState() != null && !ownerStateMatches(condition.ownerState(), owner)) {
            return false;
        }
        if (condition.biome() != null) {
            if (owner.getLocation() == null || owner.getLocation().getBlock() == null) return false;
            Biome biome = owner.getLocation().getBlock().getBiome();
            if (biome == null || !biome.name().equalsIgnoreCase(condition.biome())) {
                return false;
            }
        }
        if (condition.world() != null && (owner.getWorld() == null || !owner.getWorld().getName().equals(condition.world()))) {
            return false;
        }
        if (condition.timeOfDay() != null && (owner.getWorld() == null || !timeMatches(condition.timeOfDay(), owner.getWorld().getTime()))) {
            return false;
        }
        if (condition.weather() != null && (owner.getWorld() == null || !weatherMatches(condition.weather(), owner.getWorld()))) {
            return false;
        }
        if (condition.minY() != null || condition.maxY() != null) {
            if (owner.getLocation() == null) return false;
            int y = owner.getLocation().getBlockY();
            if (condition.minY() != null && y < condition.minY()) return false;
            if (condition.maxY() != null && y > condition.maxY()) return false;
        }
        if (condition.minLight() != null || condition.maxLight() != null) {
            if (owner.getLocation() == null || owner.getLocation().getBlock() == null) return false;
            int light = owner.getLocation().getBlock().getLightLevel();
            if (condition.minLight() != null && light < condition.minLight()) return false;
            if (condition.maxLight() != null && light > condition.maxLight()) return false;
        }
        return true;
    }

    private static boolean ownerStateMatches(PetOwnerState state, Player owner) {
        return switch (state) {
            case WALKING -> !owner.isFlying() && owner.isOnGround() && !owner.isInWater();
            case FLYING -> owner.isFlying();
            case SNEAKING -> owner.isSneaking();
            case IN_WATER -> owner.isInWater();
            case FALLING -> !owner.isOnGround() && owner.getVelocity().getY() < 0.0;
            case RIDING -> owner.isInsideVehicle();
        };
    }

    private static boolean timeMatches(PetTimeOfDay timeOfDay, long worldTime) {
        long normalized = ((worldTime % 24000L) + 24000L) % 24000L;
        boolean night = normalized >= 13000L && normalized < 23000L;
        return switch (timeOfDay) {
            case DAY -> !night;
            case NIGHT -> night;
        };
    }

    private static boolean weatherMatches(PetWeather weather, org.bukkit.World world) {
        return switch (weather) {
            case CLEAR -> !world.hasStorm();
            case RAIN -> world.hasStorm() && !world.isThundering();
            case THUNDER -> world.isThundering();
        };
    }
}
