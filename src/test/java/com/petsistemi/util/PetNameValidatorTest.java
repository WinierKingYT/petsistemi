package com.petsistemi.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PetNameValidatorTest {

    @Test
    @DisplayName("Boş veya eksik isim reddedilir")
    void testEmptyName() {
        var res1 = PetNameValidator.validate(null, "");
        assertFalse(res1.valid());

        var res2 = PetNameValidator.validate(null, "   ");
        assertFalse(res2.valid());
    }

    @Test
    @DisplayName("Çok kısa veya çok uzun isim reddedilir")
    void testLengthLimits() {
        var shortRes = PetNameValidator.validate(null, "a");
        assertFalse(shortRes.valid());

        var longRes = PetNameValidator.validate(null, "a".repeat(35));
        assertFalse(longRes.valid());
    }

    @Test
    @DisplayName("Geçerli düz isim onaylanır")
    void testValidName() {
        var res = PetNameValidator.validate(null, "Pamuk");
        assertTrue(res.valid());
        assertEquals("Pamuk", res.sanitizedName());
    }
}
