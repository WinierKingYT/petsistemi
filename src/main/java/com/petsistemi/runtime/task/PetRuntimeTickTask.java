package com.petsistemi.runtime.task;

import com.petsistemi.runtime.PetRuntimeCoordinator;

/**
 * Single tick entrypoint for all pet runtime updates (movement / behavior).
 * Per-pet cadence is handled inside {@link PetRuntimeCoordinator#tickAll()}
 * via update-interval-ticks.
 */
public final class PetRuntimeTickTask implements Runnable {

    private final PetRuntimeCoordinator coordinator;

    public PetRuntimeTickTask(PetRuntimeCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void run() {
        if (coordinator != null) {
            coordinator.tickAll();
        }
    }
}
