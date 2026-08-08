package com.petsistemi.integration.model;

import com.petsistemi.api.model.PetModelHandle;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional reflective Oraxen item-model adapter backed by an ItemDisplay. */
public final class OraxenModelProvider extends AbstractOptionalModelProvider {
    public static final NamespacedKey KEY = new NamespacedKey("oraxen", "model");
    static final String API_CLASS = "io.th0rgal.oraxen.api.OraxenItems";

    public OraxenModelProvider(JavaPlugin plugin) {
        this(plugin, new ReflectiveApiAccess(plugin.getClass().getClassLoader()));
    }

    OraxenModelProvider(JavaPlugin plugin, ExternalApiAccess api) {
        super(plugin, api, API_CLASS);
    }

    @Override public NamespacedKey key() { return KEY; }
    @Override public String pluginName() { return "Oraxen"; }

    @Override
    public PetModelHandle spawn(PetInstance pet, PetDefinition definition, Player owner) {
        String modelId = modelId(definition);
        Object builder = api.invokeStatic(API_CLASS, "getItemById", modelId);
        if (builder == null) throw new IllegalArgumentException("Oraxen model bulunamadı: " + modelId);
        Object built = api.invoke(builder, "build");
        if (!(built instanceof ItemStack stack)) {
            throw new IllegalStateException("Oraxen ItemBuilder#build geçerli ItemStack döndürmedi.");
        }
        ItemDisplay display = (ItemDisplay) owner.getWorld().spawnEntity(owner.getLocation(), EntityType.ITEM_DISPLAY);
        display.setItemStack(stack);
        configureBase(display, definition);
        return new PetModelHandle(display, builder, modelId);
    }
}
