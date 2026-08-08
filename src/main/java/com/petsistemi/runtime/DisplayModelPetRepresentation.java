package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.domain.visual.PetDisplayAnimationDefinition;
import com.petsistemi.domain.visual.PetDisplayKeyframeDefinition;
import com.petsistemi.domain.visual.PetDisplayModelDefinition;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Display-only skeletal model with hierarchical local transforms and keyframe channels. */
public final class DisplayModelPetRepresentation implements PetRepresentationController {

    private final CompositePetRepresentation composite;
    private final Map<UUID, ModelSession> sessionsByHandle = new ConcurrentHashMap<>();
    private final Map<UUID, ModelSession> sessionsByRoot = new ConcurrentHashMap<>();

    public DisplayModelPetRepresentation(CompositePetRepresentation composite) {
        this.composite = Objects.requireNonNull(composite, "composite representation null olamaz.");
    }

    @Override
    public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
        return spawnVisual(pet, definition, owner).primaryEntity().orElseThrow();
    }

    @Override
    public PetVisualHandle spawnVisual(PetInstance pet, PetDefinition definition, Player owner) {
        PetDisplayModelDefinition model = requireModel(definition);
        PetDefinition inner = innerDefinition(definition, model);
        PetVisualHandle handle = composite.spawnVisual(pet, inner, owner);
        try {
            Map<String, BaseTransform> baseTransforms = captureBaseTransforms(handle, model);
            ModelSession session = new ModelSession(handle, model, inner, baseTransforms,
                    definition.representationOrEntity().scale());
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
        ModelSession session = session(visual);
        if (session == null) return;
        composite.tickVisualHandle(visual, pet, session.innerDefinition, owner);
        applyPose(session);
        session.animationTick++;
    }

    @Override
    public void updateVisualHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition) {
        ModelSession session = session(visual);
        if (session == null) return;
        PetDisplayModelDefinition fresh = requireModel(definition);
        requireCompatibleSkeleton(session.model.skeleton(), fresh.skeleton());
        resetToBase(session);
        PetDefinition freshInner = innerDefinition(definition, fresh);
        composite.updateVisualHandle(visual, pet, freshInner);
        session.model = fresh;
        session.innerDefinition = freshInner;
        session.modelScale = definition.representationOrEntity().scale();
        session.baseTransforms = captureBaseTransforms(visual, fresh);
        applyPose(session);
    }

    @Override
    public void applyRestStateHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition,
                                     boolean resting) {
        ModelSession session = session(visual);
        if (session == null) return;
        resetToBase(session);
        composite.applyRestStateHandle(visual, pet, session.innerDefinition, resting);
        session.baseTransforms = captureBaseTransforms(visual, session.model);
        applyPose(session);
    }

    @Override
    public void applyAnimationHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition,
                                     PetAnimationTransition transition) {
        ModelSession session = session(visual);
        if (session == null) return;
        session.state = transition != null && transition.state() != null
                ? transition.state() : PetAnimationState.IDLE;
        session.animationTick = 0;
        applyPose(session);
    }

    @Override
    public void removeVisualHandle(PetVisualHandle visual) {
        ModelSession session = session(visual);
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
        ModelSession session = session(entity);
        if (session != null) tickVisualHandle(session.handle, pet, definition, owner);
    }

    @Override
    public void updateVisual(Entity entity, PetInstance pet, PetDefinition definition) {
        ModelSession session = session(entity);
        if (session != null) updateVisualHandle(session.handle, pet, definition);
    }

    @Override
    public void applyRestState(Entity entity, PetInstance pet, PetDefinition definition, boolean resting) {
        ModelSession session = session(entity);
        if (session != null) applyRestStateHandle(session.handle, pet, definition, resting);
    }

    @Override
    public void applyAnimation(Entity entity, PetInstance pet, PetDefinition definition,
                               PetAnimationTransition transition) {
        ModelSession session = session(entity);
        if (session != null) applyAnimationHandle(session.handle, pet, definition, transition);
    }

    @Override
    public void remove(Entity entity) {
        ModelSession session = session(entity);
        if (session != null) removeVisualHandle(session.handle);
        else if (entity != null && entity.isValid()) entity.remove();
    }

    @Override
    public boolean isValid(Entity entity) {
        ModelSession session = session(entity);
        return session != null ? isVisualHandleValid(session.handle)
                : entity != null && entity.isValid() && !entity.isDead();
    }

    private void applyPose(ModelSession session) {
        Entity rootEntity = session.handle.primaryEntity().orElse(null);
        if (rootEntity == null || !rootEntity.isValid()) return;
        Location anchor = rootEntity.getLocation();
        if (anchor == null) return;

        PetDisplayAnimationDefinition animation = session.model.animations().get(session.state);
        Map<String, GlobalPose> globals = new LinkedHashMap<>();
        for (PetVisualNodeDefinition bone : session.model.skeleton().topologicalNodes()) {
            PetVisualTransform animated = sample(animation, bone.id(), session.animationTick);
            PetVisualTransform base = bone.transform();
            Vector3f localTranslation = add(vector(base.translation()), vector(animated.translation()));
            Quaternionf localRotation = quaternion(base.rotation()).mul(quaternion(animated.rotation()));
            Vector3f localScale = multiply(vector(base.scale()), vector(animated.scale()));

            GlobalPose parent = bone.parentId() != null ? globals.get(bone.parentId()) : null;
            GlobalPose global;
            if (parent == null) {
                global = new GlobalPose(localTranslation, localRotation,
                        multiply(localScale, vector(session.modelScale != null ? session.modelScale : PetVector3.ONE)));
            } else {
                Vector3f scaledOffset = multiply(new Vector3f(localTranslation), parent.scale);
                parent.rotation.transform(scaledOffset);
                global = new GlobalPose(add(new Vector3f(parent.translation), scaledOffset),
                        new Quaternionf(parent.rotation).mul(localRotation),
                        multiply(new Vector3f(parent.scale), localScale));
            }
            globals.put(bone.id(), global);
            applyBone(session, bone.id(), bone.id().equals(session.model.skeleton().rootId()), anchor, global);
        }
    }

    private void applyBone(ModelSession session, String boneId, boolean root, Location anchor, GlobalPose pose) {
        Entity entity = session.handle.component(boneId).flatMap(component -> component.serverEntity()).orElse(null);
        if (!(entity instanceof Display display) || !entity.isValid()) return;
        BaseTransform base = session.baseTransforms.get(boneId);
        if (base == null) return;

        if (!root) {
            Vector offset = new Vector(pose.translation.x, pose.translation.y, pose.translation.z)
                    .rotateAroundY(Math.toRadians(-anchor.getYaw()));
            Location target = anchor.clone().add(offset);
            target.setYaw(anchor.getYaw());
            target.setPitch(0f);
            entity.teleport(target);
        }
        Vector3f displayTranslation = root
                ? add(new Vector3f(base.translation), pose.translation)
                : new Vector3f(base.translation);
        display.setTransformation(new Transformation(
                displayTranslation,
                new Quaternionf(base.leftRotation).mul(pose.rotation),
                multiply(new Vector3f(base.scale), pose.scale),
                new Quaternionf(base.rightRotation)));
    }

    private static PetVisualTransform sample(PetDisplayAnimationDefinition animation, String bone, long rawTick) {
        if (animation == null) return PetVisualTransform.IDENTITY;
        List<PetDisplayKeyframeDefinition> frames = animation.channels().get(bone);
        if (frames == null || frames.isEmpty()) return PetVisualTransform.IDENTITY;
        int tick = animation.loop()
                ? (int) (rawTick % animation.durationTicks())
                : (int) Math.min(rawTick, animation.durationTicks());
        PetDisplayKeyframeDefinition previous = frames.get(0);
        if (tick <= previous.tick()) return previous.transform();
        for (int i = 1; i < frames.size(); i++) {
            PetDisplayKeyframeDefinition next = frames.get(i);
            if (tick <= next.tick()) {
                double span = next.tick() - previous.tick();
                double alpha = span <= 0 ? 1.0 : (tick - previous.tick()) / span;
                return interpolate(previous.transform(), next.transform(), alpha);
            }
            previous = next;
        }
        return previous.transform();
    }

    private static PetVisualTransform interpolate(PetVisualTransform from, PetVisualTransform to, double alpha) {
        return new PetVisualTransform(lerp(from.translation(), to.translation(), alpha),
                lerp(from.rotation(), to.rotation(), alpha), lerp(from.scale(), to.scale(), alpha));
    }

    private static PetVector3 lerp(PetVector3 from, PetVector3 to, double alpha) {
        return new PetVector3(from.x() + (to.x() - from.x()) * alpha,
                from.y() + (to.y() - from.y()) * alpha,
                from.z() + (to.z() - from.z()) * alpha);
    }

    private static Map<String, BaseTransform> captureBaseTransforms(PetVisualHandle handle,
                                                                    PetDisplayModelDefinition model) {
        Map<String, BaseTransform> transforms = new LinkedHashMap<>();
        for (PetVisualNodeDefinition bone : model.skeleton().nodes()) {
            Entity entity = handle.component(bone.id()).flatMap(component -> component.serverEntity()).orElse(null);
            if (!(entity instanceof Display display)) {
                throw new IllegalStateException("DISPLAY_MODEL part Display entity üretmedi: " + bone.id());
            }
            display.setBillboard(Display.Billboard.FIXED);
            display.setInterpolationDuration(1);
            Transformation current = display.getTransformation();
            if (current == null) current = identityTransformation();
            transforms.put(bone.id(), new BaseTransform(new Vector3f(current.getTranslation()),
                    new Quaternionf(current.getLeftRotation()), new Vector3f(current.getScale()),
                    new Quaternionf(current.getRightRotation())));
        }
        return transforms;
    }

    private static void resetToBase(ModelSession session) {
        session.baseTransforms.forEach((bone, base) -> session.handle.component(bone)
                .flatMap(component -> component.serverEntity()).filter(Display.class::isInstance)
                .map(Display.class::cast).ifPresent(display -> display.setTransformation(base.transformation())));
    }

    private static PetDefinition innerDefinition(PetDefinition outer, PetDisplayModelDefinition model) {
        List<PetVisualNodeDefinition> spawnNodes = model.skeleton().nodes().stream()
                .map(node -> new PetVisualNodeDefinition(node.id(), node.parentId(), node.representation(),
                        new PetVisualTransform(node.transform().translation(), node.transform().rotation(), PetVector3.ONE)))
                .toList();
        PetVisualGraphDefinition spawnGraph = new PetVisualGraphDefinition(model.skeleton().rootId(), spawnNodes);
        return outer.toBuilder().representation(PetRepresentationDefinition.composite(spawnGraph)).build();
    }

    private static void requireCompatibleSkeleton(PetVisualGraphDefinition current, PetVisualGraphDefinition fresh) {
        if (current.nodes().size() != fresh.nodes().size() || !current.rootId().equals(fresh.rootId())) {
            throw new IllegalStateException("DISPLAY_MODEL skeleton topolojisi canlı değiştirilemez.");
        }
        for (PetVisualNodeDefinition bone : current.nodes()) {
            PetVisualNodeDefinition next = fresh.find(bone.id()).orElseThrow(() ->
                    new IllegalStateException("DISPLAY_MODEL bone canlı kaldırılamaz: " + bone.id()));
            if (!Objects.equals(bone.parentId(), next.parentId())
                    || !bone.representation().key().equals(next.representation().key())) {
                throw new IllegalStateException("DISPLAY_MODEL bone parent/provider canlı değiştirilemez: " + bone.id());
            }
        }
    }

    private static PetDisplayModelDefinition requireModel(PetDefinition definition) {
        PetDisplayModelDefinition model = definition != null ? definition.representationOrEntity().displayModel() : null;
        if (model == null) throw new IllegalArgumentException("DISPLAY_MODEL tanımı eksik.");
        return model;
    }

    private ModelSession session(PetVisualHandle handle) {
        return handle != null ? sessionsByHandle.get(handle.handleId()) : null;
    }

    private ModelSession session(Entity root) {
        return root != null && root.getUniqueId() != null ? sessionsByRoot.get(root.getUniqueId()) : null;
    }

    private static Transformation identityTransformation() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf());
    }

    private static Vector3f vector(PetVector3 value) {
        return new Vector3f((float) value.x(), (float) value.y(), (float) value.z());
    }

    private static Quaternionf quaternion(PetVector3 degrees) {
        return new Quaternionf().rotationXYZ((float) Math.toRadians(degrees.x()),
                (float) Math.toRadians(degrees.y()), (float) Math.toRadians(degrees.z()));
    }

    private static Vector3f add(Vector3f left, Vector3f right) { return left.add(right); }
    private static Vector3f multiply(Vector3f left, Vector3f right) { return left.mul(right); }

    private record BaseTransform(Vector3f translation, Quaternionf leftRotation,
                                 Vector3f scale, Quaternionf rightRotation) {
        private Transformation transformation() {
            return new Transformation(new Vector3f(translation), new Quaternionf(leftRotation),
                    new Vector3f(scale), new Quaternionf(rightRotation));
        }
    }

    private record GlobalPose(Vector3f translation, Quaternionf rotation, Vector3f scale) { }

    private static final class ModelSession {
        private final PetVisualHandle handle;
        private PetDisplayModelDefinition model;
        private PetDefinition innerDefinition;
        private Map<String, BaseTransform> baseTransforms;
        private PetVector3 modelScale;
        private PetAnimationState state = PetAnimationState.IDLE;
        private long animationTick;

        private ModelSession(PetVisualHandle handle, PetDisplayModelDefinition model,
                             PetDefinition innerDefinition, Map<String, BaseTransform> baseTransforms,
                             PetVector3 modelScale) {
            this.handle = handle;
            this.model = model;
            this.innerDefinition = innerDefinition;
            this.baseTransforms = baseTransforms;
            this.modelScale = modelScale != null ? modelScale : PetVector3.ONE;
        }
    }
}
