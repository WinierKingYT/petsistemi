package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetBuffDefinition;
import com.petsistemi.domain.PetDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.List;

/**
 * Periodically applies configured passive potion effects (buffs) to owners of active pets.
 */
public class PetBuffController {

    public void tick(ActivePetRegistry activePetRegistry, PetDefinitionRegistry definitionRegistry) {
        if (activePetRegistry == null || definitionRegistry == null) return;

        for (ActivePet pet : activePetRegistry.getAllActive()) {
            if (pet == null || !pet.isValid() || pet.getOwnerId() == null) {
                continue;
            }

            PetDefinition def = definitionRegistry.find(pet.getDefinitionId()).orElse(null);
            if (def == null) continue;

            List<PetBuffDefinition> buffs = def.buffs();
            if (buffs == null || buffs.isEmpty()) {
                continue;
            }

            Player owner = Bukkit.getPlayer(pet.getOwnerId());
            if (owner == null || !owner.isOnline()) {
                continue;
            }

            int petLevel = pet.getLevel();

            for (PetBuffDefinition buff : buffs) {
                if (buff == null || buff.effectType() == null) continue;
                if (petLevel >= buff.minLevel()) {
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
    }
}
