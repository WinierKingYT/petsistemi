package com.petsistemi.integration;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.result.PetDisableResult;
import com.petsistemi.api.result.PetDismissResult;
import com.petsistemi.api.result.PetGiveResult;
import com.petsistemi.api.result.PetRemoveResult;
import com.petsistemi.api.result.PetRenameResult;
import com.petsistemi.api.result.PetSummonResult;
import com.petsistemi.command.PetCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Runs {@code /pet list} through the real command entry point under a Turkish locale.
 *
 * <p>Reported from a live server: {@code /pet LIST} showed the help menu. The subcommand is
 * normalised with {@code Locale.ROOT}, so this pins the behaviour end to end rather than
 * trusting that reading.</p>
 */
class PetListSubcommandTest {

    private ServerMock server;
    private Locale originalLocale;
    private PetCommand petCommand;

    /** Minimal service: the player owns nothing, so handleList takes its "no pets" branch. */
    private static final class EmptyPetService implements PetService {
        @Override public Optional<PetSnapshot> findPet(UUID petId) { return Optional.empty(); }
        @Override public Collection<PetSnapshot> getOwnedPets(UUID ownerId) { return List.of(); }
        @Override public Optional<PetSnapshot> getSelectedPet(UUID ownerId) { return Optional.empty(); }
        @Override public Optional<PetSnapshot> getActivePet(UUID ownerId) { return Optional.empty(); }
        @Override public Optional<PetSnapshot> getSpawnedPet(UUID ownerId) { return Optional.empty(); }
        @Override public PetGiveResult givePet(UUID ownerId, String definitionId) { return null; }
        @Override public PetSummonResult summon(org.bukkit.entity.Player owner, UUID petId) { return null; }
        @Override public PetDismissResult dismiss(org.bukkit.entity.Player owner) { return null; }
        @Override public PetRenameResult rename(org.bukkit.entity.Player owner, UUID petId, String newName) { return null; }
        @Override public PetRenameResult rename(UUID ownerId, UUID petId, String newName) { return null; }
        @Override public PetDisableResult disablePet(UUID petId) { return null; }
        @Override public PetDisableResult enablePet(UUID petId) { return null; }
        @Override public PetRemoveResult removePet(UUID petId) { return null; }
    }

    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        server = MockBukkit.mock();
        petCommand = new PetCommand(MockBukkit.createMockPlugin("PetSistemi"), null,
                new EmptyPetService(), null, null, null, null, null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        Locale.setDefault(originalLocale);
    }

    /** Drains every message the player received, as plain text. */
    private String messagesOf(PlayerMock player) {
        StringBuilder all = new StringBuilder();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            all.append(PlainTextComponentSerializer.plainText().serialize(next)).append('\n');
        }
        return all.toString();
    }

    private String runPet(String... args) {
        PlayerMock player = server.addPlayer("Eleven");
        player.addAttachment(MockBukkit.createMockPlugin("perm"), "companionpets.use", true);

        petCommand.onCommand(player, mock(Command.class), "pet", args);
        server.getScheduler().performTicks(3);

        return messagesOf(player);
    }

    private static final String HELP_MARKER = "Minecraft Pet Sistemi";

    @Test
    void lowercaseListReachesTheListHandler() {
        String output = runPet("list");

        assertFalse(output.contains(HELP_MARKER), () -> "yardım menüsü gelmemeli:\n" + output);
    }

    /** The reported case: uppercase must not fall through to help under a Turkish locale. */
    @Test
    void uppercaseListReachesTheListHandler() {
        String output = runPet("LIST");

        assertFalse(output.contains(HELP_MARKER), () -> "yardım menüsü gelmemeli:\n" + output);
    }

    @Test
    void mixedCaseListReachesTheListHandler() {
        assertFalse(runPet("LiSt").contains(HELP_MARKER));
    }

    /** A genuinely unknown subcommand still gets help — otherwise the test above proves nothing. */
    @Test
    void anUnknownSubcommandStillShowsHelp() {
        String output = runPet("kayipkomut");

        assertTrue(output.contains(HELP_MARKER), () -> "bilinmeyen komut yardım göstermeli:\n" + output);
    }
}
