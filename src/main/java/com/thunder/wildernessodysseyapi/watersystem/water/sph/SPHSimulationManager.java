package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import net.minecraft.core.BlockPos;
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
                return overloaded;
            }

            if (countSimulations(level) >= SPHConstants.MAX_ACTIVE_SIMULATIONS) {
                SPHSimulator closest = findClosestSimulation(x, y, z, level);
                return closest != null ? closest : new SPHSimulator(level);
            }
        }

        SPHSimulator sim = new SPHSimulator(level);

        if (SPHConstants.CONVERT_SETTLED_TO_BLOCKS) {
            sim.setSettleListener(finalParticles -> {
                pendingSettleCallbacks.add(() -> {
                    Set<BlockPos> placed = new HashSet<>();
                    for (SPHParticle p : finalParticles) {
                        BlockPos bp = new BlockPos(
                                (int)Math.floor(p.position.x),
                                (int)Math.floor(p.position.y),
                                (int)Math.floor(p.position.z)
                        );
                        if (!placed.contains(bp)) {
                            placed.add(bp);
                            placer.placeBlock(bp);
                        }
                    }
                    active.remove(sim);
                });
            });
        }

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
                active.remove(sim);
                return true;
            }
        }
        return false;
    }

    private void removeEmptySimulations() {
        active.removeIf(sim -> sim.particleCount() == 0);
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
            if (sim.particleCount() == 0) {
                active.remove(sim);
            }
        }
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

    /**
     * Terminates all physics processing. Should be called during server/client shutdown.
     */
    public void shutdown() {
        active.clear();
        pendingSettleCallbacks.clear();
    }

    /**
     * Interface defining how a finalized fluid particle converts back into a Minecraft block.
     */
    @FunctionalInterface
    public interface SettleBlockPlacer {
        void placeBlock(BlockPos pos);
    }
}
