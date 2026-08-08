package com.petsistemi.marketplace;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceCatalogCodecTest {
    @Test void parsesHttpsCatalogWithRequiredChecksum() {
        String sha = "a".repeat(64);
        String yaml = """
                schema-version: 1
                entries:
                  - id: forest-pack
                    name: Forest Pack
                    version: 1.0.0
                    download-url: packs/forest.petpack
                    sha256: %s
                """.formatted(sha);

        MarketplaceEntry entry = MarketplaceCatalogCodec.decode(yaml,
                URI.create("https://example.com/catalog.yml"), true).iterator().next();

        assertEquals(URI.create("https://example.com/packs/forest.petpack"), entry.downloadUri());
        assertEquals(sha, entry.sha256());
    }

    @Test void rejectsInsecureRemoteUrlAndMissingChecksum() {
        assertThrows(IllegalArgumentException.class, () -> MarketplaceCatalogCodec.requireSafeUri(
                URI.create("http://example.com/catalog.yml")));
        String yaml = """
                entries:
                  - id: forest-pack
                    version: 1.0.0
                    download-url: https://example.com/forest.petpack
                """;
        assertThrows(IllegalArgumentException.class, () -> MarketplaceCatalogCodec.decode(yaml,
                URI.create("https://example.com/catalog.yml"), true));
    }

    @Test void permitsMissingChecksumOnlyWhenPolicyDisablesRequirement() {
        String yaml = """
                entries:
                  - id: local-pack
                    version: 1.0.0
                    download-url: https://example.com/local.petpack
                """;

        MarketplaceEntry entry = MarketplaceCatalogCodec.decode(yaml,
                URI.create("https://example.com/catalog.yml"), false).iterator().next();

        assertEquals("", entry.sha256());
    }
}
