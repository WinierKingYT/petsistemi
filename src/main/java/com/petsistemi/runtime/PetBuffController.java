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

    /**
     * Applies one pet's passive buffs to its owner.
     *
     * <p>Deliberately per-pet rather than a self-driven sweep: the runtime tick loop
     * isolates each pet in its own try/catch, so a buff failure on one pet must not be
     * able to abort the tick for everyone else.</p>
     */
    public void apply(ActivePet pet, Player owner, PetDefinition definition) {
        if (pet == null || owner == null || definition == null || !owner.isOnline()) {
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

            // Re-apply potion effect with ambient and icon flags
            PotionEffect effect = new PotionEffect(
                    buff.effectType(),
                    buff.durationTicks(),
                    buff.amplifier(),
                    true, // ambient
                    true  // particles & icon visible
            );
            owner.addPotionEffect(effect);
        }
    }
}
