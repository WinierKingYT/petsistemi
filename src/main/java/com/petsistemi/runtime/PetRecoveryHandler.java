package com.petsistemi.runtime;

import org.bukkit.entity.Player;

/**
 * Invoked by the watchdog when a runtime pet's entity is gone (dead/unloaded)
 * while its owner is still online. Implementations re-validate the DB selection
 * and re-summon the pet when it should still be active.
 */
@FunctionalInterface
public interface PetRecoveryHandler {

    void attemptRecovery(ActivePet activePet, Player owner);
}
