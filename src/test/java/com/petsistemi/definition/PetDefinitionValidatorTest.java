package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PetDefinitionValidatorTest {

    @Test
    void testValidDefinitionPassesValidation() {
        PetDefinition def = new PetDefinition(
                "wolf",
                "Kurt",
                Collections.emptyList(),
                "WOLF",
                false,
                false,
                true,
                false,
                true,
                true,
                100,
                true,
                Collections.emptyList()
        );

        List<String> errors = PetDefinitionValidator.validate(def, 1);
        assertTrue(errors.isEmpty(), "Valid definition must pass validation with 0 errors");
    }

    @Test
    void testInvalidSchemaVersionFailsValidation() {
        PetDefinition def = new PetDefinition("wolf", "Kurt", Collections.emptyList(), "WOLF", false, false, true, false, true, true, 100, true, Collections.emptyList());
        List<String> errors = PetDefinitionValidator.validate(def, 99);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("schema-version")));
    }

    @Test
    void testInvalidIdFormatFailsValidation() {
        PetDefinition def = new PetDefinition("Wolf System!!", "Kurt", Collections.emptyList(), "WOLF", false, false, true, false, true, true, 100, true, Collections.emptyList());
        List<String> errors = PetDefinitionValidator.validate(def, 1);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("format")));
    }
}
