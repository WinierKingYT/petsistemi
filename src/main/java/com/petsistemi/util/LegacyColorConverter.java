package com.petsistemi.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to convert legacy Bukkit color codes (&a, &c, &l, etc.) and hex codes (&#RRGGBB) to MiniMessage tags.
 */
public final class LegacyColorConverter {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern COLOR_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private LegacyColorConverter() {}

    public static String convertLegacyToMiniMessage(String input) {
        if (input == null || input.isEmpty() || !input.contains("&")) {
            return input;
        }

        // 1. Convert hex format &#RRGGBB to <#RRGGBB>
        Matcher hexMatcher = HEX_PATTERN.matcher(input);
        StringBuilder hexSb = new StringBuilder();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            hexMatcher.appendReplacement(hexSb, "<#" + hex + ">");
        }
        hexMatcher.appendTail(hexSb);
        String hexConverted = hexSb.toString();

        // 2. Convert standard legacy & codes
        Matcher matcher = COLOR_PATTERN.matcher(hexConverted);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String tag = switch (code) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> "&" + code;
            };
            matcher.appendReplacement(sb, tag);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
