package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.domain.visual.PetParticleModelDefinition;
import com.petsistemi.domain.visual.PetParticleModelPartDefinition;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Procedural particle shapes rendered around an invisible marker anchor. */
public final class ParticleModelPetRepresentation implements PetRepresentationController {

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private final InvisiblePetRepresentation markerRepresentation;
    private final Map<UUID, ParticleModelSession> sessions = new ConcurrentHashMap<>();

    public ParticleModelPetRepresentation(InvisiblePetRepresentation markerRepresentation) {
        this.markerRepresentation = Objects.requireNonNull(markerRepresentation,
                "invisible marker representation null olamaz.");
    }

    @Override
    public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
        PetParticleModelDefinition model = requireModel(definition);
        Entity marker = markerRepresentation.spawn(pet, definition, owner);
        sessions.put(marker.getUniqueId(), new ParticleModelSession(model));
        return marker;
    }

    @Override
    public void tickVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition, Player owner) {
        if (primaryEntity == null || !primaryEntity.isValid() || owner == null || !owner.isOnline()) return;
        ParticleModelSession session = sessions.get(primaryEntity.getUniqueId());
        if (session == null) return;
        double stateMultiplier = stateSpeed(session.state);
        double animationTime = session.animationTime;
        session.animationTime += stateMultiplier;
        int interval = session.model.updateIntervalTicks();
        if (session.ticks++ % interval != 0) return;

        Location anchor = primaryEntity.getLocation();
        World world = primaryEntity.getWorld();
        PetVector3 scale = definition.representationOrEntity().scale();
        if (scale == null) scale = PetVector3.ONE;
        for (PetParticleModelPartDefinition part : session.model.parts()) {
            Particle particle = ParticlePetRepresentation.resolveParticle(part.particleType());
            if (particle == null) continue;
            double phase = part.rotationSpeed() * animationTime;
            for (PetVector3 point : sample(part, phase)) {
                Location position = anchor.clone().add(
                        point.x() * scale.x(), point.y() * scale.y(), point.z() * scale.z());
                world.spawnParticle(particle, position, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    public void applyAnimation(Entity primaryEntity, PetInstance pet, PetDefinition definition,
                               PetAnimationTransition transition) {
        if (primaryEntity == null || transition == null) return;
        ParticleModelSession session = sessions.get(primaryEntity.getUniqueId());
        if (session != null) {
            session.state = transition.state() != null ? transition.state() : PetAnimationState.IDLE;
        }
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        if (primaryEntity == null) return;
        PetParticleModelDefinition model = requireModel(definition);
        sessions.compute(primaryEntity.getUniqueId(), (ignored, existing) -> {
            if (existing == null) return new ParticleModelSession(model);
            existing.model = model;
            existing.ticks = 0;
            return existing;
        });
    }

    @Override
    public void remove(Entity primaryEntity) {
        if (primaryEntity != null) sessions.remove(primaryEntity.getUniqueId());
        markerRepresentation.remove(primaryEntity);
    }

    @Override
    public boolean isValid(Entity primaryEntity) {
        return primaryEntity != null && sessions.containsKey(primaryEntity.getUniqueId())
                && markerRepresentation.isValid(primaryEntity);
    }

    static List<PetVector3> sample(PetParticleModelPartDefinition part, double phaseDegrees) {
        List<PetVector3> points = new ArrayList<>(part.points());
        for (int index = 0; index < part.points(); index++) {
            PetVector3 local = switch (part.shape()) {
                case RING -> ring(part, index);
                case SPHERE -> sphere(part, index);
                case HELIX -> helix(part, index);
                case CUBE -> cube(part, index);
                case CONE -> cone(part, index);
            };
            points.add(rotateAndOffset(local, part.offset(), phaseDegrees));
        }
        return List.copyOf(points);
    }

    private static PetVector3 ring(PetParticleModelPartDefinition part, int index) {
        double angle = Math.PI * 2.0 * index / part.points();
        return new PetVector3(Math.cos(angle) * part.radius(), 0.0, Math.sin(angle) * part.radius());
    }

    private static PetVector3 sphere(PetParticleModelPartDefinition part, int index) {
        double y = 1.0 - (2.0 * index + 1.0) / part.points();
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double angle = GOLDEN_ANGLE * index;
        return new PetVector3(Math.cos(angle) * horizontal * part.radius(),
                y * part.height() * 0.5, Math.sin(angle) * horizontal * part.radius());
    }

    private static PetVector3 helix(PetParticleModelPartDefinition part, int index) {
        double progress = progress(part, index);
        double angle = Math.PI * 4.0 * progress;
        return new PetVector3(Math.cos(angle) * part.radius(),
                (progress - 0.5) * part.height(), Math.sin(angle) * part.radius());
    }

    private static PetVector3 cone(PetParticleModelPartDefinition part, int index) {
        double progress = progress(part, index);
        double angle = Math.PI * 4.0 * progress;
        double radius = part.radius() * (1.0 - progress);
        return new PetVector3(Math.cos(angle) * radius,
                (progress - 0.5) * part.height(), Math.sin(angle) * radius);
    }

    private static PetVector3 cube(PetParticleModelPartDefinition part, int index) {
        double edgePosition = index * 12.0 / part.points();
        int edge = Math.min(11, (int) edgePosition);
        double t = edgePosition - edge;
        double r = part.radius();
        double h = part.height() * 0.5;
        PetVector3[] start = {
                new PetVector3(-r, -h, -r), new PetVector3(r, -h, -r),
                new PetVector3(r, -h, r), new PetVector3(-r, -h, r),
                new PetVector3(-r, h, -r), new PetVector3(r, h, -r),
                new PetVector3(r, h, r), new PetVector3(-r, h, r),
                new PetVector3(-r, -h, -r), new PetVector3(r, -h, -r),
                new PetVector3(r, -h, r), new PetVector3(-r, -h, r)
        };
        PetVector3[] end = {
                new PetVector3(r, -h, -r), new PetVector3(r, -h, r),
                new PetVector3(-r, -h, r), new PetVector3(-r, -h, -r),
                new PetVector3(r, h, -r), new PetVector3(r, h, r),
                new PetVector3(-r, h, r), new PetVector3(-r, h, -r),
                new PetVector3(-r, h, -r), new PetVector3(r, h, -r),
                new PetVector3(r, h, r), new PetVector3(-r, h, r)
        };
        return lerp(start[edge], end[edge], t);
    }

    private static PetVector3 lerp(PetVector3 start, PetVector3 end, double t) {
        return new PetVector3(start.x() + (end.x() - start.x()) * t,
                start.y() + (end.y() - start.y()) * t,
                start.z() + (end.z() - start.z()) * t);
    }

    private static PetVector3 rotateAndOffset(PetVector3 point, PetVector3 offset, double phaseDegrees) {
        double radians = Math.toRadians(phaseDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = point.x() * cos - point.z() * sin;
        double z = point.x() * sin + point.z() * cos;
        return new PetVector3(x + offset.x(), point.y() + offset.y(), z + offset.z());
    }

    private static double progress(PetParticleModelPartDefinition part, int index) {
        return part.points() <= 1 ? 0.0 : index / (double) (part.points() - 1);
    }

    private static double stateSpeed(PetAnimationState state) {
        return switch (state) {
            case SLEEPING -> 0.25;
            case MOVING -> 1.25;
            case SPRINTING -> 1.75;
            case ATTACKING -> 2.25;
            case IDLE -> 1.0;
        };
    }

    private static PetParticleModelDefinition requireModel(PetDefinition definition) {
        PetParticleModelDefinition model = definition.representationOrEntity().particleModel();
        if (model == null) throw new IllegalArgumentException("PARTICLE_MODEL tanımı eksik.");
        return model;
    }

    private static final class ParticleModelSession {
        private PetParticleModelDefinition model;
        private PetAnimationState state = PetAnimationState.IDLE;
        private int ticks;
        private double animationTime;

        private ParticleModelSession(PetParticleModelDefinition model) {
            this.model = model;
        }
    }
}
