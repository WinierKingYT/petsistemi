package com.petsistemi.runtime;

import com.petsistemi.domain.PetBuffDefinition;
import com.petsistemi.domain.PetDefinition;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.List;

/**
 * Periodically applies configured passive potion effects (buffs) to owners of active pets.
 */
public class PetBuffController {

    private final boolean enabled;

    /** Buffs on, which is the shipped default — each pet still opts in via its own {@code buffs:}. */
    public PetBuffController() {
        this(true);
    }

    /**
     * @param enabled server-wide kill switch ({@code features.buffs.enabled}); when off, no pet
     *                grants potion effects no matter what its definition declares.
     */
    public PetBuffController(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Applies one pet's passive buffs to its owner.
     *
     * <p>Deliberately per-pet rather than a self-driven sweep: the runtime tick loop
     * isolates each pet in its own try/catch, so a buff failure on one pet must not be
     * able to abort the tick for everyone else.</p>
     */
    public void apply(ActivePet pet, Player owner, PetDefinition definition) {
        if (!enabled || pet == null || owner == null || definition == null || !owner.isOnline()) {
            return;
        }

        List<PetBuffDefinition> buffs = definition.buffs();
        if (buffs == null || buffs.isEmpty()) {
            return;
        }

        int petLevel = pet.getLevel();
        for (PetBuffDefinition buff : buffs) {
            if (buff == null || buff.effectType() == null) continue;
            if (petLevel < buff.minLevel()) continue;

            // Pet buffs are refreshed for as long as the pet is out, so visible particles
            // would wrap the owner in swirls permanently. The icon stays on: without it the
            // player has no way to tell where the effect is coming from.
            PotionEffect effect = new PotionEffect(
                    buff.effectType(),
                    buff.durationTicks(),
                    buff.amplifier(),
                    true,  // ambient
                    false, // particles
                    true   // icon
            );
            owner.addPotionEffect(effect);
        }
    }
}
