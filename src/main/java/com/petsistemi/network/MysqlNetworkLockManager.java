package com.petsistemi.network;

import com.petsistemi.persistence.ConnectionProvider;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/** Serializes writes concerning the same pet/owner across all MySQL-backed servers. */
public final class MysqlNetworkLockManager {
    private static final int WAIT_SECONDS = 5;
    private final ConnectionProvider connectionProvider;

    public MysqlNetworkLockManager(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    public void withLock(String resource, Runnable action) {
        withLock(resource, () -> {
            action.run();
            return null;
        });
    }

    public <T> T withLock(String resource, Supplier<T> action) {
        String key = "petsistemi:" + resource;
        boolean acquired = false;
        Throwable failure = null;
        try (PreparedStatement statement = connectionProvider.getConnection()
                .prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, key);
            statement.setInt(2, WAIT_SECONDS);
            try (ResultSet result = statement.executeQuery()) {
                acquired = result.next() && result.getInt(1) == 1;
            }
            if (!acquired) throw new IllegalStateException("Dağıtık pet kilidi alınamadı: " + resource);
            return action.get();
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } catch (java.sql.SQLException error) {
            IllegalStateException wrapped = new IllegalStateException("Dağıtık pet kilidi hatası: " + resource, error);
            failure = wrapped;
            throw wrapped;
        } finally {
            if (acquired) {
                try { release(key); }
                catch (RuntimeException releaseError) {
                    if (failure != null) failure.addSuppressed(releaseError); else throw releaseError;
                }
            }
        }
    }

    public void withLockTransaction(String resource, Runnable action) {
        withLock(resource, () -> {
            Connection connection = connectionProvider.getConnection();
            boolean autoCommit;
            try {
                autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
            } catch (SQLException error) {
                throw new IllegalStateException("Network transaction başlatılamadı: " + resource, error);
            }
            Throwable failure = null;
            try {
                action.run();
                connection.commit();
            } catch (RuntimeException | Error error) {
                try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                failure = error;
                throw error;
            } catch (SQLException error) {
                try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                IllegalStateException wrapped = new IllegalStateException("Network transaction başarısız: " + resource, error);
                failure = wrapped;
                throw wrapped;
            } finally {
                try { connection.setAutoCommit(autoCommit); }
                catch (SQLException error) {
                    if (failure != null) failure.addSuppressed(error);
                    else throw new IllegalStateException("Network transaction durumu geri yüklenemedi.", error);
                }
            }
            return null;
        });
    }

    Connection connection() {
        return connectionProvider.getConnection();
    }

    private void release(String key) {
        try (PreparedStatement statement = connectionProvider.getConnection()
                .prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, key);
            statement.executeQuery().close();
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException("Dağıtık pet kilidi bırakılamadı: " + key, error);
        }
    }
}
