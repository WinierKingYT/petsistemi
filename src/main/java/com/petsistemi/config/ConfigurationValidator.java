package com.petsistemi.config;

import java.util.ArrayList;
import java.util.List;

public final class ConfigurationValidator {

    public static List<String> validate(PluginConfiguration config) {
        List<String> errors = new ArrayList<>();

        if (config == null) {
            errors.add("PluginConfiguration nesnesi null olamaz.");
            return errors;
        }

        if (config.limits() == null || config.limits().maximumOwnedPets() <= 0) {
            errors.add("limits.maximum-owned-pets 0'dan büyük olmalıdır.");
        }

        if (config.naming() == null) {
            errors.add("naming yapılandırması eksik.");
        } else {
            if (config.naming().minimumLength() <= 0) {
                errors.add("naming.minimum-length 0'dan büyük olmalıdır.");
            }
            if (config.naming().minimumLength() > config.naming().maximumLength()) {
                errors.add("naming.minimum-length, naming.maximum-length değerinden büyük olamaz.");
            }
        }

        if (config.runtime() == null) {
            errors.add("runtime yapılandırması eksik.");
        } else {
            if (config.runtime().tickIntervalTicks() <= 0) {
                errors.add("runtime.tick-interval-ticks 0'dan büyük olmalıdır.");
            }
            if (config.runtime().stopDistance() >= config.runtime().startDistance()) {
                errors.add("runtime.stop-distance, runtime.start-distance değerinden küçük olmalıdır.");
            }
            if (config.runtime().startDistance() >= config.runtime().teleportDistance()) {
                errors.add("runtime.start-distance, runtime.teleport-distance değerinden küçük olmalıdır.");
            }
            if (config.runtime().followSpeed() <= 0) {
                errors.add("runtime.follow-speed 0'dan büyük olmalıdır.");
            }
        }

        if (config.progression() == null) {
            errors.add("progression yapılandırması eksik.");
        } else {
            if (config.progression().maximumLevel() <= 0) {
                errors.add("progression.maximum-level 0'dan büyük olmalıdır.");
            }
        }

        if (config.locale() == null || config.locale().trim().isEmpty()) {
            errors.add("locale boş bırakılamaz.");
        }

        if (config.database() == null || config.database().maxBackups() <= 0) {
            errors.add("database.maximum-backups 0'dan büyük olmalıdır.");
        }

        return errors;
    }
}
