package com.petsistemi.definition.editor;

import com.petsistemi.definition.AtomicPetDefinitionRegistry;
import com.petsistemi.definition.PetDefinitionValidator;
import com.petsistemi.definition.PetDefinitionYamlParser;
import com.petsistemi.domain.PetDefinition;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Validates and atomically publishes administrator-authored definition drafts. */
public final class PetDefinitionEditorService {

    private static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9_-]+$");
    private final Path petsFolder;
    private final AtomicPetDefinitionRegistry registry;

    public PetDefinitionEditorService(JavaPlugin plugin, AtomicPetDefinitionRegistry registry) {
        this(plugin.getDataFolder().toPath().resolve("pets"), registry);
    }

    PetDefinitionEditorService(Path petsFolder, AtomicPetDefinitionRegistry registry) {
        this.petsFolder = petsFolder;
        this.registry = registry;
    }

    public Draft open(String rawId) throws IOException, InvalidConfigurationException {
        String id = normalizeId(rawId);
        Path file = resolveFile(id);
        String source = Files.readString(file, StandardCharsets.UTF_8);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return new Draft(id, file, source, yaml);
    }

    public Validation validate(Draft draft) {
        if (draft == null) return new Validation(null, List.of("Etkin bir taslak yok."));
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse(draft.id(), draft.yaml());
        List<String> errors = new ArrayList<>(parsed.errors());
        if (parsed.definition() != null) {
            errors.addAll(PetDefinitionValidator.validate(parsed.definition(), draft.yaml().getInt("schema-version", 1)));
        }
        return new Validation(parsed.definition(), List.copyOf(errors));
    }

    public synchronized SaveResult save(Draft draft) {
        if (draft == null) return new SaveResult(false, "Etkin bir taslak yok.", List.of());
        Validation validation = validate(draft);
        if (!validation.valid()) return new SaveResult(false, "Taslak doğrulanamadı.", validation.errors());

        try {
            String current = Files.readString(draft.file(), StandardCharsets.UTF_8);
            if (!current.equals(draft.originalSource())) {
                return new SaveResult(false, "Dosya editör dışında değiştirildi; çakışmayı önlemek için kayıt reddedildi.", List.of());
            }

            String candidate = draft.yaml().saveToString();
            atomicWrite(draft.file(), candidate);
            try {
                Map<String, PetDefinition> snapshot = registry.loadCandidateSnapshot();
                registry.publishSnapshot(snapshot);
            } catch (RuntimeException publishFailure) {
                atomicWrite(draft.file(), draft.originalSource());
                return new SaveResult(false, "Kayıt geri alındı: " + publishFailure.getMessage(), List.of());
            }
            draft.markSaved(candidate);
            return new SaveResult(true, "Tanım atomik olarak kaydedildi ve canlı kataloğa yayımlandı.", List.of());
        } catch (IOException e) {
            return new SaveResult(false, "Dosya kaydedilemedi: " + e.getMessage(), List.of());
        }
    }

    private Path resolveFile(String id) throws IOException {
        Path yml = petsFolder.resolve(id + ".yml").normalize();
        Path yaml = petsFolder.resolve(id + ".yaml").normalize();
        if (!yml.getParent().equals(petsFolder.normalize())) throw new IOException("Geçersiz tanım yolu.");
        if (Files.isRegularFile(yml)) return yml;
        if (Files.isRegularFile(yaml)) return yaml;
        throw new IOException("Pet tanım dosyası bulunamadı: " + id);
    }

    private static String normalizeId(String rawId) {
        String id = rawId == null ? "" : rawId.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_ID.matcher(id).matches()) throw new IllegalArgumentException("Geçersiz pet tanım kimliği: " + rawId);
        return id;
    }

    private static void atomicWrite(Path file, String source) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, source, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static final class Draft {
        private final String id;
        private final Path file;
        private String originalSource;
        private final YamlConfiguration yaml;

        private Draft(String id, Path file, String originalSource, YamlConfiguration yaml) {
            this.id = id;
            this.file = file;
            this.originalSource = originalSource;
            this.yaml = yaml;
        }

        public String id() { return id; }
        public Path file() { return file; }
        public String originalSource() { return originalSource; }
        public YamlConfiguration yaml() { return yaml; }
        public String value(PetEditorField field) {
            Object value = yaml.get(field.path());
            return value == null ? "—" : String.valueOf(value);
        }
        public void set(PetEditorField field, String input) {
            String value = input == null ? "" : input.trim();
            yaml.set(field.path(), field.removable() && "-".equals(value) ? null : value);
        }
        public boolean toggleGlowing() {
            String path = yaml.isConfigurationSection("representation") ? "representation.glowing" : "glowing";
            boolean next = !yaml.getBoolean(path, false);
            yaml.set(path, next);
            return next;
        }
        private void markSaved(String source) { this.originalSource = source; }
    }

    public record Validation(PetDefinition definition, List<String> errors) {
        public boolean valid() { return definition != null && errors.isEmpty(); }
    }

    public record SaveResult(boolean success, String message, List<String> errors) {}
}
