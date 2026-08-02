package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

public class YamlPetDefinitionRegistry implements PetDefinitionRegistry {

    private final JavaPlugin plugin;
    private final Map<String, PetDefinition> definitions = new HashMap<>();
    private final File petsDir;

    public YamlPetDefinitionRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.petsDir = new File(plugin.getDataFolder(), "pets");
    }

    @Override
    public Optional<PetDefinition> find(String definitionId) {
        if (definitionId == null) return Optional.empty();
        return Optional.ofNullable(definitions.get(definitionId.toLowerCase()));
    }

    @Override
    public Collection<PetDefinition> getAll() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    @Override
    public void reload() {
        definitions.clear();
        ensureDirectoryExists();
        loadDefinitions();
    }

    private void ensureDirectoryExists() {
        if (!petsDir.exists()) {
            petsDir.mkdirs();
            // Copy default pet definitions from resources
            copyDefaultResource("pets/wolf.yml", new File(petsDir, "wolf.yml"));
            copyDefaultResource("pets/cat.yml", new File(petsDir, "cat.yml"));
            copyDefaultResource("pets/allay.yml", new File(petsDir, "allay.yml"));
        }
    }

    private void copyDefaultResource(String resourcePath, File destination) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                Files.copy(in, destination.toPath());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Varsayılan pet şablonu kopyalanamadı: " + destination.getName(), e);
        }
    }

    private void loadDefinitions() {
        File[] files = petsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml") || name.toLowerCase().endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                
                String id = config.getString("id");
                if (id == null || id.isEmpty()) {
                    id = file.getName().substring(0, file.getName().lastIndexOf('.'));
                }
                id = id.toLowerCase();

                if (definitions.containsKey(id)) {
                    plugin.getLogger().warning("Mükerrer pet ID'si tespit edildi (" + id + ") dosya: " + file.getName() + ", yüklenmedi.");
                    continue;
                }

                String displayName = config.getString("display.name", id);
                List<String> description = config.getStringList("display.description");
                
                String entityTypeStr = config.getString("entity.type", "WOLF");
                EntityType entityType;
                try {
                    entityType = EntityType.valueOf(entityTypeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().severe("Geçersiz entity tipi '" + entityTypeStr + "' dosya: " + file.getName() + ". Default olarak WOLF atandı.");
                    entityType = EntityType.WOLF;
                }

                boolean baby = config.getBoolean("entity.baby", false);
                boolean glowing = config.getBoolean("entity.glowing", false);
                boolean invulnerable = config.getBoolean("entity.invulnerable", true);
                boolean silent = config.getBoolean("entity.silent", false);
                boolean gravity = config.getBoolean("entity.gravity", true);
                
                boolean progressionEnabled = config.getBoolean("progression.enabled", true);
                int maxLevel = config.getInt("progression.maximum-level", 100);
                
                boolean nameplateEnabled = config.getBoolean("nameplate.enabled", true);
                List<String> nameplateFormat = config.getStringList("nameplate.format");
                if (nameplateFormat.isEmpty()) {
                    nameplateFormat = Arrays.asList("<white>{pet_name}", "<gray>Seviye {level}");
                }

                PetDefinition def = new PetDefinition(
                        id,
                        displayName,
                        description,
                        entityType,
                        baby,
                        glowing,
                        invulnerable,
                        silent,
                        gravity,
                        progressionEnabled,
                        maxLevel,
                        nameplateEnabled,
                        nameplateFormat
                );

                definitions.put(id, def);
                plugin.getLogger().info("Pet türü başarıyla yüklendi: " + id + " (" + displayName + ")");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Pet dosyası yüklenirken hata oluştu: " + file.getName(), e);
            }
        }
    }
}
