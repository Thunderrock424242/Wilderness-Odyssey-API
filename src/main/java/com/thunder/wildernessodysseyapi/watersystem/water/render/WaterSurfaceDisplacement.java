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
    private static final int IMPACT_FOAM_LIFETIME_TICKS = 82;
    private static final int WAKE_FOAM_LIFETIME_TICKS = 70;
    private static final int BOW_WAVE_FOAM_LIFETIME_TICKS = 96;
    /** Shared CPU/GPU cap for the sum of all active impulse heights. */
    public static final float MAX_COMBINED_HEIGHT_OFFSET = 0.25f;

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
        Vec3 velocity = entity.getDeltaMovement();
        float speed = (float) velocity.length();
        spawnImpact(entity, x, z, clamp(0.10f + speed * 1.8f, 0.10f, 1.0f));
    }

    /** Adds an impact using pre-contact energy retained by the entry handler. */
    public static void spawnImpact(Entity entity, double x, double z, float impactStrength) {
        Level level = entity.level();
        if (!shouldRecord(level)) {
            return;
        }

        float strength = clamp(impactStrength, 0.0f, 1.0f);
        float radius = clamp(
                entity.getBbWidth() * (1.30f + strength * 0.90f),
                0.70f,
                3.20f
        );
        float amplitude = clamp(
                0.025f + strength * 0.115f + entity.getBbHeight() * 0.004f,
                0.025f,
                0.16f
        );
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
        if (entity instanceof Boat && horizontalSpeed > 0.03f) {
            // Two aft lobes accumulate a spreading wake behind a moving hull.
            // Reuse the existing capped disturbance pool and GPU selection.
            double forwardX = velocity.x / horizontalSpeed;
            double forwardZ = velocity.z / horizontalSpeed;
            double aftX = entity.getX() - forwardX * bodyScale;
            double aftZ = entity.getZ() - forwardZ * bodyScale;
            double spread = bodyScale * 0.65;
            add(level, aftX - forwardZ * spread, aftZ + forwardX * spread,
                    amplitude * 0.45f, radius * 0.70f, WAKE_LIFETIME_TICKS, DisturbanceKind.WAKE);
            add(level, aftX + forwardZ * spread, aftZ - forwardX * spread,
                    amplitude * 0.45f, radius * 0.70f, WAKE_LIFETIME_TICKS, DisturbanceKind.WAKE);
        }
    }

    /**
     * Samples the temporary height offset at a world-space surface vertex.
     *
     * @param sampleTick absolute client tick, including partial tick
     * @return a small signed height offset in blocks
     */
    public static float sampleHeight(Level level, double x, double z, float sampleTick) {
        return sampleHeight(level, x, z, (double) sampleTick, x, z);
    }

    /**
     * Samples the temporary height using the same impulse-selection origin as
     * the GPU upload.
     *
     * <p>Boat footprint points pass the render camera as the selection origin,
     * ensuring all points sum the same bounded eight wakes visible in the
     * shader instead of independently choosing a different nearest set.</p>
     */
    public static float sampleHeight(
            Level level,
            double x,
            double z,
            double sampleTick,
            double selectionX,
            double selectionZ
    ) {
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
        int selected = selectNearest(sampleTick, selectionX, selectionZ, false);
        for (int index = 0; index < selected; index++) {
            Disturbance disturbance = GPU_SELECTION[index];
            double age = sampleTick - disturbance.startTick;
            float life = (float) (age / disturbance.displacementLifetimeTicks);
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
        return clampCombinedHeight(height);
    }

    /**
     * Writes the nearest active impulses into a reusable shader-upload array.
     *
     * <p>Each entry stores {@code x, z, radius, faded amplitude} followed by
     * {@code center width, ring width, ring scale, persistent foam strength}.
     * Selection and animation are client-tick based, capped, and never rebuild
     * chunk meshes.</p>
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
        return writeGpuImpulses(level, (double) sampleTick, cameraX, cameraZ, destination);
    }

    /** Double-tick variant that preserves wake age in long-running worlds. */
    public static int writeGpuImpulses(
            Level level,
            double sampleTick,
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
        float foamScale = WaterRenderingConfig.persistentWakeFoamScale();
        int selected = selectNearest(sampleTick, cameraX, cameraZ, foamScale > 0.0f);
        double anchorX = impulseAnchor(cameraX);
        double anchorZ = impulseAnchor(cameraZ);

        for (int index = 0; index < selected; index++) {
            Disturbance disturbance = GPU_SELECTION[index];
            double age = sampleTick - disturbance.startTick;
            float life = clamp(
                    (float) (age / disturbance.displacementLifetimeTicks),
                    0.0f,
                    1.0f
            );
            float fade = age <= disturbance.displacementLifetimeTicks
                    ? (1.0f - life) * (1.0f - life)
                    : 0.0f;
            float foamStrength = persistentFoamEnvelope(
                    age,
                    disturbance.displacementLifetimeTicks,
                    disturbance.foamLifetimeTicks
            ) * disturbance.kind.foamScale * foamScale;
            int offset = index * GPU_IMPULSE_STRIDE;
            destination[offset] = (float) (disturbance.x - anchorX);
            destination[offset + 1] = (float) (disturbance.z - anchorZ);
            destination[offset + 2] = disturbance.radius * (0.30f + life * 1.15f);
            destination[offset + 3] = disturbance.amplitude * fade;
            destination[offset + 4] = Math.max(0.35f, disturbance.radius * 0.36f);
            destination[offset + 5] = Math.max(0.22f, disturbance.radius * 0.16f);
            destination[offset + 6] = disturbance.kind.ringScale;
            destination[offset + 7] = foamStrength;
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
                kind.foamLifetimeTicks,
                clamp(amplitude, 0.0f, MAX_COMBINED_HEIGHT_OFFSET),
                Math.max(0.35f, radius),
                kind
        ));
    }

    private static void prune(long gameTime) {
        Iterator<Disturbance> iterator = DISTURBANCES.iterator();
        while (iterator.hasNext()) {
            Disturbance disturbance = iterator.next();
            if (gameTime - disturbance.startTick
                    > Math.max(disturbance.displacementLifetimeTicks, disturbance.foamLifetimeTicks)) {
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

    private static boolean isActive(
            Disturbance disturbance,
            double age,
            boolean includePersistentFoam
    ) {
        int lifetime = includePersistentFoam
                ? Math.max(disturbance.displacementLifetimeTicks, disturbance.foamLifetimeTicks)
                : disturbance.displacementLifetimeTicks;
        return age >= 0.0f && age <= lifetime;
    }

    static boolean wakeIntervalElapsed(long gameTime, long lastWake, int intervalTicks) {
        // Treat the sentinel separately: subtracting Long.MIN_VALUE can
        // overflow and suppress the entity's first wake forever.
        return lastWake == Long.MIN_VALUE || gameTime - lastWake >= intervalTicks;
    }

    private static int selectNearest(
            double sampleTick,
            double sampleX,
            double sampleZ,
            boolean includePersistentFoam
    ) {
        Arrays.fill(GPU_SELECTION, null);
        int selected = 0;

        // Physical disturbances are selected first so the GPU always receives
        // the same nearest active height impulses used by CPU immersion and
        // boat-footprint sampling. Older foam tails may use only spare slots.
        for (Disturbance disturbance : DISTURBANCES) {
            double age = sampleTick - disturbance.startTick;
            if (!isActive(disturbance, age, false)) {
                continue;
            }
            selected = retainNearest(
                    disturbance, sampleX, sampleZ, selected, 0);
        }
        if (!includePersistentFoam || selected >= MAX_GPU_IMPULSES) {
            return selected;
        }

        int physicalCount = selected;
        for (Disturbance disturbance : DISTURBANCES) {
            double age = sampleTick - disturbance.startTick;
            boolean foamTailOnly = age > disturbance.displacementLifetimeTicks
                    && isActive(disturbance, age, true);
            if (!foamTailOnly) {
                continue;
            }
            selected = retainNearest(
                    disturbance, sampleX, sampleZ, selected, physicalCount);
        }
        return selected;
    }

    private static int retainNearest(
            Disturbance disturbance,
            double sampleX,
            double sampleZ,
            int selected,
            int replacementStart
    ) {
        double dx = disturbance.x - sampleX;
        double dz = disturbance.z - sampleZ;
        double distanceSquared = dx * dx + dz * dz;
        if (selected < MAX_GPU_IMPULSES) {
            GPU_SELECTION[selected] = disturbance;
            GPU_SELECTION_DISTANCE[selected] = distanceSquared;
            return selected + 1;
        }

        int farthest = replacementStart;
        for (int index = replacementStart + 1; index < selected; index++) {
            if (GPU_SELECTION_DISTANCE[index] > GPU_SELECTION_DISTANCE[farthest]) {
                farthest = index;
            }
        }
        if (distanceSquared < GPU_SELECTION_DISTANCE[farthest]) {
            GPU_SELECTION[farthest] = disturbance;
            GPU_SELECTION_DISTANCE[farthest] = distanceSquared;
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

    static float clampCombinedHeight(float height) {
        return clamp(height, -MAX_COMBINED_HEIGHT_OFFSET, MAX_COMBINED_HEIGHT_OFFSET);
    }

    /**
     * Keeps foam visible after the short physical displacement settles.
     *
     * <p>The plateau lasts through the displacement lifetime, then a smooth
     * tail removes the foam before the bounded disturbance slot is released.</p>
     */
    static float persistentFoamEnvelope(
            double ageTicks,
            int displacementLifetimeTicks,
            int foamLifetimeTicks
    ) {
        int displacementLifetime = Math.max(1, displacementLifetimeTicks);
        int foamLifetime = Math.max(displacementLifetime, foamLifetimeTicks);
        if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= foamLifetime) {
            return 0.0f;
        }
        if (ageTicks <= displacementLifetime || foamLifetime == displacementLifetime) {
            return 1.0f;
        }
        float tail = (float) ((ageTicks - displacementLifetime)
                / (foamLifetime - (double) displacementLifetime));
        float smoothTail = tail * tail * (3.0f - 2.0f * tail);
        return 1.0f - smoothTail;
    }

    /** Returns the chunk-aligned frame used for precision-safe GPU wake positions. */
    static double impulseAnchor(double coordinate) {
        return Math.floor(coordinate / 16.0) * 16.0;
    }

    private static float gaussian(float normalizedDistance) {
        return (float) Math.exp(-(normalizedDistance * normalizedDistance));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum DisturbanceKind {
        IMPACT(0.75f, 0.82f, IMPACT_FOAM_LIFETIME_TICKS),
        WAKE(0.55f, 0.58f, WAKE_FOAM_LIFETIME_TICKS),
        BOW_WAVE(0.95f, 1.0f, BOW_WAVE_FOAM_LIFETIME_TICKS);

        private final float ringScale;
        private final float foamScale;
        private final int foamLifetimeTicks;

        DisturbanceKind(float ringScale, float foamScale, int foamLifetimeTicks) {
            this.ringScale = ringScale;
            this.foamScale = foamScale;
            this.foamLifetimeTicks = foamLifetimeTicks;
        }
    }

    private record Disturbance(
            double x,
            double z,
            long startTick,
            int displacementLifetimeTicks,
            int foamLifetimeTicks,
            float amplitude,
            float radius,
            DisturbanceKind kind
    ) {
    }

}
