package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

/**
 * The core mathematical engine for the Smoothed Particle Hydrodynamics (SPH) fluid simulation.
 * <p>
 * This class handles density calculations, pressure gradients, viscosity forces, and block collisions.
 * It uses a <b>Double Buffering</b> technique for thread safety:
 * <ul>
 * <li>{@link #particles} is modified exclusively by the physics thread.</li>
 * <li>{@link #renderParticles} is a safe snapshot updated once per tick for the renderer to draw.</li>
 * </ul>
 */
public class SPHSimulator {

    /** Stable identity used by persistence and server-to-client snapshots. */
    private final UUID simulationId;

    /** Remote mirrors interpolate snapshots but never run authoritative SPH physics. */
    private final boolean remoteMirror;

    /** The internal array of particles. Fast and non-allocating. Modified ONLY by the physics thread. */
    public final List<SPHParticle> particles = new ArrayList<>();

    /** A thread-safe snapshot of the particles for the renderer to read. Updated at the end of the tick. */
    private volatile List<SPHParticle> renderParticles = new ArrayList<>();

    private final SpatialHashGrid grid = new SpatialHashGrid(SPHConstants.SMOOTHING_RADIUS);

    // Scratch buffers (reused to avoid allocation per step)
    private final List<Integer> neighbours = new ArrayList<>(64);
    private final float[] gradBuf = new float[3];
    private BlockPos.MutableBlockPos collisionPos;

    /** The Minecraft level used for querying block boundaries and collisions. */
    private BlockGetter level;

    private int settleCounter = 0;
    private boolean settled = false;

    private SettleListener settleListener;
    private float timeAccumulator = 0f;
    private final Random random = new Random();
    private long renderRevision = 0L;
    private float centerX = 0.0f;
    private float centerY = 0.0f;
    private float centerZ = 0.0f;
    private boolean transientSimulation = false;
    private int remainingLifetimeTicks = -1;
    private List<SPHParticle> remotePreviousParticles = List.of();
    private float remoteInterpolationAlpha = 1.0f;
    private int remoteSnapshotAgeTicks = 0;
    private int canonicalVolumeUnits = 0;

    /**
     * Callback interface triggered when the fluid slows down enough to be converted
     * back into static Minecraft fluid blocks.
     */
    public interface SettleListener {
        void onSettle(List<SPHParticle> finalParticles);
    }

    public SPHSimulator(BlockGetter level) {
        this(UUID.randomUUID(), level, false);
    }

    private SPHSimulator(UUID simulationId, BlockGetter level, boolean remoteMirror) {
        this.simulationId = Objects.requireNonNull(simulationId, "simulationId");
        this.level = level;
        this.remoteMirror = remoteMirror;
    }

    /**
     * Creates a client-side mirror that can only be updated from server snapshots.
     *
     * @param simulationId stable server-owned fluid-body ID
     * @param level client level used by rendering and distance filtering
     * @return a non-authoritative simulator mirror
     */
    public static SPHSimulator createRemoteMirror(UUID simulationId, BlockGetter level) {
        return new SPHSimulator(simulationId, level, true);
    }

    /**
     * Reconstructs a server-owned body from persistent particle state.
     *
     * @param simulationId identity stored in the level's SavedData
     * @param level server level used for collision queries
     * @param snapshot last persisted particle state
     * @return restored authoritative simulator
     */
    public static SPHSimulator restoreAuthoritative(
            UUID simulationId,
            BlockGetter level,
            List<SPHParticle> snapshot
    ) {
        return restoreAuthoritative(simulationId, level, snapshot, 0);
    }

    /** Restores a server body together with its conserved fixed-point volume. */
    public static SPHSimulator restoreAuthoritative(
            UUID simulationId,
            BlockGetter level,
            List<SPHParticle> snapshot,
            int canonicalVolumeUnits
    ) {
        SPHSimulator simulator = new SPHSimulator(simulationId, level, false);
        for (SPHParticle particle : snapshot) {
            simulator.particles.add(new SPHParticle(particle));
        }
        simulator.canonicalVolumeUnits = Math.max(0, canonicalVolumeUnits);
        simulator.updateRenderSnapshot();
        return simulator;
    }

    public void setSettleListener(SettleListener l) { this.settleListener = l; }
    public UUID getSimulationId()                   { return simulationId; }
    public void setLevel(BlockGetter level)          { this.level = level; }
    public BlockGetter getLevel()                    { return level; }
    public boolean isSettled()                       { return settled; }
    public long getRenderRevision()                  { return renderRevision; }
    public float getCenterX()                        { return centerX; }
    public float getCenterY()                        { return centerY; }
    public float getCenterZ()                        { return centerZ; }
    public boolean isTransientSimulation()           { return transientSimulation; }
    public boolean isRemoteMirror()                  { return remoteMirror; }
    public int getCanonicalVolumeUnits()              { return canonicalVolumeUnits; }

    /** Adds conserved volume represented by a newly merged bucket pour. */
    public void addCanonicalVolumeUnits(int volumeUnits) {
        canonicalVolumeUnits = (int) Math.min(
                Integer.MAX_VALUE,
                (long) canonicalVolumeUnits + Math.max(0, volumeUnits)
        );
    }

    // A fully enclosed settled body can remain SPH-owned until canonical
    // capacity becomes available. Keep one marker so that residual volume is
    // renderable and included in SavedData even if its particles expired.
    void ensureResidualMarker() {
        if (canonicalVolumeUnits <= 0 || !particles.isEmpty()) {
            return;
        }
        particles.add(new SPHParticle(centerX, centerY, centerZ));
        updateRenderSnapshot();
    }
    public boolean isRemoteExpired() {
        return remoteMirror && remoteSnapshotAgeTicks > SPHConstants.REMOTE_SNAPSHOT_EXPIRY_TICKS;
    }

    public void setTransientLifetimeTicks(int ticks) {
        transientSimulation = true;
        remainingLifetimeTicks = Math.max(1, ticks);
    }

    /**
     * Returns a thread-safe snapshot of the particle states.
     * The renderer should call this instead of trying to access the physics list directly.
     *
     * @return A list of particles safe for reading on the render thread.
     */
    public List<SPHParticle> getRenderParticles() {
        return renderParticles;
    }

    /**
     * Spawns a cluster of fluid particles in a spherical shape at the target coordinates.
     *
     * @param cx The center X coordinate.
     * @param cy The center Y coordinate.
     * @param cz The center Z coordinate.
     */
    public void spawnBucket(float cx, float cy, float cz) {
        spawnPulse(cx, cy, cz, SPHConstants.PARTICLES_PER_BUCKET, 0.0f, 0.0f, 0.0f);
    }

    public void spawnBucket(float cx, float cy, float cz, int requestedCount) {
        spawnPulse(cx, cy, cz, requestedCount, 0.0f, 0.0f, 0.0f);
    }

    public void spawnPulse(float cx, float cy, float cz, int requestedCount,
                           float impulseX, float impulseY, float impulseZ) {
        if (particles.size() >= SPHConstants.MAX_PARTICLES) return;

        int count = Math.min(requestedCount, SPHConstants.MAX_PARTICLES - particles.size());
        float r = SPHConstants.SPAWN_RADIUS;

        for (int i = 0; i < count; i++) {
            float px, pz;
            do {
                px = (random.nextFloat() * 2 - 1) * r;
                pz = (random.nextFloat() * 2 - 1) * r;
            } while (px*px + pz*pz > r*r);

            // A three-dimensional spawn volume keeps normalized 3-D kernel
            // density near REST_DENSITY instead of disabling pressure.
            float py = (random.nextFloat() - 0.5f) * SPHConstants.SPAWN_HEIGHT;
            float radial = (float)Math.sqrt(px * px + pz * pz);
            float outX = radial > 0.0001f ? px / radial : 0.0f;
            float outZ = radial > 0.0001f ? pz / radial : 0.0f;

            SPHParticle p = new SPHParticle(cx + px, cy + py, cz + pz);
            p.velocity.set(
                    impulseX + outX * (0.12f + random.nextFloat() * 0.20f),
                    impulseY - (0.35f + random.nextFloat() * 0.65f),
                    impulseZ + outZ * (0.12f + random.nextFloat() * 0.20f)
            );
            particles.add(p);
        }

        settled = false;
        settleCounter = 0;
        timeAccumulator = 0.0f;
        updateRenderSnapshot();
    }

    /**
     * Advances the simulation by the given delta time.
     * Uses a fixed-timestep accumulator to ensure physics determinism regardless of framerate.
     *
     * @param deltaTime The time elapsed since the last tick in seconds.
     */
    public void tick(float deltaTime) {
        if (remoteMirror) {
            tickRemoteMirror();
            return;
        }

        if (remainingLifetimeTicks > 0 && --remainingLifetimeTicks == 0) {
            particles.clear();
            settled = true;
            updateRenderSnapshot();
            return;
        }

        if (settled) return;
        if (particles.isEmpty()) {
            settle(Collections.emptyList());
            updateRenderSnapshot();
            return;
        }

        timeAccumulator += deltaTime;
        int steps = 0;

        // Drain the accumulator using fixed timesteps
        while (timeAccumulator >= SPHConstants.TIMESTEP && steps < SPHConstants.MAX_STEPS_PER_FRAME) {
            step();
            timeAccumulator -= SPHConstants.TIMESTEP;
            steps++;
            if (settled) break;
        }

        updateRenderSnapshot();
    }

    private void updateRenderSnapshot() {
        publishRenderSnapshot(particles);
    }

    /**
     * Applies a complete authoritative snapshot to this client mirror.
     * Particle indices remain stable between most SPH steps, allowing inexpensive
     * interpolation without predicting collision-sensitive server physics.
     *
     * @param snapshot authoritative particle state decoded from the server packet
     */
    public void applyRemoteSnapshot(List<SPHParticle> snapshot) {
        if (!remoteMirror) {
            throw new IllegalStateException("Cannot apply a remote snapshot to an authoritative simulator");
        }

        remotePreviousParticles = renderParticles;
        particles.clear();
        for (SPHParticle particle : snapshot) {
            particles.add(new SPHParticle(particle));
        }
        remoteSnapshotAgeTicks = 0;
        remoteInterpolationAlpha = remotePreviousParticles.size() == particles.size() ? 0.0f : 1.0f;

        if (remoteInterpolationAlpha >= 1.0f) {
            publishRenderSnapshot(particles);
        }
    }

    // Client mirrors blend toward the latest server state instead of running a
    // second divergent collision simulation with a different random history.
    private void tickRemoteMirror() {
        remoteSnapshotAgeTicks++;
        if (remoteInterpolationAlpha >= 1.0f || remotePreviousParticles.size() != particles.size()) {
            return;
        }

        remoteInterpolationAlpha = Math.min(1.0f,
                remoteInterpolationAlpha + 1.0f / SPHConstants.NETWORK_SNAPSHOT_INTERVAL_TICKS);
        List<SPHParticle> interpolated = new ArrayList<>(particles.size());
        for (int i = 0; i < particles.size(); i++) {
            SPHParticle from = remotePreviousParticles.get(i);
            SPHParticle to = particles.get(i);
            SPHParticle result = new SPHParticle(to);
            result.position.set(from.position).lerp(to.position, remoteInterpolationAlpha);
            result.velocity.set(from.velocity).lerp(to.velocity, remoteInterpolationAlpha);
            interpolated.add(result);
        }
        publishRenderSnapshot(interpolated);
    }

    private void publishRenderSnapshot(List<SPHParticle> source) {
        List<SPHParticle> snapshot = new ArrayList<>(source.size());
        float sx = 0.0f;
        float sy = 0.0f;
        float sz = 0.0f;
        for (SPHParticle particle : source) {
            snapshot.add(new SPHParticle(particle));
            sx += particle.position.x;
            sy += particle.position.y;
            sz += particle.position.z;
        }
        if (!source.isEmpty()) {
            float inv = 1.0f / source.size();
            centerX = sx * inv;
            centerY = sy * inv;
            centerZ = sz * inv;
        }
        renderParticles = snapshot;
        renderRevision++;
    }

    public int particleCount() {
        return particles.size();
    }

    public float distanceSquaredTo(float x, float y, float z) {
        if (particles.isEmpty()) return Float.MAX_VALUE;

        float dx = centerX - x;
        float dy = centerY - y;
        float dz = centerZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean hasCapacity() {
        return particles.size() < SPHConstants.MAX_PARTICLES;
    }

    /**
     * Executes exactly one iteration of the physics pipeline.
     */
    private void step() {
        List<SPHParticle> pts = particles;
        int n = pts.size();
        if (n == 0) return;

        // 1. Rebuild spatial hash for O(1) neighbor lookups
        grid.clear();
        for (int i = 0; i < n; i++) {
            SPHParticle p = pts.get(i);
            sanitizeParticle(p);
            grid.insert(i, p.position.x, p.position.y, p.position.z);
        }

        // 2. Density + pressure pass
        for (int i = 0; i < n; i++) {
            SPHParticle pi = pts.get(i);
            pi.resetStep();

            grid.queryNeighbours(pts, pi.position.x, pi.position.y, pi.position.z, SPHConstants.SMOOTHING_RADIUS, neighbours);

            float density = 0f;
            for (int j : neighbours) {
                SPHParticle pj = pts.get(j);
                float dx = pi.position.x - pj.position.x;
                float dy = pi.position.y - pj.position.y;
                float dz = pi.position.z - pj.position.z;
                float r2 = dx*dx + dy*dy + dz*dz;
                density += SPHConstants.PARTICLE_MASS * SPHKernels.poly6(r2);
            }
            pi.density  = Math.max(density, 1f); // prevent division by zero

            // Tait equation of state for pressure
            float ratio = pi.density / SPHConstants.REST_DENSITY;
            float pressure = SPHConstants.PRESSURE_STIFFNESS * (pressureGamma7(ratio) - 1f);
            pi.pressure = Float.isFinite(pressure)
                    ? Math.max(0f, Math.min(SPHConstants.MAX_PRESSURE, pressure))
                    : SPHConstants.MAX_PRESSURE;
        }

        // 3. Force pass: compute pressure gradients and viscosity
        for (int i = 0; i < n; i++) {
            SPHParticle pi = pts.get(i);

            grid.queryNeighbours(pts, pi.position.x, pi.position.y, pi.position.z, SPHConstants.SMOOTHING_RADIUS, neighbours);

            float ax = 0, ay = 0, az = 0;

            for (int j : neighbours) {
                if (i == j) continue;
                SPHParticle pj = pts.get(j);

                float dx = pi.position.x - pj.position.x;
                float dy = pi.position.y - pj.position.y;
                float dz = pi.position.z - pj.position.z;
                float r2 = dx*dx + dy*dy + dz*dz;
                float r  = (float)Math.sqrt(r2);

                // Pressure force (pushes particles apart)
                SPHKernels.spikyGradient(dx, dy, dz, r, gradBuf);
                float pressureTerm = SPHConstants.PARTICLE_MASS * (pi.pressure + pj.pressure) / (2f * pj.density);
                ax -= pressureTerm * gradBuf[0];
                ay -= pressureTerm * gradBuf[1];
                az -= pressureTerm * gradBuf[2];

                // Viscosity force (aligns velocities of neighboring particles)
                float lap = SPHKernels.viscosityLaplacian(r);
                float viscTerm = SPHConstants.VISCOSITY * SPHConstants.PARTICLE_MASS * lap / pj.density;
                ax += viscTerm * (pj.velocity.x - pi.velocity.x);
                ay += viscTerm * (pj.velocity.y - pi.velocity.y);
                az += viscTerm * (pj.velocity.z - pi.velocity.z);
            }

            // Divide force by density to get acceleration
            float invDensity = 1f / pi.density;
            pi.acceleration.set(ax * invDensity, ay * invDensity, az * invDensity);
            clampAcceleration(pi);
        }

        // 4. Integration & Collision
        float dt = SPHConstants.TIMESTEP;
        float totalSpeed = 0f;
        float centerX = 0.0f;
        float centerZ = 0.0f;

        for (SPHParticle p : pts) {
            centerX += p.position.x;
            centerZ += p.position.z;
        }
        centerX /= n;
        centerZ /= n;

        for (int i = 0; i < n; i++) {
            SPHParticle p = pts.get(i);
            float previousX = p.position.x;
            float previousY = p.position.y;
            float previousZ = p.position.z;

            // Apply global gravity
            p.acceleration.y -= SPHConstants.GRAVITY;
            clampAcceleration(p);

            // Symplectic Euler integration
            p.velocity.x += p.acceleration.x * dt;
            p.velocity.y += p.acceleration.y * dt;
            p.velocity.z += p.acceleration.z * dt;

            // Apply environmental damping
            float damp = 1f - SPHConstants.DAMPING;
            p.velocity.mul(damp);
            clampVelocity(p);

            p.position.x += p.velocity.x * dt;
            p.position.y += p.velocity.y * dt;
            p.position.z += p.velocity.z * dt;

            if (!isFinite(p.position.x, p.position.y, p.position.z)) {
                p.position.set(previousX, previousY, previousZ);
                p.velocity.zero();
                p.acceleration.zero();
            }

            resolveBlockCollision(p, previousX, previousY, previousZ);
            applyGroundSpread(p, centerX, centerZ, dt);
            clampVelocity(p);

            totalSpeed += p.velocity.length();
        }

        // 5. Droplet Classification & Cleanup
        classifyDroplets(pts);
        for (int i = pts.size() - 1; i >= 0; i--) {
            SPHParticle p = pts.get(i);
            if (!p.isDroplet) {
                continue;
            }
            if (p.dropletLife <= 0) {
                pts.remove(i);
            } else {
                p.dropletLife--;
            }
        }

        // 6. Check for settling (fluid has become completely still)
        float avgSpeed = n > 0 ? totalSpeed / n : 0f;
        if (avgSpeed < SPHConstants.SETTLE_SPEED) {
            settleCounter++;
            if (settleCounter >= SPHConstants.SETTLE_FRAMES) {
                settle(new ArrayList<>(pts));
            }
        } else {
            settleCounter = 0;
        }
    }

    private void settle(List<SPHParticle> finalParticles) {
        if (settled) return;
        settled = true;
        timeAccumulator = 0.0f;
        if (settleListener != null) settleListener.onSettle(finalParticles);
    }

    /**
     * Ensures fluid particles respect Minecraft terrain.
     * Prevents particles from phasing through walls or floors by bouncing them back.
     */
    private void resolveBlockCollision(SPHParticle p, float previousX, float previousY, float previousZ) {
        if (level == null) return;

        float targetX = p.position.x;
        float targetY = p.position.y;
        float targetZ = p.position.z;
        float deltaX = targetX - previousX;
        float deltaY = targetY - previousY;
        float deltaZ = targetZ - previousZ;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        int samples = Math.max(1, (int) Math.ceil(
                distance / SPHConstants.MAX_COLLISION_SAMPLE_DISTANCE
        ));

        // Sweep the point along its bounded step. Sampling prevents thin panes,
        // fences, and partial block shapes from being skipped at high velocity.
        for (int sample = 1; sample <= samples; sample++) {
            float progress = sample / (float) samples;
            p.position.set(
                    previousX + deltaX * progress,
                    previousY + deltaY * progress,
                    previousZ + deltaZ * progress
            );
            if (resolveCollisionAtCurrentPosition(p)) {
                return;
            }
        }
    }

    private boolean resolveCollisionAtCurrentPosition(SPHParticle p) {
        int bx = (int) Math.floor(p.position.x);
        int by = (int) Math.floor(p.position.y);
        int bz = (int) Math.floor(p.position.z);
        if (collisionPos == null) {
            collisionPos = new BlockPos.MutableBlockPos();
        }
        BlockPos.MutableBlockPos pos = collisionPos;
        pos.set(bx, by, bz);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        for (AABB localBox : shape.toAabbs()) {
            AABB worldBox = localBox.move(pos);
            if (resolveCollisionBox(p, worldBox)) {
                return true;
            }
        }
        return false;
    }

    private static boolean resolveCollisionBox(SPHParticle p, AABB box) {
        float px = p.position.x;
        float py = p.position.y;
        float pz = p.position.z;
        if (px <= box.minX || px >= box.maxX
                || py <= box.minY || py >= box.maxY
                || pz <= box.minZ || pz >= box.maxZ) {
            return false;
        }

        float overlapNX = (float) (px - box.minX);
        float overlapPX = (float) (box.maxX - px);
        float overlapNY = (float) (py - box.minY);
        float overlapPY = (float) (box.maxY - py);
        float overlapNZ = (float) (pz - box.minZ);
        float overlapPZ = (float) (box.maxZ - pz);
        float minOverlap = Math.min(
                Math.min(Math.min(overlapNX, overlapPX), Math.min(overlapNY, overlapPY)),
                Math.min(overlapNZ, overlapPZ)
        );

        float nx = 0.0f;
        float ny = 0.0f;
        float nz = 0.0f;
        float push = minOverlap + 0.001f;
        if (minOverlap == overlapNX) {
            nx = -1.0f;
            p.position.x -= push;
        } else if (minOverlap == overlapPX) {
            nx = 1.0f;
            p.position.x += push;
        } else if (minOverlap == overlapNY) {
            ny = -1.0f;
            p.position.y -= push;
        } else if (minOverlap == overlapPY) {
            ny = 1.0f;
            p.position.y += push;
            p.onGround = true;
        } else if (minOverlap == overlapNZ) {
            nz = -1.0f;
            p.position.z -= push;
        } else {
            nz = 1.0f;
            p.position.z += push;
        }

        float velocityIntoSurface = p.velocity.x * nx + p.velocity.y * ny + p.velocity.z * nz;
        if (velocityIntoSurface < 0.0f) {
            p.velocity.x -= (1.0f + SPHConstants.RESTITUTION) * velocityIntoSurface * nx;
            p.velocity.y -= (1.0f + SPHConstants.RESTITUTION) * velocityIntoSurface * ny;
            p.velocity.z -= (1.0f + SPHConstants.RESTITUTION) * velocityIntoSurface * nz;
            if (ny != 0.0f) {
                p.velocity.x *= 1.0f - SPHConstants.FRICTION;
                p.velocity.z *= 1.0f - SPHConstants.FRICTION;
            } else if (nx != 0.0f) {
                p.velocity.y *= 1.0f - SPHConstants.FRICTION;
                p.velocity.z *= 1.0f - SPHConstants.FRICTION;
            } else {
                p.velocity.x *= 1.0f - SPHConstants.FRICTION;
                p.velocity.y *= 1.0f - SPHConstants.FRICTION;
            }
        }
        return true;
    }

    private static float pressureGamma7(float ratio) {
        float ratio2 = ratio * ratio;
        float ratio4 = ratio2 * ratio2;
        return ratio4 * ratio2 * ratio;
    }

    private void applyGroundSpread(SPHParticle p, float centerX, float centerZ, float dt) {
        if (!p.onGround) return;

        float dx = p.position.x - centerX;
        float dz = p.position.z - centerZ;
        float len = (float)Math.sqrt(dx * dx + dz * dz);
        if (len < 0.0001f) return;

        float strength = SPHConstants.GROUND_SPREAD_FORCE * dt;
        p.velocity.x += (dx / len) * strength;
        p.velocity.z += (dz / len) * strength;
    }

    private static void clampHorizontalSpeed(SPHParticle p) {
        float speed2 = p.velocity.x * p.velocity.x + p.velocity.z * p.velocity.z;
        float max = SPHConstants.MAX_HORIZONTAL_SPEED;
        if (speed2 <= max * max) return;

        float scale = max / (float)Math.sqrt(speed2);
        p.velocity.x *= scale;
        p.velocity.z *= scale;
    }

    private static void clampAcceleration(SPHParticle p) {
        if (!isFinite(p.acceleration.x, p.acceleration.y, p.acceleration.z)) {
            p.acceleration.zero();
            return;
        }
        float accelerationSquared = p.acceleration.lengthSquared();
        float maximum = SPHConstants.MAX_ACCELERATION;
        if (accelerationSquared > maximum * maximum) {
            p.acceleration.mul(maximum / (float) Math.sqrt(accelerationSquared));
        }
    }

    private static void clampVelocity(SPHParticle p) {
        if (!isFinite(p.velocity.x, p.velocity.y, p.velocity.z)) {
            p.velocity.zero();
            return;
        }
        clampHorizontalSpeed(p);
        p.velocity.y = Math.max(-SPHConstants.MAX_VERTICAL_SPEED,
                Math.min(SPHConstants.MAX_VERTICAL_SPEED, p.velocity.y));
    }

    private void sanitizeParticle(SPHParticle p) {
        if (!isFinite(p.position.x, p.position.y, p.position.z)) {
            p.position.set(
                    Float.isFinite(centerX) ? centerX : 0.0f,
                    Float.isFinite(centerY) ? centerY : 0.0f,
                    Float.isFinite(centerZ) ? centerZ : 0.0f
            );
        }
        clampVelocity(p);
    }

    private static boolean isFinite(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
    }

    private void classifyDroplets(List<SPHParticle> pts) {
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            SPHParticle p = pts.get(i);
            if (p.isDroplet) continue;

            grid.queryNeighbours(pts, p.position.x, p.position.y, p.position.z, SPHConstants.SMOOTHING_RADIUS, neighbours);

            boolean fastUp  = p.velocity.y > SPHConstants.DROPLET_VELOCITY_THRESHOLD;
            boolean isolated = neighbours.size() < SPHConstants.MIN_DROPLET_NEIGHBOURS;

            if (fastUp && isolated) {
                p.isDroplet  = true;
                p.dropletLife = SPHConstants.DROPLET_LIFETIME;
            }
        }
    }
}
