package com.petsistemi.runtime.behavior;

import java.util.Map;

@FunctionalInterface
public interface BehaviorAction {
    void execute(BehaviorContext context, Map<String, Object> parameters);
}
