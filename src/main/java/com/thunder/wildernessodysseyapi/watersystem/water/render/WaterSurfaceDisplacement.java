package com.thunder.wildernessodysseyapi.watersystem.water.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Runtime-only surface displacement caused by entities moving through water.
 *
 * <p>This layer makes boats, players, and splash impacts temporarily depress
 * and lift the visible surface without changing canonical water storage. The
 * server-authoritative water volume remains owned by the Water Authority; these
 * samples are client visual detail that fade out under a strict cap.</p>
 */
public final class WaterSurfaceDisplacement {

    private static final int WAKE_INTERVAL_TICKS = 4;
    private static final int BOAT_WAKE_INTERVAL_TICKS = 2;
    private static final int IMPACT_LIFETIME_TICKS = 42;
    private static final int WAKE_LIFETIME_TICKS = 34;
    private static final float MAX_HEIGHT_OFFSET = 0.22f;

    private static final List<Disturbance> DISTURBANCES = new ArrayList<>();
    private static final Map<Integer, Long> LAST_ENTITY_WAKE_TICK = new HashMap<>();

    private WaterSurfaceDisplacement() {
    }

    /**
     * Adds a circular impact depression and raised ring for a water entry.
     *
     * <p>The impact strength scales with the entity size and speed so a player
     * step is subtle while a falling or fast-moving entity throws a stronger
     * ring. No network data is sent because this is a cosmetic client layer.</p>
     */
    public static void spawnImpact(Entity entity, double x, double z) {
        Level level = entity.level();
        if (!shouldRecord(level)) {
            return;
        }

        Vec3 velocity = entity.getDeltaMovement();
        float speed = (float) velocity.length();
        float radius = Math.max(1.0f, entity.getBbWidth() * 2.0f);
        float amplitude = clamp(0.035f + speed * 0.045f + entity.getBbHeight() * 0.008f,
                0.035f,
                0.14f);
        add(level, x, z, amplitude, radius, IMPACT_LIFETIME_TICKS, DisturbanceKind.IMPACT);
    }

    /**
     * Adds a throttled local wake for an entity already touching water.
     *
     * <p>Repeated tiny events approximate a temporary displacement footprint:
     * a center depression under the body plus a moving ring that reads as
     * ripples. Canonical water amount is never changed.</p>
     */
    public static void spawnEntityWake(Entity entity) {
        Level level = entity.level();
        if (!shouldRecord(level)) {
            return;
        }

        long gameTime = level.getGameTime();
        int interval = entity instanceof Boat ? BOAT_WAKE_INTERVAL_TICKS : WAKE_INTERVAL_TICKS;
        long lastWake = LAST_ENTITY_WAKE_TICK.getOrDefault(entity.getId(), Long.MIN_VALUE);
        if (gameTime - lastWake < interval) {
            return;
        }
        LAST_ENTITY_WAKE_TICK.put(entity.getId(), gameTime);

        Vec3 velocity = entity.getDeltaMovement();
        float horizontalSpeed = (float) Math.sqrt(
                velocity.x * velocity.x + velocity.z * velocity.z
        );
        float bodyScale = Math.max(0.6f, entity.getBbWidth());
        float radius = entity instanceof Boat
                ? Math.max(1.8f, bodyScale * 2.6f)
                : Math.max(1.0f, bodyScale * 1.8f);
        float amplitude = entity instanceof Boat
                ? clamp(0.055f + horizontalSpeed * 0.12f, 0.055f, 0.16f)
                : clamp(0.028f + horizontalSpeed * 0.08f, 0.028f, 0.10f);

        double x = entity.getX();
        double z = entity.getZ();
        DisturbanceKind kind = DisturbanceKind.WAKE;
        if (entity instanceof Boat) {
            float yawRadians = (float) Math.toRadians(entity.getYRot());
            double forwardX = -Math.sin(yawRadians);
            double forwardZ = Math.cos(yawRadians);
            x += forwardX * bodyScale * 0.9;
            z += forwardZ * bodyScale * 0.9;
            kind = DisturbanceKind.BOW_WAVE;
        }
        add(level, x, z, amplitude, radius, WAKE_LIFETIME_TICKS, kind);
    }

    /**
     * Samples the temporary height offset at a world-space surface vertex.
     *
     * @param sampleTick absolute client tick, including partial tick
     * @return a small signed height offset in blocks
     */
    public static float sampleHeight(Level level, double x, double z, float sampleTick) {
        if (!level.isClientSide() || DISTURBANCES.isEmpty()) {
            return 0.0f;
        }
        if (!WaterRenderingConfig.ENABLE_RIPPLES.get()) {
            DISTURBANCES.clear();
            LAST_ENTITY_WAKE_TICK.clear();
            return 0.0f;
        }
        prune(level.getGameTime());

        float height = 0.0f;
        for (Disturbance disturbance : DISTURBANCES) {
            float age = sampleTick - disturbance.startTick;
            if (age < 0.0f || age > disturbance.lifetimeTicks) {
                continue;
            }

            float life = age / disturbance.lifetimeTicks;
            float fade = (1.0f - life) * (1.0f - life);
            double dx = x - disturbance.x;
            double dz = z - disturbance.z;
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            float currentRadius = disturbance.radius * (0.30f + life * 1.15f);
            float centerWidth = Math.max(0.35f, disturbance.radius * 0.36f);
            float ringWidth = Math.max(0.22f, disturbance.radius * 0.16f);

            // The center term is the displaced footprint. The ring term is the
            // water pushed away from that footprint returning as a wake/ripple.
            float depression = -disturbance.amplitude
                    * gaussian(distance / centerWidth)
                    * fade;
            float ring = disturbance.amplitude
                    * disturbance.kind.ringScale
                    * gaussian((distance - currentRadius) / ringWidth)
                    * fade;
            height += depression + ring;
        }
        return clamp(height, -MAX_HEIGHT_OFFSET, MAX_HEIGHT_OFFSET);
    }

    private static boolean shouldRecord(Level level) {
        return level.isClientSide()
                && WaterRenderingConfig.ENABLE_RIPPLES.get()
                && WaterRenderingConfig.maxRipples() > 0;
    }

    private static void add(
            Level level,
            double x,
            double z,
            float amplitude,
            float radius,
            int lifetimeTicks,
            DisturbanceKind kind
    ) {
        prune(level.getGameTime());
        int cap = Math.max(4, WaterRenderingConfig.maxRipples() * 2);
        while (DISTURBANCES.size() >= cap) {
            DISTURBANCES.remove(0);
        }
        DISTURBANCES.add(new Disturbance(
                x,
                z,
                level.getGameTime(),
                Math.max(8, lifetimeTicks),
                clamp(amplitude, 0.0f, MAX_HEIGHT_OFFSET),
                Math.max(0.35f, radius),
                kind
        ));
    }

    private static void prune(long gameTime) {
        Iterator<Disturbance> iterator = DISTURBANCES.iterator();
        while (iterator.hasNext()) {
            Disturbance disturbance = iterator.next();
            if (gameTime - disturbance.startTick > disturbance.lifetimeTicks) {
                iterator.remove();
            }
        }
        LAST_ENTITY_WAKE_TICK.entrySet().removeIf(entry -> gameTime - entry.getValue() > 200L);
    }

    private static float gaussian(float normalizedDistance) {
        return (float) Math.exp(-(normalizedDistance * normalizedDistance));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum DisturbanceKind {
        IMPACT(0.75f),
        WAKE(0.55f),
        BOW_WAVE(0.95f);

        private final float ringScale;

        DisturbanceKind(float ringScale) {
            this.ringScale = ringScale;
        }
    }

    private record Disturbance(
            double x,
            double z,
            long startTick,
            int lifetimeTicks,
            float amplitude,
            float radius,
            DisturbanceKind kind
    ) {
    }
}
