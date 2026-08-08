package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetEvolutionDefinition;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.item.PetItemActionDefinition;
import com.petsistemi.domain.visual.PetVisualGraphDefinition;
import com.petsistemi.domain.visual.PetVisualNodeDefinition;
import com.petsistemi.domain.visual.PetVisualTransform;
import com.petsistemi.domain.visual.PetProceduralDefinition;
import com.petsistemi.domain.visual.PetProceduralShape;
import com.petsistemi.runtime.item.BuiltInPetItemActions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetEvolutionReferenceValidationTest {

    @Test
    void missingTargetRejectsSourceDefinition() {
        PetDefinition source = PetDefinition.builder("source", "Source")
                .evolutions(List.of(new PetEvolutionDefinition(10, "missing", null, null))).build();
        Map<String, PetDefinition> definitions = new HashMap<>(Map.of("source", source));
        Map<String, List<String>> errors = new HashMap<>();

        AtomicPetDefinitionRegistry.validateEvolutionReferences(definitions,
                Map.of("source", "source.yml"), errors);

        assertFalse(definitions.containsKey("source"));
        assertTrue(errors.get("source.yml").get(0).contains("bulunamadı"));
    }

    @Test
    void providerChangeRejectsEvolutionButSameProviderIsAccepted() {
        PetDefinition target = PetDefinition.builder("target", "Target")
                .representation(PetRepresentationDefinition.display(RuntimeRepresentationType.ITEM_DISPLAY,
                        "STONE", null, PetVector3.ONE)).build();
        PetDefinition source = PetDefinition.builder("source", "Source")
                .evolutions(List.of(new PetEvolutionDefinition(10, "target", null, null))).build();
        Map<String, PetDefinition> definitions = new HashMap<>(Map.of("source", source, "target", target));
        Map<String, List<String>> errors = new HashMap<>();

        AtomicPetDefinitionRegistry.validateEvolutionReferences(definitions,
                Map.of("source", "source.yml", "target", "target.yml"), errors);

        assertFalse(definitions.containsKey("source"));
        assertTrue(errors.get("source.yml").get(0).contains("representation"));

        PetDefinition self = PetDefinition.builder("self", "Self")
                .evolutions(List.of(new PetEvolutionDefinition(10, "self", "Evolved", null))).build();
        definitions = new HashMap<>(Map.of("self", self));
        errors = new HashMap<>();
        AtomicPetDefinitionRegistry.validateEvolutionReferences(definitions, Map.of("self", "self.yml"), errors);
        assertTrue(definitions.containsKey("self"));
        assertTrue(errors.isEmpty());
    }

    @Test
    void missingPersistentEvolutionItemTargetRejectsSourceDefinition() {
        PetItemActionDefinition action = new PetItemActionDefinition("evolve", "AMETHYST_SHARD",
                null, 1, 0, 1, 0, null, BuiltInPetItemActions.EVOLVE_PET,
                Map.of("target-id", "missing"));
        PetDefinition source = PetDefinition.builder("source", "Source").itemActions(List.of(action)).build();
        Map<String, PetDefinition> definitions = new HashMap<>(Map.of("source", source));
        Map<String, List<String>> errors = new HashMap<>();

        AtomicPetDefinitionRegistry.validateEvolutionReferences(definitions,
                Map.of("source", "source.yml"), errors);

        assertFalse(definitions.containsKey("source"));
        assertTrue(errors.get("source.yml").stream().anyMatch(error -> error.contains("item-actions")));
    }

    @Test
    void compositeEvolutionRequiresStableNodeTopologyAndProviders() {
        PetRepresentationDefinition body = PetRepresentationDefinition.display(
                RuntimeRepresentationType.ITEM_DISPLAY, "PAPER", null, PetVector3.ONE);
        PetRepresentationDefinition particle = new PetRepresentationDefinition(
                RuntimeRepresentationType.PARTICLE, "MARKER", false, false, true, true, false,
                null, null, PetVector3.ONE, "FLAME", 2, 0.1, 0, 0, null);
        PetRepresentationDefinition sourceComposite = PetRepresentationDefinition.composite(
                new PetVisualGraphDefinition("body", List.of(
                        new PetVisualNodeDefinition("body", null, body, PetVisualTransform.IDENTITY),
                        new PetVisualNodeDefinition("aura", "body", particle, PetVisualTransform.IDENTITY))));
        PetRepresentationDefinition targetComposite = PetRepresentationDefinition.composite(
                new PetVisualGraphDefinition("body", List.of(
                        new PetVisualNodeDefinition("body", null, body, PetVisualTransform.IDENTITY),
                        new PetVisualNodeDefinition("crown", "body", body, PetVisualTransform.IDENTITY))));
        PetDefinition target = PetDefinition.builder("target", "Target").representation(targetComposite).build();
        PetDefinition source = PetDefinition.builder("source", "Source").representation(sourceComposite)
                .evolutions(List.of(new PetEvolutionDefinition(10, "target", null, null))).build();
        Map<String, PetDefinition> definitions = new HashMap<>(Map.of("source", source, "target", target));
        Map<String, List<String>> errors = new HashMap<>();

        AtomicPetDefinitionRegistry.validateEvolutionReferences(definitions,
                Map.of("source", "source.yml", "target", "target.yml"), errors);

        assertFalse(definitions.containsKey("source"));
        assertTrue(errors.get("source.yml").stream().anyMatch(error -> error.contains("COMPOSITE")));
    }

    @Test
    void proceduralEvolutionRequiresStablePointCountAndContentProvider() {
        PetRepresentationDefinition content = PetRepresentationDefinition.display(
                RuntimeRepresentationType.ITEM_DISPLAY, "AMETHYST_SHARD", null, PetVector3.ONE);
        PetRepresentationDefinition sourceRepresentation = PetRepresentationDefinition.procedural(
                new PetProceduralDefinition(PetProceduralShape.RING, 8, 1.0, 1.0,
                        1.0, 0.0, 1.0, 1, content), PetVector3.ONE);
        PetRepresentationDefinition targetRepresentation = PetRepresentationDefinition.procedural(
                new PetProceduralDefinition(PetProceduralShape.SPHERE, 9, 1.0, 1.0,
                        1.0, 0.0, 1.0, 1, content), PetVector3.ONE);
        PetDefinition target = PetDefinition.builder("target", "Target")
                .representation(targetRepresentation).build();
        PetDefinition source = PetDefinition.builder("source", "Source").representation(sourceRepresentation)
                .evolutions(List.of(new PetEvolutionDefinition(10, "target", null, null))).build();
        Map<String, PetDefinition> definitions = new HashMap<>(Map.of("source", source, "target", target));
        Map<String, List<String>> errors = new HashMap<>();

        AtomicPetDefinitionRegistry.validateEvolutionReferences(definitions,
                Map.of("source", "source.yml", "target", "target.yml"), errors);

        assertFalse(definitions.containsKey("source"));
        assertTrue(errors.get("source.yml").stream().anyMatch(error -> error.contains("PROCEDURAL")));
    }
}
