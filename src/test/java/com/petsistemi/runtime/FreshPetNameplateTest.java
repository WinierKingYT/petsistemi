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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Regression for a bug found on a live server: every freshly given pet showed a literal
 * {@code <gold>Kurt Dostu</gold>} on its nameplate.
 *
 * <p>The renderer was right — player-chosen names must be escaped so they cannot inject
 * markup. The data was wrong: {@code givePet} seeded {@code customName} with the admin's
 * {@code display-name}, pushing trusted markup through the untrusted path. A pet nobody has
 * renamed must have a {@code null} custom name.</p>
 */
class FreshPetNameplateTest {

    private static final String MARKUP_DISPLAY_NAME = "<gold>Kurt Dostu</gold>";

    private static PetDefinition wolf() {
        return PetDefinition.builder("wolf", MARKUP_DISPLAY_NAME)
                .nameplateFormat(List.of("<gold>{pet_name}</gold> <gray>Lv.{level}</gray>"))
                .build();
    }

    private static PetInstance pet(String customName) {
        long now = System.currentTimeMillis();
        return new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "wolf", customName,
                1, 0, PetAvailabilityState.AVAILABLE, now, now);
    }

    private static String renderedPlainText(PetInstance instance) {
        Entity entity = mock(Entity.class);
        PetNameplateRenderer.updateName(entity, instance, wolf());

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(entity).customName(captor.capture());
        return PlainTextComponentSerializer.plainText().serialize(captor.getValue());
    }

    /** A never-renamed pet: markup in display-name must style the text, not print. */
    @Test
    void aFreshPetRendersTheDisplayNameAsStyleNotText() {
        assertEquals("Kurt Dostu Lv.1", renderedPlainText(pet(null)));
    }

    /** The escaping stays in place for genuine player input. */
    @Test
    void aPlayerChosenNameIsStillEscaped() {
        assertEquals("<red>sahte</red> Lv.1", renderedPlainText(pet("<red>sahte</red>")));
    }

    @Test
    void anOrdinaryPlayerNameRendersNormally() {
        assertEquals("Karabaş Lv.1", renderedPlainText(pet("Karabaş")));
    }

    /**
     * The invariant that caused the bug: seeding customName from the definition makes the
     * two indistinguishable, so the renderer cannot tell trusted markup from untrusted.
     */
    @Test
    void seedingCustomNameFromTheDisplayNameReproducesTheBug() {
        String rendered = renderedPlainText(pet(MARKUP_DISPLAY_NAME));

        assertEquals("<gold>Kurt Dostu</gold> Lv.1", rendered,
                "bu, customName'e display-name kopyalanırsa oluşan bozuk çıktıdır");
    }
}
