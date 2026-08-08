package com.petsistemi.api.pack;

import com.petsistemi.pack.PetPackInstallResult;
import com.petsistemi.pack.PetPackManifest;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;

public interface PetPackService {
    PetPackInstallResult install(Path archive, URI source);
    PetPackInstallResult uninstall(String packId);
    Collection<PetPackManifest> installed();
    Path exportPack(PetPackManifest manifest, Collection<String> definitionIds, Path output);
}
