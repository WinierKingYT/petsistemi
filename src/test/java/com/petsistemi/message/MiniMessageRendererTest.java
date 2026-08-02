package com.petsistemi.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiniMessageRendererTest {

    @Test
    void testRenderBasicPlaceholder() {
        String template = "<green>Hello <player>!</green>";
        PlaceholderMap placeholders = PlaceholderMap.of("player", "Alex");

        Component component = MiniMessageRenderer.render(template, placeholders);
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        assertEquals("Hello Alex!", plain);
    }

    @Test
    void testEscapesPlaceholderInjectionSafely() {
        String template = "<gold>Pet Name: <name></gold>";
        PlaceholderMap maliciousInput = PlaceholderMap.of("name", "<click:run_command:/op me>Hacked</click>");

        Component component = MiniMessageRenderer.render(template, maliciousInput);
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        assertNotNull(plain);
        assertTrue(plain.contains("Hacked"));
        assertFalse(plain.equals("Pet Name: Hacked"), "Malicious tag must not be evaluated as a click event");
    }
}
