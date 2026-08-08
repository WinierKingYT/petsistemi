package com.petsistemi.persistence;

import java.util.Locale;

public enum DatabaseBackend {
    SQLITE,
    MYSQL;

    public static DatabaseBackend from(String raw) {
        if (raw == null) return SQLITE;
        try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return SQLITE; }
    }
}
