package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SPHSimulator
 *
 * The main SPH fluid simulation.
 * Runs on a background thread at a fixed timestep.
 * The render thread reads particle positions for meshing.
 *
 * Pipeline each step:
 *   1. Rebuild spatial hash
 *   2. Compute density and pressure for every particle
 *   3. Compute pressure + viscosity forces
 *   4. Apply gravity
 *   5. Integrate (Symplectic Euler)
 *   6. Resolve block collisions
 *   7. Classify droplets
 *   8. Check settle condition
 */
public class SPHSimulator {

    // Thread-safe particle list — renderer reads, sim writes
    public final CopyOnWriteArrayList<SPHParticle> particles = new CopyOnWriteArrayList<>();

    private final SpatialHashGrid grid =
        new SpatialHashGrid(SPHConstants.SMOOTHING_RADIUS);

    // Scratch buffers (reused to avoid allocation per step)
    private final List<Integer> neighbours = new ArrayList<>(64);
    private final float[] gradBuf = new float[3];

    // The Minecraft level used for collision queries
    private BlockGetter level;

    // Settling detection
    private int   settleCounter = 0;
    private boolean settled     = false;

    // Listener notified when the simulation settles
    private SettleListener settleListener;

    // Simulation accumulator (seconds) to handle variable frame times
    private float timeAccumulator = 0f;

    public interface SettleListener {
        void onSettle(List<SPHParticle> finalParticles);
    }

    public SPHSimulator(BlockGetter level) {
        this.level = level;
    }

    public void setSettleListener(SettleListener l) { this.settleListener = l; }
    public void setLevel(BlockGetter level)          { this.level = level; }
    public boolean isSettled()                       { return settled; }

    // -------------------------------------------------------------------------
    // Particle spawning
    // -------------------------------------------------------------------------

    /**
     * Spawn a cluster of particles at world position (cx, cy, cz)
     * simulating a bucket being placed.
     */
    public void spawnBucket(float cx, float cy, float cz) {
        if (particles.size() >= SPHConstants.MAX_PARTICLES) return;

        int count = Math.min(SPHConstants.PARTICLES_PER_BUCKET,
                             SPHConstants.MAX_PARTICLES - particles.size());
        Random rng = new Random();
        float r = SPHConstants.SPAWN_RADIUS;

        for (int i = 0; i < count; i++) {
            // Pack particles in a sphere using rejection sampling
            float px, py, pz;
            do {
                px = (rng.nextFloat() * 2 - 1) * r;
                py = (rng.nextFloat() * 2 - 1) * r;
                pz = (rng.nextFloat() * 2 - 1) * r;
            } while (px*px + py*py + pz*pz > r*r);

            SPHParticle p = new SPHParticle(cx + px, cy + py, cz + pz);
            // Initial downward velocity to simulate pour
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

    // -------------------------------------------------------------------------
    // Tick entry point
    // -------------------------------------------------------------------------

    /**
     * Advance the simulation by deltaTime seconds.
     * Runs up to MAX_STEPS_PER_FRAME fixed steps.
     */
    public void tick(float deltaTime) {
        if (settled || particles.isEmpty()) return;

        timeAccumulator += deltaTime;
        int steps = 0;
        while (timeAccumulator >= SPHConstants.TIMESTEP
               && steps < SPHConstants.MAX_STEPS_PER_FRAME) {
            step();
            timeAccumulator -= SPHConstants.TIMESTEP;
            steps++;
        }
    }

    // -------------------------------------------------------------------------
    // Single simulation step
    // -------------------------------------------------------------------------

    private void step() {
        List<SPHParticle> pts = particles;
        int n = pts.size();
        if (n == 0) return;

        // 1. Rebuild spatial hash
        grid.clear();
        for (int i = 0; i < n; i++) {
            SPHParticle p = pts.get(i);
            grid.insert(i, p.position.x, p.position.y, p.position.z);
        }

        // 2. Density + pressure pass
        for (int i = 0; i < n; i++) {
            SPHParticle pi = pts.get(i);
            pi.resetStep();

            grid.queryNeighbours(pts,
                pi.position.x, pi.position.y, pi.position.z,
                SPHConstants.SMOOTHING_RADIUS, neighbours);

            float density = 0f;
            for (int j : neighbours) {
                SPHParticle pj = pts.get(j);
                float dx = pi.position.x - pj.position.x;
                float dy = pi.position.y - pj.position.y;
                float dz = pi.position.z - pj.position.z;
                float r2 = dx*dx + dy*dy + dz*dz;
                density += SPHConstants.PARTICLE_MASS * SPHKernels.poly6(r2);
            }
            pi.density  = Math.max(density, 1f); // prevent div-by-zero

            // Tait equation of state
            float ratio = pi.density / SPHConstants.REST_DENSITY;
            pi.pressure = SPHConstants.PRESSURE_STIFFNESS
                          * ((float)Math.pow(ratio, SPHConstants.PRESSURE_GAMMA) - 1f);
            pi.pressure = Math.max(0f, pi.pressure); // no negative pressure
        }

        // 3. Force pass: pressure + viscosity
        for (int i = 0; i < n; i++) {
            SPHParticle pi = pts.get(i);

            grid.queryNeighbours(pts,
                pi.position.x, pi.position.y, pi.position.z,
                SPHConstants.SMOOTHING_RADIUS, neighbours);

            float ax = 0, ay = 0, az = 0;

            for (int j : neighbours) {
                if (i == j) continue;
                SPHParticle pj = pts.get(j);

                float dx = pi.position.x - pj.position.x;
                float dy = pi.position.y - pj.position.y;
                float dz = pi.position.z - pj.position.z;
                float r2 = dx*dx + dy*dy + dz*dz;
                float r  = (float)Math.sqrt(r2);

                // -- Pressure force --
                SPHKernels.spikyGradient(dx, dy, dz, r, gradBuf);
                float pressureTerm = SPHConstants.PARTICLE_MASS
                    * (pi.pressure + pj.pressure)
                    / (2f * pj.density);
                ax -= pressureTerm * gradBuf[0];
                ay -= pressureTerm * gradBuf[1];
                az -= pressureTerm * gradBuf[2];

                // -- Viscosity force --
                float lap = SPHKernels.viscosityLaplacian(r);
                float viscTerm = SPHConstants.VISCOSITY * SPHConstants.PARTICLE_MASS
                    * lap / pj.density;
                ax += viscTerm * (pj.velocity.x - pi.velocity.x);
                ay += viscTerm * (pj.velocity.y - pi.velocity.y);
                az += viscTerm * (pj.velocity.z - pi.velocity.z);
            }

            // Divide by own density to get acceleration
            float invDensity = 1f / pi.density;
            pi.acceleration.set(ax * invDensity, ay * invDensity, az * invDensity);
        }

        // 4. Gravity + integrate + collide
        float dt = SPHConstants.TIMESTEP;
        float totalSpeed = 0f;

        for (int i = 0; i < n; i++) {
            SPHParticle p = pts.get(i);

            // Gravity
            p.acceleration.y -= SPHConstants.GRAVITY;

            // Symplectic Euler integration
            p.velocity.x += p.acceleration.x * dt;
            p.velocity.y += p.acceleration.y * dt;
            p.velocity.z += p.acceleration.z * dt;

            // Damping
            float damp = 1f - SPHConstants.DAMPING;
            p.velocity.mul(damp);

            p.position.x += p.velocity.x * dt;
            p.position.y += p.velocity.y * dt;
            p.position.z += p.velocity.z * dt;

            // Block collision
            resolveBlockCollision(p);

            totalSpeed += p.velocity.length();
        }

        // 5. Classify droplets
        classifyDroplets(pts);

        // 6. Remove dead droplets
        pts.removeIf(p -> p.isDroplet && p.dropletLife <= 0);
        // Decrement droplet life
        for (SPHParticle p : pts) {
            if (p.isDroplet && p.dropletLife > 0) p.dropletLife--;
        }

        // 7. Settle check
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

    // -------------------------------------------------------------------------
    // Block collision resolution
    // -------------------------------------------------------------------------

    private void resolveBlockCollision(SPHParticle p) {
        if (level == null) return;

        // Check the block the particle is inside and its 6 face-neighbours
        int bx = (int)Math.floor(p.position.x);
        int by = (int)Math.floor(p.position.y);
        int bz = (int)Math.floor(p.position.z);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(bx+dx, by+dy, bz+dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;

                    // Compute AABB bounds of this block
                    double minX = pos.getX() + shape.min(net.minecraft.core.Direction.Axis.X);
                    double minY = pos.getY() + shape.min(net.minecraft.core.Direction.Axis.Y);
                    double minZ = pos.getZ() + shape.min(net.minecraft.core.Direction.Axis.Z);
                    double maxX = pos.getX() + shape.max(net.minecraft.core.Direction.Axis.X);
                    double maxY = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                    double maxZ = pos.getZ() + shape.max(net.minecraft.core.Direction.Axis.Z);

                    float px = p.position.x, py = p.position.y, pz = p.position.z;

                    // Only resolve if particle is inside the block
                    if (px > minX && px < maxX &&
                        py > minY && py < maxY &&
                        pz > minZ && pz < maxZ) {

                        // Find shallowest penetration axis
                        float overlapNX = (float)(px - minX);
                        float overlapPX = (float)(maxX - px);
                        float overlapNY = (float)(py - minY);
                        float overlapPY = (float)(maxY - py);
                        float overlapNZ = (float)(pz - minZ);
                        float overlapPZ = (float)(maxZ - pz);

                        float minOverlap = Math.min(Math.min(Math.min(overlapNX, overlapPX),
                                                   Math.min(overlapNY, overlapPY)),
                                                   Math.min(overlapNZ, overlapPZ));

                        float nx = 0, ny = 0, nz = 0;
                        float push = minOverlap + 0.001f;

                        if      (minOverlap == overlapNX) { nx = -1; p.position.x -= push; }
                        else if (minOverlap == overlapPX) { nx =  1; p.position.x += push; }
                        else if (minOverlap == overlapNY) { ny = -1; p.position.y -= push; }
                        else if (minOverlap == overlapPY) { ny =  1; p.position.y += push; }
                        else if (minOverlap == overlapNZ) { nz = -1; p.position.z -= push; }
                        else                              { nz =  1; p.position.z += push; }

                        // Reflect velocity component along normal
                        float vDotN = p.velocity.x*nx + p.velocity.y*ny + p.velocity.z*nz;
                        if (vDotN < 0) {
                            p.velocity.x -= (1 + SPHConstants.RESTITUTION) * vDotN * nx;
                            p.velocity.y -= (1 + SPHConstants.RESTITUTION) * vDotN * ny;
                            p.velocity.z -= (1 + SPHConstants.RESTITUTION) * vDotN * nz;

                            // Apply friction to tangential component
                            p.velocity.x *= (1 - SPHConstants.FRICTION * Math.abs(nx == 0 ? 1 : 0));
                            p.velocity.z *= (1 - SPHConstants.FRICTION * Math.abs(nz == 0 ? 1 : 0));
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Droplet classification
    // -------------------------------------------------------------------------

    private void classifyDroplets(List<SPHParticle> pts) {
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            SPHParticle p = pts.get(i);
            if (p.isDroplet) continue;

            // Count neighbours
            grid.queryNeighbours(pts,
                p.position.x, p.position.y, p.position.z,
                SPHConstants.SMOOTHING_RADIUS, neighbours);

            boolean fastUp  = p.velocity.y > SPHConstants.DROPLET_VELOCITY_THRESHOLD;
            boolean isolated = neighbours.size() < SPHConstants.MIN_DROPLET_NEIGHBOURS;

            if (fastUp && isolated) {
                p.isDroplet  = true;
                p.dropletLife = SPHConstants.DROPLET_LIFETIME;
            }
        }
    }
}
