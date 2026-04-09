package com.thunder.wildernessodysseyapi.watersystem.water.sph;

/**
 * SPHKernels
 *
 * The three standard SPH smoothing kernels used in fluid simulation:
 *
 *  Poly6      — density estimation (W)
 *  Spiky      — pressure gradient (∇W)  — has non-zero gradient at origin
 *  Viscosity  — viscosity Laplacian (∇²W)
 *
 * All kernels use smoothing radius h = SPHConstants.SMOOTHING_RADIUS.
 */
public final class SPHKernels {

    private SPHKernels() {}

    private static final float H  = SPHConstants.SMOOTHING_RADIUS;
    private static final float H2 = H * H;
    private static final float H3 = H2 * H;
    private static final float H6 = H3 * H3;
    private static final float H9 = H6 * H3;

    // Pre-computed normalisation constants
    private static final float POLY6_COEFF     = 315f / (64f * (float)Math.PI * H9);
    private static final float SPIKY_COEFF     = -45f / ((float)Math.PI * H6);
    private static final float VISCOSITY_COEFF =  45f / ((float)Math.PI * H6);

    // -------------------------------------------------------------------------
    // Poly6 kernel — W_poly6(r, h)
    // Used for: density
    // -------------------------------------------------------------------------

    /**
     * @param r2  squared distance |r|²
     * @return    kernel value
     */
    public static float poly6(float r2) {
        if (r2 >= H2) return 0f;
        float diff = H2 - r2;
        return POLY6_COEFF * diff * diff * diff;
    }

    // -------------------------------------------------------------------------
    // Spiky kernel gradient — ∇W_spiky(r, h)
    // Used for: pressure force
    // Writes result into (outX, outY, outZ) — call site adds to accumulator
    // -------------------------------------------------------------------------

    /**
     * @param rx, ry, rz  vector from neighbour to self (r = pos_i - pos_j)
     * @param r           |r| (must be > 0 and < h)
     * @param out         float[3] output for the gradient vector
     */
    public static void spikyGradient(float rx, float ry, float rz, float r, float[] out) {
        if (r <= 1e-6f || r >= H) { out[0]=0; out[1]=0; out[2]=0; return; }
        float diff = H - r;
        float coeff = SPIKY_COEFF * diff * diff / r;
        out[0] = coeff * rx;
        out[1] = coeff * ry;
        out[2] = coeff * rz;
    }

    // -------------------------------------------------------------------------
    // Viscosity Laplacian — ∇²W_visc(r, h)
    // Used for: viscosity force
    // -------------------------------------------------------------------------

    /**
     * @param r  |r|
     * @return   Laplacian scalar value
     */
    public static float viscosityLaplacian(float r) {
        if (r >= H) return 0f;
        return VISCOSITY_COEFF * (H - r);
    }
}
