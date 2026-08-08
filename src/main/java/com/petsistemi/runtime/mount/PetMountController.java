package com.petsistemi.runtime.mount;

import com.petsistemi.api.mount.PetMountResult;
import com.petsistemi.api.mount.PetMountService;
import com.petsistemi.api.mount.PetMountStatus;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetMountDefinition;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Owns mount authorization, rider sessions, steering physics, and gravity restoration. */
public final class PetMountController implements PetMountService {

    static final double BASE_SPEED = 0.24D;
    static final double JUMP_VELOCITY = 0.42D;
    private static final PetMountDefinition LEGACY_DEFAULT =
            new PetMountDefinition(true, null, 1.0D, false);

    private final ActivePetRegistry activeRegistry;
    private final PetDefinitionRegistry definitionRegistry;
    private final BooleanSupplier ridingEnabled;
    private final PetMountInputProvider inputProvider;
    private final Map<UUID, MountSession> sessions = new ConcurrentHashMap<>();

    public PetMountController(ActivePetRegistry activeRegistry,
                              PetDefinitionRegistry definitionRegistry,
                              AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                              PetMountInputProvider inputProvider) {
        this(activeRegistry, definitionRegistry, () -> {
            RuntimeConfigurationSnapshot snapshot = configSnapshot != null ? configSnapshot.get() : null;
            return snapshot != null && snapshot.configuration() != null
                    && snapshot.configuration().features() != null
                    && snapshot.configuration().features().ridingEnabled();
        }, inputProvider);
    }

    public PetMountController(ActivePetRegistry activeRegistry,
                              PetDefinitionRegistry definitionRegistry,
                              BooleanSupplier ridingEnabled,
                              PetMountInputProvider inputProvider) {
        if (activeRegistry == null || definitionRegistry == null) {
            throw new IllegalArgumentException("Mount controller registry'leri null olamaz.");
        }
        this.activeRegistry = activeRegistry;
        this.definitionRegistry = definitionRegistry;
        this.ridingEnabled = ridingEnabled != null ? ridingEnabled : () -> false;
        this.inputProvider = inputProvider != null ? inputProvider : player -> PetMountInput.NONE;
    }

    @Override
    public PetMountResult toggleMount(Player player) {
        if (player == null) return result(PetMountStatus.FAILED, "Oyuncu bulunamadı.");
        ActivePet active = activeRegistry.getByOwner(player.getUniqueId()).orElse(null);
        if (active == null) return result(PetMountStatus.NO_ACTIVE_PET, "Önce petinizi çağırın.");
        Entity entity = active.getSpawnedEntity();
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return result(PetMountStatus.ENTITY_UNAVAILABLE, "Pet varlığı binmeye uygun değil.");
        }
        if (player.getVehicle() == entity) {
            sessions.computeIfAbsent(player.getUniqueId(), ignored ->
                    new MountSession(active.getPetId(), entity, entity.hasGravity(), mountRules(active)));
            return dismount(player);
        }
        if (sessions.containsKey(player.getUniqueId())) {
            endSession(player.getUniqueId(), player);
        }

        PetMountDefinition mount = mountRules(active);
        PetMountResult gate = gate(player, mount);
        if (gate != null) return gate;
        if (player.isInsideVehicle() && !player.leaveVehicle()) {
            return result(PetMountStatus.FAILED, "Mevcut araçtan inilemedi.");
        }

        boolean originalGravity = entity.hasGravity();
        if (!entity.addPassenger(player)) {
            return result(PetMountStatus.FAILED, "Pet üzerine binilemedi.");
        }
        if (mount.allowFly()) entity.setGravity(false);
        sessions.put(player.getUniqueId(), new MountSession(active.getPetId(), entity, originalGravity, mount));
        return result(PetMountStatus.MOUNTED, "Petinize bindiniz.");
    }

    @Override
    public PetMountResult dismount(Player player) {
        if (player == null) return result(PetMountStatus.FAILED, "Oyuncu bulunamadı.");
        MountSession session = sessions.remove(player.getUniqueId());
        if (session == null) return result(PetMountStatus.FAILED, "Aktif pet sürüş oturumu yok.");
        boolean left = session.entity.removePassenger(player);
        restore(session);
        return result(PetMountStatus.DISMOUNTED, left ? "Petinizden indiniz." : "Sürüş oturumu kapatıldı.");
    }

    @Override
    public boolean isMounted(UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
    }

    /** Called every runtime tick. Returns true while normal follow movement must be suppressed. */
    public boolean tick(ActivePet active, Player owner) {
        if (active == null || owner == null) return false;
        MountSession session = sessions.get(owner.getUniqueId());
        if (session == null) return false;
        Entity entity = active.getSpawnedEntity();
        if (!active.getPetId().equals(session.petId) || entity != session.entity
                || !entity.isValid() || entity.isDead() || owner.getVehicle() != entity
                || entity.getWorld() == null || owner.getWorld() == null
                || !entity.getWorld().equals(owner.getWorld())) {
            endSession(owner.getUniqueId(), owner);
            return false;
        }

        PetMountDefinition currentRules = mountRules(active);
        if (gate(owner, currentRules) != null) {
            endSession(owner.getUniqueId(), owner);
            return false;
        }
        session.rules = currentRules;
        applySteering(session, owner, inputProvider.read(owner));
        return true;
    }

    public void cleanup(UUID ownerId) {
        endSession(ownerId, null);
    }

    public void cleanupAll() {
        for (UUID ownerId : java.util.List.copyOf(sessions.keySet())) endSession(ownerId, null);
    }

    private PetMountResult gate(Player player, PetMountDefinition mount) {
        if (!ridingEnabled.getAsBoolean() || mount == null || !mount.enabled()) {
            return result(PetMountStatus.DISABLED, "Bu pet için sürüş devre dışı.");
        }
        if (mount.permission() != null && !mount.permission().isBlank()
                && !player.hasPermission(mount.permission())) {
            return result(PetMountStatus.NO_PERMISSION, "Bu pete binme yetkiniz yok.");
        }
        return null;
    }

    private PetMountDefinition mountRules(ActivePet active) {
        PetDefinition definition = active != null
                ? definitionRegistry.find(active.getDefinitionId()).orElse(null) : null;
        return definition != null && definition.mount() != null ? definition.mount() : LEGACY_DEFAULT;
    }

    private static void applySteering(MountSession session, Player rider, PetMountInput input) {
        Entity entity = session.entity;
        PetMountDefinition rules = session.rules;
        PetMountInput safeInput = input != null ? input : PetMountInput.NONE;
        Location riderLocation = rider.getLocation();
        if (riderLocation == null) return;

        float yaw = riderLocation.getYaw();
        float pitch = rules.allowFly() ? riderLocation.getPitch() : 0.0F;
        entity.setRotation(yaw, pitch);

        Vector forward = riderLocation.getDirection();
        Vector horizontalForward = forward.clone().setY(0);
        if (horizontalForward.lengthSquared() < 1.0E-6D) horizontalForward.setZ(1);
        horizontalForward.normalize();
        if (!rules.allowFly()) forward = horizontalForward.clone();
        else if (forward.lengthSquared() > 1.0E-6D) forward.normalize();
        Vector left = new Vector(-horizontalForward.getZ(), 0, horizontalForward.getX());
        Vector desired = forward.multiply(safeInput.forward()).add(left.multiply(safeInput.sideways()));
        if (desired.lengthSquared() > 1.0D) desired.normalize();
        desired.multiply(BASE_SPEED * clampSpeed(rules.speedMultiplier()));

        Vector current = entity.getVelocity();
        if (rules.allowFly()) {
            if (safeInput.jumping()) desired.setY(Math.max(desired.getY(), BASE_SPEED * rules.speedMultiplier()));
            if (Math.abs(safeInput.forward()) < 0.01F && Math.abs(safeInput.sideways()) < 0.01F
                    && !safeInput.jumping()) desired.setY(0.0D);
        } else {
            desired.setY(current != null ? current.getY() : 0.0D);
            if (safeInput.jumping() && entity.isOnGround() && !session.jumpHeld) {
                desired.setY(JUMP_VELOCITY);
            }
        }
        session.jumpHeld = safeInput.jumping();
        entity.setVelocity(desired);
    }

    private void endSession(UUID ownerId, Player player) {
        if (ownerId == null) return;
        MountSession session = sessions.remove(ownerId);
        if (session == null) return;
        if (player != null && session.entity.isValid()) session.entity.removePassenger(player);
        restore(session);
    }

    private static void restore(MountSession session) {
        if (session.entity != null && session.entity.isValid()) {
            session.entity.setGravity(session.originalGravity);
            Vector velocity = session.entity.getVelocity();
            if (velocity != null) session.entity.setVelocity(new Vector(0, velocity.getY(), 0));
        }
    }

    private static double clampSpeed(double multiplier) {
        if (!Double.isFinite(multiplier)) return 1.0D;
        return Math.max(0.1D, Math.min(3.0D, multiplier));
    }

    private static PetMountResult result(PetMountStatus status, String message) {
        return new PetMountResult(status, message);
    }

    private static final class MountSession {
        private final UUID petId;
        private final Entity entity;
        private final boolean originalGravity;
        private PetMountDefinition rules;
        private boolean jumpHeld;

        private MountSession(UUID petId, Entity entity, boolean originalGravity, PetMountDefinition rules) {
            this.petId = petId;
            this.entity = entity;
            this.originalGravity = originalGravity;
            this.rules = rules;
        }
    }
}
