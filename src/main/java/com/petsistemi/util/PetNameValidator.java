package com.petsistemi.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

public final class PetNameValidator {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 32;

    private PetNameValidator() {}

    public record ValidationResult(boolean valid, String sanitizedName, String errorMessage) {
        public static ValidationResult success(String name) {
            return new ValidationResult(true, name, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, null, message);
        }
    }

    public static ValidationResult validate(Player player, String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return ValidationResult.error("Pet ismi boş olamaz.");
        }

        String trimmed = rawInput.trim();

        // Strip tags/formatting to check clean text length
        String strippedText;
        try {
            strippedText = MiniMessage.miniMessage().stripTags(trimmed);
        } catch (Exception e) {
            strippedText = trimmed;
        }

        if (strippedText.length() < MIN_LENGTH) {
            return ValidationResult.error("Pet ismi en az " + MIN_LENGTH + " karakter olmalıdır.");
        }

        if (strippedText.length() > MAX_LENGTH) {
            return ValidationResult.error("Pet ismi en fazla " + MAX_LENGTH + " karakter olabilir.");
        }

        // Color permission check: if player lacks color permission, auto-strip color tags for seamless UX
        boolean allowColor = player == null || player.hasPermission("petsistemi.rename.color") || player.hasPermission("companionpets.color");
        if (!allowColor && (trimmed.contains("&") || trimmed.contains("<") || trimmed.contains("§"))) {
            trimmed = strippedText;
        }

        if (allowColor) {
            trimmed = LegacyColorConverter.convertLegacyToMiniMessage(trimmed);
        }

        return ValidationResult.success(trimmed);
    }
}
