package com.petsistemi.integration.model;

/** Small injectable reflection seam used by optional provider adapters and their tests. */
public interface ExternalApiAccess {
    boolean isPresent(String className);

    Object invokeStatic(String className, String method, Object... arguments);

    Object invoke(Object target, String method, Object... arguments);
}
