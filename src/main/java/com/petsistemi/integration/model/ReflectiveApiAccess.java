package com.petsistemi.integration.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/** Reflection implementation that keeps optional plugin bytecode out of core linkage. */
public final class ReflectiveApiAccess implements ExternalApiAccess {
    private static final Map<Class<?>, Class<?>> PRIMITIVES = primitiveWrappers();
    private final ClassLoader classLoader;

    public ReflectiveApiAccess(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : ReflectiveApiAccess.class.getClassLoader();
    }

    @Override
    public boolean isPresent(String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public Object invokeStatic(String className, String method, Object... arguments) {
        try {
            Class<?> type = Class.forName(className, true, classLoader);
            return invokeMethod(null, type, method, true, arguments);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Harici API sınıfı bulunamadı: " + className, e);
        }
    }

    @Override
    public Object invoke(Object target, String method, Object... arguments) {
        if (target == null) throw new IllegalArgumentException("Reflection target null olamaz.");
        return invokeMethod(target, target.getClass(), method, false, arguments);
    }

    private static Object invokeMethod(Object target, Class<?> type, String name, boolean requireStatic,
                                       Object[] arguments) {
        Method selected = null;
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            if (requireStatic != Modifier.isStatic(method.getModifiers())) continue;
            if (compatible(method.getParameterTypes(), arguments)) {
                selected = method;
                break;
            }
        }
        if (selected == null) {
            throw new IllegalStateException("Uyumlu harici API metodu bulunamadı: "
                    + type.getName() + "#" + name + "/" + arguments.length);
        }
        try {
            return selected.invoke(target, arguments);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Harici API metoduna erişilemedi: " + selected, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Harici API çağrısı başarısız: " + selected, cause);
        }
    }

    private static boolean compatible(Class<?>[] parameters, Object[] arguments) {
        for (int i = 0; i < parameters.length; i++) {
            Object argument = arguments[i];
            if (argument == null) {
                if (parameters[i].isPrimitive()) return false;
                continue;
            }
            Class<?> parameter = parameters[i].isPrimitive()
                    ? PRIMITIVES.getOrDefault(parameters[i], parameters[i]) : parameters[i];
            if (!parameter.isAssignableFrom(argument.getClass())) return false;
        }
        return true;
    }

    private static Map<Class<?>, Class<?>> primitiveWrappers() {
        Map<Class<?>, Class<?>> map = new HashMap<>();
        map.put(boolean.class, Boolean.class);
        map.put(byte.class, Byte.class);
        map.put(short.class, Short.class);
        map.put(int.class, Integer.class);
        map.put(long.class, Long.class);
        map.put(float.class, Float.class);
        map.put(double.class, Double.class);
        map.put(char.class, Character.class);
        return Map.copyOf(map);
    }
}
