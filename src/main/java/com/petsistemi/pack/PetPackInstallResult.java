package com.petsistemi.pack;

import java.util.List;

public record PetPackInstallResult(boolean success, String message, PetPackManifest manifest,
                                   List<String> installedDefinitionIds, boolean rolledBack) {
    public PetPackInstallResult {
        installedDefinitionIds = installedDefinitionIds == null ? List.of() : List.copyOf(installedDefinitionIds);
    }
}
