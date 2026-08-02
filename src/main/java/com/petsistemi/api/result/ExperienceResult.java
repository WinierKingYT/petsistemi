package com.petsistemi.api.result;

public record ExperienceResult(boolean success, String message, long newExperience, boolean leveledUp) {
}
