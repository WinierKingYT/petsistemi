package com.petsistemi.command;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.message.MessageService;
import com.petsistemi.message.PlaceholderMap;
import com.petsistemi.persistence.PlayerPetProfile;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetEmoteController;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class PetCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PetRuntimeOperationService operationService;
    private final PetService petService;
    private final PetDefinitionRegistry definitionRegistry;
    private final PlayerPetProfileCache profileCache;
    private final ActivePetRegistry activeRegistry;
    private final MessageService messageService;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final PetEmoteController emoteController;

    public PetCommand(JavaPlugin plugin, PetRuntimeOperationService operationService, PetService petService, PetDefinitionRegistry definitionRegistry, PlayerPetProfileCache profileCache, ActivePetRegistry activeRegistry, MessageService messageService, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot, PetEmoteController emoteController) {
        this.plugin = plugin;
        this.operationService = operationService;
        this.petService = petService;
        this.definitionRegistry = definitionRegistry;
        this.profileCache = profileCache;
        this.activeRegistry = activeRegistry;
        this.messageService = messageService;
        this.configSnapshot = configSnapshot;
        this.emoteController = emoteController;
    }

    public PetCommand(JavaPlugin plugin, PetRuntimeOperationService operationService, PetService petService, PetDefinitionRegistry definitionRegistry, PlayerPetProfileCache profileCache, ActivePetRegistry activeRegistry, MessageService messageService, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this(plugin, operationService, petService, definitionRegistry, profileCache, activeRegistry, messageService, configSnapshot, null);
    }

    public PetCommand(JavaPlugin plugin, PetRuntimeOperationService operationService, PetService petService, PetDefinitionRegistry definitionRegistry, PlayerPetProfileCache profileCache, ActivePetRegistry activeRegistry) {
        this(plugin, operationService, petService, definitionRegistry, profileCache, activeRegistry, null, null);
    }

    public PetCommand(JavaPlugin plugin, PetRuntimeOperationService operationService, PetService petService, PetDefinitionRegistry definitionRegistry, PlayerPetProfileCache profileCache) {
        this(plugin, operationService, petService, definitionRegistry, profileCache, null, null, null);
    }

    public PetCommand(JavaPlugin plugin, PetRuntimeOperationService operationService, PetService petService, PetDefinitionRegistry definitionRegistry) {
        this(plugin, operationService, petService, definitionRegistry, null, null, null, null);
    }

    public PetCommand(JavaPlugin plugin, PetService petService, PetDefinitionRegistry definitionRegistry) {
        this(plugin, null, petService, definitionRegistry, null, null, null, null);
    }

    public PetCommand(JavaPlugin plugin, PetService petService) {
        this(plugin, null, petService, null, null, null, null, null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-players", "<red>Bu komut sadece oyuncular tarafından kullanılabilir.</red>", null);
            return true;
        }

        if (!player.hasPermission("companionpets.use")) {
            send(player, "command.no-permission", "<red>Bu komutu kullanmak için yetkiniz yok.</red>", PlaceholderMap.of("permission", "companionpets.use"));
            return true;
        }

        if (args.length == 0) {
            com.petsistemi.gui.PetListMenu.open(player, petService, 0, plugin, definitionRegistry, configSnapshot, messageService);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> handleList(player);
            case "summon" -> handleSummon(player, args);
            case "dismiss" -> handleDismiss(player);
            case "info" -> handleInfo(player, args);
            case "rename" -> handleRename(player, args);
            case "mode" -> handleMode(player, args);
            case "emote" -> handleEmote(player, args);
            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        send(player, "command.help",
                "<gold>=== Minecraft Pet Sistemi ===</gold>" +
                "<newline><yellow>/pet list - Sahip olduğunuz petleri listeler.</yellow>" +
                "<newline><yellow>/pet summon <pet_id> - Belirtilen peti çağırır.</yellow>" +
                "<newline><yellow>/pet dismiss - Aktif petinizi kaldırır.</yellow>" +
                "<newline><yellow>/pet info [pet_id] - Pet detaylarını gösterir.</yellow>" +
                "<newline><yellow>/pet rename <pet_id> <isim> - Petinizi yeniden adlandırır.</yellow>" +
                "<newline><yellow>/pet mode <follow|stay|wander> - Petinizin takip modunu ayarlar.</yellow>" +
                "<newline><yellow>/pet emote <ad> - Petinizin tanımlı bir emotesini oynatır.</yellow>",
                null);
    }

    private void handleEmote(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "emote.usage", "<red>Kullanım: /pet emote <ad></red>", null);
            return;
        }
        if (emoteController == null || activeRegistry == null || definitionRegistry == null) {
            send(player, "emote.unavailable", "<red>Emote sistemi şu anda kullanılamıyor.</red>", null);
            return;
        }

        Optional<ActivePet> active = activeRegistry.getByOwner(player.getUniqueId());
        if (active.isEmpty() || active.get().getSpawnedEntity() == null) {
            send(player, "emote.no-active-pet", "<red>Önce petinizi çağırın (/pet list).</red>", null);
            return;
        }

        PetDefinition definition = definitionRegistry.find(active.get().getDefinitionId()).orElse(null);
        if (definition == null || definition.emotes() == null || definition.emotes().isEmpty()) {
            send(player, "emote.none-defined", "<red>Bu pet için tanımlı emote yok.</red>", null);
            return;
        }

        PetEmoteController.EmoteOutcome outcome = emoteController.play(
                player.getUniqueId(), active.get().getSpawnedEntity(), definition.emotes(), args[1]);

        switch (outcome.result()) {
            case PLAYED -> send(player, "emote.played",
                    "<green>Petiniz '" + args[1].toLowerCase() + "' emotesini oynattı!</green>",
                    PlaceholderMap.of("emote", args[1].toLowerCase()));
            case COOLDOWN -> send(player, "emote.cooldown",
                    "<yellow>Bu emote henüz kullanılamaz. Kalan süre: " + outcome.remainingSeconds() + " saniye.</yellow>",
                    PlaceholderMap.of("seconds", String.valueOf(outcome.remainingSeconds())));
            default -> send(player, "emote.invalid",
                    "<red>Geçersiz emote: " + args[1] + ". Mevcut emoteler: " + String.join(", ", definition.emotes().keySet()) + "</red>",
                    PlaceholderMap.of("emote", args[1]).add("list", String.join(", ", definition.emotes().keySet())));
        }
    }

    private void send(CommandSender sender, String key, String fallback, PlaceholderMap placeholders) {
        if (messageService != null) {
            messageService.send(sender, key, fallback, placeholders);
        } else if (sender != null) {
            String raw = fallback != null ? fallback : "";
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(raw));
        }
    }

    private void handleList(Player player) {
        CompletableFuture<List<PetSnapshot>> petsFuture = petService instanceof AsyncPetService async ? async.getOwnedPetsAsync(player.getUniqueId()).thenApply(ArrayList::new) : CompletableFuture.completedFuture(new ArrayList<>(petService.getOwnedPets(player.getUniqueId())));

        petsFuture.thenAccept(pets -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (pets.isEmpty()) {
                send(player, "command.no-pets", "<gray>Herhangi bir pete sahip değilsiniz. Yetkili kişilerden pet talep edebilirsiniz.</gray>", null);
                return;
            }

            send(player, "command.pets-header", "<gold>=== Evcil Hayvanlarınız ===</gold>", null);
            int i = 1;
            for (PetSnapshot pet : pets) {
                String shortId = pet.petId().toString().substring(0, 6);
                String name = pet.customName() != null ? pet.customName() : pet.definitionId();
                send(player, "command.pet-list-line",
                        "<yellow>" + i + ". " + name + " (ID: " + shortId + ") - Seviye " + pet.level() + "</yellow>",
                        PlaceholderMap.of("index", String.valueOf(i))
                                .add("name", name)
                                .add("pet_id", shortId)
                                .add("level", String.valueOf(pet.level())));
                i++;
            }
        }));
    }

    private void handleSummon(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "command.summon-usage", "<red>Kullanım: /pet summon <pet_id></red>", null);
            return;
        }

        String shortId = args[1];
        resolveOwnedPetByShortId(player, shortId).thenAccept(match -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (match.status == SearchStatus.NOT_FOUND) {
                send(player, "command.pet-id-not-found", "<red>Belirtilen ID ile eşleşen bir pet bulunamadı.</red>", null);
                return;
            } else if (match.status == SearchStatus.AMBIGUOUS) {
                send(player, "command.pet-id-ambiguous", "<red>Birden fazla pet bu kimlik ön ekiyle eşleşiyor (belirsiz ID). Lütfen daha fazla karakter girin.</red>", null);
                return;
            }

            CompletableFuture<?> summonFuture = operationService != null ? operationService.summonAsync(player, match.pet.petId()) : CompletableFuture.completedFuture(petService.summon(player, match.pet.petId()));
            summonFuture.thenAccept(res -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                boolean success = res instanceof com.petsistemi.api.result.PetSummonResult r && r.success();
                String msg = res instanceof com.petsistemi.api.result.PetSummonResult r ? r.message() : "Çağırma başarısız.";
                if (success) {
                    String name = match.pet.customName() != null ? match.pet.customName() : match.pet.definitionId();
                    send(player, "command.pet-summoned", "<green>" + name + " başarıyla çağırıldı!</green>", PlaceholderMap.of("name", name));
                } else {
                    send(player, "command.summon-failed", "<red>" + msg + "</red>", PlaceholderMap.of("error", msg));
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
                send(player, "command.pet-dismissed", "<yellow>Petiniz kaldırıldı.</yellow>", null);
            } else {
                send(player, "command.dismiss-failed", "<red>" + msg + "</red>", PlaceholderMap.of("error", msg));
            }
        }));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length >= 2) {
            String shortId = args[1];
            resolveOwnedPetByShortId(player, shortId).thenAccept(match -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (match.status == SearchStatus.NOT_FOUND) {
                    send(player, "command.pet-not-found", "<red>Pet bulunamadı.</red>", null);
                    return;
                } else if (match.status == SearchStatus.AMBIGUOUS) {
                    send(player, "command.pet-id-ambiguous", "<red>Birden fazla pet bu kimlik ön ekiyle eşleşiyor.</red>", null);
                    return;
                }
                displayInfo(player, match.pet);
            }));
        } else {
            CompletableFuture<Optional<PetSnapshot>> selectedFuture = petService instanceof AsyncPetService async ? async.getSelectedPetAsync(player.getUniqueId()) : CompletableFuture.completedFuture(petService.getSelectedPet(player.getUniqueId()));

            selectedFuture.thenAccept(opt -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (opt.isEmpty()) {
                    send(player, "command.no-active-pet", "<red>Detaylarını görecek aktif veya belirtilmiş bir pet bulunamadı.</red>", null);
                    return;
                }
                displayInfo(player, opt.get());
            }));
        }
    }

    private void displayInfo(Player player, PetSnapshot pet) {
        String customName = pet.customName() != null ? pet.customName() : "Yok";
        send(player, "command.info-line",
                "<gold>=== Pet Bilgisi ===</gold>" +
                "<newline><yellow>Tür ID: " + pet.definitionId() + "</yellow>" +
                "<newline><yellow>Özel İsim: " + customName + "</yellow>" +
                "<newline><yellow>Seviye: " + pet.level() + "</yellow>" +
                "<newline><yellow>Deneyim: " + pet.experience() + "</yellow>" +
                "<newline><yellow>Durum: " + pet.availabilityState().name() + "</yellow>" +
                "<newline><yellow>Pet UUID: " + pet.petId() + "</yellow>",
                PlaceholderMap.of("definition", pet.definitionId())
                        .add("name", customName)
                        .add("level", String.valueOf(pet.level()))
                        .add("experience", String.valueOf(pet.experience()))
                        .add("state", pet.availabilityState().name())
                        .add("pet_id", pet.petId().toString()));
    }

    private void handleRename(Player player, String[] args) {
        if (args.length < 3) {
            send(player, "command.rename-usage", "<red>Kullanım: /pet rename <pet_id> <yeni_isim></red>", null);
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
                send(player, "command.pet-not-found", "<red>Pet bulunamadı.</red>", null);
                return;
            } else if (match.status == SearchStatus.AMBIGUOUS) {
                send(player, "command.pet-id-ambiguous", "<red>Birden fazla pet bu kimlik ön ekiyle eşleşiyor.</red>", null);
                return;
            }

            CompletableFuture<?> renameFuture = petService instanceof AsyncPetService async ? async.renameAsync(player.getUniqueId(), match.pet.petId(), newName) : CompletableFuture.completedFuture(petService.rename(player.getUniqueId(), match.pet.petId(), newName));

            renameFuture.thenAccept(res -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                boolean success = res instanceof com.petsistemi.api.result.PetRenameResult r && r.success();
                String msg = res instanceof com.petsistemi.api.result.PetRenameResult r ? r.message() : "İsim değiştirme başarısız.";
                if (success) {
                    send(player, "command.pet-renamed", "<green>Petinizin ismi '" + newName + "' olarak güncellendi!</green>", PlaceholderMap.of("name", newName));
                } else {
                    send(player, "command.rename-failed", "<red>İsim değiştirilemedi: " + msg + "</red>", PlaceholderMap.of("error", msg));
                }
            }));
        }));
    }

    private void handleMode(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "mode.usage", "<red>Kullanım: /pet mode <follow|stay|wander></red>", null);
            return;
        }

        PetFollowMode mode = PetFollowMode.fromString(args[1]);
        if (mode == null) {
            send(player, "mode.invalid", "<red>Geçersiz mod! Seçenekler: follow, stay, wander</red>", null);
            return;
        }

        if (activeRegistry == null) {
            send(player, "mode.require-spawned", "<red>Mod sistemi şu anda kullanılamıyor.</red>", null);
            return;
        }

        Optional<ActivePet> active = activeRegistry.getByOwner(player.getUniqueId());
        if (active.isEmpty()) {
            send(player, "mode.require-spawned", "<red>Önce petinizi çağırın (/pet list).</red>", null);
            return;
        }

        ActivePet activePet = active.get();
        activePet.setFollowMode(mode);
        if (activePet.getSpawnedEntity() instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
        }

        if (operationService != null) {
            operationService.setFollowModeAsync(player, mode).thenAccept(persisted -> {
                if (persisted) {
                    send(player, "mode.set", "<green>Petinizin takip modu '" + mode.name().toLowerCase() + "' olarak ayarlandı ve kaydedildi.</green>", PlaceholderMap.of("mode", mode.name().toLowerCase()));
                } else {
                    send(player, "mode.persist-failed", "<yellow>Petinizin takip modu '" + mode.name().toLowerCase() + "' olarak ayarlandı, ancak kaydedilemedi (yeniden girişte sıfırlanır).</yellow>", PlaceholderMap.of("mode", mode.name().toLowerCase()));
                }
            });
        } else {
            send(player, "mode.set", "<green>Petinizin takip modu '" + mode.name().toLowerCase() + "' olarak ayarlandı.</green>", PlaceholderMap.of("mode", mode.name().toLowerCase()));
        }
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
            return Arrays.asList("list", "summon", "dismiss", "info", "rename", "mode", "emote").stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("mode")) {
                return Arrays.asList("follow", "stay", "wander").stream()
                        .filter(m -> m.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("emote")) {
                if (definitionRegistry != null && activeRegistry != null) {
                    Optional<ActivePet> active = activeRegistry.getByOwner(player.getUniqueId());
                    if (active.isPresent()) {
                        PetDefinition definition = definitionRegistry.find(active.get().getDefinitionId()).orElse(null);
                        if (definition != null && definition.emotes() != null) {
                            return definition.emotes().keySet().stream()
                                    .filter(name -> name.startsWith(args[1].toLowerCase()))
                                    .collect(Collectors.toList());
                        }
                    }
                }
                return Collections.emptyList();
            }
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
