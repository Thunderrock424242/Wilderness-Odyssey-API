package com.thunder.wildernessodysseyapi.temporalrift.echo;

/** Pure sampling and coherent small-fragment transforms for the existing build-echo ledger. */
public final class EchoRealityModel {
    private EchoRealityModel() { }

    /** Deterministic chance; repeated edits to a position cannot reroll admission by spamming events. */
    public static boolean sample(long hash, double chance, boolean broken) {
        double probability = Double.isFinite(chance) ? Math.max(0, Math.min(1, chance)) : 0;
        return (hash >>> 11) * 0x1.0p-53 < probability * (broken ? 0.5 : 1);
    }

    /** Blocks in an eight-block fragment share an offset, leaving recognizable partial construction. */
    public static long fragmentHash(long seed, int x, int y, int z) {
        return EchoRegionModel.mix(seed ^ (long) Math.floorDiv(x, 8) * 0x9E3779B97F4A7C15L
                ^ (long) Math.floorDiv(y, 8) * 0xC2B2AE3D27D4EB4FL
                ^ (long) Math.floorDiv(z, 8) * 0x165667B19E3779F9L);
    }

    /** Small lateral displacement; sampled records retain missing blocks between fragments. */
    public static int lateralOffset(long hash) {
        return Math.floorMod(hash, 5) - 2;
    }

    /** Some fragments settle up to three blocks below their original position. */
    public static int verticalOffset(long hash) {
        return -Math.floorMod(hash >>> 8, 4);
    }
}
