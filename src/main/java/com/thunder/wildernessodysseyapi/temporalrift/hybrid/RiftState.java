package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

/** Persisted server-owned state. Timed progression and destinations belong to future integrations. */
public enum RiftState {
    DORMANT(0.10, 0, false),
    BUILDUP(0.45, 0.25, true),
    ACTIVE(0.75, 1.0, true),
    CRITICAL(1.0, 1.35, true),
    DECAY(0.35, 0.35, true),
    STABILIZED(0.20, 0, false);

    private final double visualIntensity;
    private final double forceMultiplier;
    private final boolean rejectsEntry;

    RiftState(double visualIntensity, double forceMultiplier, boolean rejectsEntry) {
        this.visualIntensity = visualIntensity;
        this.forceMultiplier = forceMultiplier;
        this.rejectsEntry = rejectsEntry;
    }

    public double visualIntensity() { return visualIntensity; }
    public double forceMultiplier() { return forceMultiplier; }
    public boolean rejectsEntry() { return rejectsEntry; }

    /** Unknown save values degrade to the bounded active interaction, never a travel destination. */
    public static RiftState fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return ACTIVE;
        }
    }
}
