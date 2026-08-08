package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.domain.visual.PetProceduralDefinition;
import com.petsistemi.domain.visual.PetVisualGraphDefinition;
import com.petsistemi.domain.visual.PetVisualNodeDefinition;
import com.petsistemi.domain.visual.PetVisualTransform;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import com.petsistemi.runtime.visual.PetVisualHandle;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime-generated persistent display nodes arranged into mathematical shapes. */
public final class ProceduralPetRepresentation implements PetRepresentationController {

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private final CompositePetRepresentation composite;
    private final Map<UUID, ProceduralSession> sessionsByHandle = new ConcurrentHashMap<>();
    private final Map<UUID, ProceduralSession> sessionsByRoot = new ConcurrentHashMap<>();

    public ProceduralPetRepresentation(CompositePetRepresentation composite) {
        this.composite = Objects.requireNonNull(composite, "composite representation null olamaz.");
    }

    @Override
    public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
        return spawnVisual(pet, definition, owner).primaryEntity().orElseThrow();
    }

    @Override
    public PetVisualHandle spawnVisual(PetInstance pet, PetDefinition definition, Player owner) {
        PetProceduralDefinition procedural = requireProcedural(definition);
        PetDefinition inner = innerDefinition(definition, procedural);
        PetVisualHandle handle = composite.spawnVisual(pet, inner, owner);
        try {
            ProceduralSession session = new ProceduralSession(handle, procedural, inner,
                    definition.representationOrEntity().scale(), captureBaseTransforms(handle, procedural.points()));
            applyPose(session);
            sessionsByHandle.put(handle.handleId(), session);
            handle.primaryEntity().map(Entity::getUniqueId).ifPresent(id -> sessionsByRoot.put(id, session));
            return handle;
        } catch (RuntimeException exception) {
            composite.removeVisualHandle(handle);
            throw exception;
        }
    }

    @Override
    public void tickVisualHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition, Player owner) {
        ProceduralSession session = session(visual);
        if (session == null) return;
        // Content is validation-restricted to display controllers, whose tick is a no-op.
        // Calling Composite here would re-apply the spawn graph's identity transforms and
        // collapse every point onto the root between procedural update intervals.
        double speed = stateSpeed(session.state);
        session.animationTime += speed;
        if (session.ticks++ % session.procedural.updateIntervalTicks() == 0) applyPose(session);
    }

    @Override
    public void updateVisualHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition) {
        ProceduralSession session = session(visual);
        if (session == null) return;
        PetProceduralDefinition fresh = requireProcedural(definition);
        requireCompatible(session.procedural, fresh);
        PetDefinition inner = innerDefinition(definition, fresh);
        composite.updateVisualHandle(visual, pet, inner);
        session.procedural = fresh;
        session.innerDefinition = inner;
        session.modelScale = definition.representationOrEntity().scale();
        session.baseTransforms = captureBaseTransforms(visual, fresh.points());
        session.ticks = 0;
        applyPose(session);
    }

    @Override
    public void applyAnimationHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition,
                                     PetAnimationTransition transition) {
        ProceduralSession session = session(visual);
        if (session == null) return;
        session.state = transition != null && transition.state() != null
                ? transition.state() : PetAnimationState.IDLE;
    }

    @Override
    public void removeVisualHandle(PetVisualHandle visual) {
        ProceduralSession session = session(visual);
        if (session != null) {
            sessionsByHandle.remove(session.handle.handleId());
            session.handle.primaryEntity().map(Entity::getUniqueId).ifPresent(sessionsByRoot::remove);
        }
        composite.removeVisualHandle(visual);
    }

    @Override
    public boolean isVisualHandleValid(PetVisualHandle visual) {
        return session(visual) != null && composite.isVisualHandleValid(visual);
    }

    @Override
    public void tickVisual(Entity entity, PetInstance pet, PetDefinition definition, Player owner) {
        ProceduralSession session = session(entity);
        if (session != null) tickVisualHandle(session.handle, pet, definition, owner);
    }

    @Override
    public void updateVisual(Entity entity, PetInstance pet, PetDefinition definition) {
        ProceduralSession session = session(entity);
        if (session != null) updateVisualHandle(session.handle, pet, definition);
    }

    @Override
    public void applyAnimation(Entity entity, PetInstance pet, PetDefinition definition,
                               PetAnimationTransition transition) {
        ProceduralSession session = session(entity);
        if (session != null) applyAnimationHandle(session.handle, pet, definition, transition);
    }

    @Override
    public void remove(Entity entity) {
        ProceduralSession session = session(entity);
        if (session != null) removeVisualHandle(session.handle);
        else if (entity != null && entity.isValid()) entity.remove();
    }

    @Override
    public boolean isValid(Entity entity) {
        ProceduralSession session = session(entity);
        return session != null ? isVisualHandleValid(session.handle)
                : entity != null && entity.isValid() && !entity.isDead();
    }

    private static void applyPose(ProceduralSession session) {
        Entity root = session.handle.primaryEntity().orElse(null);
        if (root == null || !root.isValid()) return;
        Location anchor = root.getLocation();
        if (anchor == null) return;
        PetVector3 scale = session.modelScale != null ? session.modelScale : PetVector3.ONE;
        double phase = session.procedural.rotationSpeed() * session.animationTime;
        double pulse = 1.0 + session.procedural.pulseAmplitude() * Math.sin(
                Math.toRadians(session.procedural.pulseSpeed() * session.animationTime));
        List<PetVector3> points = sample(session.procedural, phase);
        for (int index = 0; index < points.size(); index++) {
            String id = pointId(index);
            Entity entity = session.handle.component(id).flatMap(component -> component.serverEntity()).orElse(null);
            if (entity == null || !entity.isValid()) continue;
            PetVector3 point = points.get(index);
            Vector offset = new Vector(point.x() * scale.x() * pulse,
                    point.y() * scale.y() * pulse, point.z() * scale.z() * pulse)
                    .rotateAroundY(Math.toRadians(-anchor.getYaw()));
            Location target = anchor.clone().add(offset);
            target.setYaw(anchor.getYaw());
            target.setPitch(0f);
            entity.teleport(target);
            if (entity instanceof Display display) applyPulse(display, session.baseTransforms.get(id), pulse);
        }
    }

    private static void applyPulse(Display display, Transformation base, double pulse) {
        if (base == null) return;
        Vector3f baseScale = base.getScale();
        display.setTransformation(new Transformation(new Vector3f(base.getTranslation()),
                new org.joml.Quaternionf(base.getLeftRotation()),
                new Vector3f(baseScale).mul((float) pulse),
                new org.joml.Quaternionf(base.getRightRotation())));
    }

    static List<PetVector3> sample(PetProceduralDefinition procedural, double phaseDegrees) {
        List<PetVector3> points = new ArrayList<>(procedural.points());
        double phase = Math.toRadians(phaseDegrees);
        for (int index = 0; index < procedural.points(); index++) {
            PetVector3 local = switch (procedural.shape()) {
                case RING -> ring(procedural, index);
                case SPHERE -> sphere(procedural, index, 1.0);
                case HELIX -> helix(procedural, index);
                case SPIRAL -> spiral(procedural, index);
                case CUBE -> cube(procedural, index);
                case WAVE -> wave(procedural, index);
                case CONE -> cone(procedural, index);
                case CONSTELLATION -> constellation(procedural, index);
            };
            double cos = Math.cos(phase);
            double sin = Math.sin(phase);
            points.add(new PetVector3(local.x() * cos - local.z() * sin, local.y(),
                    local.x() * sin + local.z() * cos));
        }
        return List.copyOf(points);
    }

    private static PetVector3 ring(PetProceduralDefinition definition, int index) {
        double angle = Math.PI * 2.0 * index / definition.points();
        return new PetVector3(Math.cos(angle) * definition.radius(), 0.0,
                Math.sin(angle) * definition.radius());
    }

    private static PetVector3 sphere(PetProceduralDefinition definition, int index, double radiusFactor) {
        double y = 1.0 - (2.0 * index + 1.0) / definition.points();
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double angle = GOLDEN_ANGLE * index;
        return new PetVector3(Math.cos(angle) * horizontal * definition.radius() * radiusFactor,
                y * definition.height() * 0.5 * radiusFactor,
                Math.sin(angle) * horizontal * definition.radius() * radiusFactor);
    }

    private static PetVector3 helix(PetProceduralDefinition definition, int index) {
        double progress = progress(definition, index);
        double angle = Math.PI * 4.0 * progress;
        return new PetVector3(Math.cos(angle) * definition.radius(),
                (progress - 0.5) * definition.height(), Math.sin(angle) * definition.radius());
    }

    private static PetVector3 spiral(PetProceduralDefinition definition, int index) {
        double progress = progress(definition, index);
        double angle = Math.PI * 6.0 * progress;
        double radius = definition.radius() * progress;
        return new PetVector3(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
    }

    private static PetVector3 wave(PetProceduralDefinition definition, int index) {
        double progress = progress(definition, index);
        return new PetVector3((progress * 2.0 - 1.0) * definition.radius(),
                Math.sin(progress * Math.PI * 4.0) * definition.height() * 0.5,
                Math.cos(progress * Math.PI * 2.0) * definition.radius() * 0.2);
    }

    private static PetVector3 cone(PetProceduralDefinition definition, int index) {
        double progress = progress(definition, index);
        double angle = Math.PI * 4.0 * progress;
        double radius = definition.radius() * (1.0 - progress);
        return new PetVector3(Math.cos(angle) * radius,
                (progress - 0.5) * definition.height(), Math.sin(angle) * radius);
    }

    private static PetVector3 constellation(PetProceduralDefinition definition, int index) {
        double variation = 0.55 + 0.45 * pseudoRandom(index);
        return sphere(definition, index, variation);
    }

    private static PetVector3 cube(PetProceduralDefinition definition, int index) {
        double edgePosition = index * 12.0 / definition.points();
        int edge = Math.min(11, (int) edgePosition);
        double t = edgePosition - edge;
        double r = definition.radius();
        double h = definition.height() * 0.5;
        PetVector3[] corners = {
                new PetVector3(-r, -h, -r), new PetVector3(r, -h, -r),
                new PetVector3(r, -h, r), new PetVector3(-r, -h, r),
                new PetVector3(-r, h, -r), new PetVector3(r, h, -r),
                new PetVector3(r, h, r), new PetVector3(-r, h, r)
        };
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        return lerp(corners[edges[edge][0]], corners[edges[edge][1]], t);
    }

    private static PetVector3 lerp(PetVector3 start, PetVector3 end, double t) {
        return new PetVector3(start.x() + (end.x() - start.x()) * t,
                start.y() + (end.y() - start.y()) * t,
                start.z() + (end.z() - start.z()) * t);
    }

    private static double progress(PetProceduralDefinition definition, int index) {
        return index / (double) (definition.points() - 1);
    }

    private static double pseudoRandom(int index) {
        double raw = Math.sin((index + 1) * 12.9898) * 43758.5453;
        return raw - Math.floor(raw);
    }

    private static Map<String, Transformation> captureBaseTransforms(PetVisualHandle handle, int points) {
        Map<String, Transformation> base = new LinkedHashMap<>();
        for (int index = 0; index < points; index++) {
            String id = pointId(index);
            Entity entity = handle.component(id).flatMap(component -> component.serverEntity()).orElse(null);
            if (!(entity instanceof Display display)) {
                throw new IllegalStateException("PROCEDURAL content Display entity üretmedi: " + id);
            }
            display.setInterpolationDuration(1);
            Transformation transformation = display.getTransformation();
            if (transformation == null) transformation = new Transformation(new Vector3f(),
                    new org.joml.Quaternionf(), new Vector3f(1, 1, 1), new org.joml.Quaternionf());
            base.put(id, copy(transformation));
        }
        return base;
    }

    private static Transformation copy(Transformation value) {
        return new Transformation(new Vector3f(value.getTranslation()),
                new org.joml.Quaternionf(value.getLeftRotation()), new Vector3f(value.getScale()),
                new org.joml.Quaternionf(value.getRightRotation()));
    }

    private static PetDefinition innerDefinition(PetDefinition outer, PetProceduralDefinition procedural) {
        List<PetVisualNodeDefinition> nodes = new ArrayList<>();
        PetRepresentationDefinition invisible = new PetRepresentationDefinition(RuntimeRepresentationType.INVISIBLE,
                "MARKER", false, false, true, true, false, null, null, PetVector3.ONE);
        nodes.add(new PetVisualNodeDefinition("root", null, invisible, PetVisualTransform.IDENTITY));
        for (int index = 0; index < procedural.points(); index++) {
            nodes.add(new PetVisualNodeDefinition(pointId(index), "root", procedural.content(),
                    PetVisualTransform.IDENTITY));
        }
        return outer.toBuilder().representation(PetRepresentationDefinition.composite(
                new PetVisualGraphDefinition("root", nodes))).build();
    }

    private static void requireCompatible(PetProceduralDefinition current, PetProceduralDefinition fresh) {
        if (current.points() != fresh.points() || !current.content().key().equals(fresh.content().key())) {
            throw new IllegalStateException("PROCEDURAL node sayısı/provider canlı değiştirilemez.");
        }
    }

    private static PetProceduralDefinition requireProcedural(PetDefinition definition) {
        PetProceduralDefinition procedural = definition != null
                ? definition.representationOrEntity().procedural() : null;
        if (procedural == null) throw new IllegalArgumentException("PROCEDURAL tanımı eksik.");
        return procedural;
    }

    private static String pointId(int index) {
        return String.format(java.util.Locale.ROOT, "point-%02d", index + 1);
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

    private ProceduralSession session(PetVisualHandle handle) {
        return handle != null ? sessionsByHandle.get(handle.handleId()) : null;
    }

    private ProceduralSession session(Entity root) {
        return root != null && root.getUniqueId() != null ? sessionsByRoot.get(root.getUniqueId()) : null;
    }

    private static final class ProceduralSession {
        private final PetVisualHandle handle;
        private PetProceduralDefinition procedural;
        private PetDefinition innerDefinition;
        private PetVector3 modelScale;
        private Map<String, Transformation> baseTransforms;
        private PetAnimationState state = PetAnimationState.IDLE;
        private long ticks;
        private double animationTime;

        private ProceduralSession(PetVisualHandle handle, PetProceduralDefinition procedural,
                                  PetDefinition innerDefinition, PetVector3 modelScale,
                                  Map<String, Transformation> baseTransforms) {
            this.handle = handle;
            this.procedural = procedural;
            this.innerDefinition = innerDefinition;
            this.modelScale = modelScale;
            this.baseTransforms = baseTransforms;
        }
    }
}
