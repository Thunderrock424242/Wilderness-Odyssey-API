package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Defines the canonical compact precision and physical threshold for rain/snow.
 *
 * <p>Regional snapshots encode precipitation in six bits. Server gameplay and
 * client prediction classify the same quantized bucket so rounding at the wire
 * boundary cannot make one side wet while the other remains dry.</p>
 */
public final class PrecipitationIntensity {

    /** Largest unsigned value representable by the six-bit wire field. */
    public static final int QUANTIZED_MAX = 63;

    /** First wire bucket considered strong enough to affect gameplay. */
    public static final int MINIMUM_FUNCTIONAL_CODE = 2;

    /** Dequantized value received by clients for the first functional bucket. */
    public static final double FIRST_FUNCTIONAL_DEQUANTIZED_VALUE =
            (double) MINIMUM_FUNCTIONAL_CODE / QUANTIZED_MAX;

    private PrecipitationIntensity() {
    }

    /** Quantizes a server value exactly as the regional snapshot codec does. */
    public static int quantize(double intensity) {
        double finite = Double.isFinite(intensity) ? intensity : 0.0;
        double bounded = Math.max(0.0, Math.min(1.0, finite));
        return quantize((float) bounded);
    }

    /** Quantizes a client/wire float into the unsigned six-bit range. */
    public static int quantize(float intensity) {
        float finite = Float.isFinite(intensity) ? intensity : 0.0F;
        float bounded = Math.max(0.0F, Math.min(1.0F, finite));
        return Math.round(bounded * QUANTIZED_MAX);
    }

    /** Restores a bounded six-bit bucket to its normalized float value. */
    public static float dequantize(int code) {
        int bounded = Math.max(0, Math.min(QUANTIZED_MAX, code));
        return (float) bounded / QUANTIZED_MAX;
    }

    /** Returns whether this value occupies a gameplay-active wire bucket. */
    public static boolean isFunctional(double intensity) {
        return quantize(intensity) >= MINIMUM_FUNCTIONAL_CODE;
    }
}
