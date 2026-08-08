package com.petsistemi.pack;

import java.util.List;

public record PetPackManifest(int schemaVersion, String id, String namespace, String version,
                              String minimumEngineVersion, String description,
                              List<String> authors, List<String> dependencies) {
    public PetPackManifest {
        authors = authors == null ? List.of() : List.copyOf(authors);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
