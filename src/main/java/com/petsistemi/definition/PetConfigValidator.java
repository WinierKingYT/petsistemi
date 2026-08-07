package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Diagnostic utility that inspects parsed pet definitions and logs detailed
 * warnings in Turkish if questionable values or missing resources exist.
 */
public final class PetConfigValidator {

    private PetConfigValidator() {}

    public static List<String> validateAndLog(PetDefinition def, Logger logger) {
        List<String> warnings = new ArrayList<>();
        if (def == null) return warnings;

        // Material check
        if (def.guiMaterial() != null) {
            String matName = def.guiMaterial().trim().toUpperCase();
            Material mat = Material.matchMaterial(matName);
            if (mat == null) {
                warnings.add("Geçersiz GUI materyali: '" + def.guiMaterial() + "'. Varsayılan materyal kullanılacak.");
            }
        }

        // Particle check
        if (def.representationOrEntity() != null && def.representationOrEntity().particleType() != null) {
            try {
                Particle.valueOf(def.representationOrEntity().particleType().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                warnings.add("Geçersiz parçacık türü: '" + def.representationOrEntity().particleType() + "'.");
            }
        }

        if (logger != null && !warnings.isEmpty()) {
            logger.warning("['" + def.id() + "' Pet Tanımı Uyarıları]:");
            for (String w : warnings) {
                logger.warning("  - " + w);
            }
        }

        return warnings;
    }
}
