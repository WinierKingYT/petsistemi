package com.petsistemi.progression;

public class LinearExperienceCurve implements ExperienceCurve {

    private final long xpPerLevel;

    public LinearExperienceCurve(long xpPerLevel) {
        this.xpPerLevel = Math.max(1, xpPerLevel);
    }

    @Override
    public long getRequiredExperience(int level) {
        if (level <= 1) return 0;
        return (level - 1) * xpPerLevel;
    }

    @Override
    public int getLevelForExperience(long experience) {
        if (experience <= 0) return 1;
        return (int) (experience / xpPerLevel) + 1;
    }
}
