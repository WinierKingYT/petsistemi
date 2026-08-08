package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.PetVisualOverride;
import com.petsistemi.domain.visual.PetVisualGraphDefinition;
import com.petsistemi.domain.visual.PetVisualNodeDefinition;
import com.petsistemi.domain.visual.PetVisualTransform;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import com.petsistemi.runtime.visual.PetRenderBackend;
import com.petsistemi.runtime.visual.PetVisualComponent;
import com.petsistemi.runtime.visual.PetVisualHandle;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph-native representation that composes existing representation controllers.
 * It owns hierarchy/transform/lifecycle only; each node's content stays with its
 * registered atomic controller.
 */
public final class CompositePetRepresentation implements PetRepresentationController {

    private final PetRepresentationRegistry registry;
    private final Map<UUID, CompositeSession> sessionsByHandle = new ConcurrentHashMap<>();
    private final Map<UUID, CompositeSession> sessionsByRootEntity = new ConcurrentHashMap<>();

    public CompositePetRepresentation(PetRepresentationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "representation registry null olamaz.");
    }

    @Override
    public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
        return spawnVisual(pet, definition, owner).primaryEntity().orElseThrow();
    }

    @Override
    public PetVisualHandle spawnVisual(PetInstance pet, PetDefinition definition, Player owner) {
        PetVisualGraphDefinition graph = requireGraph(definition);
        List<NodeRuntime> spawned = new ArrayList<>();
        try {
            PetVisualHandle.Builder composite = PetVisualHandle.builder(graph.rootId(), PetRenderBackend.SERVER);
            for (PetVisualNodeDefinition node : graph.topologicalNodes()) {
                PetRepresentationController controller = registry.get(node.representation().key());
                if (controller == null) {
                    throw new IllegalStateException("COMPOSITE component provider kayıtlı değil: "
                            + node.representation().key() + " (component=" + node.id() + ")");
                }
                if (controller == this || node.representation().type() == RuntimeRepresentationType.COMPOSITE) {
                    throw new IllegalStateException("İç içe COMPOSITE component desteklenmez: " + node.id());
                }

                PetDefinition componentDefinition = componentDefinition(definition, node,
                        node.id().equals(graph.rootId()));
                PetVisualHandle componentHandle = Objects.requireNonNull(
                        controller.spawnVisual(pet, componentDefinition, owner),
                        "Component controller null visual handle döndürdü: " + node.id());
                Entity componentRoot = componentHandle.primaryEntity().orElseThrow(() ->
                        new IllegalStateException("COMPOSITE component server entity anchor üretmedi: " + node.id()));

                composite.component(new PetVisualComponent(node.id(), node.parentId(),
                        node.representation().key(), node.transform(), componentRoot));
                for (PetVisualComponent part : componentHandle.components()) {
                    if (part.id().equals(componentHandle.rootComponentId())) continue;
                    String id = nestedComponentId(node.id(), part.id());
                    String parent = part.parentId() == null || part.parentId().equals(componentHandle.rootComponentId())
                            ? node.id() : nestedComponentId(node.id(), part.parentId());
                    composite.component(new PetVisualComponent(id, parent, part.representationKey(),
                            part.localTransform(), part.entity()));
                }
                spawned.add(new NodeRuntime(node, componentDefinition, controller, componentHandle));
            }

            PetVisualHandle handle = composite.build();
            CompositeSession session = new CompositeSession(handle, graph, spawned);
            synchronize(session);
            sessionsByHandle.put(handle.handleId(), session);
            UUID rootEntityId = handle.primaryEntity().map(Entity::getUniqueId).orElse(null);
            if (rootEntityId != null) sessionsByRootEntity.put(rootEntityId, session);
            return handle;
        } catch (RuntimeException exception) {
            RuntimeException cleanupFailure = cleanupSpawned(spawned);
            if (cleanupFailure != null) exception.addSuppressed(cleanupFailure);
            throw exception;
        }
    }

    @Override
    public void tickVisualHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition, Player owner) {
        CompositeSession session = session(visual);
        if (session == null) return;
        synchronize(session);
        for (NodeRuntime node : session.nodes) {
            node.controller.tickVisualHandle(node.handle, pet, node.definition, owner);
        }
    }

    @Override
    public void updateVisualHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition) {
        CompositeSession session = session(visual);
        if (session == null) return;
        PetVisualGraphDefinition freshGraph = requireGraph(definition);
        if (freshGraph.nodes().size() != session.nodes.size()) {
            throw new IllegalStateException("COMPOSITE component topolojisi canlı değiştirilemez.");
        }
        for (NodeRuntime runtime : session.nodes) {
            PetVisualNodeDefinition fresh = freshGraph.find(runtime.node.id()).orElseThrow(() ->
                    new IllegalStateException("COMPOSITE component canlı kaldırılamaz: " + runtime.node.id()));
            if (!Objects.equals(fresh.parentId(), runtime.node.parentId())) {
                throw new IllegalStateException("COMPOSITE component parent ilişkisi canlı değiştirilemez: "
                        + runtime.node.id());
            }
            if (!fresh.representation().key().equals(runtime.node.representation().key())) {
                throw new IllegalStateException("COMPOSITE component provider canlı değiştirilemez: " + runtime.node.id());
            }
            runtime.node = fresh;
            runtime.definition = componentDefinition(definition, fresh, fresh.id().equals(freshGraph.rootId()));
            runtime.controller.updateVisualHandle(runtime.handle, pet, runtime.definition);
        }
        session.graph = freshGraph;
        synchronize(session);
    }

    @Override
    public void applyRestStateHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition,
                                     boolean resting) {
        CompositeSession session = session(visual);
        if (session == null) return;
        for (NodeRuntime node : session.nodes) {
            node.controller.applyRestStateHandle(node.handle, pet, node.definition, resting);
        }
    }

    @Override
    public void applyAnimationHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition,
                                     PetAnimationTransition transition) {
        CompositeSession session = session(visual);
        if (session == null) return;
        for (NodeRuntime node : session.nodes) {
            node.controller.applyAnimationHandle(node.handle, pet, node.definition, transition);
        }
    }

    @Override
    public void removeVisualHandle(PetVisualHandle visual) {
        CompositeSession session = session(visual);
        if (session == null) {
            PetRepresentationController.super.removeVisualHandle(visual);
            return;
        }
        sessionsByHandle.remove(session.handle.handleId());
        session.handle.primaryEntity().map(Entity::getUniqueId).ifPresent(sessionsByRootEntity::remove);
        RuntimeException failure = cleanupSpawned(session.nodes);
        if (failure != null) throw failure;
    }

    @Override
    public boolean isVisualHandleValid(PetVisualHandle visual) {
        CompositeSession session = session(visual);
        if (session == null || !visual.isValid()) return false;
        return session.nodes.stream().allMatch(node -> node.controller.isVisualHandleValid(node.handle));
    }

    @Override
    public void tickVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition, Player owner) {
        CompositeSession session = session(primaryEntity);
        if (session != null) tickVisualHandle(session.handle, pet, definition, owner);
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        CompositeSession session = session(primaryEntity);
        if (session != null) updateVisualHandle(session.handle, pet, definition);
    }

    @Override
    public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        CompositeSession session = session(primaryEntity);
        if (session != null) applyRestStateHandle(session.handle, pet, definition, resting);
    }

    @Override
    public void applyAnimation(Entity primaryEntity, PetInstance pet, PetDefinition definition,
                               PetAnimationTransition transition) {
        CompositeSession session = session(primaryEntity);
        if (session != null) applyAnimationHandle(session.handle, pet, definition, transition);
    }

    @Override
    public void remove(Entity primaryEntity) {
        CompositeSession session = session(primaryEntity);
        if (session != null) removeVisualHandle(session.handle);
        else if (primaryEntity != null && primaryEntity.isValid()) primaryEntity.remove();
    }

    @Override
    public boolean isValid(Entity primaryEntity) {
        CompositeSession session = session(primaryEntity);
        return session != null ? isVisualHandleValid(session.handle)
                : primaryEntity != null && primaryEntity.isValid() && !primaryEntity.isDead();
    }

    private void synchronize(CompositeSession session) {
        Map<String, NodeRuntime> byId = new LinkedHashMap<>();
        for (NodeRuntime node : session.nodes) byId.put(node.node.id(), node);
        for (PetVisualNodeDefinition definition : session.graph.topologicalNodes()) {
            if (definition.parentId() == null) continue;
            NodeRuntime child = byId.get(definition.id());
            NodeRuntime parent = byId.get(definition.parentId());
            Entity childEntity = child != null ? child.handle.primaryEntity().orElse(null) : null;
            Entity parentEntity = parent != null ? parent.handle.primaryEntity().orElse(null) : null;
            if (childEntity == null || parentEntity == null || !childEntity.isValid() || !parentEntity.isValid()) continue;

            Location parentLocation = parentEntity.getLocation();
            PetVisualTransform transform = definition.transform();
            PetVector3 translation = transform.translation();
            Vector offset = new Vector(translation.x(), translation.y(), translation.z())
                    .rotateAroundY(Math.toRadians(-parentLocation.getYaw()));
            Location target = parentLocation.clone().add(offset);
            target.setYaw(parentLocation.getYaw() + (float) transform.rotation().y());
            target.setPitch(parentLocation.getPitch() + (float) transform.rotation().x());
            childEntity.teleport(target);
        }
    }

    private PetDefinition componentDefinition(PetDefinition outer, PetVisualNodeDefinition node, boolean root) {
        PetRepresentationDefinition representation = node.representation();
        PetVector3 base = representation.scale() != null ? representation.scale() : PetVector3.ONE;
        PetVector3 local = node.transform().scale();
        PetVector3 scale = new PetVector3(base.x() * local.x(), base.y() * local.y(), base.z() * local.z());
        representation = representation.applyOverride(new PetVisualOverride(
                null, null, null, scale, null, null, null, null, null));
        return outer.toBuilder()
                .representation(representation)
                .entityType(representation.entityType() != null ? representation.entityType() : "WOLF")
                .baby(representation.baby())
                .glowing(representation.glowing())
                .invulnerable(representation.invulnerable())
                .silent(representation.silent())
                .gravity(representation.gravity())
                .nameplateEnabled(root && outer.nameplateEnabled())
                .spawnStyle(root ? outer.spawnStyle() : null)
                .build();
    }

    private static PetVisualGraphDefinition requireGraph(PetDefinition definition) {
        if (definition == null || definition.representationOrEntity().visualGraph() == null) {
            throw new IllegalArgumentException("COMPOSITE visual graph eksik.");
        }
        return definition.representationOrEntity().visualGraph();
    }

    private CompositeSession session(PetVisualHandle handle) {
        return handle != null ? sessionsByHandle.get(handle.handleId()) : null;
    }

    private CompositeSession session(Entity root) {
        return root != null && root.getUniqueId() != null ? sessionsByRootEntity.get(root.getUniqueId()) : null;
    }

    private static String nestedComponentId(String nodeId, String partId) {
        String candidate = nodeId + "." + partId;
        if (candidate.length() <= 64) return candidate;
        String prefix = nodeId.substring(0, Math.min(44, nodeId.length()));
        return prefix + ".part-" + Integer.toUnsignedString(candidate.hashCode(), 36);
    }

    private static RuntimeException cleanupSpawned(List<NodeRuntime> nodes) {
        List<NodeRuntime> reverse = new ArrayList<>(nodes);
        Collections.reverse(reverse);
        RuntimeException failure = null;
        for (NodeRuntime node : reverse) {
            try {
                node.controller.removeVisualHandle(node.handle);
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
            }
        }
        return failure;
    }

    private static final class NodeRuntime {
        private PetVisualNodeDefinition node;
        private PetDefinition definition;
        private final PetRepresentationController controller;
        private final PetVisualHandle handle;

        private NodeRuntime(PetVisualNodeDefinition node, PetDefinition definition,
                            PetRepresentationController controller, PetVisualHandle handle) {
            this.node = node;
            this.definition = definition;
            this.controller = controller;
            this.handle = handle;
        }
    }

    private static final class CompositeSession {
        private final PetVisualHandle handle;
        private PetVisualGraphDefinition graph;
        private final List<NodeRuntime> nodes;

        private CompositeSession(PetVisualHandle handle, PetVisualGraphDefinition graph, List<NodeRuntime> nodes) {
            this.handle = handle;
            this.graph = graph;
            this.nodes = List.copyOf(nodes);
        }
    }
}
