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
import com.petsistemi.persistence.*;
import com.petsistemi.persistence.migration.MigrationBackupManager;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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
    private final AdminPersistenceService adminPersistenceService;
    private final AuditLogger auditLogger;
    private final PetRuntimeCoordinator coordinator;
    private final PlayerPetProfileCache profileCache;
    private final MessageService messageService;
    private final com.petsistemi.bootstrap.TaskRegistry taskRegistry;
    private final com.petsistemi.bootstrap.PetPluginContext context;

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
        this.adminPersistenceService = (context != null && context.dbExecutor() != null && connectionProvider != null)
                ? new AdminPersistenceService(context.dbExecutor(), connectionProvider, plugin != null ? plugin.getLogger() : java.util.logging.Logger.getLogger("PetAdminCommand"))
                : null;
        this.auditLogger = auditLogger;
        this.coordinator = coordinator;
        this.profileCache = profileCache;
        this.messageService = messageService;
        this.taskRegistry = taskRegistry;
        this.context = context;

        this.petIdKey = plugin != null ? new NamespacedKey(plugin, "pet_id") : null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!checkPerm(sender, "companionpets.admin")) return true;
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give" -> {
                if (!checkPerm(sender, "companionpets.admin.give")) return true;
                handleGive(sender, args);
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
            default -> sendHelp(sender);
        }

        return true;
    }

    private boolean checkPerm(CommandSender sender, String perm) {
        if (sender.hasPermission("companionpets.admin") || sender.hasPermission(perm)) {
            return true;
        }
        sender.sendMessage(Component.text("Bu komut için yetkiniz yok: " + perm, NamedTextColor.RED));
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("=== PetSistemi Yönetici Komutları ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sendHelpLine(sender, "/petadmin give <oyuncu> <tur_id>",         "Pet ver");
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
        sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void sendHelpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(
                Component.text("  ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(cmd, NamedTextColor.YELLOW))
                        .append(Component.text(" — " + desc, NamedTextColor.GRAY))
        );
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Kullanım: /petadmin give <oyuncu> <tur_id>", NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String defId = args[2].toLowerCase();
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);

        CompletableFuture<PetGiveResult> future = petService instanceof AsyncPetService async ? async.givePetAsync(target.getUniqueId(), defId) : CompletableFuture.completedFuture(petService.givePet(target.getUniqueId(), defId));

        future.thenAccept(result -> sendMessageOnMain(sender, () -> {
            if (result.success() && result.petSnapshot() != null) {
                UUID petId = result.petSnapshot().petId();
                sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
                sender.sendMessage(Component.text("✔ Pet başarıyla verildi!", NamedTextColor.GREEN, TextDecoration.BOLD));
                sender.sendMessage(Component.text("  Oyuncu: ", NamedTextColor.GRAY).append(Component.text(targetName, NamedTextColor.AQUA)));
                sender.sendMessage(Component.text("  Tür: ", NamedTextColor.GRAY).append(Component.text(defId.toUpperCase(), NamedTextColor.GOLD)));
                sender.sendMessage(Component.text("  Pet ID: ", NamedTextColor.GRAY).append(Component.text(petId.toString(), NamedTextColor.YELLOW)));
                sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
                if (auditLogger != null) {
                    auditLogger.logAction("GIVE_PET", sender.getName(), target.getUniqueId(), petId, "Tür: " + defId);
                }
            } else {
                sender.sendMessage(Component.text("✖ Pet verilemedi: ", NamedTextColor.RED)
                        .append(Component.text(result.message(), NamedTextColor.DARK_RED)));
            }
        }));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Kullanım: /petadmin remove <oyuncu> <pet_id>", NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() == SearchStatus.NOT_FOUND) {
                sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
                return;
            } else if (search.status() == SearchStatus.AMBIGUOUS) {
                sender.sendMessage(Component.text("Birden fazla pet eşleşti! Lütfen tam UUID yazın.", NamedTextColor.RED));
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
                    sender.sendMessage(Component.text("Pet başarıyla silindi.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Pet silinemedi: " + msg, NamedTextColor.RED));
                }
            }));
        }));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin list <oyuncu>", NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);

        CompletableFuture<List<PetSnapshot>> future = petService instanceof AsyncPetService async ? async.getOwnedPetsAsync(target.getUniqueId()).thenApply(ArrayList::new) : CompletableFuture.completedFuture(new ArrayList<>(petService.getOwnedPets(target.getUniqueId())));

        future.thenAccept(pets -> sendMessageOnMain(sender, () -> {
            sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
            sender.sendMessage(Component.text("=== " + targetName + " — Pet Listesi (", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(pets.size(), NamedTextColor.YELLOW))
                    .append(Component.text(" pet) ===", NamedTextColor.GOLD, TextDecoration.BOLD)));

            if (pets.isEmpty()) {
                sender.sendMessage(Component.text("  Bu oyuncunun hiç peti yok.", NamedTextColor.GRAY));
            } else {
                for (PetSnapshot pet : pets) {
                    String name = pet.customName() != null ? pet.customName() : pet.definitionId();
                    NamedTextColor stateColor = pet.availabilityState() == PetAvailabilityState.DISABLED
                            ? NamedTextColor.RED : NamedTextColor.GREEN;
                    String stateLabel = pet.availabilityState() == PetAvailabilityState.DISABLED
                            ? "KAPALI" : (pet.spawned() ? "AKTIF" : "HAZIR");
                    sender.sendMessage(
                            Component.text("  ● ", NamedTextColor.GOLD)
                                    .append(Component.text("[" + pet.definitionId().toUpperCase() + "] ", NamedTextColor.LIGHT_PURPLE))
                                    .append(Component.text(name, NamedTextColor.WHITE))
                                    .append(Component.text(" Lv." + pet.level(), NamedTextColor.YELLOW))
                                    .append(Component.text(" [" + stateLabel + "]", stateColor))
                                    .append(Component.text(" #" + pet.petId().toString().substring(0, 8), NamedTextColor.DARK_GRAY))
                    );
                }
            }
            sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
        }));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin info <oyuncu>", NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        CompletableFuture<List<PetSnapshot>> ownedFuture = petService instanceof AsyncPetService async ? async.getOwnedPetsAsync(target.getUniqueId()).thenApply(ArrayList::new) : CompletableFuture.completedFuture(new ArrayList<>(petService.getOwnedPets(target.getUniqueId())));
        CompletableFuture<Optional<PetSnapshot>> selectedFuture = petService instanceof AsyncPetService async ? async.getSelectedPetAsync(target.getUniqueId()) : CompletableFuture.completedFuture(petService.getSelectedPet(target.getUniqueId()));

        ownedFuture.thenCombine(selectedFuture, (owned, selected) -> {
            sendMessageOnMain(sender, () -> {
                Optional<PetSnapshot> spawned = owned.stream().filter(PetSnapshot::spawned).findFirst();

                sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
                sender.sendMessage(Component.text("=== Oyuncu Pet Raporu: " + (target.getName() != null ? target.getName() : target.getUniqueId()) + " ===", NamedTextColor.GOLD, TextDecoration.BOLD));
                sender.sendMessage(Component.text("Oyuncu UUID: ", NamedTextColor.GRAY).append(Component.text(target.getUniqueId().toString(), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("Toplam Pet Sayısı: ", NamedTextColor.GRAY).append(Component.text(owned.size(), NamedTextColor.YELLOW)));
                sender.sendMessage(Component.text("Seçili Pet ID: ", NamedTextColor.GRAY).append(Component.text(selected.map(s -> s.petId().toString().substring(0, 8)).orElse("Yok"), NamedTextColor.AQUA)));
                sender.sendMessage(Component.text("Dünyada Spawned: ", NamedTextColor.GRAY).append(Component.text(spawned.map(s -> s.petId().toString().substring(0, 8)).orElse("Yok"), NamedTextColor.GREEN)));
                sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
                for (PetSnapshot pet : owned) {
                    String name = pet.customName() != null ? pet.customName() : pet.definitionId();
                    sender.sendMessage(Component.text("- [" + pet.definitionId() + "] ", NamedTextColor.GOLD)
                            .append(Component.text(name, NamedTextColor.WHITE))
                            .append(Component.text(" (Lv." + pet.level() + ", " + pet.experience() + " XP) ", NamedTextColor.YELLOW))
                            .append(Component.text("[" + pet.availabilityState() + "]", pet.availabilityState() == PetAvailabilityState.DISABLED ? NamedTextColor.RED : NamedTextColor.GREEN))
                            .append(Component.text(" ID: " + pet.petId().toString().substring(0, 8), NamedTextColor.DARK_GRAY)));
                }
                sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
            });
            return null;
        });
    }

    private void handleAddXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Kullanım: /petadmin addxp <oyuncu> <pet_id> <miktar>", NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() != SearchStatus.FOUND) {
                sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
                return;
            }

            try {
                long xp = Long.parseLong(args[3]);
                CompletableFuture<ExperienceResult> future = experienceService instanceof AsyncPetExperienceService async ? async.addExperienceAsync(search.pet().petId(), xp, ExperienceSource.COMMAND) : CompletableFuture.completedFuture(experienceService.addExperience(search.pet().petId(), xp, ExperienceSource.COMMAND));

                future.thenAccept(result -> sendMessageOnMain(sender, () -> {
                    if (result.success()) {
                        sender.sendMessage(Component.text("XP eklendi! Toplam XP: " + result.newExperience(), NamedTextColor.GREEN));
                        if (auditLogger != null) {
                            auditLogger.logAction("ADD_XP", sender.getName(), target.getUniqueId(), search.pet().petId(), "Miktar: " + xp);
                        }
                    } else {
                        sender.sendMessage(Component.text("XP eklenemedi: " + result.message(), NamedTextColor.RED));
                    }
                }));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Geçersiz XP miktarı.", NamedTextColor.RED));
            }
        }));
    }

    private void handleSetXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Kullanım: /petadmin setxp <oyuncu> <pet_id> <miktar>", NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() != SearchStatus.FOUND) {
                sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
                return;
            }

            try {
                long xp = Long.parseLong(args[3]);
                CompletableFuture<ExperienceResult> future = experienceService instanceof AsyncPetExperienceService async ? async.setExperienceAsync(search.pet().petId(), xp, ExperienceSource.COMMAND) : CompletableFuture.completedFuture(experienceService.setExperience(search.pet().petId(), xp, ExperienceSource.COMMAND));

                future.thenAccept(result -> sendMessageOnMain(sender, () -> {
                    if (result.success()) {
                        sender.sendMessage(Component.text("XP güncellendi! Toplam XP: " + result.newExperience(), NamedTextColor.GREEN));
                        if (auditLogger != null) {
                            auditLogger.logAction("SET_XP", sender.getName(), target.getUniqueId(), search.pet().petId(), "Miktar: " + xp);
                        }
                    } else {
                        sender.sendMessage(Component.text("XP güncellenemedi: " + result.message(), NamedTextColor.RED));
                    }
                }));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Geçersiz XP miktarı.", NamedTextColor.RED));
            }
        }));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Kullanım: /petadmin setlevel <oyuncu> <pet_id> <seviye>", NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() != SearchStatus.FOUND) {
                sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
                return;
            }

            try {
                int level = Integer.parseInt(args[3]);
                CompletableFuture<LevelResult> future = experienceService instanceof AsyncPetExperienceService async ? async.setLevelAsync(search.pet().petId(), level) : CompletableFuture.completedFuture(experienceService.setLevel(search.pet().petId(), level));

                future.thenAccept(result -> sendMessageOnMain(sender, () -> {
                    if (result.success()) {
                        sender.sendMessage(Component.text("Seviye güncellendi! Yeni Seviye: " + result.newLevel(), NamedTextColor.GREEN));
                        if (auditLogger != null) {
                            auditLogger.logAction("SET_LEVEL", sender.getName(), target.getUniqueId(), search.pet().petId(), "Seviye: " + level);
                        }
                    } else {
                        sender.sendMessage(Component.text("Seviye güncellenemedi: " + result.message(), NamedTextColor.RED));
                    }
                }));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Geçersiz seviye miktarı.", NamedTextColor.RED));
            }
        }));
    }

    private void handleSummon(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Kullanım: /petadmin summon <oyuncu> <pet_id>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("Oyuncu çevrimiçi değil.", NamedTextColor.RED));
            return;
        }

        findPetByShortId(target.getUniqueId(), args[2]).thenAccept(search -> sendMessageOnMain(sender, () -> {
            if (search.status() != SearchStatus.FOUND) {
                sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
                return;
            }

            PetRuntimeOperationService opService = context != null ? context.operationService() : null;
            CompletableFuture<?> future = opService != null ? opService.summonAsync(target, search.pet().petId()) : CompletableFuture.completedFuture(petService.summon(target, search.pet().petId()));

            future.thenAccept(res -> sendMessageOnMain(sender, () -> {
                boolean success = res instanceof com.petsistemi.api.result.PetSummonResult r && r.success();
                String msg = res instanceof com.petsistemi.api.result.PetSummonResult r ? r.message() : "Çağırma başarısız.";
                if (success) {
                    sender.sendMessage(Component.text("Pet oyuncu için başarıyla çağırıldı.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Pet çağırılamadı: " + msg, NamedTextColor.RED));
                }
            }));
        }));
    }

    private void handleDismiss(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin dismiss <oyuncu>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("Oyuncu çevrimiçi değil.", NamedTextColor.RED));
            return;
        }

        PetRuntimeOperationService opService = context != null ? context.operationService() : null;
        CompletableFuture<?> future = opService != null ? opService.dismissAsync(target) : CompletableFuture.completedFuture(petService.dismiss(target));

        future.thenAccept(res -> sendMessageOnMain(sender, () -> {
            sender.sendMessage(Component.text("Oyuncunun peti kaldırıldı.", NamedTextColor.GREEN));
        }));
    }

    private void handleReload(CommandSender sender) {
        sender.sendMessage(Component.text("PetSistemi konfigürasyon ve tanımları yenileniyor...", NamedTextColor.YELLOW));
        com.petsistemi.definition.AtomicPetDefinitionRegistry atomicRegistry =
                (definitionRegistry instanceof com.petsistemi.definition.AtomicPetDefinitionRegistry atomic) ? atomic : null;

        com.petsistemi.config.RuntimeReloadService.ReloadResult result =
                com.petsistemi.config.RuntimeReloadService.performReload(context, plugin, messageService, atomicRegistry);

        if (result.success()) {
            sender.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
            if (auditLogger != null) {
                auditLogger.logAction("RELOAD", sender.getName(), null, null, "Atomik reload başarıyla tamamlandı");
            }
        } else {
            String suffix = result.rolledBack()
                    ? " (eski canlı konfigürasyon başarıyla geri yüklendi)"
                    : " (rollback tamamlanamadı; sunucu yöneticisi logları kontrol etmeli)";
            sender.sendMessage(Component.text("Yenileme sırasında hata oluştu" + suffix + ": " + result.message(), NamedTextColor.RED));
        }
    }

    private void handleInspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Bu komut sadece oyunda kullanılabilir.", NamedTextColor.RED));
            return;
        }

        Entity target = player.getTargetEntity(10);
        if (target == null) {
            player.sendMessage(Component.text("Baktığınız yönde varlık bulunamadı.", NamedTextColor.RED));
            return;
        }

        PersistentDataContainer pdc = target.getPersistentDataContainer();
        if (!pdc.has(petIdKey, PersistentDataType.STRING)) {
            player.sendMessage(Component.text("Hedef varlık bir PetSistemi peti değil.", NamedTextColor.RED));
            return;
        }

        String petIdStr = pdc.get(petIdKey, PersistentDataType.STRING);
        UUID petId = UUID.fromString(petIdStr);

        Optional<ActivePet> activeOpt = activeRegistry.getByEntity(target.getUniqueId());
        if (activeOpt.isEmpty()) {
            activeOpt = activeRegistry.getAllActive().stream().filter(a -> a.getPetId().equals(petId)).findFirst();
        }

        player.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("=== Baktığınız Pet Varlık Denetimi ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("Entity Type: ", NamedTextColor.GRAY).append(Component.text(target.getType().name(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Pet Instance ID: ", NamedTextColor.GRAY).append(Component.text(petIdStr, NamedTextColor.YELLOW)));

        if (target instanceof org.bukkit.entity.LivingEntity living) {
            double hp = living.getHealth();
            org.bukkit.attribute.AttributeInstance maxAttr = living.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            double maxHp = maxAttr != null ? maxAttr.getValue() : hp;
            player.sendMessage(Component.text("Can Durumu: ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f / %.1f HP", hp, maxHp), NamedTextColor.GREEN)));
        }

        if (activeOpt.isPresent()) {
            ActivePet activePet = activeOpt.get();
            OfflinePlayer owner = Bukkit.getOfflinePlayer(activePet.getOwnerId());
            player.sendMessage(Component.text("Sahibi: ", NamedTextColor.GRAY).append(Component.text(owner.getName() != null ? owner.getName() : owner.getUniqueId().toString(), NamedTextColor.AQUA)));
            player.sendMessage(Component.text("Runtime State: ", NamedTextColor.GRAY).append(Component.text("ACTIVE", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("Registry Durumu: ", NamedTextColor.GRAY).append(Component.text("YETİM / UNREGISTERED", NamedTextColor.RED)));
        }

        player.sendMessage(Component.text("Konum: ", NamedTextColor.GRAY).append(Component.text(String.format("%s (%.1f, %.1f, %.1f)", target.getWorld().getName(), target.getLocation().getX(), target.getLocation().getY(), target.getLocation().getZ()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void handleHealth(CommandSender sender) {
        sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("=== PetSistemi Sağlık ve Raporlama ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        if (adminPersistenceService != null) {
            adminPersistenceService.checkHealthAsync().thenAccept(report -> sendMessageOnMain(sender, () -> {
                if (report.ok()) {
                    sender.sendMessage(Component.text("SQLite Bütünlüğü (Integrity): ", NamedTextColor.GRAY).append(Component.text("ok".equalsIgnoreCase(report.integrity()) ? "TAM (OK)" : report.integrity(), "ok".equalsIgnoreCase(report.integrity()) ? NamedTextColor.GREEN : NamedTextColor.RED)));
                    sender.sendMessage(Component.text("Yabancı Anahtar İhlali: ", NamedTextColor.GRAY).append(Component.text(report.fkClean() ? "YOK (Temiz)" : "İHLAL VAR!", report.fkClean() ? NamedTextColor.GREEN : NamedTextColor.RED)));
                } else {
                    sender.sendMessage(Component.text("Veritabanı sağlık sorgusu hatası: " + report.errorMessage(), NamedTextColor.RED));
                }
            }));
        }

        File dbFile = new File(plugin.getDataFolder(), "database.db");
        long dbSizeKb = dbFile.exists() ? dbFile.length() / 1024 : 0;
        long totalMemMb = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMemMb = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        long usedMemMb = totalMemMb - freeMemMb;

        sender.sendMessage(Component.text("Veritabanı Dosya Boyutu: ", NamedTextColor.GRAY).append(Component.text(dbSizeKb + " KB", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("JVM Bellek Kullanımı: ", NamedTextColor.GRAY).append(Component.text(usedMemMb + " MB / " + totalMemMb + " MB", NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("Yüklü Pet Tanımları: ", NamedTextColor.GRAY).append(Component.text(definitionRegistry.getAll().size(), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Aktif Runtime Petler: ", NamedTextColor.GRAY).append(Component.text(activeRegistry.getAllActive().size(), NamedTextColor.GREEN)));
        if (profileCache != null) {
            sender.sendMessage(Component.text("Profil Önbellek Kayıtları: ", NamedTextColor.GRAY).append(Component.text(profileCache.size(), NamedTextColor.YELLOW)));
        }
        if (taskRegistry != null) {
            sender.sendMessage(Component.text("Kayıtlı Arka Plan Görevleri: ", NamedTextColor.GRAY).append(Component.text(taskRegistry.size(), NamedTextColor.LIGHT_PURPLE)));
        }
        sender.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void handleBackup(CommandSender sender) {
        if (adminPersistenceService == null) {
            sender.sendMessage(Component.text("Yedekleme hizmeti kullanılamıyor.", NamedTextColor.RED));
            return;
        }

        File dbFile = new File(plugin.getDataFolder(), "database.db");
        File backupDir = new File(plugin.getDataFolder(), "database-backups");
        adminPersistenceService.createBackupAsync(dbFile, backupDir, 5).thenAccept(backup -> sendMessageOnMain(sender, () -> {
            if (backup != null) {
                sender.sendMessage(Component.text("WAL-safe ve doğrulanmış veritabanı yedeği alındı: " + backup.getName(), NamedTextColor.GREEN));
                if (auditLogger != null) {
                    auditLogger.logAction("MANUAL_BACKUP", sender.getName(), null, null, "Yedek: " + backup.getName());
                }
            } else {
                sender.sendMessage(Component.text("Yedekleme dosyası oluşturulamadı.", NamedTextColor.RED));
            }
        })).exceptionally(ex -> {
            sender.sendMessage(Component.text("Yedek alma hatası: " + ex.getMessage(), NamedTextColor.RED));
            return null;
        });
    }

    private void handleReconcile(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("Veritabanı ve dünya pet durumları uzlaştırılıyor...", NamedTextColor.YELLOW));
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

        sender.sendMessage(Component.text("Uzlaştırma tamamlandı. Kaldırılan yetim varlık: " + despawnedOrphans + " | Restore başlatılan: " + restoredCount, NamedTextColor.GREEN));
        if (auditLogger != null) {
            auditLogger.logAction("RECONCILE", sender.getName(), null, null, "Despawn: " + despawnedOrphans + ", Restore: " + restoredCount);
        }
    }

    private void handleDisable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin disable <pet_id>", NamedTextColor.RED));
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
                    sender.sendMessage(Component.text("Pet " + petId + " başarıyla devre dışı bırakıldı (DISABLED).", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Pet devre dışı bırakılamadı: " + msg, NamedTextColor.RED));
                }
            }));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Geçersiz UUID formatı.", NamedTextColor.RED));
        }
    }

    private void handleEnable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin enable <pet_id>", NamedTextColor.RED));
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
                    sender.sendMessage(Component.text("Pet " + petId + " başarıyla etkinleştirildi (AVAILABLE).", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Pet etkinleştirilemedi: " + msg, NamedTextColor.RED));
                }
            }));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Geçersiz UUID formatı.", NamedTextColor.RED));
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
                    .filter(p -> p.petId().toString().toLowerCase().startsWith(input.toLowerCase()))
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
        List<String> allSubs = Arrays.asList("give", "remove", "list", "info", "addxp", "setxp", "setlevel", "summon", "dismiss", "reload", "inspect", "health", "backup", "reconcile", "disable", "enable");

        List<String> allowedSubs = allSubs.stream()
                .filter(sub -> sender.hasPermission("companionpets.admin") || sender.hasPermission("companionpets.admin." + sub))
                .collect(Collectors.toList());

        if (allowedSubs.isEmpty()) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return allowedSubs.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("remove") || sub.equals("list") || sub.equals("addxp") || sub.equals("setxp") || sub.equals("setlevel") || sub.equals("summon") || sub.equals("dismiss")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give")) {
                return definitionRegistry.getAll().stream()
                        .map(PetDefinition::id)
                        .filter(id -> id.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("remove") || sub.equals("addxp") || sub.equals("setxp") || sub.equals("setlevel") || sub.equals("summon")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    return petService.getOwnedPets(target.getUniqueId()).stream()
                            .map(p -> p.petId().toString().substring(0, 6))
                            .filter(id -> id.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }
}
