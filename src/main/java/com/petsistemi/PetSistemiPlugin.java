package com.petsistemi;

import com.petsistemi.bootstrap.PetPluginBootstrap;
import com.petsistemi.bootstrap.PetPluginContext;
import com.petsistemi.bootstrap.registrar.CommandRegistrar;
import com.petsistemi.bootstrap.registrar.ListenerRegistrar;
import com.petsistemi.bootstrap.registrar.SchedulerRegistrar;
import com.petsistemi.bootstrap.registrar.ServiceRegistrar;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class PetSistemiPlugin extends JavaPlugin {

    private PetPluginContext context;

    @Override
    public void onEnable() {
        try {
            // Bootstrap plugin context
            context = PetPluginBootstrap.bootstrap(this);

            // Register components
            ServiceRegistrar.register(context);
            CommandRegistrar.register(context);
            ListenerRegistrar.register(context);
            SchedulerRegistrar.register(context);

            if (context != null && context.petService() != null) {
                new com.petsistemi.listener.PlayerProfilePrewarmListener(context.petService()).prewarmAllOnlinePlayers();
            }

            getLogger().info("\n" +
                    "  ____  _____ _____   ____ ___ ____ _____ _____ __  __ ___ \n" +
                    " |  _ \\| ____|_   _| / ___|_ _|  ___|_   _| ____|  \\/  |_ _|\n" +
                    " | |_) |  _|   | |   \\___ \\| | | |_    | | |  _| | |\\/| || | \n" +
                    " |  __/| |___  | |    ___) | | |  _|   | | | |___| |  | || | \n" +
                    " |_|   |_____| |_|   |____/___|_|     |_| |_____|_|  |_|___| \n" +
                    "                                                             \n" +
                    "  PetSistemi v" + getDescription().getVersion() + " :: Başarıyla Aktif Edildi!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "PetSistemi başlatılamadı! Devre dışı bırakılıyor...", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("PetSistemi devredışı bırakılıyor...");

        if (context != null) {
            // 1. Cancel registered tasks
            if (context.taskRegistry() != null) {
                context.taskRegistry().cancelAll();
            }

            // 2. Force cleanup runtime entities
            if (context.coordinator() != null) {
                context.coordinator().forceCleanupAll();
            }

            // 3. Unregister Bukkit services
            ServiceRegistrar.unregister(context);

            // 4. Close Database Executor
            if (context.dbExecutor() != null) {
                context.dbExecutor().close();
            }

            // 5. Clear Caches, Open GUI Inventories & Sessions
            for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
                if (p.getOpenInventory() != null && p.getOpenInventory().getTopInventory() != null
                        && p.getOpenInventory().getTopInventory().getHolder() instanceof com.petsistemi.gui.PetMenuHolder) {
                    p.closeInventory();
                }
            }
            if (context.profileCache() != null) {
                context.profileCache().clearAll();
            }
            if (context.sessionManager() != null) {
                context.sessionManager().clearAll();
            }

            // 6. Close database connection
            if (context.connectionProvider() != null) {
                try {
                    context.connectionProvider().close();
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Veritabanı kapatılırken hata oluştu: " + e.getMessage(), e);
                }
            }
        }

        getLogger().info("PetSistemi başarıyla devredışı bırakıldı!");
    }

    public PetPluginContext getContext() {
        return context;
    }
}
