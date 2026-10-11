package com.thunder.wildernessodysseyapi.anomaly.block;

/**
 * Common-safe visual identity for terrain energy seams. Future optional material
 * renderers can read this policy without becoming an owner of gameplay state.
 */
public enum AnomalyBlockMaterial {
    STABLE(0x6D9FB7, 160),
    SPATIAL(0x9280AC, 160),
    CRITICAL(0xAF5E61, 128);

    private final int energyRgb;
    private final int moteSampleBound;

    AnomalyBlockMaterial(int energyRgb, int moteSampleBound) {
        this.energyRgb = energyRgb;
        this.moteSampleBound = moteSampleBound;
    }

    /** The restrained seam color, independent of client rendering libraries. */
    public int energyRgb() {
        return energyRgb;
    }

    /** Bound used by Minecraft's nearby block animation sampling, not server ticks. */
    public int moteSampleBound() {
        return moteSampleBound;
    }

    /** Emits at most one mote, only for an enabled client animation sample. */
    public boolean shouldEmitCrackMote(boolean clientSide, boolean enabled, int sample) {
        return clientSide && enabled && sample == 0;
    }
}
