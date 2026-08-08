package com.petsistemi.integration.model;

import com.petsistemi.api.model.PetModelHandle;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional reflective adapter for ItemsAdder custom entities. */
public final class ItemsAdderModelProvider extends AbstractOptionalModelProvider {
    public static final NamespacedKey KEY = new NamespacedKey("itemsadder", "model");
    static final String API_CLASS = "dev.lone.itemsadder.api.CustomEntity";

    public ItemsAdderModelProvider(JavaPlugin plugin) {
        this(plugin, new ReflectiveApiAccess(plugin.getClass().getClassLoader()));
    }

    ItemsAdderModelProvider(JavaPlugin plugin, ExternalApiAccess api) {
        super(plugin, api, API_CLASS);
    }

    @Override public NamespacedKey key() { return KEY; }
    @Override public String pluginName() { return "ItemsAdder"; }

    @Override
    public PetModelHandle spawn(PetInstance pet, PetDefinition definition, Player owner) {
        String modelId = modelId(definition);
        Object custom;
        try {
            custom = api.invokeStatic(API_CLASS, "spawn", modelId, owner.getLocation());
        } catch (RuntimeException oldSignatureMissing) {
            custom = api.invokeStatic(API_CLASS, "spawn", modelId, owner.getLocation(), false);
        }
        if (custom == null) throw new IllegalArgumentException("ItemsAdder model bulunamadı: " + modelId);
        Object rawEntity = api.invoke(custom, "getEntity");
        if (!(rawEntity instanceof Entity entity)) {
            throw new IllegalStateException("ItemsAdder CustomEntity#getEntity geçerli entity döndürmedi.");
        }
        configureBase(entity, definition);
        return new PetModelHandle(entity, custom, modelId);
    }

    @Override
    public void applyAnimation(PetModelHandle handle, PetAnimationTransition transition) {
        if (handle == null || handle.providerHandle() == null || transition == null || transition.clip() == null) return;
        if (transition.previousClip() != null) optionalInvoke(handle.providerHandle(), "stopAnimation");
        api.invoke(handle.providerHandle(), "playAnimation", transition.clip().key().getKey());
    }

    @Override
    public void remove(PetModelHandle handle) {
        if (handle != null && handle.providerHandle() != null) optionalInvoke(handle.providerHandle(), "destroy");
        if (handle != null && handle.entity().isValid()) handle.entity().remove();
    }
}
