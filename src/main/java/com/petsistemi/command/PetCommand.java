package com.petsistemi.command;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.result.PetDismissResult;
import com.petsistemi.api.result.PetRenameResult;
import com.petsistemi.api.result.PetSummonResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class PetCommand implements CommandExecutor, TabCompleter {

    private final PetService petService;

    public PetCommand(PetService petService) {
        this.petService = petService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Bu komut sadece oyuncular tarafından kullanılabilir.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
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
        Collection<PetSnapshot> pets = petService.getOwnedPets(player.getUniqueId());
        if (pets.isEmpty()) {
            player.sendMessage(Component.text("Herhangi bir pete sahip değilsiniz. Admin'den pet isteyebilirsiniz.", NamedTextColor.GRAY));
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
    }

    private void handleSummon(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Kullanım: /pet summon <pet_id>", NamedTextColor.RED));
            return;
        }

        String shortId = args[1];
        Optional<PetSnapshot> petOpt = findOwnedPetByShortId(player, shortId);
        if (petOpt.isEmpty()) {
            player.sendMessage(Component.text("Belirtilen ID ile eşleşen bir pet bulunamadı.", NamedTextColor.RED));
            return;
        }

        PetSummonResult result = petService.summon(player, petOpt.get().petId());
        if (result.success()) {
            player.sendMessage(Component.text("Pet başarıyla çağırıldı!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleDismiss(Player player) {
        PetDismissResult result = petService.dismiss(player);
        if (result.success()) {
            player.sendMessage(Component.text("Petiniz kaldırıldı.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleInfo(Player player, String[] args) {
        Optional<PetSnapshot> petOpt;
        if (args.length >= 2) {
            String shortId = args[1];
            petOpt = findOwnedPetByShortId(player, shortId);
        } else {
            petOpt = petService.getActivePet(player.getUniqueId());
        }

        if (petOpt.isEmpty()) {
            player.sendMessage(Component.text("Detaylarını görecek aktif veya belirtilmiş bir pet bulunamadı.", NamedTextColor.RED));
            return;
        }

        PetSnapshot pet = petOpt.get();
        String shortId = pet.petId().toString().substring(0, 6);
        String customName = pet.customName() != null ? pet.customName() : "Yok";
        
        player.sendMessage(Component.text("=== Pet Bilgisi ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Tür ID: " + pet.definitionId(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Özel İsim: " + customName, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Seviye: " + pet.level(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Deneyim: " + pet.experience(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Durum: " + pet.storageState().name(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Pet UUID: " + pet.petId(), NamedTextColor.YELLOW));
    }

    private void handleRename(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Kullanım: /pet rename <pet_id> <yeni_isim>", NamedTextColor.RED));
            return;
        }

        String shortId = args[1];
        // Join remaining arguments for full name
        StringJoiner sj = new StringJoiner(" ");
        for (int i = 2; i < args.length; i++) {
            sj.add(args[i]);
        }
        String newName = sj.toString();

        Optional<PetSnapshot> petOpt = findOwnedPetByShortId(player, shortId);
        if (petOpt.isEmpty()) {
            player.sendMessage(Component.text("Pet bulunamadı.", NamedTextColor.RED));
            return;
        }

        PetRenameResult result = petService.rename(player, petOpt.get().petId(), newName);
        if (result.success()) {
            player.sendMessage(Component.text("Petinizin ismi '" + newName + "' olarak güncellendi!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private Optional<PetSnapshot> findOwnedPetByShortId(Player player, String shortId) {
        return petService.getOwnedPets(player.getUniqueId()).stream()
                .filter(p -> p.petId().toString().toLowerCase().startsWith(shortId.toLowerCase()))
                .findFirst();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
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
                return petService.getOwnedPets(player.getUniqueId()).stream()
                        .map(p -> p.petId().toString().substring(0, 6))
                        .filter(id -> id.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
