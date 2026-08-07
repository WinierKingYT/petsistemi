package com.petsistemi.domain;

import java.util.List;

/**
 * Conditional visibility definition for pets that appear only under certain triggers.
 */
public record PetPresenceDefinition(
        String mode, // ALWAYS, CONDITIONAL
        List<String> triggers, // OWNER_SNEAK, OWNER_JUMP, OWNER_COMBAT, OWNER_LOW_HEALTH
        int visibleDurationSeconds
) {
    public static final PetPresenceDefinition ALWAYS = new PetPresenceDefinition("ALWAYS", List.of(), 0);

    public boolean isConditional() {
        return "CONDITIONAL".equalsIgnoreCase(mode);
    }
}
