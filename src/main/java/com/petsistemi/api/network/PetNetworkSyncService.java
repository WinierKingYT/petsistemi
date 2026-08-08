package com.petsistemi.api.network;

import java.util.concurrent.CompletableFuture;

public interface PetNetworkSyncService {
    boolean enabled();
    long cursor();
    CompletableFuture<Integer> pollOnceAsync();
}
