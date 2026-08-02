package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PetDefinitionValidator {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]+$");

    public static List<String> validate(PetDefinition def, int schemaVersion) {
        List<String> errors = new ArrayList<>();

        if (def == null) {
            errors.add("PetDefinition null olamaz.");
            return errors;
        }

        if (schemaVersion != 1) {
            errors.add("Bilinmeyen schema-version: " + schemaVersion + " (Beklenen: 1)");
        }

        if (def.id() == null || !ID_PATTERN.matcher(def.id()).matches()) {
            errors.add("Geçersiz pet ID formatı: '" + def.id() + "'. Sadece küçük harf, rakam, alt çizgi ve tire içerebilir.");
        }

        if (def.displayName() == null || def.displayName().trim().isEmpty()) {
            errors.add("Görünen isim (displayName) boş olamaz.");
        }

        if (def.entityType() == null || !def.entityType().isSpawnable() || !LivingEntity.class.isAssignableFrom(def.entityType().getEntityClass())) {
            errors.add("Geçersiz EntityType: " + def.entityType() + ". Canlı (LivingEntity) ve çağırılabilir varlık olmalıdır.");
        }

        if (def.maxLevel() <= 0) {
            errors.add("Maksimum seviye (maxLevel) 0'dan büyük olmalıdır.");
        }

        return errors;
    }
}
