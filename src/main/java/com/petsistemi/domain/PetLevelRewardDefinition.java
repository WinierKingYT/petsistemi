package com.petsistemi.domain;

import java.util.List;

/**
 * Commands and messages rewarded to the owner when the pet reaches a specific level.
 */
public record PetLevelRewardDefinition(
        int level,
        List<String> commands,
        String message
) {
}
