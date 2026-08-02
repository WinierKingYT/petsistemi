package com.petsistemi.message;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessageService {

    private final JavaPlugin plugin;
    private MessageBundle activeBundle;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        loadBundle(plugin.getConfig().getString("locale", "tr_TR"));
    }

    public void loadBundle(String locale) {
        File messageFile = new File(plugin.getDataFolder(), "messages/" + locale + ".yml");
        YamlConfiguration yamlConfig;

        if (messageFile.exists()) {
            yamlConfig = YamlConfiguration.loadConfiguration(messageFile);
        } else {
            InputStream is = plugin.getResource("messages/" + locale + ".yml");
            if (is == null) {
                is = plugin.getResource("messages/tr_TR.yml");
            }
            if (is != null) {
                yamlConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
            } else {
                yamlConfig = new YamlConfiguration();
            }
        }

        this.activeBundle = new MessageBundle(yamlConfig);
    }

    public Component getComponent(String key, String fallback, PlaceholderMap placeholders) {
        String raw = activeBundle.getMessage(key, fallback);
        return MiniMessageRenderer.render(raw, placeholders);
    }

    public Component getComponent(String key, String fallback) {
        return getComponent(key, fallback, null);
    }
}
