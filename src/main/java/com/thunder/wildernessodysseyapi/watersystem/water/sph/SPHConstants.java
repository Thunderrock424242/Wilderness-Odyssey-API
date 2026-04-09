package com.thunder.wildernessodysseyapi.watersystem.water.sph;

/**
 * SPHConstants
 *
 * All tuning parameters for the SPH fluid simulation.
 * Change values here to adjust fluid behaviour without
 * touching simulation logic.
 */
public final class SPHConstants {

    private SPHConstants() {}

    // -------------------------------------------------------------------------
    // Particle properties
    // -------------------------------------------------------------------------

    /** Mass of each particle (kg equivalent). */
    public static final float PARTICLE_MASS       = 0.08f;

    /** Rest density — target density at equilibrium (kg/m³ equivalent). */
    public static final float REST_DENSITY        = 1000f;

    /** SPH smoothing radius — particles within this range interact. */
    public static final float SMOOTHING_RADIUS    = 0.35f;

    /** h² cached for kernel calculations. */
    public static final float H2 = SMOOTHING_RADIUS * SMOOTHING_RADIUS;

    // -------------------------------------------------------------------------
    // Pressure
    // -------------------------------------------------------------------------

    /**
     * Stiffness constant k in the Tait equation of state:
     *   pressure = k * ((density/restDensity)^gamma - 1)
     * Higher = stiffer fluid (more incompressible), but can cause instability.
     */
    public static final float PRESSURE_STIFFNESS  = 200f;

    /** Tait equation gamma exponent. 7 is standard for water. */
    public static final float PRESSURE_GAMMA      = 7f;

    // -------------------------------------------------------------------------
    // Viscosity
    // -------------------------------------------------------------------------

    /**
     * Dynamic viscosity coefficient μ.
     * Higher = thicker fluid (honey-like). 0.01–0.1 for water-like behaviour.
     */
    public static final float VISCOSITY           = 0.04f;

    // -------------------------------------------------------------------------
    // Integration
    // -------------------------------------------------------------------------

    /** Fixed timestep in seconds for the SPH integrator. */
    public static final float TIMESTEP            = 0.008f;

    /** Maximum simulation sub-steps per game frame (caps CPU usage). */
    public static final int   MAX_STEPS_PER_FRAME = 4;

    /** Gravity (blocks/s²). Minecraft uses ~20 blocks/s², real is ~9.8. */
    public static final float GRAVITY             = 14f;

    /** Velocity damping applied each step (0 = no damping, 1 = freeze). */
    public static final float DAMPING             = 0.002f;

    // -------------------------------------------------------------------------
    // Boundaries
    // -------------------------------------------------------------------------

    /**
     * Coefficient of restitution for block surface collisions.
     * 0 = perfectly inelastic (no bounce), 1 = perfectly elastic.
     */
    public static final float RESTITUTION         = 0.18f;

    /** Friction coefficient applied to velocity tangent on collision. */
    public static final float FRICTION            = 0.35f;

    // -------------------------------------------------------------------------
    // Droplets
    // -------------------------------------------------------------------------

    /**
     * A particle becomes a droplet when its upward velocity exceeds this
     * threshold and it has fewer than MIN_DROPLET_NEIGHBOURS neighbours.
     */
    public static final float DROPLET_VELOCITY_THRESHOLD = 1.8f;

    /** Minimum neighbour count — particles with fewer become droplets. */
    public static final int   MIN_DROPLET_NEIGHBOURS     = 4;

    /** Droplet lifetime in simulation steps before being removed. */
    public static final int   DROPLET_LIFETIME           = 120;

    // -------------------------------------------------------------------------
    // Simulation limits
    // -------------------------------------------------------------------------

    /** Maximum number of particles in a single fluid body. */
    public static final int MAX_PARTICLES         = 2000;

    /**
     * Radius around the bucket placement point in which particles
     * are initially spawned (blocks).
     */
    public static final float SPAWN_RADIUS        = 0.4f;

    /** Number of particles spawned per bucket placement. */
    public static final int   PARTICLES_PER_BUCKET = 400;

    // -------------------------------------------------------------------------
    // Marching Cubes / mesh
    // -------------------------------------------------------------------------

    /** Grid cell size for the density field sampled by marching cubes. */
    public static final float GRID_CELL_SIZE      = 0.18f;

    /** Iso-surface threshold. Lower = larger blob surface. */
    public static final float ISO_THRESHOLD       = 0.5f;

    /**
     * Kernel radius used when splatting particle density onto the grid.
     * Should be ~2× GRID_CELL_SIZE.
     */
    public static final float SPLAT_RADIUS        = 0.36f;

    // -------------------------------------------------------------------------
    // Settling
    // -------------------------------------------------------------------------

    /**
     * When average particle speed drops below this value for SETTLE_FRAMES
     * consecutive frames, the simulation converts particles to Minecraft blocks.
     */
    public static final float SETTLE_SPEED        = 0.05f;
    public static final int   SETTLE_FRAMES       = 40;
}
