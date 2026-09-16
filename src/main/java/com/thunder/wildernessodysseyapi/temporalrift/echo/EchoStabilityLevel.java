package com.thunder.wildernessodysseyapi.temporalrift.echo;

/** Environmental bands of Echo Earth's server-authoritative instability. */
public enum EchoStabilityLevel {
    STABLE(0.08),
    DESYNCED(0.48),
    FRACTURED(0.9);

    private final double intensity;

    EchoStabilityLevel(double intensity) {
        this.intensity = intensity;
    }

    /** Representative intensity for deterministic regions and operator overrides. */
    public double intensity() {
        return intensity;
    }

    /** Classifies a continuous regional/fracture intensity. */
    public static EchoStabilityLevel fromIntensity(double intensity) {
        return intensity >= 0.75 ? FRACTURED : intensity >= 0.3 ? DESYNCED : STABLE;
    }
}
