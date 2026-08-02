package com.petsistemi.message;

import java.util.HashMap;
import java.util.Map;

public final class PlaceholderMap {

    private final Map<String, String> map = new HashMap<>();

    public PlaceholderMap add(String key, String value) {
        map.put(key, value != null ? value : "");
        return this;
    }

    public static PlaceholderMap of(String key, String value) {
        return new PlaceholderMap().add(key, value);
    }

    public Map<String, String> getMap() {
        return map;
    }
}
