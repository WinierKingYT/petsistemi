package com.petsistemi.domain.visual;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable, validated parent-child graph used by future COMPOSITE and DISPLAY_MODEL schemas. */
public record PetVisualGraphDefinition(String rootId, List<PetVisualNodeDefinition> nodes) {
    public PetVisualGraphDefinition {
        rootId = rootId == null ? "" : rootId.trim().toLowerCase(java.util.Locale.ROOT);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        Map<String, PetVisualNodeDefinition> indexed = index(nodes);
        PetVisualNodeDefinition root = indexed.get(rootId);
        if (root == null) throw new IllegalArgumentException("Visual graph root node bulunamadı: " + rootId);
        if (root.parentId() != null) throw new IllegalArgumentException("Visual graph root node parent taşıyamaz: " + rootId);
        for (PetVisualNodeDefinition node : nodes) {
            if (!node.id().equals(rootId) && node.parentId() == null) {
                throw new IllegalArgumentException("Root dışındaki visual node parent taşımalıdır: " + node.id());
            }
            if (node.parentId() != null && !indexed.containsKey(node.parentId())) {
                throw new IllegalArgumentException(node.id() + " parent node bulunamadı: " + node.parentId());
            }
            requireAcyclic(node, indexed);
        }
    }

    public Optional<PetVisualNodeDefinition> find(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.trim().toLowerCase(java.util.Locale.ROOT);
        return nodes.stream().filter(node -> node.id().equals(normalized)).findFirst();
    }

    public List<PetVisualNodeDefinition> childrenOf(String parentId) {
        if (parentId == null) return List.of();
        String normalized = parentId.trim().toLowerCase(java.util.Locale.ROOT);
        return nodes.stream().filter(node -> normalized.equals(node.parentId())).toList();
    }

    /** Parent-before-child order, independent from the order used in YAML. */
    public List<PetVisualNodeDefinition> topologicalNodes() {
        List<PetVisualNodeDefinition> ordered = new java.util.ArrayList<>(nodes.size());
        java.util.Set<String> added = new java.util.LinkedHashSet<>();
        while (ordered.size() < nodes.size()) {
            boolean progressed = false;
            for (PetVisualNodeDefinition node : nodes) {
                if (added.contains(node.id())) continue;
                if (node.parentId() == null || added.contains(node.parentId())) {
                    ordered.add(node);
                    added.add(node.id());
                    progressed = true;
                }
            }
            if (!progressed) throw new IllegalStateException("Doğrulanmış visual graph sıralanamadı.");
        }
        return List.copyOf(ordered);
    }

    private static Map<String, PetVisualNodeDefinition> index(List<PetVisualNodeDefinition> nodes) {
        Map<String, PetVisualNodeDefinition> indexed = new LinkedHashMap<>();
        for (PetVisualNodeDefinition node : nodes) {
            if (node == null) throw new IllegalArgumentException("Visual graph null node içeremez.");
            if (indexed.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Tekrarlı visual node id: " + node.id());
            }
        }
        return indexed;
    }

    private static void requireAcyclic(PetVisualNodeDefinition start,
                                       Map<String, PetVisualNodeDefinition> nodes) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        PetVisualNodeDefinition current = start;
        while (current != null) {
            if (!visited.add(current.id())) {
                throw new IllegalArgumentException("Visual graph parent döngüsü içeriyor: " + start.id());
            }
            current = current.parentId() != null ? nodes.get(current.parentId()) : null;
        }
    }
}
