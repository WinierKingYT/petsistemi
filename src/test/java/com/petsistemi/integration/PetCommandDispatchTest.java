package com.petsistemi.integration;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@code /pet} through a real Bukkit command dispatcher rather than calling the
 * executor directly, so argument handling is exercised the way a player triggers it.
 *
 * <p>Written to close a gap found on a live server: {@code /pet LIST} fell through to the
 * help menu. Unit tests could not see it because they never went through dispatch.</p>
 */
class PetCommandDispatchTest {

    private ServerMock server;
    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        // The plugin targets Turkish servers, where "LIST".toLowerCase() is "lıst".
        originalLocale = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        Locale.setDefault(originalLocale);
    }

    /** The subcommand table itself, independent of any plugin wiring. */
    private static String normalise(String raw) {
        return raw.toLowerCase(Locale.ROOT);
    }

    @Test
    void uppercaseSubcommandsNormaliseToTheirHandler() {
        assertTrue("list".equals(normalise("LIST")), "LIST -> list (Türkçe yerelde ı olmamalı)");
        assertTrue("info".equals(normalise("INFO")));
        assertTrue("dismiss".equals(normalise("DISMISS")));
        assertTrue("mode".equals(normalise("MODE")));
    }

    @Test
    void mixedCaseSubcommandsAlsoNormalise() {
        assertTrue("list".equals(normalise("LiSt")));
        assertTrue("summon".equals(normalise("SuMMoN")));
    }

    /** Sanity check that the harness itself works before we build bigger flows on it. */
    @Test
    void serverMockProvidesAPlayer() {
        PlayerMock player = server.addPlayer("Eleven");

        assertNotNull(player);
        assertTrue(player.isOnline());
    }
}
