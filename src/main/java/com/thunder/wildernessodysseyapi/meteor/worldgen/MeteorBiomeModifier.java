package com.thunder.wildernessodysseyapi.meteor.worldgen;

/**
 * Legacy entry point retained for integrations compiled against older builds.
 *
 * <p>Natural craters use the data-pack {@code meteor_craters} structure set
 * and bounded structure pieces. Calling this compatibility method is a no-op.</p>
 *
 * @deprecated natural placement is owned by the crater structure set
 */
@Deprecated(forRemoval = true)
public final class MeteorBiomeModifier {

    private MeteorBiomeModifier() {
    }

    /**
     * Retains source and binary compatibility with the former registration hook.
     *
     * @deprecated the structure registry owns natural crater placement
     */
    @Deprecated(forRemoval = true)
    public static void register() {
        // Retained for integrations using the former biome-injection hook.
    }
}
