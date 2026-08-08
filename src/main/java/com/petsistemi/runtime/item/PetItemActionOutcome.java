package com.petsistemi.runtime.item;

import com.petsistemi.domain.item.PetItemActionDefinition;

public record PetItemActionOutcome(
        PetItemActionStatus status,
        String message,
        PetItemActionDefinition definition,
        long remainingSeconds
) {
    public boolean matched() { return status != PetItemActionStatus.NOT_MATCHED; }
    public boolean success() { return status == PetItemActionStatus.SUCCESS; }
}
