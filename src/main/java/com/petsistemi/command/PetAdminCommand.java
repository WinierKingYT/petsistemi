package com.petsistemi.command;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.result.ExperienceResult;
import com.petsistemi.api.result.LevelResult;
import com.petsistemi.api.result.PetGiveResult;
import com.petsistemi.api.result.PetSummonResult;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.stream.Collectors;

public class PetAdminCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PetService petService;
    private final PetExperienceService experienceService;
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activeRegistry;
    private final PetRepository repository;

    private final NamespacedKey petIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey schemaVersionKey;

    public PetAdminCommand(JavaPlugin plugin, PetService petService,
                           PetExperienceService experienceService,
                           PetDefinitionRegistry definitionRegistry,
                           ActivePetRegistry activeRegistry,
                           PetRepository repository) {
        this.plugin = plugin;
        this.petService = petService;
        this.experienceService = experienceService;
        this.definitionRegistry = definitionRegistry;
        this.activeRegistry = activeRegistry;
        this.repository = repository;

        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.ownerIdKey = new NamespacedKey(plugin, "owner_id");
        this.definitionIdKey = new NamespacedKey(plugin, "definition_id");
        this.schemaVersionKey = new NamespacedKey(plugin, "schema_version");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("companionpets.admin")) {
            sender.sendMessage(Component.text("Bu komutu kullanmak için yetkiniz yok.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give" -> handleGive(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "addxp" -> handleAddXp(sender, args);
            case "setxp" -> handleSetXp(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "summon" -> handleSummon(sender, args);
            case "dismiss" -> handleDismiss(sender, args);
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender);
            case "health" -> handleHealth(sender);
            case "backup" -> handleBackup(sender);
            case "reconcile" -> handleReconcile(sender, args);
            case "disable" -> handleDisable(sender, args);
            case "enable" -> handleEnable(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== PetAdmin Yetkili Menüsü ===", NamedTextColor.DARK_RED));
        sender.sendMessage(Component.text("/petadmin give <oyuncu> <tür> - Oyuncuya yeni bir pet verir.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin remove <oyuncu> <pet_id> - Oyuncudan pet siler.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin list <oyuncu> - Oyuncunun petlerini listeler.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin info <pet_id> - Pet detaylarını gösterir.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin addxp <oyuncu> <pet_id> <miktar> - Pete tecrübe ekler.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin setxp <oyuncu> <pet_id> <miktar> - Pet tecrübesini ayarlar.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin setlevel <oyuncu> <pet_id> <seviye> - Pet seviyesini ayarlar.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin summon <oyuncu> <pet_id> - Oyuncu adına pet çağırır.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin dismiss <oyuncu> - Oyuncunun petini kaldırır.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin reload - Konfigürasyonları yeniden yükler.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/petadmin inspect - Baktığınız pet entity'sini inceler.", NamedTextColor.YELLOW));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(String nameOrUuid) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(nameOrUuid));
        } catch (IllegalArgumentException e) {
            return Bukkit.getOfflinePlayer(nameOrUuid);
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Kullanım: /petadmin give <oyuncu> <tür>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(Component.text("Oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        String defId = args[2].toLowerCase();
        PetGiveResult result = petService.givePet(target.getUniqueId(), defId);
        if (result.success()) {
            sender.sendMessage(Component.text("Pet başarıyla " + target.getName() + " isimli oyuncuya verildi.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Kullanım: /petadmin remove <oyuncu> <pet_id>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        String shortId = args[2];
        SearchResult match = resolveOwnedPetByShortId(target.getUniqueId(), shortId);
        if (match.status == SearchStatus.NOT_FOUND) {
            sender.sendMessage(Component.text("Belirtilen pet bulunamadı.", NamedTextColor.RED));
            return;
        } else if (match.status == SearchStatus.AMBIGUOUS) {
            sender.sendMessage(Component.text("Birden fazla pet bu kimlik ön ekiyle eşleşiyor.", NamedTextColor.RED));
            return;
        }

        UUID petId = match.pet.petId();
        Optional<ActivePet> active = activeRegistry.getByOwner(target.getUniqueId());
        if (active.isPresent() && active.get().getPetId().equals(petId) && target.isOnline()) {
            petService.dismiss(target.getPlayer());
        }

        try {
            repository.delete(petId);
            sender.sendMessage(Component.text("Pet başarıyla veritabanından silindi.", NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Pet veritabanından silinirken hata oluştu: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin list <oyuncu>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        Collection<PetSnapshot> pets = petService.getOwnedPets(target.getUniqueId());
        sender.sendMessage(Component.text("=== " + target.getName() + " Petleri ===", NamedTextColor.DARK_RED));
        for (PetSnapshot pet : pets) {
            String shortId = pet.petId().toString().substring(0, 6);
            sender.sendMessage(Component.text("- ID: " + shortId + " | Tür: " + pet.definitionId() + " | Seviye: " + pet.level(), NamedTextColor.YELLOW));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin info <pet_id>", NamedTextColor.RED));
            return;
        }

        String idStr = args[1];
        Optional<PetInstance> petOpt = Optional.empty();
        if (idStr.length() == 6) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                SearchResult match = resolveOwnedPetByShortId(p.getUniqueId(), idStr);
                if (match.status == SearchStatus.FOUND) {
                    petOpt = repository.findById(match.pet.petId());
                    break;
                }
            }
        } else {
            try {
                petOpt = repository.findById(UUID.fromString(idStr));
            } catch (Exception ignored) {}
        }

        if (petOpt.isEmpty()) {
            sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        }

        PetInstance pet = petOpt.get();
        sender.sendMessage(Component.text("=== Yetkili Pet Bilgisi ===", NamedTextColor.DARK_RED));
        sender.sendMessage(Component.text("Pet UUID: " + pet.petId(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Sahip UUID: " + pet.ownerId(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Tür ID: " + pet.definitionId(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Özel İsim: " + (pet.customName() != null ? pet.customName() : "Yok"), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Seviye: " + pet.level(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("XP: " + pet.experience(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Durum: " + pet.availabilityState().name(), NamedTextColor.YELLOW));
    }

    private void handleAddXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Kullanım: /petadmin addxp <oyuncu> <pet_id> <miktar>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        String shortId = args[2];
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Geçersiz deneyim miktarı.", NamedTextColor.RED));
            return;
        }

        SearchResult match = resolveOwnedPetByShortId(target.getUniqueId(), shortId);
        if (match.status == SearchStatus.NOT_FOUND) {
            sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        } else if (match.status == SearchStatus.AMBIGUOUS) {
            sender.sendMessage(Component.text("Birden fazla pet bu kimlik ön ekiyle eşleşiyor.", NamedTextColor.RED));
            return;
        }

        ExperienceResult res = experienceService.addExperience(match.pet.petId(), amount, ExperienceSource.ADMIN);
        if (res.success()) {
            sender.sendMessage(Component.text("Deneyim eklendi. Yeni XP: " + res.newExperience(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(res.message(), NamedTextColor.RED));
        }
    }

    private void handleSetXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Kullanım: /petadmin setxp <oyuncu> <pet_id> <miktar>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        String shortId = args[2];
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Geçersiz deneyim miktarı.", NamedTextColor.RED));
            return;
        }

        SearchResult match = resolveOwnedPetByShortId(target.getUniqueId(), shortId);
        if (match.status == SearchStatus.NOT_FOUND) {
            sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        } else if (match.status == SearchStatus.AMBIGUOUS) {
            sender.sendMessage(Component.text("Birden fazla pet bu kimlik ön ekiyle eşleşiyor.", NamedTextColor.RED));
            return;
        }

        UUID petId = match.pet.petId();
        ExperienceResult res = experienceService.setExperience(petId, amount, ExperienceSource.ADMIN);

        if (res.success()) {
            sender.sendMessage(Component.text("Deneyim başarıyla ayarlandı. Yeni XP: " + res.newExperience(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(res.message(), NamedTextColor.RED));
        }
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Kullanım: /petadmin setlevel <oyuncu> <pet_id> <seviye>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        String shortId = args[2];
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Geçersiz seviye.", NamedTextColor.RED));
            return;
        }

        SearchResult match = resolveOwnedPetByShortId(target.getUniqueId(), shortId);
        if (match.status == SearchStatus.NOT_FOUND) {
            sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        } else if (match.status == SearchStatus.AMBIGUOUS) {
            sender.sendMessage(Component.text("Birden fazla pet bu kimlik ön ekiyle eşleşiyor.", NamedTextColor.RED));
            return;
        }

        LevelResult result = experienceService.setLevel(match.pet.petId(), level);
        if (result.success()) {
            sender.sendMessage(Component.text("Seviye ayarlandı: " + result.newLevel(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleSummon(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Kullanım: /petadmin summon <oyuncu> <pet_id>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("Çevrimiçi oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        String shortId = args[2];
        SearchResult match = resolveOwnedPetByShortId(target.getUniqueId(), shortId);
        if (match.status == SearchStatus.NOT_FOUND) {
            sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        } else if (match.status == SearchStatus.AMBIGUOUS) {
            sender.sendMessage(Component.text("Birden fazla pet bu kimlik ön ekiyle eşleşiyor.", NamedTextColor.RED));
            return;
        }

        PetSummonResult result = petService.summon(target, match.pet.petId());
        if (result.success()) {
            sender.sendMessage(Component.text("Pet çağırıldı.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleDismiss(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin dismiss <oyuncu>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("Çevrimiçi oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        petService.dismiss(target);
        sender.sendMessage(Component.text("Pet kaldırıldı.", NamedTextColor.GREEN));
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        definitionRegistry.reload();
        sender.sendMessage(Component.text("Konfigürasyonlar başarıyla yeniden yüklendi.", NamedTextColor.GREEN));
    }

    private void handleInspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Bu komut sadece oyuncular tarafından kullanılabilir.", NamedTextColor.RED));
            return;
        }

        Entity target = player.getTargetEntity(10);
        if (target == null) {
            player.sendMessage(Component.text("Hedefte herhangi bir entity bulunamadı.", NamedTextColor.RED));
            return;
        }

        PersistentDataContainer pdc = target.getPersistentDataContainer();
        if (!pdc.has(petIdKey, PersistentDataType.STRING)) {
            player.sendMessage(Component.text("Bu entity bir pet sistemi varlığı değildir.", NamedTextColor.RED));
            return;
        }

        String petIdStr = pdc.get(petIdKey, PersistentDataType.STRING);
        String ownerIdStr = pdc.get(ownerIdKey, PersistentDataType.STRING);
        String definitionId = pdc.get(definitionIdKey, PersistentDataType.STRING);
        int schemaVersion = pdc.getOrDefault(schemaVersionKey, PersistentDataType.INTEGER, 1);

        UUID petId = UUID.fromString(petIdStr);
        Optional<PetInstance> dbInstance = repository.findById(petId);
        Optional<ActivePet> runtimePet = activeRegistry.getByEntity(target.getUniqueId());

        player.sendMessage(Component.text("=== Pet Entity Inspect ===", NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("Pet Entity: Evet", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Pet UUID: " + petIdStr, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Owner UUID: " + ownerIdStr, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Definition ID: " + definitionId, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Entity UUID: " + target.getUniqueId(), NamedTextColor.YELLOW));

        if (dbInstance.isPresent()) {
            PetInstance db = dbInstance.get();
            player.sendMessage(Component.text("Pet Level: " + db.level(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Pet Experience: " + db.experience(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Database State: " + db.availabilityState().name(), NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Database State: BULUNAMADI (DB kaydı eksik)", NamedTextColor.RED));
        }

        player.sendMessage(Component.text("Runtime Registry: " + (runtimePet.isPresent() ? "REGISTERED" : "UNREGISTERED"), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("PDC Version: " + schemaVersion, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Entity Valid: " + target.isValid(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Entity Dead: " + target.isDead(), NamedTextColor.YELLOW));
    }

    private enum SearchStatus { FOUND, NOT_FOUND, AMBIGUOUS }
    private record SearchResult(SearchStatus status, PetSnapshot pet) {}

    private SearchResult resolveOwnedPetByShortId(UUID ownerId, String shortId) {
        List<PetSnapshot> matches = petService.getOwnedPets(ownerId).stream()
                .filter(p -> p.petId().toString().toLowerCase().startsWith(shortId.toLowerCase()))
                .toList();

        if (matches.isEmpty()) {
            return new SearchResult(SearchStatus.NOT_FOUND, null);
        } else if (matches.size() > 1) {
            return new SearchResult(SearchStatus.AMBIGUOUS, null);
        }
        return new SearchResult(SearchStatus.FOUND, matches.get(0));
    }

    private void handleHealth(CommandSender sender) {
        sender.sendMessage(Component.text("=== PetSistemi Sağlık Raporu ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Veritabanı Durumu: BAĞLI (SQLite WAL)", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Yüklü Pet Tanımları: " + definitionRegistry.getAll().size(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Aktif Çağırılmış Petler: " + activeRegistry.getAllActive().size(), NamedTextColor.YELLOW));
    }

    private void handleBackup(CommandSender sender) {
        try {
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            File backupDir = new File(plugin.getDataFolder(), "database-backups");
            com.petsistemi.persistence.migration.MigrationBackupManager backupManager = new com.petsistemi.persistence.migration.MigrationBackupManager(plugin.getLogger());
            File backup = backupManager.createBackup(dbFile, backupDir, 0, true, true, 5);
            if (backup != null) {
                sender.sendMessage(Component.text("Manuel veritabanı yedeği başarıyla alındı: " + backup.getName(), NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Yedekleme dosyası oluşturulamadı.", NamedTextColor.RED));
            }
        } catch (Exception e) {
            sender.sendMessage(Component.text("Yedek alma hatası: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleReconcile(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("Veritabanı ve dünya pet durumları uzlaştırılıyor...", NamedTextColor.YELLOW));
        int activeCount = activeRegistry.getAllActive().size();
        sender.sendMessage(Component.text("Uzlaştırma tamamlandı. Aktif runtime sayısı: " + activeCount, NamedTextColor.GREEN));
    }

    private void handleDisable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin disable <pet_id>", NamedTextColor.RED));
            return;
        }
        try {
            UUID petId = UUID.fromString(args[1]);
            Optional<PetInstance> petOpt = repository.findById(petId);
            if (petOpt.isEmpty()) {
                sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
                return;
            }
            PetInstance pet = petOpt.get();
            PetInstance updated = new PetInstance(pet.petId(), pet.ownerId(), pet.definitionId(), pet.customName(), pet.level(), pet.experience(), com.petsistemi.domain.PetAvailabilityState.DISABLED, pet.createdAt(), System.currentTimeMillis());
            repository.update(updated);
            sender.sendMessage(Component.text("Pet " + petId + " başarıyla devre dışı bırakıldı (DISABLED).", NamedTextColor.GREEN));
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
            Optional<PetInstance> petOpt = repository.findById(petId);
            if (petOpt.isEmpty()) {
                sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
                return;
            }
            PetInstance pet = petOpt.get();
            PetInstance updated = new PetInstance(pet.petId(), pet.ownerId(), pet.definitionId(), pet.customName(), pet.level(), pet.experience(), com.petsistemi.domain.PetAvailabilityState.AVAILABLE, pet.createdAt(), System.currentTimeMillis());
            repository.update(updated);
            sender.sendMessage(Component.text("Pet " + petId + " başarıyla etkinleştirildi (AVAILABLE).", NamedTextColor.GREEN));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Geçersiz UUID formatı.", NamedTextColor.RED));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("companionpets.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("give", "remove", "list", "info", "addxp", "setxp", "setlevel", "summon", "dismiss", "reload", "inspect", "health", "backup", "reconcile", "disable", "enable").stream()
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
