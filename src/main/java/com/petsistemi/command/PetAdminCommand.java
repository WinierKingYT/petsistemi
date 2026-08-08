package com.petsistemi.command;

import com.petsistemi.api.AsyncPetExperienceService;
import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.result.*;
import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.PluginConfigurationLoader;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.message.MessageService;
import com.petsistemi.message.PlaceholderMap;
import com.petsistemi.persistence.*;
import com.petsistemi.persistence.migration.MigrationBackupManager;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PetAdminCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PetService petService;
    private final PetExperienceService experienceService;
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activeRegistry;
    private final PetRepository repository;
    private final PetSelectionRepository selectionRepository;
    private final ConnectionProvider connectionProvider;
    private final AdminPersistenceService adminPersistenceService;
    private final AuditLogger auditLogger;
    private final PetRuntimeCoordinator coordinator;
    private final PlayerPetProfileCache profileCache;
    private final MessageService messageService;
    private final com.petsistemi.bootstrap.TaskRegistry taskRegistry;
    private final com.petsistemi.bootstrap.PetPluginContext context;
    private final com.petsistemi.definition.editor.PetEditorSessionManager editorSessions;

    private final NamespacedKey petIdKey;

    public PetAdminCommand(com.petsistemi.bootstrap.PetPluginContext context) {
        this(
                context.plugin(),
                context.petService(),
                context.experienceService(),
                context.definitionRegistry(),
                context.activePetRegistry(),
                context.petRepository(),
                context.selectionRepository(),
                context.connectionProvider(),
                context.auditLogger(),
                context.coordinator(),
                context.profileCache(),
                context.messageService(),
                context.taskRegistry(),
                context
        );
    }

    public PetAdminCommand(JavaPlugin plugin, PetService petService,
                           PetExperienceService experienceService,
                           PetDefinitionRegistry definitionRegistry,
                           ActivePetRegistry activeRegistry,
                           PetRepository repository,
                           PetSelectionRepository selectionRepository,
                           ConnectionProvider connectionProvider,
                           AuditLogger auditLogger,
                           PetRuntimeCoordinator coordinator,
                           PlayerPetProfileCache profileCache,
                           MessageService messageService) {
        this(plugin, petService, experienceService, definitionRegistry, activeRegistry, repository, selectionRepository, connectionProvider, auditLogger, coordinator, profileCache, messageService, null, null);
    }

    public PetAdminCommand(JavaPlugin plugin, PetService petService,
                           PetExperienceService experienceService,
                           PetDefinitionRegistry definitionRegistry,
                           ActivePetRegistry activeRegistry,
                           PetRepository repository,
                           PetSelectionRepository selectionRepository,
                           ConnectionProvider connectionProvider,
                           AuditLogger auditLogger,
                           PetRuntimeCoordinator coordinator,
                           PlayerPetProfileCache profileCache,
                           MessageService messageService,
                           com.petsistemi.bootstrap.TaskRegistry taskRegistry) {
        this(plugin, petService, experienceService, definitionRegistry, activeRegistry, repository, selectionRepository, connectionProvider, auditLogger, coordinator, profileCache, messageService, taskRegistry, null);
    }

    public PetAdminCommand(JavaPlugin plugin, PetService petService,
                           PetExperienceService experienceService,
                           PetDefinitionRegistry definitionRegistry,
                           ActivePetRegistry activeRegistry,
                           PetRepository repository,
                           PetSelectionRepository selectionRepository,
                           ConnectionProvider connectionProvider,
                           AuditLogger auditLogger,
                           PetRuntimeCoordinator coordinator,
                           PlayerPetProfileCache profileCache,
                           MessageService messageService,
                           com.petsistemi.bootstrap.TaskRegistry taskRegistry,
                           com.petsistemi.bootstrap.PetPluginContext context) {
        this.plugin = plugin;
        this.petService = petService;
        this.experienceService = experienceService;
        this.definitionRegistry = definitionRegistry;
        this.activeRegistry = activeRegistry;
        this.repository = repository;
        this.selectionRepository = selectionRepository;
        this.connectionProvider = connectionProvider;
        this.adminPersistenceService = context != null && context.adminPersistenceService() != null
                ? context.adminPersistenceService()
                : (context != null && context.dbExecutor() != null && connectionProvider != null
                ? new AdminPersistenceService(context.dbExecutor(), connectionProvider,
                plugin != null ? plugin.getLogger() : java.util.logging.Logger.getLogger("PetAdminCommand"),
                DatabaseBackend.from(context.config() != null ? context.config().database().backend() : "SQLITE"))
                : null);
        this.auditLogger = auditLogger;
        this.coordinator = coordinator;
        this.profileCache = profileCache;
        this.messageService = messageService;
        this.taskRegistry = taskRegistry;
        this.context = context;
        this.editorSessions = context != null ? context.editorSessionManager() : null;

        this.petIdKey = plugin != null ? new NamespacedKey(plugin, "pet_id") : null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!checkPerm(sender, "companionpets.admin")) return true;
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!checkPerm(sender, "companionpets.admin.give")) return true;
                handleGive(sender, args);
            }
            case "unlockitem" -> {
                if (!checkPerm(sender, "companionpets.admin.unlockitem")) return true;
                handleUnlockItem(sender, args);
            }
            case "pack" -> {
                if (!checkPerm(sender, "companionpets.admin.pack")) return true;
                handlePack(sender, args);
            }
            case "marketplace", "market" -> {
                if (!checkPerm(sender, "companionpets.admin.marketplace")) return true;
                handleMarketplace(sender, args);
            }
            case "remove" -> {
                if (!checkPerm(sender, "companionpets.admin.remove")) return true;
                handleRemove(sender, args);
            }
            case "list" -> {
                if (!checkPerm(sender, "companionpets.admin.list")) return true;
                handleList(sender, args);
            }
            case "info" -> {
                if (!checkPerm(sender, "companionpets.admin.info")) return true;
                handleInfo(sender, args);
            }
            case "addxp" -> {
                if (!checkPerm(sender, "companionpets.admin.addxp")) return true;
                handleAddXp(sender, args);
            }
            case "setxp" -> {
                if (!checkPerm(sender, "companionpets.admin.setxp")) return true;
                handleSetXp(sender, args);
            }
            case "setlevel" -> {
                if (!checkPerm(sender, "companionpets.admin.setlevel")) return true;
                handleSetLevel(sender, args);
            }
            case "summon" -> {
                if (!checkPerm(sender, "companionpets.admin.summon")) return true;
                handleSummon(sender, args);
            }
            case "dismiss" -> {
                if (!checkPerm(sender, "companionpets.admin.dismiss")) return true;
                handleDismiss(sender, args);
            }
            case "reload" -> {
                if (!checkPerm(sender, "companionpets.admin.reload")) return true;
                handleReload(sender);
            }
            case "editor" -> {
                if (!checkPerm(sender, "companionpets.admin.editor")) return true;
                handleEditor(sender, args);
            }
            case "inspect" -> {
                if (!checkPerm(sender, "companionpets.admin.inspect")) return true;
                handleInspect(sender);
            }
            case "health" -> {
                if (!checkPerm(sender, "companionpets.admin.health")) return true;
                handleHealth(sender);
            }
            case "backup" -> {
                if (!checkPerm(sender, "companionpets.admin.backup")) return true;
                handleBackup(sender);
            }
            case "reconcile" -> {
                if (!checkPerm(sender, "companionpets.admin.reconcile")) return true;
                handleReconcile(sender, args);
            }
            case "disable" -> {
                if (!checkPerm(sender, "companionpets.admin.disable")) return true;
                handleDisable(sender, args);
            }
            case "enable" -> {
                if (!checkPerm(sender, "companionpets.admin.enable")) return true;
                handleEnable(sender, args);
            }
            case "giveall" -> {
                if (!checkPerm(sender, "companionpets.admin.giveall")) return true;
                handleGiveAll(sender, args);
            }
            case "benchmark" -> {
                if (!checkPerm(sender, "companionpets.admin.benchmark")) return true;
                handleBenchmark(sender);
            }
            case "vacuum" -> {
                if (!checkPerm(sender, "companionpets.admin.vacuum")) return true;
                handleVacuum(sender);
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private boolean checkPerm(CommandSender sender, String perm) {
        if (sender.hasPermission("companionpets.admin") || sender.hasPermission(perm)) {
            return true;
        }
        send(sender, "command.no-permission", "<red>Bu komutu kullanmak için yetkiniz yok: " + perm + "</red>", PlaceholderMap.of("permission", perm));
        return false;
    }

    private void handleEditor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-players", "<red>Oyun içi editör yalnızca oyuncular tarafından açılabilir.</red>", null);
            return;
        }
        if (editorSessions == null || definitionRegistry == null) {
            send(player, "admin.editor-unavailable", "<red>Tanım editörü bu çalışma zamanında kullanılamıyor.</red>", null);
            return;
        }
        if (args.length < 2) {
            com.petsistemi.gui.PetDefinitionEditorMenu.openCatalogue(player, plugin, definitionRegistry, 0);
            return;
        }
        try {
            if (context != null && context.sessionManager() != null) {
                context.sessionManager().removeSession(player.getUniqueId());
            }
            editorSessions.begin(player.getUniqueId(), args[1]);
            com.petsistemi.gui.PetDefinitionEditorMenu.openDefinition(player, plugin, editorSessions);
        } catch (Exception e) {
            send(player, "admin.editor-open-failed", "<red>Editör açılamadı: " + e.getMessage() + "</red>", null);
        }
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
        send(sender, "admin.help-header", "<gold><b>=== PetSistemi Yönetici Komutları ===</b></gold>", null);
        sendHelpLine(sender, "/petadmin give <oyuncu> <tur_id>",         "Pet ver");
        sendHelpLine(sender, "/petadmin unlockitem <oyuncu> <tur_id> [miktar] [materyal]", "Unlock itemi ver");
        sendHelpLine(sender, "/petadmin pack <list|install|uninstall|export>", "Pet Pack yönetimi");
        sendHelpLine(sender, "/petadmin marketplace <refresh|list|install>", "Marketplace yönetimi");
        sendHelpLine(sender, "/petadmin remove <oyuncu> <pet_id>",       "Pet sil");
        sendHelpLine(sender, "/petadmin list <oyuncu>",                  "Pet listesi");
        sendHelpLine(sender, "/petadmin info <oyuncu>",                  "Detaylı pet raporu");
        sendHelpLine(sender, "/petadmin addxp <oyuncu> <pet_id> <xp>",   "XP ekle");
        sendHelpLine(sender, "/petadmin setxp <oyuncu> <pet_id> <xp>",   "XP ayarla");
        sendHelpLine(sender, "/petadmin setlevel <oyuncu> <pet_id> <lv>","Seviye ayarla");
        sendHelpLine(sender, "/petadmin summon <oyuncu> <pet_id>",        "Pet çağır");
        sendHelpLine(sender, "/petadmin dismiss <oyuncu>",                "Peti gönder");
        sendHelpLine(sender, "/petadmin disable <pet_id>",                "Peti devre dışı bırak");
        sendHelpLine(sender, "/petadmin enable <pet_id>",                 "Peti etkinleştir");
        sendHelpLine(sender, "/petadmin inspect",                         "Baktığın peti denetle");
        sendHelpLine(sender, "/petadmin health",                          "Sistem sağlığı raporu");
        sendHelpLine(sender, "/petadmin backup",                          "Veritabanı yedeği al");
        sendHelpLine(sender, "/petadmin reconcile",                       "Yetim entity uzlaştır");
        sendHelpLine(sender, "/petadmin reload",                          "Yapılandırmayı yenile");
        sendHelpLine(sender, "/petadmin editor [tanım_id]",                "Oyun içi tanım editörünü aç");
        send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
    }

    private void sendHelpLine(CommandSender sender, String cmd, String desc) {
        if (sender instanceof Player player) {
            String interactiveLine = "<dark_gray>  ● </dark_gray>" +
                    "<click:suggest_command:'" + cmd + "'>" +
                    "<hover:show_text:'<gold>" + cmd + "</gold><newline><gray>" + desc + "<newline><yellow>⚡ Komutu hazırlamak için tıkla!</yellow>'>" +
                    "<yellow><u>" + cmd + "</u></yellow></hover></click> <gray>— " + desc + "</gray>";
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(interactiveLine));
        } else {
            send(sender, "admin.help-line", "<dark_gray>  </dark_gray><yellow>" + cmd + "</yellow><gray> — " + desc + "</gray>",
                    PlaceholderMap.of("command", cmd).add("description", desc));
        }
    }

    private void send(CommandSender sender, String key, String fallback, PlaceholderMap placeholders) {
        if (messageService != null) {
            messageService.send(sender, key, fallback, placeholders);
        } else if (sender != null) {
            sender.sendMessage(com.petsistemi.message.MiniMessageRenderer.render(fallback, placeholders));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin give <oyuncu> <tur_id> [miktar]</red>", PlaceholderMap.of("usage", "/petadmin give <oyuncu> <tur_id> [miktar]"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String defId = args[2].toLowerCase(java.util.Locale.ROOT);
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
        int quantity = 1;
        if (args.length >= 4) {
            try { quantity = Math.max(1, Integer.parseInt(args[3])); } catch (Exception ignored) {}
        }

        for (int i = 0; i < quantity; i++) {
            CompletableFuture<PetGiveResult> future = petService instanceof AsyncPetService async ? async.givePetAsync(target.getUniqueId(), defId) : CompletableFuture.completedFuture(petService.givePet(target.getUniqueId(), defId));
            future.thenAccept(result -> sendMessageOnMain(sender, () -> {
                if (result.success() && result.petSnapshot() != null) {
                    UUID petId = result.petSnapshot().petId();
                    send(sender, "admin.pet-given-header", "<green><b>✔ Pet verilme başarılı: " + targetName + " -> " + defId.toUpperCase(java.util.Locale.ROOT) + "</b></green>", null);
                    if (auditLogger != null) {
                        auditLogger.logAction("GIVE_PET", sender.getName(), target.getUniqueId(), petId, "Tür: " + defId);
                    }
                } else {
                    send(sender, "admin.pet-give-failed", "<red>✖ Pet verilemedi: " + result.message() + "</red>", PlaceholderMap.of("error", result.message()));
                }
            }));
        }
    }

    private void handleUnlockItem(CommandSender sender, String[] args) {
        if (context == null || context.unlockItemController() == null) {
            send(sender, "admin.unlockitem-unavailable", "<red>Unlock item servisi hazır değil.</red>", null);
            return;
        }
        if (args.length < 3) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin unlockitem <oyuncu> <tur_id> [miktar] [materyal]</red>",
                    PlaceholderMap.of("usage", "/petadmin unlockitem <oyuncu> <tur_id> [miktar] [materyal]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "admin.player-not-found", "<red>Oyuncu çevrimiçi değil veya bulunamadı.</red>", null);
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Integer.parseInt(args[3]); }
            catch (NumberFormatException ignored) {
                send(sender, "admin.invalid-number", "<red>Miktar geçerli bir sayı olmalıdır.</red>", null);
                return;
            }
        }
        Material material = Material.NAME_TAG;
        if (args.length >= 5) {
            material = Material.matchMaterial(args[4]);
            if (material == null) {
                send(sender, "admin.invalid-material", "<red>Geçersiz materyal: " + args[4] + "</red>", null);
                return;
            }
        }
        try {
            ItemStack item = context.unlockItemController().create(args[2], amount, material);
            target.getInventory().addItem(item).values().forEach(leftover ->
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            send(sender, "admin.unlockitem-success", "<green>" + target.getName() + " oyuncusuna " + amount
                    + " adet " + args[2].toLowerCase(java.util.Locale.ROOT) + " unlock itemi verildi.</green>", null);
            if (auditLogger != null) {
                auditLogger.logAction("GIVE_UNLOCK_ITEM", sender.getName(), target.getUniqueId(), null,
                        args[2] + " x" + amount + " (" + material.name() + ")");
            }
        } catch (IllegalArgumentException error) {
            send(sender, "admin.unlockitem-failed", "<red>Unlock itemi oluşturulamadı: " + error.getMessage() + "</red>", null);
        }
    }

    private void handlePack(CommandSender sender, String[] args) {
        if (context == null || context.petPackService() == null) {
            send(sender, "admin.pack-unavailable", "<red>Pet Pack servisi hazır değil.</red>", null);
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "list";
        if (action.equals("list")) {
            var installed = context.petPackService().installed();
            if (installed.isEmpty()) {
                send(sender, "admin.pack-empty", "<gray>Kurulu Pet Pack yok.</gray>", null);
                return;
            }
            send(sender, "admin.pack-header", "<gold>Kurulu Pet Pack'ler:</gold>", null);
            installed.forEach(pack -> send(sender, "admin.pack-line",
                    "<yellow>• " + pack.id() + "</yellow><gray> @ " + pack.version() + " [" + pack.namespace() + "]</gray>", null));
            return;
        }
        if (action.equals("install")) {
            if (args.length < 3) {
                send(sender, "admin.usage", "<red>Kullanım: /petadmin pack install <dosya.petpack></red>", null);
                return;
            }
            java.nio.file.Path inbox = plugin.getDataFolder().toPath().resolve("packs").resolve("inbox").toAbsolutePath().normalize();
            java.nio.file.Path archive = inbox.resolve(args[2]).normalize();
            if (!archive.startsWith(inbox)) {
                send(sender, "admin.pack-invalid-path", "<red>Paket yolu inbox dışına çıkamaz.</red>", null);
                return;
            }
            runPackAsync(sender, () -> context.petPackService().install(archive, archive.toUri()));
            return;
        }
        if (action.equals("uninstall")) {
            if (args.length < 3) {
                send(sender, "admin.usage", "<red>Kullanım: /petadmin pack uninstall <pack_id></red>", null);
                return;
            }
            runPackAsync(sender, () -> context.petPackService().uninstall(args[2]));
            return;
        }
        if (action.equals("export")) {
            if (args.length < 6) {
                send(sender, "admin.usage", "<red>Kullanım: /petadmin pack export <id> <namespace> <version> <pet_id...></red>", null);
                return;
            }
            com.petsistemi.pack.PetPackManifest manifest = new com.petsistemi.pack.PetPackManifest(
                    1, args[2].toLowerCase(java.util.Locale.ROOT), args[3].toLowerCase(java.util.Locale.ROOT),
                    args[4], plugin.getDescription().getVersion(), "", java.util.List.of(sender.getName()), java.util.List.of());
            java.util.List<String> definitions = java.util.Arrays.asList(args).subList(5, args.length);
            java.nio.file.Path output = plugin.getDataFolder().toPath().resolve("packs").resolve("exports")
                    .resolve(manifest.id() + "-" + manifest.version() + ".petpack");
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    java.nio.file.Path exported = context.petPackService().exportPack(manifest, definitions, output);
                    sendMessageOnMain(sender, () -> send(sender, "admin.pack-exported",
                            "<green>Pet Pack dışa aktarıldı: " + exported.getFileName() + "</green>", null));
                } catch (Exception error) {
                    sendMessageOnMain(sender, () -> send(sender, "admin.pack-failed", "<red>Pet Pack dışa aktarılamadı: " + error.getMessage() + "</red>", null));
                }
            });
            return;
        }
        send(sender, "admin.usage", "<red>Kullanım: /petadmin pack <list|install|uninstall|export></red>", null);
    }

    private void runPackAsync(CommandSender sender, java.util.function.Supplier<com.petsistemi.pack.PetPackInstallResult> action) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            com.petsistemi.pack.PetPackInstallResult result;
            try {
                result = action.get();
            } catch (Exception error) {
                result = new com.petsistemi.pack.PetPackInstallResult(false,
                        "Pet Pack işlemi başarısız: " + rootMessage(error), null, java.util.List.of(), false);
            }
            com.petsistemi.pack.PetPackInstallResult completed = result;
            sendMessageOnMain(sender, () -> send(sender, completed.success() ? "admin.pack-success" : "admin.pack-failed",
                    (completed.success() ? "<green>" : "<red>") + completed.message() + (completed.success() ? "</green>" : "</red>"), null));
        });
    }

    private void handleMarketplace(CommandSender sender, String[] args) {
        if (context == null || context.marketplaceService() == null) {
            send(sender, "admin.marketplace-disabled", "<red>Marketplace config.yml içinde etkin değil.</red>", null);
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "list";
        if (action.equals("refresh")) {
            context.marketplaceService().refreshAsync().whenComplete((entries, error) -> sendMessageOnMain(sender, () -> {
                if (error != null) send(sender, "admin.marketplace-failed", "<red>Katalog yenilenemedi: " + rootMessage(error) + "</red>", null);
                else send(sender, "admin.marketplace-refreshed", "<green>Marketplace kataloğu yenilendi: " + entries.size() + " paket.</green>", null);
            }));
            return;
        }
        if (action.equals("list")) {
            var entries = context.marketplaceService().entries();
            if (entries.isEmpty()) {
                send(sender, "admin.marketplace-empty", "<gray>Katalog boş; önce /petadmin marketplace refresh kullanın.</gray>", null);
            } else {
                send(sender, "admin.marketplace-header", "<gold>Marketplace paketleri:</gold>", null);
                entries.forEach(entry -> send(sender, "admin.marketplace-line", "<yellow>• " + entry.id()
                        + "</yellow><gray> @ " + entry.version() + " — " + entry.name() + "</gray>", null));
            }
            return;
        }
        if (action.equals("install") && args.length >= 3) {
            context.marketplaceService().installAsync(args[2]).thenAccept(result -> sendMessageOnMain(sender, () ->
                    send(sender, result.success() ? "admin.marketplace-success" : "admin.marketplace-failed",
                            (result.success() ? "<green>" : "<red>") + result.message()
                                    + (result.success() ? "</green>" : "</red>"), null)));
            return;
        }
        send(sender, "admin.usage", "<red>Kullanım: /petadmin marketplace <refresh|list|install [id]></red>", null);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private void handleGiveAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin giveall <tur_id> [miktar]</red>", PlaceholderMap.of("usage", "/petadmin giveall <tur_id> [miktar]"));
            return;
        }
        String defId = args[1].toLowerCase(java.util.Locale.ROOT);
        int quantity = 1;
        if (args.length >= 3) {
            try { quantity = Math.max(1, Integer.parseInt(args[2])); } catch (Exception ignored) {}
        }

        int playerCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            playerCount++;
            for (int i = 0; i < quantity; i++) {
                if (petService instanceof AsyncPetService async) {
                    async.givePetAsync(p.getUniqueId(), defId);
                } else {
                    petService.givePet(p.getUniqueId(), defId);
                }
            }
        }
        send(sender, "admin.giveall-success", "<green>✔ Toplam " + playerCount + " çevrimiçi oyuncuya " + quantity + " adet '" + defId.toUpperCase(java.util.Locale.ROOT) + "' peti verilme talimatı gönderildi!</green>", null);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin remove <oyuncu> <pet_id></red>", PlaceholderMap.of("usage", "/petadmin remove <oyuncu> <pet_id>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() == SearchStatus.NOT_FOUND) {
                send(sender, "command.pet-not-found", "<red>Pet bulunamadı.</red>", null);
                return;
            } else if (search.status() == SearchStatus.AMBIGUOUS) {
                send(sender, "admin.pet-ambiguous", "<red>Birden fazla pet eşleşti! Lütfen tam UUID yazın.</red>", null);
                return;
            }

            UUID petId = search.pet().petId();
            CompletableFuture<?> future = petService instanceof AsyncPetService async ? async.removePetAsync(petId) : CompletableFuture.completedFuture(petService.removePet(petId));
            future.thenAccept(res -> sendMessageOnMain(sender, () -> {
                boolean success = res instanceof PetRemoveResult r && r.success();
                String msg = res instanceof PetRemoveResult r ? r.message() : "Silme başarısız.";
                if (success) {
                    if (auditLogger != null) {
                        auditLogger.logAction("REMOVE_PET", sender.getName(), target.getUniqueId(), petId, "Pet silindi");
                    }
                    send(sender, "command.pet-removed", "<green>Pet başarıyla silindi.</green>", null);
                } else {
                    send(sender, "admin.pet-remove-failed", "<red>Pet silinemedi: " + msg + "</red>", PlaceholderMap.of("error", msg));
                }
            }));
        }));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin list <oyuncu></red>", PlaceholderMap.of("usage", "/petadmin list <oyuncu>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);

        CompletableFuture<List<PetSnapshot>> future = petService instanceof AsyncPetService async ? async.getOwnedPetsAsync(target.getUniqueId()).thenApply(ArrayList::new) : CompletableFuture.completedFuture(new ArrayList<>(petService.getOwnedPets(target.getUniqueId())));

        future.thenAccept(pets -> sendMessageOnMain(sender, () -> {
            send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
            send(sender, "admin.list-header", "<gold><b>=== " + targetName + " — Pet Listesi (" + pets.size() + " pet) ===</b></gold>",
                    PlaceholderMap.of("player", targetName).add("count", String.valueOf(pets.size())));

            if (pets.isEmpty()) {
                send(sender, "admin.list-empty", "<gray>  Bu oyuncunun hiç peti yok.</gray>", null);
            } else {
                for (PetSnapshot pet : pets) {
                    String name = pet.customName() != null ? pet.customName() : pet.definitionId();
                    String stateLabel = pet.availabilityState() == PetAvailabilityState.DISABLED
                            ? "KAPALI" : (pet.spawned() ? "AKTIF" : "HAZIR");
                    send(sender, "admin.list-line",
                            "<gold>  ● </gold><light_purple>[" + pet.definitionId().toUpperCase(java.util.Locale.ROOT) + "] </light_purple><white>" + name + "</white><yellow> Lv." + pet.level() + "</yellow> [" + stateLabel + "] <dark_gray>#" + pet.petId().toString().substring(0, 8) + "</dark_gray>",
                            PlaceholderMap.of("definition", pet.definitionId().toUpperCase(java.util.Locale.ROOT))
                                    .add("name", name)
                                    .add("level", String.valueOf(pet.level()))
                                    .add("state", stateLabel)
                                    .add("pet_id", pet.petId().toString().substring(0, 8)));
                }
            }
            send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
        }));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin info <oyuncu></red>", PlaceholderMap.of("usage", "/petadmin info <oyuncu>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);

        CompletableFuture<List<PetSnapshot>> ownedFuture = petService instanceof AsyncPetService async ? async.getOwnedPetsAsync(target.getUniqueId()).thenApply(ArrayList::new) : CompletableFuture.completedFuture(new ArrayList<>(petService.getOwnedPets(target.getUniqueId())));
        CompletableFuture<Optional<PetSnapshot>> selectedFuture = petService instanceof AsyncPetService async ? async.getSelectedPetAsync(target.getUniqueId()) : CompletableFuture.completedFuture(petService.getSelectedPet(target.getUniqueId()));

        ownedFuture.thenCombine(selectedFuture, (owned, selected) -> {
            sendMessageOnMain(sender, () -> {
                Optional<PetSnapshot> spawned = owned.stream().filter(PetSnapshot::spawned).findFirst();

                send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
                send(sender, "command.info-header", "<gold>=== Oyuncu Pet Bilgileri: " + targetName + " ===</gold>", PlaceholderMap.of("player", targetName));
                send(sender, "admin.info-uuid", "<gray>Oyuncu UUID: </gray><white>" + target.getUniqueId() + "</white>", PlaceholderMap.of("uuid", target.getUniqueId().toString()));
                send(sender, "admin.info-total", "<gray>Toplam Pet Sayısı: </gray><yellow>" + owned.size() + "</yellow>", PlaceholderMap.of("count", String.valueOf(owned.size())));
                send(sender, "admin.info-selected", "<gray>Seçili Pet ID: </gray><aqua>" + selected.map(s -> s.petId().toString().substring(0, 8)).orElse("Yok") + "</aqua>", PlaceholderMap.of("pet_id", selected.map(s -> s.petId().toString().substring(0, 8)).orElse("Yok")));
                send(sender, "admin.info-spawned", "<gray>Dünyada Spawned: </gray><green>" + spawned.map(s -> s.petId().toString().substring(0, 8)).orElse("Yok") + "</green>", PlaceholderMap.of("pet_id", spawned.map(s -> s.petId().toString().substring(0, 8)).orElse("Yok")));
                send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
                for (PetSnapshot pet : owned) {
                    String name = pet.customName() != null ? pet.customName() : pet.definitionId();
                    send(sender, "admin.info-line",
                            "<gold>- [" + pet.definitionId() + "] </gold><white>" + name + "</white><yellow> (Lv." + pet.level() + ", " + pet.experience() + " XP) </yellow><gray>[" + pet.availabilityState() + "]</gray><dark_gray> ID: " + pet.petId().toString().substring(0, 8) + "</dark_gray>",
                            PlaceholderMap.of("definition", pet.definitionId())
                                    .add("name", name)
                                    .add("level", String.valueOf(pet.level()))
                                    .add("experience", String.valueOf(pet.experience()))
                                    .add("state", pet.availabilityState().name())
                                    .add("pet_id", pet.petId().toString().substring(0, 8)));
                }
                send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
            });
            return null;
        });
    }

    private void handleAddXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin addxp <oyuncu> <pet_id> <miktar></red>", PlaceholderMap.of("usage", "/petadmin addxp <oyuncu> <pet_id> <miktar>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() != SearchStatus.FOUND) {
                send(sender, "command.pet-not-found", "<red>Pet bulunamadı.</red>", null);
                return;
            }

            try {
                long xp = Long.parseLong(args[3]);
                CompletableFuture<ExperienceResult> future = experienceService instanceof AsyncPetExperienceService async ? async.addExperienceAsync(search.pet().petId(), xp, ExperienceSource.COMMAND) : CompletableFuture.completedFuture(experienceService.addExperience(search.pet().petId(), xp, ExperienceSource.COMMAND));

                future.thenAccept(result -> sendMessageOnMain(sender, () -> {
                    if (result.success()) {
                        send(sender, "admin.xp-added", "<green>XP eklendi! Toplam XP: " + result.newExperience() + "</green>", PlaceholderMap.of("experience", String.valueOf(result.newExperience())));
                        if (auditLogger != null) {
                            auditLogger.logAction("ADD_XP", sender.getName(), target.getUniqueId(), search.pet().petId(), "Miktar: " + xp);
                        }
                    } else {
                        send(sender, "admin.xp-add-failed", "<red>XP eklenemedi: " + result.message() + "</red>", PlaceholderMap.of("error", result.message()));
                    }
                }));
            } catch (NumberFormatException e) {
                send(sender, "admin.invalid-xp", "<red>Geçersiz XP miktarı.</red>", null);
            }
        }));
    }

    private void handleSetXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin setxp <oyuncu> <pet_id> <miktar></red>", PlaceholderMap.of("usage", "/petadmin setxp <oyuncu> <pet_id> <miktar>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() != SearchStatus.FOUND) {
                send(sender, "command.pet-not-found", "<red>Pet bulunamadı.</red>", null);
                return;
            }

            try {
                long xp = Long.parseLong(args[3]);
                CompletableFuture<ExperienceResult> future = experienceService instanceof AsyncPetExperienceService async ? async.setExperienceAsync(search.pet().petId(), xp, ExperienceSource.COMMAND) : CompletableFuture.completedFuture(experienceService.setExperience(search.pet().petId(), xp, ExperienceSource.COMMAND));

                future.thenAccept(result -> sendMessageOnMain(sender, () -> {
                    if (result.success()) {
                        send(sender, "admin.xp-set", "<green>XP güncellendi! Toplam XP: " + result.newExperience() + "</green>", PlaceholderMap.of("experience", String.valueOf(result.newExperience())));
                        if (auditLogger != null) {
                            auditLogger.logAction("SET_XP", sender.getName(), target.getUniqueId(), search.pet().petId(), "Miktar: " + xp);
                        }
                    } else {
                        send(sender, "admin.xp-set-failed", "<red>XP güncellenemedi: " + result.message() + "</red>", PlaceholderMap.of("error", result.message()));
                    }
                }));
            } catch (NumberFormatException e) {
                send(sender, "admin.invalid-xp", "<red>Geçersiz XP miktarı.</red>", null);
            }
        }));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin setlevel <oyuncu> <pet_id> <seviye></red>", PlaceholderMap.of("usage", "/petadmin setlevel <oyuncu> <pet_id> <seviye>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() != SearchStatus.FOUND) {
                send(sender, "command.pet-not-found", "<red>Pet bulunamadı.</red>", null);
                return;
            }

            try {
                int level = Integer.parseInt(args[3]);
                CompletableFuture<LevelResult> future = experienceService instanceof AsyncPetExperienceService async ? async.setLevelAsync(search.pet().petId(), level) : CompletableFuture.completedFuture(experienceService.setLevel(search.pet().petId(), level));

                future.thenAccept(result -> sendMessageOnMain(sender, () -> {
                    if (result.success()) {
                        send(sender, "admin.level-set", "<green>Seviye güncellendi! Yeni Seviye: " + result.newLevel() + "</green>", PlaceholderMap.of("level", String.valueOf(result.newLevel())));
                        if (auditLogger != null) {
                            auditLogger.logAction("SET_LEVEL", sender.getName(), target.getUniqueId(), search.pet().petId(), "Seviye: " + level);
                        }
                    } else {
                        send(sender, "admin.level-set-failed", "<red>Seviye güncellenemedi: " + result.message() + "</red>", PlaceholderMap.of("error", result.message()));
                    }
                }));
            } catch (NumberFormatException e) {
                send(sender, "admin.invalid-level", "<red>Geçersiz seviye miktarı.</red>", null);
            }
        }));
    }

    private void handleSummon(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin summon <oyuncu> <pet_id></red>", PlaceholderMap.of("usage", "/petadmin summon <oyuncu> <pet_id>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            send(sender, "admin.player-offline", "<red>Oyuncu çevrimiçi değil.</red>", null);
            return;
        }

        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() != SearchStatus.FOUND) {
                send(sender, "command.pet-not-found", "<red>Pet bulunamadı.</red>", null);
                return;
            }

            PetRuntimeOperationService opService = context != null ? context.operationService() : null;
            CompletableFuture<?> future = opService != null ? opService.summonAsync(target, search.pet().petId()) : CompletableFuture.completedFuture(petService.summon(target, search.pet().petId()));

            future.thenAccept(res -> sendMessageOnMain(sender, () -> {
                boolean success = res instanceof com.petsistemi.api.result.PetSummonResult r && r.success();
                String msg = res instanceof com.petsistemi.api.result.PetSummonResult r ? r.message() : "Çağırma başarısız.";
                if (success) {
                    send(sender, "admin.summon-success", "<green>Pet oyuncu için başarıyla çağırıldı.</green>", null);
                } else {
                    send(sender, "command.summon-failed", "<red>" + msg + "</red>", PlaceholderMap.of("error", msg));
                }
            }));
        }));
    }

    private void handleDismiss(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin.usage", "<red>Kullanım: /petadmin dismiss <oyuncu></red>", PlaceholderMap.of("usage", "/petadmin dismiss <oyuncu>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            send(sender, "admin.player-offline", "<red>Oyuncu çevrimiçi değil.</red>", null);
            return;
        }

        PetRuntimeOperationService opService = context != null ? context.operationService() : null;
        CompletableFuture<?> future = opService != null ? opService.dismissAsync(target) : CompletableFuture.completedFuture(petService.dismiss(target));

        future.thenAccept(res -> sendMessageOnMain(sender, () -> {
            send(sender, "admin.dismiss-success", "<green>Oyuncunun peti kaldırıldı.</green>", null);
        }));
    }

    private void handleReload(CommandSender sender) {
        send(sender, "admin.reload-starting", "<yellow>PetSistemi konfigürasyon ve tanımları yenileniyor...</yellow>", null);
        com.petsistemi.definition.AtomicPetDefinitionRegistry atomicRegistry =
                (definitionRegistry instanceof com.petsistemi.definition.AtomicPetDefinitionRegistry atomic) ? atomic : null;

        com.petsistemi.config.RuntimeReloadService.ReloadResult result =
                com.petsistemi.config.RuntimeReloadService.performReload(context, plugin, messageService, atomicRegistry);

        if (result.success()) {
            if (context != null && context.petService() != null) {
                new com.petsistemi.listener.PlayerProfilePrewarmListener(context.petService()).prewarmAllOnlinePlayers();
            }
            send(sender, "command.reload-success", "<green>PetSistemi yapılandırması ve pet tanımları atomik olarak hatasız yeniden yüklendi!</green>", null);
            if (auditLogger != null) {
                auditLogger.logAction("RELOAD", sender.getName(), null, null, "Atomik reload başarıyla tamamlandı");
            }
        } else {
            String suffix = result.rolledBack()
                    ? getRaw("admin.reload-rolledback", " (eski canlı konfigürasyon başarıyla geri yüklendi)")
                    : getRaw("admin.reload-rollback-failed", " (rollback tamamlanamadı; sunucu yöneticisi logları kontrol etmeli)");
            send(sender, "admin.reload-failed", "<red>Yenileme sırasında hata oluştu" + suffix + ": " + result.message() + "</red>",
                    PlaceholderMap.of("suffix", suffix).add("error", result.message()));
        }
    }

    private String getRaw(String key, String fallback) {
        if (messageService != null && messageService.currentBundle() != null) {
            return messageService.currentBundle().getMessage(key, fallback);
        }
        return fallback;
    }

    private void handleInspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-players", "<red>Bu komut sadece oyuncular tarafından kullanılabilir.</red>", null);
            return;
        }

        Entity target = player.getTargetEntity(10);
        if (target == null) {
            send(player, "admin.inspect-no-target", "<red>Baktığınız yönde varlık bulunamadı.</red>", null);
            return;
        }

        PersistentDataContainer pdc = target.getPersistentDataContainer();
        if (!pdc.has(petIdKey, PersistentDataType.STRING)) {
            send(player, "admin.inspect-not-pet", "<red>Hedef varlık bir PetSistemi peti değil.</red>", null);
            return;
        }

        String petIdStr = pdc.get(petIdKey, PersistentDataType.STRING);
        UUID petId = UUID.fromString(petIdStr);

        Optional<ActivePet> activeOpt = activeRegistry.getByEntity(target.getUniqueId());
        if (activeOpt.isEmpty()) {
            activeOpt = activeRegistry.getAllActive().stream().filter(a -> a.getPetId().equals(petId)).findFirst();
        }

        send(player, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
        send(player, "command.inspect-header", "<gold><b>=== Baktığınız Pet Varlık Denetimi ===</b></gold>", null);
        send(player, "admin.inspect-entity-type", "<gray>Entity Type: </gray><white>" + target.getType().name() + "</white>", PlaceholderMap.of("type", target.getType().name()));
        send(player, "admin.inspect-pet-id", "<gray>Pet Instance ID: </gray><yellow>" + petIdStr + "</yellow>", PlaceholderMap.of("pet_id", petIdStr));

        if (target instanceof org.bukkit.entity.LivingEntity living) {
            double hp = living.getHealth();
            org.bukkit.attribute.AttributeInstance maxAttr = living.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            double maxHp = maxAttr != null ? maxAttr.getValue() : hp;
            String healthText = String.format("%.1f / %.1f HP", hp, maxHp);
            send(player, "admin.inspect-health", "<gray>Can Durumu: </gray><green>" + healthText + "</green>", PlaceholderMap.of("health", healthText));
        }

        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            OfflinePlayer owner = Bukkit.getOfflinePlayer(activePet.getOwnerId());
            String ownerName = owner.getName() != null ? owner.getName() : owner.getUniqueId().toString();
            send(player, "admin.inspect-owner", "<gray>Sahibi: </gray><aqua>" + ownerName + "</aqua>", PlaceholderMap.of("player", ownerName));
            send(player, "admin.inspect-runtime-state", "<gray>Runtime State: </gray><green>ACTIVE</green>", null);
        } else {
            send(player, "admin.inspect-orphan", "<gray>Registry Durumu: </gray><red>YETİM / UNREGISTERED</red>", null);
        }

        String locationText = String.format("%s (%.1f, %.1f, %.1f)", target.getWorld().getName(), target.getLocation().getX(), target.getLocation().getY(), target.getLocation().getZ());
        send(player, "admin.inspect-location", "<gray>Konum: </gray><white>" + locationText + "</white>", PlaceholderMap.of("location", locationText));
        send(player, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
    }

    private void handleHealth(CommandSender sender) {
        send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
        send(sender, "command.health-header", "<gold><b>=== PetSistemi Sağlık ve Raporlama ===</b></gold>", null);

        boolean mysqlBackend = context != null && context.config() != null
                && DatabaseBackend.from(context.config().database().backend()) == DatabaseBackend.MYSQL;
        if (adminPersistenceService != null) {
            adminPersistenceService.checkHealthAsync().thenAccept(report -> sendMessageOnMain(sender, () -> {
                if (report.ok()) {
                    if ("ok".equalsIgnoreCase(report.integrity())) {
                        send(sender, "admin.health-integrity-ok", mysqlBackend
                                ? "<gray>MySQL Bağlantısı ve Şeması: </gray><green>TAM (OK)</green>"
                                : "<gray>SQLite Bütünlüğü (Integrity): </gray><green>TAM (OK)</green>", null);
                    } else {
                        send(sender, "admin.health-integrity-bad", "<gray>SQLite Bütünlüğü (Integrity): </gray><red>" + report.integrity() + "</red>", PlaceholderMap.of("value", report.integrity()));
                    }
                    if (report.fkClean()) {
                        send(sender, "admin.health-fk-ok", "<gray>Yabancı Anahtar İhlali: </gray><green>YOK (Temiz)</green>", null);
                    } else {
                        send(sender, "admin.health-fk-bad", "<gray>Yabancı Anahtar İhlali: </gray><red>İHLAL VAR!</red>", null);
                    }
                } else {
                    send(sender, "admin.health-error", "<red>Veritabanı sağlık sorgusu hatası: " + report.errorMessage() + "</red>", PlaceholderMap.of("error", report.errorMessage()));
                }
            }));
        }

        File dbFile = com.petsistemi.persistence.DatabaseManager.databaseFile(plugin);
        long dbSizeKb = dbFile.exists() ? dbFile.length() / 1024 : 0;
        long totalMemMb = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMemMb = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        long usedMemMb = totalMemMb - freeMemMb;

        if (!mysqlBackend) {
            send(sender, "admin.health-db-size", "<gray>Veritabanı Dosya Boyutu: </gray><yellow>" + dbSizeKb + " KB</yellow>", PlaceholderMap.of("size", String.valueOf(dbSizeKb)));
        }
        send(sender, "admin.health-memory", "<gray>JVM Bellek Kullanımı: </gray><aqua>" + usedMemMb + " MB / " + totalMemMb + " MB</aqua>", PlaceholderMap.of("used", String.valueOf(usedMemMb)).add("total", String.valueOf(totalMemMb)));
        send(sender, "admin.health-definitions", "<gray>Yüklü Pet Tanımları: </gray><yellow>" + definitionRegistry.getAll().size() + "</yellow>", PlaceholderMap.of("count", String.valueOf(definitionRegistry.getAll().size())));
        send(sender, "admin.health-active-pets", "<gray>Aktif Runtime Petler: </gray><green>" + activeRegistry.getAllActive().size() + "</green>", PlaceholderMap.of("count", String.valueOf(activeRegistry.getAllActive().size())));
        if (profileCache != null) {
            send(sender, "admin.health-cache", "<gray>Profil Önbellek Kayıtları: </gray><yellow>" + profileCache.size() + "</yellow>", PlaceholderMap.of("count", String.valueOf(profileCache.size())));
        }
        if (taskRegistry != null) {
            send(sender, "admin.health-tasks", "<gray>Kayıtlı Arka Plan Görevleri: </gray><light_purple>" + taskRegistry.size() + "</light_purple>", PlaceholderMap.of("count", String.valueOf(taskRegistry.size())));
        }
        send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
    }

    private void handleBackup(CommandSender sender) {
        if (adminPersistenceService == null) {
            send(sender, "admin.backup-unavailable", "<red>Yedekleme hizmeti kullanılamıyor.</red>", null);
            return;
        }
        PluginConfiguration configuration = context != null ? context.config() : null;
        if (configuration != null && DatabaseBackend.from(configuration.database().backend()) == DatabaseBackend.MYSQL) {
            send(sender, "admin.backup-mysql", "<yellow>MySQL yedekleri sunucu sağlayıcınızın snapshot/mysqldump sistemiyle alınmalıdır; dosya tabanlı /petadmin backup yalnızca SQLite içindir.</yellow>", null);
            return;
        }

        File dbFile = com.petsistemi.persistence.DatabaseManager.databaseFile(plugin);
        File backupDir = com.petsistemi.persistence.DatabaseManager.backupDirectory(plugin);
        adminPersistenceService.createBackupAsync(dbFile, backupDir, 5).thenAccept(backup -> sendMessageOnMain(sender, () -> {
            if (backup != null) {
                send(sender, "command.backup-success", "<green>WAL-safe ve doğrulanmış veritabanı yedeği alındı: " + backup.getName() + "</green>", PlaceholderMap.of("backup", backup.getName()));
                if (auditLogger != null) {
                    auditLogger.logAction("MANUAL_BACKUP", sender.getName(), null, null, "Yedek: " + backup.getName());
                }
            } else {
                send(sender, "admin.backup-failed", "<red>Yedekleme dosyası oluşturulamadı.</red>", null);
            }
        })).exceptionally(ex -> {
            sendMessageOnMain(sender, () -> send(sender, "admin.backup-error", "<red>Yedek alma hatası: " + rootMessage(ex) + "</red>", PlaceholderMap.of("error", rootMessage(ex))));
            return null;
        });
    }

    private void handleReconcile(CommandSender sender, String[] args) {
        send(sender, "admin.reconcile-starting", "<yellow>Veritabanı ve dünya pet durumları uzlaştırılıyor...</yellow>", null);
        int despawnedOrphans = 0;
        int restoredCount = 0;

        for (ActivePet activePet : activeRegistry.getAllActive()) {
            Player owner = Bukkit.getPlayer(activePet.getOwnerId());
            if (owner == null || !owner.isOnline()) {
                if (coordinator != null) coordinator.despawnRuntime(activePet.getOwnerId());
                despawnedOrphans++;
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (activeRegistry.getByOwner(online.getUniqueId()).isEmpty()) {
                if (context != null && context.operationService() != null) {
                    context.operationService().restoreSelectedPetAsync(online);
                    restoredCount++;
                }
            }
        }

        send(sender, "command.reconcile-complete", "<green>Uzlaştırma tamamlandı. Kaldırılan yetim varlık: " + despawnedOrphans + " | Restore başlatılan: " + restoredCount + "</green>",
                PlaceholderMap.of("despawned", String.valueOf(despawnedOrphans)).add("restored", String.valueOf(restoredCount)));
        if (auditLogger != null) {
            auditLogger.logAction("RECONCILE", sender.getName(), null, null, "Despawn: " + despawnedOrphans + ", Restore: " + restoredCount);
        }
    }

    private void handleDisable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin.disable-usage", "<red>Kullanım: /petadmin disable <pet_id></red>", null);
            return;
        }
        try {
            UUID petId = UUID.fromString(args[1]);
            CompletableFuture<?> future = petService instanceof AsyncPetService async ? async.disablePetAsync(petId) : CompletableFuture.completedFuture(petService.disablePet(petId));

            future.thenAccept(res -> sendMessageOnMain(sender, () -> {
                boolean success = res instanceof PetDisableResult r && r.success();
                String msg = res instanceof PetDisableResult r ? r.message() : "Devre dışı bırakma başarısız.";
                if (success) {
                    if (auditLogger != null) {
                        auditLogger.logAction("DISABLE_PET", sender.getName(), null, petId, "Pet DISABLED yapıldı");
                    }
                    send(sender, "admin.disable-success", "<green>Pet " + petId + " başarıyla devre dışı bırakıldı (DISABLED).</green>", PlaceholderMap.of("pet_id", petId.toString()));
                } else {
                    send(sender, "admin.disable-failed", "<red>Pet devre dışı bırakılamadı: " + msg + "</red>", PlaceholderMap.of("error", msg));
                }
            }));
        } catch (IllegalArgumentException e) {
            send(sender, "admin.invalid-uuid", "<red>Geçersiz UUID formatı.</red>", null);
        }
    }

    private void handleEnable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin.enable-usage", "<red>Kullanım: /petadmin enable <pet_id></red>", null);
            return;
        }
        try {
            UUID petId = UUID.fromString(args[1]);
            CompletableFuture<?> future = petService instanceof AsyncPetService async ? async.enablePetAsync(petId) : CompletableFuture.completedFuture(petService.enablePet(petId));

            future.thenAccept(res -> sendMessageOnMain(sender, () -> {
                boolean success = res instanceof PetDisableResult r && r.success();
                String msg = res instanceof PetDisableResult r ? r.message() : "Etkinleştirme başarısız.";
                if (success) {
                    if (auditLogger != null) {
                        auditLogger.logAction("ENABLE_PET", sender.getName(), null, petId, "Pet AVAILABLE yapıldı");
                    }
                    send(sender, "admin.enable-success", "<green>Pet " + petId + " başarıyla etkinleştirildi (AVAILABLE).</green>", PlaceholderMap.of("pet_id", petId.toString()));
                } else {
                    send(sender, "admin.enable-failed", "<red>Pet etkinleştirilemedi: " + msg + "</red>", PlaceholderMap.of("error", msg));
                }
            }));
        } catch (IllegalArgumentException e) {
            send(sender, "admin.invalid-uuid", "<red>Geçersiz UUID formatı.</red>", null);
        }
    }

    private void sendMessageOnMain(CommandSender sender, Runnable action) {
        if (plugin != null) {
            Bukkit.getScheduler().runTask(plugin, action);
        } else {
            action.run();
        }
    }

    private enum SearchStatus { FOUND, NOT_FOUND, AMBIGUOUS }
    private record SearchResult(SearchStatus status, PetSnapshot pet) {}

    private CompletableFuture<SearchResult> findPetByShortId(UUID ownerId, String input) {
        CompletableFuture<List<PetSnapshot>> ownedFuture = petService instanceof AsyncPetService async ? async.getOwnedPetsAsync(ownerId).thenApply(ArrayList::new) : CompletableFuture.completedFuture(new ArrayList<>(petService.getOwnedPets(ownerId)));

        return ownedFuture.thenApply(pets -> {
            List<PetSnapshot> matches = pets.stream()
                    .filter(p -> p.petId().toString().toLowerCase(java.util.Locale.ROOT).startsWith(input.toLowerCase(java.util.Locale.ROOT)))
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
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> allSubs = Arrays.asList("give", "giveall", "unlockitem", "pack", "marketplace", "remove", "list", "info", "addxp", "setxp", "setlevel", "summon", "dismiss", "reload", "editor", "inspect", "health", "backup", "reconcile", "disable", "enable", "benchmark", "vacuum");

        List<String> allowedSubs = allSubs.stream()
                .filter(sub -> sender.hasPermission("companionpets.admin") || sender.hasPermission("companionpets.admin." + sub))
                .collect(Collectors.toList());

        if (allowedSubs.isEmpty()) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return allowedSubs.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(java.util.Locale.ROOT);
            if (sub.equals("pack")) return filterPrefix(java.util.List.of("list", "install", "uninstall", "export"), args[1]);
            if (sub.equals("marketplace")) return filterPrefix(java.util.List.of("list", "refresh", "install"), args[1]);
            if (sub.equals("editor")) {
                return definitionRegistry.getAll().stream()
                        .map(PetDefinition::id)
                        .filter(id -> id.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                        .sorted()
                        .collect(Collectors.toList());
            }
            if (sub.equals("give") || sub.equals("unlockitem") || sub.equals("remove") || sub.equals("list") || sub.equals("addxp") || sub.equals("setxp") || sub.equals("setlevel") || sub.equals("summon") || sub.equals("dismiss")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase(java.util.Locale.ROOT);
            if (sub.equals("pack") && args[1].equalsIgnoreCase("uninstall") && context != null && context.petPackService() != null) {
                return filterPrefix(context.petPackService().installed().stream().map(com.petsistemi.pack.PetPackManifest::id).toList(), args[2]);
            }
            if (sub.equals("marketplace") && args[1].equalsIgnoreCase("install") && context != null && context.marketplaceService() != null) {
                return filterPrefix(context.marketplaceService().entries().stream().map(com.petsistemi.marketplace.MarketplaceEntry::id).toList(), args[2]);
            }
            if (sub.equals("give") || sub.equals("unlockitem")) {
                return definitionRegistry.getAll().stream()
                        .map(PetDefinition::id)
                        .filter(id -> id.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                        .collect(Collectors.toList());
            }
            if (sub.equals("remove") || sub.equals("addxp") || sub.equals("setxp") || sub.equals("setlevel") || sub.equals("summon")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    return petService.getOwnedPets(target.getUniqueId()).stream()
                            .map(p -> p.petId().toString().substring(0, 6))
                            .filter(id -> id.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("unlockitem")) {
            return Arrays.asList("1", "8", "16", "32", "64").stream()
                    .filter(value -> value.startsWith(args[3])).collect(Collectors.toList());
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("unlockitem")) {
            String prefix = args[4].toUpperCase(java.util.Locale.ROOT);
            return Arrays.stream(Material.values()).filter(material -> material.isItem() && !material.isAir())
                    .map(Material::name).filter(name -> name.startsWith(prefix)).limit(50).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private static List<String> filterPrefix(Collection<String> values, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(java.util.Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(lower)).sorted().toList();
    }

    private void handleBenchmark(CommandSender sender) {
        send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
        send(sender, "admin.benchmark-starting", "<yellow>📊 PetSistemi Performans Benchmark Başlatılıyor...</yellow>", null);

        long startDb = System.nanoTime();
        boolean dbOk = false;
        if (connectionProvider != null) {
            try (java.sql.Connection conn = connectionProvider.getConnection()) {
                dbOk = conn != null && !conn.isClosed() && conn.isValid(2);
            } catch (Exception ignored) {}
        }
        double dbMs = (System.nanoTime() - startDb) / 1_000_000.0;

        int activeCount = activeRegistry != null ? activeRegistry.getAllActive().size() : 0;
        int defCount = definitionRegistry != null ? definitionRegistry.getAll().size() : 0;

        long startCoord = System.nanoTime();
        if (coordinator != null) {
            coordinator.tickAll();
        }
        double coordMs = (System.nanoTime() - startCoord) / 1_000_000.0;

        String report = "<gold>=== Benchmark Sonuçları ===</gold>" +
                "<newline><gray>● Veritabanı Yanıt Süresi (DB Ping): </gray><green>" + String.format("%.2f", dbMs) + " ms</green> <gray>(" + (dbOk ? "OK" : "N/A") + ")</gray>" +
                "<newline><gray>● Runtime Tick İşleme Süresi: </gray><aqua>" + String.format("%.2f", coordMs) + " ms</aqua>" +
                "<newline><gray>● Yüklü Tanımlar: </gray><yellow>" + defCount + "</yellow>" +
                "<newline><gray>● Aktif Canlı Petler: </gray><green>" + activeCount + "</green>";

        sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(report));
        send(sender, "admin.divider", "<dark_gray>-----------------------------------------</dark_gray>", null);
    }

    private void handleVacuum(CommandSender sender) {
        if (adminPersistenceService == null) {
            send(sender, "admin.vacuum-failed", "<red>Veritabanı servisleri aktif değil.</red>", null);
            return;
        }
        boolean mysqlBackend = context != null && context.config() != null
                && DatabaseBackend.from(context.config().database().backend()) == DatabaseBackend.MYSQL;
        send(sender, "admin.vacuum-starting", mysqlBackend
                ? "<yellow>⚡ MySQL ANALYZE TABLE optimizasyonu başlatılıyor...</yellow>"
                : "<yellow>⚡ Veritabanı VACUUM & ANALYZE optimizasyonu başlatılıyor...</yellow>", null);
        adminPersistenceService.vacuumDatabaseAsync().thenAccept(success -> sendMessageOnMain(sender, () -> {
            if (success) {
                send(sender, "admin.vacuum-success", mysqlBackend
                        ? "<green>✔ MySQL tablo istatistikleri başarıyla güncellendi!</green>"
                        : "<green>✔ Veritabanı optimizasyonu (VACUUM & ANALYZE) başarıyla tamamlandı!</green>", null);
            } else {
                send(sender, "admin.vacuum-failed", "<red>✖ Veritabanı optimizasyonu çalıştırılamadı.</red>", null);
            }
        }));
    }
}
