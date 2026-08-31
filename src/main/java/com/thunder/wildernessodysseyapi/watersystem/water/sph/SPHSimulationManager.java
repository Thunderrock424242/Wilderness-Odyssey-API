package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderingConfig;
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
 * Server-owned SPH is reserved for tiny gameplay-critical active water such as
 * falling canonical volume. Visual splashes are normally spawned from compact
 * client events and do not own persistent water volume. Authoritative
 * simulations tick on the logical server thread because their collision pass
 * queries Minecraft block states and voxel shapes, which are not safe to read
 * from worker threads.
 */
public class SPHSimulationManager {

    private static final SPHSimulationManager INSTANCE = new SPHSimulationManager();
    public static SPHSimulationManager get() { return INSTANCE; }

    /** A list of all currently active fluid simulations. Thread-safe for iteration. */
    private final List<SPHSimulator> active = new CopyOnWriteArrayList<>();

    /** Settlements queued when a fluid simulation comes to a stop.
     * These retain their level identity so unload can flush or discard them safely.
     */
    private final Queue<PendingSettlement> pendingSettlements = new ConcurrentLinkedQueue<>();

    /** Level identities already restored from SavedData during this server session. */
    private final Set<BlockGetter> restoredPersistentLevels =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** Settled authoritative bodies waiting for nearby canonical capacity. */
    private final Map<SPHSimulator, Long> settlementRetries = new IdentityHashMap<>();

    /** Per-level round-robin cursor that prevents particle-budget starvation. */
    private final Map<BlockGetter, Integer> tickCursors =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private SPHSimulationManager() {}

    public SPHSimulator createTransientSimulation(float x, float y, float z, BlockGetter level,
                                                  int requestedCount, float impulseX, float impulseY, float impulseZ,
                                                  int lifetimeTicks) {
        return createTransientSimulation(
                x,
                y,
                z,
                level,
                requestedCount,
                impulseX,
                impulseY,
                impulseZ,
                lifetimeTicks,
                SPHConstants.MAX_TRANSIENT_SHORE_SIMULATIONS
        );
    }

    private SPHSimulator createTransientSimulation(
            float x,
            float y,
            float z,
            BlockGetter level,
            int requestedCount,
            float impulseX,
            float impulseY,
            float impulseZ,
            int lifetimeTicks,
            int maxTransientSimulations
    ) {
        runPendingSettleCallbacks(level);
        removeEmptySimulations();

        int maxSimulations = Math.max(0, maxTransientSimulations);
        if (maxSimulations <= 0) {
            return null;
        }

        if (countTransientSimulations(level) >= maxSimulations) {
            removeFirstTransientSimulation(level);
        }

        if (countTransientSimulations(level) >= maxSimulations) {
            return null;
        }

        SPHSimulator sim = new SPHSimulator(level);
        sim.setTransientLifetimeTicks(lifetimeTicks);
        sim.spawnPulse(x, y, z, requestedCount, impulseX, impulseY, impulseZ);
        active.add(sim);
        return sim;
    }

    /**
     * Creates a client-owned visual SPH effect from a compact network event.
     *
     * <p>The active client quality profile clamps particle count, lifetime, and
     * active effect count. This path intentionally does not own canonical
     * volume and should not be used for gameplay-critical water.</p>
     */
    public SPHSimulator createLocalVisualEffect(
            float x,
            float y,
            float z,
            BlockGetter level,
            int requestedCount,
            float impulseX,
            float impulseY,
            float impulseZ,
            int requestedLifetimeTicks
    ) {
        if (level instanceof ServerLevel || !WaterRenderingConfig.localSphEffectsEnabled()) {
            return null;
        }
        int particleCount = WaterRenderingConfig.localSphParticleCount(requestedCount);
        int lifetimeTicks = WaterRenderingConfig.localSphLifetimeTicks(requestedLifetimeTicks);
        if (particleCount <= 0 || lifetimeTicks <= 0) {
            return null;
        }
        return createTransientSimulation(
                x,
                y,
                z,
                level,
                particleCount,
                impulseX,
                impulseY,
                impulseZ,
                lifetimeTicks,
                WaterRenderingConfig.maxLocalSphEffects()
        );
    }

    /**
     * Converts a slice of canonical finite-volume water into a mobile SPH body.
     *
     * <p>This is used by the canonical solver for energetic falling water. The
     * caller drains the source only after this method succeeds; the SPH body
     * owns the conserved volume until settlement writes it back into canonical
     * chunk cells.</p>
     *
     * @return {@code true} when SPH accepted ownership of the supplied volume
     */
    public boolean createCanonicalFlowSimulation(
            float x,
            float y,
            float z,
            ServerLevel level,
            int volumeUnits,
            float impulseX,
            float impulseY,
            float impulseZ
    ) {
        int conservedVolume = Math.max(0, volumeUnits);
        if (conservedVolume <= 0) {
            return false;
        }
        if (!WaterSimulationConfig.serverSphLocalSimulationEnabled()) {
            return false;
        }

        runPendingSettleCallbacks(level);
        removeEmptySimulations();

        int maxParticlesPerBody = Math.max(1, WaterSimulationConfig.serverSphMaxParticlesPerBody());
        int particleCount = Math.min(particleCountForVolume(conservedVolume), maxParticlesPerBody);
        SPHSimulator existing = findMergeTarget(x, y, z, level, SPHConstants.MERGE_RADIUS);
        if (existing != null) {
            int availableParticles = Math.max(0, maxParticlesPerBody - existing.particleCount());
            if (availableParticles <= 0) {
                return false;
            }
            existing.spawnPulse(x, y, z, Math.min(particleCount, availableParticles),
                    impulseX, impulseY, impulseZ);
            existing.addCanonicalVolumeUnits(conservedVolume);
            return true;
        }

        int maxActiveBodies = WaterSimulationConfig.serverSphMaxActiveBodies();
        if (countSimulations(level) >= maxActiveBodies) {
            SPHSimulator overloaded = findMergeTarget(x, y, z, level, SPHConstants.OVERLOAD_MERGE_RADIUS);
            if (overloaded == null && removeFirstSettledSimulation(level)) {
                overloaded = findMergeTarget(x, y, z, level, SPHConstants.OVERLOAD_MERGE_RADIUS);
            }
            if (overloaded != null) {
                int availableParticles = Math.max(0, maxParticlesPerBody - overloaded.particleCount());
                if (availableParticles <= 0) {
                    return false;
                }
                overloaded.spawnPulse(x, y, z, Math.min(Math.max(8, particleCount / 2), availableParticles),
                        impulseX, impulseY, impulseZ);
                overloaded.addCanonicalVolumeUnits(conservedVolume);
                return true;
            }
            if (countSimulations(level) >= maxActiveBodies) {
                return false;
            }
        }

        SPHSimulator sim = new SPHSimulator(level);
        sim.addCanonicalVolumeUnits(conservedVolume);
        configureSettlement(sim, level, pos -> { });
        sim.spawnPulse(x, y, z, particleCount, impulseX, impulseY, impulseZ);
        active.add(sim);
        return true;
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

    /**
     * Applies only settlements owned by the level currently ticking.
     *
     * <p>The integrated client and server share this manager singleton. Filtering
     * before application prevents a client tick from draining a queued
     * `ServerLevel` mutation that belongs on the server thread.</p>
     */
    private void runPendingSettleCallbacks(BlockGetter level) {
        List<PendingSettlement> deferred = new ArrayList<>();
        PendingSettlement pending;
        while ((pending = pendingSettlements.poll()) != null) {
            if (pending.level() == level && canApplySettlement(pending)) {
                applyPendingSettlement(pending);
            } else {
                deferred.add(pending);
            }
        }
        pendingSettlements.addAll(deferred);
    }

    // Level unload must resolve that level's queued writes before SavedData is
    // captured. Other dimensions remain queued for their next server tick.
    private void flushPendingSettlements(BlockGetter level) {
        List<PendingSettlement> deferred = new ArrayList<>();
        PendingSettlement pending;
        while ((pending = pendingSettlements.poll()) != null) {
            if (pending.level() == level && canApplySettlement(pending)) {
                applyPendingSettlement(pending);
            } else {
                deferred.add(pending);
            }
        }
        pendingSettlements.addAll(deferred);
    }

    /**
     * Triggers the physics step for all active simulations.
     *
     * @param deltaTime The time elapsed since the last tick.
     */
    public void tickAll(float deltaTime) {
        // This compatibility helper advances physics only. Server-owned
        // settlements require tickLevel so the owning level/thread is explicit.
        for (SPHSimulator sim : active) {
            sim.tick(deltaTime);
            if (sim.particleCount() == 0 && sim.getCanonicalVolumeUnits() <= 0) {
                active.remove(sim);
                settlementRetries.remove(sim);
            }
        }
    }

    public void tickLevel(BlockGetter level, float deltaTime) {
        runPendingSettleCallbacks(level);
        if (!(level instanceof ServerLevel) && !WaterRenderingConfig.localSphEffectsEnabled()) {
            active.removeIf(sim -> sim.getLevel() == level
                    && sim.isTransientSimulation()
                    && !sim.isRemoteMirror());
        }
        int totalParticleBudget = particleTickBudget(level);
        int remainingParticleBudget = totalParticleBudget;
        List<SPHSimulator> levelSimulations = new ArrayList<>();
        for (SPHSimulator simulator : active) {
            if (simulator.getLevel() == level) {
                levelSimulations.add(simulator);
            }
        }
        if (levelSimulations.isEmpty()) {
            tickCursors.remove(level);
            return;
        }

        int startIndex = Math.floorMod(tickCursors.getOrDefault(level, 0), levelSimulations.size());
        tickCursors.put(level, (startIndex + 1) % levelSimulations.size());
        boolean advancedBudgetedSimulation = false;

        // Rotate the first candidate every level tick. Without this cursor, the
        // stable active-list order lets early bodies consume the whole budget
        // forever while later bodies never advance or settle.
        for (int offset = 0; offset < levelSimulations.size(); offset++) {
            SPHSimulator sim = levelSimulations.get((startIndex + offset) % levelSimulations.size());

            // Server-owned collision queries must sleep with their naturally
            // unloaded chunks instead of pulling terrain back into memory.
            if (level instanceof ServerLevel serverLevel && !simulationAreaLoaded(serverLevel, sim)) {
                continue;
            }
            retrySettlementIfNeeded(level, sim);
            if (!sim.isRemoteMirror()) {
                int particleCost = Math.max(1, sim.particleCount());
                if (remainingParticleBudget <= 0) {
                    continue;
                }
                if (particleCost > remainingParticleBudget && advancedBudgetedSimulation) {
                    continue;
                }

                // Treat the configured budget as a soft per-level ceiling for
                // one oversized body. Otherwise lowering the budget below an
                // existing body's particle count freezes that body permanently.
                remainingParticleBudget = Math.max(0, remainingParticleBudget - particleCost);
                advancedBudgetedSimulation = true;
            }
            sim.tick(deltaTime);
            if ((sim.particleCount() == 0 && sim.getCanonicalVolumeUnits() <= 0)
                    || sim.isRemoteExpired()) {
                active.remove(sim);
                settlementRetries.remove(sim);
            }
        }
    }

    private static boolean simulationAreaLoaded(ServerLevel level, SPHSimulator simulator) {
        List<SPHParticle> particles = simulator.getRenderParticles();
        if (particles.isEmpty()) {
            return level.hasChunkAt(BlockPos.containing(
                    simulator.getCenterX(),
                    simulator.getCenterY(),
                    simulator.getCenterZ()
            ));
        }
        for (SPHParticle particle : particles) {
            if (!level.hasChunkAt(BlockPos.containing(particle.position.x, particle.position.y, particle.position.z))) {
                return false;
            }
        }
        return true;
    }

    private static int particleTickBudget(BlockGetter level) {
        if (level instanceof ServerLevel) {
            return WaterSimulationConfig.serverSphParticleTickBudget();
        }
        return WaterRenderingConfig.localSphParticleTickBudget();
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
        flushPendingSettlements(level);
        for (SPHSimulator simulator : active) {
            if (simulator.getLevel() == level
                    && simulator.getCanonicalVolumeUnits() > 0
                    && simulator.particleCount() == 0) {
                simulator.ensureResidualMarker();
            }
        }
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
        simulator.setSettleListener(finalParticles -> pendingSettlements.add(new PendingSettlement(
                simulator,
                level,
                copyParticles(finalParticles),
                fallbackPlacer
        )));
    }

    private void applyPendingSettlement(PendingSettlement pending) {
        SPHSimulator simulator = pending.simulator();
        if (!active.contains(simulator)) {
            return;
        }
        if (pending.level() instanceof ServerLevel serverLevel
                && simulator.getCanonicalVolumeUnits() > 0) {
            if (materializeCanonicalVolume(serverLevel, simulator, pending.particles(), true)) {
                settlementRetries.remove(simulator);
                active.remove(simulator);
            } else {
                simulator.ensureResidualMarker();
                settlementRetries.put(simulator, serverLevel.getGameTime() + 20L);
            }
        } else if (SPHConstants.CONVERT_SETTLED_TO_BLOCKS) {
            Set<BlockPos> placed = new HashSet<>();
            for (SPHParticle particle : pending.particles()) {
                BlockPos pos = BlockPos.containing(
                        particle.position.x,
                        particle.position.y,
                        particle.position.z
                );
                if (placed.add(pos)) {
                    pending.fallbackPlacer().placeBlock(pos);
                }
            }
            active.remove(simulator);
        } else {
            active.remove(simulator);
        }
    }

    private static boolean canApplySettlement(PendingSettlement pending) {
        return !(pending.level() instanceof ServerLevel serverLevel)
                || serverLevel.getServer().isSameThread();
    }

    private static List<SPHParticle> copyParticles(List<SPHParticle> particles) {
        List<SPHParticle> copies = new ArrayList<>(particles.size());
        for (SPHParticle particle : particles) {
            copies.add(new SPHParticle(particle));
        }
        return List.copyOf(copies);
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
            ), simulator.getCanonicalVolumeUnits(), writes, 0.0f, 0.0f, 0.0f);
            return finishSettlement(level, simulator, remaining, writes, warnOnFailure);
        }

        Map<BlockPos, SettlementAccumulator> particleCounts = new LinkedHashMap<>();
        SettlementAccumulator totalParticles = new SettlementAccumulator();
        for (SPHParticle particle : particles) {
            BlockPos particlePos = BlockPos.containing(
                    particle.position.x,
                    particle.position.y,
                    particle.position.z
            );
            particleCounts.computeIfAbsent(particlePos, ignored -> new SettlementAccumulator())
                    .add(particle);
            totalParticles.add(particle);
        }

        int remainingVolume = simulator.getCanonicalVolumeUnits();
        int remainingParticles = particles.size();
        for (Map.Entry<BlockPos, SettlementAccumulator> entry : particleCounts.entrySet()) {
            SettlementAccumulator accumulator = entry.getValue();
            int share = remainingParticles == accumulator.count
                    ? remainingVolume
                    : (int) ((long) remainingVolume * accumulator.count / remainingParticles);
            remainingVolume -= share;
            remainingParticles -= accumulator.count;
            remainingVolume += deposit(
                    level,
                    entry.getKey(),
                    share,
                    writes,
                    accumulator.averageVelocityX(),
                    accumulator.averageVelocityY(),
                    accumulator.averageVelocityZ()
            );
        }

        if (remainingVolume > 0) {
            remainingVolume = deposit(level, BlockPos.containing(
                    simulator.getCenterX(),
                    simulator.getCenterY(),
                    simulator.getCenterZ()
            ), remainingVolume, writes,
                    totalParticles.averageVelocityX(),
                    totalParticles.averageVelocityY(),
                    totalParticles.averageVelocityZ());
        }
        return finishSettlement(level, simulator, remainingVolume, writes, warnOnFailure);
    }

    // Searches a compact settlement area so a particle body that stops against
    // terrain keeps its exact volume instead of being written inside solids.
    private static int deposit(
            ServerLevel level,
            BlockPos target,
            int volumeUnits,
            List<SettlementWrite> writes,
            float velocityX,
            float velocityY,
            float velocityZ
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
                                velocityX,
                                velocityY,
                                velocityZ
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

    private static int particleCountForVolume(int volumeUnits) {
        float fullBlockFraction = volumeUnits / (float) WaterVolumeChunk.UNITS_PER_BLOCK;
        return Math.max(8, Math.min(
                SPHConstants.PARTICLES_PER_FULL_BLOCK,
                Math.round(SPHConstants.PARTICLES_PER_FULL_BLOCK * fullBlockFraction)
        ));
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
        pendingSettlements.removeIf(pending -> pending.level() == level);
        settlementRetries.keySet().removeIf(sim -> sim.getLevel() == level);
        active.removeIf(sim -> sim.getLevel() == level);
        restoredPersistentLevels.remove(level);
        tickCursors.remove(level);
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

    private static final class SettlementAccumulator {
        private int count;
        private float velocityX;
        private float velocityY;
        private float velocityZ;

        private void add(SPHParticle particle) {
            count++;
            velocityX += particle.velocity.x;
            velocityY += particle.velocity.y;
            velocityZ += particle.velocity.z;
        }

        private float averageVelocityX() {
            return count == 0 ? 0.0f : velocityX / count;
        }

        private float averageVelocityY() {
            return count == 0 ? 0.0f : velocityY / count;
        }

        private float averageVelocityZ() {
            return count == 0 ? 0.0f : velocityZ / count;
        }
    }

    private record PendingSettlement(
            SPHSimulator simulator,
            BlockGetter level,
            List<SPHParticle> particles,
            SettleBlockPlacer fallbackPlacer
    ) {
    }

    /**
     * Terminates all physics processing. Should be called during server/client shutdown.
     */
    public void shutdown() {
        active.clear();
        pendingSettlements.clear();
        settlementRetries.clear();
        restoredPersistentLevels.clear();
        tickCursors.clear();
    }

    /**
     * Interface defining how a finalized fluid particle converts back into a Minecraft block.
     */
    @FunctionalInterface
    public interface SettleBlockPlacer {
        void placeBlock(BlockPos pos);
    }
}
