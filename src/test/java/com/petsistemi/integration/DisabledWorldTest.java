package com.petsistemi.integration;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.petsistemi.listener.WorldChangeListener;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pets are dismissed on entry to minigame worlds so they cannot follow a player into an
 * arena and interfere with the match. The matching is keyword-based and configurable, and
 * a wrong match is costly in both directions: a pet loose in a bedwars game, or a pet
 * silently despawning in a world the admin never meant to disable.
 */
class DisabledWorldTest {

    private ServerMock server;
    private MockPlugin plugin;
    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        // Keyword matching lowercases; under a Turkish locale "MINIGAMES" must still match.
        originalLocale = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("PetSistemi");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        Locale.setDefault(originalLocale);
    }

    /** Builds a listener whose disabled-worlds list comes from config, or defaults when empty. */
    private WorldChangeListener listenerWith(List<String> configuredWorlds) {
        if (configuredWorlds != null) {
            plugin.getConfig().set("disabled-worlds", configuredWorlds);
        }
        return new WorldChangeListener(plugin, new ActivePetRegistry());
    }

    private static boolean disabled(WorldChangeListener listener, String worldName) {
        try {
            Method method = WorldChangeListener.class.getDeclaredMethod("isWorldDisabled", String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(listener, worldName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void builtInKeywordsMatchArenaWorlds() {
        WorldChangeListener listener = listenerWith(null);

        assertTrue(disabled(listener, "bedwars"));
        assertTrue(disabled(listener, "bedwars_arena_3"), "alt dize eşleşmeli");
        assertTrue(disabled(listener, "minigames"));
    }

    /** Turkish locale turns "I" into "ı"; a world named MINIGAMES must still be caught. */
    @Test
    void keywordMatchingIsLocaleIndependent() {
        WorldChangeListener listener = listenerWith(null);

        assertTrue(disabled(listener, "MINIGAMES"));
        assertTrue(disabled(listener, "BedWars_Arena"));
    }

    @Test
    void ordinaryWorldsAreNeverDisabled() {
        WorldChangeListener listener = listenerWith(null);

        assertFalse(disabled(listener, "world"));
        assertFalse(disabled(listener, "world_nether"));
        assertFalse(disabled(listener, "survival"));
        assertFalse(disabled(listener, null));
    }

    @Test
    void configuredWorldsReplaceTheBuiltInDefaults() {
        WorldChangeListener listener = listenerWith(List.of("skyblock", "duel"));

        assertTrue(disabled(listener, "skyblock_1"));
        assertTrue(disabled(listener, "duel"));
        assertFalse(disabled(listener, "bedwars"), "config verildiğinde varsayılanlar geçerli olmamalı");
    }

    @Test
    void configuredKeywordsAreAlsoCaseInsensitive() {
        WorldChangeListener listener = listenerWith(List.of("SkyBlock"));

        assertTrue(disabled(listener, "skyblock_arena"));
        assertTrue(disabled(listener, "SKYBLOCK"));
    }

    /** Changing world with no active pet must be a quiet no-op, not an error. */
    @Test
    void aWorldChangeWithoutAnActivePetIsHarmless() {
        WorldChangeListener listener = listenerWith(null);
        WorldMock from = server.addSimpleWorld("world");
        WorldMock arena = server.addSimpleWorld("bedwars_arena");
        PlayerMock player = server.addPlayer("Eleven");
        player.teleport(arena.getSpawnLocation());

        listener.onWorldChange(new PlayerChangedWorldEvent(player, from));
    }
}
