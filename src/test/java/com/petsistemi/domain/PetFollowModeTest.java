package com.petsistemi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PetFollowModeTest {

    @Test
    void parsesValidModesCaseInsensitively() {
        assertEquals(PetFollowMode.FOLLOW, PetFollowMode.fromString("follow"));
        assertEquals(PetFollowMode.STAY, PetFollowMode.fromString("STAY"));
        assertEquals(PetFollowMode.WANDER, PetFollowMode.fromString("  Wander  "));
    }

    @Test
    void rejectsUnknownOrNullModes() {
        assertNull(PetFollowMode.fromString("fly"));
        assertNull(PetFollowMode.fromString(""));
        assertNull(PetFollowMode.fromString(null));
    }
}
