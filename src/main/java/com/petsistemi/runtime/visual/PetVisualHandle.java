package com.petsistemi.runtime.visual;

import com.petsistemi.domain.RuntimeKeyResolver;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.visual.PetVisualTransform;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One spawned visual graph. It replaces the implicit "primary entity + anonymous children"
 * contract with stable component ids while retaining a legacy primary-entity view.
 */
public final class PetVisualHandle {
    public static final String ROOT_COMPONENT = "root";

    private final UUID handleId;
    private final PetRenderBackend backend;
    private final String rootComponentId;
    private final Map<String, PetVisualComponent> components;

    private PetVisualHandle(UUID handleId, PetRenderBackend backend, String rootComponentId,
                            Map<String, PetVisualComponent> components) {
        this.handleId = handleId != null ? handleId : UUID.randomUUID();
        this.backend = backend != null ? backend : PetRenderBackend.SERVER;
        this.rootComponentId = rootComponentId;
        this.components = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(components));
        validate();
    }

    public static Builder builder(String rootComponentId, PetRenderBackend backend) {
        return new Builder(rootComponentId, backend);
    }

    public static PetVisualHandle legacy(NamespacedKey representationKey, Entity primary,
                                         Collection<? extends Entity> children) {
        NamespacedKey key = representationKey != null ? representationKey
                : RuntimeKeyResolver.representationKey(RuntimeRepresentationType.ENTITY);
        Builder builder = builder(ROOT_COMPONENT, PetRenderBackend.SERVER)
                .component(new PetVisualComponent(ROOT_COMPONENT, null, key,
                        PetVisualTransform.IDENTITY, primary));
        int index = 1;
        if (children != null) {
            for (Entity child : children) {
                builder.component(new PetVisualComponent("child-" + index++, ROOT_COMPONENT, key,
                        PetVisualTransform.IDENTITY, child));
            }
        }
        return builder.build();
    }

    public UUID handleId() { return handleId; }
    public PetRenderBackend backend() { return backend; }
    public String rootComponentId() { return rootComponentId; }
    public Collection<PetVisualComponent> components() { return components.values(); }
    public Optional<PetVisualComponent> component(String id) { return Optional.ofNullable(components.get(id)); }
    public PetVisualComponent rootComponent() { return components.get(rootComponentId); }
    public Optional<Entity> primaryEntity() { return rootComponent().serverEntity(); }

    public List<Entity> serverEntities() {
        java.util.Set<Entity> unique = new java.util.LinkedHashSet<>();
        for (PetVisualComponent component : components.values()) {
            component.serverEntity().ifPresent(unique::add);
        }
        return List.copyOf(unique);
    }

    public List<Entity> secondaryEntities() {
        Entity primary = primaryEntity().orElse(null);
        return serverEntities().stream().filter(entity -> entity != primary).toList();
    }

    public Optional<PetVisualComponent> componentForEntity(UUID entityId) {
        if (entityId == null) return Optional.empty();
        return components.values().stream().filter(component -> component.serverEntity()
                .map(entity -> entityId.equals(entity.getUniqueId())).orElse(false)).findFirst();
    }

    public boolean isValid() {
        if (backend == PetRenderBackend.VIRTUAL) return !components.isEmpty();
        return primaryEntity().map(entity -> entity.isValid() && !entity.isDead()).orElse(false);
    }

    private void validate() {
        if (rootComponentId == null || !components.containsKey(rootComponentId)) {
            throw new IllegalArgumentException("Runtime visual root component bulunamadı: " + rootComponentId);
        }
        PetVisualComponent root = components.get(rootComponentId);
        if (root.parentId() != null) throw new IllegalArgumentException("Runtime visual root parent taşıyamaz.");
        for (PetVisualComponent component : components.values()) {
            if (!component.id().equals(rootComponentId) && component.parentId() == null) {
                throw new IllegalArgumentException("Root dışındaki runtime visual component parent taşımalıdır: " + component.id());
            }
            if (component.parentId() != null && !components.containsKey(component.parentId())) {
                throw new IllegalArgumentException(component.id() + " runtime parent component bulunamadı: " + component.parentId());
            }
            java.util.Set<String> visited = new java.util.HashSet<>();
            PetVisualComponent current = component;
            while (current != null) {
                if (!visited.add(current.id())) throw new IllegalArgumentException("Runtime visual component döngüsü: " + component.id());
                current = current.parentId() != null ? components.get(current.parentId()) : null;
            }
        }
    }

    public static final class Builder {
        private final String rootComponentId;
        private final PetRenderBackend backend;
        private final Map<String, PetVisualComponent> components = new LinkedHashMap<>();
        private UUID handleId;

        private Builder(String rootComponentId, PetRenderBackend backend) {
            this.rootComponentId = rootComponentId;
            this.backend = backend;
        }

        public Builder handleId(UUID handleId) { this.handleId = handleId; return this; }
        public Builder component(PetVisualComponent component) {
            if (component == null) throw new IllegalArgumentException("Runtime visual component null olamaz.");
            if (components.putIfAbsent(component.id(), component) != null) {
                throw new IllegalArgumentException("Tekrarlı runtime visual component id: " + component.id());
            }
            return this;
        }
        public PetVisualHandle build() { return new PetVisualHandle(handleId, backend, rootComponentId, components); }
    }
}
