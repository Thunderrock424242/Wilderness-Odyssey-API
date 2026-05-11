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
    public static final float PRESSURE_STIFFNESS  = 360f;

    /** Tait equation gamma exponent. 7 is standard for water. */
    public static final float PRESSURE_GAMMA      = 7f;

    // -------------------------------------------------------------------------
    // Viscosity
    // -------------------------------------------------------------------------

    /**
     * Dynamic viscosity coefficient μ.
     * Higher = thicker fluid (honey-like). 0.01–0.1 for water-like behaviour.
     */
    public static final float VISCOSITY           = 0.025f;

    // -------------------------------------------------------------------------
    // Integration
    // -------------------------------------------------------------------------

    /** Fixed timestep in seconds for the SPH integrator. */
    public static final float TIMESTEP            = 0.0125f;

    /** Maximum simulation sub-steps per game frame (caps CPU usage). */
    public static final int   MAX_STEPS_PER_FRAME = 4;

    /** Gravity (blocks/s²). Minecraft uses ~20 blocks/s², real is ~9.8. */
    public static final float GRAVITY             = 30f;

    /** Velocity damping applied each step (0 = no damping, 1 = freeze). */
    public static final float DAMPING             = 0.004f;

    // -------------------------------------------------------------------------
    // Boundaries
    // -------------------------------------------------------------------------

    /**
     * Coefficient of restitution for block surface collisions.
     * 0 = perfectly inelastic (no bounce), 1 = perfectly elastic.
     */
    public static final float RESTITUTION         = 0.08f;

    /** Friction coefficient applied to velocity tangent on collision. */
    public static final float FRICTION            = 0.12f;

    /** Extra horizontal acceleration applied when particles hit a floor. */
    public static final float GROUND_SPREAD_FORCE = 9.0f;

    /** Clamp for horizontal speed so spreading stays stable. */
    public static final float MAX_HORIZONTAL_SPEED = 5.0f;

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
    public static final int MAX_PARTICLES         = 720;

    /** Nearby bucket pours are merged into one sim instead of creating more bodies. */
    public static final float MERGE_RADIUS        = 5.0f;

    /** Wider merge radius used only once the normal active-body budget is full. */
    public static final float OVERLOAD_MERGE_RADIUS = 10.0f;

    /** Hard cap for separate SPH bodies in one level. Extra pours merge into existing SPH. */
    public static final int MAX_ACTIVE_SIMULATIONS = 24;

    /** Keep settled water as SPH mesh instead of converting it into vanilla source blocks. */
    public static final boolean CONVERT_SETTLED_TO_BLOCKS = false;

    /**
     * Radius around the bucket placement point in which particles
     * are initially spawned (blocks).
     */
    public static final float SPAWN_RADIUS        = 0.4f;

    /** Number of particles spawned per bucket placement. */
    public static final int   PARTICLES_PER_BUCKET = 140;

    /** Smaller pour size when a bucket has to merge into an overloaded sim. */
    public static final int   OVERLOAD_PARTICLES_PER_BUCKET = 72;

    /** Small SPH pulse used for shoreline wave wash and tide surge visuals. */
    public static final int   SHORE_WAVE_PARTICLES = 14;

    /** Hard cap for automatic shore wash so bucket water keeps priority. */
    public static final int   MAX_TRANSIENT_SHORE_SIMULATIONS = 4;

    /** Lifetime for automatic shore wash pulses before they are removed. */
    public static final int   SHORE_WAVE_LIFETIME_TICKS = 70;

    // -------------------------------------------------------------------------
    // Marching Cubes / mesh
    // -------------------------------------------------------------------------

    /** Grid cell size for the density field sampled by marching cubes. */
    public static final float GRID_CELL_SIZE      = 0.22f;

    /** Iso-surface threshold. Lower = larger blob surface. */
    public static final float ISO_THRESHOLD       = 0.5f;

    /**
     * Kernel radius used when splatting particle density onto the grid.
     * Should be ~2× GRID_CELL_SIZE.
     */
    public static final float SPLAT_RADIUS        = 0.44f;

    // -------------------------------------------------------------------------
    // Settling
    // -------------------------------------------------------------------------

    /**
     * When average particle speed drops below this value for SETTLE_FRAMES
     * consecutive frames, the simulation converts particles to Minecraft blocks.
     */
    public static final float SETTLE_SPEED        = 0.035f;
    public static final int   SETTLE_FRAMES       = 100;
}
