package com.petsistemi.marketplace;

import java.net.URI;
import java.util.List;

public record MarketplaceEntry(String id, String name, String version, String description,
                               URI downloadUri, String sha256, String minimumEngineVersion,
                               List<String> tags) {
    public MarketplaceEntry {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
