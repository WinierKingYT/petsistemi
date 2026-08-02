package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface PetEntityController {

    Entity spawn(PetInstance pet, PetDefinition definition, Player owner);

    void remove(Entity entity);

    void updateName(Entity entity, PetInstance pet, PetDefinition definition);

    boolean isValid(Entity entity);
}
