package com.thunder.wildernessodysseyapi.watersystem.water.sph;

/** Pressure and low-speed contact corrections shared by SPH runtime and tests. */
final class SPHEquationOfState {

    private static final float GROUND_ASSIST_START_RATIO = 0.90f;
    private static final float GROUND_ASSIST_FULL_RATIO = 1.15f;
    private static final float GROUND_ASSIST_MAX_SPEED = 1.25f;

    private SPHEquationOfState() {
    }

    /** Evaluates the bounded Tait equation used by every particle. */
    static float pressureForDensity(float density) {
        float safeDensity = Float.isFinite(density) ? Math.max(0.0f, density) : 0.0f;
        float ratio = safeDensity / SPHConstants.REST_DENSITY;
        float ratio2 = ratio * ratio;
        float ratio4 = ratio2 * ratio2;
        float densityPower = SPHConstants.PRESSURE_GAMMA == 7.0f
                ? ratio4 * ratio2 * ratio
                : (float) Math.pow(ratio, SPHConstants.PRESSURE_GAMMA);
        float pressure = SPHConstants.PRESSURE_STIFFNESS * (densityPower - 1.0f);
        return Float.isFinite(pressure)
                ? Math.max(0.0f, Math.min(SPHConstants.MAX_PRESSURE, pressure))
                : SPHConstants.MAX_PRESSURE;
    }

    /**
     * Returns a small contact-assist factor for compressed, slow particles.
     *
     * <p>Particles below the useful pressure-density band receive no invented
     * radial force. The correction fades out as real horizontal motion builds,
     * leaving SPH pressure and terrain slope as the primary spreading forces.</p>
     */
    static float groundAssistFactor(float density, float horizontalSpeed) {
        float safeDensity = Float.isFinite(density) ? Math.max(0.0f, density) : 0.0f;
        float safeHorizontalSpeed = Float.isFinite(horizontalSpeed)
                ? Math.max(0.0f, horizontalSpeed)
                : GROUND_ASSIST_MAX_SPEED;
        float densityRatio = safeDensity / SPHConstants.REST_DENSITY;
        float compression = clamp01(
                (densityRatio - GROUND_ASSIST_START_RATIO)
                        / (GROUND_ASSIST_FULL_RATIO - GROUND_ASSIST_START_RATIO)
        );
        float slowMotion = 1.0f - clamp01(
                safeHorizontalSpeed / GROUND_ASSIST_MAX_SPEED
        );
        return compression * slowMotion;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
