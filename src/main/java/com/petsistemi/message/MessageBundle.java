package com.petsistemi.message;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;

public class MessageBundle {

    private final Map<String, String> messages = new HashMap<>();

    public MessageBundle(YamlConfiguration config) {
        if (config != null) {
            for (String key : config.getKeys(true)) {
                if (config.isString(key)) {
                    messages.put(key, config.getString(key));
                }
            }
        }
    }

    public String getMessage(String key, String fallback) {
        return messages.getOrDefault(key, fallback != null ? fallback : key);
    }
}
