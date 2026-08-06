package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetOrbitDefinition;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PetDefinitionValidatorTest {

    private static PetDefinition entityDef(String entityType) {
        return new PetDefinition("wolf", "Wolf", Collections.emptyList(), entityType,
                false, false, true, false, true, true, 100, true,
                List.of("<gold>{pet_name}</gold>"), null, null);
    }

    private static PetDefinition displayDef(String material, PetVector3 scale, Integer customModelData) {
        return new PetDefinition("crystal", "Crystal", Collections.emptyList(), "WOLF",
                false, false, true, false, true, true, 100, true,
                List.of("{pet_name}"),
                new PetRepresentationDefinition(RuntimeRepresentationType.ITEM_DISPLAY, "WOLF",
                        false, false, true, false, true, material, customModelData, scale),
                null);
    }

    @Test
    void validLegacyEntityPasses() {
        assertTrue(PetDefinitionValidator.validate(entityDef("WOLF"), 1).isEmpty());
    }

    @Test
    void nonLivingEntityTypeFails() {
        List<String> errors = PetDefinitionValidator.validate(entityDef("STONE"), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("EntityType")));
    }

    @Test
    void unknownEntityTypeFails() {
        assertFalse(PetDefinitionValidator.validate(entityDef("NOT_A_THING"), 1).isEmpty());
    }

    @Test
    void validItemDisplayPasses() {
        List<String> errors = PetDefinitionValidator.validate(
                displayDef("AMETHYST_SHARD", PetVector3.ONE, 12001), 1);
        assertTrue(errors.isEmpty(), () -> "errors: " + errors);
    }

    @Test
    void invalidItemMaterialFails() {
        List<String> errors = PetDefinitionValidator.validate(
                displayDef("NOT_A_MATERIAL", PetVector3.ONE, null), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("material")));
    }

    @Test
    void invalidScaleFails() {
        List<String> errors = PetDefinitionValidator.validate(
                displayDef("AMETHYST_SHARD", new PetVector3(0.0, 1.0, 1.0), null), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("ölçek")));
    }

    @Test
    void invalidGuiMaterialFails() {
        PetDefinition base = entityDef("WOLF");
        PetDefinition withBadIcon = new PetDefinition(base.id(), base.displayName(), base.description(),
                base.entityType(), base.baby(), base.glowing(), base.invulnerable(), base.silent(), base.gravity(),
                base.progressionEnabled(), base.maxLevel(), base.nameplateEnabled(), base.nameplateFormat(),
                null, null, null, null, null, null, "NOT_A_MATERIAL", null);

        List<String> errors = PetDefinitionValidator.validate(withBadIcon, 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("gui-material")));
    }

    @Test
    void validGuiMaterialAndPermissionPass() {
        PetDefinition base = entityDef("WOLF");
        PetDefinition gated = new PetDefinition(base.id(), base.displayName(), base.description(),
                base.entityType(), base.baby(), base.glowing(), base.invulnerable(), base.silent(), base.gravity(),
                base.progressionEnabled(), base.maxLevel(), base.nameplateEnabled(), base.nameplateFormat(),
                null, null, null, null, null, null, "WOLF_SPAWN_EGG", "companionpets.pet.wolf");

        assertTrue(PetDefinitionValidator.validate(gated, 1).isEmpty());
    }

    @Test
    void transformWithInvalidApplyParticleTypeFails() {
        PetDefinition base = entityDef("WOLF");
        PetDefinition withTransform = new PetDefinition(base.id(), base.displayName(), base.description(),
                base.entityType(), base.baby(), base.glowing(), base.invulnerable(), base.silent(), base.gravity(),
                base.progressionEnabled(), base.maxLevel(), base.nameplateEnabled(), base.nameplateFormat(),
                null, null, null,
                List.of(new com.petsistemi.domain.PetTransformDefinition(
                        new com.petsistemi.domain.PetTransformCondition(
                                null, null, null, com.petsistemi.domain.PetTimeOfDay.NIGHT, null),
                        new com.petsistemi.domain.PetVisualOverride(
                                null, null, null, null, "NOT_A_PARTICLE", null, null, null))));

        List<String> errors = PetDefinitionValidator.validate(withTransform, 1);
        assertFalse(errors.isEmpty(), "geçersiz apply.particle-type sessizce kabul edilmemeli");
        assertTrue(errors.stream().anyMatch(e -> e.contains("particle-type")));
    }

    @Test
    void orbitMovementWithoutOrbitConfigFails() {
        PetDefinition def = new PetDefinition("orb", "Orb", Collections.emptyList(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                new PetRepresentationDefinition(RuntimeRepresentationType.ITEM_DISPLAY, "WOLF",
                        false, false, true, false, true, "DIAMOND", null, PetVector3.ONE),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.ORBIT,
                        0.0, 0.0, 0, 0.0, 0.0, 0.0, null));

        List<String> errors = PetDefinitionValidator.validate(def, 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("radius")));
    }

    @Test
    void allRepresentationTypesValidateWhenCorrectlyConfigured() {
        List<PetRepresentationDefinition> reps = List.of(
                new PetRepresentationDefinition(RuntimeRepresentationType.ENTITY, "WOLF", false, false, true, false, true,
                        null, null, PetVector3.ONE),
                new PetRepresentationDefinition(RuntimeRepresentationType.ITEM_DISPLAY, "WOLF", false, false, true, false, true,
                        "DIAMOND", null, PetVector3.ONE),
                new PetRepresentationDefinition(RuntimeRepresentationType.BLOCK_DISPLAY, "WOLF", false, false, true, false, true,
                        "CRYING_OBSIDIAN", null, PetVector3.ONE),
                new PetRepresentationDefinition(RuntimeRepresentationType.TEXT_DISPLAY, "WOLF", false, false, true, false, true,
                        null, null, PetVector3.ONE),
                new PetRepresentationDefinition(RuntimeRepresentationType.PARTICLE, "WOLF", false, false, true, false, true,
                        null, null, PetVector3.ONE, "SOUL_FIRE_FLAME", 6, 0.4, 0.02, 0, null),
                new PetRepresentationDefinition(RuntimeRepresentationType.INVISIBLE, "WOLF", false, false, true, false, true,
                        null, null, PetVector3.ONE),
                new PetRepresentationDefinition(RuntimeRepresentationType.MULTI_ENTITY, "WOLF", false, false, true, false, true,
                        "ALLIUM", null, PetVector3.ONE, null, 0, 0.0, 0.0, 3, "POPPY")
        );
        for (PetRepresentationDefinition rep : reps) {
            List<String> errors = PetDefinitionValidator.validate(withRepresentation(rep), 1);
            assertTrue(errors.isEmpty(), () -> rep.type() + " errors: " + errors);
        }
    }

    @Test
    void allMovementTypesValidateWhenCorrectlyConfigured() {
        PetRepresentationDefinition rep = new PetRepresentationDefinition(
                RuntimeRepresentationType.ITEM_DISPLAY, "WOLF", false, false, true, false, true,
                "DIAMOND", null, PetVector3.ONE);
        List<PetMovementDefinition> movs = List.of(
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.GROUND_FOLLOW, 0.0, 0.0, 0, 0.0, 0.0, 0.0, null),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.FLYING_FOLLOW, 0.0, 0.0, 0, 1.5, 1.1, 0.18, null),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.HOVER, 0.0, 0.0, 0, 2.2, 0.0, 0.0, null),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.ORBIT, 0.0, 0.0, 0, 0.0, 0.0, 0.0,
                        new PetOrbitDefinition(1.7, 1.4, 1.2, true)),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.TRAIL, 6.0, 0.0, 0, 0.0, 0.0, 0.0, null),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.FORMATION, 0.0, 0.0, 0, 0.0, 0.0, 0.0, null),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.SHOULDER, 0.0, 0.0, 0, 0.9, 0.0, 0.0, null),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.ANCHORED, 0.0, 0.0, 0, 0.0, 0.0, 0.0, null,
                        new com.petsistemi.domain.PetAnchorDefinition(
                                com.petsistemi.domain.PetAnchorPosition.BEHIND_RIGHT, 1.8, 0.4, true)),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.STATIC_NEAR_OWNER, 0.0, 0.0, 0, 0.0, 0.0, 0.0, null),
                new PetMovementDefinition(com.petsistemi.domain.PetMovementType.TELEPORT_ONLY, 0.0, 0.0, 0, 0.0, 0.0, 0.0, null)
        );
        for (PetMovementDefinition mov : movs) {
            List<String> errors = PetDefinitionValidator.validate(withMovement(rep, mov), 1);
            assertTrue(errors.isEmpty(), () -> mov.type() + " errors: " + errors);
        }
    }

    @Test
    void unknownSchemaVersionFails() {
        assertFalse(PetDefinitionValidator.validate(entityDef("WOLF"), 99).isEmpty());
    }

    private static PetDefinition withRepresentation(PetRepresentationDefinition rep) {
        return new PetDefinition("test", "Test", Collections.emptyList(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                rep, null);
    }

    private static PetDefinition withMovement(PetRepresentationDefinition rep, PetMovementDefinition mov) {
        return new PetDefinition("test", "Test", Collections.emptyList(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                rep, mov);
    }

    private static PetDefinition withMovementType(PetMovementDefinition mov) {
        return new PetDefinition("test_mov", "Test Mov", Collections.emptyList(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                null, mov, null, null);
    }

    private static PetDefinition withStates(com.petsistemi.domain.PetStatesDefinition states) {
        return new PetDefinition("test", "Test", Collections.emptyList(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                null, null, states);
    }

    private static PetDefinition withTransforms(java.util.List<com.petsistemi.domain.PetTransformDefinition> transforms) {
        return new PetDefinition("test", "Test", Collections.emptyList(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                null, null, null, transforms);
    }

    @Test
    void validBlockDisplayPasses() {
        PetRepresentationDefinition rep = new PetRepresentationDefinition(
                RuntimeRepresentationType.BLOCK_DISPLAY, "WOLF", false, false, true, false, true,
                "CRYING_OBSIDIAN", null, PetVector3.ONE);
        assertTrue(PetDefinitionValidator.validate(withRepresentation(rep), 1).isEmpty());
    }

    @Test
    void blockDisplayRequiresBlockMaterial() {
        PetRepresentationDefinition rep = new PetRepresentationDefinition(
                RuntimeRepresentationType.BLOCK_DISPLAY, "WOLF", false, false, true, false, true,
                "DIAMOND", null, PetVector3.ONE);
        List<String> errors = PetDefinitionValidator.validate(withRepresentation(rep), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("BLOCK_DISPLAY")));
    }

    @Test
    void blockDisplayRequiresMaterial() {
        PetRepresentationDefinition rep = new PetRepresentationDefinition(
                RuntimeRepresentationType.BLOCK_DISPLAY, "WOLF", false, false, true, false, true,
                null, null, PetVector3.ONE);
        assertFalse(PetDefinitionValidator.validate(withRepresentation(rep), 1).isEmpty());
    }

    @Test
    void textDisplayPassesWithoutMaterial() {
        PetRepresentationDefinition rep = new PetRepresentationDefinition(
                RuntimeRepresentationType.TEXT_DISPLAY, "WOLF", false, false, true, false, true,
                null, null, PetVector3.ONE);
        assertTrue(PetDefinitionValidator.validate(withRepresentation(rep), 1).isEmpty());
    }

    @Test
    void particleRequiresValidTypeAndCount() {
        PetRepresentationDefinition valid = new PetRepresentationDefinition(
                RuntimeRepresentationType.PARTICLE, "WOLF", false, false, true, false, true,
                null, null, PetVector3.ONE, "SOUL_FIRE_FLAME", 6, 0.4, 0.02, 0, null);
        assertTrue(PetDefinitionValidator.validate(withRepresentation(valid), 1).isEmpty());

        PetRepresentationDefinition badType = new PetRepresentationDefinition(
                RuntimeRepresentationType.PARTICLE, "WOLF", false, false, true, false, true,
                null, null, PetVector3.ONE, "NOT_A_PARTICLE", 6, 0.4, 0.02, 0, null);
        assertFalse(PetDefinitionValidator.validate(withRepresentation(badType), 1).isEmpty());

        PetRepresentationDefinition zeroCount = new PetRepresentationDefinition(
                RuntimeRepresentationType.PARTICLE, "WOLF", false, false, true, false, true,
                null, null, PetVector3.ONE, "SOUL_FIRE_FLAME", 0, 0.4, 0.02, 0, null);
        assertFalse(PetDefinitionValidator.validate(withRepresentation(zeroCount), 1).isEmpty());
    }

    @Test
    void invisiblePasses() {
        PetRepresentationDefinition rep = new PetRepresentationDefinition(
                RuntimeRepresentationType.INVISIBLE, "WOLF", false, false, true, false, true,
                null, null, PetVector3.ONE);
        assertTrue(PetDefinitionValidator.validate(withRepresentation(rep), 1).isEmpty());
    }

    @Test
    void multiEntityRequiresChildCountInRange() {
        PetRepresentationDefinition valid = new PetRepresentationDefinition(
                RuntimeRepresentationType.MULTI_ENTITY, "WOLF", false, false, true, false, true,
                "ALLIUM", null, PetVector3.ONE, null, 0, 0.0, 0.0, 3, "POPPY");
        assertTrue(PetDefinitionValidator.validate(withRepresentation(valid), 1).isEmpty());

        PetRepresentationDefinition noChildren = new PetRepresentationDefinition(
                RuntimeRepresentationType.MULTI_ENTITY, "WOLF", false, false, true, false, true,
                "ALLIUM", null, PetVector3.ONE, null, 0, 0.0, 0.0, 0, "POPPY");
        assertFalse(PetDefinitionValidator.validate(withRepresentation(noChildren), 1).isEmpty());

        PetRepresentationDefinition badChildMaterial = new PetRepresentationDefinition(
                RuntimeRepresentationType.MULTI_ENTITY, "WOLF", false, false, true, false, true,
                "ALLIUM", null, PetVector3.ONE, null, 0, 0.0, 0.0, 3, "NOT_A_MATERIAL");
        assertFalse(PetDefinitionValidator.validate(withRepresentation(badChildMaterial), 1).isEmpty());
    }

    @Test
    void anchoredMovementPasses() {
        PetRepresentationDefinition rep = new PetRepresentationDefinition(
                RuntimeRepresentationType.ITEM_DISPLAY, "WOLF", false, false, true, false, true,
                "DIAMOND", null, PetVector3.ONE);
        PetMovementDefinition mov = new PetMovementDefinition(
                com.petsistemi.domain.PetMovementType.ANCHORED, 0.0, 0.0, 0, 0.0, 0.0, 0.0, null,
                new com.petsistemi.domain.PetAnchorDefinition(
                        com.petsistemi.domain.PetAnchorPosition.RIGHT_SHOULDER, 1.8, 0.6, true));
        assertTrue(PetDefinitionValidator.validate(withMovement(rep, mov), 1).isEmpty());
    }

    @Test
    void validStatesPass() {
        com.petsistemi.domain.PetStatesDefinition states = new com.petsistemi.domain.PetStatesDefinition(
                new com.petsistemi.domain.PetStateDefinition(0, com.petsistemi.domain.PetIdleAnimation.WALK),
                new com.petsistemi.domain.PetStateDefinition(100, com.petsistemi.domain.PetIdleAnimation.SLEEP));
        assertTrue(PetDefinitionValidator.validate(withStates(states), 1).isEmpty());
    }

    @Test
    void idleStateRejectsWalkAnimation() {
        com.petsistemi.domain.PetStatesDefinition states = new com.petsistemi.domain.PetStatesDefinition(
                null,
                new com.petsistemi.domain.PetStateDefinition(100, com.petsistemi.domain.PetIdleAnimation.WALK));
        List<String> errors = PetDefinitionValidator.validate(withStates(states), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("states.IDLE.animation")));
    }

    @Test
    void movingStateRejectsSleepAnimation() {
        com.petsistemi.domain.PetStatesDefinition states = new com.petsistemi.domain.PetStatesDefinition(
                new com.petsistemi.domain.PetStateDefinition(0, com.petsistemi.domain.PetIdleAnimation.SLEEP),
                null);
        List<String> errors = PetDefinitionValidator.validate(withStates(states), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("states.MOVING.animation")));
    }

    @Test
    void emptyStatesSectionFails() {
        com.petsistemi.domain.PetStatesDefinition states = new com.petsistemi.domain.PetStatesDefinition(null, null);
        List<String> errors = PetDefinitionValidator.validate(withStates(states), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("states")));
    }

    @Test
    void validTransformPasses() {
        com.petsistemi.domain.PetTransformDefinition transform = new com.petsistemi.domain.PetTransformDefinition(
                new com.petsistemi.domain.PetTransformCondition(
                        com.petsistemi.domain.PetOwnerState.FLYING, "PLAINS", "world",
                        com.petsistemi.domain.PetTimeOfDay.NIGHT, com.petsistemi.domain.PetWeather.RAIN),
                new com.petsistemi.domain.PetVisualOverride("SOUL_LANTERN", null, null,
                        new PetVector3(0.9, 0.9, 0.9), null, null, true, null));
        assertTrue(PetDefinitionValidator.validate(withTransforms(List.of(transform)), 1).isEmpty());
    }

    @Test
    void transformWithoutConditionsFails() {
        com.petsistemi.domain.PetTransformDefinition transform = new com.petsistemi.domain.PetTransformDefinition(
                null,
                new com.petsistemi.domain.PetVisualOverride(null, null, null, null, null, null, true, null));
        List<String> errors = PetDefinitionValidator.validate(withTransforms(List.of(transform)), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("when")));
    }

    @Test
    void transformWithoutApplyFails() {
        com.petsistemi.domain.PetTransformDefinition transform = new com.petsistemi.domain.PetTransformDefinition(
                new com.petsistemi.domain.PetTransformCondition(null, "PLAINS", null, null, null),
                null);
        List<String> errors = PetDefinitionValidator.validate(withTransforms(List.of(transform)), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("apply")));
    }

    @Test
    void unknownTransformBiomeFails() {
        com.petsistemi.domain.PetTransformDefinition transform = new com.petsistemi.domain.PetTransformDefinition(
                new com.petsistemi.domain.PetTransformCondition(null, "NOT_A_BIOME", null, null, null),
                new com.petsistemi.domain.PetVisualOverride(null, null, null, null, null, null, true, null));
        List<String> errors = PetDefinitionValidator.validate(withTransforms(List.of(transform)), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("biome")));
    }

    @Test
    void invalidTransformScaleFails() {
        com.petsistemi.domain.PetTransformDefinition transform = new com.petsistemi.domain.PetTransformDefinition(
                new com.petsistemi.domain.PetTransformCondition(null, "PLAINS", null, null, null),
                new com.petsistemi.domain.PetVisualOverride(null, null, null,
                        new PetVector3(0.0, 1.0, 1.0), null, null, null, null));
        List<String> errors = PetDefinitionValidator.validate(withTransforms(List.of(transform)), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("scale")));
    }

    @Test
    void invalidTransformMaterialFails() {
        com.petsistemi.domain.PetTransformDefinition transform = new com.petsistemi.domain.PetTransformDefinition(
                new com.petsistemi.domain.PetTransformCondition(null, "PLAINS", null, null, null),
                new com.petsistemi.domain.PetVisualOverride("NOT_A_MATERIAL", null, null, null, null, null, null, null));
        List<String> errors = PetDefinitionValidator.validate(withTransforms(List.of(transform)), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("item-material")));
    }

    private static PetDefinition withReactions(Map<com.petsistemi.domain.PetReactionType,
            com.petsistemi.domain.PetReactionDefinition> reactions) {
        return new PetDefinition("cat", "Cat", Collections.emptyList(), "CAT",
                false, false, true, false, true, true, 100, true,
                List.of("{pet_name}"), null, null, null, null, reactions, null);
    }

    private static PetDefinition withEmotes(Map<String, com.petsistemi.domain.PetEmoteDefinition> emotes) {
        return new PetDefinition("cat", "Cat", Collections.emptyList(), "CAT",
                false, false, true, false, true, true, 100, true,
                List.of("{pet_name}"), null, null, null, null, null, emotes);
    }

    @Test
    void validReactionsPass() {
        Map<com.petsistemi.domain.PetReactionType, com.petsistemi.domain.PetReactionDefinition> reactions = Map.of(
                com.petsistemi.domain.PetReactionType.OWNER_DAMAGE,
                new com.petsistemi.domain.PetReactionDefinition(true, "ENTITY_CAT_HISS", "VILLAGER_ANGRY", 4, 0.9));
        assertTrue(PetDefinitionValidator.validate(withReactions(reactions), 1).isEmpty());
    }

    @Test
    void invalidReactionSoundAndParticleFail() {
        Map<com.petsistemi.domain.PetReactionType, com.petsistemi.domain.PetReactionDefinition> reactions = Map.of(
                com.petsistemi.domain.PetReactionType.OWNER_DAMAGE,
                new com.petsistemi.domain.PetReactionDefinition(true, "NOT_A_SOUND", "NOT_A_PARTICLE", 4, 0.9));
        List<String> errors = PetDefinitionValidator.validate(withReactions(reactions), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("sound")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("particle")));
    }

    @Test
    void outOfRangeReactionCountAndVolumeFail() {
        Map<com.petsistemi.domain.PetReactionType, com.petsistemi.domain.PetReactionDefinition> reactions = Map.of(
                com.petsistemi.domain.PetReactionType.LEVEL_UP,
                new com.petsistemi.domain.PetReactionDefinition(true, null, null, 900, 3.0));
        List<String> errors = PetDefinitionValidator.validate(withReactions(reactions), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("particle-count")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("volume")));
    }

    @Test
    void validEmotesPass() {
        Map<String, com.petsistemi.domain.PetEmoteDefinition> emotes = Map.of(
                "purr", new com.petsistemi.domain.PetEmoteDefinition(true, "ENTITY_CAT_PURR", "HEART", 5, 10));
        assertTrue(PetDefinitionValidator.validate(withEmotes(emotes), 1).isEmpty());
    }

    @Test
    void invalidEmoteNameFails() {
        Map<String, com.petsistemi.domain.PetEmoteDefinition> emotes = Map.of(
                "Bad Name!", new com.petsistemi.domain.PetEmoteDefinition(true, "ENTITY_CAT_PURR", "HEART", 5, 10));
        List<String> errors = PetDefinitionValidator.validate(withEmotes(emotes), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("emote")));
    }

    @Test
    void invalidEmoteSoundAndParticleFail() {
        Map<String, com.petsistemi.domain.PetEmoteDefinition> emotes = Map.of(
                "purr", new com.petsistemi.domain.PetEmoteDefinition(true, "NOT_A_SOUND", "NOT_A_PARTICLE", 500, 10));
        List<String> errors = PetDefinitionValidator.validate(withEmotes(emotes), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("sound")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("particle")));
    }

    @Test
    void outOfRangeEmoteParticleCountFails() {
        Map<String, com.petsistemi.domain.PetEmoteDefinition> emotes = Map.of(
                "purr", new com.petsistemi.domain.PetEmoteDefinition(true, "ENTITY_CAT_PURR", "HEART", 501, 10));
        List<String> errors = PetDefinitionValidator.validate(withEmotes(emotes), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("particle-count")));
    }

    @Test
    void newMovementTypesAreAccepted() {
        com.petsistemi.domain.PetMovementDefinition echo = new com.petsistemi.domain.PetMovementDefinition(
                com.petsistemi.domain.PetMovementType.ECHO, 8.0, 30.0, 0, 0.0, 0.0, 0.0, null);
        assertTrue(PetDefinitionValidator.validate(withMovementType(echo), 1).isEmpty());

        com.petsistemi.domain.PetMovementDefinition shadow = new com.petsistemi.domain.PetMovementDefinition(
                com.petsistemi.domain.PetMovementType.SHADOW_TRAIL, 5.0, 0.0, 0, 0.0, 0.0, 0.0, null);
        assertTrue(PetDefinitionValidator.validate(withMovementType(shadow), 1).isEmpty());

        com.petsistemi.domain.PetMovementDefinition roam = new com.petsistemi.domain.PetMovementDefinition(
                com.petsistemi.domain.PetMovementType.ROAM_NEAR_OWNER, 4.0, 0.0, 0, 0.0, 0.0, 0.12, null);
        assertTrue(PetDefinitionValidator.validate(withMovementType(roam), 1).isEmpty());

        com.petsistemi.domain.PetMovementDefinition mirror = new com.petsistemi.domain.PetMovementDefinition(
                com.petsistemi.domain.PetMovementType.MIRROR, 0.0, 0.0, 0, 0.0, 1.2, 0.0, null, null, 10);
        assertTrue(PetDefinitionValidator.validate(withMovementType(mirror), 1).isEmpty());
    }

    @Test
    void outOfRangeDelayTicksFails() {
        com.petsistemi.domain.PetMovementDefinition mov = new com.petsistemi.domain.PetMovementDefinition(
                com.petsistemi.domain.PetMovementType.MIRROR, 0.0, 0.0, 0, 0.0, 1.2, 0.0, null, null, 700);
        List<String> errors = PetDefinitionValidator.validate(withMovementType(mov), 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("delay-ticks")));
    }
}
