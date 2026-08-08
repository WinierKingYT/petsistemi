package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.RuntimeKeyResolver;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.domain.visual.PetSpriteAnimationDefinition;
import com.petsistemi.domain.visual.PetSpriteBillboard;
import com.petsistemi.domain.visual.PetSpriteDefinition;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A resource-pack sprite rendered by ItemDisplay. Animation state changes select
 * frame sequences; runtime ticks only replace custom-model-data, never respawn the entity.
 */
public final class SpritePetRepresentation implements PetRepresentationController {

    private static final Material FALLBACK_MATERIAL = Material.PAPER;

    private final ItemDisplayPetRepresentation itemDisplay;
    private final Map<UUID, SpriteSession> sessions = new ConcurrentHashMap<>();

    public SpritePetRepresentation(ItemDisplayPetRepresentation itemDisplay) {
        this.itemDisplay = Objects.requireNonNull(itemDisplay, "item display representation null olamaz.");
    }

    @Override
    public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
        PetSpriteDefinition sprite = requireSprite(definition);
        PetSpriteAnimationDefinition animation = sprite.animation(PetAnimationState.IDLE);
        int firstFrame = animation.frames().get(0);
        Entity entity = itemDisplay.spawn(pet, itemDefinition(definition, firstFrame), owner);
        if (!(entity instanceof ItemDisplay display)) {
            itemDisplay.remove(entity);
            throw new IllegalStateException("SPRITE ItemDisplay entity üretmedi.");
        }
        display.setBillboard(toBukkit(sprite.billboard()));
        sessions.put(display.getUniqueId(), new SpriteSession(sprite, firstFrame));
        return display;
    }

    @Override
    public void tickVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition, Player owner) {
        if (!(primaryEntity instanceof ItemDisplay display)) return;
        SpriteSession session = sessions.get(display.getUniqueId());
        if (session == null) return;
        session.elapsedTicks++;
        applyCurrentFrame(display, session);
    }

    @Override
    public void applyAnimation(Entity primaryEntity, PetInstance pet, PetDefinition definition,
                               PetAnimationTransition transition) {
        if (!(primaryEntity instanceof ItemDisplay display) || transition == null) return;
        SpriteSession session = sessions.get(display.getUniqueId());
        if (session == null) return;
        itemDisplay.applyAnimation(display, pet, itemDefinition(definition, currentFrame(session)), transition);
        session.state = transition.state() != null ? transition.state() : PetAnimationState.IDLE;
        session.elapsedTicks = 0;
        session.appliedFrame = Integer.MIN_VALUE;
        applyCurrentFrame(display, session);
    }

    @Override
    public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        SpriteSession session = session(primaryEntity);
        if (session != null) {
            itemDisplay.applyRestState(primaryEntity, pet,
                    itemDefinition(definition, currentFrame(session)), resting);
        }
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        if (!(primaryEntity instanceof ItemDisplay display)) return;
        PetSpriteDefinition sprite = requireSprite(definition);
        SpriteSession session = sessions.computeIfAbsent(display.getUniqueId(),
                ignored -> new SpriteSession(sprite, Integer.MIN_VALUE));
        session.sprite = sprite;
        session.elapsedTicks = 0;
        session.appliedFrame = Integer.MIN_VALUE;
        itemDisplay.updateVisual(display, pet, itemDefinition(definition, currentFrame(session)));
        display.setBillboard(toBukkit(sprite.billboard()));
        applyCurrentFrame(display, session);
    }

    @Override
    public void remove(Entity primaryEntity) {
        if (primaryEntity != null) sessions.remove(primaryEntity.getUniqueId());
        itemDisplay.remove(primaryEntity);
    }

    @Override
    public boolean isValid(Entity primaryEntity) {
        return primaryEntity != null && sessions.containsKey(primaryEntity.getUniqueId())
                && itemDisplay.isValid(primaryEntity);
    }

    private SpriteSession session(Entity entity) {
        return entity == null ? null : sessions.get(entity.getUniqueId());
    }

    private static PetSpriteDefinition requireSprite(PetDefinition definition) {
        PetSpriteDefinition sprite = definition.representationOrEntity().sprite();
        if (sprite == null) throw new IllegalArgumentException("SPRITE tanımı eksik.");
        return sprite;
    }

    private static int currentFrame(SpriteSession session) {
        PetSpriteAnimationDefinition animation = session.sprite.animation(session.state);
        int index = session.elapsedTicks / animation.frameTicks();
        if (animation.loop()) index %= animation.frames().size();
        else index = Math.min(index, animation.frames().size() - 1);
        return animation.frames().get(index);
    }

    private static void applyCurrentFrame(ItemDisplay display, SpriteSession session) {
        int frame = currentFrame(session);
        if (frame == session.appliedFrame) return;
        Material material = Material.matchMaterial(session.sprite.material());
        ItemStack stack = new ItemStack(material != null ? material : FALLBACK_MATERIAL);
        stack.editMeta(meta -> meta.setCustomModelData(frame));
        display.setItemStack(stack);
        session.appliedFrame = frame;
    }

    private static Display.Billboard toBukkit(PetSpriteBillboard billboard) {
        return switch (billboard) {
            case CENTER -> Display.Billboard.CENTER;
            case VERTICAL -> Display.Billboard.VERTICAL;
            case HORIZONTAL -> Display.Billboard.HORIZONTAL;
            case FIXED -> Display.Billboard.FIXED;
        };
    }

    private static PetDefinition itemDefinition(PetDefinition definition, int frame) {
        PetRepresentationDefinition source = definition.representationOrEntity();
        PetRepresentationDefinition item = new PetRepresentationDefinition(
                RuntimeRepresentationType.ITEM_DISPLAY,
                RuntimeKeyResolver.representationKey(RuntimeRepresentationType.ITEM_DISPLAY),
                source.entityType(), source.baby(), source.glowing(), source.invulnerable(), source.silent(),
                source.gravity(), source.sprite().material(), frame, source.scale(), source.particleType(),
                source.particleCount(), source.particleOffset(), source.particleSpeed(), source.childCount(),
                source.childMaterial(), source.modelId(), null, null, null);
        return definition.toBuilder().representation(item).build();
    }

    private static final class SpriteSession {
        private PetSpriteDefinition sprite;
        private PetAnimationState state = PetAnimationState.IDLE;
        private int elapsedTicks;
        private int appliedFrame = Integer.MIN_VALUE;

        private SpriteSession(PetSpriteDefinition sprite, int appliedFrame) {
            this.sprite = sprite;
            this.appliedFrame = appliedFrame;
        }
    }
}
