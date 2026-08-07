package com.petsistemi.integration.papi;

import com.petsistemi.persistence.PlayerPetProfileCache;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thin PlaceholderAPI adapter. All resolution lives in {@link PetPlaceholderResolver};
 * this class only supplies the cached profile and the expansion metadata.
 *
 * <p>Reads exclusively from {@link PlayerPetProfileCache}. PlaceholderAPI resolves on the
 * main server thread — often several times a second per player — so the previous
 * {@code PetService#getOwnedPets} call blocked the main thread on the single-threaded
 * database executor, exactly what ADR 0003 exists to prevent.</p>
 */
public class PetPapiExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final PlayerPetProfileCache profileCache;

    public PetPapiExpansion(JavaPlugin plugin, PlayerPetProfileCache profileCache) {
        this.plugin = plugin;
        this.profileCache = profileCache;
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
        if (player == null || profileCache == null) {
            return "";
        }
        return PetPlaceholderResolver.resolve(profileCache.getProfile(player.getUniqueId()), params);
    }
}
