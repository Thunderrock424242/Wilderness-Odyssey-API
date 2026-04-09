package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import org.joml.Vector3f;

/**
 * SPHParticle
 *
 * Represents a single fluid particle in the SPH simulation.
 * Each particle carries position, velocity, acceleration,
 * density, and pressure — the five quantities needed for
 * Navier-Stokes SPH integration.
 *
 * Mass is kept uniform across all particles (see SPHConstants).
 */
public class SPHParticle {

    // World-space position (metres, 1 unit = 1 Minecraft block)
    public final Vector3f position    = new Vector3f();
    // Velocity (blocks/second)
    public final Vector3f velocity    = new Vector3f();
    // Accumulated force / acceleration this step
    public final Vector3f acceleration = new Vector3f();

    // Computed each step by the density pass
    public float density  = SPHConstants.REST_DENSITY;
    public float pressure = 0f;

    // Whether this particle has detached from the main body (airborne droplet)
    public boolean isDroplet = false;

    // Lifetime counter for droplets (ticks); -1 = permanent
    public int dropletLife = -1;

    // Scratch flag used by the spatial hash during neighbour search
    public int lastQueryId = -1;

    public SPHParticle() {}

    public SPHParticle(float x, float y, float z) {
        position.set(x, y, z);
    }

    /** Reset per-step accumulated quantities before the force pass. */
    public void resetStep() {
        acceleration.set(0f, 0f, 0f);
        density  = 0f;
        pressure = 0f;
    }

    @Override
    public String toString() {
        return String.format("SPHParticle[pos=(%.2f,%.2f,%.2f) vel=(%.2f,%.2f,%.2f) droplet=%b]",
            position.x, position.y, position.z,
            velocity.x, velocity.y, velocity.z,
            isDroplet);
    }
}
