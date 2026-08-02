package com.petsistemi.progression;

import java.util.List;

public class TableExperienceCurve implements ExperienceCurve {

    private final List<Long> table;

    public TableExperienceCurve(List<Long> table) {
        this.table = table != null ? table : List.of(0L);
    }

    @Override
    public long getRequiredExperience(int level) {
        if (level <= 1) return 0;
        int index = level - 1;
        if (index >= table.size()) {
            return table.get(table.size() - 1);
        }
        return table.get(index);
    }

    @Override
    public int getLevelForExperience(long experience) {
        if (experience <= 0) return 1;
        for (int i = 0; i < table.size(); i++) {
            if (experience < table.get(i)) {
                return i;
            }
        }
        return table.size();
    }
}
