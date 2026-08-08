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
            if (config.progression().maximumLevel() < 1) {
                errors.add("progression.maximum-level en az 1 olmalıdır.");
            }
            if (config.progression().xpPerLevel() < 1) {
                errors.add("progression.xp-per-level en az 1 olmalıdır.");
            }
            if (config.progression().passiveXpPerMinute() < 0) {
                errors.add("progression.passive-xp-per-minute negatif olamaz.");
            }
            if (config.progression().walkXpThreshold() <= 0 || !Double.isFinite(config.progression().walkXpThreshold())) {
                errors.add("progression.walk-xp-threshold 0'dan büyük ve sonlu olmalıdır.");
            }
            if (config.progression().walkXpAmount() < 0) {
                errors.add("progression.walk-xp-amount negatif olamaz.");
            }
            if (config.progression().blockBreakXp() < 0) {
                errors.add("progression.block-break-xp negatif olamaz.");
            }
            if (config.progression().killXpMultiplier() < 0 || !Double.isFinite(config.progression().killXpMultiplier())) {
                errors.add("progression.kill-xp-multiplier negatif olamaz ve sonlu olmalıdır.");
            }
        }

        if (config.locale() == null || config.locale().trim().isEmpty()) {
            errors.add("locale boş bırakılamaz.");
        }

        if (config.database() == null || config.database().maxBackups() <= 0) {
            errors.add("database.maximum-backups 0'dan büyük olmalıdır.");
        } else {
            String backend = config.database().backend();
            if (!"SQLITE".equalsIgnoreCase(backend) && !"MYSQL".equalsIgnoreCase(backend)) {
                errors.add("database.backend SQLITE veya MYSQL olmalıdır.");
            }
            if ("MYSQL".equalsIgnoreCase(backend)) {
                PluginConfiguration.MysqlConfiguration mysql = config.database().mysql();
                if (mysql == null || mysql.host() == null || mysql.host().isBlank()
                        || mysql.database() == null || mysql.database().isBlank()
                        || mysql.username() == null || mysql.username().isBlank()) {
                    errors.add("MySQL host, database ve username alanları zorunludur.");
                } else if (mysql.port() < 1 || mysql.port() > 65535 || mysql.connectTimeoutMs() < 1000) {
                    errors.add("MySQL port veya connect-timeout-ms geçersizdir.");
                }
            }
        }

        if (config.ecosystem() == null) {
            errors.add("ecosystem yapılandırması eksik.");
        } else {
            var network = config.ecosystem().network();
            if (network == null || network.serverId() == null || network.serverId().isBlank()
                    || network.pollIntervalTicks() < 1 || network.batchSize() < 1 || network.batchSize() > 1000) {
                errors.add("ecosystem.network yapılandırması geçersizdir.");
            } else if (network.enabled() && !"MYSQL".equalsIgnoreCase(config.database().backend())) {
                errors.add("Network senkronizasyonu yalnızca MYSQL backend ile etkinleştirilebilir.");
            }
            var packs = config.ecosystem().petPacks();
            if (packs == null || packs.maximumFiles() < 1 || packs.maximumArchiveBytes() < 1024
                    || packs.maximumExpandedBytes() < packs.maximumArchiveBytes()) {
                errors.add("ecosystem.pet-packs limitleri geçersizdir.");
            }
            var market = config.ecosystem().marketplace();
            if (market == null || market.maximumDownloadBytes() < 1024 || market.requestTimeoutMs() < 1000) {
                errors.add("ecosystem.marketplace limitleri geçersizdir.");
            } else if (market.enabled() && (market.catalogUrl() == null || market.catalogUrl().isBlank())) {
                errors.add("Marketplace etkinse catalog-url zorunludur.");
            }
        }

        return errors;
    }
}
