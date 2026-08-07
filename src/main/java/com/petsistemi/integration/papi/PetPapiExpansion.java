package com.petsistemi.integration.papi;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PetPapiExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final PetService petService;

    public PetPapiExpansion(JavaPlugin plugin, PetService petService) {
        this.plugin = plugin;
        this.petService = petService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "petsistemi";
    }

    @Override
    public @NotNull String getAuthor() {
        return "PetSistemiTeam";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin != null ? plugin.getDescription().getVersion() : "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || petService == null) {
            return "";
        }

        Collection<PetSnapshot> playerPets = petService.getOwnedPets(player.getUniqueId());
        PetSnapshot activePet = playerPets.stream()
                .filter(PetSnapshot::selected)
                .findFirst()
                .orElse(null);

        long totalXp = playerPets.stream().mapToLong(PetSnapshot::experience).sum();

        return switch (params.toLowerCase()) {
            case "active_pet" -> activePet != null ? activePet.definitionId() : "Yok";
            case "active_pet_custom_name" -> activePet != null
                    ? (activePet.customName() != null ? activePet.customName() : activePet.definitionId())
                    : "Yok";
            case "active_pet_level" -> activePet != null ? String.valueOf(activePet.level()) : "0";
            case "active_pet_xp" -> activePet != null ? String.valueOf(activePet.experience()) : "0";
            case "active_pet_status" -> activePet != null ? (activePet.spawned() ? "Çağrıldı" : "Seçili") : "Yok";
            case "active_pet_speed" -> "1.0";
            case "active_pet_buffs_count" -> "0";
            case "total_xp" -> String.valueOf(totalXp);
            case "total_pets" -> String.valueOf(playerPets.size());
            default -> null;
        };
    }
}
