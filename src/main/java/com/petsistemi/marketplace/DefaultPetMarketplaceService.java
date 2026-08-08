package com.petsistemi.marketplace;

import com.petsistemi.api.marketplace.PetMarketplaceService;
import com.petsistemi.pack.DefaultPetPackService;
import com.petsistemi.pack.PetPackInstallResult;
import com.petsistemi.pack.PetPackManifest;
import com.petsistemi.pack.PetPackManifestCodec;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DefaultPetMarketplaceService implements PetMarketplaceService {
    private static final long MAXIMUM_CATALOG_BYTES = 1_048_576L;
    private final JavaPlugin plugin;
    private final URI catalogUri;
    private final boolean requireSha256;
    private final long maximumDownloadBytes;
    private final Duration timeout;
    private final DefaultPetPackService packs;
    private final HttpClient client;
    private final Map<String, MarketplaceEntry> catalog = new ConcurrentHashMap<>();

    public DefaultPetMarketplaceService(JavaPlugin plugin, URI catalogUri, boolean requireSha256,
                                        long maximumDownloadBytes, int requestTimeoutMs,
                                        DefaultPetPackService packs) {
        this.plugin = plugin;
        MarketplaceCatalogCodec.requireSafeUri(catalogUri);
        this.catalogUri = catalogUri;
        this.requireSha256 = requireSha256;
        this.maximumDownloadBytes = maximumDownloadBytes;
        this.timeout = Duration.ofMillis(requestTimeoutMs);
        this.packs = packs;
        this.client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override public CompletableFuture<Collection<MarketplaceEntry>> refreshAsync() {
        HttpRequest request = HttpRequest.newBuilder(catalogUri).timeout(timeout).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    requireResponse(response.statusCode(), response.uri());
                    Collection<MarketplaceEntry> decoded = MarketplaceCatalogCodec.decode(
                            readTextLimited(response.body(), Math.min(maximumDownloadBytes, MAXIMUM_CATALOG_BYTES)),
                            response.uri(), requireSha256);
                    catalog.clear();
                    decoded.forEach(entry -> catalog.put(entry.id(), entry));
                    return entries();
                });
    }

    @Override public Collection<MarketplaceEntry> entries() {
        return catalog.values().stream().sorted(Comparator.comparing(MarketplaceEntry::id)).toList();
    }

    @Override public CompletableFuture<PetPackInstallResult> installAsync(String entryId) {
        MarketplaceEntry entry = catalog.get(entryId == null ? "" : entryId.trim().toLowerCase(Locale.ROOT));
        if (entry == null) return CompletableFuture.completedFuture(
                new PetPackInstallResult(false, "Marketplace girdisi bulunamadı: " + entryId, null, java.util.List.of(), false));
        HttpRequest request = HttpRequest.newBuilder(entry.downloadUri()).timeout(timeout).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> downloadAndInstall(entry, response))
                .exceptionally(error -> new PetPackInstallResult(false, "Marketplace kurulumu başarısız: "
                        + rootMessage(error), null, java.util.List.of(), false));
    }

    private PetPackInstallResult downloadAndInstall(MarketplaceEntry entry, HttpResponse<InputStream> response) {
        Path temporary = null;
        try {
            requireResponse(response.statusCode(), response.uri());
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declared > maximumDownloadBytes) throw new IllegalArgumentException("İndirme boyutu sınırı aşıyor.");
            Path downloads = plugin.getDataFolder().toPath().resolve("packs").resolve("downloads");
            Files.createDirectories(downloads);
            temporary = Files.createTempFile(downloads, entry.id() + "-", ".petpack.tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream input = response.body(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > maximumDownloadBytes) throw new IllegalArgumentException("İndirme boyutu sınırı aşıyor.");
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if ((requireSha256 || !entry.sha256().isBlank()) && !actual.equalsIgnoreCase(entry.sha256())) {
                throw new SecurityException("SHA-256 uyuşmuyor; paket kurulmadı.");
            }
            verifyManifestIdentity(temporary, entry);
            return packs.install(temporary, entry.downloadUri());
        } catch (Exception error) {
            return new PetPackInstallResult(false, "Marketplace paketi kurulamadı: " + error.getMessage(), null, java.util.List.of(), false);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
        }
    }

    private static void requireResponse(int status, URI finalUri) {
        MarketplaceCatalogCodec.requireSafeUri(finalUri);
        if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
    }
    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }
    private static void verifyManifestIdentity(Path archive, MarketplaceEntry entry) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry manifestEntry = zip.getEntry("pack.yml");
            if (manifestEntry == null || manifestEntry.isDirectory()) {
                throw new IllegalArgumentException("İndirilen pakette pack.yml eksik.");
            }
            PetPackManifest manifest = PetPackManifestCodec.decode(
                    readTextLimited(zip.getInputStream(manifestEntry), 65_536));
            if (!entry.id().equals(manifest.id()) || !entry.version().equals(manifest.version())) {
                throw new SecurityException("Katalog ve paket kimliği/sürümü eşleşmiyor.");
            }
            return;
        }
    }
    private static String readTextLimited(InputStream input, long limit) {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IllegalArgumentException("Marketplace kataloğu boyut sınırını aşıyor.");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Marketplace kataloğu okunamadı.", error);
        }
    }
}
