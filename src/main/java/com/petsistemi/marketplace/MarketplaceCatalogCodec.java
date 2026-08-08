package com.petsistemi.marketplace;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MarketplaceCatalogCodec {
    private MarketplaceCatalogCodec() {}

    public static Collection<MarketplaceEntry> decode(String text, URI catalogUri, boolean requireSha256) {
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.loadFromString(text); }
        catch (Exception error) { throw new IllegalArgumentException("Marketplace kataloğu ayrıştırılamadı: " + error.getMessage(), error); }
        if (yaml.getInt("schema-version", 1) != 1) throw new IllegalArgumentException("Desteklenmeyen marketplace schema-version.");
        List<?> rawEntries = yaml.getList("entries", List.of());
        List<MarketplaceEntry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rawEntries.size(); i++) {
            if (!(rawEntries.get(i) instanceof java.util.Map<?, ?> raw)) throw new IllegalArgumentException("entries[" + i + "] map olmalıdır.");
            String id = string(raw.get("id")).toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9][a-z0-9._-]{1,63}") || !ids.add(id)) throw new IllegalArgumentException("Geçersiz/tekrarlı marketplace id: " + id);
            String version = string(raw.get("version"));
            if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?")) throw new IllegalArgumentException(id + " version SemVer olmalıdır.");
            String minimumEngine = stringOr(raw.get("minimum-engine-version"), "0.2.0");
            if (!minimumEngine.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?")) {
                throw new IllegalArgumentException(id + " minimum-engine-version SemVer olmalıdır.");
            }
            URI download = catalogUri.resolve(string(raw.get("download-url")));
            requireSafeUri(download);
            String sha = raw.get("sha256") == null ? "" : raw.get("sha256").toString().trim().toLowerCase(Locale.ROOT);
            if (requireSha256 && !sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(id + " geçerli SHA-256 gerektirir.");
            if (!sha.isEmpty() && !sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(id + " SHA-256 alanı geçersiz.");
            List<String> tags = raw.get("tags") instanceof List<?> list ? list.stream().map(Object::toString).toList() : List.of();
            entries.add(new MarketplaceEntry(id, stringOr(raw.get("name"), id), version,
                    stringOr(raw.get("description"), ""), download, sha,
                    minimumEngine, tags));
        }
        return List.copyOf(entries);
    }

    static void requireSafeUri(URI uri) {
        String scheme = uri != null ? uri.getScheme() : null;
        boolean loopback = uri != null && uri.getHost() != null
                && (uri.getHost().equalsIgnoreCase("localhost") || uri.getHost().equals("127.0.0.1") || uri.getHost().equals("::1"));
        if (!("https".equalsIgnoreCase(scheme) || ("http".equalsIgnoreCase(scheme) && loopback))) {
            throw new IllegalArgumentException("Marketplace URL HTTPS olmalıdır (localhost testleri hariç): " + uri);
        }
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("URL içinde kullanıcı bilgisi kabul edilmez.");
    }

    private static String string(Object value) {
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("Zorunlu katalog alanı eksik.");
        return value.toString().trim();
    }
    private static String stringOr(Object value, String fallback) { return value == null ? fallback : value.toString().trim(); }
}
