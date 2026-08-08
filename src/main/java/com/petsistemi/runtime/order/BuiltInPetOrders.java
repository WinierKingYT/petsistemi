package com.petsistemi.runtime.order;

import com.petsistemi.api.order.PetOrderResult;
import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.concurrent.CompletableFuture;

/** Built-in persistent modes and the one-shot come order. */
public final class BuiltInPetOrders {

    public static final NamespacedKey FOLLOW = key("follow");
    public static final NamespacedKey STAY = key("stay");
    public static final NamespacedKey WANDER = key("wander");
    public static final NamespacedKey COME = key("come");

    private BuiltInPetOrders() {}

    public static void register(PetOrderEngine engine, ActivePetRegistry activeRegistry,
                                PetRuntimeOperationService operationService) {
        registerMode(engine, activeRegistry, operationService, FOLLOW, PetFollowMode.FOLLOW);
        registerMode(engine, activeRegistry, operationService, STAY, PetFollowMode.STAY);
        registerMode(engine, activeRegistry, operationService, WANDER, PetFollowMode.WANDER);
        engine.registerOrder(COME, context -> {
            Location target = comeTarget(context.player().getLocation());
            boolean moved = false;
            for (Entity entity : context.petEntities()) {
                if (entity != null && entity.isValid() && !entity.isDead()) {
                    moved |= entity.teleport(target);
                }
            }
            return CompletableFuture.completedFuture(moved
                    ? PetOrderResult.success("Petiniz yanınıza geldi.")
                    : PetOrderResult.failure("Petiniz yanınıza getirilemedi."));
        });
    }

    private static void registerMode(PetOrderEngine engine, ActivePetRegistry activeRegistry,
                                     PetRuntimeOperationService operationService,
                                     NamespacedKey key, PetFollowMode mode) {
        engine.registerOrder(key, context -> {
            ActivePet active = activeRegistry.getByOwner(context.player().getUniqueId()).orElse(null);
            if (active == null || !active.getPetId().equals(context.petId())) {
                return CompletableFuture.completedFuture(PetOrderResult.failure("Aktif pet değişti; emri tekrar deneyin."));
            }
            active.setFollowMode(mode);
            if (active.getSpawnedEntity() instanceof Mob mob) mob.getPathfinder().stopPathfinding();
            if (operationService == null) {
                return CompletableFuture.completedFuture(PetOrderResult.success(
                        "Petinizin takip modu '" + key.getKey() + "' olarak ayarlandı."));
            }
            return operationService.setFollowModeAsync(context.player(), mode).thenApply(persisted -> persisted
                    ? PetOrderResult.success("Petinizin takip modu '" + key.getKey() + "' olarak ayarlandı ve kaydedildi.")
                    : PetOrderResult.success("<yellow>Petinizin takip modu '" + key.getKey()
                    + "' olarak ayarlandı, ancak kaydedilemedi (yeniden girişte sıfırlanır).</yellow>"));
        });
    }

    private static Location comeTarget(Location ownerLocation) {
        Location target = ownerLocation.clone();
        Vector direction = ownerLocation.getDirection().setY(0);
        if (direction.lengthSquared() < 0.01D) direction = new Vector(1, 0, 0);
        return target.add(direction.normalize().multiply(-1.5D));
    }

    private static NamespacedKey key(String value) { return new NamespacedKey("petsistemi", value); }
}
