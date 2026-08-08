package com.petsistemi.api.order;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface PetOrderHandler {
    CompletionStage<PetOrderResult> execute(PetOrderContext context);
}
