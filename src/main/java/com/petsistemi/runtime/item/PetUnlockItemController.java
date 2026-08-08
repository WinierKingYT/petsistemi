package com.petsistemi.runtime.item;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.item.PetItemActionResult;
import com.petsistemi.api.item.PetUnlockItemService;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PetUnlockItemController implements PetUnlockItemService {

    private static final int SCHEMA_VERSION = 1;
    private final NamespacedKey definitionKey;
    private final NamespacedKey versionKey;
    private final PetDefinitionRegistry definitions;
    private final PetService petService;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public PetUnlockItemController(JavaPlugin plugin, PetDefinitionRegistry definitions, PetService petService) {
        this.definitionKey = new NamespacedKey(plugin, "unlock_definition");
        this.versionKey = new NamespacedKey(plugin, "unlock_version");
        this.definitions = definitions;
        this.petService = petService;
    }

    @Override
    public ItemStack create(String definitionId, int amount, Material material) {
        String id = normalize(definitionId);
        PetDefinition definition = definitions.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Pet tanımı bulunamadı: " + id));
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("Item miktarı 1-64 arasında olmalıdır.");
        Material type = material != null ? material : Material.NAME_TAG;
        if (type.isAir()) throw new IllegalArgumentException("Unlock item materyali hava olamaz.");

        ItemStack item = new ItemStack(type, amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(definitionKey, PersistentDataType.STRING, definition.id());
        meta.getPersistentDataContainer().set(versionKey, PersistentDataType.INTEGER, SCHEMA_VERSION);
        meta.displayName(Component.text(definition.displayName() + " Kilidi", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Sağ tıklayarak bu peti koleksiyonuna ekle.", NamedTextColor.GRAY),
                Component.text("Pet: " + definition.id(), NamedTextColor.DARK_GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean matches(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        Integer version = meta.getPersistentDataContainer().get(versionKey, PersistentDataType.INTEGER);
        String definitionId = meta.getPersistentDataContainer().get(definitionKey, PersistentDataType.STRING);
        return version != null && version == SCHEMA_VERSION && definitionId != null && !definitionId.isBlank();
    }

    @Override
    public CompletableFuture<PetItemActionResult> redeem(Player player, ItemStack item) {
        if (player == null || !matches(item)) {
            return CompletableFuture.completedFuture(PetItemActionResult.failure("Geçerli bir pet unlock itemi değil."));
        }
        String definitionId = normalize(item.getItemMeta().getPersistentDataContainer()
                .get(definitionKey, PersistentDataType.STRING));
        if (definitions.find(definitionId).isEmpty()) {
            return CompletableFuture.completedFuture(PetItemActionResult.failure("Unlock itemindeki pet tanımı artık mevcut değil: " + definitionId));
        }
        UUID playerId = player.getUniqueId();
        if (!pending.add(playerId)) {
            return CompletableFuture.completedFuture(PetItemActionResult.failure("Zaten devam eden bir unlock işleminiz var."));
        }

        CompletableFuture<com.petsistemi.api.result.PetGiveResult> give;
        try {
            give = petService instanceof AsyncPetService async
                    ? async.givePetAsync(playerId, definitionId)
                    : CompletableFuture.completedFuture(petService.givePet(playerId, definitionId));
            if (give == null) throw new IllegalStateException("PetService null future döndürdü.");
        } catch (Exception error) {
            pending.remove(playerId);
            return CompletableFuture.completedFuture(PetItemActionResult.failure("Pet kilidi açılamadı: " + error.getMessage()));
        }
        return give.handle((result, error) -> {
            pending.remove(playerId);
            if (error != null) return PetItemActionResult.failure("Pet kilidi açılamadı: " + error.getMessage());
            return result != null && result.success()
                    ? PetItemActionResult.success("Yeni pet açıldı: " + definitionId)
                    : PetItemActionResult.failure(result != null ? result.message() : "Pet kilidi açılamadı.");
        });
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
