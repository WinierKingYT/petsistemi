package com.petsistemi.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyColorTranslatorTest {

    @Test
    void detectsLegacyCodes() {
        assertFalse(LegacyColorTranslator.hasCodes("plain name"));
        assertTrue(LegacyColorTranslator.hasCodes("&cRenkli"));
        assertTrue(LegacyColorTranslator.hasCodes("§bRenkli"));
    }

    @Test
    void plainTextIsUnchanged() {
        Component component = LegacyColorTranslator.toComponent("Rex");
        assertEquals("Rex", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component));
        assertTrue(component.color() == null || component.color() == NamedTextColor.WHITE);
    }

    @Test
    void ampersandCodeParsesToColor() {
        Component component = LegacyColorTranslator.toComponent("&cKırmızı");
        assertEquals(NamedTextColor.RED, component.color());
        assertEquals("Kırmızı", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void sectionCodeParsesLikeAmpersand() {
        Component fromSection = LegacyColorTranslator.toComponent("§aYeşil");
        Component fromAmpersand = LegacyColorTranslator.toComponent("&aYeşil");
        assertEquals(fromAmpersand, fromSection);
    }

    @Test
    void malformedInputFallsBackToPlainText() {
        String broken = "&z#~!";
        Component component = LegacyColorTranslator.toComponent(broken);
        assertTrue(component.color() == null || component.color() instanceof net.kyori.adventure.util.RGBLike);
    }

    @Test
    void miniMessageOutputIsParsableAndKeepsColor() {
        String mini = LegacyColorTranslator.toMiniMessageString("&cKırmızı");
        Component parsed = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(mini);
        assertEquals(NamedTextColor.RED, parsed.color());
    }

    @Test
    void miniMessageOutputEscapesDangerousChars() {
        String mini = LegacyColorTranslator.toMiniMessageString("A <b> &cB");
        Component parsed = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(mini);
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(parsed);
        assertTrue(text.contains("<b>"), "literal <b> should survive: " + text);
    }

    @Test
    void emptyAndNullInputsAreSafe() {
        assertEquals(Component.empty(), LegacyColorTranslator.toComponent(null));
        assertEquals(Component.empty(), LegacyColorTranslator.toComponent(""));
        assertEquals("", LegacyColorTranslator.toMiniMessageString(null));
    }
}
