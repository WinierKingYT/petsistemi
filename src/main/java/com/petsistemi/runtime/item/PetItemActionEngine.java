package com.petsistemi.runtime.item;

import com.petsistemi.api.item.PetItemActionContext;
import com.petsistemi.api.item.PetItemActionHandler;
import com.petsistemi.api.item.PetItemActionResult;
import com.petsistemi.api.item.PetItemActionService;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.item.PetItemActionDefinition;
import com.petsistemi.runtime.ActivePet;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Matches an item, guards one execution, and commits cooldown only after success. */
public final class PetItemActionEngine implements PetItemActionService {

    private final Map<NamespacedKey, PetItemActionHandler> handlers = new ConcurrentHashMap<>();
    private final Map<UseKey, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UseKey> pending = ConcurrentHashMap.newKeySet();

    @Override
    public void registerAction(NamespacedKey key, PetItemActionHandler handler) {
        if (key == null || handler == null) throw new IllegalArgumentException("Item action key/handler null olamaz.");
        if (handlers.putIfAbsent(key, handler) != null) throw new IllegalArgumentException("Item action zaten kayıtlı: " + key);
    }

    @Override public void unregisterAction(NamespacedKey key) { if (key != null) handlers.remove(key); }
    @Override public Set<NamespacedKey> registeredActions() { return Set.copyOf(handlers.keySet()); }

    public CompletableFuture<PetItemActionOutcome> use(Player player, ActivePet active,
                                                       PetDefinition definition, ItemStack item) {
        PetItemActionDefinition matched = match(definition, item);
        if (matched == null) return CompletableFuture.completedFuture(outcome(PetItemActionStatus.NOT_MATCHED, null, null, 0));
        if (player == null || active == null) return CompletableFuture.completedFuture(outcome(PetItemActionStatus.FAILED, "Pet kullanılamıyor.", matched, 0));
        if (item.getAmount() < matched.consumeAmount()) {
            return CompletableFuture.completedFuture(outcome(PetItemActionStatus.INSUFFICIENT_ITEMS,
                    "Bu işlem için yeterli item yok.", matched, 0));
        }
        if (matched.permission() != null && !player.hasPermission(matched.permission())) {
            return CompletableFuture.completedFuture(outcome(PetItemActionStatus.NO_PERMISSION, "Bu item aksiyonu için yetkiniz yok.", matched, 0));
        }
        int level = active.getPetInstance() != null ? active.getPetInstance().level() : 1;
        if (level < matched.minimumLevel() || (matched.maximumLevel() > 0 && level > matched.maximumLevel())) {
            return CompletableFuture.completedFuture(outcome(PetItemActionStatus.LEVEL_BLOCKED, "Pet seviyesi bu item için uygun değil.", matched, 0));
        }
        PetItemActionHandler handler = handlers.get(matched.action());
        if (handler == null) {
            return CompletableFuture.completedFuture(outcome(PetItemActionStatus.ACTION_UNAVAILABLE,
                    "Item aksiyonu kayıtlı değil: " + matched.action(), matched, 0));
        }

        UseKey useKey = new UseKey(player.getUniqueId(), active.getPetId(), matched.id());
        long now = System.currentTimeMillis();
        long remaining = cooldowns.getOrDefault(useKey, 0L) - now;
        if (remaining > 0) {
            return CompletableFuture.completedFuture(outcome(PetItemActionStatus.COOLDOWN,
                    "Bu item henüz tekrar kullanılamaz.", matched, (remaining + 999L) / 1000L));
        }
        if (!pending.add(useKey)) {
            return CompletableFuture.completedFuture(outcome(PetItemActionStatus.PENDING, "Item işlemi zaten sürüyor.", matched, 0));
        }

        PetItemActionContext context = new PetItemActionContext(player, active.getPetId(), definition,
                active.getSpawnedEntity(), item.clone());
        CompletableFuture<PetItemActionResult> execution;
        try {
            execution = handler.execute(context, matched.parameters()).toCompletableFuture();
        } catch (Exception e) {
            pending.remove(useKey);
            return CompletableFuture.completedFuture(outcome(PetItemActionStatus.FAILED, e.getMessage(), matched, 0));
        }
        return execution.handle((result, error) -> {
            pending.remove(useKey);
            if (error != null || result == null || !result.success()) {
                String message = error != null ? error.getMessage() : (result != null ? result.message() : "Item işlemi başarısız.");
                return outcome(PetItemActionStatus.FAILED, message, matched, 0);
            }
            if (matched.cooldownSeconds() > 0) {
                cooldowns.put(useKey, System.currentTimeMillis() + matched.cooldownSeconds() * 1000L);
            }
            return outcome(PetItemActionStatus.SUCCESS, result.message(), matched, 0);
        });
    }

    public void cleanup(UUID playerId) {
        if (playerId == null) return;
        cooldowns.keySet().removeIf(key -> key.playerId().equals(playerId));
        pending.removeIf(key -> key.playerId().equals(playerId));
    }

    public boolean matches(PetDefinition definition, ItemStack item) {
        return match(definition, item) != null;
    }

    private static PetItemActionDefinition match(PetDefinition definition, ItemStack item) {
        List<PetItemActionDefinition> actions = definition != null ? definition.itemActions() : null;
        if (actions == null || item == null || item.getType().isAir()) return null;
        for (PetItemActionDefinition action : actions) {
            Material material = Material.matchMaterial(action.material());
            if (material == null || material != item.getType()) continue;
            if (action.customModelData() != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != action.customModelData()) continue;
            }
            return action;
        }
        return null;
    }

    private static PetItemActionOutcome outcome(PetItemActionStatus status, String message,
                                                PetItemActionDefinition definition, long remaining) {
        return new PetItemActionOutcome(status, message, definition, remaining);
    }

    private record UseKey(UUID playerId, UUID petId, String actionId) {}
}
