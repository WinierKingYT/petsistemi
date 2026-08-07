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

    /**
     * This is a UX pre-check; DefaultPetService#validateNameInput is authoritative and
     * rejects '<'/'>' outright. Emitting MiniMessage tags here made every coloured GUI
     * rename fail that later check — holding the colour permission broke renaming.
     */
    @Test
    @DisplayName("Doğrulayıcı çıktısı asla MiniMessage etiketi üretmez")
    void sanitizedNameNeverContainsAngleBrackets() {
        for (String input : new String[]{"&cKızıl", "&6&lAltın", "&#FF00FFMor", "Pamuk"}) {
            var res = PetNameValidator.validate(null, input);
            assertTrue(res.valid(), () -> "reddedilmemeli: " + input);
            assertFalse(res.sanitizedName().contains("<"),
                    () -> input + " -> " + res.sanitizedName() + " ('<' servis doğrulayıcısında reddedilir)");
            assertFalse(res.sanitizedName().contains(">"),
                    () -> input + " -> " + res.sanitizedName());
        }
    }

    @Test
    @DisplayName("Legacy renk kodları olduğu gibi geçer, dönüştürülmez")
    void legacyCodesArePassedThroughUntouched() {
        var res = PetNameValidator.validate(null, "&cKızıl");

        assertTrue(res.valid());
        assertEquals("&cKızıl", res.sanitizedName(),
                "dönüşüm burada değil, isim etiketi katmanında yapılır");
    }

    @Test
    @DisplayName("Uzunluk etiketler soyulduktan sonra ölçülür")
    void lengthIsMeasuredOnStrippedText() {
        // Tags themselves must not eat the player's character budget.
        var res = PetNameValidator.validate(null, "<red>Ad</red>");

        assertTrue(res.valid(), "etiketler uzunluk sınırını tüketmemeli");
    }
}
