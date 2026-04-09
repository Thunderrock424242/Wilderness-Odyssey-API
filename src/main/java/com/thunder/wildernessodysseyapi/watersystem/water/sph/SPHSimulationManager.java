package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.*;
import java.util.concurrent.*;

/**
 * SPHSimulationManager
 *
 * Singleton that owns and ticks all active SPH fluid simulations.
 * Each bucket placement creates one SPHSimulator instance.
 *
 * Simulations run on a dedicated background thread pool so the
 * render thread never blocks waiting for physics.
 *
 * When a simulation settles, its particles are converted to
 * Minecraft fluid blocks via the provided settle callback.
 */
public class SPHSimulationManager {

    private static final SPHSimulationManager INSTANCE = new SPHSimulationManager();
    public static SPHSimulationManager get() { return INSTANCE; }

    // All currently active simulations
    private final List<SPHSimulator> active = new CopyOnWriteArrayList<>();

    // Thread pool for physics ticking (2 threads — keep headroom for game)
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "sph-physics");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    // Per-simulation settle callback: called on game thread next tick
    private final Queue<Runnable> pendingSettleCallbacks = new ConcurrentLinkedQueue<>();

    private SPHSimulationManager() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Create a new fluid simulation at world position (x, y, z).
     * Call this when a player places a water bucket.
     */
    public SPHSimulator createSimulation(float x, float y, float z,
                                          BlockGetter level,
                                          SettleBlockPlacer placer) {
        SPHSimulator sim = new SPHSimulator(level);

        sim.setSettleListener(finalParticles -> {
            // Enqueue block placement to run on the game/server thread
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
     * Tick all active simulations.
     * Call from the client render tick (for visual simulations)
     * or the server tick (for block-placement simulations).
     */
    public void tickAll(float deltaTime) {
        // Drain settled callbacks (must run on calling thread)
        Runnable cb;
        while ((cb = pendingSettleCallbacks.poll()) != null) {
            cb.run();
        }

        // Submit each sim to the thread pool
        List<Future<?>> futures = new ArrayList<>();
        for (SPHSimulator sim : active) {
            futures.add(executor.submit(() -> sim.tick(deltaTime)));
        }
        // Wait for all to finish (keeps frame ordering deterministic)
        for (Future<?> f : futures) {
            try { f.get(); }
            catch (Exception e) { /* log if needed */ }
        }
    }

    /** Get all active simulators (read-only, for rendering). */
    public List<SPHSimulator> getActive() {
        return Collections.unmodifiableList(active);
    }

    /** Shut down the thread pool cleanly on game exit. */
    public void shutdown() {
        executor.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Callback interface
    // -------------------------------------------------------------------------

    @FunctionalInterface
    public interface SettleBlockPlacer {
        void placeBlock(BlockPos pos);
    }
}
