package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Nameplate rendering is shared by every representation type, so its rules are load-bearing. */
class PetNameplateRendererTest {

    private static PetInstance pet(String customName, int level) {
        long now = System.currentTimeMillis();
        return new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "wolf", customName,
                level, 0, PetAvailabilityState.AVAILABLE, now, now);
    }

    private static PetDefinition definition(boolean nameplateEnabled, List<String> format) {
        return new PetDefinition("wolf", "Kurt Dostu", List.of(), "WOLF",
                false, false, true, false, true, true, 100, nameplateEnabled, format);
    }

    /** Renders the nameplate and returns its plain text. */
    private static String render(PetInstance instance, PetDefinition def) {
        Entity entity = mock(Entity.class);
        PetNameplateRenderer.updateName(entity, instance, def);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(entity).customName(captor.capture());
        return PlainTextComponentSerializer.plainText().serialize(captor.getValue());
    }

    @Test
    void placeholdersAreSubstituted() {
        String text = render(pet("Karabaş", 7), definition(true, List.of("{pet_name} Lv.{level}")));

        assertEquals("Karabaş Lv.7", text);
    }

    @Test
    void customNameWinsOverDefinitionDisplayName() {
        assertEquals("Karabaş", render(pet("Karabaş", 1), definition(true, List.of("{pet_name}"))));
        assertEquals("Kurt Dostu", render(pet(null, 1), definition(true, List.of("{pet_name}"))));
    }

    @Test
    void multipleFormatLinesAreJoinedWithASeparator() {
        String text = render(pet("Karabaş", 3),
                definition(true, List.of("{pet_name}", "Seviye {level}")));

        assertEquals("Karabaş - Seviye 3", text);
    }

    @Test
    void disabledNameplateHidesInsteadOfRendering() {
        Entity entity = mock(Entity.class);

        PetNameplateRenderer.updateName(entity, pet("Karabaş", 1), definition(false, List.of("{pet_name}")));

        verify(entity).setCustomNameVisible(false);
        verify(entity, never()).customName(any());
    }

    @Test
    void enabledNameplateIsMadeVisible() {
        Entity entity = mock(Entity.class);

        PetNameplateRenderer.updateName(entity, pet("Karabaş", 1), definition(true, List.of("{pet_name}")));

        verify(entity).setCustomNameVisible(true);
    }

    /** Legacy §/& colour codes in a player-chosen name must not leak into the output as literals. */
    @Test
    void legacyColourCodesAreTranslatedNotPrinted() {
        String text = render(pet("&cKızıl", 1), definition(true, List.of("{pet_name}")));

        assertEquals("Kızıl", text);
    }

    /**
     * Every bundled definition ships MiniMessage in `display-name` (e.g. wolf.yml's
     * "&lt;gold&gt;Kurt Dostu&lt;/gold&gt;"). Escaping it printed the tags on the nameplate of
     * every freshly given pet.
     */
    @Test
    void adminDisplayNameKeepsItsMiniMessageMarkup() {
        String text = render(pet(null, 1),
                definition(true, List.of("<gold>{pet_name}</gold> <gray>Lv.{level}</gray>")));

        assertEquals("Kurt Dostu Lv.1", text);
    }

    @Test
    void adminDisplayNameStillHonoursLegacyCodes() {
        PetDefinition legacyNamed = new PetDefinition("wolf", "&6Kurt Dostu", List.of(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"));

        assertEquals("Kurt Dostu", render(pet(null, 1), legacyNamed));
    }

    /**
     * A player-chosen name is untrusted input: its tags must never become live markup,
     * otherwise a pet name could inject colours or click/hover events into the nameplate.
     */
    @Test
    void playerChosenNameCannotInjectMarkup() {
        String text = render(pet("<red>sahte</red>", 1), definition(true, List.of("{pet_name}")));

        assertEquals("<red>sahte</red>", text, "oyuncu adındaki etiketler düz metin kalmalı");
    }

    @Test
    void playerChosenNameCannotInjectClickEvents() {
        String text = render(pet("<click:run_command:'/op me'>tıkla</click>", 1),
                definition(true, List.of("{pet_name}")));

        assertTrue(text.contains("<click"), () -> "tıklama etiketi etkisiz kalmalı: " + text);
    }

    @Test
    void miniMessageMarkupInTheFormatIsRenderedAsStyle() {
        String text = render(pet("Karabaş", 2),
                definition(true, List.of("<gold>{pet_name}</gold> <gray>Lv.{level}</gray>")));

        assertEquals("Karabaş Lv.2", text, "biçim etiketleri düz metne sızmamalı");
    }

    @Test
    void nullEntityIsIgnored() {
        PetNameplateRenderer.updateName(null, pet("Karabaş", 1), definition(true, List.of("{pet_name}")));
    }

    @Test
    void emptyFormatListStillProducesAName() {
        Entity entity = mock(Entity.class);

        PetNameplateRenderer.updateName(entity, pet("Karabaş", 1), definition(true, List.of()));

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(entity).customName(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(captor.getValue()).isEmpty());
    }
}
