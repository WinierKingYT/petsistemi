package com.petsistemi.runtime.behavior;

import java.util.Map;

@FunctionalInterface
public interface BehaviorCondition {
    boolean matches(BehaviorContext context, Map<String, Object> parameters);
}
