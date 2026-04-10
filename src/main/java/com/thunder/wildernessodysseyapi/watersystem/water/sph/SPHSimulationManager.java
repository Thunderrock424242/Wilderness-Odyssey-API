package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.*;
import java.util.concurrent.*;

/**
 * A Singleton manager that oversees all active SPH fluid simulations in the world.
 * <p>
 * When a player places a water bucket, this manager creates an isolated {@link SPHSimulator}.
 * To prevent the server or client from lagging, it offloads the heavy physics math
 * into the centralized {@link AsyncTaskManager} CPU pool.
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
        SPHSimulator sim = new SPHSimulator(level);

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

        sim.spawnBucket(x, y, z);
        active.add(sim);
        return sim;
    }

    /**
     * Triggers the physics step for all active simulations simultaneously.
     * <p>
     * <b>Performance Note:</b> Each simulation's math is dispatched to the background async thread pool.
     * The main thread waits for them to finish to ensure deterministic visual rendering, but
     * does not waste main-thread CPU cycles doing the math itself.
     *
     * @param deltaTime The time elapsed since the last tick.
     */
    public void tickAll(float deltaTime) {
        // First, safely place blocks on the main thread for any simulations that finished last tick.
        Runnable cb;
        while ((cb = pendingSettleCallbacks.poll()) != null) {
            cb.run();
        }

        // Dispatch all physics math to the background CPU pool.
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (SPHSimulator sim : active) {
            futures.add(AsyncTaskManager.submitCpuTask("SPH_Tick", () -> {
                sim.tick(deltaTime);
                return Optional.empty(); // No main-thread task needed; state is read by renderer
            }));
        }

        // Wait for the background pool to finish before moving on to rendering.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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