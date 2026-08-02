package com.petsistemi.progression;

public class ExponentialExperienceCurve implements ExperienceCurve {

    private final double baseFactor;

    public ExponentialExperienceCurve(double baseFactor) {
        this.baseFactor = Math.max(1.1, baseFactor);
    }

    @Override
    public long getRequiredExperience(int level) {
        if (level <= 1) return 0;
        return (long) (100 * Math.pow(baseFactor, level - 1));
    }

    @Override
    public int getLevelForExperience(long experience) {
        if (experience <= 0) return 1;
        return (int) (Math.log(experience / 100.0) / Math.log(baseFactor)) + 1;
    }
}
