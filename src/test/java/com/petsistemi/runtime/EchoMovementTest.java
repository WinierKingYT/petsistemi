package com.petsistemi.runtime;

import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetMovementType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EchoMovementTest {

    @Test
    void noWorldIndependentTestYet() {
        // EchoMovement tick logic relies on Bukkit entities; pure math verification
        // is done via the TrailMovement.pushOwnerPosition test pattern.
        // Smoke test: movement can be constructed (just to verify it compiles).
        assertDoesNotThrow(() -> {
            PetMovementDefinition def = PetMovementDefinition.echo(8.0, 30.0);
            assertNotNull(def);
            assertEquals(com.petsistemi.domain.PetMovementType.ECHO, def.type());
        });
    }
}