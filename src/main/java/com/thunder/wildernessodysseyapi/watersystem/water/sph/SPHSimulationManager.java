package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Singleton manager that oversees all active SPH fluid simulations in the world.
 * <p>
 * When a player places a water bucket, this manager creates an isolated {@link SPHSimulator}.
 * Simulations tick on the logical server thread because the collision pass queries
 * Minecraft block states and voxel shapes, which are not safe to read from worker threads.
 */
public class SPHSimulationManager {

    private static final SPHSimulationManager INSTANCE = new SPHSimulationManager();
    public static SPHSimulationManager get() { return INSTANCE; }

    /** A list of all currently active fluid simulations. Thread-safe for iteration. */
    private final List<SPHSimulator> active = new CopyOnWriteArrayList<>();

    /** * Callbacks queued when a fluid simulation comes to a stop.
     * These must be executed on the main thread so we can safely place Minecraft blocks.
     */
    private final Queue<Runnable> pendingSettleCallbacks = new ConcurrentLinkedQueue<>();

    /** Level identities already restored from SavedData during this server session. */
    private final Set<BlockGetter> restoredPersistentLevels =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** Settled authoritative bodies waiting for nearby canonical capacity. */
    private final Map<SPHSimulator, Long> settlementRetries = new IdentityHashMap<>();

    private SPHSimulationManager() {}

    /**
     * Initializes a new fluid simulation at the designated coordinates.
     *
     * @param x      The starting world X coordinate (usually a bucket click location).
     * @param y      The starting world Y coordinate.
     * @param z      The starting world Z coordinate.
     * @param level  The Minecraft block getter, used to calculate collisions.
     * @param placer The callback function used to generate physical fluid blocks once the water stops moving.
     * @return The newly created simulator instance.
     */
    public SPHSimulator createSimulation(float x, float y, float z, BlockGetter level, SettleBlockPlacer placer) {
        return createSimulation(x, y, z, level, placer,
                SPHConstants.PARTICLES_PER_BUCKET, 0.0f, 0.0f, 0.0f);
    }

    public SPHSimulator createSimulation(float x, float y, float z, BlockGetter level, SettleBlockPlacer placer,
                                         int requestedCount, float impulseX, float impulseY, float impulseZ) {
        runPendingSettleCallbacks();
        removeEmptySimulations();

        SPHSimulator existing = findMergeTarget(x, y, z, level, SPHConstants.MERGE_RADIUS);
        if (existing != null) {
            existing.spawnPulse(x, y, z, requestedCount, impulseX, impulseY, impulseZ);
            existing.addCanonicalVolumeUnits(WaterVolumeChunk.UNITS_PER_BLOCK);
            return existing;
        }

        if (countSimulations(level) >= SPHConstants.MAX_ACTIVE_SIMULATIONS) {
            SPHSimulator overloaded = findMergeTarget(x, y, z, level, SPHConstants.OVERLOAD_MERGE_RADIUS);
            if (overloaded == null) {
                boolean madeRoom = removeFirstSettledSimulation(level);
                overloaded = madeRoom ? null : findClosestReusable(x, y, z, level);
            }

            if (overloaded != null) {
                overloaded.spawnPulse(x, y, z, Math.min(requestedCount, SPHConstants.OVERLOAD_PARTICLES_PER_BUCKET),
                        impulseX, impulseY, impulseZ);
                overloaded.addCanonicalVolumeUnits(WaterVolumeChunk.UNITS_PER_BLOCK);
                return overloaded;
            }

            if (countSimulations(level) >= SPHConstants.MAX_ACTIVE_SIMULATIONS) {
                SPHSimulator closest = findClosestSimulation(x, y, z, level);
                if (level instanceof ServerLevel serverLevel) {
                    CanonicalWater.addVolume(
                            serverLevel,
                            BlockPos.containing(x, y, z),
                            WaterVolumeChunk.UNITS_PER_BLOCK,
                            impulseX,
                            impulseY,
                            impulseZ
                    );
                }
                return closest != null ? closest : new SPHSimulator(level);
            }
        }

        SPHSimulator sim = new SPHSimulator(level);
        sim.addCanonicalVolumeUnits(WaterVolumeChunk.UNITS_PER_BLOCK);
        configureSettlement(sim, level, placer);

        sim.spawnPulse(x, y, z, requestedCount, impulseX, impulseY, impulseZ);
        active.add(sim);
        return sim;
    }

    public SPHSimulator createTransientSimulation(float x, float y, float z, BlockGetter level,
                                                  int requestedCount, float impulseX, float impulseY, float impulseZ,
                                                  int lifetimeTicks) {
        runPendingSettleCallbacks();
        removeEmptySimulations();

        if (countTransientSimulations(level) >= SPHConstants.MAX_TRANSIENT_SHORE_SIMULATIONS) {
            removeFirstTransientSimulation(level);
        }

        if (countTransientSimulations(level) >= SPHConstants.MAX_TRANSIENT_SHORE_SIMULATIONS) {
            return null;
        }

        SPHSimulator sim = new SPHSimulator(level);
        sim.setTransientLifetimeTicks(lifetimeTicks);
        sim.spawnPulse(x, y, z, requestedCount, impulseX, impulseY, impulseZ);
        active.add(sim);
        return sim;
    }

    private SPHSimulator findMergeTarget(float x, float y, float z, BlockGetter level, float radius) {
        float mergeRadius2 = radius * radius;

        SPHSimulator best = null;
        float bestDistance2 = mergeRadius2;
        for (SPHSimulator sim : active) {
            if (sim.isTransientSimulation() || sim.getLevel() != level || !sim.hasCapacity()) {
                continue;
            }

            float distance2 = sim.distanceSquaredTo(x, y, z);
            if (distance2 < bestDistance2) {
                bestDistance2 = distance2;
                best = sim;
            }
        }

        return best;
    }

    private SPHSimulator findClosestReusable(float x, float y, float z, BlockGetter level) {
        SPHSimulator best = null;
        float bestDistance2 = Float.MAX_VALUE;
        for (SPHSimulator sim : active) {
            if (sim.isTransientSimulation() || sim.getLevel() != level || !sim.hasCapacity()) {
                continue;
            }

            float distance2 = sim.distanceSquaredTo(x, y, z);
            if (distance2 < bestDistance2) {
                bestDistance2 = distance2;
                best = sim;
            }
        }
        return best;
    }

    private SPHSimulator findClosestSimulation(float x, float y, float z, BlockGetter level) {
        SPHSimulator best = null;
        float bestDistance2 = Float.MAX_VALUE;
        for (SPHSimulator sim : active) {
            if (sim.isTransientSimulation() || sim.getLevel() != level) {
                continue;
            }

            float distance2 = sim.distanceSquaredTo(x, y, z);
            if (distance2 < bestDistance2) {
                bestDistance2 = distance2;
                best = sim;
            }
        }
        return best;
    }

    private int countSimulations(BlockGetter level) {
        int count = 0;
        for (SPHSimulator sim : active) {
            if (!sim.isTransientSimulation() && sim.getLevel() == level) count++;
        }
        return count;
    }

    private int countTransientSimulations(BlockGetter level) {
        int count = 0;
        for (SPHSimulator sim : active) {
            if (sim.isTransientSimulation() && sim.getLevel() == level) count++;
        }
        return count;
    }

    private boolean removeFirstTransientSimulation(BlockGetter level) {
        for (SPHSimulator sim : active) {
            if (sim.getLevel() == level && sim.isTransientSimulation()) {
                active.remove(sim);
                return true;
            }
        }
        return false;
    }

    private boolean removeFirstSettledSimulation(BlockGetter level) {
        for (SPHSimulator sim : active) {
            if (sim.getLevel() == level && sim.isSettled()) {
                if (level instanceof ServerLevel serverLevel && sim.getCanonicalVolumeUnits() > 0) {
                    if (!materializeCanonicalVolume(serverLevel, sim, sim.getRenderParticles(), true)) {
                        sim.ensureResidualMarker();
                        settlementRetries.put(sim, serverLevel.getGameTime() + 20L);
                        continue;
                    }
                }
                settlementRetries.remove(sim);
                active.remove(sim);
                return true;
            }
        }
        return false;
    }

    private void removeEmptySimulations() {
        active.removeIf(sim -> sim.particleCount() == 0 && sim.getCanonicalVolumeUnits() <= 0);
        settlementRetries.keySet().removeIf(sim -> !active.contains(sim));
    }

    private void runPendingSettleCallbacks() {
        Runnable cb;
        while ((cb = pendingSettleCallbacks.poll()) != null) {
            cb.run();
        }
    }

    /**
     * Triggers the physics step for all active simulations.
     *
     * @param deltaTime The time elapsed since the last tick.
     */
    public void tickAll(float deltaTime) {
        // First, safely place blocks on the main thread for any simulations that finished last tick.
        runPendingSettleCallbacks();

        for (SPHSimulator sim : active) {
            sim.tick(deltaTime);
            if (sim.particleCount() == 0 && sim.getCanonicalVolumeUnits() <= 0) {
                active.remove(sim);
                settlementRetries.remove(sim);
            }
        }
    }

    public void tickLevel(BlockGetter level, float deltaTime) {
        runPendingSettleCallbacks();

        for (SPHSimulator sim : active) {
            if (sim.getLevel() != level) {
                continue;
            }

            sim.tick(deltaTime);
            retrySettlementIfNeeded(level, sim);
            if ((sim.particleCount() == 0 && sim.getCanonicalVolumeUnits() <= 0)
                    || sim.isRemoteExpired()) {
                active.remove(sim);
                settlementRetries.remove(sim);
            }
        }
    }

    /**
     * Creates or updates the client-side mirror for one server-owned fluid body.
     *
     * @param simulationId stable ID assigned by the authoritative server simulator
     * @param level client level that owns the mirror
     * @param particles complete decoded particle snapshot
     */
    public void applyRemoteSnapshot(UUID simulationId, BlockGetter level, List<SPHParticle> particles) {
        SPHSimulator mirror = null;
        for (SPHSimulator sim : active) {
            if (sim.isRemoteMirror()
                    && sim.getLevel() == level
                    && sim.getSimulationId().equals(simulationId)) {
                mirror = sim;
                break;
            }
        }

        if (mirror == null) {
            mirror = SPHSimulator.createRemoteMirror(simulationId, level);
            active.add(mirror);
        }
        mirror.applyRemoteSnapshot(particles);
    }

    /**
     * Restores a dimension's volumetric bodies exactly once per loaded level instance.
     *
     * @param level server level whose SavedData should be loaded
     */
    public void ensurePersistentLevelLoaded(ServerLevel level) {
        if (restoredPersistentLevels.add(level)) {
            SphWaterSavedData.get(level).restoreInto(this, level);
        }
    }

    /**
     * Captures the current authoritative state into dimension-scoped SavedData.
     *
     * @param level server level whose bodies should be persisted
     */
    public void capturePersistentLevel(ServerLevel level) {
        SphWaterSavedData.get(level).capture(getActive(level));
    }

    void restorePersistentSimulation(
            UUID simulationId,
            ServerLevel level,
            List<SPHParticle> particles,
            int canonicalVolumeUnits
    ) {
        if (particles.isEmpty() || countSimulations(level) >= SPHConstants.MAX_ACTIVE_SIMULATIONS) {
            return;
        }
        for (SPHSimulator sim : active) {
            if (sim.getLevel() == level && sim.getSimulationId().equals(simulationId)) {
                return;
            }
        }
        SPHSimulator simulator = SPHSimulator.restoreAuthoritative(
                simulationId,
                level,
                particles,
                canonicalVolumeUnits
        );
        configureSettlement(simulator, level, pos -> { });
        active.add(simulator);
    }

    // Converts the mobile particle body into exact fixed-point chunk volume
    // once its motion settles, then removes the redundant SPH representation.
    private void configureSettlement(SPHSimulator simulator, BlockGetter level, SettleBlockPlacer fallbackPlacer) {
        simulator.setSettleListener(finalParticles -> pendingSettleCallbacks.add(() -> {
            if (level instanceof ServerLevel serverLevel && simulator.getCanonicalVolumeUnits() > 0) {
                if (materializeCanonicalVolume(serverLevel, simulator, finalParticles, true)) {
                    settlementRetries.remove(simulator);
                    active.remove(simulator);
                } else {
                    simulator.ensureResidualMarker();
                    settlementRetries.put(simulator, serverLevel.getGameTime() + 20L);
                }
            } else if (SPHConstants.CONVERT_SETTLED_TO_BLOCKS) {
                Set<BlockPos> placed = new HashSet<>();
                for (SPHParticle particle : finalParticles) {
                    BlockPos pos = BlockPos.containing(
                            particle.position.x,
                            particle.position.y,
                            particle.position.z
                    );
                    if (placed.add(pos)) {
                        fallbackPlacer.placeBlock(pos);
                    }
                }
                active.remove(simulator);
            } else {
                active.remove(simulator);
            }
        }));
    }

    private void retrySettlementIfNeeded(BlockGetter level, SPHSimulator simulator) {
        Long retryAt = settlementRetries.get(simulator);
        if (retryAt == null
                || !(level instanceof ServerLevel serverLevel)
                || serverLevel.getGameTime() < retryAt) {
            return;
        }

        if (materializeCanonicalVolume(
                serverLevel,
                simulator,
                new ArrayList<>(simulator.getRenderParticles()),
                false
        )) {
            settlementRetries.remove(simulator);
            active.remove(simulator);
        } else {
            settlementRetries.put(simulator, serverLevel.getGameTime() + 20L);
        }
    }

    private static boolean materializeCanonicalVolume(
            ServerLevel level,
            SPHSimulator simulator,
            List<SPHParticle> particles,
            boolean warnOnFailure
    ) {
        List<SettlementWrite> writes = new ArrayList<>();
        if (particles.isEmpty()) {
            int remaining = deposit(level, BlockPos.containing(
                    simulator.getCenterX(),
                    simulator.getCenterY(),
                    simulator.getCenterZ()
            ), simulator.getCanonicalVolumeUnits(), writes);
            return finishSettlement(level, simulator, remaining, writes, warnOnFailure);
        }

        Map<BlockPos, Integer> particleCounts = new LinkedHashMap<>();
        for (SPHParticle particle : particles) {
            particleCounts.merge(BlockPos.containing(
                    particle.position.x,
                    particle.position.y,
                    particle.position.z
            ), 1, Integer::sum);
        }

        int remainingVolume = simulator.getCanonicalVolumeUnits();
        int remainingParticles = particles.size();
        for (Map.Entry<BlockPos, Integer> entry : particleCounts.entrySet()) {
            int share = remainingParticles == entry.getValue()
                    ? remainingVolume
                    : (int) ((long) remainingVolume * entry.getValue() / remainingParticles);
            remainingVolume -= share;
            remainingParticles -= entry.getValue();
            remainingVolume += deposit(level, entry.getKey(), share, writes);
        }

        if (remainingVolume > 0) {
            remainingVolume = deposit(level, BlockPos.containing(
                    simulator.getCenterX(),
                    simulator.getCenterY(),
                    simulator.getCenterZ()
            ), remainingVolume, writes);
        }
        return finishSettlement(level, simulator, remainingVolume, writes, warnOnFailure);
    }

    // Searches a compact settlement area so a particle body that stops against
    // terrain keeps its exact volume instead of being written inside solids.
    private static int deposit(
            ServerLevel level,
            BlockPos target,
            int volumeUnits,
            List<SettlementWrite> writes
    ) {
        int remaining = volumeUnits;
        for (int offsetY = 0; offsetY <= 4 && remaining > 0; offsetY++) {
            for (int radius = 0; radius <= 3 && remaining > 0; radius++) {
                for (int offsetX = -radius; offsetX <= radius && remaining > 0; offsetX++) {
                    for (int offsetZ = -radius; offsetZ <= radius && remaining > 0; offsetZ++) {
                        if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
                            continue;
                        }
                        BlockPos destination = target.offset(offsetX, offsetY, offsetZ);
                        WaterVolumeChunk.WaterCell previous = CanonicalWater.getOrImport(level, destination);
                        int accepted = CanonicalWater.addVolume(
                                level,
                                destination,
                                remaining,
                                0.0f,
                                0.0f,
                                0.0f
                        );
                        if (accepted > 0) {
                            writes.add(new SettlementWrite(destination.immutable(), previous));
                        }
                        remaining -= accepted;
                    }
                }
            }
        }
        return remaining;
    }

    private static boolean finishSettlement(
            ServerLevel level,
            SPHSimulator simulator,
            int remainingVolume,
            List<SettlementWrite> writes,
            boolean warnOnFailure
    ) {
        if (remainingVolume <= 0) {
            return true;
        }

        // Partial materialization would duplicate or lose volume. Restore every
        // touched cell and retain the complete settled SPH body for a later retry.
        for (int index = writes.size() - 1; index >= 0; index--) {
            SettlementWrite write = writes.get(index);
            CanonicalWater.set(level, write.pos, write.previous, true);
        }
        if (warnOnFailure) {
            warnIfVolumeCouldNotSettle(simulator, remainingVolume);
        }
        return false;
    }

    private static void warnIfVolumeCouldNotSettle(SPHSimulator simulator, int remainingVolume) {
        if (remainingVolume > 0) {
            ModConstants.LOGGER.warn(
                    "Could not settle {} of {} canonical water units near SPH body {}; retaining it for retry.",
                    remainingVolume,
                    simulator.getCanonicalVolumeUnits(),
                    simulator.getSimulationId()
            );
        }
    }

    /**
     * Removes every body owned by an unloading level.
     *
     * @param level level instance being discarded
     */
    public void clearLevel(BlockGetter level) {
        settlementRetries.keySet().removeIf(sim -> sim.getLevel() == level);
        active.removeIf(sim -> sim.getLevel() == level);
        restoredPersistentLevels.remove(level);
    }

    /**
     * Retrieves an unmodifiable view of all currently running simulations.
     * Mostly used by the rendering engine to loop through and draw particles.
     *
     * @return A list of active SPH simulations.
     */
    public List<SPHSimulator> getActive() {
        return Collections.unmodifiableList(active);
    }

    public List<SPHSimulator> getActive(BlockGetter level) {
        List<SPHSimulator> result = new ArrayList<>();
        for (SPHSimulator sim : active) {
            if (sim.getLevel() == level) {
                result.add(sim);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public void collectActive(List<SPHSimulator> result) {
        result.addAll(active);
    }

    public void collectActive(BlockGetter level, List<SPHSimulator> result) {
        for (SPHSimulator sim : active) {
            if (sim.getLevel() == level) {
                result.add(sim);
            }
        }
    }

    /**
     * Samples mobile SPH water around a point for entity and API integration.
     * The center-distance rejection keeps the bounded particle scan inexpensive.
     */
    public MobileWaterSample sampleAt(BlockGetter level, double x, double y, double z) {
        int neighbours = 0;
        float velocityX = 0.0f;
        float velocityY = 0.0f;
        float velocityZ = 0.0f;
        for (SPHSimulator simulator : active) {
            if (simulator.getLevel() != level
                    || simulator.isTransientSimulation()
                    || simulator.distanceSquaredTo((float) x, (float) y, (float) z) > 36.0f) {
                continue;
            }
            for (SPHParticle particle : simulator.getRenderParticles()) {
                double dx = particle.position.x - x;
                double dy = particle.position.y - y;
                double dz = particle.position.z - z;
                if (dx * dx + dy * dy + dz * dz > 0.25) {
                    continue;
                }
                neighbours++;
                velocityX += particle.velocity.x;
                velocityY += particle.velocity.y;
                velocityZ += particle.velocity.z;
            }
        }
        if (neighbours == 0) {
            return MobileWaterSample.DRY;
        }
        float inverseCount = 1.0f / neighbours;
        return new MobileWaterSample(
                true,
                velocityX * inverseCount,
                velocityY * inverseCount,
                velocityZ * inverseCount
        );
    }

    /** Point sample from a mobile server-owned or synchronized SPH body. */
    public record MobileWaterSample(boolean wet, float velocityX, float velocityY, float velocityZ) {
        private static final MobileWaterSample DRY = new MobileWaterSample(false, 0.0f, 0.0f, 0.0f);
    }

    private record SettlementWrite(BlockPos pos, WaterVolumeChunk.WaterCell previous) {
    }

    /**
     * Terminates all physics processing. Should be called during server/client shutdown.
     */
    public void shutdown() {
        active.clear();
        pendingSettleCallbacks.clear();
        settlementRetries.clear();
        restoredPersistentLevels.clear();
    }

    /**
     * Interface defining how a finalized fluid particle converts back into a Minecraft block.
     */
    @FunctionalInterface
    public interface SettleBlockPlacer {
        void placeBlock(BlockPos pos);
    }
}
