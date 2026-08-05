package com.petsistemi.command;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.persistence.PlayerPetProfile;
import com.petsistemi.persistence.PlayerPetProfileCache;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PetCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PetRuntimeOperationService operationService;
    private final PetService petService;
    private final PetDefinitionRegistry definitionRegistry;
    private final PlayerPetProfileCache profileCache;

    public PetCommand(JavaPlugin plugin, PetRuntimeOperationService operationService, PetService petService, PetDefinitionRegistry definitionRegistry, PlayerPetProfileCache profileCache) {
        this.plugin = plugin;
        this.operationService = operationService;
        this.petService = petService;
        this.definitionRegistry = definitionRegistry;
        this.profileCache = profileCache;
    }

    public PetCommand(JavaPlugin plugin, PetRuntimeOperationService operationService, PetService petService, PetDefinitionRegistry definitionRegistry) {
        this(plugin, operationService, petService, definitionRegistry, null);
    }

    public PetCommand(JavaPlugin plugin, PetService petService, PetDefinitionRegistry definitionRegistry) {
        this(plugin, null, petService, definitionRegistry, null);
    }

    public PetCommand(JavaPlugin plugin, PetService petService) {
        this(plugin, null, petService, null, null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Bu komut sadece oyuncular tarafından kullanılabilir.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("companionpets.use")) {
            player.sendMessage(Component.text("Bu komutu kullanmak için yetkiniz yok.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            com.petsistemi.gui.PetListMenu.open(player, petService, 0, plugin, definitionRegistry);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> handleList(player);
            case "summon" -> handleSummon(player, args);
            case "dismiss" -> handleDismiss(player);
            case "info" -> handleInfo(player, args);
            case "rename" -> handleRename(player, args);
            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== Minecraft Pet Sistemi ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/pet list - Sahip olduğunuz petleri listeler.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/pet summon <pet_id> - Belirtilen peti çağırır.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/pet dismiss - Aktif petinizi kaldırır.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/pet info [pet_id] - Pet detaylarını gösterir.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/pet rename <pet_id> <isim> - Petinizi yeniden adlandırır.", NamedTextColor.YELLOW));
    }

    private void handleList(Player player) {
        CompletableFuture<List<PetSnapshot>> petsFuture = petService instanceof AsyncPetService async ? async.getOwnedPetsAsync(player.getUniqueId()).thenApply(ArrayList::new) : CompletableFuture.completedFuture(new ArrayList<>(petService.getOwnedPets(player.getUniqueId())));

        petsFuture.thenAccept(pets -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (pets.isEmpty()) {
                player.sendMessage(Component.text("Herhangi bir pete sahip değilsiniz. Yetkili kişilerden pet talep edebilirsiniz.", NamedTextColor.GRAY));
                return;
            }

            player.sendMessage(Component.text("=== Evcil Hayvanlarınız ===", NamedTextColor.GOLD));
            int i = 1;
            for (PetSnapshot pet : pets) {
                String shortId = pet.petId().toString().substring(0, 6);
                String name = pet.customName() != null ? pet.customName() : pet.definitionId();
                player.sendMessage(Component.text(i + ". " + name + " (ID: " + shortId + ") - Seviye " + pet.level(), NamedTextColor.YELLOW));
                i++;
            }
        }));
    }

    private void handleSummon(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Kullanım: /pet summon <pet_id>", NamedTextColor.RED));
            return;
        }

        String shortId = args[1];
        resolveOwnedPetByShortId(player, shortId).thenAccept(match -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (match.status == SearchStatus.NOT_FOUND) {
                player.sendMessage(Component.text("Belirtilen ID ile eşleşen bir pet bulunamadı.", NamedTextColor.RED));
                return;
            } else if (match.status == SearchStatus.AMBIGUOUS) {
                player.sendMessage(Component.text("Birden fazla pet bu kimlik ön ekiyle eşleşiyor (belirsiz ID). Lütfen daha fazla karakter girin.", NamedTextColor.RED));
                return;
            }

            CompletableFuture<?> summonFuture = operationService != null ? operationService.summonAsync(player, match.pet.petId()) : CompletableFuture.completedFuture(petService.summon(player, match.pet.petId()));
            summonFuture.thenAccept(res -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                boolean success = res instanceof com.petsistemi.api.result.PetSummonResult r && r.success();
                String msg = res instanceof com.petsistemi.api.result.PetSummonResult r ? r.message() : "Çağırma başarısız.";
                if (success) {
                    player.sendMessage(Component.text("Pet başarıyla çağırıldı!", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text(msg, NamedTextColor.RED));
                }
            }));
        }));
    }

    private void handleDismiss(Player player) {
        CompletableFuture<?> dismissFuture = operationService != null ? operationService.dismissAsync(player) : CompletableFuture.completedFuture(petService.dismiss(player));

        dismissFuture.thenAccept(res -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            boolean success = res instanceof com.petsistemi.api.result.PetDismissResult r && r.success();
            String msg = res instanceof com.petsistemi.api.result.PetDismissResult r ? r.message() : "Gönderme başarısız.";
            if (success) {
                player.sendMessage(Component.text("Petiniz kaldırıldı.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(msg, NamedTextColor.RED));
            }
        }));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length >= 2) {
            String shortId = args[1];
            resolveOwnedPetByShortId(player, shortId).thenAccept(match -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (match.status == SearchStatus.NOT_FOUND) {
                    player.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
                    return;
                } else if (match.status == SearchStatus.AMBIGUOUS) {
                    player.sendMessage(Component.text("Birden fazla pet bu kimlik ön ekiyle eşleşiyor.", NamedTextColor.RED));
                    return;
                }
                displayInfo(player, match.pet);
            }));
        } else {
            CompletableFuture<Optional<PetSnapshot>> selectedFuture = petService instanceof AsyncPetService async ? async.getSelectedPetAsync(player.getUniqueId()) : CompletableFuture.completedFuture(petService.getSelectedPet(player.getUniqueId()));

            selectedFuture.thenAccept(opt -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("Detaylarını görecek aktif veya belirtilmiş bir pet bulunamadı.", NamedTextColor.RED));
                    return;
                }
                displayInfo(player, opt.get());
            }));
        }
    }

    private void displayInfo(Player player, PetSnapshot pet) {
        String customName = pet.customName() != null ? pet.customName() : "Yok";

        player.sendMessage(Component.text("=== Pet Bilgisi ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Tür ID: " + pet.definitionId(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Özel İsim: " + customName, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Seviye: " + pet.level(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Deneyim: " + pet.experience(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Durum: " + pet.availabilityState().name(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Pet UUID: " + pet.petId(), NamedTextColor.YELLOW));
    }

    private void handleRename(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Kullanım: /pet rename <pet_id> <yeni_isim>", NamedTextColor.RED));
            return;
        }

        String shortId = args[1];
        StringJoiner sj = new StringJoiner(" ");
        for (int i = 2; i < args.length; i++) {
            sj.add(args[i]);
        }
        String newName = sj.toString();

        resolveOwnedPetByShortId(player, shortId).thenAccept(match -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (match.status == SearchStatus.NOT_FOUND) {
                player.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
                return;
            } else if (match.status == SearchStatus.AMBIGUOUS) {
                player.sendMessage(Component.text("Birden fazla pet bu kimlik ön ekiyle eşleşiyor.", NamedTextColor.RED));
                return;
            }

            CompletableFuture<?> renameFuture = petService instanceof AsyncPetService async ? async.renameAsync(player.getUniqueId(), match.pet.petId(), newName) : CompletableFuture.completedFuture(petService.rename(player.getUniqueId(), match.pet.petId(), newName));

            renameFuture.thenAccept(res -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                boolean success = res instanceof com.petsistemi.api.result.PetRenameResult r && r.success();
                String msg = res instanceof com.petsistemi.api.result.PetRenameResult r ? r.message() : "İsim değiştirme başarısız.";
                if (success) {
                    player.sendMessage(Component.text("Petinizin ismi '" + newName + "' olarak güncellendi!", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text(msg, NamedTextColor.RED));
                }
            }));
        }));
    }

    private enum SearchStatus { FOUND, NOT_FOUND, AMBIGUOUS }
    private record SearchResult(SearchStatus status, PetSnapshot pet) {}

    private CompletableFuture<SearchResult> resolveOwnedPetByShortId(Player player, String shortId) {
        CompletableFuture<List<PetSnapshot>> petsFuture = petService instanceof AsyncPetService async ? async.getOwnedPetsAsync(player.getUniqueId()).thenApply(ArrayList::new) : CompletableFuture.completedFuture(new ArrayList<>(petService.getOwnedPets(player.getUniqueId())));

        return petsFuture.thenApply(list -> {
            List<PetSnapshot> matches = list.stream()
                    .filter(p -> p.petId().toString().toLowerCase().startsWith(shortId.toLowerCase()))
                    .toList();

            if (matches.isEmpty()) {
                return new SearchResult(SearchStatus.NOT_FOUND, null);
            } else if (matches.size() > 1) {
                return new SearchResult(SearchStatus.AMBIGUOUS, null);
            }
            return new SearchResult(SearchStatus.FOUND, matches.get(0));
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("companionpets.use")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("list", "summon", "dismiss", "info", "rename").stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("summon") || sub.equals("info") || sub.equals("rename")) {
                // Cache-only tab completion: never block the main thread on a DB query.
                if (profileCache != null) {
                    Optional<PlayerPetProfile> profile = profileCache.getProfile(player.getUniqueId());
                    if (profile.isPresent()) {
                        return profile.get().pets().values().stream()
                                .map(p -> p.petId().toString().substring(0, 6))
                                .filter(id -> id.startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }
}
