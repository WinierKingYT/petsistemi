package com.petsistemi.integration.model;

import com.petsistemi.api.model.PetModelHandle;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.animation.PetAnimationClipDefinition;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptionalModelProvidersTest {
    private JavaPlugin plugin;
    private Player owner;
    private World world;
    private Location location;
    private PetInstance pet;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(manager.isPluginEnabled(anyString())).thenReturn(true);

        world = mock(World.class);
        location = new Location(world, 1, 64, 2);
        owner = mock(Player.class);
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(location);
        UUID petId = UUID.randomUUID();
        pet = new PetInstance(petId, UUID.randomUUID(), "model_pet", "Model", 1, 0,
                PetAvailabilityState.AVAILABLE, 1L, 1L);
    }

    @Test
    void modelEngineReceivesNamedClipPriorityAndBlendMetadata() {
        RecordingApi api = new RecordingApi();
        Object modeled = new Object();
        Object activeModel = new Object();
        Object animationHandler = new Object();
        Entity entity = mock(Entity.class);
        when(entity.isValid()).thenReturn(true);
        when(world.spawnEntity(eq(location), any())).thenReturn(entity);
        api.staticResult("createModeledEntity", modeled);
        api.staticResult("createActiveModel", activeModel);
        api.instanceResult(activeModel, "getAnimationHandler", animationHandler);
        ModelEngineModelProvider provider = new ModelEngineModelProvider(plugin, api);

        PetModelHandle handle = provider.spawn(pet,
                definition(ModelEngineModelProvider.KEY, "phoenix"), owner);
        PetAnimationClipDefinition clip = new PetAnimationClipDefinition(
                new NamespacedKey("models", "attack"), 80, 4, 6, false);
        provider.applyAnimation(handle, new PetAnimationTransition(
                PetAnimationState.IDLE, null, PetAnimationState.ATTACKING, clip));

        RecordingApi.Call play = api.calls.stream()
                .filter(call -> call.target == animationHandler && call.method.equals("playAnimation"))
                .findFirst().orElseThrow();
        assertArrayEquals(new Object[]{80, "attack", 0.2, 0.3, 1.0, false}, play.arguments);
        assertTrue(api.calls.stream().anyMatch(call -> call.target == modeled && call.method.equals("addModel")));

        provider.remove(handle);
        assertTrue(api.calls.stream().anyMatch(call -> call.target == activeModel && call.method.equals("destroy")));
        verify(entity).remove();
    }

    @Test
    void itemsAdderSpawnsAnimatesAndDestroysCustomEntity() {
        RecordingApi api = new RecordingApi();
        Object customEntity = new Object();
        Entity entity = mock(Entity.class);
        when(entity.isValid()).thenReturn(true);
        api.staticResult("spawn", customEntity);
        api.instanceResult(customEntity, "getEntity", entity);
        ItemsAdderModelProvider provider = new ItemsAdderModelProvider(plugin, api);

        PetModelHandle handle = provider.spawn(pet,
                definition(ItemsAdderModelProvider.KEY, "pack:fox"), owner);
        PetAnimationClipDefinition clip = new PetAnimationClipDefinition(
                new NamespacedKey("petsistemi", "run"), 10, 2, 2, true);
        provider.applyAnimation(handle, new PetAnimationTransition(
                PetAnimationState.IDLE, null, PetAnimationState.MOVING, clip));
        provider.remove(handle);

        assertSame(entity, handle.entity());
        assertTrue(api.calls.stream().anyMatch(call -> call.target == customEntity
                && call.method.equals("playAnimation") && "run".equals(call.arguments[0])));
        assertTrue(api.calls.stream().anyMatch(call -> call.target == customEntity && call.method.equals("destroy")));
        verify(entity).remove();
    }

    @Test
    void oraxenBuildsConfiguredItemIntoItemDisplay() {
        RecordingApi api = new RecordingApi();
        Object itemBuilder = new Object();
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemDisplay display = mock(ItemDisplay.class);
        api.staticResult("getItemById", itemBuilder);
        api.instanceResult(itemBuilder, "build", stack);
        when(world.spawnEntity(eq(location), any())).thenReturn(display);
        OraxenModelProvider provider = new OraxenModelProvider(plugin, api);

        PetModelHandle handle = provider.spawn(pet,
                definition(OraxenModelProvider.KEY, "floating_book"), owner);

        assertSame(display, handle.entity());
        verify(display).setItemStack(stack);
    }

    private static PetDefinition definition(NamespacedKey key, String modelId) {
        PetRepresentationDefinition representation = new PetRepresentationDefinition(
                key, modelId, "ARMOR_STAND", false, false, true, true, false,
                null, null, PetVector3.ONE, null, 0, 0, 0, 0, null);
        return PetDefinition.builder("model_pet", "Model").representation(representation).build();
    }

    private static final class RecordingApi implements ExternalApiAccess {
        private final List<Call> calls = new ArrayList<>();
        private final java.util.Map<String, Object> staticResults = new java.util.HashMap<>();
        private final java.util.Map<InstanceKey, Object> instanceResults = new java.util.HashMap<>();

        void staticResult(String method, Object result) { staticResults.put(method, result); }
        void instanceResult(Object target, String method, Object result) {
            instanceResults.put(new InstanceKey(target, method), result);
        }

        @Override public boolean isPresent(String className) { return true; }

        @Override
        public Object invokeStatic(String className, String method, Object... arguments) {
            calls.add(new Call(null, method, arguments));
            if (!staticResults.containsKey(method)) throw new IllegalStateException("missing " + method);
            return staticResults.get(method);
        }

        @Override
        public Object invoke(Object target, String method, Object... arguments) {
            calls.add(new Call(target, method, arguments));
            InstanceKey key = new InstanceKey(target, method);
            return instanceResults.get(key);
        }

        private record InstanceKey(Object target, String method) {
            @Override public boolean equals(Object other) {
                return other instanceof InstanceKey key && target == key.target && method.equals(key.method);
            }
            @Override public int hashCode() { return System.identityHashCode(target) * 31 + method.hashCode(); }
        }
        private record Call(Object target, String method, Object[] arguments) {}
    }
}
