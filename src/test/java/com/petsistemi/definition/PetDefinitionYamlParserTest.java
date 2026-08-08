package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.NamespacedKey;
import be.seeseemelk.mockbukkit.MockBukkit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static java.util.Objects.requireNonNull;

class PetDefinitionYamlParserTest {

    @BeforeAll
    static void startBukkitRegistry() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopBukkitRegistry() {
        MockBukkit.unmock();
    }

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return cfg;
    }

    @Test
    void legacyFlatKeysMapToEntityPetWithNullMovement() {
        YamlConfiguration cfg = yaml("""
                display-name: "Wolf"
                entity-type: WOLF
                baby: true
                glowing: true
                invulnerable: false
                silent: true
                gravity: false
                progression:
                  enabled: true
                  maximum-level: 50
                nameplate:
                  enabled: true
                  format:
                    - "<gold>{pet_name}</gold>"
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("wolf", cfg);

        assertTrue(parsed.errors().isEmpty(), () -> "errors: " + parsed.errors());
        PetDefinition def = parsed.definition();
        assertNotNull(def);
        assertEquals("wolf", def.id());
        assertTrue(def.baby(), "legacy baby flag must be honored");
        assertTrue(def.glowing());
        assertFalse(def.invulnerable());
        assertTrue(def.silent());
        assertFalse(def.gravity());
        assertEquals(50, def.maxLevel());
        assertNull(def.movement(), "legacy entity pets keep null movement (config defaults)");
        assertEquals(RuntimeRepresentationType.ENTITY, def.representationOrEntity().type());
        assertEquals("WOLF", def.representationOrEntity().entityType());
    }

    @Test
    void newSchemaWithItemDisplayAndOrbitParsesFullPipeline() {
        YamlConfiguration cfg = yaml("""
                display-name: "Arcane Crystal"
                representation:
                  type: ITEM_DISPLAY
                  item-material: AMETHYST_SHARD
                  custom-model-data: 12001
                  scale:
                    x: 1.2
                    y: 1.2
                    z: 1.2
                  glowing: true
                movement:
                  type: ORBIT
                  orbit:
                    radius: 1.7
                    height: 1.4
                    angular-speed: 1.2
                    clockwise: true
                  update-interval-ticks: 5
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("arcane_crystal", cfg);

        assertTrue(parsed.errors().isEmpty(), () -> "errors: " + parsed.errors());
        PetDefinition def = parsed.definition();
        assertEquals(RuntimeRepresentationType.ITEM_DISPLAY, def.representation().type());
        assertEquals("AMETHYST_SHARD", def.representation().itemMaterial());
        assertEquals(12001, def.representation().customModelData());
        assertEquals(1.2, def.representation().scale().x());
        assertTrue(def.representation().glowing());

        assertEquals(PetMovementType.ORBIT, def.movement().type());
        assertEquals(1.7, def.movement().orbit().radius());
        assertEquals(1.4, def.movement().orbit().height());
        assertEquals(1.2, def.movement().orbit().angularSpeed());
        assertTrue(def.movement().orbit().clockwise());
        assertEquals(5, def.movement().updateIntervalTicks());
    }

    @Test
    void displayPetWithoutMovementDefaultsToFlyingFollow() {
        YamlConfiguration cfg = yaml("""
                display-name: "Tome"
                representation:
                  type: ITEM_DISPLAY
                  item-material: ENCHANTED_BOOK
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("tome", cfg);

        assertTrue(parsed.errors().isEmpty());
        PetDefinition def = parsed.definition();
        assertEquals(PetMovementType.FLYING_FOLLOW, def.movement().type());
        assertEquals(1.5, def.movement().height());
        assertEquals(1.1, def.movement().sideOffset());
    }

    @Test
    void nestedRepresentationTakesPrecedenceOverFlatKeys() {
        YamlConfiguration cfg = yaml("""
                display-name: "Hybrid"
                entity-type: WOLF
                representation:
                  type: ITEM_DISPLAY
                  item-material: DIAMOND
                movement:
                  type: FLYING_FOLLOW
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("hybrid", cfg);

        assertTrue(parsed.errors().isEmpty());
        PetDefinition def = parsed.definition();
        assertEquals(RuntimeRepresentationType.ITEM_DISPLAY, def.representation().type());
        assertEquals(PetMovementType.FLYING_FOLLOW, def.movement().type());
    }

    @Test
    void invalidEnumValuesAreCollectedAsErrors() {
        YamlConfiguration cfg = yaml("""
                display-name: "Bad"
                representation:
                  type: NOT_A_TYPE
                  item-material: AMETHYST_SHARD
                movement:
                  type: NOT_A_MOVE
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("bad", cfg);

        assertFalse(parsed.errors().isEmpty());
        assertTrue(parsed.errors().stream().anyMatch(e -> e.contains("representation.type")));
        assertTrue(parsed.errors().stream().anyMatch(e -> e.contains("movement.type")));
    }

    @Test
    void missingIdReturnsErrors() {
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse(null, new YamlConfiguration());
        assertNull(parsed.definition());
        assertFalse(parsed.errors().isEmpty());
    }

    @Test
    void counterClockwiseOrbitDirectionIsRespected() {
        YamlConfiguration cfg = yaml("""
                display-name: "CCW"
                representation:
                  type: ITEM_DISPLAY
                  item-material: DIAMOND
                movement:
                  type: ORBIT
                  orbit:
                    direction: COUNTER_CLOCKWISE
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("ccw", cfg);
        assertTrue(parsed.errors().isEmpty());
        assertFalse(parsed.definition().movement().orbit().clockwise());
    }

    @Test
    void defaultNameplateFormatAppliedWhenMissing() {
        YamlConfiguration cfg = yaml("""
                display-name: "Plain"
                """);
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("plain", cfg);
        assertTrue(parsed.errors().isEmpty());
        assertFalse(parsed.definition().nameplateFormat().isEmpty());
    }

    @Test
    void bundledSampleYamlsParseWithoutErrors() throws Exception {
        List<String> samples = List.of(
                "arcane_crystal.yml", "floating_book.yml", "wolf.yml",
                "void_cube.yml", "spirit_flame.yml", "ghost_scribe.yml",
                "familiar_swarm.yml", "shoulder_orb.yml", "sleepy_cat.yml", "wisplight.yml");
        for (String sample : samples) {
            String raw = new String(requireNonNull(
                    getClass().getResourceAsStream("/pets/" + sample)).readAllBytes());
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.loadFromString(raw);
            String id = sample.substring(0, sample.length() - 4);

            PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse(id, cfg);

            assertTrue(parsed.errors().isEmpty(), () -> sample + " errors: " + parsed.errors());
            assertNotNull(parsed.definition(), sample + " must produce a definition");
        }
    }

    @Test
    void blockDisplayAndParticleFieldsParse() throws Exception {
        String raw = new String(requireNonNull(
                getClass().getResourceAsStream("/pets/void_cube.yml")).readAllBytes());
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(raw);
        PetDefinition cube = PetDefinitionYamlParser.parse("void_cube", cfg).definition();

        assertEquals(RuntimeRepresentationType.BLOCK_DISPLAY, cube.representation().type());
        assertEquals("CRYING_OBSIDIAN", cube.representation().itemMaterial());
        assertEquals(com.petsistemi.domain.PetMovementType.HOVER, cube.movement().type());
        assertEquals(2.2, cube.movement().height());

        raw = new String(requireNonNull(
                getClass().getResourceAsStream("/pets/spirit_flame.yml")).readAllBytes());
        cfg.loadFromString(raw);
        PetDefinition flame = PetDefinitionYamlParser.parse("spirit_flame", cfg).definition();

        assertEquals(RuntimeRepresentationType.PARTICLE, flame.representation().type());
        assertEquals("SOUL_FIRE_FLAME", flame.representation().particleType());
        assertEquals(6, flame.representation().particleCount());
        assertEquals(0.4, flame.representation().particleOffset());
    }

    @Test
    void multiEntityAndAnchoredFieldsParse() throws Exception {
        String raw = new String(requireNonNull(
                getClass().getResourceAsStream("/pets/familiar_swarm.yml")).readAllBytes());
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(raw);
        PetDefinition swarm = PetDefinitionYamlParser.parse("familiar_swarm", cfg).definition();

        assertEquals(RuntimeRepresentationType.MULTI_ENTITY, swarm.representation().type());
        assertEquals(3, swarm.representation().childCount());
        assertEquals("POPPY", swarm.representation().childMaterial());
        assertEquals(com.petsistemi.domain.PetMovementType.FORMATION, swarm.movement().type());

        raw = new String(requireNonNull(
                getClass().getResourceAsStream("/pets/shoulder_orb.yml")).readAllBytes());
        cfg.loadFromString(raw);
        PetDefinition orb = PetDefinitionYamlParser.parse("shoulder_orb", cfg).definition();

        assertEquals(com.petsistemi.domain.PetMovementType.ANCHORED, orb.movement().type());
        assertEquals(com.petsistemi.domain.PetAnchorPosition.RIGHT_SHOULDER, orb.movement().anchor().position());
        assertEquals(0.6, orb.movement().anchor().height());
        assertTrue(orb.movement().anchor().rotateWithOwner());
    }

    @Test
    void bundledArcaneCrystalIsOrbitDisplayPet() throws Exception {
        String raw = new String(requireNonNull(
                getClass().getResourceAsStream("/pets/arcane_crystal.yml")).readAllBytes());
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(raw);

        PetDefinition def = PetDefinitionYamlParser.parse("arcane_crystal", cfg).definition();

        assertEquals(RuntimeRepresentationType.ITEM_DISPLAY, def.representation().type());
        assertEquals("AMETHYST_SHARD", def.representation().itemMaterial());
        assertEquals(12001, def.representation().customModelData());
        assertEquals(PetMovementType.ORBIT, def.movement().type());
        assertTrue(def.movement().orbit().clockwise());
    }

    @Test
    void statesSectionParsesIntoPetStatesDefinition() {
        YamlConfiguration cfg = yaml("""
                display-name: "Sleepy Cat"
                entity-type: CAT
                states:
                  MOVING:
                    animation: WALK
                  IDLE:
                    after-ticks: 100
                    animation: SLEEP
                """);

        PetDefinition def = PetDefinitionYamlParser.parse("sleepy_cat", cfg).definition();

        assertNotNull(def.states());
        assertEquals(com.petsistemi.domain.PetIdleAnimation.WALK, def.states().moving().animation());
        assertEquals(100, def.states().idle().afterTicks());
        assertEquals(com.petsistemi.domain.PetIdleAnimation.SLEEP, def.states().idle().animation());
    }

    @Test
    void animationStatesParseNamedClipPriorityAndBlending() {
        YamlConfiguration cfg = yaml("""
                display-name: "Runner"
                entity-type: WOLF
                states:
                  SPRINTING:
                    clip: modelengine:run_fast
                    priority: 40
                    blend-in-ticks: 3
                    blend-out-ticks: 5
                    loop: true
                  ATTACKING:
                    clip: bite
                    priority: 100
                    loop: false
                """);

        PetDefinition def = PetDefinitionYamlParser.parse("runner", cfg).definition();

        assertEquals("modelengine:run_fast", def.states().sprinting().clip().toString());
        assertEquals(40, def.states().sprinting().priority());
        assertEquals(3, def.states().sprinting().blendInTicks());
        assertEquals(5, def.states().sprinting().blendOutTicks());
        assertEquals("petsistemi:bite", def.states().attacking().clip().toString());
        assertFalse(def.states().attacking().loop());
    }

    @Test
    void statesSectionIsOptionalAndDefaultsToNull() {
        YamlConfiguration cfg = yaml("""
                display-name: "Plain"
                entity-type: WOLF
                """);

        PetDefinition def = PetDefinitionYamlParser.parse("plain", cfg).definition();

        assertNull(def.states());
    }

    @Test
    void externalModelRepresentationParsesNamespacedProviderAndModelId() {
        YamlConfiguration cfg = yaml("""
                display-name: "Phoenix"
                representation:
                  type: modelengine:model
                  model-id: phoenix_pet
                  entity-type: ARMOR_STAND
                """);

        PetDefinition def = PetDefinitionYamlParser.parse("phoenix", cfg).definition();

        assertEquals("modelengine:model", def.representation().key().toString());
        assertEquals("phoenix_pet", def.representation().modelId());
    }

    @Test
    void invalidStateAnimationAddsErrorButKeepsParsing() {
        YamlConfiguration cfg = yaml("""
                display-name: "Bad"
                entity-type: WOLF
                states:
                  IDLE:
                    animation: FLYING
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("bad", cfg);

        assertFalse(parsed.errors().isEmpty());
        assertNotNull(parsed.definition());
        assertNotNull(parsed.definition().states().idle());
    }

    @Test
    void transformsSectionParsesWithNamedKeysAndConditions() {
        YamlConfiguration cfg = yaml("""
                display-name: "Wisplight"
                representation:
                  type: ITEM_DISPLAY
                  item-material: GLOWSTONE_DUST
                transforms:
                  night:
                    when:
                      time-of-day: NIGHT
                    apply:
                      representation:
                        item-material: SOUL_LANTERN
                        glowing: true
                        scale:
                          x: 0.9
                          y: 0.9
                          z: 0.9
                  water:
                    when:
                      owner-state: IN_WATER
                      biome: PLAINS
                    apply:
                      representation:
                        item-material: LIGHT_BLUE_STAINED_GLASS
                """);

        PetDefinition def = PetDefinitionYamlParser.parse("wisplight", cfg).definition();

        assertNotNull(def.transforms());
        assertEquals(2, def.transforms().size());
        assertEquals(com.petsistemi.domain.PetTimeOfDay.NIGHT, def.transforms().get(0).condition().timeOfDay());
        assertNull(def.transforms().get(0).condition().ownerState());
        assertEquals("SOUL_LANTERN", def.transforms().get(0).apply().itemMaterial());
        assertTrue(def.transforms().get(0).apply().glowing());
        assertEquals(0.9, def.transforms().get(0).apply().scale().x());
        assertEquals(com.petsistemi.domain.PetOwnerState.IN_WATER, def.transforms().get(1).condition().ownerState());
        assertEquals("PLAINS", def.transforms().get(1).condition().biome());
        assertEquals("LIGHT_BLUE_STAINED_GLASS", def.transforms().get(1).apply().itemMaterial());
    }

    @Test
    void transformsSectionIsOptionalAndDefaultsToNull() {
        YamlConfiguration cfg = yaml("""
                display-name: "Plain"
                entity-type: WOLF
                """);

        assertNull(PetDefinitionYamlParser.parse("plain", cfg).definition().transforms());
    }

    @Test
    void unknownTransformConditionEnumAddsError() {
        YamlConfiguration cfg = yaml("""
                display-name: "Bad"
                entity-type: WOLF
                transforms:
                  night:
                    when:
                      time-of-day: TWILIGHT
                    apply:
                      representation:
                        glowing: true
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("bad", cfg);

        assertFalse(parsed.errors().isEmpty());
    }

    @Test
    void reactionsSectionParsesIntoPerTypeDefinitions() {
        YamlConfiguration cfg = yaml("""
                display-name: "Cat"
                entity-type: CAT
                reactions:
                  OWNER_DAMAGE:
                    sound: ENTITY_CAT_HISS
                    particle: VILLAGER_ANGRY
                    particle-count: 4
                    volume: 0.9
                  LEVEL_UP:
                    enabled: false
                    particle: VILLAGER_HAPPY
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("cat", cfg);

        assertTrue(parsed.errors().isEmpty(), () -> "errors: " + parsed.errors());
        PetDefinition def = parsed.definition();
        assertNotNull(def.reactions());
        assertEquals(2, def.reactions().size());
        assertEquals("ENTITY_CAT_HISS", def.reactions().get(com.petsistemi.domain.PetReactionType.OWNER_DAMAGE).sound());
        assertEquals("VILLAGER_ANGRY", def.reactions().get(com.petsistemi.domain.PetReactionType.OWNER_DAMAGE).particle());
        assertEquals(4, def.reactions().get(com.petsistemi.domain.PetReactionType.OWNER_DAMAGE).particleCount());
        assertEquals(0.9, def.reactions().get(com.petsistemi.domain.PetReactionType.OWNER_DAMAGE).volume());
        assertFalse(def.reactions().get(com.petsistemi.domain.PetReactionType.LEVEL_UP).enabled());
        assertNull(def.reactions().get(com.petsistemi.domain.PetReactionType.LEVEL_UP).sound());
    }

    @Test
    void emotesSectionParsesWithLowerCaseKeysAndCooldown() {
        YamlConfiguration cfg = yaml("""
                display-name: "Cat"
                entity-type: CAT
                emotes:
                  Purr:
                    sound: ENTITY_CAT_PURR
                    particle: HEART
                    particle-count: 6
                    cooldown-seconds: 15
                  hiss:
                    enabled: false
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("cat", cfg);

        assertTrue(parsed.errors().isEmpty(), () -> "errors: " + parsed.errors());
        PetDefinition def = parsed.definition();
        assertNotNull(def.emotes());
        assertEquals(2, def.emotes().size());
        assertTrue(def.emotes().containsKey("purr"), "emote keys must be lowercased");
        assertEquals("ENTITY_CAT_PURR", def.emotes().get("purr").sound());
        assertEquals(6, def.emotes().get("purr").particleCount());
        assertEquals(15, def.emotes().get("purr").cooldownSeconds());
        assertFalse(def.emotes().get("hiss").enabled());
    }

    @Test
    void unknownReactionTypeAddsError() {
        YamlConfiguration cfg = yaml("""
                display-name: "Bad"
                entity-type: CAT
                reactions:
                  EXPLODE:
                    sound: ENTITY_CAT_HISS
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("bad", cfg);

        assertFalse(parsed.errors().isEmpty());
    }

    @Test
    void reactionsAndEmotesSectionsAreOptional() {
        YamlConfiguration cfg = yaml("""
                display-name: "Plain"
                entity-type: WOLF
                """);

        PetDefinition def = PetDefinitionYamlParser.parse("plain", cfg).definition();

        assertNull(def.reactions());
        assertNull(def.emotes());
    }

    @Test
    void newMovementTypesParseWithoutErrors() {
        YamlConfiguration cfg = yaml("""
                display-name: "Test"
                entity-type: WOLF
                movement:
                  type: ECHO
                  follow-distance: 8.0
                  teleport-distance: 30.0
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("echo_test", cfg);

        assertTrue(parsed.errors().isEmpty(), () -> "errors: " + parsed.errors());
        assertEquals(com.petsistemi.domain.PetMovementType.ECHO, parsed.definition().movement().type());
    }

    @Test
    void delayTicksParsesFromYaml() {
        YamlConfiguration cfg = yaml("""
                display-name: "Mirror"
                entity-type: VILLAGER
                movement:
                  type: MIRROR
                  height: 0.0
                  side-offset: 1.2
                  delay-ticks: 10
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("mirror_test", cfg);

        assertTrue(parsed.errors().isEmpty(), () -> "errors: " + parsed.errors());
        assertEquals(10, parsed.definition().movement().delayTicks());
    }

    @Test
    void blockDisplayAcceptsBlockMaterialAsMaterialSource() {
        YamlConfiguration cfg = yaml("""
                display-name: "Shadow"
                representation:
                  type: BLOCK_DISPLAY
                  block-material: BLACK_STAINED_GLASS
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("shadow", cfg);

        assertTrue(parsed.errors().isEmpty(), () -> "errors: " + parsed.errors());
        assertEquals("BLACK_STAINED_GLASS", parsed.definition().representation().itemMaterial());
        assertTrue(PetDefinitionValidator.validate(parsed.definition(), 1).isEmpty(),
                "block-material ile tanımlı BLOCK_DISPLAY doğrulamayı geçmeli");
    }

    @Test
    void itemMaterialTakesPrecedenceOverBlockMaterial() {
        YamlConfiguration cfg = yaml("""
                display-name: "Both"
                representation:
                  type: BLOCK_DISPLAY
                  item-material: CRYING_OBSIDIAN
                  block-material: BLACK_STAINED_GLASS
                """);

        assertEquals("CRYING_OBSIDIAN",
                PetDefinitionYamlParser.parse("both", cfg).definition().representation().itemMaterial());
    }

    @Test
    void guiMaterialAndPermissionAreParsed() {
        YamlConfiguration cfg = yaml("""
                display-name: "Gated"
                entity-type: WOLF
                gui-material: WOLF_SPAWN_EGG
                permission: companionpets.pet.gated
                """);

        PetDefinition def = PetDefinitionYamlParser.parse("gated", cfg).definition();

        assertEquals("WOLF_SPAWN_EGG", def.guiMaterial());
        assertEquals("companionpets.pet.gated", def.permission());
    }

    @Test
    void guiMaterialAndPermissionDefaultToNullWhenAbsentOrBlank() {
        YamlConfiguration cfg = yaml("""
                display-name: "Plain"
                entity-type: WOLF
                permission: "   "
                """);

        PetDefinition def = PetDefinitionYamlParser.parse("plain", cfg).definition();

        assertNull(def.guiMaterial());
        assertNull(def.permission(), "boş permission alanı kısıtlama sayılmamalı");
    }

    @Test
    void shadowTrailAndRoamParse() {
        YamlConfiguration cfg = yaml("""
                display-name: "Shadow"
                representation:
                  type: BLOCK_DISPLAY
                  block-material: BLACK_STAINED_GLASS
                movement:
                  type: SHADOW_TRAIL
                  follow-distance: 5.0
                """);

        PetDefinitionYamlParser.Parsed shadow = PetDefinitionYamlParser.parse("shadow_test", cfg);
        assertTrue(shadow.errors().isEmpty(), () -> "shadow errors: " + shadow.errors());
        assertEquals(com.petsistemi.domain.PetMovementType.SHADOW_TRAIL, shadow.definition().movement().type());
    }

    @Test
    void customNamespacedMovementKeyIsPreservedForRuntimeDispatch() {
        YamlConfiguration cfg = yaml("""
                display-name: "Extension pet"
                representation:
                  type: ENTITY
                movement:
                  type: test:custom_movement
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("extension_pet", cfg);

        assertTrue(parsed.errors().isEmpty(), () -> "parser errors: " + parsed.errors());
        assertEquals(new NamespacedKey("test", "custom_movement"), parsed.definition().movement().key());
    }

    @Test
    void nativeBehaviorsParseNamespacedTriggerConditionsActionsAndParameters() {
        YamlConfiguration cfg = yaml("""
                display-name: "Behavior pet"
                behaviors:
                  - trigger: petsistemi:tick
                    conditions:
                      - type: petsistemi:min_level
                        parameters:
                          level: 5
                    actions:
                      - type: petsistemi:apply_potion_effect
                        parameters:
                          effect: SPEED
                          amplifier: 1
                          duration-ticks: 80
                """);

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("behavior_pet", cfg);

        assertTrue(parsed.errors().isEmpty(), () -> "parser errors: " + parsed.errors());
        assertNotNull(parsed.definition().behaviors());
        assertEquals(new NamespacedKey("petsistemi", "tick"), parsed.definition().behaviors().get(0).trigger());
        assertEquals(5, parsed.definition().behaviors().get(0).conditions().get(0).parameters().get("level"));
        assertEquals("SPEED", parsed.definition().behaviors().get(0).actions().get(0).parameters().get("effect"));
    }

    @Test
    void malformedNativeBehaviorReportsPathSpecificErrors() {
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("broken", yaml("""
                display-name: "Broken"
                behaviors:
                  - trigger: missing_namespace
                    actions: []
                """));

        assertFalse(parsed.errors().isEmpty());
        assertTrue(parsed.errors().stream().anyMatch(error -> error.contains("behaviors[0].trigger")));
    }

    @Test
    void abilityParsesAsCooldownAndTargetedBehavior() {
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("mage", yaml("""
                display-name: "Mage"
                abilities:
                  arcane_burst:
                    cooldown-seconds: 12
                    target: OWNER_TARGET
                    range: 16.0
                    actions:
                      - type: test:record_target
                        parameters:
                          power: 3
                """));

        assertTrue(parsed.errors().isEmpty(), () -> "parser errors: " + parsed.errors());
        var key = new NamespacedKey("petsistemi", "arcane_burst");
        var ability = parsed.definition().abilities().get(key);
        assertNotNull(ability);
        assertEquals(12, ability.cooldownSeconds());
        assertEquals(com.petsistemi.domain.ability.AbilityTargetType.OWNER_TARGET, ability.targetType());
        assertEquals(16.0, ability.range());
        assertEquals(new NamespacedKey("petsistemi", "ability"), ability.behavior().trigger());
    }
}
