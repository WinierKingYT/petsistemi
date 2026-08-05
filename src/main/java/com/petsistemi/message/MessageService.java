package com.petsistemi.message;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class MessageService {

    private final JavaPlugin plugin;
    private final AtomicReference<MessageBundle> activeBundle = new AtomicReference<>();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        String initialLocale = (plugin != null && plugin.getConfig() != null) ? plugin.getConfig().getString("locale", "tr_TR") : "tr_TR";
        publish(loadCandidate(initialLocale));
    }

    public MessageBundle loadCandidate(String locale) {
        String targetLocale = (locale != null && !locale.trim().isEmpty()) ? locale.trim() : "tr_TR";
        YamlConfiguration yamlConfig = null;

        if (plugin != null && plugin.getDataFolder() != null) {
            File messageFile = new File(plugin.getDataFolder(), "messages/" + targetLocale + ".yml");
            if (messageFile.exists()) {
                try {
                    yamlConfig = YamlConfiguration.loadConfiguration(messageFile);
                } catch (Exception ignored) {}
            }
        }

        if (yamlConfig == null && plugin != null) {
            InputStream is = plugin.getResource("messages/" + targetLocale + ".yml");
            if (is == null) {
                is = plugin.getResource("messages/tr_TR.yml");
            }
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    yamlConfig = YamlConfiguration.loadConfiguration(reader);
                } catch (Exception ignored) {}
            }
        }

        if (yamlConfig == null) {
            yamlConfig = new YamlConfiguration();
        }

        return new MessageBundle(yamlConfig);
    }

    public void publish(MessageBundle candidate) {
        Objects.requireNonNull(candidate, "MessageBundle candidate null olamaz.");
        this.activeBundle.set(candidate);
    }

    public MessageBundle currentBundle() {
        return activeBundle.get();
    }

    public void reload() {
        String locale = (plugin != null && plugin.getConfig() != null) ? plugin.getConfig().getString("locale", "tr_TR") : "tr_TR";
        publish(loadCandidate(locale));
    }

    public void loadBundle(String locale) {
        publish(loadCandidate(locale));
    }

    public Component getComponent(String key, String fallback, PlaceholderMap placeholders) {
        MessageBundle bundle = currentBundle();
        if (bundle == null) {
            return MiniMessageRenderer.render(fallback, placeholders);
        }
        String raw = bundle.getMessage(key, fallback);
        return MiniMessageRenderer.render(raw, placeholders);
    }

    public Component getComponent(String key, String fallback) {
        return getComponent(key, fallback, null);
    }

    public void send(CommandSender sender, String key, String fallback) {
        if (sender != null) {
            sender.sendMessage(getComponent(key, fallback));
        }
    }

    public void send(CommandSender sender, String key, String fallback, PlaceholderMap placeholders) {
        if (sender != null) {
            sender.sendMessage(getComponent(key, fallback, placeholders));
        }
    }
}
