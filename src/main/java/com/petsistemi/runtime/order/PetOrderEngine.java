package com.petsistemi.runtime.order;

import com.petsistemi.api.order.PetOrderContext;
import com.petsistemi.api.order.PetOrderHandler;
import com.petsistemi.api.order.PetOrderResult;
import com.petsistemi.api.order.PetOrderService;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves the active pet, applies definition gates, and serializes orders per owner. */
public final class PetOrderEngine implements PetOrderService {

    private static final String NAMESPACE = "petsistemi";
    private static final Map<String, PetFollowMode> MODE_ORDERS = Map.of(
            "follow", PetFollowMode.FOLLOW,
            "stay", PetFollowMode.STAY,
            "wander", PetFollowMode.WANDER
    );

    private final ActivePetRegistry activeRegistry;
    private final PetDefinitionRegistry definitionRegistry;
    private final Map<NamespacedKey, PetOrderHandler> handlers = new ConcurrentHashMap<>();
    private final Set<UUID> pendingOwners = ConcurrentHashMap.newKeySet();

    public PetOrderEngine(ActivePetRegistry activeRegistry, PetDefinitionRegistry definitionRegistry) {
        if (activeRegistry == null || definitionRegistry == null) {
            throw new IllegalArgumentException("Order engine registry'leri null olamaz.");
        }
        this.activeRegistry = activeRegistry;
        this.definitionRegistry = definitionRegistry;
    }

    @Override
    public void registerOrder(NamespacedKey key, PetOrderHandler handler) {
        if (key == null || handler == null) throw new IllegalArgumentException("Order key/handler null olamaz.");
        if (handlers.putIfAbsent(key, handler) != null) throw new IllegalArgumentException("Order zaten kayıtlı: " + key);
    }

    @Override public void unregisterOrder(NamespacedKey key) { if (key != null) handlers.remove(key); }
    @Override public Set<NamespacedKey> registeredOrders() { return Set.copyOf(handlers.keySet()); }

    @Override
    public CompletableFuture<PetOrderResult> executeOrder(Player player, NamespacedKey key) {
        if (player == null || key == null) {
            return completedFailure("Oyuncu veya emir anahtarı eksik.");
        }
        PetOrderHandler handler = handlers.get(key);
        if (handler == null) {
            return completedFailure("Bilinmeyen pet emri: " + key);
        }
        ActivePet active = activeRegistry.getByOwner(player.getUniqueId()).orElse(null);
        if (active == null) {
            return completedFailure("Önce petinizi çağırın.");
        }
        PetDefinition definition = definitionRegistry.find(active.getDefinitionId()).orElse(null);
        if (definition == null) {
            return completedFailure("Aktif pet tanımı bulunamadı: " + active.getDefinitionId());
        }
        PetFollowMode requestedMode = modeFor(key);
        if (requestedMode != null && definition.allowedModes() != null
                && !definition.allowedModes().isEmpty() && !definition.allowedModes().contains(requestedMode)) {
            return completedFailure("Bu pet için '" + key.getKey() + "' emri kullanılamaz.");
        }
        UUID ownerId = player.getUniqueId();
        if (!pendingOwners.add(ownerId)) {
            return completedFailure("Önceki pet emriniz hâlâ işleniyor.");
        }

        PetOrderContext context = new PetOrderContext(player, active.getPetId(), definition,
                active.getSpawnedEntity(), active.entities().stream().toList());
        CompletableFuture<PetOrderResult> execution;
        try {
            var stage = handler.execute(context);
            if (stage == null) {
                pendingOwners.remove(ownerId);
                return completedFailure("Pet emri sonuç döndürmedi.");
            }
            execution = stage.toCompletableFuture();
        } catch (Exception exception) {
            pendingOwners.remove(ownerId);
            return completedFailure(message(exception));
        }
        return execution.handle((result, error) -> {
            pendingOwners.remove(ownerId);
            if (error != null) return PetOrderResult.failure(message(error));
            return result != null ? result : PetOrderResult.failure("Pet emri sonuç döndürmedi.");
        });
    }

    public Set<NamespacedKey> availableOrders(Player player) {
        if (player == null) return Set.of();
        ActivePet active = activeRegistry.getByOwner(player.getUniqueId()).orElse(null);
        PetDefinition definition = active != null ? definitionRegistry.find(active.getDefinitionId()).orElse(null) : null;
        if (definition == null || definition.allowedModes() == null || definition.allowedModes().isEmpty()) {
            return registeredOrders();
        }
        return handlers.keySet().stream()
                .filter(key -> modeFor(key) == null || definition.allowedModes().contains(modeFor(key)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static PetFollowMode modeFor(NamespacedKey key) {
        return NAMESPACE.equals(key.getNamespace()) ? MODE_ORDERS.get(key.getKey()) : null;
    }

    private static CompletableFuture<PetOrderResult> completedFailure(String message) {
        return CompletableFuture.completedFuture(PetOrderResult.failure(message));
    }

    private static String message(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
