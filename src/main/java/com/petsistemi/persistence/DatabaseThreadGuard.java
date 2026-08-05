package com.petsistemi.persistence;

public final class DatabaseThreadGuard {

    private static volatile DatabaseExecutor activeExecutor;
    private static volatile boolean guardEnabled = true;

    private DatabaseThreadGuard() {}

    public static void setDatabaseExecutor(DatabaseExecutor executor) {
        activeExecutor = executor;
    }

    public static void clearDatabaseExecutor(DatabaseExecutor executor) {
        if (activeExecutor == executor) {
            activeExecutor = null;
        }
    }

    public static void setGuardEnabled(boolean enabled) {
        guardEnabled = enabled;
    }

    public static boolean isDatabaseThread() {
        if (!guardEnabled || activeExecutor == null || activeExecutor.isClosed()) {
            return true;
        }
        return activeExecutor.isDatabaseThread();
    }

    public static void requireDatabaseThread() {
        if (guardEnabled && activeExecutor != null && !activeExecutor.isClosed() && !activeExecutor.isDatabaseThread()) {
            throw new IllegalStateException("Bu veritabanı işlemi yalnızca PetSistemi-Database thread'inde çalıştırılabilir! [Aktif thread: " + Thread.currentThread().getName() + "]");
        }
    }
}
