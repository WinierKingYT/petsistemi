package com.petsistemi.integration.model;

import com.petsistemi.api.model.PetModelHandle;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional reflective adapter for Model Engine 4. */
public final class ModelEngineModelProvider extends AbstractOptionalModelProvider {
    public static final NamespacedKey KEY = new NamespacedKey("modelengine", "model");
    static final String API_CLASS = "com.ticxo.modelengine.api.ModelEngineAPI";

    public ModelEngineModelProvider(JavaPlugin plugin) {
        this(plugin, new ReflectiveApiAccess(plugin.getClass().getClassLoader()));
    }

    ModelEngineModelProvider(JavaPlugin plugin, ExternalApiAccess api) {
        super(plugin, api, API_CLASS);
    }

    @Override public NamespacedKey key() { return KEY; }
    @Override public String pluginName() { return "ModelEngine"; }

    @Override
    public PetModelHandle spawn(PetInstance pet, PetDefinition definition, Player owner) {
        String modelId = modelId(definition);
        Entity entity = spawnBase(definition, owner);
        try {
            Object modeled = api.invokeStatic(API_CLASS, "createModeledEntity", entity);
            Object active = api.invokeStatic(API_CLASS, "createActiveModel", modelId);
            api.invoke(modeled, "addModel", active, true);
            optionalInvoke(modeled, "setBaseEntityVisible", false);
            return new PetModelHandle(entity, new Handle(modeled, active), modelId);
        } catch (RuntimeException e) {
            if (entity.isValid()) entity.remove();
            throw e;
        }
    }

    @Override
    public void applyAnimation(PetModelHandle handle, PetAnimationTransition transition) {
        if (!(handle.providerHandle() instanceof Handle model) || transition == null || transition.clip() == null) return;
        Object animationHandler = api.invoke(model.activeModel(), "getAnimationHandler");
        if (transition.previousClip() != null) {
            optionalInvoke(animationHandler, "stopAnimation", transition.previousClip().key().getKey());
            optionalInvoke(animationHandler, "stopAnimation", transition.previousClip().priority(),
                    transition.previousClip().key().getKey());
        }
        String clip = transition.clip().key().getKey();
        double blendIn = transition.clip().blendInTicks() / 20.0;
        double blendOut = transition.clip().blendOutTicks() / 20.0;
        try {
            api.invoke(animationHandler, "playAnimation", transition.clip().priority(), clip,
                    blendIn, blendOut, 1.0, transition.clip().loop());
        } catch (RuntimeException modernMissing) {
            api.invoke(animationHandler, "playAnimation", clip, blendIn, blendOut, 1.0,
                    transition.clip().loop());
        }
    }

    @Override
    public void remove(PetModelHandle handle) {
        if (handle != null && handle.providerHandle() instanceof Handle model) {
            optionalInvoke(model.modeledEntity(), "removeModel", handle.modelId());
            optionalInvoke(model.activeModel(), "destroy");
        }
        if (handle != null && handle.entity().isValid()) handle.entity().remove();
    }

    private static Entity spawnBase(PetDefinition definition, Player owner) {
        String raw = definition.representationOrEntity().entityType();
        EntityType type;
        try {
            type = EntityType.valueOf(raw != null ? raw.toUpperCase(java.util.Locale.ROOT) : "ARMOR_STAND");
        } catch (IllegalArgumentException ignored) {
            type = EntityType.ARMOR_STAND;
        }
        Entity entity = owner.getWorld().spawnEntity(owner.getLocation(), type);
        configureBase(entity, definition);
        return entity;
    }

    private record Handle(Object modeledEntity, Object activeModel) {}
}
