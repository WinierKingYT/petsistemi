package com.petsistemi.pack;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PetPackManifestCodec {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{1,63}");
    private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?");

    private PetPackManifestCodec() {}

    public static PetPackManifest decode(String yamlText) {
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.loadFromString(yamlText); }
        catch (Exception error) { throw new IllegalArgumentException("pack.yml ayrıştırılamadı: " + error.getMessage(), error); }
        PetPackManifest manifest = new PetPackManifest(
                yaml.getInt("schema-version", 1), normalize(yaml.getString("id")),
                normalize(yaml.getString("namespace")), yaml.getString("version", ""),
                yaml.getString("minimum-engine-version", "0.2.0"), yaml.getString("description", ""),
                yaml.getStringList("authors"), yaml.getStringList("dependencies").stream().map(PetPackManifestCodec::normalize).toList());
        List<String> errors = validate(manifest);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        return manifest;
    }

    public static String encode(PetPackManifest manifest) {
        List<String> errors = validate(manifest);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", manifest.schemaVersion());
        yaml.set("id", manifest.id());
        yaml.set("namespace", manifest.namespace());
        yaml.set("version", manifest.version());
        yaml.set("minimum-engine-version", manifest.minimumEngineVersion());
        yaml.set("description", manifest.description());
        yaml.set("authors", manifest.authors());
        yaml.set("dependencies", manifest.dependencies());
        return yaml.saveToString();
    }

    public static List<String> validate(PetPackManifest manifest) {
        List<String> errors = new ArrayList<>();
        if (manifest == null) return List.of("Manifest eksik.");
        if (manifest.schemaVersion() != 1) errors.add("Desteklenmeyen pack schema-version: " + manifest.schemaVersion());
        if (manifest.id() == null || !ID.matcher(manifest.id()).matches()) errors.add("Geçersiz pack id.");
        if (manifest.namespace() == null || !ID.matcher(manifest.namespace()).matches()) errors.add("Geçersiz namespace.");
        if (manifest.version() == null || !VERSION.matcher(manifest.version()).matches()) errors.add("version SemVer olmalıdır.");
        if (manifest.minimumEngineVersion() == null || !VERSION.matcher(manifest.minimumEngineVersion()).matches()) errors.add("minimum-engine-version SemVer olmalıdır.");
        for (String dependency : manifest.dependencies()) {
            if (dependency == null || !ID.matcher(dependency).matches()) errors.add("Geçersiz paket bağımlılığı: " + dependency);
        }
        if (manifest.dependencies().contains(manifest.id())) errors.add("Paket kendisine bağımlı olamaz.");
        return List.copyOf(errors);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
