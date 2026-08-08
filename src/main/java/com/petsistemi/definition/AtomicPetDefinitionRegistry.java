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
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AtomicPetDefinitionRegistry implements PetDefinitionRegistry {

    private final JavaPlugin plugin;
    private volatile Map<String, PetDefinition> registry = new ConcurrentHashMap<>();

    public AtomicPetDefinitionRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Outcome of reading the pets folder: the definitions that are usable, plus the
     * per-file errors that kept the rest out. Never partial in a hidden way — callers
     * decide whether errors are fatal.
     */
    public record ScanResult(Map<String, PetDefinition> definitions, Map<String, List<String>> errorsPerFile) {

        public boolean hasErrors() {
            return !errorsPerFile.isEmpty();
        }

        public String errorSummary() {
            return errorsPerFile.entrySet().stream()
                    .map(e -> e.getKey() + ": [" + String.join("; ", e.getValue()) + "]")
                    .collect(Collectors.joining(", "));
        }
    }

    /**
     * Reads every {@code pets/*.yml}, collecting valid definitions and per-file errors
     * side by side. Does not throw on definition errors — that judgement belongs to the
     * caller, because startup and reload want opposite behaviour.
     */
    public ScanResult scanPetsFolder() {
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

        Map<String, PetDefinition> definitions = new HashMap<>();
        Map<String, List<String>> errorsPerFile = new HashMap<>();

        if (files != null) {
            for (File file : files) {
                try {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    int schemaVersion = yaml.getInt("schema-version", 1);
                    String id = file.getName().replace(".yml", "").replace(".yaml", "").toLowerCase(java.util.Locale.ROOT);

                    PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse(id, yaml);
                    List<String> errors = new java.util.ArrayList<>(parsed.errors());
                    if (parsed.definition() != null) {
                        errors.addAll(PetDefinitionValidator.validate(parsed.definition(), schemaVersion));
                    }

                    if (!errors.isEmpty()) {
                        errorsPerFile.put(file.getName(), errors);
                    } else if (parsed.definition() != null) {
                        definitions.put(id, parsed.definition());
                        PetConfigValidator.validateAndLog(parsed.definition(), plugin != null ? plugin.getLogger() : null);
                    }
                } catch (Exception e) {
                    errorsPerFile.put(file.getName(), List.of("Ayrıştırma hatası: " + e.getMessage()));
                }
            }
        }

        return new ScanResult(Collections.unmodifiableMap(definitions), Collections.unmodifiableMap(errorsPerFile));
    }

    /**
     * Strict load used by {@code /petadmin reload}: a single bad file rejects the whole
     * candidate so the running snapshot is never replaced by a half-valid one.
     */
    public Map<String, PetDefinition> loadCandidateSnapshot() throws IllegalStateException {
        ScanResult scan = scanPetsFolder();
        if (scan.hasErrors()) {
            throw new IllegalStateException("Pet tanımları yüklenirken hata oluştu! Hatalı dosyalar: " + scan.errorSummary());
        }
        return scan.definitions();
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
        return Optional.ofNullable(registry.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    public Collection<PetDefinition> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /**
     * Startup load: publishes every definition that is valid and reports the rest loudly.
     * A typo in one admin-authored file must not leave the server with zero pets — the
     * all-or-nothing guarantee belongs to {@code /petadmin reload}, not to boot.
     */
    @Override
    public synchronized void reload() {
        ScanResult scan;
        try {
            scan = scanPetsFolder();
        } catch (Exception e) {
            log(Level.SEVERE, "Pet tanım klasörü okunamadı: " + e.getMessage());
            return;
        }

        publishSnapshot(scan.definitions());

        if (scan.hasErrors()) {
            log(Level.SEVERE, "Reddedilen pet tanımı: " + scan.errorsPerFile().size()
                    + " dosya yüklenemedi -> " + scan.errorSummary());
            log(Level.SEVERE, "Bu petler düzeltilene kadar kullanılamaz. Diğer petler normal çalışmaya devam ediyor.");
        }

        if (scan.definitions().isEmpty()) {
            log(Level.SEVERE, "Hiçbir pet tanımı yüklenemedi! pets/ klasörünü kontrol edin.");
        } else {
            log(Level.INFO, "Pet tanımları yüklendi. Aktif tanım sayısı: " + scan.definitions().size());
            // What each pet actually resolved to. Without this, diagnosing "my particle pet
            // does not hover" means guessing whether the server is running a stale YAML —
            // saveDefaultPetFiles never overwrites a file that already exists.
            log(Level.INFO, "Yüklenen petler: " + summarise(scan.definitions()));
        }
    }

    /** Renders each pet as {@code id(REPRESENTATION/MOVEMENT)} for the startup log. */
    static String summarise(Map<String, PetDefinition> definitions) {
        return definitions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    PetDefinition def = entry.getValue();
                    String representation = def.representationOrEntity() != null
                            ? String.valueOf(def.representationOrEntity().type())
                            : "?";
                    String movement = def.movement() != null
                            ? String.valueOf(def.movement().type())
                            : "config";
                    return entry.getKey() + "(" + representation + "/" + movement + ")";
                })
                .collect(Collectors.joining(", "));
    }

    private void log(Level level, String message) {
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().log(level, message);
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
            "shadow_wisp.yml", "mirror_doll.yml", "echo_phantom.yml", "roam_fox.yml",
            "phoenix.yml", "swarm_bees.yml");

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
