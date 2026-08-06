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

                    PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse(id, yaml);
                    List<String> errors = new java.util.ArrayList<>(parsed.errors());
                    if (parsed.definition() != null) {
                        errors.addAll(PetDefinitionValidator.validate(parsed.definition(), schemaVersion));
                    }

                    if (!errors.isEmpty()) {
                        errorsPerFile.put(file.getName(), errors);
                    } else if (parsed.definition() != null) {
                        candidateMap.put(id, parsed.definition());
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

    /**
     * Pet templates copied into {@code plugins/PetSistemi/pets/} on first run.
     * Every file bundled under {@code resources/pets/} must be listed here, otherwise the
     * showcase pets exist in the jar but never reach a fresh server.
     * {@code BundledPetDefinitionsTest} fails if this list drifts from the resource folder.
     */
    static final List<String> DEFAULT_PET_FILES = List.of(
            "wolf.yml", "cat.yml", "allay.yml",
            "arcane_crystal.yml", "floating_book.yml", "shoulder_orb.yml", "ghost_scribe.yml",
            "familiar_swarm.yml", "void_cube.yml", "spirit_flame.yml",
            "sleepy_cat.yml", "wisplight.yml",
            "shadow_wisp.yml", "mirror_doll.yml", "echo_phantom.yml", "roam_fox.yml");

    private void saveDefaultPetFiles(File petsFolder) {
        for (String defFile : DEFAULT_PET_FILES) {
            try {
                File target = new File(petsFolder, defFile);
                if (!target.exists() && plugin != null) {
                    plugin.saveResource("pets/" + defFile, false);
                }
            } catch (Exception e) {
                if (plugin != null && plugin.getLogger() != null) {
                    plugin.getLogger().warning("Varsayılan pet şablonu kopyalanamadı: " + defFile + " (" + e.getMessage() + ")");
                }
            }
        }
    }
}
