package com.petsistemi.runtime.mount;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Isolates the 1.20.4 NMS input seam. No CraftBukkit/NMS type is linked from core bytecode;
 * a server mapping change therefore disables steering with one warning instead of preventing
 * the plugin from loading.
 */
public final class ReflectivePlayerMountInputProvider implements PetMountInputProvider {

    private final Logger logger;
    private final Map<Class<?>, Accessor> accessors = new ConcurrentHashMap<>();
    private final AtomicBoolean warningLogged = new AtomicBoolean();

    public ReflectivePlayerMountInputProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public PetMountInput read(Player player) {
        if (player == null) return PetMountInput.NONE;
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(player);
            if (handle == null) return PetMountInput.NONE;
            Accessor accessor = accessors.computeIfAbsent(handle.getClass(), ReflectivePlayerMountInputProvider::resolve);
            if (!accessor.available()) {
                warnOnce("Bu Paper sürümünde sürüş input alanları çözümlenemedi; mount hareketi devre dışı.");
                return PetMountInput.NONE;
            }
            return new PetMountInput(accessor.sideways().getFloat(handle),
                    accessor.forward().getFloat(handle), accessor.jumping().getBoolean(handle));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("Sürüş input'u okunamadı; mount hareketi devre dışı: " + exception.getMessage());
            return PetMountInput.NONE;
        }
    }

    private static Accessor resolve(Class<?> handleType) {
        Field sideways = findField(handleType, "xxa", "sidewaysSpeed");
        Field forward = findField(handleType, "zza", "forwardSpeed");
        Field jumping = findField(handleType, "jumping");
        return new Accessor(sideways, forward, jumping);
    }

    private static Field findField(Class<?> type, String... candidates) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String candidate : candidates) {
                try {
                    Field field = current.getDeclaredField(candidate);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException | RuntimeException ignored) {
                    // Try the next mapping candidate/superclass.
                }
            }
        }
        return null;
    }

    private void warnOnce(String message) {
        if (logger != null && warningLogged.compareAndSet(false, true)) logger.warning(message);
    }

    private record Accessor(Field sideways, Field forward, Field jumping) {
        boolean available() { return sideways != null && forward != null && jumping != null; }
    }
}
