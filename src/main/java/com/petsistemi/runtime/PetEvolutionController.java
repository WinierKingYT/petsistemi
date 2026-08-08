package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetEvolutionDefinition;
import org.bukkit.entity.Entity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Resolves the highest level-qualified evolution stage and maintains its derived definition. */
public final class PetEvolutionController {

    private final PetDefinitionRegistry definitions;
    private final PetRepresentationRegistry representations;
    private final Map<UUID, String> activeStages = new HashMap<>();
    private final Map<UUID, PetDefinition> activeDefinitions = new HashMap<>();

    public PetEvolutionController(PetDefinitionRegistry definitions, PetRepresentationRegistry representations) {
        this.definitions = definitions;
        this.representations = representations;
    }

    public void tick(ActivePet active, Entity entity) {
        if (active == null) return;
        PetDefinition base = resolveBase(active);
        PetEvolutionDefinition stage = selectStage(base, active.getLevel());
        PetDefinition derived = derive(base, stage);
        String signature = stage == null ? "base:" + java.util.Objects.hashCode(base)
                : stage.minLevel() + ":" + stage.targetDefinitionId() + ":" + java.util.Objects.hashCode(derived);
        if (signature.equals(activeStages.get(active.getPetId()))) return;

        activeStages.put(active.getPetId(), signature);
        if (derived == null || stage == null) activeDefinitions.remove(active.getPetId());
        else activeDefinitions.put(active.getPetId(), derived);
        reRender(active, entity, derived != null ? derived : base);
    }

    public PetDefinition activeDefinition(ActivePet active) {
        if (active == null) return null;
        return activeDefinitions.getOrDefault(active.getPetId(), resolveBase(active));
    }

    public void cleanup(UUID petId) {
        if (petId == null) return;
        activeStages.remove(petId);
        activeDefinitions.remove(petId);
    }

    static PetEvolutionDefinition selectStage(PetDefinition definition, int level) {
        if (definition == null || definition.evolutions() == null) return null;
        return definition.evolutions().stream()
                .filter(stage -> stage != null && level >= stage.minLevel())
                .max(Comparator.comparingInt(PetEvolutionDefinition::minLevel))
                .orElse(null);
    }

    private PetDefinition derive(PetDefinition base, PetEvolutionDefinition stage) {
        if (base == null || stage == null) return base;
        PetDefinition target = definitions.find(stage.targetDefinitionId()).orElse(base);
        return base.withEvolutionApplied(stage, target);
    }

    private PetDefinition resolveBase(ActivePet active) {
        return active == null || active.getDefinitionId() == null
                ? null : definitions.find(active.getDefinitionId()).orElse(null);
    }

    private void reRender(ActivePet active, Entity entity, PetDefinition definition) {
        if (definition == null || representations == null) return;
        PetRepresentationController representation = representations.get(active.getRepresentationKey());
        if (representation == null) return;
        if (active.getVisualHandle() != null) {
            representation.updateVisualHandle(active.getVisualHandle(), active.getPetInstance(), definition);
            if (active.isResting()) representation.applyRestStateHandle(active.getVisualHandle(), active.getPetInstance(), definition, true);
        } else if (entity != null) {
            representation.updateVisual(entity, active.getPetInstance(), definition);
            if (active.isResting()) representation.applyRestState(entity, active.getPetInstance(), definition, true);
        }
    }
}
