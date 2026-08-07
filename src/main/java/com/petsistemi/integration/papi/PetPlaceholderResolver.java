package com.petsistemi.integration.papi;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.persistence.PlayerPetProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Resolves PetSistemi placeholders from an already-loaded {@link PlayerPetProfile}.
 *
 * <p>Deliberately free of any PlaceholderAPI type so the logic is testable without the
 * optional soft-depend on the classpath — {@link PetPapiExpansion} is only the adapter.</p>
 *
 * <p>Every lookup is a pure read over the cached profile. PlaceholderAPI resolves on the
 * main server thread, repeatedly, for every player with a scoreboard or tab entry; going
 * to the database there would block the server on the single-threaded executor (ADR 0003).</p>
 */
public final class PetPlaceholderResolver {

    /** Rendered when the player's profile has not been loaded into the cache yet. */
    public static final String UNKNOWN = "...";

    /** Rendered when the player has no pet in the requested slot. */
    public static final String NONE = "Yok";

    private PetPlaceholderResolver() {}

    /** @return the placeholder value, or {@code null} when the key is not ours. */
    public static String resolve(Optional<PlayerPetProfile> profileOpt, String params) {
        String key = params != null ? params.toLowerCase(java.util.Locale.ROOT) : "";
        if (profileOpt == null || profileOpt.isEmpty()) {
            // Unknown keys must stay null even without a profile, so PlaceholderAPI can
            // offer them to another expansion rather than printing our filler.
            return isKnownKey(key) ? UNKNOWN : null;
        }

        PlayerPetProfile profile = profileOpt.get();
        Collection<PetSnapshot> pets = profile.pets() != null ? profile.pets().values() : List.of();
        PetSnapshot active = profile.selectedPetId() != null && profile.pets() != null
                ? profile.pets().get(profile.selectedPetId())
                : null;

        return switch (key) {
            case "active_pet" -> active != null ? active.definitionId() : NONE;
            case "active_pet_custom_name" -> active != null
                    ? (active.customName() != null ? active.customName() : active.definitionId())
                    : NONE;
            case "active_pet_level" -> active != null ? String.valueOf(active.level()) : "0";
            case "active_pet_xp" -> active != null ? String.valueOf(active.experience()) : "0";
            case "active_pet_status" -> active != null ? (active.spawned() ? "Çağrıldı" : "Seçili") : NONE;
            case "total_xp" -> String.valueOf(pets.stream().mapToLong(PetSnapshot::experience).sum());
            case "total_pets" -> String.valueOf(pets.size());
            default -> null;
        };
    }

    static boolean isKnownKey(String key) {
        return switch (key) {
            case "active_pet", "active_pet_custom_name", "active_pet_level", "active_pet_xp",
                 "active_pet_status", "total_xp", "total_pets" -> true;
            default -> false;
        };
    }
}
