package com.petsistemi.runtime;

public enum RecoveryOutcome {
    RESTORED,
    OWNER_OFFLINE,
    SELECTION_CHANGED,
    PET_DISABLED,
    DEFINITION_MISSING,
    RETRIES_EXHAUSTED,
    DATABASE_ERROR
}
