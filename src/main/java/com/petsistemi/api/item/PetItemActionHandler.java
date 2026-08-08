package com.petsistemi.api.item;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface PetItemActionHandler {
    CompletionStage<PetItemActionResult> execute(PetItemActionContext context, Map<String, Object> parameters);
}
