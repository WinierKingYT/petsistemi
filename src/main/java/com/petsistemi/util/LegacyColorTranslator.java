package com.petsistemi.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Translates legacy ampersand ({@code &}) / section ({@code §}) color codes into
 * Adventure components and MiniMessage strings so user-entered pet names with
 * colors render correctly in nameplates and GUIs.
 */
public final class LegacyColorTranslator {

    private LegacyColorTranslator() {}

    public static boolean hasCodes(String text) {
        return text != null && (text.contains("&") || text.contains("§"));
    }

    /** Converts {@code &color}/{@code §color} codes to an Adventure component. */
    public static Component toComponent(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (!hasCodes(text)) return Component.text(text);
        String normalized = text.replace('§', '&');
        try {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(normalized);
        } catch (Exception e) {
            return Component.text(text);
        }
    }

    /**
     * Converts legacy codes to a MiniMessage-safe string (special characters are
     * escaped, colors become {@code <color:...>} tags). Safe to embed into
     * MiniMessage templates such as nameplate formats.
     */
    public static String toMiniMessageString(String text) {
        if (text == null || text.isEmpty()) return "";
        return MiniMessage.miniMessage().serialize(toComponent(text));
    }
}
