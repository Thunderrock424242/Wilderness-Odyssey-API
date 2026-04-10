package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
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

    /** The internal array of particles. Fast and non-allocating. Modified ONLY by the physics thread. */
    private final List<SPHParticle> particles = new ArrayList<>();

    /** A thread-safe snapshot of the particles for the renderer to read. Updated at the end of the tick. */
    private volatile List<SPHParticle> renderParticles = new ArrayList<>();

    private final SpatialHashGrid grid = new SpatialHashGrid(SPHConstants.SMOOTHING_RADIUS);

    // Scratch buffers (reused to avoid allocation per step)
    private final List<Integer> neighbours = new ArrayList<>(64);
    private final float[] gradBuf = new float[3];

    /** The Minecraft level used for querying block boundaries and collisions. */
    private BlockGetter level;

    private int settleCounter = 0;
    private boolean settled = false;

    private SettleListener settleListener;
    private float timeAccumulator = 0f;

    /**
     * Callback interface triggered when the fluid slows down enough to be converted
     * back into static Minecraft fluid blocks.
     */
    public interface SettleListener {
        void onSettle(List<SPHParticle> finalParticles);
    }

    public SPHSimulator(BlockGetter level) {
        this.level = level;
    }

    public void setSettleListener(SettleListener l) { this.settleListener = l; }
    public void setLevel(BlockGetter level)          { this.level = level; }
    public boolean isSettled()                       { return settled; }

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
        if (particles.size() >= SPHConstants.MAX_PARTICLES) return;

        int count = Math.min(SPHConstants.PARTICLES_PER_BUCKET, SPHConstants.MAX_PARTICLES - particles.size());
        Random rng = new Random();
        float r = SPHConstants.SPAWN_RADIUS;

        for (int i = 0; i < count; i++) {
            float px, py, pz;
            do {
                px = (rng.nextFloat() * 2 - 1) * r;
                py = (rng.nextFloat() * 2 - 1) * r;
                pz = (rng.nextFloat() * 2 - 1) * r;
            } while (px*px + py*py + pz*pz > r*r);

            SPHParticle p = new SPHParticle(cx + px, cy + py, cz + pz);
            p.velocity.set(
                    (rng.nextFloat() - 0.5f) * 0.3f,
                    -rng.nextFloat() * 1.5f - 0.5f,
                    (rng.nextFloat() - 0.5f) * 0.3f
            );
            particles.add(p);
        }

        settled = false;
        settleCounter = 0;
    }

    /**
     * Advances the simulation by the given delta time.
     * Uses a fixed-timestep accumulator to ensure physics determinism regardless of framerate.
     *
     * @param deltaTime The time elapsed since the last tick in seconds.
     */
    public void tick(float deltaTime) {
        if (settled || particles.isEmpty()) return;

        timeAccumulator += deltaTime;
        int steps = 0;

        // Drain the accumulator using fixed timesteps
        while (timeAccumulator >= SPHConstants.TIMESTEP && steps < SPHConstants.MAX_STEPS_PER_FRAME) {
            step();
            timeAccumulator -= SPHConstants.TIMESTEP;
            steps++;
        }

        // Double Buffering: Provide the renderer with a fresh, safe snapshot
        renderParticles = new ArrayList<>(particles);
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
            pi.pressure = SPHConstants.PRESSURE_STIFFNESS * ((float)Math.pow(ratio, SPHConstants.PRESSURE_GAMMA) - 1f);
            pi.pressure = Math.max(0f, pi.pressure); // Fluid resists compression but doesn't pull inward
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
        }

        // 4. Integration & Collision
        float dt = SPHConstants.TIMESTEP;
        float totalSpeed = 0f;

        for (int i = 0; i < n; i++) {
            SPHParticle p = pts.get(i);

            // Apply global gravity
            p.acceleration.y -= SPHConstants.GRAVITY;

            // Symplectic Euler integration
            p.velocity.x += p.acceleration.x * dt;
            p.velocity.y += p.acceleration.y * dt;
            p.velocity.z += p.acceleration.z * dt;

            // Apply environmental damping
            float damp = 1f - SPHConstants.DAMPING;
            p.velocity.mul(damp);

            p.position.x += p.velocity.x * dt;
            p.position.y += p.velocity.y * dt;
            p.position.z += p.velocity.z * dt;

            resolveBlockCollision(p);

            totalSpeed += p.velocity.length();
        }

        // 5. Droplet Classification & Cleanup
        classifyDroplets(pts);
        pts.removeIf(p -> p.isDroplet && p.dropletLife <= 0);
        for (SPHParticle p : pts) {
            if (p.isDroplet && p.dropletLife > 0) p.dropletLife--;
        }

        // 6. Check for settling (fluid has become completely still)
        float avgSpeed = n > 0 ? totalSpeed / n : 0f;
        if (avgSpeed < SPHConstants.SETTLE_SPEED) {
            settleCounter++;
            if (settleCounter >= SPHConstants.SETTLE_FRAMES) {
                settled = true;
                if (settleListener != null) settleListener.onSettle(new ArrayList<>(pts));
            }
        } else {
            settleCounter = 0;
        }
    }

    /**
     * Ensures fluid particles respect Minecraft terrain.
     * Prevents particles from phasing through walls or floors by bouncing them back.
     */
    private void resolveBlockCollision(SPHParticle p) {
        if (level == null) return;

        int bx = (int)Math.floor(p.position.x);
        int by = (int)Math.floor(p.position.y);
        int bz = (int)Math.floor(p.position.z);

        // Check the 3x3x3 area around the particle for solid geometry
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(bx+dx, by+dy, bz+dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;

                    double minX = pos.getX() + shape.min(net.minecraft.core.Direction.Axis.X);
                    double minY = pos.getY() + shape.min(net.minecraft.core.Direction.Axis.Y);
                    double minZ = pos.getZ() + shape.min(net.minecraft.core.Direction.Axis.Z);
                    double maxX = pos.getX() + shape.max(net.minecraft.core.Direction.Axis.X);
                    double maxY = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                    double maxZ = pos.getZ() + shape.max(net.minecraft.core.Direction.Axis.Z);

                    float px = p.position.x, py = p.position.y, pz = p.position.z;

                    if (px > minX && px < maxX && py > minY && py < maxY && pz > minZ && pz < maxZ) {
                        float overlapNX = (float)(px - minX);
                        float overlapPX = (float)(maxX - px);
                        float overlapNY = (float)(py - minY);
                        float overlapPY = (float)(maxY - py);
                        float overlapNZ = (float)(pz - minZ);
                        float overlapPZ = (float)(maxZ - pz);

                        float minOverlap = Math.min(Math.min(Math.min(overlapNX, overlapPX), Math.min(overlapNY, overlapPY)), Math.min(overlapNZ, overlapPZ));

                        float nx = 0, ny = 0, nz = 0;
                        float push = minOverlap + 0.001f;

                        if      (minOverlap == overlapNX) { nx = -1; p.position.x -= push; }
                        else if (minOverlap == overlapPX) { nx =  1; p.position.x += push; }
                        else if (minOverlap == overlapNY) { ny = -1; p.position.y -= push; }
                        else if (minOverlap == overlapPY) { ny =  1; p.position.y += push; }
                        else if (minOverlap == overlapNZ) { nz = -1; p.position.z -= push; }
                        else                              { nz =  1; p.position.z += push; }

                        float vDotN = p.velocity.x*nx + p.velocity.y*ny + p.velocity.z*nz;
                        if (vDotN < 0) {
                            p.velocity.x -= (1 + SPHConstants.RESTITUTION) * vDotN * nx;
                            p.velocity.y -= (1 + SPHConstants.RESTITUTION) * vDotN * ny;
                            p.velocity.z -= (1 + SPHConstants.RESTITUTION) * vDotN * nz;

                            p.velocity.x *= (1 - SPHConstants.FRICTION * Math.abs(nx == 0 ? 1 : 0));
                            p.velocity.z *= (1 - SPHConstants.FRICTION * Math.abs(nz == 0 ? 1 : 0));
                        }
                    }
                }
            }
        }
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