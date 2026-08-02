package com.petsistemi.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;

public final class MiniMessageRenderer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static Component render(String template, PlaceholderMap placeholders) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }

        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.getMap().entrySet()) {
                String safeValue = escapeMiniMessageTags(entry.getValue());
                result = result.replace("<" + entry.getKey() + ">", safeValue);
            }
        }

        return MINI_MESSAGE.deserialize(result);
    }

    public static String escapeMiniMessageTags(String input) {
        if (input == null) return "";
        return input.replace("<", "\\<").replace(">", "\\>");
    }
}
