package com.thunder.wildernessodysseyapi.dataengine.interest;

/**
 * Chunk radii used by one feature to classify player interest.
 *
 * @param nearRadiusChunks full-detail radius
 * @param regionalRadiusChunks reduced-rate radius
 * @param distantRadiusChunks summarized-state radius
 */
public record InterestProfile(
        int nearRadiusChunks,
        int regionalRadiusChunks,
        int distantRadiusChunks
) {
    private static final int MAXIMUM_RADIUS_CHUNKS = 4_096;

    public InterestProfile {
        if (nearRadiusChunks < 0
                || regionalRadiusChunks < nearRadiusChunks
                || distantRadiusChunks < regionalRadiusChunks
                || distantRadiusChunks > MAXIMUM_RADIUS_CHUNKS) {
            throw new IllegalArgumentException("Interest radii must be ordered and between 0 and 4096 chunks");
        }
    }

    /** Creates a single-radius profile where interested players receive near detail. */
    public static InterestProfile within(int radiusChunks) {
        return new InterestProfile(radiusChunks, radiusChunks, radiusChunks);
    }
}
