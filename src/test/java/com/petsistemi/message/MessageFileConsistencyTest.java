package com.petsistemi.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageFileConsistencyTest {

    private static YamlConfiguration load(String path) {
        InputStream is = MessageFileConsistencyTest.class.getClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new IllegalStateException("Resource bulunamadı: " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Yaml yüklenemedi: " + path, e);
        }
    }

    private static Set<String> flattenKeys(YamlConfiguration yaml) {
        return StreamSupport.stream(yaml.getKeys(true).spliterator(), false)
                .filter(key -> !key.endsWith(".") && yaml.get(key) instanceof String)
                .collect(Collectors.toSet());
    }

    @Test
    void trAndEnHaveIdenticalKeySets() {
        Set<String> trKeys = flattenKeys(load("messages/tr_TR.yml"));
        Set<String> enKeys = flattenKeys(load("messages/en_US.yml"));

        assertEquals(trKeys, enKeys, "tr_TR ve en_US anahtar setleri aynı olmalı. Eksik/fazla: " + missingDiff(trKeys, enKeys));
    }

    private static Set<String> missingDiff(Set<String> tr, Set<String> en) {
        Set<String> diff = new java.util.HashSet<>(tr);
        diff.removeAll(en);
        return diff;
    }

    @Test
    void everyMessageFileContainsRequiredCoreKeys() {
        for (String path : new String[]{"messages/tr_TR.yml", "messages/en_US.yml"}) {
            Set<String> keys = flattenKeys(load(path));
            for (String required : new String[]{
                    "command.only-players", "command.no-permission", "command.pet-summoned",
                    "command.pet-dismissed", "command.pet-renamed", "command.rename-prompt",
                    "command.rename-cancelled", "command.rename-failed", "command.help",
                    "command.level-up", "riding.enter", "riding.exit", "inspect.not-found",
                    "mode.usage", "mode.invalid", "mode.require-spawned", "mode.set"
            }) {
                assertTrue(keys.contains(required), path + " eksik anahtar: " + required);
            }
        }
    }

    @Test
    void messageTemplatesUseBalancedMiniMessageTags() {
        for (String path : new String[]{"messages/tr_TR.yml", "messages/en_US.yml"}) {
            YamlConfiguration yaml = load(path);
            for (String key : flattenKeys(yaml)) {
                String value = yaml.getString(key);
                int opens = value.length() - value.replace("<", "").length();
                int closes = value.length() - value.replace(">", "").length();
                // placeholder tags (<name>) and self-contained tags are balanced by character count
                if (opens != closes) {
                    throw new AssertionError(path + " -> " + key + " dengesiz < > sayısı: " + value);
                }
            }
        }
    }
}
