package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** For a TEXT_DISPLAY pet the text <em>is</em> the body, so its content rules matter. */
class TextDisplayPetRepresentationTest {

    private static PetInstance instance(String customName) {
        long now = System.currentTimeMillis();
        return new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "scribe", customName,
                1, 0, PetAvailabilityState.AVAILABLE, now, now);
    }

    private static PetDefinition definition(String displayName) {
        return new PetDefinition("scribe", displayName, List.of(), "WOLF",
                false, false, true, false, true, true, 100, false, List.of("{pet_name}"));
    }

    private static String plain(String customName, String displayName) {
        return PlainTextComponentSerializer.plainText()
                .serialize(TextDisplayPetRepresentation.textFor(instance(customName), definition(displayName)));
    }

    @Test
    void customNameIsShownWhenSet() {
        assertEquals("Yazıcı", plain("Yazıcı", "Ghost Scribe"));
    }

    @Test
    void definitionDisplayNameIsTheFallback() {
        assertEquals("Ghost Scribe", plain(null, "Ghost Scribe"));
    }

    @Test
    void legacyColourCodesAreTranslatedNotPrinted() {
        assertEquals("Yazıcı", plain("&bYazıcı", "Ghost Scribe"));
    }

    /**
     * `display-name` is admin-authored and documented as MiniMessage-capable, so its tags
     * must style the text rather than be printed as literals.
     */
    @Test
    void miniMessageMarkupInTheDefinitionNameBecomesStyleNotText() {
        assertEquals("Ghost Scribe", plain(null, "<gold>Ghost Scribe</gold>"));
    }

    /** A player-chosen name is untrusted: its tags must stay inert text, never become markup. */
    @Test
    void miniMessageMarkupInAPlayerChosenNameIsNeutralised() {
        assertEquals("<red>sahte</red>", plain("<red>sahte</red>", "Ghost Scribe"));
    }

    /** No nameplate is drawn above a TEXT_DISPLAY, so the text must carry the name itself. */
    @Test
    void renamingChangesTheRenderedBody() {
        assertEquals("Eski", plain("Eski", "Ghost Scribe"));
        assertEquals("Yeni", plain("Yeni", "Ghost Scribe"));
    }
}
