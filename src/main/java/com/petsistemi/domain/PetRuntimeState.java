package com.petsistemi.domain;

public enum PetRuntimeState {
    ABSENT,
    SPAWNING,
    ACTIVE,
    RESTORING,
    DESPAWNING,
    FAILED;

    public boolean canTransitionTo(PetRuntimeState nextState) {
        if (nextState == null) return false;
        if (this == nextState) return true;

        return switch (this) {
            case ABSENT -> nextState == SPAWNING || nextState == RESTORING || nextState == FAILED;
            case SPAWNING -> nextState == ACTIVE || nextState == FAILED || nextState == DESPAWNING;
            case ACTIVE -> nextState == DESPAWNING || nextState == RESTORING || nextState == FAILED;
            case RESTORING -> nextState == ACTIVE || nextState == FAILED || nextState == DESPAWNING;
            case DESPAWNING -> nextState == ABSENT || nextState == FAILED;
            case FAILED -> nextState == SPAWNING || nextState == RESTORING || nextState == ABSENT;
        };
    }
}
