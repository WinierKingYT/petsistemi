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

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Kullanım: /petadmin give <oyuncu> <tür>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Belirtilen oyuncu çevrimiçi değil.", NamedTextColor.RED));
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

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        String shortId = args[2];
        Optional<PetSnapshot> petOpt = findOwnedPetByShortId(target.getUniqueId(), shortId);
        if (petOpt.isEmpty()) {
            sender.sendMessage(Component.text("Belirtilen pet bulunamadı.", NamedTextColor.RED));
            return;
        }

        UUID petId = petOpt.get().petId();
        // If active, dismiss first
        Optional<ActivePet> active = activeRegistry.getByOwner(target.getUniqueId());
        if (active.isPresent() && active.get().getPetId().equals(petId)) {
            petService.dismiss(target);
        }

        repository.delete(petId);
        sender.sendMessage(Component.text("Pet başarıyla veritabanından silindi.", NamedTextColor.GREEN));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Kullanım: /petadmin list <oyuncu>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
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
            // Find by shortId globally (slower but helpful)
            for (Player p : Bukkit.getOnlinePlayers()) {
                Optional<PetSnapshot> opt = findOwnedPetByShortId(p.getUniqueId(), idStr);
                if (opt.isPresent()) {
                    petOpt = repository.findById(opt.get().petId());
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
        sender.sendMessage(Component.text("Durum: " + pet.storageState().name(), NamedTextColor.YELLOW));
    }

    private void handleAddXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Kullanım: /petadmin addxp <oyuncu> <pet_id> <miktar>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
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

        Optional<PetSnapshot> petOpt = findOwnedPetByShortId(target.getUniqueId(), shortId);
        if (petOpt.isEmpty()) {
            sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        }

        ExperienceResult res = experienceService.addExperience(petOpt.get().petId(), amount, ExperienceSource.ADMIN);
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

        Player target = Bukkit.getPlayer(args[1]);
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

        Optional<PetSnapshot> petOpt = findOwnedPetByShortId(target.getUniqueId(), shortId);
        if (petOpt.isEmpty()) {
            sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        }

        // Set XP by first removing all and adding target amount
        UUID petId = petOpt.get().petId();
        repository.findById(petId).ifPresent(p -> {
            experienceService.removeExperience(petId, p.experience());
            experienceService.addExperience(petId, amount, ExperienceSource.ADMIN);
        });

        sender.sendMessage(Component.text("Deneyim başarıyla ayarlandı.", NamedTextColor.GREEN));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Kullanım: /petadmin setlevel <oyuncu> <pet_id> <seviye>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
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

        Optional<PetSnapshot> petOpt = findOwnedPetByShortId(target.getUniqueId(), shortId);
        if (petOpt.isEmpty()) {
            sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        }

        LevelResult result = experienceService.setLevel(petOpt.get().petId(), level);
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
        if (target == null) {
            sender.sendMessage(Component.text("Oyuncu bulunamadı.", NamedTextColor.RED));
            return;
        }

        String shortId = args[2];
        Optional<PetSnapshot> petOpt = findOwnedPetByShortId(target.getUniqueId(), shortId);
        if (petOpt.isEmpty()) {
            sender.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        }

        PetSummonResult result = petService.summon(target, petOpt.get().petId());
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
        if (target == null) {
            sender.sendMessage(Component.text("Oyuncu bulunamadı.", NamedTextColor.RED));
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
            player.sendMessage(Component.text("Database State: " + db.storageState().name(), NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Database State: BULUNAMADI (DB kaydı eksik)", NamedTextColor.RED));
        }

        player.sendMessage(Component.text("Runtime Registry: " + (runtimePet.isPresent() ? "REGISTERED" : "UNREGISTERED"), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("PDC Version: " + schemaVersion, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Entity Valid: " + target.isValid(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Entity Dead: " + target.isDead(), NamedTextColor.YELLOW));
    }

    private Optional<PetSnapshot> findOwnedPetByShortId(UUID ownerId, String shortId) {
        return petService.getOwnedPets(ownerId).stream()
                .filter(p -> p.petId().toString().toLowerCase().startsWith(shortId.toLowerCase()))
                .findFirst();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("companionpets.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("give", "remove", "list", "info", "addxp", "setxp", "setlevel", "summon", "dismiss", "reload", "inspect").stream()
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
