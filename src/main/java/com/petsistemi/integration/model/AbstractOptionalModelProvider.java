package com.petsistemi.integration.model;

import com.petsistemi.api.model.PetModelProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

abstract class AbstractOptionalModelProvider implements PetModelProvider {
    protected final JavaPlugin plugin;
    protected final ExternalApiAccess api;
    private final String apiClass;

    AbstractOptionalModelProvider(JavaPlugin plugin, ExternalApiAccess api, String apiClass) {
        this.plugin = Objects.requireNonNull(plugin, "plugin null olamaz.");
        this.api = Objects.requireNonNull(api, "api null olamaz.");
        this.apiClass = apiClass;
    }

    @Override
    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled(pluginName()) && api.isPresent(apiClass);
    }

    protected static String modelId(com.petsistemi.domain.PetDefinition definition) {
        String id = definition != null && definition.representationOrEntity() != null
                ? definition.representationOrEntity().modelId() : null;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("representation.model-id zorunludur.");
        }
        return id;
    }

    protected static void configureBase(org.bukkit.entity.Entity entity,
                                        com.petsistemi.domain.PetDefinition definition) {
        var rep = definition.representationOrEntity();
        entity.setInvulnerable(rep.invulnerable());
        entity.setSilent(rep.silent());
        entity.setGravity(rep.gravity());
        entity.setGlowing(rep.glowing());
        entity.setPersistent(false);
        if (entity instanceof org.bukkit.entity.LivingEntity living) {
            living.setCollidable(false);
            living.setCanPickupItems(false);
            living.setRemoveWhenFarAway(false);
        }
    }

    protected void optionalInvoke(Object target, String method, Object... arguments) {
        try {
            api.invoke(target, method, arguments);
        } catch (RuntimeException ignored) {
            // Optional compatibility method absent in another provider version.
        }
    }
}
