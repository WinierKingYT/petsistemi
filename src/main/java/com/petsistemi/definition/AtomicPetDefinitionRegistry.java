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
        Map<String, String> sourceFiles = new HashMap<>();

        if (files != null) {
            for (File file : files) {
                try {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    int schemaVersion = yaml.getInt("schema-version", 1);
                    String fileId = file.getName().replace(".yml", "").replace(".yaml", "").toLowerCase(java.util.Locale.ROOT);
                    String id = yaml.getString("id", fileId).trim().toLowerCase(java.util.Locale.ROOT);

                    PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse(id, yaml);
                    List<String> errors = new java.util.ArrayList<>(parsed.errors());
                    if (!id.matches("[a-z0-9][a-z0-9._-]{1,63}(?::[a-z0-9][a-z0-9._-]{1,63})?")) {
                        errors.add("Geçersiz pet kimliği: " + id);
                    }
                    if (parsed.definition() != null) {
                        errors.addAll(PetDefinitionValidator.validate(parsed.definition(), schemaVersion));
                    }

                    if (!errors.isEmpty()) {
                        errorsPerFile.put(file.getName(), errors);
                    } else if (parsed.definition() != null && definitions.containsKey(id)) {
                        errorsPerFile.put(file.getName(), List.of("Pet kimliği başka bir dosyada zaten tanımlı: " + id));
                    } else if (parsed.definition() != null) {
                        definitions.put(id, parsed.definition());
                        sourceFiles.put(id, file.getName());
                        PetConfigValidator.validateAndLog(parsed.definition(), plugin != null ? plugin.getLogger() : null);
                    }
                } catch (Exception e) {
                    errorsPerFile.put(file.getName(), List.of("Ayrıştırma hatası: " + e.getMessage()));
                }
            }
        }

        validateEvolutionReferences(definitions, sourceFiles, errorsPerFile);

        return new ScanResult(Collections.unmodifiableMap(definitions), Collections.unmodifiableMap(errorsPerFile));
    }

    public static void validateEvolutionReferences(Map<String, PetDefinition> definitions,
                                            Map<String, String> sourceFiles,
                                            Map<String, List<String>> errorsPerFile) {
        boolean removed;
        do {
            Map<String, List<String>> crossErrors = new HashMap<>();
            for (Map.Entry<String, PetDefinition> entry : definitions.entrySet()) {
                PetDefinition source = entry.getValue();
                if (source.evolutions() != null) {
                    for (int i = 0; i < source.evolutions().size(); i++) {
                        com.petsistemi.domain.PetEvolutionDefinition evolution = source.evolutions().get(i);
                        if (evolution == null || evolution.targetDefinitionId() == null) continue;
                        PetDefinition target = definitions.get(evolution.targetDefinitionId().toLowerCase(java.util.Locale.ROOT));
                        String path = "evolutions[" + i + "]";
                        if (target == null) {
                            crossErrors.computeIfAbsent(entry.getKey(), ignored -> new java.util.ArrayList<>())
                                    .add(path + ".target-id bulunamadı: " + evolution.targetDefinitionId());
                        } else if (!source.representationOrEntity().key().equals(target.representationOrEntity().key())) {
                            crossErrors.computeIfAbsent(entry.getKey(), ignored -> new java.util.ArrayList<>())
                                    .add(path + " farklı representation sağlayıcısına geçemez ("
                                            + source.representationOrEntity().key() + " -> " + target.representationOrEntity().key() + ").");
                        } else if (!compatibleVisualGraph(source, target)) {
                            String topologyError = source.representationOrEntity().type()
                                    == com.petsistemi.domain.RuntimeRepresentationType.PROCEDURAL
                                    ? " farklı PROCEDURAL node sayısına veya content provider'ına geçemez."
                                    : " farklı COMPOSITE/DISPLAY_MODEL topolojisine veya provider'ına geçemez.";
                            crossErrors.computeIfAbsent(entry.getKey(), ignored -> new java.util.ArrayList<>())
                                    .add(path + topologyError);
                        } else if (!java.util.Objects.equals(
                                source.movement() != null ? source.movement().key() : null,
                                target.movement() != null ? target.movement().key() : null)) {
                            crossErrors.computeIfAbsent(entry.getKey(), ignored -> new java.util.ArrayList<>())
                                    .add(path + " farklı movement sağlayıcısına geçemez.");
                        }
                    }
                }
                validateItemActionReferences(entry.getKey(), source, definitions, crossErrors);
            }
            crossErrors.forEach((id, errors) -> {
                definitions.remove(id);
                errorsPerFile.put(sourceFiles.getOrDefault(id, id + ".yml"), List.copyOf(errors));
            });
            removed = !crossErrors.isEmpty();
        } while (removed);
    }

    private static boolean compatibleVisualGraph(PetDefinition source, PetDefinition target) {
        com.petsistemi.domain.RuntimeRepresentationType type = source.representationOrEntity().type();
        if (type == com.petsistemi.domain.RuntimeRepresentationType.PROCEDURAL) {
            com.petsistemi.domain.visual.PetProceduralDefinition sourceProcedural =
                    source.representationOrEntity().procedural();
            com.petsistemi.domain.visual.PetProceduralDefinition targetProcedural =
                    target.representationOrEntity().procedural();
            return sourceProcedural != null && targetProcedural != null
                    && sourceProcedural.points() == targetProcedural.points()
                    && sourceProcedural.content().key().equals(targetProcedural.content().key());
        }
        if (type != com.petsistemi.domain.RuntimeRepresentationType.COMPOSITE
                && type != com.petsistemi.domain.RuntimeRepresentationType.DISPLAY_MODEL) return true;
        com.petsistemi.domain.visual.PetDisplayModelDefinition sourceModel = source.representationOrEntity().displayModel();
        com.petsistemi.domain.visual.PetDisplayModelDefinition targetModel = target.representationOrEntity().displayModel();
        com.petsistemi.domain.visual.PetVisualGraphDefinition sourceGraph = type
                == com.petsistemi.domain.RuntimeRepresentationType.COMPOSITE
                ? source.representationOrEntity().visualGraph()
                : (sourceModel != null ? sourceModel.skeleton() : null);
        com.petsistemi.domain.visual.PetVisualGraphDefinition targetGraph = type
                == com.petsistemi.domain.RuntimeRepresentationType.COMPOSITE
                ? target.representationOrEntity().visualGraph()
                : (targetModel != null ? targetModel.skeleton() : null);
        if (sourceGraph == null || targetGraph == null
                || !sourceGraph.rootId().equals(targetGraph.rootId())
                || sourceGraph.nodes().size() != targetGraph.nodes().size()) return false;
        for (com.petsistemi.domain.visual.PetVisualNodeDefinition node : sourceGraph.nodes()) {
            com.petsistemi.domain.visual.PetVisualNodeDefinition targetNode =
                    targetGraph.find(node.id()).orElse(null);
            if (targetNode == null
                    || !java.util.Objects.equals(node.parentId(), targetNode.parentId())
                    || !node.representation().key().equals(targetNode.representation().key())) return false;
        }
        return true;
    }

    private static void validateItemActionReferences(String sourceId, PetDefinition source,
                                                     Map<String, PetDefinition> definitions,
                                                     Map<String, List<String>> errors) {
        if (source.itemActions() == null) return;
        for (int i = 0; i < source.itemActions().size(); i++) {
            com.petsistemi.domain.item.PetItemActionDefinition action = source.itemActions().get(i);
            if (action == null || action.action() == null) continue;
            String parameter = null;
            if (com.petsistemi.runtime.item.BuiltInPetItemActions.UNLOCK_PET.equals(action.action())) {
                parameter = "definition-id";
            } else if (com.petsistemi.runtime.item.BuiltInPetItemActions.EVOLVE_PET.equals(action.action())) {
                parameter = "target-id";
            }
            if (parameter == null) continue;
            Object rawTarget = action.parameters().get(parameter);
            if (rawTarget == null || rawTarget.toString().isBlank()) continue;
            String targetId = rawTarget.toString().trim().toLowerCase(java.util.Locale.ROOT);
            if (!definitions.containsKey(targetId)) {
                errors.computeIfAbsent(sourceId, ignored -> new java.util.ArrayList<>())
                        .add("item-actions[" + i + "].parameters." + parameter + " bulunamadı: " + targetId);
            }
        }
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
            "phoenix.yml", "swarm_bees.yml", "fire_familiar.yml", "mechanical_bird.yml",
            "pixel_slime.yml", "astral_spirit.yml", "arcane_galaxy.yml");

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
