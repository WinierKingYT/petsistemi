package com.petsistemi.runtime;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface PetBehaviorController {

    void initialize(ActivePet activePet, LivingEntity entity, Player owner);

    void tick(ActivePet activePet, LivingEntity entity, Player owner);

    void remove(ActivePet activePet, LivingEntity entity);
}
