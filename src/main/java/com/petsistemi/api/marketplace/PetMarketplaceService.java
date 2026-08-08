package com.petsistemi.api.marketplace;

import com.petsistemi.marketplace.MarketplaceEntry;
import com.petsistemi.pack.PetPackInstallResult;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface PetMarketplaceService {
    CompletableFuture<Collection<MarketplaceEntry>> refreshAsync();
    Collection<MarketplaceEntry> entries();
    CompletableFuture<PetPackInstallResult> installAsync(String entryId);
}
