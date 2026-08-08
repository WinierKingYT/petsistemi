package com.petsistemi.runtime.item;

import com.petsistemi.api.AsyncPetExperienceService;
import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.item.PetItemActionResult;
import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.domain.ExperienceSource;
import org.bukkit.NamespacedKey;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Built-in item actions; the engine remains open to third-party registrations. */
public final class BuiltInPetItemActions {

    public static final NamespacedKey GAIN_EXPERIENCE = new NamespacedKey("petsistemi", "gain_experience");
    public static final NamespacedKey UNLOCK_PET = new NamespacedKey("petsistemi", "unlock_pet");
    public static final NamespacedKey EVOLVE_PET = new NamespacedKey("petsistemi", "evolve_pet");

    private BuiltInPetItemActions() {}

    public static void register(PetItemActionEngine engine, PetExperienceService experienceService,
                                PetService petService) {
        register(engine, experienceService, petService, null);
    }

    public static void register(PetItemActionEngine engine, PetExperienceService experienceService,
                                PetService petService, PetRuntimeOperationService operationService) {
        engine.registerAction(GAIN_EXPERIENCE, (context, parameters) -> {
            long amount = positiveLong(parameters, "amount");
            if (amount <= 0) return CompletableFuture.completedFuture(PetItemActionResult.failure("XP miktarı pozitif olmalıdır."));
            var future = experienceService instanceof AsyncPetExperienceService async
                    ? async.addExperienceAsync(context.petId(), amount, ExperienceSource.ITEM_ACTION)
                    : CompletableFuture.completedFuture(experienceService.addExperience(context.petId(), amount, ExperienceSource.ITEM_ACTION));
            return future.thenApply(result -> result.success()
                    ? PetItemActionResult.success("Pet " + amount + " XP kazandı.")
                    : PetItemActionResult.failure(result.message()));
        });

        engine.registerAction(UNLOCK_PET, (context, parameters) -> {
            String target = string(parameters, "definition-id");
            if (target == null) return CompletableFuture.completedFuture(PetItemActionResult.failure("definition-id eksik."));
            var future = petService instanceof AsyncPetService async
                    ? async.givePetAsync(context.player().getUniqueId(), target)
                    : CompletableFuture.completedFuture(petService.givePet(context.player().getUniqueId(), target));
            return future.thenApply(result -> result.success()
                    ? PetItemActionResult.success("Yeni pet açıldı: " + target)
                    : PetItemActionResult.failure(result.message()));
        });

        engine.registerAction(EVOLVE_PET, (context, parameters) -> {
            String target = string(parameters, "target-id");
            if (target == null) return CompletableFuture.completedFuture(PetItemActionResult.failure("target-id eksik."));
            if (operationService == null) return CompletableFuture.completedFuture(PetItemActionResult.failure("Kalıcı evrim servisi hazır değil."));
            return operationService.evolveAsync(context.player(), context.petId(), target)
                    .thenApply(result -> result.success()
                            ? PetItemActionResult.success(result.message())
                            : PetItemActionResult.failure(result.message()));
        });
    }

    private static long positiveLong(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        if (value instanceof Number number) return number.longValue();
        try { return value != null ? Long.parseLong(value.toString()) : -1L; }
        catch (NumberFormatException ignored) { return -1L; }
    }

    private static String string(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }
}
