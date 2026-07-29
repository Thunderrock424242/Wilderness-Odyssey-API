package com.thunder.wildernessodysseyapi.watersystem.water.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
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

    /** Maximum nearby impulses uploaded to the vertex shader each frame. */
    public static final int MAX_GPU_IMPULSES = 8;
    /** Float count for position/radius/strength and shape data of one impulse. */
    public static final int GPU_IMPULSE_STRIDE = 8;

    private static final int WAKE_INTERVAL_TICKS = 4;
    private static final int BOAT_WAKE_INTERVAL_TICKS = 2;
    private static final int IMPACT_LIFETIME_TICKS = 42;
    private static final int WAKE_LIFETIME_TICKS = 34;
    private static final float MAX_HEIGHT_OFFSET = 0.22f;

    private static final List<Disturbance> DISTURBANCES = new ArrayList<>();
    private static final Map<Integer, Long> LAST_ENTITY_WAKE_TICK = new HashMap<>();
    private static final Disturbance[] GPU_SELECTION = new Disturbance[MAX_GPU_IMPULSES];
    private static final double[] GPU_SELECTION_DISTANCE = new double[MAX_GPU_IMPULSES];
    private static Level activeLevel;

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
        if (!wakeIntervalElapsed(gameTime, lastWake, interval)) {
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
        if (!level.isClientSide() || !activate(level) || DISTURBANCES.isEmpty()) {
            return 0.0f;
        }
        if (!WaterRenderingConfig.ENABLE_RIPPLES.get()) {
            DISTURBANCES.clear();
            LAST_ENTITY_WAKE_TICK.clear();
            return 0.0f;
        }
        prune(level.getGameTime());

        float height = 0.0f;
        int selected = selectNearest(sampleTick, x, z);
        for (int index = 0; index < selected; index++) {
            Disturbance disturbance = GPU_SELECTION[index];
            float age = sampleTick - disturbance.startTick;
            float life = age / disturbance.lifetimeTicks;
            float fade = (1.0f - life) * (1.0f - life);
            double dx = x - disturbance.x;
            double dz = z - disturbance.z;
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            height += sampleImpulse(
                    distance,
                    disturbance.radius * (0.30f + life * 1.15f),
                    disturbance.amplitude * fade,
                    Math.max(0.35f, disturbance.radius * 0.36f),
                    Math.max(0.22f, disturbance.radius * 0.16f),
                    disturbance.kind.ringScale
            );
        }
        return clamp(height, -MAX_HEIGHT_OFFSET, MAX_HEIGHT_OFFSET);
    }

    /**
     * Writes the nearest active impulses into a reusable shader-upload array.
     *
     * <p>Each entry stores {@code x, z, radius, faded amplitude} followed by
     * {@code center width, ring width, ring scale, enabled}. Selection and
     * animation are client-tick based, capped, and never rebuild chunk meshes.</p>
     *
     * @return number of active entries written
     */
    public static int writeGpuImpulses(
            Level level,
            float sampleTick,
            double cameraX,
            double cameraZ,
            float[] destination
    ) {
        if (destination.length < MAX_GPU_IMPULSES * GPU_IMPULSE_STRIDE) {
            throw new IllegalArgumentException("GPU impulse destination is too small");
        }
        Arrays.fill(destination, 0.0f);
        if (!level.isClientSide() || !activate(level)
                || !WaterRenderingConfig.ENABLE_RIPPLES.get()) {
            return 0;
        }
        prune(level.getGameTime());
        int selected = selectNearest(sampleTick, cameraX, cameraZ);

        for (int index = 0; index < selected; index++) {
            Disturbance disturbance = GPU_SELECTION[index];
            float life = (sampleTick - disturbance.startTick) / disturbance.lifetimeTicks;
            float fade = (1.0f - life) * (1.0f - life);
            int offset = index * GPU_IMPULSE_STRIDE;
            destination[offset] = (float) disturbance.x;
            destination[offset + 1] = (float) disturbance.z;
            destination[offset + 2] = disturbance.radius * (0.30f + life * 1.15f);
            destination[offset + 3] = disturbance.amplitude * fade;
            destination[offset + 4] = Math.max(0.35f, disturbance.radius * 0.36f);
            destination[offset + 5] = Math.max(0.22f, disturbance.radius * 0.16f);
            destination[offset + 6] = disturbance.kind.ringScale;
            destination[offset + 7] = 1.0f;
        }
        return selected;
    }

    /** Clears cosmetic displacement state during a client-level handoff. */
    public static void clear() {
        DISTURBANCES.clear();
        LAST_ENTITY_WAKE_TICK.clear();
        Arrays.fill(GPU_SELECTION, null);
        activeLevel = null;
    }

    private static boolean shouldRecord(Level level) {
        return level.isClientSide()
                && activate(level)
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

    // The client has one active level. Switching it invalidates entity ids and
    // absolute ticks, so cosmetic state must never cross the dimension handoff.
    private static boolean activate(Level level) {
        if (activeLevel == level) {
            return true;
        }
        clear();
        activeLevel = level;
        return true;
    }

    private static boolean isActive(Disturbance disturbance, float age) {
        return age >= 0.0f && age <= disturbance.lifetimeTicks;
    }

    static boolean wakeIntervalElapsed(long gameTime, long lastWake, int intervalTicks) {
        // Treat the sentinel separately: subtracting Long.MIN_VALUE can
        // overflow and suppress the entity's first wake forever.
        return lastWake == Long.MIN_VALUE || gameTime - lastWake >= intervalTicks;
    }

    private static int selectNearest(float sampleTick, double sampleX, double sampleZ) {
        Arrays.fill(GPU_SELECTION, null);
        int selected = 0;
        for (Disturbance disturbance : DISTURBANCES) {
            if (!isActive(disturbance, sampleTick - disturbance.startTick)) {
                continue;
            }
            double dx = disturbance.x - sampleX;
            double dz = disturbance.z - sampleZ;
            double distanceSquared = dx * dx + dz * dz;
            if (selected < MAX_GPU_IMPULSES) {
                GPU_SELECTION[selected] = disturbance;
                GPU_SELECTION_DISTANCE[selected] = distanceSquared;
                selected++;
                continue;
            }

            int farthest = 0;
            for (int index = 1; index < selected; index++) {
                if (GPU_SELECTION_DISTANCE[index] > GPU_SELECTION_DISTANCE[farthest]) {
                    farthest = index;
                }
            }
            if (distanceSquared < GPU_SELECTION_DISTANCE[farthest]) {
                GPU_SELECTION[farthest] = disturbance;
                GPU_SELECTION_DISTANCE[farthest] = distanceSquared;
            }
        }
        return selected;
    }

    // Shared with the shader contract: a displaced footprint is surrounded by
    // the positive ring of water pushed away from the entity.
    static float sampleImpulse(
            float distance,
            float radius,
            float amplitude,
            float centerWidth,
            float ringWidth,
            float ringScale
    ) {
        float depression = -amplitude * gaussian(distance / Math.max(0.001f, centerWidth));
        float ring = amplitude * ringScale
                * gaussian((distance - radius) / Math.max(0.001f, ringWidth));
        return depression + ring;
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
