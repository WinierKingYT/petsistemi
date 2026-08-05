package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AtomicPetDefinitionRegistry implements PetDefinitionRegistry {

    private final JavaPlugin plugin;
    private volatile Map<String, PetDefinition> registry = new ConcurrentHashMap<>();

    public AtomicPetDefinitionRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, PetDefinition> loadCandidateSnapshot() throws IllegalStateException {
        File petsFolder = new File(plugin.getDataFolder(), "pets");
        if (!petsFolder.exists()) {
            petsFolder.mkdirs();
            saveDefaultPetFiles(petsFolder);
        }

        File[] files = petsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            saveDefaultPetFiles(petsFolder);
            files = petsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        }

        Map<String, PetDefinition> candidateMap = new HashMap<>();
        Map<String, List<String>> errorsPerFile = new HashMap<>();

        if (files != null) {
            for (File file : files) {
                try {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    int schemaVersion = yaml.getInt("schema-version", 1);
                    String id = file.getName().replace(".yml", "").replace(".yaml", "").toLowerCase();
                    String displayName = yaml.getString("display-name", id);
                    List<String> description = yaml.getStringList("description");
                    String typeStr = yaml.getString("entity-type", "WOLF");
                    boolean baby = yaml.getBoolean("baby", false);
                    boolean glowing = yaml.getBoolean("glowing", false);
                    boolean invulnerable = yaml.getBoolean("invulnerable", true);
                    boolean silent = yaml.getBoolean("silent", false);
                    boolean gravity = yaml.getBoolean("gravity", true);

                    boolean progressionEnabled = yaml.getBoolean("progression.enabled", true);
                    int maxLevel = yaml.getInt("progression.maximum-level", 100);

                    boolean nameplateEnabled = yaml.getBoolean("nameplate.enabled", true);
                    List<String> nameplateFormat = yaml.getStringList("nameplate.format");
                    if (nameplateFormat == null || nameplateFormat.isEmpty()) {
                        nameplateFormat = List.of("<gradient:#ffaa00:#ff5500>{pet_name}</gradient> <gray>Lv.{level}</gray>");
                    }

                    PetDefinition def = new PetDefinition(
                            id,
                            displayName,
                            description,
                            typeStr.toUpperCase(),
                            baby,
                            glowing,
                            invulnerable,
                            silent,
                            gravity,
                            progressionEnabled,
                            maxLevel,
                            nameplateEnabled,
                            nameplateFormat
                    );

                    List<String> errors = PetDefinitionValidator.validate(def, schemaVersion);
                    if (!errors.isEmpty()) {
                        errorsPerFile.put(file.getName(), errors);
                    } else {
                        candidateMap.put(id, def);
                    }
                } catch (Exception e) {
                    errorsPerFile.put(file.getName(), List.of("Ayrıştırma hatası: " + e.getMessage()));
                }
            }
        }

        if (!errorsPerFile.isEmpty()) {
            String errorSummary = errorsPerFile.entrySet().stream()
                    .map(e -> e.getKey() + ": [" + String.join("; ", e.getValue()) + "]")
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Pet tanımları yüklenirken hata oluştu! Hatalı dosyalar: " + errorSummary);
        }

        return Collections.unmodifiableMap(candidateMap);
    }

    public void publishSnapshot(Map<String, PetDefinition> candidateMap) {
        Objects.requireNonNull(candidateMap, "PetDefinition candidate map null olamaz.");
        this.registry = new ConcurrentHashMap<>(candidateMap);
    }

    public Map<String, PetDefinition> currentSnapshot() {
        return Collections.unmodifiableMap(registry);
    }

    @Override
    public Optional<PetDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(registry.get(id.toLowerCase()));
    }

    @Override
    public Collection<PetDefinition> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    @Override
    public synchronized void reload() {
        try {
            Map<String, PetDefinition> candidate = loadCandidateSnapshot();
            publishSnapshot(candidate);
            if (plugin != null && plugin.getLogger() != null) {
                plugin.getLogger().info("Pet tanımları başarıyla yüklendi. Aktif tanım sayısı: " + candidate.size());
            }
        } catch (Exception e) {
            if (plugin != null && plugin.getLogger() != null) {
                plugin.getLogger().severe(e.getMessage());
            }
        }
    }

    private void saveDefaultPetFiles(File petsFolder) {
        String[] defaults = new String[]{"wolf.yml", "cat.yml", "allay.yml"};
        for (String defFile : defaults) {
            try {
                File target = new File(petsFolder, defFile);
                if (!target.exists() && plugin != null) {
                    plugin.saveResource("pets/" + defFile, false);
                }
            } catch (Exception ignored) {}
        }
    }
}
