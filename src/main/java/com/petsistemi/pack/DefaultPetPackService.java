package com.petsistemi.pack;

import com.petsistemi.api.pack.PetPackService;
import com.petsistemi.definition.AtomicPetDefinitionRegistry;
import com.petsistemi.definition.PetDefinitionValidator;
import com.petsistemi.definition.PetDefinitionYamlParser;
import com.petsistemi.domain.PetDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class DefaultPetPackService implements PetPackService {
    private final JavaPlugin plugin;
    private final AtomicPetDefinitionRegistry registry;
    private final int maximumFiles;
    private final long maximumArchiveBytes;
    private final long maximumExpandedBytes;
    private final Path petsDirectory;
    private final Path receiptsDirectory;

    public DefaultPetPackService(JavaPlugin plugin, AtomicPetDefinitionRegistry registry,
                                 int maximumFiles, long maximumArchiveBytes, long maximumExpandedBytes) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.maximumFiles = maximumFiles;
        this.maximumArchiveBytes = maximumArchiveBytes;
        this.maximumExpandedBytes = maximumExpandedBytes;
        this.petsDirectory = plugin.getDataFolder().toPath().resolve("pets");
        this.receiptsDirectory = plugin.getDataFolder().toPath().resolve("packs").resolve("installed");
    }

    @Override
    public synchronized PetPackInstallResult install(Path archive, URI source) {
        PetPackManifest manifest = null;
        List<Path> committed = new ArrayList<>();
        Map<Path, byte[]> backups = new LinkedHashMap<>();
        try {
            if (archive == null || !Files.isRegularFile(archive)) throw new IllegalArgumentException("Paket arşivi bulunamadı.");
            long archiveSize = Files.size(archive);
            if (archiveSize < 1 || archiveSize > maximumArchiveBytes) throw new IllegalArgumentException("Paket arşivi boyut sınırını aşıyor.");
            ArchiveContent content = readArchive(archive);
            manifest = PetPackManifestCodec.decode(content.manifest());
            requireEngineCompatibility(manifest);
            requireDependencies(manifest);
            Map<String, PreparedDefinition> prepared = prepareDefinitions(manifest, content.petFiles());
            validateCombined(prepared);
            Files.createDirectories(petsDirectory);
            Files.createDirectories(receiptsDirectory);

            List<String> previousFiles = receiptFiles(manifest.id());

            for (PreparedDefinition definition : prepared.values()) {
                Path target = petsDirectory.resolve(definition.fileName()).normalize();
                requireInside(petsDirectory, target);
                if (Files.exists(target)) backups.put(target, Files.readAllBytes(target));
                Path temporary = Files.createTempFile(petsDirectory, ".pack-", ".tmp");
                Files.writeString(temporary, definition.yaml(), StandardCharsets.UTF_8);
                move(temporary, target);
                committed.add(target);
            }
            var currentFiles = prepared.values().stream().map(PreparedDefinition::fileName)
                    .collect(java.util.stream.Collectors.toSet());
            for (String previousFile : previousFiles) {
                if (currentFiles.contains(previousFile)) continue;
                Path stale = petsDirectory.resolve(previousFile).normalize();
                requireInside(petsDirectory, stale);
                if (Files.exists(stale)) {
                    backups.putIfAbsent(stale, Files.readAllBytes(stale));
                    Files.delete(stale);
                    committed.add(stale);
                }
            }
            registry.publishSnapshot(registry.loadCandidateSnapshot());
            String sha256 = sha256(Files.readAllBytes(archive));
            writeReceipt(manifest, sha256, source, committed);
            return new PetPackInstallResult(true, "Pet Pack kuruldu: " + manifest.id() + "@" + manifest.version(),
                    manifest, new ArrayList<>(prepared.keySet()), false);
        } catch (Exception error) {
            boolean rolledBack = rollback(committed, backups);
            return new PetPackInstallResult(false, "Pet Pack kurulamadı: " + error.getMessage(), manifest, List.of(), rolledBack);
        }
    }

    @Override
    public synchronized PetPackInstallResult uninstall(String packId) {
        String id = normalize(packId);
        Path receipt = receiptPath(id);
        if (!Files.isRegularFile(receipt)) return new PetPackInstallResult(false, "Kurulu paket bulunamadı: " + id, null, List.of(), false);
        Map<Path, byte[]> backups = new LinkedHashMap<>();
        PetPackManifest manifest = null;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(receipt.toFile());
            manifest = PetPackManifestCodec.decode(yaml.getString("manifest"));
            List<String> dependants = installed().stream()
                    .filter(candidate -> candidate.dependencies().contains(id))
                    .map(PetPackManifest::id).sorted().toList();
            if (!dependants.isEmpty()) throw new IllegalArgumentException("Pakete bağımlı kurulumlar var: " + dependants);
            for (String file : yaml.getStringList("files")) {
                Path target = petsDirectory.resolve(file).normalize();
                requireInside(petsDirectory, target);
                if (Files.exists(target)) {
                    backups.put(target, Files.readAllBytes(target));
                    Files.delete(target);
                }
            }
            registry.publishSnapshot(registry.loadCandidateSnapshot());
            Files.delete(receipt);
            return new PetPackInstallResult(true, "Pet Pack kaldırıldı: " + id, manifest, List.of(), false);
        } catch (Exception error) {
            boolean rolledBack = restore(backups);
            return new PetPackInstallResult(false, "Pet Pack kaldırılamadı: " + error.getMessage(), manifest, List.of(), rolledBack);
        }
    }

    @Override public Collection<PetPackManifest> installed() {
        if (!Files.isDirectory(receiptsDirectory)) return List.of();
        List<PetPackManifest> manifests = new ArrayList<>();
        try (var stream = Files.list(receiptsDirectory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().forEach(path -> {
                try {
                    YamlConfiguration receipt = YamlConfiguration.loadConfiguration(path.toFile());
                    manifests.add(PetPackManifestCodec.decode(receipt.getString("manifest")));
                } catch (Exception error) {
                    plugin.getLogger().warning("Bozuk Pet Pack receipt atlandı: " + path.getFileName());
                }
            });
        } catch (IOException error) {
            throw new IllegalStateException("Kurulu paketler okunamadı.", error);
        }
        return List.copyOf(manifests);
    }

    @Override public Path exportPack(PetPackManifest manifest, Collection<String> definitionIds, Path output) {
        Objects.requireNonNull(output, "output");
        if (definitionIds == null || definitionIds.isEmpty()) throw new IllegalArgumentException("En az bir pet tanımı gerekir.");
        try {
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
                writeZip(zip, "pack.yml", PetPackManifestCodec.encode(manifest));
                for (String rawId : definitionIds) {
                    String id = normalize(rawId);
                    PetDefinition definition = registry.find(id).orElseThrow(() -> new IllegalArgumentException("Pet tanımı bulunamadı: " + id));
                    if (id.contains(":") && !id.substring(0, id.indexOf(':')).equals(manifest.namespace())) {
                        throw new IllegalArgumentException("Pet namespace'i manifest ile eşleşmiyor: " + id);
                    }
                    Path source = findDefinitionFile(id).orElseThrow(() -> new IllegalArgumentException("Pet dosyası bulunamadı: " + id));
                    String local = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                    String yaml = Files.readString(source, StandardCharsets.UTF_8);
                    writeZip(zip, "pets/" + local + ".yml", yaml);
                }
            }
            return output;
        } catch (IOException error) {
            throw new IllegalStateException("Pet Pack dışa aktarılamadı.", error);
        }
    }

    private ArchiveContent readArchive(Path archive) throws IOException {
        String manifest = null;
        Map<String, String> pets = new LinkedHashMap<>();
        int files = 0;
        long expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                files++;
                if (files > maximumFiles) throw new IllegalArgumentException("Paket dosya sayısı sınırını aşıyor.");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.contains(":")) throw new IllegalArgumentException("Güvensiz ZIP yolu: " + name);
                byte[] bytes = readLimited(zip, maximumExpandedBytes - expanded);
                expanded += bytes.length;
                if (expanded > maximumExpandedBytes) throw new IllegalArgumentException("Açılmış paket boyutu sınırı aşıyor.");
                String text = new String(bytes, StandardCharsets.UTF_8);
                if ("pack.yml".equals(name)) manifest = text;
                else if (name.matches("pets/[a-zA-Z0-9._-]+\\.ya?ml") && pets.putIfAbsent(name, text) != null) {
                    throw new IllegalArgumentException("Tekrarlı paket dosyası: " + name);
                }
            }
        }
        if (manifest == null) throw new IllegalArgumentException("pack.yml eksik.");
        if (pets.isEmpty()) throw new IllegalArgumentException("Paket en az bir pets/*.yml içermelidir.");
        return new ArchiveContent(manifest, pets);
    }

    private Map<String, PreparedDefinition> prepareDefinitions(PetPackManifest manifest, Map<String, String> files) throws Exception {
        Map<String, PreparedDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            String localId = file.getKey().substring("pets/".length()).replaceFirst("\\.ya?ml$", "").toLowerCase(Locale.ROOT);
            String id = manifest.namespace() + ":" + localId;
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(file.getValue());
            String declared = normalize(yaml.getString("id", id));
            if (!id.equals(declared)) throw new IllegalArgumentException(file.getKey() + " id alanı " + id + " olmalıdır.");
            yaml.set("id", id);
            PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse(id, yaml);
            List<String> errors = new ArrayList<>(parsed.errors());
            if (parsed.definition() != null) errors.addAll(PetDefinitionValidator.validate(parsed.definition(), yaml.getInt("schema-version", 1)));
            if (!errors.isEmpty() || parsed.definition() == null) throw new IllegalArgumentException(file.getKey() + ": " + String.join("; ", errors));
            String fileName = "pack-" + manifest.namespace() + "-" + localId + ".yml";
            result.put(id, new PreparedDefinition(fileName, yaml.saveToString(), parsed.definition()));
        }
        return result;
    }

    private void validateCombined(Map<String, PreparedDefinition> prepared) {
        Map<String, PetDefinition> combined = new HashMap<>(registry.currentSnapshot());
        Map<String, String> sources = new HashMap<>();
        combined.keySet().forEach(id -> sources.put(id, id));
        prepared.forEach((id, value) -> {
            combined.put(id, value.definition());
            sources.put(id, value.fileName());
        });
        Map<String, List<String>> errors = new HashMap<>();
        AtomicPetDefinitionRegistry.validateEvolutionReferences(combined, sources, errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException("Paket referans doğrulaması başarısız: " + errors);
    }

    private void requireDependencies(PetPackManifest manifest) {
        var installedIds = installed().stream().map(PetPackManifest::id).collect(java.util.stream.Collectors.toSet());
        List<String> missing = manifest.dependencies().stream().filter(id -> !installedIds.contains(id)).toList();
        if (!missing.isEmpty()) throw new IllegalArgumentException("Eksik paket bağımlılıkları: " + missing);
    }

    private void requireEngineCompatibility(PetPackManifest manifest) {
        String current = "0.2.0";
        if (plugin.getDescription() != null && plugin.getDescription().getVersion() != null) {
            current = plugin.getDescription().getVersion();
        }
        if (compareVersionCore(current, manifest.minimumEngineVersion()) < 0) {
            throw new IllegalArgumentException("Paket motor " + manifest.minimumEngineVersion()
                    + "+ gerektiriyor; kurulu sürüm " + current + ".");
        }
    }

    private List<String> receiptFiles(String packId) {
        Path receipt = receiptPath(packId);
        if (!Files.isRegularFile(receipt)) return List.of();
        return List.copyOf(YamlConfiguration.loadConfiguration(receipt.toFile()).getStringList("files"));
    }

    private void writeReceipt(PetPackManifest manifest, String sha256, URI source, List<Path> files) throws IOException {
        YamlConfiguration receipt = new YamlConfiguration();
        receipt.set("manifest", PetPackManifestCodec.encode(manifest));
        receipt.set("sha256", sha256);
        receipt.set("source-uri", source != null ? source.toString() : null);
        receipt.set("installed-at", System.currentTimeMillis());
        receipt.set("files", files.stream().map(path -> path.getFileName().toString()).toList());
        Path target = receiptPath(manifest.id());
        Path temporary = Files.createTempFile(receiptsDirectory, ".receipt-", ".tmp");
        Files.writeString(temporary, receipt.saveToString(), StandardCharsets.UTF_8);
        move(temporary, target);
    }

    private Optional<Path> findDefinitionFile(String id) throws IOException {
        if (!Files.isDirectory(petsDirectory)) return Optional.empty();
        try (var stream = Files.list(petsDirectory)) {
            return stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().matches(".*\\.ya?ml"))
                    .filter(path -> {
                        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
                        String fileId = path.getFileName().toString().replaceFirst("\\.ya?ml$", "").toLowerCase(Locale.ROOT);
                        return id.equals(normalize(yaml.getString("id", fileId)));
                    }).findFirst();
        }
    }

    private boolean rollback(List<Path> committed, Map<Path, byte[]> backups) {
        boolean success = true;
        for (Path path : committed) {
            try {
                byte[] old = backups.get(path);
                if (old != null) Files.write(path, old); else Files.deleteIfExists(path);
            } catch (IOException error) { success = false; }
        }
        try { registry.publishSnapshot(registry.loadCandidateSnapshot()); }
        catch (Exception error) { success = false; }
        return success;
    }

    private boolean restore(Map<Path, byte[]> backups) {
        boolean success = true;
        for (Map.Entry<Path, byte[]> backup : backups.entrySet()) {
            try { Files.write(backup.getKey(), backup.getValue()); }
            catch (IOException error) { success = false; }
        }
        try { registry.publishSnapshot(registry.loadCandidateSnapshot()); }
        catch (Exception error) { success = false; }
        return success;
    }

    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        if (limit < 0) throw new IllegalArgumentException("Açılmış paket boyutu sınırı aşıyor.");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IllegalArgumentException("Açılmış paket boyutu sınırı aşıyor.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static void writeZip(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static String sha256(byte[] data) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static int compareVersionCore(String left, String right) {
        int[] a = versionCore(left);
        int[] b = versionCore(right);
        for (int i = 0; i < 3; i++) {
            int compared = Integer.compare(a[i], b[i]);
            if (compared != 0) return compared;
        }
        return 0;
    }
    private static int[] versionCore(String version) {
        String core = version == null ? "" : version.trim().split("[-+]", 2)[0];
        String[] parts = core.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Geçersiz motor SemVer: " + version);
        try { return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])}; }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Geçersiz motor SemVer: " + version); }
    }
    private Path receiptPath(String id) { return receiptsDirectory.resolve(normalize(id) + ".yml").normalize(); }
    private static void requireInside(Path root, Path target) {
        if (!target.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) throw new IllegalArgumentException("Hedef klasör dışına çıkıyor.");
    }

    private record ArchiveContent(String manifest, Map<String, String> petFiles) {}
    private record PreparedDefinition(String fileName, String yaml, PetDefinition definition) {}
}
