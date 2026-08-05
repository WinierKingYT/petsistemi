package com.petsistemi.config;

import com.petsistemi.bootstrap.PetPluginContext;
import com.petsistemi.bootstrap.registrar.SchedulerRegistrar;
import com.petsistemi.definition.AtomicPetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.message.MessageBundle;
import com.petsistemi.message.MessageService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public class RuntimeReloadService {

    public record ReloadResult(boolean success, String message, boolean rolledBack) {}

    public static ReloadResult performReload(PetPluginContext context, JavaPlugin plugin, MessageService messageService, AtomicPetDefinitionRegistry definitionRegistry) {
        Objects.requireNonNull(plugin, "plugin null olamaz.");

        // 1. Load config file into candidate YamlConfiguration
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration candidateYaml = new YamlConfiguration();
        if (configFile.exists()) {
            try {
                candidateYaml.load(configFile);
            } catch (Exception e) {
                return new ReloadResult(false, "Diskteki config.yml okunamadı veya biçimi bozuk: " + e.getMessage(), false);
            }
        }

        // 2. Validate candidate PluginConfiguration
        PluginConfiguration candidateConfig;
        try {
            candidateConfig = PluginConfigurationLoader.load(candidateYaml);
        } catch (Exception e) {
            return new ReloadResult(false, "Konfigürasyon doğrulama hatası: " + e.getMessage(), false);
        }

        // 3. Load candidate MessageBundle using candidate locale
        MessageBundle candidateBundle;
        try {
            candidateBundle = messageService != null ? messageService.loadCandidate(candidateConfig.locale()) : null;
        } catch (Exception e) {
            return new ReloadResult(false, "Dil paketi (" + candidateConfig.locale() + ") yüklenirken hata: " + e.getMessage(), false);
        }

        // 4. Load candidate PetDefinition map
        Map<String, PetDefinition> candidateDefinitions;
        try {
            candidateDefinitions = definitionRegistry != null ? definitionRegistry.loadCandidateSnapshot() : null;
        } catch (Exception e) {
            return new ReloadResult(false, "Pet tanımları yüklenirken hata: " + e.getMessage(), false);
        }

        // 5. Save current state for potential rollback
        RuntimeConfigurationSnapshot oldSnapshot = (context != null && context.configSnapshot() != null) ? context.configSnapshot().get() : null;
        MessageBundle oldBundle = messageService != null ? messageService.currentBundle() : null;
        Map<String, PetDefinition> oldDefinitions = definitionRegistry != null ? definitionRegistry.currentSnapshot() : null;
        String oldBukkitConfigYaml = plugin.getConfig() != null ? plugin.getConfig().saveToString() : null;

        // 6-11. Publish candidate state and re-evaluate tasks
        try {
            if (messageService != null && candidateBundle != null) {
                messageService.publish(candidateBundle);
            }

            if (definitionRegistry != null && candidateDefinitions != null) {
                definitionRegistry.publishSnapshot(candidateDefinitions);
            }

            plugin.reloadConfig();

            RuntimeConfigurationSnapshot candidateSnapshot = new RuntimeConfigurationSnapshot(
                    candidateConfig,
                    messageService,
                    definitionRegistry,
                    System.currentTimeMillis()
            );

            if (context != null && context.configSnapshot() != null) {
                context.configSnapshot().set(candidateSnapshot);
            }

            if (context != null) {
                SchedulerRegistrar.reevaluateReloadableTasks(context);
            }

            return new ReloadResult(true, "Konfigürasyon ve pet tanımları atomik olarak başarıyla yenilendi!", false);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Publish veya task yenileme sırasında hata oluştu, rollback başlatılıyor...", e);

            boolean rollbackSuccess = true;
            try {
                if (messageService != null && oldBundle != null) {
                    messageService.publish(oldBundle);
                }
                if (definitionRegistry != null && oldDefinitions != null) {
                    definitionRegistry.publishSnapshot(oldDefinitions);
                }
                if (context != null && context.configSnapshot() != null && oldSnapshot != null) {
                    context.configSnapshot().set(oldSnapshot);
                }
                if (oldBukkitConfigYaml != null && plugin.getConfig() != null) {
                    plugin.getConfig().loadFromString(oldBukkitConfigYaml);
                }
                if (context != null) {
                    SchedulerRegistrar.reevaluateReloadableTasks(context);
                }
            } catch (Exception rollbackEx) {
                rollbackSuccess = false;
                plugin.getLogger().log(Level.SEVERE, "Geri yükleme (rollback) sırasında ikincil hata oluştu!", rollbackEx);
            }

            return new ReloadResult(false, "Publish sırasında hata oluştu: " + e.getMessage(), rollbackSuccess);
        }
    }
}
