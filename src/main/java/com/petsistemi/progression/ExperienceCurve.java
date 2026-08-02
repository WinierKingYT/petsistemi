package com.petsistemi.progression;

public interface ExperienceCurve {

    long getRequiredExperience(int level);

    int getLevelForExperience(long experience);
}
