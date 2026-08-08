package com.petsistemi.domain.visual;

import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PetVisualGraphDefinitionTest {
    private static PetVisualNodeDefinition node(String id, String parent) {
        return new PetVisualNodeDefinition(id, parent,
                PetRepresentationDefinition.legacyEntity("ARMOR_STAND", false, false, true, true, false),
                PetVisualTransform.IDENTITY);
    }

    @Test void acceptsNamedHierarchyAndFindsChildren() {
        PetVisualGraphDefinition graph = new PetVisualGraphDefinition("body", List.of(
                node("body", null), node("head", "body"), node("left-wing", "body"), node("tip", "left-wing")));

        assertEquals("head", graph.find("HEAD").orElseThrow().id());
        assertEquals(List.of("head", "left-wing"), graph.childrenOf("body").stream()
                .map(PetVisualNodeDefinition::id).toList());
    }

    @Test void rejectsDuplicateMissingParentAndCycles() {
        assertThrows(IllegalArgumentException.class, () ->
                new PetVisualGraphDefinition("body", List.of(node("body", null), node("body", null))));
        assertThrows(IllegalArgumentException.class, () ->
                new PetVisualGraphDefinition("body", List.of(node("body", null), node("head", "missing"))));
        assertThrows(IllegalArgumentException.class, () ->
                new PetVisualGraphDefinition("body", List.of(node("body", null), node("a", "b"), node("b", "a"))));
    }

    @Test void transformDefaultsAndRejectsNonPositiveScale() {
        PetVisualTransform transform = new PetVisualTransform(null, null, null);
        assertEquals(PetVector3.ZERO, transform.translation());
        assertEquals(PetVector3.ONE, transform.scale());
        assertThrows(IllegalArgumentException.class, () ->
                new PetVisualTransform(PetVector3.ZERO, PetVector3.ZERO, new PetVector3(1, 0, 1)));
    }
}
