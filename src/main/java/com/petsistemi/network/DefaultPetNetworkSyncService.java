package com.petsistemi.network;

import com.petsistemi.api.network.PetNetworkSyncService;
import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.bootstrap.MainThreadDispatcher;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultPetNetworkSyncService implements PetNetworkSyncService {
    private final String serverId;
    private final int batchSize;
    private final long retentionMillis;
    private final JdbcPetNetworkEventStore store;
    private final DatabaseExecutor database;
    private final MainThreadDispatcher mainThread;
    private final PlayerPetProfileCache cache;
    private final PetRuntimeCoordinator coordinator;
    private final PetRuntimeOperationService operations;
    private final Logger logger;
    private final AtomicLong cursor = new AtomicLong();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean polling = new AtomicBoolean();

    public DefaultPetNetworkSyncService(String serverId, int batchSize, long retentionMillis,
                                        JdbcPetNetworkEventStore store, DatabaseExecutor database,
                                        MainThreadDispatcher mainThread, PlayerPetProfileCache cache,
                                        PetRuntimeCoordinator coordinator, PetRuntimeOperationService operations,
                                        Logger logger) {
        this.serverId = serverId;
        this.batchSize = batchSize;
        this.retentionMillis = retentionMillis;
        this.store = store;
        this.database = database;
        this.mainThread = mainThread;
        this.cache = cache;
        this.coordinator = coordinator;
        this.operations = operations;
        this.logger = logger;
    }

    @Override public boolean enabled() { return true; }
    @Override public long cursor() { return cursor.get(); }

    @Override public CompletableFuture<Integer> pollOnceAsync() {
        if (!polling.compareAndSet(false, true)) return CompletableFuture.completedFuture(0);
        CompletableFuture<List<PetNetworkEvent>> read = database.submit(() -> {
            if (initialized.compareAndSet(false, true)) {
                store.deleteOlderThan(System.currentTimeMillis() - retentionMillis);
            }
            return store.pollAfter(cursor.get(), batchSize);
        });
        return read.thenCompose(events -> mainThread.run(() -> apply(events)).thenApply(ignored -> events.size()))
                .whenComplete((result, error) -> {
                    polling.set(false);
                    if (error != null && logger != null) logger.log(Level.WARNING, "Network senkronizasyon turu başarısız.", error);
                });
    }

    private void apply(List<PetNetworkEvent> events) {
        var changedOwners = new LinkedHashSet<UUID>();
        for (PetNetworkEvent event : events) {
            cursor.accumulateAndGet(event.eventId(), Math::max);
            if (serverId.equals(event.serverId()) || event.ownerId() == null) continue;
            changedOwners.add(event.ownerId());
        }
        for (UUID ownerId : changedOwners) {
            if (cache != null) cache.evict(ownerId);
            Player player = Bukkit.getPlayer(ownerId);
            if (player == null || !player.isOnline()) continue;
            if (coordinator != null) coordinator.despawnRuntime(ownerId);
            if (operations != null) operations.restoreSelectedPetAsync(player);
        }
    }
}
